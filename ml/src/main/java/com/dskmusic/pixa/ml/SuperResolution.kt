package com.dskmusic.pixa.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * Superresolucion x2 con Swin2SR "lightweight" (Apache 2.0) via ONNX Runtime, que ya
 * viaja en la app para el recorte de fondo: no hace falta TensorFlow Lite ni otra
 * biblioteca. El modelo (8 MB) se descarga la primera vez, como el de U2-Net.
 *
 * La imagen se procesa por teselas con un margen que se descarta, para que no se noten
 * las costuras y la memoria de trabajo no dependa del tamano de la foto. Cada tesela se
 * manda al modelo con alto y ancho multiplos de 8 (ventana de atencion del modelo); el
 * borde se completa repitiendo el ultimo pixel.
 *
 * No hay limite de tamano: el tiempo crece con los pixeles, y por eso la operacion es
 * cancelable entre teselas. En modo "mismo tamano" cada tesela se reduce al vuelo, asi
 * que nunca se guarda el resultado x2 entero en memoria.
 */
object SuperResolution {

    private const val MODEL_FILE = "swin2sr_lightweight_x2.onnx"
    private const val MODEL_URL =
        "https://huggingface.co/Xenova/swin2SR-lightweight-x2-64/resolve/main/onnx/model.onnx"

    // x4: modelo de imagenes reales (ruido y compresion), el doble de pesado por tesela.
    private const val MODEL_FILE_X4 = "swin2sr_realworld_x4.onnx"
    private const val MODEL_URL_X4 =
        "https://huggingface.co/Xenova/swin2SR-realworld-sr-x4-64-bsrgan-psnr/resolve/main/onnx/model.onnx"

    private const val TILE = 128
    private const val MARGIN = 8

    fun isModelReady(context: Context, factor: Int = 2): Boolean =
        ModelDownloader.existing(context, if (factor == 4) MODEL_FILE_X4 else MODEL_FILE) != null

    /** Descarga el modelo si falta y devuelve la sesion compartida de [AiRuntime]. */
    private suspend fun sessionOrNull(context: Context, factor: Int, onDownload: (Float) -> Unit): OrtSession? {
        val file = if (factor == 4) MODEL_FILE_X4 else MODEL_FILE
        val url = if (factor == 4) MODEL_URL_X4 else MODEL_URL
        ModelDownloader.ensure(context, url, file, onDownload) ?: return null
        return AiRuntime.session(context, file)
    }

    /**
     * Mejora [src] con el modelo. Con [sameSize] el resultado tiene las mismas dimensiones
     * (mas detalle y menos ruido); sin el, el doble de ancho y alto.
     *
     * Devuelve null si el modelo no esta disponible (sin red la primera vez) o falla la
     * inferencia o la memoria. Si la corrutina se cancela lanza CancellationException.
     * [onDownloadProgress] y [onProgress] reciben 0..1 desde hilos de fondo; la segunda
     * empieza cuando acaba la descarga.
     */
    suspend fun enhance(
        context: Context,
        src: Bitmap,
        sameSize: Boolean,
        onDownloadProgress: (Float) -> Unit = {},
        onProgress: (Float) -> Unit = {},
        /** 2 o 4: multiplo de ampliacion. El modo [sameSize] solo existe con 2. */
        factor: Int = 2,
    ): Bitmap? = withContext(Dispatchers.Default) {
        val ort = sessionOrNull(context.applicationContext, factor, onDownloadProgress)
            ?: return@withContext null
        try {
            val w = src.width
            val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            val scale = if (sameSize) 1 else factor
            val out = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)

            val env = OrtEnvironment.getEnvironment()
            val inputName = ort.inputNames.iterator().next()
            val tilesX = (w + TILE - 1) / TILE
            val tilesY = (h + TILE - 1) / TILE
            val total = tilesX * tilesY
            var done = 0
            onProgress(0f)

            for (ty in 0 until tilesY) {
                for (tx in 0 until tilesX) {
                    currentCoroutineContext().ensureActive()
                    val x0 = tx * TILE
                    val y0 = ty * TILE
                    val cw = minOf(TILE, w - x0)
                    val ch = minOf(TILE, h - y0)
                    val inW = roundUp8(cw + 2 * MARGIN)
                    val inH = roundUp8(ch + 2 * MARGIN)
                    val plane = inW * inH
                    val data = FloatArray(3 * plane)
                    for (yy in 0 until inH) {
                        val sy = (y0 - MARGIN + yy).coerceIn(0, h - 1)
                        for (xx in 0 until inW) {
                            val sx = (x0 - MARGIN + xx).coerceIn(0, w - 1)
                            val p = pixels[sy * w + sx]
                            val i = yy * inW + xx
                            data[i] = ((p shr 16) and 0xFF) / 255f
                            data[plane + i] = ((p shr 8) and 0xFF) / 255f
                            data[2 * plane + i] = (p and 0xFF) / 255f
                        }
                    }

                    val shape = longArrayOf(1, 3, inH.toLong(), inW.toLong())
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { tensor ->
                        ort.run(mapOf(inputName to tensor)).use { result ->
                            @Suppress("UNCHECKED_CAST")
                            val o = result[0].value as Array<Array<Array<FloatArray>>>
                            val off = MARGIN * factor
                            if (sameSize) {
                                // Reduccion 2x2 -> 1 por promedio: es el "supermuestreo" que
                                // da la nitidez, sin guardar nunca el resultado doble.
                                val tile = IntArray(cw * ch)
                                for (yy in 0 until ch) {
                                    val ra = o[0][0][off + 2 * yy]; val rb = o[0][0][off + 2 * yy + 1]
                                    val ga = o[0][1][off + 2 * yy]; val gb = o[0][1][off + 2 * yy + 1]
                                    val ba = o[0][2][off + 2 * yy]; val bb = o[0][2][off + 2 * yy + 1]
                                    for (xx in 0 until cw) {
                                        val ix = off + 2 * xx
                                        val r = (ra[ix] + ra[ix + 1] + rb[ix] + rb[ix + 1]) * 0.25f
                                        val g = (ga[ix] + ga[ix + 1] + gb[ix] + gb[ix + 1]) * 0.25f
                                        val b = (ba[ix] + ba[ix + 1] + bb[ix] + bb[ix + 1]) * 0.25f
                                        tile[yy * cw + xx] = pack(r, g, b)
                                    }
                                }
                                out.setPixels(tile, 0, cw, x0, y0, cw, ch)
                            } else {
                                val ow = cw * factor
                                val oh = ch * factor
                                val tile = IntArray(ow * oh)
                                for (yy in 0 until oh) {
                                    val rr = o[0][0][off + yy]
                                    val gg = o[0][1][off + yy]
                                    val bb = o[0][2][off + yy]
                                    for (xx in 0 until ow) {
                                        tile[yy * ow + xx] = pack(rr[off + xx], gg[off + xx], bb[off + xx])
                                    }
                                }
                                out.setPixels(tile, 0, ow, x0 * factor, y0 * factor, ow, oh)
                            }
                        }
                    }
                    done++
                    onProgress(done / total.toFloat())
                }
            }

            // El modelo trabaja en RGB: si la imagen tenia transparencia, el alfa original
            // (reescalado si hace falta) se aplica al resultado.
            if (src.hasAlpha() && hasTransparency(pixels)) {
                val alpha = if (sameSize) src else Bitmap.createScaledBitmap(src, w * factor, h * factor, true)
                val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
                Canvas(out).drawBitmap(alpha, 0f, 0f, paint)
                if (alpha !== src) alpha.recycle()
            }
            out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Incluye OutOfMemoryError: el llamante lo cuenta como fallo sin mas.
            null
        }
    }

    private fun pack(r: Float, g: Float, b: Float): Int {
        val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
        val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun roundUp8(v: Int): Int = (v + 7) / 8 * 8

    private fun hasTransparency(px: IntArray): Boolean {
        for (p in px) if ((p ushr 24) != 0xFF) return true
        return false
    }
}
