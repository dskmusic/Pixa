package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Desenfoque en CPU.
 *
 * RenderScript esta retirado y RenderEffect solo existe desde API 31, asi que el motor
 * usa un box blur separable de tres pasadas, que es una aproximacion muy buena a una
 * gaussiana y funciona igual en todas las versiones. Se trabaja en alfa premultiplicado
 * para que los bordes contra zonas transparentes no generen halos.
 *
 * ponytail: O(n) por pasada e independiente del radio; si algun dia hace falta mas
 * velocidad en imagenes enormes, el salto natural es RenderEffect en API 31+.
 */
object Blur {

    /** Desenfoca una copia de [src]. [radius] en pixeles; 0 devuelve una copia. */
    fun blur(src: Bitmap, radius: Float): Bitmap {
        val r = radius.roundToInt()
        if (r <= 0) return BitmapUtils.mutableCopy(src)
        val w = src.width
        val h = src.height
        if (w < 2 || h < 2) return BitmapUtils.mutableCopy(src)
        // Se lee una sola vez de [src] y se escribe en un bitmap nuevo (antes: copia + lectura).
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        blurPixels(pixels, w, h, r)
        val out = BitmapUtils.create(w, h)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** Desenfoca [bitmap] modificandolo. Debe ser mutable. */
    fun blurInPlace(bitmap: Bitmap, radius: Int) {
        if (radius <= 0) return
        val w = bitmap.width
        val h = bitmap.height
        if (w < 2 || h < 2) return
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        blurPixels(pixels, w, h, radius)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /** Nucleo del desenfoque sobre un array ARGB: lo deja modificado. */
    private fun blurPixels(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val n = w * h
        // Canales separados en alfa premultiplicado.
        val a = IntArray(n)
        val rr = IntArray(n)
        val gg = IntArray(n)
        val bb = IntArray(n)
        Parallel.rows(n) { i0, i1 ->
            for (i in i0 until i1) {
                val p = pixels[i]
                val al = (p ushr 24) and 0xFF
                a[i] = al
                rr[i] = ((p ushr 16) and 0xFF) * al / 255
                gg[i] = ((p ushr 8) and 0xFF) * al / 255
                bb[i] = (p and 0xFF) * al / 255
            }
        }

        // Tres pasadas de box blur aproximan una gaussiana (teorema del limite central).
        val radii = boxesForGauss(radius.toFloat(), 3)
        val tmp = IntArray(n)
        for (pass in 0 until 3) {
            val rad = radii[pass]
            if (rad <= 0) continue
            boxBlur(a, tmp, w, h, rad)
            boxBlur(rr, tmp, w, h, rad)
            boxBlur(gg, tmp, w, h, rad)
            boxBlur(bb, tmp, w, h, rad)
        }

        Parallel.rows(n) { i0, i1 ->
            for (i in i0 until i1) {
                val al = a[i].coerceIn(0, 255)
                if (al == 0) {
                    pixels[i] = 0
                } else {
                    val r8 = (rr[i] * 255 / al).coerceIn(0, 255)
                    val g8 = (gg[i] * 255 / al).coerceIn(0, 255)
                    val b8 = (bb[i] * 255 / al).coerceIn(0, 255)
                    pixels[i] = (al shl 24) or (r8 shl 16) or (g8 shl 8) or b8
                }
            }
        }
    }

    /**
     * Radios de las tres cajas que mejor aproximan una gaussiana de sigma dada.
     * Formula estandar de Ivan Kutskir.
     */
    private fun boxesForGauss(sigma: Float, n: Int): IntArray {
        val wIdeal = kotlin.math.sqrt((12.0 * sigma * sigma / n) + 1.0)
        var wl = wIdeal.toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val mIdeal = (12.0 * sigma * sigma - n * wl * wl - 4.0 * n * wl - 3.0 * n) /
            (-4.0 * wl - 4.0)
        val m = mIdeal.roundToInt()
        return IntArray(n) { i -> (((if (i < m) wl else wu) - 1) / 2).coerceAtLeast(0) }
    }

    /** Box blur separable con suma deslizante: coste independiente del radio. */
    private fun boxBlur(ch: IntArray, tmp: IntArray, w: Int, h: Int, radius: Int) {
        boxBlurH(ch, tmp, w, h, radius)
        boxBlurV(tmp, ch, w, h, radius)
    }

    /** Pasada horizontal: cada fila es independiente, asi que se reparten entre nucleos. */
    private fun boxBlurH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        // Interior: x - r >= 0 y x + r + 1 <= w - 1, sin comprobar limites.
        val midStart = r
        val midEnd = w - r - 1
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
                val row = y * w
                var sum = 0
                // Precarga de la ventana con bordes replicados.
                for (i in -r..r) sum += src[row + i.coerceIn(0, w - 1)]
                val firstEnd = min(w, midStart)
                var x = 0
                while (x < firstEnd) {
                    dst[row + x] = sum / div
                    val add = src[row + (x + r + 1).coerceIn(0, w - 1)]
                    val sub = src[row + (x - r).coerceIn(0, w - 1)]
                    sum += add - sub
                    x++
                }
                if (midEnd > x) {
                    while (x < midEnd) {
                        dst[row + x] = sum / div
                        sum += src[row + x + r + 1] - src[row + x - r]
                        x++
                    }
                }
                while (x < w) {
                    dst[row + x] = sum / div
                    val add = src[row + (x + r + 1).coerceIn(0, w - 1)]
                    val sub = src[row + (x - r).coerceIn(0, w - 1)]
                    sum += add - sub
                    x++
                }
            }
        }
    }

    /**
     * Pasada vertical por filas (acceso contiguo en memoria): una suma por columna que se
     * desliza hacia abajo. Las columnas se reparten entre nucleos en bandas.
     */
    private fun boxBlurV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        Parallel.rows(w) { x0, x1 ->
            val width = x1 - x0
            val sums = IntArray(width)
            for (i in -r..r) {
                val base = i.coerceIn(0, h - 1) * w + x0
                for (k in 0 until width) sums[k] += src[base + k]
            }
            for (y in 0 until h) {
                val rowBase = y * w + x0
                for (k in 0 until width) dst[rowBase + k] = sums[k] / div
                val addBase = (y + r + 1).coerceIn(0, h - 1) * w + x0
                val subBase = (y - r).coerceIn(0, h - 1) * w + x0
                for (k in 0 until width) sums[k] += src[addBase + k] - src[subBase + k]
            }
        }
    }

    /**
     * Desenfoque de movimiento en una direccion (requisito 13).
     * [length] en pixeles, [angleDeg] 0 = horizontal.
     */
    fun motionBlur(src: Bitmap, length: Int, angleDeg: Float): Bitmap {
        val n = max(1, length)
        val out = BitmapUtils.create(src.width, src.height)
        val w = src.width
        val h = src.height
        val inPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)
        val outPx = IntArray(w * h)

        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = kotlin.math.cos(rad).toFloat()
        val dy = kotlin.math.sin(rad).toFloat()

        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                var sa = 0; var sr = 0; var sg = 0; var sb = 0; var count = 0
                for (t in -n / 2..n / 2) {
                    val sx = (x + dx * t).roundToInt()
                    val sy = (y + dy * t).roundToInt()
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue
                    val p = inPx[sy * w + sx]
                    val al = (p ushr 24) and 0xFF
                    sa += al
                    sr += ((p ushr 16) and 0xFF) * al / 255
                    sg += ((p ushr 8) and 0xFF) * al / 255
                    sb += (p and 0xFF) * al / 255
                    count++
                }
                if (count == 0) { outPx[y * w + x] = inPx[y * w + x]; continue }
                val al = sa / count
                outPx[y * w + x] = if (al == 0) 0 else {
                    (al shl 24) or
                        (((sr / count) * 255 / al).coerceIn(0, 255) shl 16) or
                        (((sg / count) * 255 / al).coerceIn(0, 255) shl 8) or
                        ((sb / count) * 255 / al).coerceIn(0, 255)
                }
            }
            }
        }
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Desenfoque radial: se promedia a lo largo de rayos que salen del centro
     * (requisito 13). [amount] 0..1.
     */
    fun radialBlur(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = BitmapUtils.create(w, h)
        val inPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)
        val outPx = IntArray(w * h)
        val cx = w / 2f
        val cy = h / 2f
        val steps = (4 + amount.coerceIn(0f, 1f) * 18f).toInt()
        val strength = amount.coerceIn(0f, 1f) * 0.18f

        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                var sa = 0; var sr = 0; var sg = 0; var sb = 0; var count = 0
                for (s in 0 until steps) {
                    val k = 1f - strength * s / steps
                    val sx = (cx + (x - cx) * k).roundToInt()
                    val sy = (cy + (y - cy) * k).roundToInt()
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue
                    val p = inPx[sy * w + sx]
                    val al = (p ushr 24) and 0xFF
                    sa += al
                    sr += ((p ushr 16) and 0xFF) * al / 255
                    sg += ((p ushr 8) and 0xFF) * al / 255
                    sb += (p and 0xFF) * al / 255
                    count++
                }
                if (count == 0) { outPx[y * w + x] = inPx[y * w + x]; continue }
                val al = sa / count
                outPx[y * w + x] = if (al == 0) 0 else {
                    (al shl 24) or
                        (((sr / count) * 255 / al).coerceIn(0, 255) shl 16) or
                        (((sg / count) * 255 / al).coerceIn(0, 255) shl 8) or
                        ((sb / count) * 255 / al).coerceIn(0, 255)
                }
            }
            }
        }
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }
}
