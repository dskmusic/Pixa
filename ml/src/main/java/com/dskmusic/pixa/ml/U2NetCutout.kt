package com.dskmusic.pixa.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * Tercer metodo de recorte de fondo (requisito del usuario): recorte general, no solo de
 * personas. U2-Netp (Apache 2.0, igual que lo usa el propio rembg), via ONNX Runtime. El
 * modelo (4,6 MB) ya no viaja en el APK: se descarga bajo demanda (o en silencio al
 * arrancar, ver [prefetch]) y se queda en el almacenamiento propio de la app. Mas lento y
 * de menor resolucion interna (320x320 fijo) que los otros dos metodos, pero sirve con
 * cualquier sujeto: ropa, producto, animal...
 */
object U2NetCutout {

    private const val MODEL_FILE = "u2netp.onnx"
    private const val MODEL_URL =
        "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx"
    private const val INPUT_SIZE = 320
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    @Volatile
    private var session: OrtSession? = null
    private val buildLock = Mutex()

    /** Deja el modelo descargado sin construir nada todavia (requisito del usuario: en
     * silencio al abrir la app, para que el primer uso real no necesite red). */
    suspend fun prefetch(context: Context) {
        ModelDownloader.ensure(context, MODEL_URL, MODEL_FILE)
    }

    private suspend fun sessionOrNull(context: Context): OrtSession? {
        session?.let { return it }
        return buildLock.withLock {
            session?.let { return@withLock it }
            val file = ModelDownloader.ensure(context, MODEL_URL, MODEL_FILE)
                ?: return@withLock null
            runCatching {
                OrtEnvironment.getEnvironment().createSession(file.readBytes())
            }.getOrNull()?.also { session = it }
        }
    }

    /** Mascara del sujeto, o null si el modelo no se pudo cargar o fallo la inferencia. */
    suspend fun segment(context: Context, bitmap: Bitmap): SubjectCutout.Mask? =
        withContext(Dispatchers.Default) {
            val ort = sessionOrNull(context) ?: return@withContext null
            runCatching {
                val small = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
                val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
                small.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
                if (small !== bitmap) small.recycle()

                // NCHW: un plano entero por canal, no RGB intercalado.
                val plane = INPUT_SIZE * INPUT_SIZE
                val data = FloatArray(3 * plane)
                for (i in pixels.indices) {
                    val p = pixels[i]
                    data[i] = (((p shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]
                    data[plane + i] = (((p shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]
                    data[2 * plane + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
                }

                val env = OrtEnvironment.getEnvironment()
                val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
                OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { tensor ->
                    val inputName = ort.inputNames.iterator().next()
                    ort.run(mapOf(inputName to tensor)).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val output = result[0].value as Array<Array<Array<FloatArray>>>
                        val raw = output[0][0]
                        var min = Float.MAX_VALUE
                        var max = -Float.MAX_VALUE
                        for (row in raw) for (v in row) {
                            if (v < min) min = v
                            if (v > max) max = v
                        }
                        val range = (max - min).coerceAtLeast(1e-6f)
                        val saliency = FloatArray(plane)
                        for (y in 0 until INPUT_SIZE) {
                            for (x in 0 until INPUT_SIZE) {
                                saliency[y * INPUT_SIZE + x] = (raw[y][x] - min) / range
                            }
                        }
                        val resized = resizeBilinear(
                            saliency, INPUT_SIZE, INPUT_SIZE, bitmap.width, bitmap.height,
                        )
                        SubjectCutout.Mask(resized, bitmap.width, bitmap.height)
                    }
                }
            }.getOrNull()
        }

    /** Reescalado bilineal simple de una mascara de valores 0..1 (sin libreria de imagen). */
    private fun resizeBilinear(src: FloatArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): FloatArray {
        if (srcW == dstW && srcH == dstH) return src
        val out = FloatArray(dstW * dstH)
        val xRatio = srcW.toFloat() / dstW
        val yRatio = srcH.toFloat() / dstH
        for (y in 0 until dstH) {
            val sy = (y * yRatio).coerceIn(0f, (srcH - 1).toFloat())
            val y0 = sy.toInt()
            val y1 = (y0 + 1).coerceAtMost(srcH - 1)
            val fy = sy - y0
            for (x in 0 until dstW) {
                val sx = (x * xRatio).coerceIn(0f, (srcW - 1).toFloat())
                val x0 = sx.toInt()
                val x1 = (x0 + 1).coerceAtMost(srcW - 1)
                val fx = sx - x0
                val top = src[y0 * srcW + x0] * (1 - fx) + src[y0 * srcW + x1] * fx
                val bottom = src[y1 * srcW + x0] * (1 - fx) + src[y1 * srcW + x1] * fx
                out[y * dstW + x] = top * (1 - fy) + bottom * fy
            }
        }
        return out
    }
}
