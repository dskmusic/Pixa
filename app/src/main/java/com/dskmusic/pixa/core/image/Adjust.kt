package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.Adjustments
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Motor de ajustes de imagen (requisito 13).
 *
 * Lo que se puede expresar como una matriz de color se resuelve con ColorMatrix, que es
 * una sola pasada acelerada. Lo que es inherentemente no lineal (vibranza, sombras,
 * luces) se hace con una pasada por pixel. El resto (desenfoque, nitidez, vineta, grano)
 * son pasadas propias y se aplican al final, en el orden en que un editor las espera.
 *
 * Todas las funciones devuelven un bitmap nuevo y no tocan el original: el modelo trata
 * los bitmaps como inmutables.
 */
object Adjust {

    /** Aplica el conjunto completo de ajustes. Pensado para ejecutarse fuera del hilo principal. */
    fun apply(src: Bitmap, adj: Adjustments): Bitmap {
        if (adj.isIdentity) return BitmapUtils.mutableCopy(src)

        // 1. Todo lo lineal, en una unica pasada de matriz de color.
        var out = applyColorMatrix(src, buildMatrix(adj))

        // 2. Pasadas no lineales sobre los pixeles.
        if (adj.vibrance != 0f || adj.shadows != 0f || adj.highlights != 0f) {
            out = applyToneCurvePass(out, adj)
        }

        // 3. Geometria y detalle: lente, neblina, ruido y contraste local, antes de desenfocar o enfocar.
        out = step(out, adj.lens != 0f) { Detail.lens(it, adj.lens) }
        out = step(out, adj.dehaze != 0f) { Detail.dehaze(it, adj.dehaze) }
        out = step(out, adj.denoise > 0f) { Detail.denoise(it, adj.denoise) }
        out = step(out, adj.clarity != 0f) { Detail.clarity(it, adj.clarity) }
        out = step(out, adj.texture != 0f) { Detail.texture(it, adj.texture) }

        // 4. Desenfoque y nitidez.
        if (adj.blur > 0f) {
            // Radio relativo al lado mayor (1080 px de referencia) para que la vista previa reducida
            // y el resultado a resolucion completa se vean igual de desenfocados.
            val radius = adj.blur.coerceIn(0f, 1f) * 40f * Effects.sideScale(out)
            val blurred = Blur.blur(out, radius)
            out.recycle()
            out = blurred
        }
        if (adj.sharpen > 0f) {
            val sharpened = sharpen(out, adj.sharpen, adj.sharpenEdges)
            out.recycle()
            out = sharpened
        }

        // 5. Vineta y grano, que se dibujan encima.
        if (adj.vignette != 0f) drawVignette(out, adj.vignette)
        if (adj.grain > 0f) drawGrain(out, adj.grain)

        return out
    }

    /** Aplica [op] sobre [bmp] si [enabled], liberando el bitmap intermedio. */
    private inline fun step(bmp: Bitmap, enabled: Boolean, op: (Bitmap) -> Bitmap): Bitmap {
        if (!enabled) return bmp
        val next = op(bmp)
        bmp.recycle()
        return next
    }

    // ----------------------------------------------------------------------------------
    // Matriz de color
    // ----------------------------------------------------------------------------------

    fun buildMatrix(adj: Adjustments): ColorMatrix {
        val m = ColorMatrix()

        // Exposicion: multiplicador en potencias de 2, como en fotografia.
        if (adj.exposure != 0f) {
            val k = 2f.pow(adj.exposure.coerceIn(-1f, 1f) * 1.5f)
            m.postConcat(ColorMatrix(floatArrayOf(
                k, 0f, 0f, 0f, 0f,
                0f, k, 0f, 0f, 0f,
                0f, 0f, k, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }

        // Contraste alrededor del gris medio.
        if (adj.contrast != 0f) {
            val s = 1f + adj.contrast.coerceIn(-1f, 1f)
            val t = (1f - s) * 127.5f
            m.postConcat(ColorMatrix(floatArrayOf(
                s, 0f, 0f, 0f, t,
                0f, s, 0f, 0f, t,
                0f, 0f, s, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )))
        }

        // Brillo: desplazamiento plano.
        if (adj.brightness != 0f) {
            val b = adj.brightness.coerceIn(-1f, 1f) * 90f
            m.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f,
            )))
        }

        // Temperatura: calido sube rojo y baja azul.
        if (adj.temperature != 0f) {
            val t = adj.temperature.coerceIn(-1f, 1f) * 45f
            m.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, t,
                0f, 1f, 0f, 0f, t * 0.25f,
                0f, 0f, 1f, 0f, -t,
                0f, 0f, 0f, 1f, 0f,
            )))
        }

        // Tinte: verde contra magenta.
        if (adj.tint != 0f) {
            val t = adj.tint.coerceIn(-1f, 1f) * 35f
            m.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, t * 0.5f,
                0f, 1f, 0f, 0f, -t,
                0f, 0f, 1f, 0f, t * 0.5f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }

        // Saturacion global.
        if (adj.saturation != 0f) {
            val s = ColorMatrix()
            s.setSaturation((1f + adj.saturation.coerceIn(-1f, 1f)).coerceAtLeast(0f))
            m.postConcat(s)
        }

        return m
    }

    fun applyColorMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = BitmapUtils.create(src.width, src.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    // ----------------------------------------------------------------------------------
    // Pasada no lineal: vibranza, sombras y luces
    // ----------------------------------------------------------------------------------

    private fun applyToneCurvePass(src: Bitmap, adj: Adjustments): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val vib = adj.vibrance.coerceIn(-1f, 1f)
        val sh = adj.shadows.coerceIn(-1f, 1f)
        val hi = adj.highlights.coerceIn(-1f, 1f)

        // Tabla de ganancia por luminancia para sombras y luces: evita recalcular
        // la curva en cada pixel.
        val gain = FloatArray(256) { i ->
            val l = i / 255f
            // Peso de sombras alto en oscuros, peso de luces alto en claros.
            val wShadow = (1f - l).pow(2f)
            val wHighlight = l.pow(2f)
            1f + sh * 0.85f * wShadow - hi * 0.85f * wHighlight
        }

        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val p = px[i]
            val a = p ushr 24 and 0xFF
            if (a == 0) continue
            var r = (p ushr 16 and 0xFF)
            var g = (p ushr 8 and 0xFF)
            var b = (p and 0xFF)

            // Sombras / luces por luminancia.
            if (sh != 0f || hi != 0f) {
                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255)
                val k = gain[lum]
                r = (r * k).roundToInt().coerceIn(0, 255)
                g = (g * k).roundToInt().coerceIn(0, 255)
                b = (b * k).roundToInt().coerceIn(0, 255)
            }

            // Vibranza: satura mas los colores apagados y deja en paz los ya saturados,
            // que es lo que la diferencia de la saturacion global.
            if (vib != 0f) {
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                val sat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
                val k = vib * (1f - sat)
                val gray = (0.2126f * r + 0.7152f * g + 0.0722f * b)
                r = (r + (r - gray) * k).roundToInt().coerceIn(0, 255)
                g = (g + (g - gray) * k).roundToInt().coerceIn(0, 255)
                b = (b + (b - gray) * k).roundToInt().coerceIn(0, 255)
            }

            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    // ----------------------------------------------------------------------------------
    // Nitidez
    // ----------------------------------------------------------------------------------

    /**
     * Mascara de enfoque: original + k * (original - desenfocado).
     *
     * Con [edgeFocus] > 0 la nitidez se limita a los bordes: donde la diferencia con la
     * version desenfocada es pequena (cielo, piel) no se toca, asi no se realza el ruido.
     */
    fun sharpen(src: Bitmap, amount: Float, edgeFocus: Float = 0f): Bitmap {
        val k = amount.coerceIn(0f, 1f) * 1.6f
        if (k <= 0f) return BitmapUtils.mutableCopy(src)

        val w = src.width
        val h = src.height
        val blurred = Blur.blur(src, max(1f, 1.6f * Effects.sideScale(src)))
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
            var kk = k
            if (edgeFocus > 0f) {
                // Diferencia de luminancia con la version desenfocada: 3 = ruido, 20 = borde.
                val d = abs(
                    0.2126f * ((pa ushr 16 and 0xFF) - (pb ushr 16 and 0xFF)) +
                        0.7152f * ((pa ushr 8 and 0xFF) - (pb ushr 8 and 0xFF)) +
                        0.0722f * ((pa and 0xFF) - (pb and 0xFF)),
                )
                val t = ((d - 3f) / 17f).coerceIn(0f, 1f)
                kk = k * (1f - edgeFocus.coerceIn(0f, 1f) * (1f - t))
            }
            val r = sharpChannel(pa ushr 16 and 0xFF, pb ushr 16 and 0xFF, kk)
            val g = sharpChannel(pa ushr 8 and 0xFF, pb ushr 8 and 0xFF, kk)
            val bl = sharpChannel(pa and 0xFF, pb and 0xFF, kk)
            a[i] = (al shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }

        val out = BitmapUtils.create(w, h)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun sharpChannel(orig: Int, blur: Int, k: Float): Int =
        (orig + (orig - blur) * k).roundToInt().coerceIn(0, 255)

    // ----------------------------------------------------------------------------------
    // Vineta y grano
    // ----------------------------------------------------------------------------------

    /** Medio lado por raiz de dos: lleva la circunferencia unidad a las esquinas. */
    private const val CORNER_SCALE = 0.70710678f

    /**
     * Vineta en las esquinas. Negativo = negra, positivo = blanca. En el sitio.
     *
     * El degradado se define en un circulo de radio 1 y una matriz lo estira hasta que la
     * circunferencia pasa justo por las cuatro esquinas del bitmap. Asi el efecto es
     * identico en una foto cuadrada de 500 px y en un panoramico de veinte megapixeles:
     * antes el radio salia del lado mayor en pixeles y en los formatos alargados el
     * oscurecido se quedaba lejos de las esquinas, de modo que apenas se notaba.
     */
    fun drawVignette(bitmap: Bitmap, amount: Float) {
        val k = amount.coerceIn(-1f, 1f)
        if (k == 0f) return
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val alpha = (abs(k) * 225f).roundToInt().coerceIn(0, 255)
        val tint = if (k < 0f) Color.argb(alpha, 0, 0, 0) else Color.argb(alpha, 255, 255, 255)
        val shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, tint),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        // La esquina queda a distancia 1 del centro: (w/2)/sx y (h/2)/sy valen 1/raiz(2)
        // cada una, y la suma de sus cuadrados es exactamente 1.
        shader.setLocalMatrix(
            Matrix().apply {
                setScale(w * CORNER_SCALE, h * CORNER_SCALE)
                postTranslate(w / 2f, h / 2f)
            },
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        // SRC_ATOP respeta el alfa de la capa: no pinta sobre zonas transparentes.
        paint.blendMode = android.graphics.BlendMode.SRC_ATOP
        Canvas(bitmap).drawRect(0f, 0f, w, h, paint)
    }

    /** Grano monocromo reproducible. Modifica [bitmap] en el sitio. */
    fun drawGrain(bitmap: Bitmap, amount: Float, seed: Long = 7L) {
        val k = amount.coerceIn(0f, 1f)
        if (k <= 0f) return
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val rnd = java.util.Random(seed)
        val strength = k * 60f
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24 and 0xFF
            if (a == 0) continue
            val n = ((rnd.nextFloat() - 0.5f) * strength).roundToInt()
            val r = ((p ushr 16 and 0xFF) + n).coerceIn(0, 255)
            val g = ((p ushr 8 and 0xFF) + n).coerceIn(0, 255)
            val b = ((p and 0xFF) + n).coerceIn(0, 255)
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(px, 0, w, 0, 0, w, h)
    }

    // ----------------------------------------------------------------------------------
    // Utilidad compartida
    // ----------------------------------------------------------------------------------

    /** Distancia de color 0..1 usada por la varita magica y el borrado por color. */
    fun colorDistance(a: Int, b: Int): Float {
        val dr = abs((a ushr 16 and 0xFF) - (b ushr 16 and 0xFF))
        val dg = abs((a ushr 8 and 0xFF) - (b ushr 8 and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        val da = abs((a ushr 24 and 0xFF) - (b ushr 24 and 0xFF))
        return (dr + dg + db + da) / (255f * 4f)
    }
}
