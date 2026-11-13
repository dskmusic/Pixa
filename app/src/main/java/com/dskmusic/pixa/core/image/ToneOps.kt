package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import android.graphics.Color
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.Adjustments
import com.dskmusic.pixa.core.model.CurvePoints
import com.dskmusic.pixa.core.model.CurvesSpec
import com.dskmusic.pixa.core.model.HslAdjust
import com.dskmusic.pixa.core.model.HslBand
import com.dskmusic.pixa.core.model.LevelsSpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Operaciones tonales finas del requisito 13: curvas, niveles, HSL por color,
 * reemplazar color, histograma y mejora automatica.
 *
 * Casi todas se resuelven con tablas de consulta de 256 entradas: la curva se calcula
 * una vez y la pasada por pixel queda en una indexacion, que es lo que permite
 * previsualizar en vivo sin bloquear la interfaz.
 */
object ToneOps {

    // ----------------------------------------------------------------------------------
    // Curvas
    // ----------------------------------------------------------------------------------

    /**
     * Convierte los puntos de control en una tabla de 256 valores.
     * Se interpola con Catmull-Rom entre puntos: da una curva suave sin los sobrepicos
     * que produce un spline cubico natural cuando el usuario arrastra un punto al borde.
     */
    fun curveLut(curve: CurvePoints): IntArray {
        val lut = IntArray(256) { it }
        if (curve.isIdentity || curve.points.size < 4) return lut

        val pts = curve.points.chunked(2)
            .filter { it.size == 2 }
            .map { it[0].coerceIn(0f, 1f) to it[1].coerceIn(0f, 1f) }
            .sortedBy { it.first }
            .distinctBy { (it.first * 1000f).roundToInt() }
        if (pts.size < 2) return lut

        for (i in 0 until 256) {
            lut[i] = (interpolate(pts, i / 255f) * 255f).roundToInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun interpolate(pts: List<Pair<Float, Float>>, x: Float): Float {
        if (x <= pts.first().first) return pts.first().second
        if (x >= pts.last().first) return pts.last().second

        var k = 0
        while (k < pts.size - 1 && pts[k + 1].first < x) k++
        val p1 = pts[k]
        val p2 = pts[k + 1]
        val p0 = pts[max(0, k - 1)]
        val p3 = pts[min(pts.size - 1, k + 2)]

        val span = p2.first - p1.first
        if (span <= 0f) return p2.second
        val t = (x - p1.first) / span

        val m1 = (p2.second - p0.second) / 2f
        val m2 = (p3.second - p1.second) / 2f
        val t2 = t * t
        val t3 = t2 * t
        val v = (2f * t3 - 3f * t2 + 1f) * p1.second +
            (t3 - 2f * t2 + t) * m1 +
            (-2f * t3 + 3f * t2) * p2.second +
            (t3 - t2) * m2
        return v.coerceIn(0f, 1f)
    }

    /** Aplica la curva maestra y las de cada canal en una sola pasada. */
    fun applyCurves(src: Bitmap, spec: CurvesSpec): Bitmap {
        if (spec.isIdentity) return BitmapUtils.mutableCopy(src)
        val rgb = curveLut(spec.rgb)
        return applyChannelLuts(
            src,
            combine(rgb, curveLut(spec.red)),
            combine(rgb, curveLut(spec.green)),
            combine(rgb, curveLut(spec.blue)),
        )
    }

    /** Composicion de dos tablas: primero la del canal, luego la maestra. */
    private fun combine(global: IntArray, channel: IntArray): IntArray =
        IntArray(256) { global[channel[it]] }

    fun applyChannelLuts(src: Bitmap, lr: IntArray, lg: IntArray, lb: IntArray): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val p = px[i]
            val a = p ushr 24 and 0xFF
            if (a == 0) {
                px[i] = 0
                continue
            }
            px[i] = (a shl 24) or
                (lr[p ushr 16 and 0xFF] shl 16) or
                (lg[p ushr 8 and 0xFF] shl 8) or
                lb[p and 0xFF]
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    // ----------------------------------------------------------------------------------
    // Niveles
    // ----------------------------------------------------------------------------------

    fun levelsLut(spec: LevelsSpec): IntArray {
        val black = spec.black.coerceIn(0f, 0.98f)
        val white = spec.white.coerceIn(black + 0.02f, 1f)
        val gamma = spec.gamma.coerceIn(0.1f, 5f)
        val span = white - black
        return IntArray(256) { i ->
            val v = ((i / 255f - black) / span).coerceIn(0f, 1f)
            (v.pow(1f / gamma) * 255f).roundToInt().coerceIn(0, 255)
        }
    }

    fun applyLevels(src: Bitmap, spec: LevelsSpec): Bitmap {
        if (spec.isIdentity) return BitmapUtils.mutableCopy(src)
        val lut = levelsLut(spec)
        return applyChannelLuts(src, lut, lut, lut)
    }

    // ----------------------------------------------------------------------------------
    // HSL por color
    // ----------------------------------------------------------------------------------

    /** Media anchura de banda en grados: con 8 bandas, 45 grados cubre el circulo con solape. */
    private const val BAND_HALF_WIDTH = 45f

    /**
     * Ajusta tono, saturacion y luminosidad por banda de color. El peso de cada banda cae
     * suavemente con la distancia angular a su tono central, asi que los limites entre
     * bandas no producen escalones visibles.
     */
    fun applyHsl(src: Bitmap, bands: Map<HslBand, HslAdjust>): Bitmap {
        val active = bands.filterValues { !it.isIdentity }
        if (active.isEmpty()) return BitmapUtils.mutableCopy(src)

        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        Parallel.rows(px.size) { i0, i1 ->
            val hsv = FloatArray(3)
            for (i in i0 until i1) {
            val p = px[i]
            val a = p ushr 24 and 0xFF
            if (a == 0) {
                px[i] = 0
                continue
            }
            Color.colorToHSV(p or OPAQUE, hsv)

            var hueShift = 0f
            var satScale = 0f
            var lumScale = 0f
            var touched = false
            for ((band, adj) in active) {
                val d = hueDistance(hsv[0], band.centerHue)
                if (d >= BAND_HALF_WIDTH) continue
                // Peso coseno: 1 en el centro de la banda, 0 en su borde.
                val weight = 0.5f + 0.5f * kotlin.math.cos(Math.PI * d / BAND_HALF_WIDTH).toFloat()
                hueShift += adj.hueShift * weight
                satScale += adj.satScale * weight
                lumScale += adj.lumScale * weight
                touched = true
            }
            if (!touched) continue

            hsv[0] = wrapHue(hsv[0] + hueShift * 60f)
            hsv[1] = (hsv[1] * (1f + satScale)).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * (1f + lumScale)).coerceIn(0f, 1f)
            px[i] = (a shl 24) or (Color.HSVToColor(hsv) and RGB_MASK)
            }
        }

        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun wrapHue(h: Float): Float = ((h % 360f) + 360f) % 360f

    // ----------------------------------------------------------------------------------
    // Reemplazar color
    // ----------------------------------------------------------------------------------

    /**
     * Sustituye un color por otro conservando el relieve: se transfiere el tono y la
     * saturacion del color destino pero se mantiene la diferencia de luminosidad que
     * tenia el pixel respecto al color original, de modo que sombras y brillos siguen ahi.
     */
    fun replaceColor(src: Bitmap, from: Int, to: Int, tolerance: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val tol = tolerance.coerceIn(0f, 1f)
        val srcHsv = FloatArray(3).also { Color.colorToHSV(from, it) }
        val dstHsv = FloatArray(3).also { Color.colorToHSV(to, it) }

        Parallel.rows(px.size) { i0, i1 ->
            val hsv = FloatArray(3)
            for (i in i0 until i1) {
            val p = px[i]
            val a = p ushr 24 and 0xFF
            if (a == 0) continue
            val dist = Adjust.colorDistance(p or OPAQUE, from or OPAQUE)
            if (dist > tol) continue
            // Transicion suave en el borde de la tolerancia.
            val k = if (tol <= 0f) 1f else (1f - dist / tol).coerceIn(0f, 1f)

            Color.colorToHSV(p or OPAQUE, hsv)
            val lumDelta = hsv[2] - srcHsv[2]
            val targetVal = (dstHsv[2] + lumDelta).coerceIn(0f, 1f)
            hsv[0] = lerpHue(hsv[0], dstHsv[0], k)
            hsv[1] = (hsv[1] + (dstHsv[1] - hsv[1]) * k).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] + (targetVal - hsv[2]) * k).coerceIn(0f, 1f)
            px[i] = (a shl 24) or (Color.HSVToColor(hsv) and RGB_MASK)
            }
        }

        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun lerpHue(a: Float, b: Float, k: Float): Float {
        var d = b - a
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return wrapHue(a + d * k)
    }

    // ----------------------------------------------------------------------------------
    // Histograma y mejora automatica
    // ----------------------------------------------------------------------------------

    class Histogram(
        val red: IntArray,
        val green: IntArray,
        val blue: IntArray,
        val luma: IntArray,
        val samples: Int,
    ) {
        /** Valor de la barra mas alta, para escalar el dibujo del histograma. */
        val maxBin: Int get() = max(max(red.max(), green.max()), blue.max())

        /** Nivel por debajo del cual queda la fraccion indicada de los pixeles. */
        fun lumaPercentile(fraction: Float): Int {
            if (samples == 0) return 0
            val target = (samples * fraction.coerceIn(0f, 1f)).toInt()
            var acc = 0
            for (i in 0 until 256) {
                acc += luma[i]
                if (acc >= target) return i
            }
            return 255
        }

        val meanLuma: Float
            get() {
                if (samples == 0) return 0f
                var sum = 0L
                for (i in 0 until 256) sum += luma[i].toLong() * i
                return sum.toFloat() / samples
            }
    }

    /**
     * Histograma de la imagen. Se muestrea como maximo [maxSamples] pixeles para que el
     * calculo sea inmediato tambien en imagenes de muchos megapixeles.
     */
    fun histogram(src: Bitmap, maxSamples: Int = 200_000): Histogram {
        val w = src.width
        val h = src.height
        val total = w * h
        val step = max(1, total / max(1, maxSamples))
        val px = IntArray(total)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val r = IntArray(256)
        val g = IntArray(256)
        val b = IntArray(256)
        val l = IntArray(256)
        var samples = 0
        var i = 0
        while (i < total) {
            val p = px[i]
            if ((p ushr 24 and 0xFF) > 8) {
                val rv = p ushr 16 and 0xFF
                val gv = p ushr 8 and 0xFF
                val bv = p and 0xFF
                r[rv]++
                g[gv]++
                b[bv]++
                l[luma(rv, gv, bv)]++
                samples++
            }
            i += step
        }
        return Histogram(r, g, b, l, samples)
    }

    private fun luma(r: Int, g: Int, b: Int): Int =
        (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255)

    /**
     * Mejora automatica de un toque (requisito 13): deduce los ajustes a partir del
     * histograma, estirando el rango tonal realmente usado y corrigiendo la exposicion
     * media. Devuelve los ajustes en vez de aplicarlos para que el usuario los vea en los
     * sliders y pueda retocarlos antes de confirmar.
     */
    fun autoEnhance(src: Bitmap): Adjustments {
        val hist = histogram(src)
        if (hist.samples == 0) return Adjustments()

        val low = hist.lumaPercentile(0.005f)
        val high = hist.lumaPercentile(0.995f)
        val range = (high - low).coerceAtLeast(1)

        // Cuanto mas estrecho es el rango tonal usado, mas contraste hace falta.
        val contrast = ((255f / range) - 1f).coerceIn(0f, 0.45f)
        // Desviacion respecto al gris medio, como correccion de brillo suave.
        val brightness = ((127.5f - hist.meanLuma) / 255f).coerceIn(-0.25f, 0.25f)
        // Si la imagen esta apagada se levanta la vibranza, que no quema los colores vivos.
        val sat = meanSaturation(src)
        val vibrance = if (sat < 0.35f) ((0.35f - sat) * 0.9f).coerceAtMost(0.3f) else 0f

        return Adjustments(
            brightness = brightness,
            contrast = contrast,
            vibrance = vibrance,
            // Negros empastados: se abren un poco las sombras.
            shadows = if (low > 24) 0f else 0.12f,
        )
    }

    private fun meanSaturation(src: Bitmap, maxSamples: Int = 40_000): Float {
        val w = src.width
        val h = src.height
        val total = w * h
        val step = max(1, total / max(1, maxSamples))
        val px = IntArray(total)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var sum = 0f
        var n = 0
        var i = 0
        while (i < total) {
            val p = px[i]
            if ((p ushr 24 and 0xFF) > 8) {
                val r = p ushr 16 and 0xFF
                val g = p ushr 8 and 0xFF
                val b = p and 0xFF
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                sum += if (mx == 0) 0f else (mx - mn) / mx.toFloat()
                n++
            }
            i += step
        }
        return if (n == 0) 0f else sum / n
    }

    private const val OPAQUE = 0xFF shl 24
    private const val RGB_MASK = 0xFFFFFF
}
