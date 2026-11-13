package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlin.math.roundToInt

/**
 * Recortes y fondos (requisito 8): borrar fondo por color, aplicar la mascara del
 * recorte automatico y pincel de restaurar o borrar bordes.
 */
object Cutout {

    /**
     * Borra el fondo por color.
     *
     * [tolerance] es la distancia a partir de la cual un pixel se considera distinto, y
     * [softness] la anchura de la zona de transicion: sin ella el recorte sale con el
     * borde de sierra tipico de recortar con umbral duro.
     */
    fun eraseColor(
        src: Bitmap,
        color: Int,
        tolerance: Float,
        softness: Float = 0.12f,
    ): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val tol = tolerance.coerceIn(0f, 1f)
        val soft = softness.coerceIn(0f, 0.5f)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val d = Adjust.colorDistance(px[i], color)
            val keep = when {
                d <= tol -> 0f
                soft <= 0f || d >= tol + soft -> 1f
                else -> (d - tol) / soft
            }
            if (keep >= 1f) continue
            val a = ((px[i] ushr 24 and 0xFF) * keep).roundToInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (px[i] and 0x00FFFFFF)
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Detecta el color de fondo mirando las cuatro esquinas.
     *
     * Se promedia un cuadrado pequeno de cada esquina en vez de leer un solo pixel, para
     * que un pixel de ruido no decida el color de todo el recorte.
     */
    fun guessBackgroundColor(src: Bitmap, sample: Int = 8): Int {
        val s = sample.coerceIn(1, min(src.width, src.height))
        val corners = listOf(
            Rect(0, 0, s, s),
            Rect(src.width - s, 0, src.width, s),
            Rect(0, src.height - s, s, src.height),
            Rect(src.width - s, src.height - s, src.width, src.height),
        )
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        val buffer = IntArray(s * s)
        for (rect in corners) {
            src.getPixels(buffer, 0, s, rect.left, rect.top, s, s)
            for (c in buffer) {
                r += Color.red(c)
                g += Color.green(c)
                b += Color.blue(c)
                n++
            }
        }
        if (n == 0) return Color.WHITE
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun min(a: Int, b: Int) = if (a < b) a else b

    /**
     * Aplica una mascara de recorte (la que devuelve ML Kit, o cualquier otra en escala de
     * grises) al alfa de la imagen.
     *
     * La mascara puede venir con otra resolucion que la foto: se escala antes de usarla.
     */
    fun applyMask(src: Bitmap, mask: Bitmap, feather: Float = 1.5f): Bitmap {
        val scaled = if (mask.width != src.width || mask.height != src.height) {
            BitmapUtils.scale(mask, src.width, src.height)
        } else {
            mask
        }
        val blurred = if (feather > 0f) Blur.blur(scaled, feather) else scaled
        val out = BitmapUtils.mutableCopy(src)
        Canvas(out).drawBitmap(
            blurred, 0f, 0f,
            Paint().apply { blendMode = android.graphics.BlendMode.DST_IN },
        )
        if (blurred !== scaled) blurred.recycle()
        if (scaled !== mask) scaled.recycle()
        return out
    }

    /**
     * Convierte una mascara de confianza (0..1 en float, como la de segmentacion) en un
     * bitmap de alfa listo para [applyMask].
     */
    fun confidenceToMask(
        confidences: FloatArray,
        width: Int,
        height: Int,
        threshold: Float = 0.5f,
    ): Bitmap {
        val px = IntArray(width * height)
        val t = threshold.coerceIn(0f, 1f)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val c = confidences.getOrElse(i) { 0f }
            // Rampa alrededor del umbral: bordes suaves en vez de un corte binario.
            val a = ((c - t + 0.1f) / 0.2f).coerceIn(0f, 1f)
            px[i] = ((a * 255).roundToInt() shl 24)
            }
        }
        val bmp = BitmapUtils.create(width, height)
        bmp.setPixels(px, 0, width, 0, 0, width, height)
        return bmp
    }

    // ----------------------------------------------------------------------------------
    // Pinceles de retoque del recorte (requisito 8)
    // ----------------------------------------------------------------------------------

    /** Borra con el pincel: deja transparente lo que toca el trazo. */
    fun eraseStroke(target: Bitmap, stroke: Path, radius: Float, hardness: Float = 0.8f) {
        val paint = strokePaint(radius, hardness)
        paint.blendMode = android.graphics.BlendMode.DST_OUT
        paint.color = Color.BLACK
        Canvas(target).drawPath(stroke, paint)
    }

    /**
     * Restaura con el pincel: vuelve a traer los pixeles del original donde pasa el trazo.
     *
     * Se pinta el trazo en una mascara aparte y con ella se recorta una copia del original;
     * hacerlo asi es lo que permite que el pincel tenga borde suave en vez de dejar el
     * canto del trazo marcado.
     */
    fun restoreStroke(
        target: Bitmap,
        original: Bitmap,
        stroke: Path,
        radius: Float,
        hardness: Float = 0.8f,
    ) {
        val mask = BitmapUtils.create(target.width, target.height)
        Canvas(mask).drawPath(stroke, strokePaint(radius, hardness).apply { color = Color.BLACK })
        val patch = if (original.width == target.width && original.height == target.height) {
            BitmapUtils.mutableCopy(original)
        } else {
            BitmapUtils.scale(original, target.width, target.height)
        }
        Canvas(patch).drawBitmap(
            mask, 0f, 0f,
            Paint().apply { blendMode = android.graphics.BlendMode.DST_IN },
        )
        Canvas(target).drawBitmap(patch, 0f, 0f, null)
        patch.recycle()
        mask.recycle()
    }

    private fun strokePaint(radius: Float, hardness: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            val soft = (1f - hardness.coerceIn(0f, 1f)) * radius
            if (soft > 0.5f) maskFilter = BlurMaskFilter(soft, BlurMaskFilter.Blur.NORMAL)
        }
}
