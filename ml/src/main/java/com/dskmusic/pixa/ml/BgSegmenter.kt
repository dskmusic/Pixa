package com.dskmusic.pixa.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * Recorte de fondo con modelos ONNX de segmentacion de primer plano. Cada familia espera
 * un tamano y una normalizacion distintos; los valores salen de las pruebas con inferencia
 * real de cada modelo:
 *
 *  - ISNet (general y anime): 1024x1024, pixel / maximo, menos la media ImageNet.
 *  - U2-Net y Silueta: 320x320, normalizacion ImageNet completa.
 *  - MODNet: 512x512, (pixel - 127,5) / 127,5. Es un matting: ya sale calibrado en 0..1.
 *  - RMBG 1.4: 1024x1024, pixel / 255 - 0,5.
 *
 * Devuelve la mascara de confianza como [SubjectCutout.Mask], el mismo tipo que ML Kit y
 * MediaPipe, para que todo el resto del flujo (parametros, vista previa, aplicar) sea comun.
 */
object BgSegmenter {

    private enum class Kind { ISNET, U2, MODNET, RMBG }

    private class Spec(val file: String, val size: Int, val kind: Kind)

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    private fun spec(model: AiModel): Spec? = when (model) {
        AiModel.BG_ISNET -> Spec("isnet-general-use.onnx", 1024, Kind.ISNET)
        AiModel.BG_ISNET_ANIME -> Spec("isnet-anime.onnx", 1024, Kind.ISNET)
        AiModel.BG_SILUETA -> Spec("silueta.onnx", 320, Kind.U2)
        AiModel.BG_U2NET -> Spec("u2net.onnx", 320, Kind.U2)
        AiModel.BG_MODNET -> Spec("modnet_photographic.onnx", 512, Kind.MODNET)
        AiModel.BG_RMBG -> Spec("rmbg_1_4_quantized.onnx", 1024, Kind.RMBG)
        else -> null
    }

    suspend fun segment(
        context: Context,
        model: AiModel,
        src: Bitmap,
        onProgress: (Float) -> Unit = {},
    ): SubjectCutout.Mask? = withContext(Dispatchers.Default) {
        val spec = spec(model) ?: return@withContext null
        val ort = AiRuntime.session(context.applicationContext, spec.file) ?: return@withContext null
        try {
            val s = spec.size
            onProgress(0.05f)
            val small = Bitmap.createScaledBitmap(src, s, s, true)
            val px = IntArray(s * s)
            small.getPixels(px, 0, s, 0, 0, s, s)
            if (small !== src) small.recycle()
            val plane = s * s
            val data = FloatArray(3 * plane)
            var maxV = 1f
            if (spec.kind == Kind.ISNET) {
                for (p in px) {
                    maxV = max(maxV, max((p shr 16 and 0xFF).toFloat(), max((p shr 8 and 0xFF).toFloat(), (p and 0xFF).toFloat())))
                }
            }
            for (i in px.indices) {
                val r = (px[i] shr 16 and 0xFF).toFloat()
                val g = (px[i] shr 8 and 0xFF).toFloat()
                val b = (px[i] and 0xFF).toFloat()
                when (spec.kind) {
                    Kind.ISNET -> {
                        data[i] = r / maxV - MEAN[0]
                        data[plane + i] = g / maxV - MEAN[1]
                        data[2 * plane + i] = b / maxV - MEAN[2]
                    }
                    Kind.U2 -> {
                        data[i] = (r / 255f - MEAN[0]) / STD[0]
                        data[plane + i] = (g / 255f - MEAN[1]) / STD[1]
                        data[2 * plane + i] = (b / 255f - MEAN[2]) / STD[2]
                    }
                    Kind.MODNET -> {
                        data[i] = (r - 127.5f) / 127.5f
                        data[plane + i] = (g - 127.5f) / 127.5f
                        data[2 * plane + i] = (b - 127.5f) / 127.5f
                    }
                    Kind.RMBG -> {
                        data[i] = r / 255f - 0.5f
                        data[plane + i] = g / 255f - 0.5f
                        data[2 * plane + i] = b / 255f - 0.5f
                    }
                }
            }
            onProgress(0.2f)
            currentCoroutineContext().ensureActive()

            val env = OrtEnvironment.getEnvironment()
            val raw = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(data), longArrayOf(1, 3, s.toLong(), s.toLong()),
            ).use { tensor ->
                ort.run(mapOf(ort.inputNames.iterator().next() to tensor)).use { result ->
                    val fb = (result[0] as OnnxTensor).floatBuffer
                    FloatArray(fb.remaining()).also { fb.get(it) }
                }
            }
            onProgress(0.9f)

            val conf = FloatArray(plane)
            if (spec.kind == Kind.MODNET) {
                for (i in 0 until plane) conf[i] = raw[i].coerceIn(0f, 1f)
            } else {
                var lo = Float.MAX_VALUE
                var hi = -Float.MAX_VALUE
                for (i in 0 until plane) {
                    lo = minOf(lo, raw[i])
                    hi = max(hi, raw[i])
                }
                val range = max(1e-6f, hi - lo)
                for (i in 0 until plane) conf[i] = ((raw[i] - lo) / range).coerceIn(0f, 1f)
            }
            onProgress(1f)
            SubjectCutout.Mask(conf, s, s)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }
}
