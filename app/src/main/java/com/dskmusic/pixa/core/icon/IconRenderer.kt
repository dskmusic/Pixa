package com.dskmusic.pixa.core.icon

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.dskmusic.pixa.core.model.FontRef
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pinta un [IconDoc] en un bitmap cuadrado. El lienzo es de 100x100 unidades y se escala
 * al tamano pedido, asi la misma imagen sale igual a 512 que a 1024.
 */
object IconRenderer {

    private const val CANVAS = 100f

    private val pathCache = HashMap<String, Path>()

    /** Trazado del icono Material en su cuadricula de 24x24. */
    private fun iconPath(name: String): Path = pathCache.getOrPut(name) {
        val out = Path()
        fun walk(node: VectorNode) {
            when (node) {
                is VectorGroup -> node.forEach { walk(it) }
                is VectorPath -> out.addPath(
                    PathParser().addPathNodes(node.pathData).toPath().asAndroidPath(),
                )
                else -> Unit
            }
        }
        walk(IconCatalog.find(name).root)
        out
    }

    fun render(
        doc: IconDoc,
        px: Int,
        image: (String) -> Bitmap?,
        font: (FontRef) -> Typeface?,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = px / CANVAS
        c.scale(s, s)
        val b = doc.back
        val frame = RectF(0f, 0f, CANVAS, CANVAS)
        val radius = if (b.borderOn) b.radius.coerceIn(0f, 50f) else 0f

        c.save()
        c.clipPath(Path().apply { addRoundRect(frame, radius, radius, Path.Direction.CW) })
        if (b.colorOn) c.drawRect(frame, fill(b.color, b.opacity))
        if (b.grad.on) c.drawRect(frame, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient(b.grad, 0f, 0f, CANVAS, CANVAS)
        })
        if (b.imageOn) b.imageUri?.let(image)?.let { bm ->
            c.save()
            c.rotate(b.imageRot, CANVAS / 2, CANVAS / 2)
            drawFit(c, bm, RectF(frame), b.imageFit, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = pct(b.imageOpacity)
            })
            c.restore()
        }
        doc.images.filter { it.on }.forEach { drawImage(c, it, image) }
        doc.icons.filter { it.on }.forEach { drawIcon(c, it, s) }
        doc.texts.filter { it.on }.forEach { drawText(c, it, font) }
        c.restore()

        if (b.borderOn && b.borderWidth > 0f) {
            val w = b.borderWidth
            val r = RectF(w / 2, w / 2, CANVAS - w / 2, CANVAS - w / 2)
            c.drawRoundRect(r, max(0f, radius - w / 2), max(0f, radius - w / 2), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = w
                color = b.borderColor
            })
        }
        return bmp
    }

    // ---------------------------------------------------------------- utilidades

    private fun pct(v: Float) = (v.coerceIn(0f, 100f) * 2.55f).roundToInt()

    private fun withAlpha(color: Int, opacity: Float): Int {
        val a = (((color ushr 24) and 0xFF) * opacity.coerceIn(0f, 100f) / 100f).roundToInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun fill(color: Int, opacity: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = withAlpha(color, opacity)
    }

    private fun anchor(a: Int, w: Float, h: Float) = (a % 3) / 2f * w to (a / 3) / 2f * h

    private fun gradient(g: Grad, l: Float, t: Float, w: Float, h: Float): Shader {
        var (x1, y1) = anchor(g.a1, w, h)
        var (x2, y2) = anchor(g.a2, w, h)
        if (x1 == x2 && y1 == y2) x2 += 0.01f // degenerado: evita un shader invalido
        return LinearGradient(
            l + x1, t + y1, l + x2, t + y2,
            intArrayOf(withAlpha(g.c1, g.o1), withAlpha(g.c2, g.o2)),
            floatArrayOf(0f, g.stop.coerceIn(0.001f, 1f)),
            Shader.TileMode.CLAMP,
        )
    }

    private fun blur(paint: Paint, radius: Float, scale: Float) {
        if (radius > 0f) paint.maskFilter = BlurMaskFilter(radius * scale, BlurMaskFilter.Blur.NORMAL)
    }

    /** Coloca un elemento de tamano [ew]x[eh] segun su Xf y lo pinta con [body] en coordenadas locales. */
    private fun element(c: Canvas, xf: Xf, ew: Float, eh: Float, body: Canvas.() -> Unit) {
        val (ax, ay) = anchor(xf.align, CANVAS - ew, CANVAS - eh)
        c.save()
        c.translate(ax + xf.px + ew / 2, ay + xf.py + eh / 2)
        c.rotate(xf.rot)
        c.scale(if (xf.flipH) -1f else 1f, if (xf.flipV) -1f else 1f)
        c.translate(-ew / 2, -eh / 2)
        c.saveLayer(
            RectF(-CANVAS * 2, -CANVAS * 2, CANVAS * 3, CANVAS * 3),
            Paint().apply { alpha = pct(xf.opacity) },
        )
        c.body()
        c.restore()
        c.restore()
    }

    /** Tamano total y desplazamiento del contenido cuando hay caja de fondo. */
    private data class Layout(val w: Float, val h: Float, val dx: Float, val dy: Float)

    private fun layout(p: Plate, cw: Float, ch: Float): Layout {
        if (!p.on) return Layout(cw, ch, 0f, 0f)
        val (l, t, r, b) = p.pad
        val w = if (p.w > 0f) p.w else cw + l + r
        val h = if (p.h > 0f) p.h else ch + t + b
        return Layout(w, h, l + (w - l - r - cw) / 2, t + (h - t - b - ch) / 2)
    }

    private fun drawPlate(c: Canvas, p: Plate, w: Float, h: Float, scale: Float) {
        if (!p.on) return
        val r = RectF(0f, 0f, w, h)
        val rad = p.radius.coerceAtLeast(0f)
        if (p.elevation > 0f) {
            val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(BLACK, 35f) }
            blur(sh, p.elevation, scale)
            c.drawRoundRect(RectF(r).apply { offset(0f, p.elevation / 2) }, rad, rad, sh)
        }
        c.drawRoundRect(r, rad, rad, fill(p.color, p.opacity))
        if (p.borderWidth > 0f) {
            val bw = p.borderWidth
            c.drawRoundRect(
                RectF(bw / 2, bw / 2, w - bw / 2, h - bw / 2), rad, rad,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = bw; color = p.borderColor
                },
            )
        }
    }

    // ---------------------------------------------------------------- icono

    private fun drawIcon(c: Canvas, it: IconItem, scale: Float) {
        val lay = layout(it.plate, it.size, it.size)
        val k = it.size / 24f
        val glyph = Path(iconPath(it.icon)).apply { transform(Matrix().apply { setScale(k, k) }) }
        element(c, it.xf, lay.w, lay.h) {
            drawPlate(this, it.plate, lay.w, lay.h, scale)
            translate(lay.dx, lay.dy)
            val ls = it.longShadow
            if (ls.on && ls.length > 0f) {
                val rad = ls.angle * PI.toFloat() / 180f
                val long = Path()
                val steps = ceil(ls.length * 2).toInt()
                for (i in 1..steps) {
                    val d = i / 2f
                    long.addPath(glyph, cos(rad) * d, sin(rad) * d)
                }
                val p = fill(ls.color, ls.opacity)
                blur(p, ls.blur, scale)
                drawPath(long, p)
            }
            val sh = it.shadow
            if (sh.on) {
                val p = fill(sh.color, sh.opacity)
                blur(p, sh.blur, scale)
                save(); translate(sh.dx, sh.dy); drawPath(glyph, p); restore()
            }
            val p = fill(it.color, 100f)
            if (it.grad.on) p.shader = gradient(it.grad, 0f, 0f, it.size, it.size)
            drawPath(glyph, p)
        }
    }

    // ---------------------------------------------------------------- imagen

    /** Dibuja [bm] dentro de [box] segun el ajuste, recortado a la caja. */
    private fun drawFit(c: Canvas, bm: Bitmap, box: RectF, fit: Fit, paint: Paint) {
        val bw = bm.width.toFloat()
        val bh = bm.height.toFloat()
        val sx = box.width() / bw
        val sy = box.height() / bh
        val (kx, ky) = when (fit) {
            Fit.FILL -> sx to sy
            Fit.CONTAIN -> min(sx, sy).let { it to it }
            Fit.COVER -> max(sx, sy).let { it to it }
            Fit.FIT_WIDTH -> sx to sx
            Fit.FIT_HEIGHT -> sy to sy
        }
        val dw = bw * kx
        val dh = bh * ky
        val dst = RectF(
            box.centerX() - dw / 2, box.centerY() - dh / 2,
            box.centerX() + dw / 2, box.centerY() + dh / 2,
        )
        c.save()
        c.clipRect(box)
        c.drawBitmap(bm, null, dst, paint)
        c.restore()
    }

    private fun drawImage(c: Canvas, it: ImageItem, image: (String) -> Bitmap?) {
        val bm = it.uri?.let(image) ?: return
        element(c, it.xf, it.w, it.h) {
            val box = RectF(0f, 0f, it.w, it.h)
            val rad = if (it.borderOn) it.radius.coerceAtLeast(0f) else 0f
            save()
            clipPath(Path().apply { addRoundRect(box, rad, rad, Path.Direction.CW) })
            val p = Paint(Paint.FILTER_BITMAP_FLAG)
            if (it.filterOn) p.colorFilter = PorterDuffColorFilter(it.filterColor, mode(it.blend))
            drawFit(this, bm, box, it.fit, p)
            restore()
            if (it.borderOn && it.borderWidth > 0f) {
                val bw = it.borderWidth
                drawRoundRect(
                    RectF(bw / 2, bw / 2, it.w - bw / 2, it.h - bw / 2), rad, rad,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = bw; color = it.borderColor
                    },
                )
            }
        }
    }

    private fun mode(b: Blend) = when (b) {
        Blend.MULTIPLY -> PorterDuff.Mode.MULTIPLY
        Blend.SCREEN -> PorterDuff.Mode.SCREEN
        Blend.OVERLAY -> PorterDuff.Mode.OVERLAY
        Blend.DARKEN -> PorterDuff.Mode.DARKEN
        Blend.LIGHTEN -> PorterDuff.Mode.LIGHTEN
        Blend.ADD -> PorterDuff.Mode.ADD
        Blend.SRC_IN -> PorterDuff.Mode.SRC_IN
        Blend.SRC_ATOP -> PorterDuff.Mode.SRC_ATOP
    }

    // ---------------------------------------------------------------- texto

    private fun drawText(c: Canvas, it: TextItem, font: (FontRef) -> Typeface?) {
        if (it.text.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            textSize = it.size.coerceAtLeast(0.5f)
            typeface = Typeface.create(font(it.font) ?: Typeface.DEFAULT, it.weight.coerceIn(100, 900), it.italic)
            letterSpacing = (it.letter - 1f) * 0.1f
        }
        val space = paint.measureText(" ") * it.word
        val lines = it.text.split("\n").map { line -> line.split(" ") }
        val widths = lines.map { words ->
            words.sumOf { w -> paint.measureText(w).toDouble() }.toFloat() + space * (words.size - 1)
        }
        val lineH = paint.fontSpacing
        val cw = widths.maxOrNull() ?: 0f
        val ch = lineH * lines.size
        val lay = layout(it.plate, cw, ch)
        element(c, it.xf, lay.w, lay.h) {
            drawPlate(this, it.plate, lay.w, lay.h, 1f)
            translate(lay.dx, lay.dy)
            paint.color = it.color
            if (it.grad.on) paint.shader = gradient(it.grad, 0f, 0f, cw, ch)
            val line = Paint(paint).apply { strokeWidth = it.size * 0.07f * it.decoThick }
            lines.forEachIndexed { i, words ->
                val base = i * lineH - paint.fontMetrics.ascent
                var x = when (it.lineAlign) { 0 -> 0f; 2 -> cw - widths[i]; else -> (cw - widths[i]) / 2 }
                val x0 = x
                words.forEach { w ->
                    drawText(w, x, base, paint)
                    x += paint.measureText(w) + space
                }
                val y = when (it.deco) {
                    Deco.UNDERLINE -> base + it.size * 0.12f
                    Deco.STRIKE -> base - it.size * 0.3f
                    Deco.OVERLINE -> base + paint.fontMetrics.ascent
                    Deco.NONE -> null
                }
                if (y != null) drawLine(x0, y, x0 + widths[i], y, line)
            }
        }
    }
}
