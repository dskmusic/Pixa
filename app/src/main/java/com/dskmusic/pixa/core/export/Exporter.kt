package com.dskmusic.pixa.core.export

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** Formatos de salida (requisito 5). */
enum class ExportFormat(val mime: String, val ext: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf"),
    ;

    /** PNG y PDF no tienen parametro de calidad. */
    val hasQuality: Boolean get() = this == JPEG || this == WEBP

    /** Solo PNG y WEBP conservan transparencia. */
    val supportsAlpha: Boolean get() = this == PNG || this == WEBP

    companion object {
        /**
         * Formato de salida equivalente a un tipo MIME, o null si el original no es uno
         * de los que la app puede escribir (p.ej. HEIC o GIF). Sirve para fijar el formato
         * al reemplazar la imagen original, de modo que el archivo no cambie de tipo.
         */
        fun fromMime(mime: String?): ExportFormat? = when (mime?.lowercase()) {
            "image/png" -> PNG
            "image/jpeg", "image/jpg" -> JPEG
            "image/webp" -> WEBP
            else -> null
        }
    }
}

/**
 * Opciones de exportacion.
 *
 * [scalePercent] va de 25 a 400 como pide el requisito 5. [targetSizeKb], si se indica,
 * manda sobre [quality]: se busca la calidad mas alta que quepa en ese tamano.
 */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.PNG,
    val quality: Int = 92,
    val scalePercent: Int = 100,
    val keepExif: Boolean = false,
    val targetSizeKb: Int? = null,
    val baseName: String = "pixa",
)

class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)

object Exporter {

    private const val MIN_SCALE = 25
    private const val MAX_SCALE = 400

    /** Aplica la escala y, si el formato no admite alfa, aplana sobre blanco. */
    fun prepare(src: Bitmap, options: ExportOptions): Bitmap {
        val percent = options.scalePercent.coerceIn(MIN_SCALE, MAX_SCALE)
        var out = src
        if (percent != 100) {
            val w = (src.width * percent / 100f).roundToInt().coerceAtLeast(1)
            val h = (src.height * percent / 100f).roundToInt().coerceAtLeast(1)
            out = BitmapUtils.scale(src, w, h)
        }
        if (!options.format.supportsAlpha && out.hasAlpha()) {
            val flat = BitmapUtils.create(out.width, out.height)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(out, 0f, 0f, null)
            }
            if (out !== src) out.recycle()
            out = flat
        }
        return out
    }

    /** Codifica el bitmap ya preparado. */
    fun encode(bitmap: Bitmap, options: ExportOptions): ByteArray {
        if (options.format == ExportFormat.PDF) return encodePdf(bitmap)
        val target = options.targetSizeKb
        return if (target != null && options.format.hasQuality) {
            compressToTarget(bitmap, options.format, target)
        } else {
            compress(bitmap, options.format, options.quality.coerceIn(1, 100))
        }
    }

    private fun compress(bitmap: Bitmap, format: ExportFormat, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val compressFormat = when (format) {
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ExportFormat.WEBP ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            ExportFormat.PDF -> throw ExportException("PDF no usa compress()")
        }
        if (!bitmap.compress(compressFormat, quality, out)) {
            throw ExportException("No se pudo codificar la imagen")
        }
        return out.toByteArray()
    }

    /**
     * Comprimir a un tamano objetivo (requisito 5).
     *
     * Antes: busqueda binaria de ~8 codificaciones a resolucion completa. Ahora la calidad se
     * estima primero sobre una copia pequena (barata) y a resolucion completa solo se busca
     * en una ventana alrededor de esa estimacion, con salida anticipada si ya se queda
     * dentro del 6 % por debajo del objetivo. Si la ventana falla se amplia, asi que el
     * resultado sigue siendo el mismo: la mayor calidad que cabe en el limite. Si ni la
     * calidad minima cabe, se reduce la resolucion en pasos del 85 % hasta que entre.
     */
    fun compressToTarget(bitmap: Bitmap, format: ExportFormat, targetKb: Int): ByteArray {
        val limit = targetKb.coerceAtLeast(5) * 1024
        var work = bitmap
        var scaled = false
        repeat(12) {
            val guess = estimateQuality(work, format, limit)
            val top = (guess + 10).coerceAtMost(100)
            var best = searchQuality(work, format, limit, (guess - 10).coerceAtLeast(5), top)
            if (best == null) {
                // La estimacion se quedo corta por arriba: buscar por debajo de la ventana.
                best = searchQuality(work, format, limit, 5, (guess - 11).coerceAtLeast(5))
            } else if (best.first == top && top < 100) {
                // Cabia incluso en el tope de la ventana: puede haber mas calidad disponible.
                val more = searchQuality(work, format, limit, top + 1, 100)
                if (more != null) best = more
            }
            val found = best
            if (found != null) {
                if (scaled) work.recycle()
                return found.second
            }
            val w = (work.width * 0.85f).roundToInt()
            val h = (work.height * 0.85f).roundToInt()
            if (w < 16 || h < 16) {
                val fallback = compress(work, format, 5)
                if (scaled) work.recycle()
                return fallback
            }
            val next = BitmapUtils.scale(work, w, h)
            if (scaled) work.recycle()
            work = next
            scaled = true
        }
        val last = compress(work, format, 5)
        if (scaled) work.recycle()
        return last
    }

    /** Mayor calidad de [low]..[high] cuyo resultado cabe en [limit], con su tamano; null si ninguna cabe. */
    private fun searchQuality(
        bitmap: Bitmap,
        format: ExportFormat,
        limit: Int,
        low: Int,
        high: Int,
    ): Pair<Int, ByteArray>? {
        var lo = low
        var hi = high
        var best: Pair<Int, ByteArray>? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val bytes = compress(bitmap, format, mid)
            if (bytes.size <= limit) {
                best = mid to bytes
                // Salida anticipada: ya queda a menos de un 6 % del limite.
                if (bytes.size >= limit * 0.94f) break
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }

    /**
     * Calidad aproximada que cumple [limit] a resolucion completa, calculada sobre una copia
     * de lado mayor 800 px con el limite escalado por el numero de pixeles. Es solo una pista
     * para acotar la busqueda real; si la imagen ya es pequena equivale a buscar en ella.
     */
    private fun estimateQuality(bitmap: Bitmap, format: ExportFormat, limit: Int): Int {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= 800) return 50
        val k = 800f / longest
        val proxy = BitmapUtils.scale(
            bitmap,
            (bitmap.width * k).roundToInt().coerceAtLeast(1),
            (bitmap.height * k).roundToInt().coerceAtLeast(1),
        )
        try {
            val ratio = proxy.width.toFloat() * proxy.height / (bitmap.width.toFloat() * bitmap.height)
            return searchQuality(proxy, format, (limit * ratio).toInt().coerceAtLeast(1), 5, 100)?.first ?: 50
        } finally {
            proxy.recycle()
        }
    }

    /** Una pagina del tamano exacto de la imagen en puntos. */
    private fun encodePdf(bitmap: Bitmap): ByteArray {
        val doc = PdfDocument()
        try {
            val info = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = doc.startPage(info)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            doc.finishPage(page)
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    // ----------------------------------------------------------------------------------
    // Escritura
    // ----------------------------------------------------------------------------------

    /**
     * Guarda en la galeria, dentro de Pictures/Pixa (requisito 5).
     *
     * Se usa MediaStore y no acceso directo al almacenamiento: asi no hace falta ningun
     * permiso de escritura en ninguna version soportada.
     */
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        options: ExportOptions,
        exifSourceUri: Uri? = null,
    ): Uri {
        val prepared = prepare(bitmap, options)
        val bytes = try {
            encode(prepared, options)
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }
        val name = fileName(options)
        val collection = if (options.format == ExportFormat.PDF) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val relative = if (options.format == ExportFormat.PDF) "Download/Pixa" else "Pictures/Pixa"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, options.format.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw ExportException("La galeria rechazo el archivo")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw ExportException("No se pudo abrir el archivo de salida")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw ExportException("No se pudo guardar la imagen", e)
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        if (options.keepExif && options.format == ExportFormat.JPEG && exifSourceUri != null) {
            copyExif(context, exifSourceUri, uri)
        }
        return uri
    }

    /**
     * Sobrescribe la imagen original de la que venia el documento, en vez de crear una
     * copia nueva (requisito del usuario: "reemplazar el original").
     *
     * Escribe los bytes codificados directamente sobre [sourceUri]. Devuelve true si se
     * pudo sobrescribir, o false si no fue posible.
     *
     * Estrategia (de mas a menos fiable para el flujo habitual de la app):
     *  1. Si la uri es `file://` o se puede resolver a una ruta real del almacen, se escribe
     *     el archivo directamente con `FileOutputStream`. Cubre el caso del Photo Picker,
     *     que entrega copias de solo lectura (`content://media/picker/...`) cuya uri no se
     *     puede abrir en modo escritura: aqui se escribe el archivo real de MediaStore al
     *     que apuntan, valido con acceso total a archivos (Android 11+) o almacenamiento
     *     legacy (Android 10).
     *  2. Si no hay ruta, se intenta `contentResolver.openOutputStream`, que sirve para
     *     uris de MediaStore sobre las que ya se obtuvo consentimiento puntual con
     *     `MediaStore.createWriteRequest` (p.ej. fotos abiertas con "Editar con").
     */
    fun replaceOriginal(context: Context, sourceUri: Uri, bitmap: Bitmap, options: ExportOptions): Boolean {
        Log.d("PixaReplace", "replaceOriginal sourceUri=$sourceUri")
        // Las copias temporales de cache de otra app (p.ej. la camara de Xiaomi) no son
        // reemplazables: no hay "original" real que sobrescribir.
        if (!isReplaceable(context, sourceUri)) {
            Log.d("PixaReplace", "  -> no reemplazable, devuelve false")
            return false
        }
        val prepared = prepare(bitmap, options)
        val outW = prepared.width
        val outH = prepared.height
        val bytes = try {
            encode(prepared, options)
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }

        // 1. Acceso total a archivos (Android 11+) o almacenamiento legacy (Android 10):
        //    se resuelve la ruta real (_data) de la uri y se escribe directamente con File.
        //    Asi no hace falta el consentimiento puntual por archivo (requisito del usuario).
        if (canAccessAllFiles()) {
            val realUri = resolveMediaUri(context, sourceUri) ?: sourceUri
            val path = resolveFilePath(context, realUri)
            Log.d("PixaReplace", "  (acceso total) realUri=$realUri path=$path")
            if (path != null) {
                val ok = runCatching {
                    FileOutputStream(File(path)).use { it.write(bytes) }
                    true
                }.getOrDefault(false)
                if (ok) {
                    notifyMediaStoreChanged(context, realUri, path, outW, outH)
                    return true
                }
            }
        }

        // 2. Recurso: escribir a traves de la uri real de MediaStore (consentimiento puntual
        //    con createWriteRequest obtenido antes de llegar aqui).
        val realUri = resolveMediaUri(context, sourceUri) ?: sourceUri
        Log.d("PixaReplace", "  realUri=$realUri")
        val viaResolver = runCatching {
            context.contentResolver.openOutputStream(realUri, "wt")?.use { out ->
                out.write(bytes)
                true
            } ?: false
        }.getOrDefault(false)
        Log.d("PixaReplace", "  write via contentResolver=$viaResolver")
        if (viaResolver) {
            notifyMediaStoreChanged(context, realUri, resolveFilePath(context, realUri), outW, outH)
            return true
        }

        // 3. Recurso legacy: escribir sobre la ruta real si aun se puede resolver.
        val path = resolveFilePath(context, sourceUri)
        Log.d("PixaReplace", "  path=$path")
        if (path != null) {
            val ok = runCatching {
                FileOutputStream(File(path)).use { it.write(bytes) }
                true
            }.getOrDefault(false)
            if (ok) {
                notifyMediaStoreChanged(context, realUri, path, outW, outH)
                return true
            }
        }

        Log.d("PixaReplace", "  -> TODO fallo, devuelve false")
        return false
    }

    /**
     * Avisa a MediaStore de que un archivo ya existente ha cambiado en disco, para que la
     * galeria refresque su miniatura y su fecha sin necesidad de reiniciar.
     *
     * Se hace por dos vias complementarias: un rescan del archivo y, si hay uri de MediaStore
     * con id, un update de DATE_MODIFIED.
     */
    private fun notifyMediaStoreChanged(context: Context, uri: Uri, path: String?, width: Int, height: Int) {
        // Galerias como Simple Gallery cachean la miniatura por fecha de modificacion: se
        // sube la del archivo y la de MediaStore para invalidarla.
        if (path != null) {
            runCatching { File(path).setLastModified(System.currentTimeMillis()) }
            runCatching {
                val mime = when (path.substringAfterLast('.').lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime), null)
            }
        }
        runCatching { context.contentResolver.notifyChange(uri, null) }
        val id = uri.lastPathSegment?.toLongOrNull() ?: return
        val where = "${MediaStore.MediaColumns._ID}=?"
        val args = arrayOf(id.toString())
        val table = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // Una columna por update: si Android rechaza una (p.ej. SIZE), las demas se aplican.
        // Nunca se toca IS_PENDING: dejarlo a 1 oculta la foto de todas las galerias.
        fun set(column: String, value: Any) {
            runCatching {
                val values = android.content.ContentValues()
                when (value) {
                    is Long -> values.put(column, value)
                    is Int -> values.put(column, value)
                }
                context.contentResolver.update(table, values, where, args)
            }
        }
        set(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        set(MediaStore.MediaColumns.WIDTH, width)
        set(MediaStore.MediaColumns.HEIGHT, height)
        path?.let { set(MediaStore.MediaColumns.SIZE, File(it).length()) }
        // Rescate de fotos que una version anterior dejo como pendientes.
        set(MediaStore.MediaColumns.IS_PENDING, 0)
    }

    /** true si la app puede leer/escribir todo el almacen directamente (sin consentimiento). */
    fun canAccessAllFiles(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> true // Android 10 con requestLegacyExternalStorage + WRITE en runtime.
    }

    /**
     * true si [uri] apunta a una foto real que se puede sobrescribir "en su sitio".
     *
     * False para las copias temporales que otras apps entregan al compartir (p.ej. la camara
     * de Xiaomi envia `content://com.miui.securitycenter.../cache/xxx.jpg`, un archivo en
     * `Android/data/` de otra app): ahi no hay "original" que reemplazar, solo una copia
     * efimera con nombre de timestamp.
     */
    fun isReplaceable(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") return true
        // Real de MediaStore (o resoluble a MediaStore): reemplazable.
        val real = resolveMediaUri(context, uri)
        if (real != null && real != uri) return true
        if (uri.authority == "media") return true
        // Cualquier otra cosa: solo si el _data resuelto esta fuera de Android/data.
        val path = resolveFilePath(context, uri) ?: return false
        val norm = path.replace('\\', '/')
        return !norm.contains("/Android/data/") && !norm.contains("/Android/obb/")
    }

    /**
     * Resuelve la ruta real en disco de una uri, si es posible, para poder escribir el
     * archivo directamente. Soportan `file://` y las uris de MediaStore (incluidas las del
     * Photo Picker, que comparten el mismo `_id` que la entrada de MediaStore).
     *
     * Devuelve null si el proveedor no expone una ruta accesible (documentos SAF, contenido
     * de otra app sin columna de ruta...). En Android 10 con almacenamiento legacy y en
     * Android 11+ con acceso total, la columna `_data` suele estar disponible; en las
     * versiones nuevas puede venir vacia por privacidad, en cuyo caso se cae al content
     * resolver.
     */
    private fun resolveFilePath(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path

        // El Photo Picker entrega uris como content://media/picker/.../media/1234, donde el
        // tramo final numerico es el _id real de la entrada de MediaStore. Se extrae para
        // poder consultar el archivo real (la uri del picker no expone _data ni, a menudo,
        // ni siquiera el nombre).
        val pickerId = uri.lastPathSegment?.toLongOrNull()

        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        val data = runCatching {
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val relIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val id = if (idIndex >= 0 && !cursor.isNull(idIndex)) cursor.getLong(idIndex) else pickerId ?: -1L
                    val path = if (dataIndex >= 0 && !cursor.isNull(dataIndex)) cursor.getString(dataIndex) else null
                    val relative = if (relIndex >= 0 && !cursor.isNull(relIndex)) cursor.getString(relIndex) else null
                    val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                    Triple(id, path, relative to name)
                } else null
            }
        }.getOrNull()

        if (data != null) {
            val (id, path, relName) = data
            if (!path.isNullOrBlank()) return path
            val (relative, name) = relName
            // Con el id real se pregunta a MediaStore por la ruta en disco.
            if (id >= 0L) {
                val direct = queryDataPath(context, id)
                if (!direct.isNullOrBlank()) return direct
            }
            if (!relative.isNullOrBlank() && !name.isNullOrBlank()) {
                return "${Environment.getExternalStorageDirectory().absolutePath}/$relative/$name"
            }
        } else if (pickerId != null) {
            // La uri del picker no contesta a ninguna columna: se usa el id del tramo final.
            val direct = queryDataPath(context, pickerId)
            if (!direct.isNullOrBlank()) return direct
        }
        return null
    }

    /** Pregunta a MediaStore la ruta en disco (_data) de un id de imagen concreto. */
    private fun queryDataPath(context: Context, id: Long): String? = runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.DATA),
            "${MediaStore.MediaColumns._ID}=?",
            arrayOf(id.toString()),
            null,
        )?.use { c ->
            val di = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (c.moveToFirst() && di >= 0 && !c.isNull(di)) c.getString(di) else null
        }
    }.getOrNull()

    /**
     * Convierte una uri (que puede ser del Photo Picker, `content://media/picker/...`) en
     * la uri real de MediaStore (`content://media/external/images/media/{id}`), sobre la que
     * si se puede pedir escritura puntual con `MediaStore.createWriteRequest` y escribir
     * con `openOutputStream`.
     *
     * El Photo Picker devuelve uris de solo lectura y su `_data` siempre es null en Android
     * reciente; pero al consultarla si expone el `_id` real de la entrada de MediaStore, que
     * es justo lo que hace falta para reconstruir la uri de escritura.
     *
     * Devuelve la uri real, o null si no se puede resolver (uri de documento SAF, de otra
     * app sin columna _id...).
     */
    fun resolveMediaUri(context: Context, sourceUri: Uri): Uri? {
        Log.d("PixaReplace", "resolveMediaUri input=$sourceUri scheme=${sourceUri.scheme} authority=${sourceUri.authority} path=${sourceUri.path} lastSeg=${sourceUri.lastPathSegment}")
        // Ya es una uri real de MediaStore (no del picker): se devuelve tal cual.
        if (sourceUri.authority == "media" && !sourceUri.path.orEmpty().contains("picker")) {
            Log.d("PixaReplace", "  -> ya es uri real de MediaStore, se devuelve igual")
            return sourceUri
        }

        // En el Photo Picker el id REAL de MediaStore es el numero grande del final del
        // camino (`.../media/1000246566`), NO el `_id` que devuelve su consulta (que en este
        // dispositivo es un id interno pequeno tipo 23922 y createWriteRequest lo rechaza).
        // Se prioriza por tanto el tramo final numerico, y solo como ultimo recurso el `_id`.
        val pathId = sourceUri.lastPathSegment?.toLongOrNull()
        Log.d("PixaReplace", "  pathId(lastPathSegment=${sourceUri.lastPathSegment}) = $pathId")
        if (pathId != null) {
            val built = buildMediaUri(pathId)
            Log.d("PixaReplace", "  -> uriConstruida(desde pathId)=$built")
            return built
        }

        // Recurso: consultar el _id (para uris del picker sin numero en el camino).
        val id = runCatching {
            context.contentResolver.query(
                sourceUri,
                arrayOf(MediaStore.MediaColumns._ID),
                null, null, null,
            )?.use { c ->
                val i = c.getColumnIndex(MediaStore.MediaColumns._ID)
                if (c.moveToFirst() && i >= 0 && !c.isNull(i)) c.getLong(i) else null
            }
        }.getOrNull()?.takeIf { it >= 0L }
        Log.d("PixaReplace", "  _id por consulta=$id")
        return id?.let { buildMediaUri(it) }
    }

    /**
     * Uri canónica de MediaStore para un id de imagen. Se usa siempre la forma clasica
     * `content://media/external/images/media/{id}` porque es la que acepta
     * `MediaStore.createWriteRequest`; `getContentUri(VOLUME_EXTERNAL_PRIMARY)` genera
     * `content://media/external_primary/...`, que el dialogo de consentimiento rechaza con
     * "Invalid Uri" en Android 15/16.
     */
    private fun buildMediaUri(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    /** Archivo temporal en cache + uri de FileProvider, para compartir. */
    fun writeShareFile(context: Context, bitmap: Bitmap, options: ExportOptions): Uri {
        val prepared = prepare(bitmap, options)
        val bytes = try {
            encode(prepared, options)
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, fileName(options))
        FileOutputStream(file).use { it.write(bytes) }
        return fileUri(context, file)
    }

    fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareIntent(uri: Uri, mime: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Intent para ver la imagen recien guardada desde el aviso "Ver". */
    fun viewIntent(uri: Uri, mime: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun fileName(options: ExportOptions): String {
        val safe = options.baseName.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifEmpty { "pixa" }
        return "${safe}_${System.currentTimeMillis()}.${options.format.ext}"
    }

    /**
     * Copia los metadatos EXIF del original (requisito 5: conservar o eliminar EXIF).
     *
     * No copiar nada ya equivale a eliminarlos, porque el bitmap exportado se escribe sin
     * metadatos. La orientacion se fuerza a normal: la imagen ya sale rotada en pixeles.
     */
    fun copyExif(context: Context, source: Uri, destination: Uri) {
        val attributes = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_TIMESTAMP,
        )
        runCatching {
            val values = context.contentResolver.openInputStream(source)?.use { input ->
                val exif = ExifInterface(input)
                attributes.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }
            } ?: return
            context.contentResolver.openFileDescriptor(destination, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                values.forEach { (tag, value) -> exif.setAttribute(tag, value) }
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString(),
                )
                exif.saveAttributes()
            }
        }
    }
}
