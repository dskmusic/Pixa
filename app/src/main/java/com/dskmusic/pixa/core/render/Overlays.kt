package com.dskmusic.pixa.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.Anchor9
import com.dskmusic.pixa.core.model.FrameKind
import com.dskmusic.pixa.core.model.FrameSpec
import com.dskmusic.pixa.core.model.TextSpec
import com.dskmusic.pixa.core.model.WatermarkKind
import com.dskmusic.pixa.core.model.WatermarkSpec
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Marcas de agua y marcos decorativos (requisito 15).
 *
 * Las dos cosas se pintan encima de un bitmap ya compuesto, no como capas, porque en el
 * editor por lotes no hay documento: hay una lista de fotos a las que aplicar lo mismo.
 * En el editor completo la UI las anade tambien como capa cuando el usuario quiere
 * seguir moviendolas.
 */
object Overlays {

    // ----------------------------------------------------------------------------------
    // Marca de agua
    // ----------------------------------------------------------------------------------

    /** Dibuja la marca sobre una copia del bitmap y la devuelve. */
    fun applyWatermark(
        src: Bitmap,
        spec: WatermarkSpec,
        typeface: Typeface? = null,
        logoDir: File? = null,
    ): Bitmap {
        val out = BitmapUtils.mutableCopy(src)
        drawWatermark(Canvas(out), out.width, out.height, spec, typeface, logoDir)
        return out
    }

    /** Dibuja la marca en un canvas del tamano indicado. */
    fun drawWatermark(
        canvas: Canvas,
        width: Int,
        height: Int,
        spec: WatermarkSpec,
        typeface: Typeface? = null,
        logoDir: File? = null,
    ) {
        val stamp = buildStamp(spec, width, height, typeface, logoDir) ?: return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (spec.opacity.coerceIn(0f, 1f) * 255).roundToInt()
        }
        if (spec.tiled) {
            drawTiled(canvas, width, height, stamp, spec, paint)
        } else {
            val margin = min(width, height) * 0.02f
            val x = when (spec.anchor) {
                Anchor9.FREE -> spec.offsetX * width - stamp.width / 2f
                else -> {
                    val fx = spec.anchor.fractionX
                    val free = width - stamp.width - margin * 2
                    margin + free * fx + (if (fx > 0.5f) -spec.offsetX * width else
                        if (fx < 0.5f) spec.offsetX * width else 0f)
                }
            }
            val y = when (spec.anchor) {
                Anchor9.FREE -> spec.offsetY * height - stamp.height / 2f
                else -> {
                    val fy = spec.anchor.fractionY
                    val free = height - stamp.height - margin * 2
                    margin + free * fy + (if (fy > 0.5f) -spec.offsetY * height else
                        if (fy < 0.5f) spec.offsetY * height else 0f)
                }
            }
            canvas.save()
            canvas.rotate(spec.rotation, x + stamp.width / 2f, y + stamp.height / 2f)
            canvas.drawBitmap(stamp, x, y, paint)
            canvas.restore()
        }
        stamp.recycle()
    }

    private fun drawTiled(
        canvas: Canvas,
        width: Int,
        height: Int,
        stamp: Bitmap,
        spec: WatermarkSpec,
        paint: Paint,
    ) {
        val longSide = max(width, height).toFloat()
        val gap = longSide * (spec.tileSpacing.coerceIn(0f, 100f) / 100f)
        val stepX = stamp.width + gap
        val stepY = stamp.height + gap
        if (stepX <= 1f || stepY <= 1f) return
        canvas.save()
        // Se rota todo el mosaico y se dibuja de sobra para que al girar no queden esquinas
        // vacias.
        canvas.rotate(spec.rotation, width / 2f, height / 2f)
        val overscan = longSide
        var y = -overscan
        var row = 0
        while (y < height + overscan) {
            // Filas alternas desplazadas: el patron se ve mas repartido y menos en rejilla.
            var x = -overscan + if (row % 2 == 0) 0f else stepX / 2f
            while (x < width + overscan) {
                canvas.drawBitmap(stamp, x, y, paint)
                x += stepX
            }
            y += stepY
            row++
        }
        canvas.restore()
    }

    /** Rasteriza la marca una sola vez; luego se estampa tantas veces como haga falta. */
    private fun buildStamp(
        spec: WatermarkSpec,
        width: Int,
        height: Int,
        typeface: Typeface?,
        logoDir: File?,
    ): Bitmap? {
        val longSide = max(width, height).toFloat()
        val targetSize = longSide * (spec.sizePercent.coerceIn(1f, 60f) / 100f)
        return when (spec.kind) {
            WatermarkKind.TEXT -> {
                if (spec.text.isBlank()) return null
                val textSpec = TextSpec(
                    text = spec.text,
                    font = spec.font,
                    sizePx = targetSize,
                    color = spec.color,
                    boxWidth = longSide,
                    shadowRadius = if (spec.shadow) targetSize * 0.08f else 0f,
                    shadowDx = if (spec.shadow) targetSize * 0.04f else 0f,
                    shadowDy = if (spec.shadow) targetSize * 0.04f else 0f,
                )
                TextRenderer.render(textSpec, typeface)
            }
            WatermarkKind.LOGO -> {
                val name = spec.logoFile ?: return null
                val dir = logoDir ?: return null
                val file = File(dir, name)
                if (!file.exists()) return null
                val logo = BitmapFactory.decodeFile(file.absolutePath) ?: return null
                val scale = targetSize / max(logo.width, logo.height).toFloat()
                val w = (logo.width * scale).roundToInt().coerceAtLeast(1)
                val h = (logo.height * scale).roundToInt().coerceAtLeast(1)
                val scaled = BitmapUtils.scale(logo, w, h)
                if (scaled !== logo) logo.recycle()
                scaled
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // Marcos decorativos
    // ----------------------------------------------------------------------------------

    /**
     * Aplica un marco. Los marcos que anaden papel alrededor (Polaroid, pasepartu) hacen
     * crecer la imagen; el resto se dibuja dentro de los limites existentes.
     */
    fun applyFrame(src: Bitmap, spec: FrameSpec): Bitmap {
        if (spec.kind == FrameKind.NONE) return BitmapUtils.mutableCopy(src)
        val shortSide = min(src.width, src.height).toFloat()
        val w = (shortSide * (spec.widthPercent.coerceIn(0f, 30f) / 100f)).coerceAtLeast(1f)
        val radius = shortSide * (spec.cornerRadiusPercent.coerceIn(0f, 50f) / 100f)

        return when (spec.kind) {
            FrameKind.POLAROID -> polaroid(src, w, spec.color)
            FrameKind.INSET -> inset(src, w, spec.color, radius)
            else -> {
                val out = BitmapUtils.mutableCopy(src)
                val canvas = Canvas(out)
                val rect = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
                when (spec.kind) {
                    FrameKind.SOLID -> canvas.drawRect(
                        rect.inset(w / 2f),
                        strokePaint(spec.color, w),
                    )
                    FrameKind.ROUNDED -> {
                        val rounded = BitmapUtils.create(out.width, out.height)
                        val rc = Canvas(rounded)
                        rc.drawRoundRect(rect.inset(w), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG))
                        rc.drawBitmap(
                            out, 0f, 0f,
                            Paint().apply { blendMode = android.graphics.BlendMode.SRC_IN },
                        )
                        canvas.drawColor(spec.color)
                        canvas.drawBitmap(rounded, 0f, 0f, null)
                        rounded.recycle()
                    }
                    FrameKind.DOUBLE -> {
                        canvas.drawRect(rect.inset(w / 2f), strokePaint(spec.color, w))
                        canvas.drawRect(
                            rect.inset(w * 2.2f),
                            strokePaint(spec.secondaryColor, w * 0.4f),
                        )
                    }
                    FrameKind.DASHED -> canvas.drawRect(
                        rect.inset(w / 2f),
                        strokePaint(spec.color, w).apply {
                            pathEffect = DashPathEffect(floatArrayOf(w * 2.5f, w * 1.5f), 0f)
                        },
                    )
                    FrameKind.FILM -> drawFilm(canvas, out.width, out.height, w, spec)
                    FrameKind.VIGNETTE_FRAME -> drawSoftFrame(canvas, out.width, out.height, spec.color)
                    FrameKind.CORNERS -> drawCorners(canvas, rect, w, spec.color)
                    FrameKind.DOTTED -> canvas.drawRect(
                        rect.inset(w / 2f),
                        strokePaint(spec.color, w).apply {
                            strokeCap = Paint.Cap.ROUND
                            pathEffect = DashPathEffect(floatArrayOf(0.1f, w * 1.6f), 0f)
                        },
                    )
                    FrameKind.GRADIENT -> canvas.drawRect(
                        rect.inset(w / 2f),
                        strokePaint(spec.color, w).apply {
                            shader = LinearGradient(
                                0f, 0f, out.width.toFloat(), out.height.toFloat(),
                                spec.color, spec.secondaryColor, Shader.TileMode.CLAMP,
                            )
                        },
                    )
                    FrameKind.NEON -> {
                        canvas.drawRect(
                            rect.inset(w),
                            strokePaint(spec.color, w * 1.8f).apply {
                                maskFilter = BlurMaskFilter(w * 1.4f, BlurMaskFilter.Blur.NORMAL)
                                alpha = 160
                            },
                        )
                        canvas.drawRect(rect.inset(w), strokePaint(spec.color, w * 0.4f))
                    }
                    FrameKind.SHADOW -> {
                        val offset = w * 0.6f
                        canvas.drawRect(
                            RectF(
                                rect.left + offset, rect.top + offset,
                                rect.right - w + offset, rect.bottom - w + offset,
                            ),
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = spec.secondaryColor
                                alpha = 140
                                maskFilter = BlurMaskFilter(w * 0.8f, BlurMaskFilter.Blur.NORMAL)
                            },
                        )
                        canvas.drawRect(rect.inset(w / 2f), strokePaint(spec.color, w))
                    }
                    FrameKind.STAMP -> {
                        canvas.drawRect(rect.inset(w / 2f), strokePaint(spec.color, w))
                        // Perforaciones: medios circulos transparentes en el borde.
                        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                        }
                        val r = w * 0.45f
                        val step = r * 2.8f
                        var x = step / 2f
                        while (x < out.width) {
                            canvas.drawCircle(x, 0f, r, clear)
                            canvas.drawCircle(x, out.height.toFloat(), r, clear)
                            x += step
                        }
                        var y = step / 2f
                        while (y < out.height) {
                            canvas.drawCircle(0f, y, r, clear)
                            canvas.drawCircle(out.width.toFloat(), y, r, clear)
                            y += step
                        }
                    }
                    FrameKind.CHECKERED -> {
                        val a = Paint().apply { color = spec.color }
                        val b = Paint().apply { color = spec.secondaryColor }
                        val cell = w
                        val fw = out.width.toFloat()
                        val fh = out.height.toFloat()
                        var i = 0
                        var x = 0f
                        while (x < fw) {
                            val right = min(x + cell, fw)
                            canvas.drawRect(x, 0f, right, cell, if (i % 2 == 0) a else b)
                            canvas.drawRect(x, fh - cell, right, fh, if (i % 2 == 0) b else a)
                            x += cell
                            i++
                        }
                        var y = cell
                        i = 1
                        while (y < fh - cell) {
                            val bottom = min(y + cell, fh - cell)
                            canvas.drawRect(0f, y, cell, bottom, if (i % 2 == 0) a else b)
                            canvas.drawRect(fw - cell, y, fw, bottom, if (i % 2 == 0) b else a)
                            y += cell
                            i++
                        }
                    }
                    FrameKind.RAINBOW -> canvas.drawRect(
                        rect.inset(w / 2f),
                        strokePaint(spec.color, w).apply {
                            shader = android.graphics.SweepGradient(
                                out.width / 2f, out.height / 2f,
                                intArrayOf(
                                    0xFFFF1744.toInt(), 0xFFFFEA00.toInt(), 0xFF00E676.toInt(),
                                    0xFF00B0FF.toInt(), 0xFFD500F9.toInt(), 0xFFFF1744.toInt(),
                                ),
                                null,
                            )
                        },
                    )
                    FrameKind.ORNATE -> {
                        val gold = LinearGradient(
                            0f, 0f, out.width.toFloat(), out.height.toFloat(),
                            intArrayOf(0xFFF7D979.toInt(), 0xFFB8861B.toInt(), 0xFFFFF1B0.toInt(), 0xFF9C6A15.toInt()),
                            null, Shader.TileMode.CLAMP,
                        )
                        canvas.drawRect(rect.inset(w / 2f), strokePaint(spec.color, w).apply { shader = gold })
                        canvas.drawRect(rect.inset(w * 1.7f), strokePaint(spec.color, w * 0.25f).apply { shader = gold })
                    }
                    FrameKind.INNER_GLOW -> {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                        paint.shader = RadialGradient(
                            out.width / 2f, out.height / 2f, max(out.width, out.height) * 0.72f,
                            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, spec.color),
                            floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP,
                        )
                        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
                    }
                    FrameKind.DOUBLE_ROUNDED -> {
                        canvas.drawRoundRect(rect.inset(w / 2f), radius, radius, strokePaint(spec.color, w))
                        canvas.drawRoundRect(
                            rect.inset(w * 2f), radius * 0.8f, radius * 0.8f,
                            strokePaint(spec.secondaryColor, w * 0.35f),
                        )
                    }
                    FrameKind.BRACKETS -> {
                        canvas.drawRect(rect.inset(w * 1.5f), strokePaint(spec.secondaryColor, w * 0.15f))
                        drawCorners(canvas, rect.inset(w * 1.5f), w * 0.6f, spec.color)
                    }
                    else -> Unit
                }
                out
            }
        }
    }

    private fun RectF.inset(amount: Float): RectF =
        RectF(left + amount, top + amount, right - amount, bottom - amount)

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

    /** Marco tipo Polaroid: borde blanco con el pie mas alto. */
    private fun polaroid(src: Bitmap, width: Float, color: Int): Bitmap {
        val side = width.roundToInt().coerceAtLeast(1)
        val bottom = (width * 3.2f).roundToInt()
        val out = BitmapUtils.create(src.width + side * 2, src.height + side + bottom)
        val canvas = Canvas(out)
        canvas.drawColor(color)
        canvas.drawBitmap(src, side.toFloat(), side.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Pasepartu: la foto se reduce y queda un margen de color alrededor. */
    private fun inset(src: Bitmap, width: Float, color: Int, radius: Float): Bitmap {
        val out = BitmapUtils.create(src.width, src.height)
        val canvas = Canvas(out)
        canvas.drawColor(color)
        val inner = RectF(width, width, src.width - width, src.height - width)
        if (inner.width() <= 1f || inner.height() <= 1f) return out
        val scaled = BitmapUtils.scale(
            src,
            inner.width().roundToInt().coerceAtLeast(1),
            inner.height().roundToInt().coerceAtLeast(1),
        )
        if (radius > 0f) {
            val rounded = BitmapUtils.create(scaled.width, scaled.height)
            val rc = Canvas(rounded)
            rc.drawRoundRect(
                RectF(0f, 0f, scaled.width.toFloat(), scaled.height.toFloat()),
                radius, radius, Paint(Paint.ANTI_ALIAS_FLAG),
            )
            rc.drawBitmap(
                scaled, 0f, 0f,
                Paint().apply { blendMode = android.graphics.BlendMode.SRC_IN },
            )
            canvas.drawBitmap(rounded, inner.left, inner.top, Paint(Paint.FILTER_BITMAP_FLAG))
            rounded.recycle()
        } else {
            canvas.drawBitmap(scaled, inner.left, inner.top, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (scaled !== src) scaled.recycle()
        return out
    }

    /** Bordes de pelicula con perforaciones arriba y abajo. */
    private fun drawFilm(canvas: Canvas, width: Int, height: Int, w: Float, spec: FrameSpec) {
        val band = w * 2.4f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.color }
        canvas.drawRect(0f, 0f, width.toFloat(), band, fill)
        canvas.drawRect(0f, height - band, width.toFloat(), height.toFloat(), fill)
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.secondaryColor }
        val holeW = band * 0.55f
        val holeH = band * 0.4f
        val step = holeW * 2f
        var x = step / 2f
        while (x + holeW < width) {
            canvas.drawRoundRect(
                RectF(x, band * 0.3f, x + holeW, band * 0.3f + holeH),
                holeH * 0.25f, holeH * 0.25f, holePaint,
            )
            canvas.drawRoundRect(
                RectF(x, height - band * 0.3f - holeH, x + holeW, height - band * 0.3f),
                holeH * 0.25f, holeH * 0.25f, holePaint,
            )
            x += step
        }
    }

    /** Marco difuminado: el color entra desde los bordes y se desvanece. */
    private fun drawSoftFrame(canvas: Canvas, width: Int, height: Int, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = max(width, height) * 0.75f
        paint.shader = RadialGradient(
            width / 2f, height / 2f, radius,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, color),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /** Solo las esquinas, estilo album de fotos. */
    private fun drawCorners(canvas: Canvas, rect: RectF, w: Float, color: Int) {
        val paint = strokePaint(color, w)
        val len = min(rect.width(), rect.height()) * 0.18f
        val r = rect.inset(w / 2f)
        canvas.drawLine(r.left, r.top, r.left + len, r.top, paint)
        canvas.drawLine(r.left, r.top, r.left, r.top + len, paint)
        canvas.drawLine(r.right - len, r.top, r.right, r.top, paint)
        canvas.drawLine(r.right, r.top, r.right, r.top + len, paint)
        canvas.drawLine(r.left, r.bottom, r.left + len, r.bottom, paint)
        canvas.drawLine(r.left, r.bottom - len, r.left, r.bottom, paint)
        canvas.drawLine(r.right - len, r.bottom, r.right, r.bottom, paint)
        canvas.drawLine(r.right, r.bottom - len, r.right, r.bottom, paint)
    }

    /** Recorte util para el editor por lotes: dimensiones finales con marco aplicado. */
    fun framedSize(src: Bitmap, spec: FrameSpec): Rect {
        val shortSide = min(src.width, src.height).toFloat()
        val w = (shortSide * (spec.widthPercent.coerceIn(0f, 30f) / 100f)).coerceAtLeast(1f)
        return when (spec.kind) {
            FrameKind.POLAROID -> Rect(
                0, 0,
                src.width + (w * 2).roundToInt(),
                src.height + w.roundToInt() + (w * 3.2f).roundToInt(),
            )
            else -> Rect(0, 0, src.width, src.height)
        }
    }
}
