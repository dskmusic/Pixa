package com.dskmusic.pixa.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Un archivo de un modelo: nombre local (tal cual lo espera ONNX), URL y peso exacto. */
class AiFile(val name: String, val url: String, val bytes: Long)

/**
 * Catalogo de modelos de IA que se descargan bajo demanda. Todos se ejecutan en el
 * dispositivo con ONNX Runtime; los pesos y URL se comprobaron con inferencia real.
 *
 * Licencias: Swin2SR y SCUNet, Apache 2.0; MobileSAM, MIT; Depth Anything V2 Small,
 * Apache 2.0; DDColor, Apache 2.0; MI-GAN, MIT; GFPGAN, Apache 2.0.
 */
enum class AiModel(val id: String, val files: List<AiFile>) {
    SUPER_RES(
        "super_res",
        listOf(
            AiFile(
                "swin2sr_lightweight_x2.onnx",
                "https://huggingface.co/Xenova/swin2SR-lightweight-x2-64/resolve/main/onnx/model.onnx",
                8_078_888,
            ),
        ),
    ),
    SUPER_RES_X4(
        "super_res_x4",
        listOf(
            AiFile(
                "swin2sr_realworld_x4.onnx",
                "https://huggingface.co/Xenova/swin2SR-realworld-sr-x4-64-bsrgan-psnr/resolve/main/onnx/model.onnx",
                52_772_645,
            ),
        ),
    ),
    DENOISE(
        "denoise",
        listOf(
            // El .data lleva los pesos: ONNX Runtime lo busca junto al .onnx con este nombre.
            AiFile(
                "scunet_color_real_psnr.onnx",
                "https://huggingface.co/Heliosoph/scunet-onnx/resolve/main/scunet_color_real_psnr.onnx",
                3_798_678,
            ),
            AiFile(
                "scunet_color_real_psnr.onnx.data",
                "https://huggingface.co/Heliosoph/scunet-onnx/resolve/main/scunet_color_real_psnr.onnx.data",
                73_138_176,
            ),
        ),
    ),
    SEGMENT(
        "segment",
        listOf(
            AiFile(
                "mobile_sam_image_encoder.onnx",
                "https://huggingface.co/Acly/MobileSAM/resolve/main/mobile_sam_image_encoder.onnx",
                28_157_093,
            ),
            AiFile(
                "sam_mask_decoder_single.onnx",
                "https://huggingface.co/Acly/MobileSAM/resolve/main/sam_mask_decoder_single.onnx",
                16_501_323,
            ),
        ),
    ),
    DEPTH(
        "depth",
        listOf(
            AiFile(
                "depth_anything_v2_small_q.onnx",
                "https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model_quantized.onnx",
                27_258_801,
            ),
        ),
    ),
    COLORIZE(
        "colorize",
        listOf(
            AiFile(
                "ddcolor_tiny_fp16.onnx",
                "https://huggingface.co/edgetools/ddcolor/resolve/main/ddcolor-tiny-fp16.onnx",
                135_444_402,
            ),
        ),
    ),
    INPAINT(
        "inpaint",
        listOf(
            AiFile(
                "migan_pipeline.onnx",
                "https://huggingface.co/anyisalin/migan-onnx/resolve/main/onnx/migan_pipeline.onnx",
                28_079_181,
            ),
        ),
    ),
    FACE(
        "face",
        listOf(
            AiFile(
                "gfpgan_v1_4.onnx",
                "https://huggingface.co/neurobytemind/GFPGANv1.4.onnx/resolve/main/GFPGANv1.4.onnx",
                340_256_686,
            ),
        ),
    ),
    BG_U2NETP(
        "bg_u2netp",
        listOf(
            AiFile(
                "u2netp.onnx",
                "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx",
                4_574_861,
            ),
        ),
    ),
    BG_MEDIAPIPE(
        "bg_mediapipe",
        listOf(
            AiFile(
                "selfie_multiclass_256x256.tflite",
                "https://storage.googleapis.com/mediapipe-models/image_segmenter/" +
                    "selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite",
                16_371_837,
            ),
        ),
    ),
    BG_SILUETA(
        "bg_silueta",
        listOf(
            AiFile(
                "silueta.onnx",
                "https://github.com/danielgatis/rembg/releases/download/v0.0.0/silueta.onnx",
                44_173_029,
            ),
        ),
    ),
    BG_U2NET(
        "bg_u2net",
        listOf(
            AiFile(
                "u2net.onnx",
                "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2net.onnx",
                175_997_641,
            ),
        ),
    ),
    BG_ISNET(
        "bg_isnet",
        listOf(
            AiFile(
                "isnet-general-use.onnx",
                "https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx",
                178_648_008,
            ),
        ),
    ),
    BG_ISNET_ANIME(
        "bg_isnet_anime",
        listOf(
            AiFile(
                "isnet-anime.onnx",
                "https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-anime.onnx",
                176_069_933,
            ),
        ),
    ),
    BG_MODNET(
        "bg_modnet",
        listOf(
            AiFile(
                "modnet_photographic.onnx",
                "https://huggingface.co/Xenova/modnet/resolve/main/onnx/model.onnx",
                25_888_640,
            ),
        ),
    ),
    BG_RMBG(
        "bg_rmbg",
        listOf(
            AiFile(
                "rmbg_1_4_quantized.onnx",
                "https://huggingface.co/briaai/RMBG-1.4/resolve/main/onnx/model_quantized.onnx",
                44_403_226,
            ),
        ),
    ),
    ;

    val totalBytes: Long get() = files.sumOf { it.bytes }

    private fun dir(context: Context) = File(context.filesDir, "models")

    fun isReady(context: Context): Boolean =
        files.all { ModelDownloader.existing(context, it.name) != null }

    /** Bytes que faltan por descargar (0 si ya esta todo). */
    fun missingBytes(context: Context): Long =
        files.filter { ModelDownloader.existing(context, it.name) == null }.sumOf { it.bytes }

    fun sizeOnDisk(context: Context): Long =
        files.sumOf { File(dir(context), it.name).takeIf { f -> f.exists() }?.length() ?: 0L }

    /** Borra los archivos y cierra las sesiones abiertas de este modelo. */
    fun delete(context: Context) {
        AiRuntime.closeAll()
        files.forEach { File(dir(context), it.name).delete() }
    }

    /**
     * Descarga lo que falte, archivo a archivo, con progreso global 0..1. Devuelve false si
     * alguna descarga falla; si se cancela la corrutina lanza CancellationException y los
     * restos parciales ya se han borrado. Mide la velocidad para estimar descargas futuras.
     */
    suspend fun download(context: Context, onProgress: (Float) -> Unit = {}): Boolean {
        val total = missingBytes(context).coerceAtLeast(1L)
        var done = 0L
        val started = System.currentTimeMillis()
        onProgress(0f)
        for (file in files) {
            if (ModelDownloader.existing(context, file.name) != null) continue
            val got = ModelDownloader.ensure(context, file.url, file.name) { p ->
                onProgress(((done + p * file.bytes) / total).coerceIn(0f, 1f))
            } ?: return false
            done += file.bytes
            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
            check(got.exists())
        }
        val seconds = (System.currentTimeMillis() - started) / 1000.0
        if (seconds > 1.0) AiStats.recordDownload(context, (total / seconds).toLong())
        return true
    }
}

/**
 * Velocidades medidas en este dispositivo, para avisar al usuario de cuanto va a tardar
 * una descarga o una operacion. Se guardan suavizadas (media con la anterior) porque la
 * red y la temperatura del movil varian. Sin medida previa, [estimateSeconds] devuelve null
 * y la interfaz dice que se calculara al empezar.
 */
object AiStats {
    private const val PREFS = "pixa_ai_stats"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordDownload(context: Context, bytesPerSecond: Long) {
        val old = prefs(context).getLong("dl_bps", 0L)
        val next = if (old <= 0L) bytesPerSecond else (old + bytesPerSecond) / 2
        prefs(context).edit().putLong("dl_bps", next).apply()
    }

    fun estimateDownloadSeconds(context: Context, bytes: Long): Long? {
        val bps = prefs(context).getLong("dl_bps", 0L)
        return if (bps <= 0L) null else (bytes / bps).coerceAtLeast(1L)
    }

    /** Guarda cuantos segundos cuesta una unidad de trabajo (megapixel, cara, pasada...). */
    fun recordOp(context: Context, op: String, secondsPerUnit: Float) {
        if (secondsPerUnit <= 0f || secondsPerUnit.isNaN()) return
        val old = prefs(context).getFloat("op_$op", 0f)
        val next = if (old <= 0f) secondsPerUnit else (old + secondsPerUnit) / 2f
        prefs(context).edit().putFloat("op_$op", next).apply()
    }

    fun estimateSeconds(context: Context, op: String, units: Float): Long? {
        val rate = prefs(context).getFloat("op_$op", 0f)
        return if (rate <= 0f) null else (rate * units).toLong().coerceAtLeast(1L)
    }
}

/**
 * Sesiones de ONNX Runtime abiertas. Se guardan como mucho dos (la segmentacion necesita
 * codificador y decodificador a la vez): los modelos grandes ocupan tanta memoria como su
 * archivo, y mantener varios abiertos acaba en un cierre de la app por memoria.
 */
object AiRuntime {

    private val lock = Mutex()
    private val sessions = LinkedHashMap<String, OrtSession>(4, 0.75f, true)

    suspend fun session(context: Context, fileName: String): OrtSession? = lock.withLock {
        sessions[fileName]?.let { return@withLock it }
        val file = ModelDownloader.existing(context, fileName) ?: return@withLock null
        runCatching {
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
            }
            OrtEnvironment.getEnvironment().createSession(file.absolutePath, options)
        }.getOrNull()?.also { created ->
            while (sessions.size >= 2) {
                val eldest = sessions.entries.first()
                runCatching { eldest.value.close() }
                sessions.remove(eldest.key)
            }
            sessions[fileName] = created
        }
    }

    fun closeAll() {
        synchronized(sessions) {
            sessions.values.forEach { runCatching { it.close() } }
            sessions.clear()
        }
    }
}
