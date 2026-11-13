package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Correccion geometrica: enderezar, perspectiva vertical y horizontal y distorsion de lente.
 *
 * [rotation] en grados; el resto de -1 a 1. Cada pixel de salida se muestrea del origen
 * aplicando, en este orden: zoom, trapecio, giro y lente. Esa composicion equivale a
 * corregir primero la lente, despues girar y por ultimo la perspectiva, que es el orden en
 * el que se estiman automaticamente.
 */
data class GeoParams(
    val rotation: Float = 0f,
    val keystoneV: Float = 0f,
    val keystoneH: Float = 0f,
    val lens: Float = 0f,
) {
    val isIdentity: Boolean
        get() = rotation == 0f && keystoneV == 0f && keystoneH == 0f && lens == 0f
}

object GeoCorrect {

    private const val LENS_K = 0.35f
    private const val KEY = 0.3f
    private const val PROXY_SIDE = 640

    // ----------------------------------------------------------------------------------
    // Transformacion
    // ----------------------------------------------------------------------------------

    /** Mapa salida -> origen para una imagen de [w] x [h]. */
    private class Mapper(val w: Int, val h: Int, p: GeoParams, val zoom: Float) {
        val cx = (w - 1) / 2f
        val cy = (h - 1) / 2f
        private val hw = max(cx, 1f)
        private val hh = max(cy, 1f)
        private val rad = p.rotation * PI.toFloat() / 180f
        private val c = cos(rad)
        private val s = sin(rad)
        private val kv = p.keystoneV
        private val kh = p.keystoneH
        private val k = p.lens * LENS_K
        private val r2n = 1f / max(1f, cx * cx + cy * cy)

        /** Coordenadas del origen para el pixel de salida ([xo], [yo]); el resultado va a [out]. */
        fun map(xo: Float, yo: Float, out: FloatArray) {
            var x = (xo - cx) / zoom
            var y = (yo - cy) / zoom
            val sx = 1f + KEY * kv * y / hh
            val sy = 1f + KEY * kh * x / hw
            x *= sx
            y *= sy
            val xr = c * x - s * y
            val yr = s * x + c * y
            val f = 1f + k * (xr * xr + yr * yr) * r2n
            out[0] = xr * f + cx
            out[1] = yr * f + cy
        }
    }

    /** Zoom minimo para que ningun pixel de salida muestree fuera de la imagen. */
    private fun fillZoom(w: Int, h: Int, p: GeoParams): Float {
        var z = 1f
        val tmp = FloatArray(2)
        val step = max(1, max(w, h) / 256)
        repeat(4) {
            val m = Mapper(w, h, p, z)
            var worst = 1f
            fun probe(x: Int, y: Int) {
                m.map(x.toFloat(), y.toFloat(), tmp)
                worst = max(worst, max(abs(tmp[0] - m.cx) / max(m.cx, 1f), abs(tmp[1] - m.cy) / max(m.cy, 1f)))
            }
            var x = 0
            while (x < w) { probe(x, 0); probe(x, h - 1); x += step }
            probe(w - 1, 0); probe(w - 1, h - 1)
            var y = 0
            while (y < h) { probe(0, y); probe(w - 1, y); y += step }
            z *= worst
        }
        return z
    }

    /** Aplica la correccion. Con [fill] se amplia lo justo para no dejar bordes vacios. */
    fun apply(src: Bitmap, p: GeoParams, fill: Boolean = true): Bitmap {
        if (p.isIdentity) return BitmapUtils.mutableCopy(src)
        val w = src.width
        val h = src.height
        val zoom = if (fill) fillZoom(w, h, p) else 1f
        val m = Mapper(w, h, p, zoom)
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        Parallel.rows(h) { y0, y1 ->
            val c = FloatArray(2)
            for (y in y0 until y1) {
            for (x in 0 until w) {
                m.map(x.toFloat(), y.toFloat(), c)
                out[y * w + x] = bilinear(px, w, h, c[0], c[1])
            }
            }
        }
        val result = BitmapUtils.create(w, h)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun bilinear(px: IntArray, w: Int, h: Int, sx: Float, sy: Float): Int {
        val fx = sx.coerceIn(0f, w - 1f)
        val fy = sy.coerceIn(0f, h - 1f)
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val tx = fx - x0
        val ty = fy - y0
        val a = px[y0 * w + x0]
        val b = px[y0 * w + x1]
        val cc = px[y1 * w + x0]
        val d = px[y1 * w + x1]
        fun ch(shift: Int): Int {
            val top = ((a shr shift) and 0xFF) * (1 - tx) + ((b shr shift) and 0xFF) * tx
            val bot = ((cc shr shift) and 0xFF) * (1 - tx) + ((d shr shift) and 0xFF) * tx
            return (top * (1 - ty) + bot * ty).roundToInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // ----------------------------------------------------------------------------------
    // Estimacion automatica
    // ----------------------------------------------------------------------------------

    private class Gray(val w: Int, val h: Int, val v: FloatArray)

    private fun toGray(src: Bitmap): Gray {
        val k = min(1f, PROXY_SIDE.toFloat() / max(src.width, src.height))
        val w = max(8, (src.width * k).roundToInt())
        val h = max(8, (src.height * k).roundToInt())
        val small = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        return Gray(w, h, FloatArray(w * h) { i ->
            (0.299f * (px[i] shr 16 and 0xFF) + 0.587f * (px[i] shr 8 and 0xFF) + 0.114f * (px[i] and 0xFF)) / 255f
        })
    }

    /** Remuestrea la copia gris con la misma transformacion que [apply]. */
    private fun remap(g: Gray, p: GeoParams): Gray {
        if (p.isIdentity) return g
        val m = Mapper(g.w, g.h, p, 1f)
        val out = FloatArray(g.w * g.h)
        val c = FloatArray(2)
        for (y in 0 until g.h) {
            for (x in 0 until g.w) {
                m.map(x.toFloat(), y.toFloat(), c)
                val fx = c[0].coerceIn(0f, g.w - 1f)
                val fy = c[1].coerceIn(0f, g.h - 1f)
                val x0 = fx.toInt()
                val y0 = fy.toInt()
                val x1 = min(x0 + 1, g.w - 1)
                val y1 = min(y0 + 1, g.h - 1)
                val tx = fx - x0
                val ty = fy - y0
                val top = g.v[y0 * g.w + x0] * (1 - tx) + g.v[y0 * g.w + x1] * tx
                val bot = g.v[y1 * g.w + x0] * (1 - tx) + g.v[y1 * g.w + x1] * tx
                out[y * g.w + x] = top * (1 - ty) + bot * ty
            }
        }
        return Gray(g.w, g.h, out)
    }

    /** Gradientes (Sobel) de la imagen suavizada y umbral del 15 % de bordes mas fuertes. */
    private class Edges(val gx: FloatArray, val gy: FloatArray, val mag: FloatArray, val threshold: Float)

    private fun edges(g: Gray): Edges {
        val w = g.w
        val h = g.h
        // Suavizado binomial 3x3, equivalente a un desenfoque de ~1 px.
        val b = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            b[i] = (g.v[i - w - 1] + 2 * g.v[i - w] + g.v[i - w + 1] +
                2 * g.v[i - 1] + 4 * g.v[i] + 2 * g.v[i + 1] +
                g.v[i + w - 1] + 2 * g.v[i + w] + g.v[i + w + 1]) / 16f
        }
        val gx = FloatArray(w * h)
        val gy = FloatArray(w * h)
        val mag = FloatArray(w * h)
        var maxM = 1e-6f
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val sx = (b[i - w + 1] + 2 * b[i + 1] + b[i + w + 1]) - (b[i - w - 1] + 2 * b[i - 1] + b[i + w - 1])
            val sy = (b[i + w - 1] + 2 * b[i + w] + b[i + w + 1]) - (b[i - w - 1] + 2 * b[i - w] + b[i - w + 1])
            gx[i] = sx
            gy[i] = sy
            val m = sqrt(sx * sx + sy * sy)
            mag[i] = m
            if (m > maxM) maxM = m
        }
        // Percentil 85 por histograma: evita ordenar cientos de miles de valores.
        val hist = IntArray(512)
        for (m in mag) hist[min(511, (m / maxM * 511f).toInt())]++
        val target = (mag.size * 0.85f).toInt()
        var acc = 0
        var bin = 0
        while (bin < 511 && acc + hist[bin] < target) { acc += hist[bin]; bin++ }
        return Edges(gx, gy, mag, max(bin / 511f * maxM, 1e-6f))
    }

    /**
     * Cuanto se concentran las orientaciones de los bordes en dos ejes perpendiculares
     * (0..1) y el desfase de esos ejes respecto a los de la imagen, en radianes. El
     * armonico de orden 4 hace que 0, 90, 180 y 270 grados sumen en fase.
     */
    private fun harmonic(e: Edges): Pair<Float, Float> {
        var re = 0.0
        var im = 0.0
        var sum = 0.0
        for (i in e.mag.indices) {
            val m = e.mag[i]
            if (m <= e.threshold) continue
            val phi = atan2(e.gy[i], e.gx[i])
            re += m * cos(4f * phi)
            im += m * sin(4f * phi)
            sum += m
        }
        if (sum <= 1e-9) return 0f to 0f
        return (sqrt(re * re + im * im) / sum).toFloat() to (atan2(im, re) / 4.0).toFloat()
    }

    /** Pendiente de los bordes casi verticales (u horizontales) frente a su posicion. */
    private fun keystone(g: Gray): Pair<Float, Float> {
        val e = edges(g)
        val cx = (g.w - 1) / 2f
        val cy = (g.h - 1) / 2f
        // Acumuladores ponderados para regresion lineal: pendiente = a * posicion + b.
        var vw = 0.0; var vx = 0.0; var vm = 0.0; var vxx = 0.0; var vxm = 0.0; var vn = 0
        var hw = 0.0; var hy = 0.0; var hm = 0.0; var hyy = 0.0; var hym = 0.0; var hn = 0
        for (y in 1 until g.h - 1) {
            for (x in 1 until g.w - 1) {
                val i = y * g.w + x
                val m = e.mag[i]
                if (m <= e.threshold) continue
                val gxv = e.gx[i]
                val gyv = e.gy[i]
                val wgt = (m * m).toDouble()
                if (abs(gxv) > 2.5f * abs(gyv)) {
                    val slope = -gyv / gxv
                    if (abs(slope) < 0.36f) {
                        val px = x - cx
                        vw += wgt; vx += wgt * px; vm += wgt * slope
                        vxx += wgt * px * px; vxm += wgt * px * slope; vn++
                    }
                } else if (abs(gyv) > 2.5f * abs(gxv)) {
                    val slope = -gxv / gyv
                    if (abs(slope) < 0.36f) {
                        val py = y - cy
                        hw += wgt; hy += wgt * py; hm += wgt * slope
                        hyy += wgt * py * py; hym += wgt * py * slope; hn++
                    }
                }
            }
        }
        var kv = 0f
        if (vn > 50 && vw > 0) {
            val den = vxx - vx * vx / vw
            if (den > 1e-9) kv = (((vxm - vx * vm / vw) / den) * max(cy, 1f) / KEY).toFloat()
        }
        var kh = 0f
        if (hn > 50 && hw > 0) {
            val den = hyy - hy * hy / hw
            if (den > 1e-9) kh = (((hym - hy * hm / hw) / den) * max(cx, 1f) / KEY).toFloat()
        }
        return kv to kh
    }

    /**
     * Busca los parametros que enderezan la imagen: lente (la distorsion que mas rectas
     * deja), giro (el desfase de los ejes de los bordes) y perspectiva (pendiente de las
     * lineas verticales y horizontales segun su posicion). Es una heuristica sobre bordes:
     * funciona bien con arquitectura, horizontes y documentos, y devuelve null si no hay
     * lineas rectas claras. El resultado se puede retocar a mano.
     */
    fun estimate(src: Bitmap): GeoParams? {
        val g = toGray(src)
        val e0 = edges(g)
        val (baseScore, _) = harmonic(e0)
        if (baseScore < 0.12f) return null

        // 1) Lente: la distorsion que deja los bordes mas concentrados en dos ejes.
        val steps = 25
        val scores = FloatArray(steps)
        val ks = FloatArray(steps) { -0.9f + 1.8f * it / (steps - 1) }
        for (i in 0 until steps) {
            scores[i] = harmonic(edges(remap(g, GeoParams(lens = ks[i])))).first
        }
        var best = 0
        for (i in 1 until steps) if (scores[i] > scores[best]) best = i
        var lens = 0f
        if (best in 1 until steps - 1 && (scores[best] - baseScore) / baseScore > 0.004f) {
            // Refinado parabolico alrededor del maximo.
            val d = scores[best - 1] - 2 * scores[best] + scores[best + 1]
            lens = if (d != 0f) ks[best] + 0.5f * (scores[best - 1] - scores[best + 1]) / d * (ks[1] - ks[0]) else ks[best]
        }
        lens = (lens * 0.85f).coerceIn(-1f, 1f)

        // 2) Giro: desfase de los ejes tras corregir la lente.
        val g1 = remap(g, GeoParams(lens = lens))
        val (_, eps) = harmonic(edges(g1))
        val rotation = (eps * 180f / PI.toFloat()).coerceIn(-15f, 15f)

        // 3) Perspectiva: regresion de la pendiente de los bordes sobre la imagen ya girada.
        val g2 = remap(g1, GeoParams(rotation = rotation))
        val (kv, kh) = keystone(g2)
        val out = GeoParams(
            rotation = (rotation * 10f).roundToInt() / 10f,
            keystoneV = kv.coerceIn(-1f, 1f),
            keystoneH = kh.coerceIn(-1f, 1f),
            lens = lens,
        )
        return if (out.isIdentity) null else out
    }
}
