package com.dskmusic.pixa.core.selection

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lazo magnetico: ajusta un trazo a mano alzada a los bordes de la imagen.
 *
 * Cada punto (remuestreado cada pocos pixeles) se mueve al pixel de mayor gradiente
 * (Sobel sobre la luminancia) dentro de [radius]; un pequeno castigo por distancia evita
 * saltos al borde de al lado. Despues se suaviza para quitar dientes.
 */
object MagneticLasso {

    /** [coords] y el resultado son pares x,y planos en coordenadas del documento. */
    fun snap(src: Bitmap, coords: FloatArray, radius: Int): FloatArray {
        val pts = resample(coords, 4f)
        if (pts.size < 6) return coords
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in pts.indices step 2) {
            minX = min(minX, pts[i]); maxX = max(maxX, pts[i])
            minY = min(minY, pts[i + 1]); maxY = max(maxY, pts[i + 1])
        }
        val pad = radius + 2
        val x0 = (minX.toInt() - pad).coerceIn(0, src.width - 1)
        val y0 = (minY.toInt() - pad).coerceIn(0, src.height - 1)
        val x1 = (maxX.toInt() + pad).coerceIn(x0 + 1, src.width)
        val y1 = (maxY.toInt() + pad).coerceIn(y0 + 1, src.height)
        val bw = x1 - x0
        val bh = y1 - y0
        val px = IntArray(bw * bh)
        src.getPixels(px, 0, bw, x0, y0, bw, bh)
        val luma = FloatArray(bw * bh) {
            val c = px[it]
            0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }
        fun l(x: Int, y: Int) = luma[y.coerceIn(0, bh - 1) * bw + x.coerceIn(0, bw - 1)]
        fun grad(x: Int, y: Int): Float {
            val gx = l(x + 1, y - 1) + 2 * l(x + 1, y) + l(x + 1, y + 1) -
                l(x - 1, y - 1) - 2 * l(x - 1, y) - l(x - 1, y + 1)
            val gy = l(x - 1, y + 1) + 2 * l(x, y + 1) + l(x + 1, y + 1) -
                l(x - 1, y - 1) - 2 * l(x, y - 1) - l(x + 1, y - 1)
            return abs(gx) + abs(gy)
        }

        val out = FloatArray(pts.size)
        for (i in pts.indices step 2) {
            val cx = pts[i].roundToInt() - x0
            val cy = pts[i + 1].roundToInt() - y0
            var best = -1f
            var bx = cx
            var by = cy
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val d = hypot(dx.toFloat(), dy.toFloat())
                if (d > radius) continue
                val score = grad(cx + dx, cy + dy) - d * 4f
                if (score > best) {
                    best = score; bx = cx + dx; by = cy + dy
                }
            }
            out[i] = (bx + x0).toFloat()
            out[i + 1] = (by + y0).toFloat()
        }
        // Media movil de 3 puntos (extremos incluidos como estan).
        val smooth = out.copyOf()
        var i = 2
        while (i + 3 < out.size) {
            smooth[i] = (out[i - 2] + out[i] + out[i + 2]) / 3f
            smooth[i + 1] = (out[i - 1] + out[i + 1] + out[i + 3]) / 3f
            i += 2
        }
        return smooth
    }

    private fun resample(c: FloatArray, step: Float): FloatArray {
        val out = ArrayList<Float>()
        var lx = c[0]
        var ly = c[1]
        out.add(lx); out.add(ly)
        var i = 2
        while (i + 1 < c.size) {
            if (hypot(c[i] - lx, c[i + 1] - ly) >= step) {
                lx = c[i]; ly = c[i + 1]
                out.add(lx); out.add(ly)
            }
            i += 2
        }
        return out.toFloatArray()
    }
}
