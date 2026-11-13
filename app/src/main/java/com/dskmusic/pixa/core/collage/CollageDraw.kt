package com.dskmusic.pixa.core.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Vista previa rapida del collage: dibuja todo directamente sobre un unico bitmap con
 * recortes de lienzo, en vez de montar el documento de capas y componerlo. El documento
 * (con sus marcos, mascaras de recorte y bitmaps de trabajo del tamano del lienzo por cada
 * hueco) sigue siendo lo que se exporta o se abre en el editor; esto solo sirve para que
 * encuadrar con los dedos sea fluido. La geometria es la misma ([CollageGeometry]), asi que
 * lo que se ve aqui es lo que sale.
 */
object CollageDraw {

    private const val EMPTY_SLOT = 0xFFCFD3D8.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun render(
        spec: CollageSpec,
        photos: List<CollagePhoto>,
        bitmapOf: (String) -> Bitmap?,
        budgetPx: Int,
        /** Bitmap de una pasada anterior del mismo tamano: se reutiliza en vez de reservar otro. */
        reuse: Bitmap? = null,
    ): Bitmap {
        val (w, h) = CollageGeometry.canvasSize(spec)
        val s = min(1f, sqrt(budgetPx / (w.toFloat() * h)))
        val ow = max(1, (w * s).roundToInt())
        val oh = max(1, (h * s).roundToInt())
        val out = reuse
            ?.takeIf { !it.isRecycled && it.isMutable && it.width == ow && it.height == oh }
            ?.also { it.eraseColor(0) }
            ?: Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        // Se dibuja en coordenadas del documento y el lienzo lo reduce.
        c.scale(ow / w.toFloat(), oh / h.toFloat())

        val f = min(w, h) / 1080f
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (spec.bgKind) {
            CollageBgKind.SOLID -> c.drawColor(spec.bgColor)
            CollageBgKind.GRADIENT -> {
                c.drawColor(WHITE)
                val colors = spec.bgGradient.ifEmpty { listOf(spec.bgColor, spec.bgColor) }.toIntArray()
                val positions = FloatArray(colors.size) { CollageGeometry.gradientPosition(spec, it, colors.size) }
                // Mismo eje que el relleno del documento: vertical por el centro y con el
                // largo de la mitad del lado mayor a cada lado.
                val half = max(w, h) / 2f
                val cx = w / 2f
                val cy = h / 2f
                val rad = Math.toRadians(spec.bgAngle.toDouble())
                val dx = kotlin.math.cos(rad).toFloat() * half
                val dy = kotlin.math.sin(rad).toFloat() * half
                fillPaint.shader = LinearGradient(
                    cx - dx, cy - dy, cx + dx, cy + dy, colors, positions, Shader.TileMode.CLAMP,
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)
                fillPaint.shader = null
            }
            CollageBgKind.BLUR -> {
                c.drawColor(WHITE)
                spec.blur?.let { b ->
                    bitmapOf(b.bitmapId)?.let { bmp ->
                        val sc = max(w / bmp.width.toFloat(), h / bmp.height.toFloat())
                        val m = Matrix().apply {
                            setScale(sc, sc)
                            postTranslate((w - bmp.width * sc) / 2f, (h - bmp.height * sc) / 2f)
                        }
                        c.drawBitmap(bmp, m, bitmapPaint)
                    }
                }
            }
        }

        CollageGeometry.slotRects(spec).forEachIndexed { i, r ->
            val rw = r.width()
            val rh = r.height()
            val radius = min(spec.radius * f, min(rw, rh) / 2f)
            val shape = spec.layout.shape(i)
            val slotPath = CollageGeometry.slotPath(shape, r, radius)

            if (spec.shadow) {
                fillPaint.color = WHITE
                fillPaint.setShadowLayer(18f * f, 0f, 8f * f, 0x73000000)
                c.drawPath(slotPath, fillPaint)
                fillPaint.clearShadowLayer()
            }
            fillPaint.color = EMPTY_SLOT
            c.drawPath(slotPath, fillPaint)

            photos.getOrNull(i)?.takeIf { !it.isEmpty }?.let { p ->
                bitmapOf(p.id)?.let { bmp ->
                    c.save()
                    c.clipPath(slotPath)
                    c.concat(CollageGeometry.photoTransform(r, p).toMatrix(p.width, p.height))
                    // La foto puede llegar reducida (copia para la vista previa): se estira
                    // a las dimensiones originales para que la transformacion siga valiendo.
                    c.scale(p.width / bmp.width.toFloat(), p.height / bmp.height.toFloat())
                    c.drawBitmap(bmp, 0f, 0f, bitmapPaint)
                    c.restore()
                }
            }

            if (spec.border > 0f) {
                val bw = (spec.border * f).coerceIn(1f, max(1f, min(rw, rh) / 2f - 1f))
                fillPaint.style = Paint.Style.STROKE
                fillPaint.strokeWidth = bw
                fillPaint.color = spec.borderColor
                val inner = RectF(r.left + bw / 2f, r.top + bw / 2f, r.right - bw / 2f, r.bottom - bw / 2f)
                c.drawPath(CollageGeometry.slotPath(shape, inner, max(0f, radius - bw / 2f)), fillPaint)
                fillPaint.style = Paint.Style.FILL
            }
        }
        spec.bubbles.forEach { BubbleDraw.draw(c, it, w, h, spec.fonts) }
        return out
    }
}
