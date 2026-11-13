package com.dskmusic.pixa.core.render

import com.dskmusic.pixa.core.model.TextShape
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Size
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.TextAlign
import com.dskmusic.pixa.core.model.TextSpec
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Rasteriza una capa de texto (requisito 10).
 *
 * La capa guarda siempre el [TextSpec] editable; este objeto solo produce el bitmap que
 * se compone en el lienzo, asi que el texto sigue siendo editable indefinidamente hasta
 * que el usuario decide rasterizarlo a proposito.
 *
 * Tres modos de trazado:
 *  - normal: StaticLayout, con salto de linea dentro de [TextSpec.boxWidth].
 *  - arco:   una sola linea sobre un arco de circunferencia.
 *  - trayecto: una sola linea sobre el trazo que el usuario ha dibujado a mano.
 *
 * La fuente llega resuelta desde fuera (FontRepository) porque las Downloadable Fonts se
 * piden de forma asincrona: aqui nunca se bloquea esperando una descarga.
 */
object TextRenderer {

    /** Margen extra alrededor del texto para que contorno, sombra y caja no se recorten. */
    private fun padding(spec: TextSpec): Float {
        val shadow = if (spec.shadowRadius > 0f || spec.shadowDx != 0f || spec.shadowDy != 0f) {
            spec.shadowRadius + max(abs(spec.shadowDx), abs(spec.shadowDy))
        } else {
            0f
        }
        return totalOutline(spec) + shadow + spec.boxPadding + 4f
    }

    /** Grosor total hacia fuera: contorno base mas los adicionales. */
    private fun totalOutline(spec: TextSpec): Float =
        spec.outlineWidth + spec.extraOutlines.sumOf { it.width.coerceAtLeast(0f).toDouble() }.toFloat()

    /** Anillos de contorno como (grosor acumulado, color), del mas externo al mas interno. */
    private fun rings(spec: TextSpec): List<Pair<Float, Int>> {
        val list = ArrayList<Pair<Float, Int>>()
        var w = spec.outlineWidth
        if (w > 0f) list += w to spec.outlineColor
        for (o in spec.extraOutlines) {
            if (o.width <= 0f) continue
            w += o.width
            list += w to o.color
        }
        return list.asReversed()
    }

    fun displayText(spec: TextSpec): String =
        if (spec.allCaps) spec.text.uppercase() else spec.text

    /** TextPaint base con toda la tipografia del spec aplicada. */
    fun buildPaint(spec: TextSpec, typeface: Typeface?): TextPaint = TextPaint(
        Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG,
    ).apply {
        textSize = spec.sizePx.coerceAtLeast(1f)
        color = spec.color
        letterSpacing = spec.letterSpacingEm
        isUnderlineText = spec.underline
        isStrikeThruText = spec.strikethrough
        // El estilo sintetico cubre las familias que no traen cursiva o negrita propias.
        val style = when {
            spec.bold && spec.italic -> Typeface.BOLD_ITALIC
            spec.bold -> Typeface.BOLD
            spec.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        this.typeface = Typeface.create(typeface ?: Typeface.DEFAULT, style)
    }

    private fun alignOf(spec: TextSpec): Layout.Alignment = when (spec.align) {
        TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
        TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }

    private fun buildLayout(spec: TextSpec, paint: TextPaint): StaticLayout {
        val text = displayText(spec)
        val width = spec.boxWidth.roundToInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignOf(spec))
            .setLineSpacing(0f, spec.lineHeightMul.coerceAtLeast(0.5f))
            .setIncludePad(true)
            .build()
    }

    /** Tamano del bitmap que produciria [render], para colocar la capa sin rasterizar. */
    fun measure(spec: TextSpec, typeface: Typeface?): Size {
        if (spec.text.isEmpty()) return Size(1, 1)
        val pad = padding(spec)
        val paint = buildPaint(spec, typeface)

        if (spec.path != null && spec.path.size >= 4) {
            val bounds = RectF()
            pathFromPoints(spec.path).computeBounds(bounds, true)
            val extra = spec.sizePx + pad
            return Size(
                ceil(bounds.width() + extra * 2f).toInt().coerceAtLeast(1),
                ceil(bounds.height() + extra * 2f).toInt().coerceAtLeast(1),
            )
        }
        shapePath(spec, paint)?.let { shape ->
            val bounds = RectF()
            shape.computeBounds(bounds, true)
            val extra = spec.sizePx + pad
            return Size(
                ceil(bounds.width() + extra * 2f).toInt().coerceAtLeast(1),
                ceil(bounds.height() + extra * 2f).toInt().coerceAtLeast(1),
            )
        }
        if (spec.arc != 0f) {
            val bounds = RectF()
            arcPath(spec, paint).computeBounds(bounds, true)
            val extra = spec.sizePx + pad
            return Size(
                ceil(bounds.width() + extra * 2f).toInt().coerceAtLeast(1),
                ceil(bounds.height() + extra * 2f).toInt().coerceAtLeast(1),
            )
        }

        val layout = buildLayout(spec, paint)
        return Size(
            ceil(layout.width + pad * 2f).toInt().coerceAtLeast(1),
            ceil(layout.height + pad * 2f).toInt().coerceAtLeast(1),
        )
    }

    /**
     * Dibuja el texto en un bitmap nuevo. [store] solo se necesita si el relleno es una
     * imagen dentro de las letras.
     */
    fun render(spec: TextSpec, typeface: Typeface?, store: BitmapStore? = null): Bitmap {
        if (spec.text.isEmpty()) return BitmapUtils.create(1, 1)
        return when {
            spec.path != null && spec.path.size >= 4 ->
                renderOnPath(spec, typeface, store, pathFromPoints(spec.path))
            spec.shape != TextShape.NONE ->
                renderOnPath(
                    spec, typeface, store,
                    shapePath(spec, buildPaint(spec, typeface)) ?: arcPath(spec, buildPaint(spec, typeface)),
                )
            spec.arc != 0f ->
                renderOnPath(spec, typeface, store, arcPath(spec, buildPaint(spec, typeface)))
            else -> renderBlock(spec, typeface, store)
        }
    }

    // ----------------------------------------------------------------------------------
    // Texto en bloque
    // ----------------------------------------------------------------------------------

    private fun renderBlock(spec: TextSpec, typeface: Typeface?, store: BitmapStore?): Bitmap {
        val pad = padding(spec)
        val fillPaint = buildPaint(spec, typeface)
        val layout = buildLayout(spec, fillPaint)
        val size = Size(
            ceil(layout.width + pad * 2f).toInt().coerceAtLeast(1),
            ceil(layout.height + pad * 2f).toInt().coerceAtLeast(1),
        )
        val out = BitmapUtils.create(size.width, size.height)
        val canvas = Canvas(out)

        // Caja de fondo (requisito 10).
        if (Color.alpha(spec.boxColor) > 0) {
            val box = RectF(
                pad - spec.boxPadding,
                pad - spec.boxPadding,
                pad + layout.width + spec.boxPadding,
                pad + layout.height + spec.boxPadding,
            )
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.boxColor }
            canvas.drawRoundRect(box, spec.boxCornerRadius, spec.boxCornerRadius, boxPaint)
        }

        canvas.save()
        canvas.translate(pad, pad)

        // Contorno debajo del relleno, con union redondeada para que las puntas no piquen.
        // Contornos debajo del relleno, del mas externo al mas interno, con union redondeada
        // para que las puntas no piquen. La sombra solo la lleva el externo.
        val rings = rings(spec)
        for ((index, ring) in rings.withIndex()) {
            val strokePaint = buildPaint(spec, typeface).apply {
                style = Paint.Style.STROKE
                strokeWidth = ring.first * 2f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = ring.second
                if (index == 0) applyShadow(spec)
            }
            buildLayout(spec, strokePaint).draw(canvas)
        }
        if (rings.isEmpty()) fillPaint.applyShadow(spec)

        // Relleno: color plano, degradado o foto dentro de las letras.
        val shaderPaint = FillPainter.paintFor(
            spec.fill, layout.width.toFloat(), layout.height.toFloat(), store,
        )
        if (shaderPaint?.shader != null) {
            fillPaint.shader = shaderPaint.shader
        } else if (shaderPaint != null && spec.fill != null) {
            fillPaint.color = shaderPaint.color
        }
        // Si ya se dibujo el contorno, el relleno no debe repetir la sombra.
        if (rings.isNotEmpty()) fillPaint.clearShadowLayer()
        buildLayout(spec, fillPaint).draw(canvas)

        canvas.restore()
        return out
    }

    private fun TextPaint.applyShadow(spec: TextSpec) {
        if (spec.shadowRadius > 0f || spec.shadowDx != 0f || spec.shadowDy != 0f) {
            setShadowLayer(
                spec.shadowRadius.coerceAtLeast(0.01f),
                spec.shadowDx,
                spec.shadowDy,
                spec.shadowColor,
            )
        }
    }

    // ----------------------------------------------------------------------------------
    // Texto sobre trayecto y en arco
    // ----------------------------------------------------------------------------------

    /** Polilinea suavizada a partir de los puntos (x,y) que el usuario ha dibujado. */
    fun pathFromPoints(points: List<Float>): Path {
        val path = Path()
        if (points.size < 4) return path
        path.moveTo(points[0], points[1])
        var i = 2
        // Curvas cuadraticas entre puntos medios: evita los picos de una polilinea cruda.
        while (i + 3 < points.size) {
            val mx = (points[i] + points[i + 2]) / 2f
            val my = (points[i + 1] + points[i + 3]) / 2f
            path.quadTo(points[i], points[i + 1], mx, my)
            i += 2
        }
        path.lineTo(points[points.size - 2], points[points.size - 1])
        return path
    }

    /**
     * Arco sobre el que se asienta el texto. [TextSpec.arc] va de -1 a 1: positivo
     * curva hacia arriba (centro del circulo debajo del texto) y negativo hacia abajo.
     */
    private fun arcPath(spec: TextSpec, paint: Paint): Path {
        val text = displayText(spec).replace('\n', ' ')
        val textWidth = max(1f, paint.measureText(text))
        val amount = spec.arc.coerceIn(-1f, 1f)
        val sweep = abs(amount) * 170f
        val radius = textWidth / Math.toRadians(sweep.toDouble()).toFloat()

        val oval = RectF(-radius, -radius, radius, radius)
        val path = Path()
        if (amount > 0f) {
            // Parte superior del circulo, de izquierda a derecha.
            path.addArc(oval, 270f - sweep / 2f, sweep)
        } else {
            // Parte inferior, recorrida al reves para que el texto siga leyendose bien.
            path.addArc(oval, 90f + sweep / 2f, -sweep)
        }
        return path
    }

    /**
     * Trayecto de una forma predefinida: circulo (el texto da la vuelta, empezando arriba) u
     * onda senoidal. Null si [TextSpec.shape] es NONE. El largo del trayecto sale del propio
     * texto para que la forma lo envuelva exactamente.
     */
    private fun shapePath(spec: TextSpec, paint: Paint): Path? {
        if (spec.shape == TextShape.NONE) return null
        val text = displayText(spec).replace('\n', ' ')
        val textWidth = max(1f, paint.measureText(text))
        val amount = spec.shapeAmount.coerceIn(0f, 1f)
        val path = Path()
        when (spec.shape) {
            TextShape.CIRCLE -> {
                // De 120 a 360 grados: con 360 el texto da una vuelta completa.
                val sweep = 120f + 240f * amount
                val radius = textWidth / Math.toRadians(sweep.toDouble()).toFloat()
                path.addArc(RectF(-radius, -radius, radius, radius), 270f - sweep / 2f, sweep)
            }
            TextShape.WAVE -> {
                val amplitude = spec.sizePx * 1.2f * amount
                val steps = 64
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = t * textWidth
                    val y = amplitude * kotlin.math.sin(2.0 * Math.PI * 1.5 * t).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }
            TextShape.NONE -> return null
        }
        return path
    }

    private fun renderOnPath(
        spec: TextSpec,
        typeface: Typeface?,
        store: BitmapStore?,
        rawPath: Path,
    ): Bitmap {
        val fillPaint = buildPaint(spec, typeface)
        val text = displayText(spec).replace('\n', ' ')
        val pad = padding(spec) + spec.sizePx

        val bounds = RectF()
        rawPath.computeBounds(bounds, true)
        val width = ceil(bounds.width() + pad * 2f).toInt().coerceAtLeast(1)
        val height = ceil(bounds.height() + pad * 2f).toInt().coerceAtLeast(1)

        // El trayecto se recoloca para que quepa con su margen dentro del bitmap.
        val path = Path(rawPath)
        path.offset(-bounds.left + pad, -bounds.top + pad)

        val out = BitmapUtils.create(width, height)
        val canvas = Canvas(out)

        val length = PathMeasure(path, false).length
        val textWidth = fillPaint.measureText(text)
        val hOffset = when (spec.align) {
            TextAlign.LEFT -> 0f
            TextAlign.CENTER -> (length - textWidth) / 2f
            TextAlign.RIGHT -> length - textWidth
        }
        // El texto se levanta media altura para que el trazo quede centrado en la linea.
        val vOffset = -(fillPaint.ascent() + fillPaint.descent()) / 2f

        val rings = rings(spec)
        for ((index, ring) in rings.withIndex()) {
            val strokePaint = buildPaint(spec, typeface).apply {
                style = Paint.Style.STROKE
                strokeWidth = ring.first * 2f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = ring.second
                if (index == 0) applyShadow(spec)
            }
            canvas.drawTextOnPath(text, path, hOffset, vOffset, strokePaint)
        }
        if (rings.isEmpty()) fillPaint.applyShadow(spec)

        FillPainter.paintFor(spec.fill, width.toFloat(), height.toFloat(), store)?.let { p ->
            if (p.shader != null) fillPaint.shader = p.shader else fillPaint.color = p.color
        }
        if (rings.isNotEmpty()) fillPaint.clearShadowLayer()
        canvas.drawTextOnPath(text, path, hOffset, vOffset, fillPaint)

        return out
    }

    /**
     * Tamano de letra que hace que [text] quepa en [maxWidth] en una sola linea.
     * Lo usan los memes (requisito 20) y los posts sociales.
     */
    fun fitTextSize(
        text: String,
        typeface: Typeface?,
        maxWidth: Float,
        maxSize: Float,
        minSize: Float = 12f,
        bold: Boolean = false,
    ): Float {
        if (text.isEmpty() || maxWidth <= 0f) return maxSize
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(
                typeface ?: Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }
        // Busqueda binaria: 12 iteraciones dejan el tamano con error menor a medio punto.
        var lo = minSize
        var hi = maxSize
        repeat(12) {
            val mid = (lo + hi) / 2f
            paint.textSize = mid
            if (paint.measureText(text) <= maxWidth) lo = mid else hi = mid
        }
        return lo
    }
}
