package com.dskmusic.pixa.core.transform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.Anchor9
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Transformaciones destructivas de pixeles (requisito 9).
 *
 * Las transformaciones no destructivas de una capa viven en su Xf y las aplica el
 * renderizador; aqui estan las que cambian de verdad el bitmap: voltear, girar, inclinar,
 * perspectiva de cuatro esquinas, cambiar el tamano del lienzo y los espejos.
 */
object TransformOps {

    fun flipHorizontal(src: Bitmap): Bitmap = draw(src.width, src.height) { canvas ->
        canvas.scale(-1f, 1f, src.width / 2f, src.height / 2f)
        canvas.drawBitmap(src, 0f, 0f, filter())
    }

    fun flipVertical(src: Bitmap): Bitmap = draw(src.width, src.height) { canvas ->
        canvas.scale(1f, -1f, src.width / 2f, src.height / 2f)
        canvas.drawBitmap(src, 0f, 0f, filter())
    }

    /** Giro de 90 grados, positivo en sentido horario. Cambia ancho por alto. */
    fun rotate90(src: Bitmap, clockwise: Boolean = true): Bitmap {
        val out = BitmapUtils.create(src.height, src.width)
        val canvas = Canvas(out)
        if (clockwise) {
            canvas.translate(src.height.toFloat(), 0f)
            canvas.rotate(90f)
        } else {
            canvas.translate(0f, src.width.toFloat())
            canvas.rotate(-90f)
        }
        canvas.drawBitmap(src, 0f, 0f, filter())
        return out
    }

    fun rotate180(src: Bitmap): Bitmap = draw(src.width, src.height) { canvas ->
        canvas.rotate(180f, src.width / 2f, src.height / 2f)
        canvas.drawBitmap(src, 0f, 0f, filter())
    }

    /**
     * Giro libre que crece hasta que cabe la imagen entera.
     *
     * Util para girar una capa de forma destructiva sin cortar esquinas.
     */
    fun rotateFree(src: Bitmap, degrees: Float): Bitmap {
        val rad = Math.toRadians(degrees.toDouble())
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()
        val w = (src.width * c + src.height * s).roundToInt().coerceAtLeast(1)
        val h = (src.width * s + src.height * c).roundToInt().coerceAtLeast(1)
        val out = BitmapUtils.create(w, h)
        val canvas = Canvas(out)
        canvas.rotate(degrees, w / 2f, h / 2f)
        canvas.drawBitmap(src, (w - src.width) / 2f, (h - src.height) / 2f, filter())
        return out
    }

    /**
     * Endereza: gira y recorta al rectangulo mas grande sin bordes vacios (requisito 9).
     *
     * El factor de recorte es la formula del rectangulo inscrito en el girado; sin el, al
     * enderezar un horizonte quedarian cunas transparentes en las esquinas.
     */
    fun straighten(src: Bitmap, degrees: Float): Bitmap {
        if (abs(degrees) < 0.01f) return BitmapUtils.mutableCopy(src)
        val rad = Math.toRadians(abs(degrees).toDouble())
        val cosA = cos(rad)
        val sinA = sin(rad)
        val w = src.width.toDouble()
        val h = src.height.toDouble()
        val longer = max(w, h)
        val shorter = min(w, h)
        val cropW: Double
        val cropH: Double
        if (shorter <= 2 * sinA * cosA * longer || abs(sinA - cosA) < 1e-9) {
            val x = 0.5 * shorter / (if (w >= h) sinA else cosA)
            val y = 0.5 * shorter / (if (w >= h) cosA else sinA)
            cropW = if (w >= h) 2 * x * cosA else 2 * y * sinA
            cropH = if (w >= h) 2 * x * sinA else 2 * y * cosA
        } else {
            val cos2 = cosA * cosA - sinA * sinA
            cropW = (w * cosA - h * sinA) / cos2
            cropH = (h * cosA - w * sinA) / cos2
        }
        val outW = cropW.roundToInt().coerceIn(1, src.width)
        val outH = cropH.roundToInt().coerceIn(1, src.height)
        val out = BitmapUtils.create(outW, outH)
        val canvas = Canvas(out)
        canvas.rotate(degrees, outW / 2f, outH / 2f)
        canvas.drawBitmap(
            src,
            (outW - src.width) / 2f,
            (outH - src.height) / 2f,
            filter(),
        )
        return out
    }

    /** Inclinacion (skew) en grados sobre cada eje. */
    fun skew(src: Bitmap, degreesX: Float, degreesY: Float): Bitmap {
        val kx = kotlin.math.tan(Math.toRadians(degreesX.toDouble())).toFloat()
        val ky = kotlin.math.tan(Math.toRadians(degreesY.toDouble())).toFloat()
        val matrix = Matrix().apply { setSkew(kx, ky) }
        val bounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        matrix.mapRect(bounds)
        val out = BitmapUtils.create(
            bounds.width().roundToInt().coerceAtLeast(1),
            bounds.height().roundToInt().coerceAtLeast(1),
        )
        matrix.postTranslate(-bounds.left, -bounds.top)
        Canvas(out).drawBitmap(src, matrix, filter())
        return out
    }

    /**
     * Perspectiva de cuatro esquinas (requisito 9).
     *
     * [corners] son ocho numeros (x,y de arriba-izquierda, arriba-derecha, abajo-derecha y
     * abajo-izquierda) en pixeles del bitmap de salida. setPolyToPoly es lo que permite una
     * deformacion real de perspectiva y no un simple escalado.
     */
    fun perspective(src: Bitmap, corners: FloatArray, outWidth: Int, outHeight: Int): Bitmap {
        require(corners.size == 8) { "Se esperan 4 esquinas (8 numeros)" }
        val srcPoly = floatArrayOf(
            0f, 0f,
            src.width.toFloat(), 0f,
            src.width.toFloat(), src.height.toFloat(),
            0f, src.height.toFloat(),
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPoly, 0, corners, 0, 4)) {
            return BitmapUtils.mutableCopy(src)
        }
        val out = BitmapUtils.create(outWidth.coerceAtLeast(1), outHeight.coerceAtLeast(1))
        Canvas(out).drawBitmap(src, matrix, filter())
        return out
    }

    // ----------------------------------------------------------------------------------
    // Tamano de imagen y de lienzo
    // ----------------------------------------------------------------------------------

    enum class CanvasFill { TRANSPARENT, COLOR, STRETCH }

    /**
     * Cambia el tamano del lienzo sin escalar el contenido (requisito 9).
     *
     * [anchor] decide donde queda el contenido existente y [fill] que hacer con el espacio
     * nuevo: dejarlo transparente, rellenarlo de color, o estirar la imagen para cubrirlo.
     */
    fun resizeCanvas(
        src: Bitmap,
        width: Int,
        height: Int,
        anchor: Anchor9 = Anchor9.CENTER,
        fill: CanvasFill = CanvasFill.TRANSPARENT,
        color: Int = Color.WHITE,
    ): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val out = BitmapUtils.create(w, h)
        val canvas = Canvas(out)
        if (fill == CanvasFill.COLOR) canvas.drawColor(color)
        if (fill == CanvasFill.STRETCH) {
            canvas.drawBitmap(src, null, Rect(0, 0, w, h), filter())
            return out
        }
        val fx = if (anchor == Anchor9.FREE) 0.5f else anchor.fractionX
        val fy = if (anchor == Anchor9.FREE) 0.5f else anchor.fractionY
        val x = (w - src.width) * fx
        val y = (h - src.height) * fy
        canvas.drawBitmap(src, x, y, filter())
        return out
    }

    /** Cambia el tamano de la imagen escalando el contenido. */
    fun resizeImage(src: Bitmap, width: Int, height: Int): Bitmap =
        BitmapUtils.scale(src, width.coerceAtLeast(1), height.coerceAtLeast(1))

    /** Rectangulo centrado con la proporcion pedida, el mayor que cabe (recortes rapidos). */
    fun aspectCrop(width: Int, height: Int, ratioW: Float, ratioH: Float): Rect {
        if (ratioW <= 0f || ratioH <= 0f) return Rect(0, 0, width, height)
        val target = ratioW / ratioH
        val current = width.toFloat() / height.toFloat()
        return if (current > target) {
            val w = (height * target).roundToInt()
            val left = (width - w) / 2
            Rect(left, 0, left + w, height)
        } else {
            val h = (width / target).roundToInt()
            val top = (height - h) / 2
            Rect(0, top, width, top + h)
        }
    }

    // ----------------------------------------------------------------------------------
    // Espejo y caleidoscopio (requisito 9)
    // ----------------------------------------------------------------------------------

    enum class MirrorAxis { LEFT, RIGHT, TOP, BOTTOM }

    /** Espejo: se queda con media imagen y la refleja sobre la otra mitad. */
    fun mirror(src: Bitmap, axis: MirrorAxis): Bitmap {
        val out = BitmapUtils.create(src.width, src.height)
        val canvas = Canvas(out)
        val halfW = src.width / 2
        val halfH = src.height / 2
        when (axis) {
            MirrorAxis.LEFT -> {
                val half = BitmapUtils.crop(src, Rect(0, 0, halfW, src.height))
                canvas.drawBitmap(half, 0f, 0f, filter())
                canvas.save()
                canvas.scale(-1f, 1f, src.width / 2f, 0f)
                canvas.drawBitmap(half, 0f, 0f, filter())
                canvas.restore()
                half.recycle()
            }
            MirrorAxis.RIGHT -> {
                val half = BitmapUtils.crop(src, Rect(src.width - halfW, 0, src.width, src.height))
                canvas.drawBitmap(half, (src.width - halfW).toFloat(), 0f, filter())
                canvas.save()
                canvas.scale(-1f, 1f, src.width / 2f, 0f)
                canvas.drawBitmap(half, (src.width - halfW).toFloat(), 0f, filter())
                canvas.restore()
                half.recycle()
            }
            MirrorAxis.TOP -> {
                val half = BitmapUtils.crop(src, Rect(0, 0, src.width, halfH))
                canvas.drawBitmap(half, 0f, 0f, filter())
                canvas.save()
                canvas.scale(1f, -1f, 0f, src.height / 2f)
                canvas.drawBitmap(half, 0f, 0f, filter())
                canvas.restore()
                half.recycle()
            }
            MirrorAxis.BOTTOM -> {
                val half = BitmapUtils.crop(src, Rect(0, src.height - halfH, src.width, src.height))
                canvas.drawBitmap(half, 0f, (src.height - halfH).toFloat(), filter())
                canvas.save()
                canvas.scale(1f, -1f, 0f, src.height / 2f)
                canvas.drawBitmap(half, 0f, (src.height - halfH).toFloat(), filter())
                canvas.restore()
                half.recycle()
            }
        }
        return out
    }

    /**
     * Caleidoscopio: repite un sector circular girandolo alrededor del centro.
     *
     * Cada repeticion alterna el espejo, que es lo que hace que las costuras encajen y el
     * resultado parezca un caleidoscopio de verdad y no una rueda de copias.
     */
    fun kaleidoscope(src: Bitmap, segments: Int = 6): Bitmap {
        val n = segments.coerceIn(2, 24)
        val side = min(src.width, src.height)
        val out = BitmapUtils.create(side, side)
        val canvas = Canvas(out)
        val center = side / 2f
        val sweep = 360f / n

        // Sector de origen: un trozo de la imagen centrado, recortado en forma de tarta.
        val sector = BitmapUtils.create(side, side)
        val sectorCanvas = Canvas(sector)
        val wedge = android.graphics.Path().apply {
            moveTo(center, center)
            arcTo(RectF(0f, 0f, side.toFloat(), side.toFloat()), -sweep / 2f, sweep)
            close()
        }
        sectorCanvas.clipPath(wedge)
        val sx = (side - src.width) / 2f
        val sy = (side - src.height) / 2f
        sectorCanvas.drawBitmap(src, sx, sy, filter())

        for (i in 0 until n) {
            canvas.save()
            canvas.rotate(sweep * i, center, center)
            if (i % 2 == 1) canvas.scale(1f, -1f, center, center)
            canvas.drawBitmap(sector, 0f, 0f, filter())
            canvas.restore()
        }
        sector.recycle()
        return out
    }

    // ----------------------------------------------------------------------------------

    private fun filter() = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private inline fun draw(width: Int, height: Int, block: (Canvas) -> Unit): Bitmap {
        val out = BitmapUtils.create(width, height)
        block(Canvas(out))
        return out
    }
}
