package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.ml.SubjectCutout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Que se pone donde estaba el fondo. */
enum class BgOutput { TRANSPARENT, COLOR, BLUR }

/**
 * Parametros del recorte de fondo, comunes a todos los modelos. Los tamanos (expandir,
 * suavizado, radios) estan en pixeles de una imagen de ~1000 px de lado y se escalan con
 * la imagen real, para que la vista previa y el resultado final coincidan.
 */
data class BgParams(
    /** 0..1: confianza a partir de la cual un pixel cuenta como sujeto. */
    val threshold: Float = 0.5f,
    /** 0..1: 1 = borde seco, 0 = transicion larga y suave. */
    val hardness: Float = 0.6f,
    /** Pixeles: positivo agranda el sujeto, negativo lo encoge (quita el halo). */
    val grow: Float = 0f,
    /** Desenfoque final del borde, en pixeles. */
    val feather: Float = 1.5f,
    /** 0..1: cuanto se ajusta el borde a los detalles reales de la foto (pelo). */
    val refine: Float = 0f,
    /** 0..1: sustituye el color del fondo que se cuela en el borde por el del sujeto. */
    val decontaminate: Float = 0f,
    /** Quita el sujeto y deja el fondo. */
    val invert: Boolean = false,
    val output: BgOutput = BgOutput.TRANSPARENT,
    val bgColor: Int = 0xFFFFFFFF.toInt(),
    /** 0..1: intensidad del desenfoque cuando [output] es BLUR. */
    val blur: Float = 0.5f,
)

/**
 * Convierte la mascara de confianza de un modelo en el resultado final aplicando los
 * [BgParams]: curva de umbral, expandir o contraer, refinado de bordes con filtro guiado,
 * suavizado, limpieza de halo y composicion. La mascara y los efectos se calculan a una
 * resolucion de trabajo de ~1600 px y solo la composicion final va a tamano completo.
 */
object BgCompose {

    private const val WORK_SIDE = 1600

    fun compose(src: Bitmap, mask: SubjectCutout.Mask, p: BgParams): Bitmap {
        val w = src.width
        val h = src.height
        val k = min(1f, WORK_SIDE.toFloat() / max(w, h))
        val ww = max(1, (w * k).roundToInt())
        val wh = max(1, (h * k).roundToInt())
        // Un pixel "de referencia" (imagen de 1000 px) medido en la resolucion de trabajo.
        val unit = max(ww, wh) / 1000f

        // 1) Confianza del modelo a la resolucion de trabajo.
        var a = resample(mask.confidences, mask.width, mask.height, ww, wh)

        // 2) Curva de umbral: una rampa alrededor de [threshold], mas ancha cuanto menos dura.
        val half = 0.02f + (1f - p.hardness.coerceIn(0f, 1f)) * 0.30f
        val t = p.threshold.coerceIn(0.02f, 0.98f)
        for (i in a.indices) a[i] = ((a[i] - t + half) / (2f * half)).coerceIn(0f, 1f)

        // 3) Expandir o contraer.
        val r = (p.grow * unit).roundToInt()
        if (r != 0) a = morph(a, ww, wh, kotlin.math.abs(r), grow = r > 0)

        // 4) Refinado: filtro guiado con la luminosidad de la foto como guia.
        val small = if (ww == w && wh == h) src else Bitmap.createScaledBitmap(src, ww, wh, true)
        val sp = IntArray(ww * wh)
        small.getPixels(sp, 0, ww, 0, 0, ww, wh)
        if (small !== src) small.recycle()
        if (p.refine > 0.01f) {
            val guide = FloatArray(ww * wh) { i ->
                (0.299f * (sp[i] shr 16 and 0xFF) + 0.587f * (sp[i] shr 8 and 0xFF) + 0.114f * (sp[i] and 0xFF)) / 255f
            }
            val q = guidedFilter(guide, a, ww, wh, max(2, (6f * unit).roundToInt()), 1e-3f)
            val m = p.refine.coerceIn(0f, 1f)
            for (i in a.indices) a[i] = (a[i] + (q[i].coerceIn(0f, 1f) - a[i]) * m).coerceIn(0f, 1f)
        }

        // 5) Suavizado del borde.
        val fr = (p.feather * unit).roundToInt()
        if (fr > 0) a = boxBlur(boxBlur(a, ww, wh, fr), ww, wh, fr)

        // 6) Color limpio del sujeto para el borde (a resolucion de trabajo).
        var clean: IntArray? = null
        if (p.decontaminate > 0.01f && !p.invert) clean = foregroundColors(sp, a, ww, wh, max(2, (10f * unit).roundToInt()))

        if (p.invert) for (i in a.indices) a[i] = 1f - a[i]

        // 7) A tamano completo: alfa y colores limpios por interpolacion bilineal.
        val alphaFull = upscaleAlpha(a, ww, wh, w, h)
        val cleanFull = clean?.let { upscaleRgb(it, ww, wh, w, h) }

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var bgBlur: IntArray? = null
        if (p.output == BgOutput.BLUR) {
            val blurred = Blur.blur(src, max(2f, max(w, h) * 0.012f * (0.2f + p.blur * 2.5f)))
            bgBlur = IntArray(w * h).also { blurred.getPixels(it, 0, w, 0, 0, w, h) }
            blurred.recycle()
        }
        val dec = p.decontaminate.coerceIn(0f, 1f)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val o = px[i]
            val oa = o ushr 24
            val al = (alphaFull[i].toInt() and 0xFF) / 255f
            var r0 = (o shr 16) and 0xFF
            var g0 = (o shr 8) and 0xFF
            var b0 = o and 0xFF
            if (cleanFull != null && al < 0.98f) {
                // Cuanto mas transparente el pixel, mas se sustituye su color por el del sujeto.
                val f = cleanFull[i]
                val tt = dec * (1f - al)
                r0 = (r0 + ((f shr 16 and 0xFF) - r0) * tt).roundToInt()
                g0 = (g0 + ((f shr 8 and 0xFF) - g0) * tt).roundToInt()
                b0 = (b0 + ((f and 0xFF) - b0) * tt).roundToInt()
            }
            px[i] = when (p.output) {
                BgOutput.TRANSPARENT -> ((oa * al).roundToInt() shl 24) or (r0 shl 16) or (g0 shl 8) or b0
                BgOutput.COLOR -> {
                    val c = p.bgColor
                    (oa shl 24) or
                        (mix(c shr 16 and 0xFF, r0, al) shl 16) or
                        (mix(c shr 8 and 0xFF, g0, al) shl 8) or
                        mix(c and 0xFF, b0, al)
                }
                BgOutput.BLUR -> {
                    val c = bgBlur!![i]
                    (oa shl 24) or
                        (mix(c shr 16 and 0xFF, r0, al) shl 16) or
                        (mix(c shr 8 and 0xFF, g0, al) shl 8) or
                        mix(c and 0xFF, b0, al)
                }
            }
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun mix(bg: Int, fg: Int, a: Float): Int = (bg + (fg - bg) * a).roundToInt().coerceIn(0, 255)

    // ----------------------------------------------------------------------------------
    // Utilidades de mascara
    // ----------------------------------------------------------------------------------

    /** Reescalado bilineal de un campo de floats. */
    private fun resample(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val out = FloatArray(dw * dh)
        val xr = sw.toFloat() / dw
        val yr = sh.toFloat() / dh
        for (y in 0 until dh) {
            val fy = ((y + 0.5f) * yr - 0.5f).coerceIn(0f, sh - 1f)
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, sh - 1)
            val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + 0.5f) * xr - 0.5f).coerceIn(0f, sw - 1f)
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, sw - 1)
                val tx = fx - x0
                val top = src[y0 * sw + x0] * (1 - tx) + src[y0 * sw + x1] * tx
                val bot = src[y1 * sw + x0] * (1 - tx) + src[y1 * sw + x1] * tx
                out[y * dw + x] = top * (1 - ty) + bot * ty
            }
        }
        return out
    }

    /** Dilatacion (maximo) o erosion (minimo) en ventana cuadrada, separable. */
    private fun morph(a: FloatArray, w: Int, h: Int, r: Int, grow: Boolean): FloatArray {
        val tmp = FloatArray(a.size)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) for (x in 0 until w) {
                var best = a[y * w + x]
                for (k in max(0, x - r)..min(w - 1, x + r)) {
                    val v = a[y * w + k]
                    if (grow) { if (v > best) best = v } else { if (v < best) best = v }
                }
                tmp[y * w + x] = best
            }
        }
        val out = FloatArray(a.size)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) for (x in 0 until w) {
                var best = tmp[y * w + x]
                for (k in max(0, y - r)..min(h - 1, y + r)) {
                    val v = tmp[k * w + x]
                    if (grow) { if (v > best) best = v } else { if (v < best) best = v }
                }
                out[y * w + x] = best
            }
        }
        return out
    }

    /** Media en ventana cuadrada de radio [r] con sumas acumuladas (O(n), independiente de r). */
    private fun boxBlur(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(a.size)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
                val row = y * w
                var sum = 0f
                for (x in 0..min(r, w - 1)) sum += a[row + x]
                for (x in 0 until w) {
                    val lo = max(0, x - r)
                    val hi = min(w - 1, x + r)
                    tmp[row + x] = sum / (hi - lo + 1)
                    if (x + r + 1 < w) sum += a[row + x + r + 1]
                    if (x - r >= 0) sum -= a[row + x - r]
                }
            }
        }
        val out = FloatArray(a.size)
        Parallel.rows(w) { x0, x1 ->
            for (x in x0 until x1) {
                var sum = 0f
                for (y in 0..min(r, h - 1)) sum += tmp[y * w + x]
                for (y in 0 until h) {
                    val lo = max(0, y - r)
                    val hi = min(h - 1, y + r)
                    out[y * w + x] = sum / (hi - lo + 1)
                    if (y + r + 1 < h) sum += tmp[(y + r + 1) * w + x]
                    if (y - r >= 0) sum -= tmp[(y - r) * w + x]
                }
            }
        }
        return out
    }

    /**
     * Filtro guiado (He et al.): suaviza [p] respetando los bordes de [guide]. Con la foto
     * como guia, el alfa se ajusta a los hilos de pelo y bordes reales.
     */
    private fun guidedFilter(guide: FloatArray, p: FloatArray, w: Int, h: Int, r: Int, eps: Float): FloatArray {
        val n = guide.size
        val meanI = boxBlur(guide, w, h, r)
        val meanP = boxBlur(p, w, h, r)
        val ip = FloatArray(n) { guide[it] * p[it] }
        val ii = FloatArray(n) { guide[it] * guide[it] }
        val meanIp = boxBlur(ip, w, h, r)
        val meanII = boxBlur(ii, w, h, r)
        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val cov = meanIp[i] - meanI[i] * meanP[i]
            val v = meanII[i] - meanI[i] * meanI[i]
            a[i] = cov / (v + eps)
            b[i] = meanP[i] - a[i] * meanI[i]
        }
        val meanA = boxBlur(a, w, h, r)
        val meanB = boxBlur(b, w, h, r)
        return FloatArray(n) { meanA[it] * guide[it] + meanB[it] }
    }

    /**
     * Color del sujeto extendido hacia el borde: media (ponderada por la confianza de ser
     * sujeto solido) de los colores vecinos. Los pixeles del borde reciben ese color en
     * vez del que traian mezclado con el fondo.
     */
    private fun foregroundColors(px: IntArray, alpha: FloatArray, w: Int, h: Int, r: Int): IntArray {
        val n = px.size
        val wgt = FloatArray(n) { if (alpha[it] > 0.92f) 1f else 0f }
        val rr = FloatArray(n) { wgt[it] * (px[it] shr 16 and 0xFF) }
        val gg = FloatArray(n) { wgt[it] * (px[it] shr 8 and 0xFF) }
        val bb = FloatArray(n) { wgt[it] * (px[it] and 0xFF) }
        val bw = boxBlur(wgt, w, h, r)
        val br = boxBlur(rr, w, h, r)
        val bg = boxBlur(gg, w, h, r)
        val bbl = boxBlur(bb, w, h, r)
        return IntArray(n) { i ->
            if (bw[i] < 1e-3f) {
                px[i]
            } else {
                (0xFF shl 24) or
                    ((br[i] / bw[i]).roundToInt().coerceIn(0, 255) shl 16) or
                    ((bg[i] / bw[i]).roundToInt().coerceIn(0, 255) shl 8) or
                    (bbl[i] / bw[i]).roundToInt().coerceIn(0, 255)
            }
        }
    }

    /** Alfa 0..1 a [dw] x [dh] como bytes 0..255, con interpolacion bilineal. */
    private fun upscaleAlpha(a: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): ByteArray {
        val out = ByteArray(dw * dh)
        val xr = sw.toFloat() / dw
        val yr = sh.toFloat() / dh
        for (y in 0 until dh) {
            val fy = ((y + 0.5f) * yr - 0.5f).coerceIn(0f, sh - 1f)
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, sh - 1)
            val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + 0.5f) * xr - 0.5f).coerceIn(0f, sw - 1f)
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, sw - 1)
                val tx = fx - x0
                val top = a[y0 * sw + x0] * (1 - tx) + a[y0 * sw + x1] * tx
                val bot = a[y1 * sw + x0] * (1 - tx) + a[y1 * sw + x1] * tx
                out[y * dw + x] = ((top * (1 - ty) + bot * ty).coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            }
        }
        return out
    }

    private fun upscaleRgb(px: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        if (sw == dw && sh == dh) return px
        val bmp = BitmapUtils.create(sw, sh)
        bmp.setPixels(px, 0, sw, 0, 0, sw, sh)
        val big = Bitmap.createScaledBitmap(bmp, dw, dh, true)
        val out = IntArray(dw * dh)
        big.getPixels(out, 0, dw, 0, 0, dw, dh)
        if (big !== bmp) big.recycle()
        bmp.recycle()
        return out
    }
}
