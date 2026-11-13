package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Ajustes de detalle: claridad, textura, dehaze y reduccion de ruido.
 *
 * Todos los radios salen del lado mayor de la imagen y no de un numero fijo de pixeles:
 * la previsualizacion se calcula sobre una copia de ~600 kpx y "Aplicar" repite la
 * operacion a resolucion completa, asi que un radio fijo daria dos resultados distintos.
 * Devuelven un bitmap nuevo y no tocan el original.
 */
object Detail {

    /** Contraste local de radio grande, con mas peso en los tonos medios. */
    fun clarity(src: Bitmap, amount: Float): Bitmap {
        val k = amount.coerceIn(-1f, 1f)
        if (k == 0f) return BitmapUtils.mutableCopy(src)
        val radius = max(2f, max(src.width, src.height) * 0.012f)
        return localContrast(src, radius, k * 1.6f, midtoneWeighted = true)
    }

    /** Contraste local fino. Negativo suaviza (piel), positivo marca el grano de las superficies. */
    fun texture(src: Bitmap, amount: Float): Bitmap {
        val k = amount.coerceIn(-1f, 1f)
        if (k == 0f) return BitmapUtils.mutableCopy(src)
        val radius = max(1f, max(src.width, src.height) * 0.0015f)
        return localContrast(src, radius, k * 2.2f, midtoneWeighted = false)
    }

    /** orig + k * (orig - desenfocado), opcionalmente mas fuerte en los medios tonos. */
    private fun localContrast(src: Bitmap, radius: Float, k: Float, midtoneWeighted: Boolean): Bitmap {
        val w = src.width
        val h = src.height
        val blurred = Blur.blur(src, radius)
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        blurred.getPixels(b, 0, w, 0, 0, w, h)
        blurred.recycle()

        Parallel.rows(a.size) { i0, i1 ->
            for (i in i0 until i1) {
            val pa = a[i]
            val al = pa ushr 24 and 0xFF
            if (al == 0) continue
            val pb = b[i]
            val r = pa ushr 16 and 0xFF
            val g = pa ushr 8 and 0xFF
            val bl = pa and 0xFF
            var gain = k
            if (midtoneWeighted) {
                val l = (0.2126f * r + 0.7152f * g + 0.0722f * bl) / 255f
                val d = 2f * l - 1f
                gain *= 1f - d * d * 0.8f
            }
            val nr = (r + (r - (pb ushr 16 and 0xFF)) * gain).roundToInt().coerceIn(0, 255)
            val ng = (g + (g - (pb ushr 8 and 0xFF)) * gain).roundToInt().coerceIn(0, 255)
            val nb = (bl + (bl - (pb and 0xFF)) * gain).roundToInt().coerceIn(0, 255)
            a[i] = (al shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Dehaze. Positivo: estima la neblina como el percentil bajo del canal minimo y la
     * resta, estirando el resto. Negativo: mezcla con un velo claro y desaturado.
     */
    fun dehaze(src: Bitmap, amount: Float): Bitmap {
        val k = amount.coerceIn(-1f, 1f)
        if (k == 0f) return BitmapUtils.mutableCopy(src)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        if (k > 0f) {
            // Histograma del canal minimo: con el percentil 0,5 % ignoramos pixeles sueltos.
            val hist = IntArray(256)
            var n = 0
            val step = max(1, px.size / 60_000)
            var i = 0
            while (i < px.size) {
                val p = px[i]
                if ((p ushr 24) != 0) {
                    hist[min(p ushr 16 and 0xFF, min(p ushr 8 and 0xFF, p and 0xFF))]++
                    n++
                }
                i += step
            }
            var acc = 0
            var haze = 0
            val target = (n * 0.005f).toInt()
            while (haze < 255 && acc + hist[haze] <= target) {
                acc += hist[haze]
                haze++
            }
            // Una foto limpia casi no tiene velo medible: se fija un minimo para que el
            // deslizador haga algo visible en cualquier imagen.
            val veil = max(haze.toFloat(), 26f) * k * 0.9f
            val scale = 255f / (255f - veil)
            Parallel.rows(px.size) { i0, i1 ->
                for (j in i0 until i1) {
                val p = px[j]
                val al = p ushr 24
                if (al == 0) continue
                val r = ((p ushr 16 and 0xFF) - veil) * scale
                val g = ((p ushr 8 and 0xFF) - veil) * scale
                val b = ((p and 0xFF) - veil) * scale
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                // Quitar velo apaga los colores: se devuelve parte de la saturacion.
                val sat = 1f + 0.25f * k
                px[j] = (al shl 24) or
                    (clamp(lum + (r - lum) * sat) shl 16) or
                    (clamp(lum + (g - lum) * sat) shl 8) or
                    clamp(lum + (b - lum) * sat)
                }
            }
        } else {
            val f = -k
            val mix = f * 0.4f
            val fog = 235f
            Parallel.rows(px.size) { i0, i1 ->
                for (j in i0 until i1) {
                val p = px[j]
                val al = p ushr 24
                if (al == 0) continue
                val r = (p ushr 16 and 0xFF).toFloat()
                val g = (p ushr 8 and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val desat = f * 0.3f
                val nr = (r + (lum - r) * desat) * (1f - mix) + fog * mix
                val ng = (g + (lum - g) * desat) * (1f - mix) + fog * mix
                val nb = (b + (lum - b) * desat) * (1f - mix) + fog * mix
                px[j] = (al shl 24) or (clamp(nr) shl 16) or (clamp(ng) shl 8) or clamp(nb)
                }
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Reduccion de ruido que respeta bordes: mezcla cada pixel con su version desenfocada
     * y el peso cae rapido cuando la diferencia es grande (un borde real, no ruido).
     */
    fun denoise(src: Bitmap, amount: Float): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        if (k == 0f) return BitmapUtils.mutableCopy(src)
        val w = src.width
        val h = src.height
        val scale = max(1f, max(w, h) / 2000f)
        val blurred = Blur.blur(src, (1f + 2f * k) * scale)
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        blurred.getPixels(b, 0, w, 0, 0, w, h)
        blurred.recycle()

        val sigma = 14f + 34f * k
        val strength = min(1f, k * 1.25f)
        // Peso por diferencia maxima de canal: tabla para no llamar a exp() por pixel.
        val weight = FloatArray(256) { d -> exp(-(d * d) / (sigma * sigma)) * strength }
        Parallel.rows(a.size) { i0, i1 ->
            for (i in i0 until i1) {
            val pa = a[i]
            val al = pa ushr 24
            if (al == 0) continue
            val pb = b[i]
            val ra = pa ushr 16 and 0xFF
            val ga = pa ushr 8 and 0xFF
            val ba = pa and 0xFF
            val rb = pb ushr 16 and 0xFF
            val gb = pb ushr 8 and 0xFF
            val bb = pb and 0xFF
            val d = max(abs(ra - rb), max(abs(ga - gb), abs(ba - bb)))
            val t = weight[d]
            a[i] = (al shl 24) or
                ((ra + ((rb - ra) * t).roundToInt()) shl 16) or
                ((ga + ((gb - ga) * t).roundToInt()) shl 8) or
                (ba + ((bb - ba) * t).roundToInt())
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Correccion de distorsion radial de lente (modelo de un coeficiente). Cada pixel de
     * salida toma el de la posicion `centro + d * (1 + k r^2)`, con `r` normalizado para que
     * la esquina valga 1. Con k > 0 se amplia un poco para no dejar bordes sin datos;
     * con k < 0 el muestreo siempre cae dentro de la imagen.
     */
    fun lens(src: Bitmap, amount: Float): Bitmap {
        val k = amount.coerceIn(-1f, 1f) * 0.35f
        if (k == 0f) return BitmapUtils.mutableCopy(src)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val cx = (w - 1) / 2f
        val cy = (h - 1) / 2f
        val norm = 1f / (cx * cx + cy * cy)
        val zoom = 1f + max(0f, k)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx
                val f = (1f + k * (dx * dx + dy * dy) * norm) / zoom
                val sx = (cx + dx * f).coerceIn(0f, (w - 1).toFloat())
                val sy = (cy + dy * f).coerceIn(0f, (h - 1).toFloat())
                val x0 = sx.toInt()
                val y0 = sy.toInt()
                val x1 = min(x0 + 1, w - 1)
                val y1 = min(y0 + 1, h - 1)
                val fx = sx - x0
                val fy = sy - y0
                val a = px[y0 * w + x0]
                val b = px[y0 * w + x1]
                val c = px[y1 * w + x0]
                val d = px[y1 * w + x1]
                out[y * w + x] = (bilerp(a ushr 24, b ushr 24, c ushr 24, d ushr 24, fx, fy) shl 24) or
                    (bilerp(a ushr 16 and 0xFF, b ushr 16 and 0xFF, c ushr 16 and 0xFF, d ushr 16 and 0xFF, fx, fy) shl 16) or
                    (bilerp(a ushr 8 and 0xFF, b ushr 8 and 0xFF, c ushr 8 and 0xFF, d ushr 8 and 0xFF, fx, fy) shl 8) or
                    bilerp(a and 0xFF, b and 0xFF, c and 0xFF, d and 0xFF, fx, fy)
            }
            }
        }
        val result = BitmapUtils.create(w, h)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun bilerp(a: Int, b: Int, c: Int, d: Int, fx: Float, fy: Float): Int {
        val top = a + (b - a) * fx
        val bottom = c + (d - c) * fx
        return (top + (bottom - top) * fy).roundToInt().coerceIn(0, 255)
    }

    private fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)
}
