package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.ml.DepthMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Efectos que usan un mapa de profundidad ([DepthMap], 1 = cerca, 0 = lejos): desenfoque
 * de fondo tipo retrato y niebla que crece con la distancia.
 */
object DepthFx {

    /**
     * Profundidad del sujeto: mediana de la zona central. Es lo que casi siempre esta
     * enfocado en una foto; si no lo esta, el usuario siempre puede elegir otro efecto.
     */
    fun autoFocus(depth: DepthMap): Float {
        val x0 = depth.width * 35 / 100
        val x1 = depth.width * 65 / 100
        val y0 = depth.height * 30 / 100
        val y1 = depth.height * 70 / 100
        val vals = ArrayList<Float>((x1 - x0) * (y1 - y0))
        for (y in y0 until y1) for (x in x0 until x1) vals.add(depth.values[y * depth.width + x])
        vals.sort()
        return if (vals.isEmpty()) 0.5f else vals[vals.size / 2]
    }

    /**
     * Desenfoque de fondo: cuanto mas lejos de [focus] (en profundidad), mas desenfoque. Se
     * interpola entre la imagen nitida y dos versiones desenfocadas, con radios relativos
     * al tamano de la imagen para que la vista previa y el resultado coincidan.
     */
    fun portraitBlur(src: Bitmap, depth: DepthMap, strength: Float, focus: Float): Bitmap {
        val k = strength.coerceIn(0f, 1f)
        val w = src.width
        val h = src.height
        val side = max(w, h).toFloat()
        val mid = Blur.blur(src, max(2f, side * 0.006f * (0.4f + k)))
        val big = Blur.blur(src, max(3f, side * 0.016f * (0.4f + k)))
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        val c = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        mid.getPixels(b, 0, w, 0, 0, w, h)
        big.getPixels(c, 0, w, 0, 0, w, h)
        mid.recycle()
        big.recycle()
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                val i = y * w + x
                val d = abs(depth.sample(x + 0.5f, y + 0.5f, w, h) - focus)
                // Zona de foco sin desenfoque y subida suave despues.
                val t = ((d - 0.06f) / 0.30f).coerceIn(0f, 1f) * 2f
                a[i] = if (t <= 1f) mixPx(a[i], b[i], t) else mixPx(b[i], c[i], t - 1f)
            }
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    /** Niebla: los pixeles lejanos se funden con un gris claro azulado. */
    fun fog(src: Bitmap, depth: DepthMap, strength: Float): Bitmap {
        val k = strength.coerceIn(0f, 1f)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val fog = (0xFF shl 24) or (226 shl 16) or (232 shl 8) or 240
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                val i = y * w + x
                val far = 1f - depth.sample(x + 0.5f, y + 0.5f, w, h)
                px[i] = mixPx(px[i], fog, far * far * k * 0.9f)
            }
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun mixPx(p: Int, q: Int, t: Float): Int {
        if (t <= 0f) return p
        val a = p ushr 24
        val r = ((p shr 16 and 0xFF) + ((q shr 16 and 0xFF) - (p shr 16 and 0xFF)) * t).roundToInt()
        val g = ((p shr 8 and 0xFF) + ((q shr 8 and 0xFF) - (p shr 8 and 0xFF)) * t).roundToInt()
        val b = ((p and 0xFF) + ((q and 0xFF) - (p and 0xFF)) * t).roundToInt()
        return (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }
}
