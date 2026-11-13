package com.dskmusic.pixa.core.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class BubbleStyle { SPEECH, THOUGHT, SHOUT, WHISPER, CAPTION, BOX }

/**
 * Bocadillo de comic. Todo en fracciones del lienzo (centro, ancho y punta de la cola
 * relativa al centro) y grosores en unidades de 1080 px del lado corto, asi que vale igual
 * para la vista previa que para el tamano de exportacion. El alto sale del texto.
 */
@kotlinx.serialization.Serializable
data class Bubble(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val style: BubbleStyle = BubbleStyle.SPEECH,
    val cx: Float = 0.5f,
    val cy: Float = 0.2f,
    val width: Float = 0.4f,
    val tailX: Float = -0.05f,
    val tailY: Float = 0.16f,
    val textSize: Float = 44f,
    val textColor: Int = 0xFF000000.toInt(),
    val fillColor: Int = 0xFFFFFFFF.toInt(),
    val strokeColor: Int = 0xFF000000.toInt(),
    val strokeWidth: Float = 5f,
    val bold: Boolean = true,
    val caps: Boolean = false,
    /** Familia de Google Fonts; por defecto una de estilo comic. */
    val font: String = "Comic Neue",
    /** Grados, alrededor del centro; gira cuerpo, texto y cola. */
    val rotation: Float = 0f,
    /** Margen interior del texto dentro del bocadillo (unidades de 1080 px): estrecha o ensancha el texto. */
    val padLeft: Float = 0f,
    val padRight: Float = 0f,
    val padTop: Float = 0f,
    val padBottom: Float = 0f,
)

/** Dibujo y medidas de los bocadillos. La vista previa y la exportacion usan lo mismo. */
object BubbleDraw {

    private class Built(
        val body: Path,
        val dots: List<FloatArray>,
        val text: StaticLayout,
        val textX: Float,
        val textY: Float,
        val bounds: RectF,
        val tip: Pair<Float, Float>,
        val strokePx: Float,
        val cx: Float,
        val cy: Float,
    )

    private fun build(b: Bubble, w: Int, h: Int, tf: (String) -> Typeface?): Built {
        val f = min(w, h) / 1080f
        val cx = b.cx * w
        val cy = b.cy * h
        val bw = (b.width * w).coerceAtLeast(80f * f)
        val caption = b.style == BubbleStyle.CAPTION
        // Parte del ancho / alto del cuerpo que ocupa el texto (el ovalo recorta las esquinas).
        val (kw, kh) = when (b.style) {
            BubbleStyle.CAPTION -> 1f to 1f
            BubbleStyle.BOX -> 0.88f to 0.82f
            BubbleStyle.SPEECH, BubbleStyle.WHISPER -> 0.72f to 0.68f
            else -> 0.64f to 0.6f
        }
        val pad = 14f * f
        val textW = ((if (caption) bw - 2 * pad else bw * kw) - (b.padLeft + b.padRight) * f)
            .coerceAtLeast(20f * f)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = b.textColor
            textSize = (b.textSize * f).coerceAtLeast(6f)
            typeface = tf(b.font)?.let { Typeface.create(it, if (b.bold) Typeface.BOLD else Typeface.NORMAL) }
                ?: if (b.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val txt = (if (b.caps) b.text.uppercase() else b.text).ifEmpty { " " }
        val layout = StaticLayout.Builder
            .obtain(txt, 0, txt.length, tp, textW.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        val th = layout.height.toFloat()
        val bh = (if (caption) th + 2 * pad else max(th / kh + 8f * f, bw * 0.2f)) +
            (b.padTop + b.padBottom) * f
        val a = bw / 2f
        val c = bh / 2f
        val tipX = cx + b.tailX * w
        val tipY = cy + b.tailY * h
        val outside = ((tipX - cx) / a).let { it * it } + ((tipY - cy) / c).let { it * it } > 1.3f
        val oval = RectF(cx - a, cy - c, cx + a, cy + c)

        val body = Path()
        val dots = ArrayList<FloatArray>()
        when (b.style) {
            BubbleStyle.CAPTION -> body.addRoundRect(oval, 10f * f, 10f * f, Path.Direction.CW)
            BubbleStyle.BOX -> {
                body.addRoundRect(oval, 24f * f, 24f * f, Path.Direction.CW)
                if (outside) body.op(tail(cx, cy, a, c, tipX, tipY, 0.22), Path.Op.UNION)
            }
            BubbleStyle.SPEECH, BubbleStyle.WHISPER -> {
                body.addOval(oval, Path.Direction.CW)
                if (outside) body.op(tail(cx, cy, a, c, tipX, tipY, 0.26), Path.Op.UNION)
            }
            BubbleStyle.SHOUT -> {
                val n = 16
                for (i in 0 until n * 2) {
                    val ang = PI * i / n
                    val k = if (i % 2 == 0) 1.1f else 0.84f
                    val x = cx + a * k * cos(ang).toFloat()
                    val y = cy + c * k * sin(ang).toFloat()
                    if (i == 0) body.moveTo(x, y) else body.lineTo(x, y)
                }
                body.close()
                if (outside) body.op(tail(cx, cy, a, c, tipX, tipY, 0.2), Path.Op.UNION)
            }
            BubbleStyle.THOUGHT -> {
                body.addOval(
                    RectF(cx - a * 0.86f, cy - c * 0.86f, cx + a * 0.86f, cy + c * 0.86f),
                    Path.Direction.CW,
                )
                val n = 14
                val r = Math.PI.toFloat() * (a + c) * 0.86f / n * 0.62f
                for (i in 0 until n) {
                    val ang = 2 * PI * i / n
                    val circle = Path().apply {
                        addCircle(
                            cx + a * 0.86f * cos(ang).toFloat(),
                            cy + c * 0.86f * sin(ang).toFloat(),
                            r, Path.Direction.CW,
                        )
                    }
                    body.op(circle, Path.Op.UNION)
                }
                if (outside) {
                    val t = atan2((tipY - cy) / c, (tipX - cx) / a)
                    val bx = cx + a * 1.05f * cos(t)
                    val by = cy + c * 1.05f * sin(t)
                    val base = min(a, c)
                    // Tres burbujas que se encogen camino de la punta.
                    listOf(0.3f to 0.2f, 0.62f to 0.13f, 1f to 0.08f).forEach { (p, rr) ->
                        dots += floatArrayOf(bx + (tipX - bx) * p, by + (tipY - by) * p, base * rr)
                    }
                }
            }
        }

        val sw = (b.strokeWidth * f).coerceAtLeast(0f)
        val bounds = RectF()
        body.computeBounds(bounds, true)
        if (outside) bounds.union(tipX, tipY)
        dots.forEach { bounds.union(it[0] - it[2], it[1] - it[2], it[0] + it[2], it[1] + it[2]) }
        bounds.inset(-sw, -sw)
        var tip = tipX to tipY
        if (b.rotation != 0f) {
            val m = Matrix().apply { setRotate(b.rotation, cx, cy) }
            m.mapRect(bounds)
            val pt = floatArrayOf(tipX, tipY)
            m.mapPoints(pt)
            tip = pt[0] to pt[1]
        }
        // El texto se desplaza hacia el lado con menos margen.
        val textX = cx - textW / 2f + (b.padLeft - b.padRight) / 2f * f
        val textY = cy - th / 2f + (b.padTop - b.padBottom) / 2f * f
        return Built(body, dots, layout, textX, textY, bounds, tip, sw, cx, cy)
    }

    /** Triangulo desde el borde del ovalo (hacia [tipX], [tipY]) hasta la punta. */
    private fun tail(cx: Float, cy: Float, a: Float, c: Float, tipX: Float, tipY: Float, half: Double): Path {
        val t = atan2((tipY - cy) / c, (tipX - cx) / a).toDouble()
        fun edge(ang: Double) = (cx + a * 0.95f * cos(ang).toFloat()) to (cy + c * 0.95f * sin(ang).toFloat())
        val (x1, y1) = edge(t - half)
        val (x2, y2) = edge(t + half)
        return Path().apply { moveTo(x1, y1); lineTo(tipX, tipY); lineTo(x2, y2); close() }
    }

    /** Caja del bocadillo (cuerpo + cola + contorno) en px del lienzo. */
    fun bounds(b: Bubble, w: Int, h: Int, tf: (String) -> Typeface? = { null }): RectF =
        build(b, w, h, tf).bounds

    /** Punta de la cola en px del lienzo. */
    fun tip(b: Bubble, w: Int, h: Int, tf: (String) -> Typeface? = { null }): Pair<Float, Float> =
        build(b, w, h, tf).tip

    /** Dibuja el bocadillo en coordenadas del lienzo (w x h). */
    fun draw(c: Canvas, b: Bubble, w: Int, h: Int, tf: (String) -> Typeface? = { null }) {
        val g = build(b, w, h, tf)
        c.save()
        if (b.rotation != 0f) c.rotate(b.rotation, g.cx, g.cy)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = b.fillColor
        c.drawPath(g.body, p)
        g.dots.forEach { c.drawCircle(it[0], it[1], it[2], p) }
        if (g.strokePx > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = g.strokePx
            p.strokeJoin = Paint.Join.ROUND
            p.color = b.strokeColor
            if (b.style == BubbleStyle.WHISPER) {
                p.pathEffect = DashPathEffect(floatArrayOf(g.strokePx * 2.4f, g.strokePx * 1.8f), 0f)
            }
            c.drawPath(g.body, p)
            p.pathEffect = null
            g.dots.forEach { c.drawCircle(it[0], it[1], it[2], p) }
        }
        c.save()
        c.translate(g.textX, g.textY)
        g.text.draw(c)
        c.restore()
        c.restore()
    }

    /** Bocadillo ya pintado en un bitmap del tamano de su caja, para el documento. */
    fun rasterize(b: Bubble, w: Int, h: Int, tf: (String) -> Typeface? = { null }): Pair<Bitmap, RectF> {
        val box = bounds(b, w, h, tf)
        val bw = box.width().toInt().coerceAtLeast(1)
        val bh = box.height().toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(-box.left, -box.top)
        draw(c, b, w, h, tf)
        return bmp to box
    }
}
