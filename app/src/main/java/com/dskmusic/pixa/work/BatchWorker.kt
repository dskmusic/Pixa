package com.dskmusic.pixa.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dskmusic.pixa.MainActivity
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.export.ExportFormat
import com.dskmusic.pixa.core.export.ExportOptions
import com.dskmusic.pixa.core.export.Exporter
import com.dskmusic.pixa.core.image.Filters
import com.dskmusic.pixa.core.model.AdjustPreset
import com.dskmusic.pixa.core.model.BuiltInFilter
import com.dskmusic.pixa.core.model.WatermarkSpec
import com.dskmusic.pixa.core.project.PixaFile
import com.dskmusic.pixa.core.render.Overlays
import com.dskmusic.pixa.core.transform.TransformOps
import com.dskmusic.pixa.pixa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** Modos de redimensionado del lote (requisito 22). */
@Serializable
enum class BatchResize { OFF, WIDTH, HEIGHT, PERCENT }

/**
 * Receta del lote.
 *
 * Va serializada en los datos de entrada del worker porque WorkManager solo acepta tipos
 * primitivos: un JSON de medio kilobyte es mas simple y mas facil de ampliar que diez
 * claves sueltas, y de paso el lote sobrevive a que la pantalla se cierre.
 */
@Serializable
data class BatchPlan(
    val resize: BatchResize = BatchResize.OFF,
    /** Pixeles cuando el modo es WIDTH/HEIGHT, porcentaje cuando es PERCENT. */
    val resizeValue: Int = 1920,
    val ratioW: Float = 0f,
    val ratioH: Float = 0f,
    /** Id de filtro predefinido, o null. Excluyente con [presetId]. */
    val filterId: String? = null,
    val presetId: String? = null,
    val watermarkId: String? = null,
    /** Cuando es false, cada imagen se guarda en su propio formato de origen. */
    val convert: Boolean = false,
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    val targetSizeKb: Int? = null,
    val keepExif: Boolean = true,
    /** Patron de nombre tipo "nombre_###"; vacio conserva el nombre original. */
    val pattern: String = "",
) {
    /** Si no hay ninguna operacion, procesar no cambiaria nada. */
    val hasWork: Boolean
        get() = resize != BatchResize.OFF ||
            (ratioW > 0f && ratioH > 0f) ||
            filterId != null ||
            presetId != null ||
            watermarkId != null ||
            convert ||
            targetSizeKb != null ||
            !keepExif ||
            pattern.isNotBlank()
}

/**
 * Procesado por lotes en segundo plano (requisito 22).
 *
 * Es un worker y no una corrutina de la pantalla para que el lote siga si el usuario sale
 * de ella, y para que cancelar sea una sola llamada a WorkManager. La pantalla solo
 * observa el progreso publicado aqui.
 *
 * Las imagenes se guardan en la carpeta elegida con el selector del sistema (asi no hace
 * falta ningun permiso de almacenamiento) o, si no se elige ninguna, en la cache para
 * compartirlas todas de una vez.
 */
class BatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val sources = inputData.getStringArray(KEY_URIS)?.map(Uri::parse).orEmpty()
        val planJson = inputData.getString(KEY_PLAN)
        if (sources.isEmpty() || planJson == null) return@withContext Result.failure()
        val plan = runCatching {
            PixaFile.json.decodeFromString(BatchPlan.serializer(), planJson)
        }.getOrNull() ?: return@withContext Result.failure()

        val folder = inputData.getString(KEY_TREE)
            ?.let { DocumentFile.fromTreeUri(applicationContext, Uri.parse(it)) }
        if (inputData.getString(KEY_TREE) != null && folder == null) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to true))
        }

        val container = applicationContext.pixa
        val userData = container.userData.refresh()
        val filter = plan.filterId?.let { Filters.byId(it) }?.takeIf { it.id != "original" }
        val preset = plan.presetId?.let { id -> userData.presets.firstOrNull { it.id == id } }
        val watermark = plan.watermarkId?.let { id -> userData.watermarks.firstOrNull { it.id == id } }
        // La fuente de la marca se descarga una vez para todo el lote, no por imagen.
        val typeface = watermark?.let { container.fonts.load(it.font) }
        val logoDir = container.userData.logoDir()

        val notifier = BatchNotifier(applicationContext)
        val shareDir = File(applicationContext.cacheDir, SHARE_DIR).apply { mkdirs() }
        val produced = ArrayList<String>(sources.size)
        var done = 0

        notifier.progress(0, sources.size)
        for ((index, source) in sources.withIndex()) {
            if (isStopped) {
                notifier.cancelled()
                return@withContext Result.failure(workDataOf(KEY_DONE to done))
            }
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to sources.size))

            val written = runCatching {
                processOne(
                    source = source,
                    index = index,
                    plan = plan,
                    filter = filter,
                    preset = preset,
                    watermark = watermark,
                    typeface = typeface,
                    logoDir = logoDir,
                    folder = folder,
                    shareDir = shareDir,
                )
            }.getOrNull()

            if (written != null) {
                produced += written.toString()
                done++
            }
            notifier.progress(done, sources.size)
        }

        notifier.finished(done)
        // Las uris solo se devuelven cuando hay que compartirlas: los datos de salida de
        // WorkManager tienen un tope de 10 KB y la ruta de carpeta no las necesita.
        val output = if (folder == null) {
            workDataOf(KEY_DONE to done, KEY_RESULTS to produced.toTypedArray())
        } else {
            workDataOf(KEY_DONE to done)
        }
        if (done == 0) Result.failure(output) else Result.success(output)
    }

    /** Toda la cadena de una imagen: decodificar, operar, codificar y escribir. */
    private fun processOne(
        source: Uri,
        index: Int,
        plan: BatchPlan,
        filter: BuiltInFilter?,
        preset: AdjustPreset?,
        watermark: WatermarkSpec?,
        typeface: Typeface?,
        logoDir: File,
        folder: DocumentFile?,
        shareDir: File,
    ): Uri? {
        var bitmap = BitmapUtils.decodeUri(applicationContext, source, MAX_DECODE)

        fun step(next: Bitmap) {
            if (next !== bitmap) {
                bitmap.recycle()
                bitmap = next
            }
        }

        if (plan.ratioW > 0f && plan.ratioH > 0f) {
            val rect = TransformOps.aspectCrop(bitmap.width, bitmap.height, plan.ratioW, plan.ratioH)
            step(BitmapUtils.crop(bitmap, rect))
        }
        val size = targetSize(bitmap.width, bitmap.height, plan)
        if (size != null && (size.first != bitmap.width || size.second != bitmap.height)) {
            step(TransformOps.resizeImage(bitmap, size.first, size.second))
        }
        if (filter != null) {
            step(Filters.apply(bitmap, filter))
        } else if (preset != null) {
            step(Filters.applyPreset(bitmap, preset))
        }
        if (watermark != null) {
            step(Overlays.applyWatermark(bitmap, watermark, typeface, logoDir))
        }

        val format = if (plan.convert) plan.format else sourceFormat(source) ?: plan.format
        val options = ExportOptions(
            format = format,
            quality = plan.quality,
            targetSizeKb = plan.targetSizeKb,
            keepExif = plan.keepExif,
        )
        val prepared = Exporter.prepare(bitmap, options)
        val bytes = try {
            Exporter.encode(prepared, options)
        } finally {
            if (prepared !== bitmap) prepared.recycle()
            bitmap.recycle()
        }

        val name = outputName(source, index, plan.pattern, format.ext)
        val target = if (folder != null) {
            val document = folder.createFile(format.mime, name)?.uri ?: return null
            applicationContext.contentResolver.openOutputStream(document)?.use { it.write(bytes) }
                ?: return null
            document
        } else {
            val file = File(shareDir, name)
            file.writeBytes(bytes)
            Exporter.fileUri(applicationContext, file)
        }
        // Conservar EXIF solo tiene sentido en JPEG: es el unico formato que los escribe.
        if (plan.keepExif && format == ExportFormat.JPEG) {
            Exporter.copyExif(applicationContext, source, target)
        }
        return target
    }

    /** Tamano final segun el modo de redimensionado, manteniendo la proporcion. */
    private fun targetSize(width: Int, height: Int, plan: BatchPlan): Pair<Int, Int>? {
        val value = plan.resizeValue
        return when (plan.resize) {
            BatchResize.OFF -> null
            BatchResize.WIDTH -> {
                val w = value.coerceIn(16, MAX_SIDE)
                w to (height * w / width.toFloat()).roundToInt().coerceAtLeast(1)
            }
            BatchResize.HEIGHT -> {
                val h = value.coerceIn(16, MAX_SIDE)
                (width * h / height.toFloat()).roundToInt().coerceAtLeast(1) to h
            }
            BatchResize.PERCENT -> {
                val k = value.coerceIn(5, 400) / 100f
                (width * k).roundToInt().coerceAtLeast(1) to (height * k).roundToInt().coerceAtLeast(1)
            }
        }
    }

    /** Formato de origen, para "convertir formato" desactivado. */
    private fun sourceFormat(source: Uri): ExportFormat? =
        when (applicationContext.contentResolver.getType(source)) {
            "image/png" -> ExportFormat.PNG
            "image/webp" -> ExportFormat.WEBP
            "image/jpeg", "image/jpg" -> ExportFormat.JPEG
            else -> null
        }

    /**
     * Nombre de salida a partir del patron (requisito 22).
     *
     * Los almohadillas seguidas marcan donde va el numero y con cuantas cifras, de forma
     * que "nombre_###" da "nombre_001". Sin patron se conserva el nombre original.
     */
    private fun outputName(source: Uri, index: Int, pattern: String, ext: String): String {
        val number = index + 1
        val base = if (pattern.isBlank()) {
            displayName(source)?.substringBeforeLast('.') ?: "pixa_$number"
        } else {
            val hashes = Regex("#+").find(pattern)
            if (hashes == null) {
                "$pattern${"%03d".format(number)}"
            } else {
                pattern.replaceRange(
                    hashes.range,
                    number.toString().padStart(hashes.value.length, '0'),
                )
            }
        }
        val safe = base.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifEmpty { "pixa_$number" }
        return "$safe.$ext"
    }

    private fun displayName(uri: Uri): String? = runCatching {
        applicationContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()

    companion object {
        const val UNIQUE_NAME = "pixa_batch"
        const val KEY_URIS = "uris"
        const val KEY_PLAN = "plan"
        const val KEY_TREE = "tree"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_RESULTS = "results"
        const val KEY_ERROR = "error"

        /** Tope de imagenes por lote: suficiente para una carpeta de fotos y seguro de memoria. */
        const val MAX_IMAGES = 60

        private const val MAX_DECODE = 4096
        private const val MAX_SIDE = 8192
        private const val SHARE_DIR = "shared"

        /**
         * Encola el lote. Si ya hay uno en marcha se mantiene el anterior, porque dos
         * lotes a la vez solo se pelearian por la memoria.
         */
        /** Encola el lote y devuelve el id del trabajo para seguir su resultado. */
        fun enqueue(
            context: Context,
            uris: List<Uri>,
            plan: BatchPlan,
            treeUri: Uri?,
        ): UUID {
            val data = Data.Builder()
                .putStringArray(KEY_URIS, uris.take(MAX_IMAGES).map(Uri::toString).toTypedArray())
                .putString(KEY_PLAN, PixaFile.json.encodeToString(BatchPlan.serializer(), plan))
                .apply { if (treeUri != null) putString(KEY_TREE, treeUri.toString()) }
                .build()
            val request = OneTimeWorkRequestBuilder<BatchWorker>().setInputData(data).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            return request.id
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}

/**
 * Aviso de progreso del lote.
 *
 * Es una notificacion normal, no un servicio en primer plano: el lote dura segundos o
 * pocos minutos y asi la app no necesita el permiso FOREGROUND_SERVICE. Si el usuario no
 * ha concedido las notificaciones, no se publica nada y el progreso se ve solo dentro de
 * la app.
 */
private class BatchNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    private val allowed: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    init {
        if (allowed) {
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.batch_notif_channel))
                    .build(),
            )
        }
    }

    fun progress(done: Int, total: Int) = post(
        text = context.getString(R.string.batch_running, done, total),
        total = total,
        done = done,
        ongoing = true,
    )

    fun finished(done: Int) = post(
        text = context.getString(R.string.batch_done, done),
        total = 0,
        done = 0,
        ongoing = false,
    )

    fun cancelled() = post(
        text = context.getString(R.string.batch_cancelled),
        total = 0,
        done = 0,
        ongoing = false,
    )

    private fun post(text: String, total: Int, done: Int, ongoing: Boolean) {
        if (!allowed) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.batch_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(ongoing)
        if (total > 0) builder.setProgress(total, done, false)
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private companion object {
        const val CHANNEL = "pixa_batch"
        const val NOTIFICATION_ID = 4201
    }
}
