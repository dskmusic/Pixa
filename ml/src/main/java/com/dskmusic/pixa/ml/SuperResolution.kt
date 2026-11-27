package com.dskmusic.pixa.ml

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

/**
 * Superresolucion con Real-ESRGAN general-x4v3 (BSD-3, 2,4 MB, empaquetado en assets) via
 * ncnn: Vulkan en la GPU y, si no hay o falla, CPU. Es una red convolucional pequena, mucho
 * mas rapida que los transformers (Swin2SR) que se usaban antes.
 *
 * El modelo siempre amplia x4; para x2 y "mismo tamano" cada tesela x4 se reduce al vuelo
 * por promedio (supermuestreo), asi que nunca se guarda mas resultado del necesario.
 *
 * La imagen se procesa por teselas con un margen que se descarta, para que no se noten las
 * costuras y la memoria no dependa del tamano de la foto. Es cancelable entre teselas.
 */
object SuperResolution {

    private const val PARAM = "sr/general_x4.param"
    private const val BIN = "sr/general_x4.bin"
    private const val NATIVE = 4

    private const val TILE = 256
    private const val MARGIN = 12

    /**
     * Mejora [src]. Con [sameSize] el resultado tiene las mismas dimensiones (mas detalle y
     * menos ruido); sin el, [factor] (2 o 4) veces el ancho y alto.
     *
     * Devuelve null si falla la inferencia o la memoria. Si la corrutina se cancela lanza
     * CancellationException. [onProgress] recibe 0..1 desde hilos de fondo.
     */
    suspend fun enhance(
        context: Context,
        src: Bitmap,
        sameSize: Boolean,
        onProgress: (Float) -> Unit = {},
        factor: Int = 2,
    ): Bitmap? = withContext(Dispatchers.Default) {
        val assets = context.applicationContext.assets
        var gpu = true
        var handle = NcnnSr.create(assets, PARAM, BIN, gpu)
        if (handle == 0L) return@withContext null
        try {
            val w = src.width
            val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            val outScale = if (sameSize) 1 else factor
            val k = NATIVE / outScale // lado del bloque a promediar
            val out = Bitmap.createBitmap(w * outScale, h * outScale, Bitmap.Config.ARGB_8888)

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
                    // Entrada = tesela + margen, recortado a la imagen (sin relleno).
                    val sx = maxOf(0, x0 - MARGIN)
                    val sy = maxOf(0, y0 - MARGIN)
                    val inW = minOf(w, x0 + cw + MARGIN) - sx
                    val inH = minOf(h, y0 + ch + MARGIN) - sy
                    val input = IntArray(inW * inH)
                    for (yy in 0 until inH) System.arraycopy(pixels, (sy + yy) * w + sx, input, yy * inW, inW)

                    var o = NcnnSr.run(handle, input, inW, inH, NATIVE)
                    if (o == null && gpu) {
                        // Vulkan fallo en este movil: se repite la tesela en CPU.
                        gpu = false
                        NcnnSr.destroy(handle)
                        handle = NcnnSr.create(assets, PARAM, BIN, false)
                        if (handle == 0L) return@withContext null
                        o = NcnnSr.run(handle, input, inW, inH, NATIVE)
                    }
                    if (o == null) return@withContext null

                    val oW = inW * NATIVE
                    val offX = (x0 - sx) * NATIVE
                    val offY = (y0 - sy) * NATIVE
                    val ow = cw * outScale
                    val oh = ch * outScale
                    val tile = IntArray(ow * oh)
                    if (k == 1) {
                        for (yy in 0 until oh) System.arraycopy(o, (offY + yy) * oW + offX, tile, yy * ow, ow)
                    } else {
                        val area = k * k
                        for (yy in 0 until oh) for (xx in 0 until ow) {
                            var r = 0; var g = 0; var b = 0
                            for (dy in 0 until k) {
                                val row = (offY + yy * k + dy) * oW + offX + xx * k
                                for (dx in 0 until k) {
                                    val p = o[row + dx]
                                    r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                                }
                            }
                            tile[yy * ow + xx] = (0xFF shl 24) or ((r + area / 2) / area shl 16) or
                                ((g + area / 2) / area shl 8) or ((b + area / 2) / area)
                        }
                    }
                    out.setPixels(tile, 0, ow, x0 * outScale, y0 * outScale, ow, oh)
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
        } finally {
            if (handle != 0L) NcnnSr.destroy(handle)
        }
    }

    private fun hasTransparency(px: IntArray): Boolean {
        for (p in px) if ((p ushr 24) != 0xFF) return true
        return false
    }
}
