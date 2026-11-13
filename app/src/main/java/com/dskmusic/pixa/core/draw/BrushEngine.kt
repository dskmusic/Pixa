package com.dskmusic.pixa.core.draw

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.selection.Selection
import com.dskmusic.pixa.core.selection.SelectionTools
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Herramientas de dibujo (requisito 11). */
enum class BrushKind { BRUSH, PENCIL, AIRBRUSH, ERASER }

/** Simetria del pincel (requisito 11). */
enum class SymmetryMode { NONE, VERTICAL, HORIZONTAL, BOTH, RADIAL }

data class BrushSettings(
    val kind: BrushKind = BrushKind.BRUSH,
    val size: Float = 24f,
    /** 0 = borde muy difuso, 1 = borde duro. */
    val hardness: Float = 0.8f,
    /** Opacidad final del trazo completo. */
    val opacity: Float = 1f,
    /** Cuanta pintura suelta cada pasada; por debajo de 1 el trazo se acumula. */
    val flow: Float = 1f,
    val color: Int = Color.BLACK,
    /** Suavizado de la linea, 0 = sigue el dedo tal cual. */
    val smoothing: Float = 0.5f,
    val symmetry: SymmetryMode = SymmetryMode.NONE,
    val radialSegments: Int = 6,
)

/**
 * Trazo en curso.
 *
 * El trazo se pinta en un bitmap aparte y solo se vuelca sobre la capa al levantar el
 * dedo. Es lo que hace que la opacidad se aplique al trazo entero: si se pintara directo,
 * cada pasada se sumaria sobre la anterior y un trazo al 50 % acabaria casi opaco donde se
 * cruza consigo mismo.
 */
class BrushStroke(
    private val width: Int,
    private val height: Int,
    private val settings: BrushSettings,
    /** Limita el trazo a la seleccion activa, si hay alguna. */
    private val selection: Selection? = null,
) {

    private val layer: Bitmap = BitmapUtils.create(width, height)
    private val canvas = Canvas(layer)
    private val paint = buildPaint()

    private var lastX = 0f
    private var lastY = 0f
    private var startedAt = false
    private var dirty = false

    /** Puntos reales del dedo, para el suavizado. */
    private var prevRawX = 0f
    private var prevRawY = 0f

    val bitmap: Bitmap get() = layer

    private fun buildPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = settings.size
        // El borrador tambien pinta: lo que cambia es como se vuelca el trazo al final.
        color = if (settings.kind == BrushKind.ERASER) Color.BLACK else settings.color
        alpha = (settings.flow.coerceIn(0.02f, 1f) * 255).roundToInt()
        when (settings.kind) {
            BrushKind.PENCIL -> Unit // borde duro, sin difuminado
            BrushKind.AIRBRUSH -> {
                maskFilter = BlurMaskFilter(settings.size * 0.6f, BlurMaskFilter.Blur.NORMAL)
                alpha = (settings.flow.coerceIn(0.02f, 1f) * 120).roundToInt()
            }
            BrushKind.BRUSH, BrushKind.ERASER -> {
                val soft = (1f - settings.hardness.coerceIn(0f, 1f)) * settings.size * 0.5f
                if (soft > 0.5f) maskFilter = BlurMaskFilter(soft, BlurMaskFilter.Blur.NORMAL)
            }
        }
    }

    fun start(x: Float, y: Float) {
        lastX = x
        lastY = y
        prevRawX = x
        prevRawY = y
        startedAt = true
        // Un toque sin arrastre tiene que dejar punto, no nada.
        drawSegment(Path().apply {
            moveTo(x, y)
            lineTo(x + 0.01f, y)
        })
        dirty = true
    }

    fun moveTo(x: Float, y: Float) {
        if (!startedAt) {
            start(x, y)
            return
        }
        val s = settings.smoothing.coerceIn(0f, 0.95f)
        // Media ponderada con el punto anterior: amortigua el temblor del dedo sin
        // retrasar el trazo de forma visible.
        val sx = prevRawX + (x - prevRawX) * (1f - s)
        val sy = prevRawY + (y - prevRawY) * (1f - s)
        prevRawX = sx
        prevRawY = sy
        if (hypot(sx - lastX, sy - lastY) < 1f) return

        val segment = Path().apply {
            moveTo(lastX, lastY)
            lineTo(sx, sy)
        }
        drawSegment(segment)
        lastX = sx
        lastY = sy
        dirty = true
    }

    private fun drawSegment(segment: Path) {
        canvas.drawPath(segment, paint)
        when (settings.symmetry) {
            SymmetryMode.NONE -> Unit
            SymmetryMode.VERTICAL -> drawMirrored(segment, flipX = true, flipY = false)
            SymmetryMode.HORIZONTAL -> drawMirrored(segment, flipX = false, flipY = true)
            SymmetryMode.BOTH -> {
                drawMirrored(segment, flipX = true, flipY = false)
                drawMirrored(segment, flipX = false, flipY = true)
                drawMirrored(segment, flipX = true, flipY = true)
            }
            SymmetryMode.RADIAL -> {
                val n = settings.radialSegments.coerceIn(2, 24)
                for (i in 1 until n) {
                    canvas.save()
                    canvas.rotate(360f / n * i, width / 2f, height / 2f)
                    canvas.drawPath(segment, paint)
                    canvas.restore()
                }
            }
        }
    }

    private fun drawMirrored(segment: Path, flipX: Boolean, flipY: Boolean) {
        canvas.save()
        canvas.scale(
            if (flipX) -1f else 1f,
            if (flipY) -1f else 1f,
            width / 2f,
            height / 2f,
        )
        canvas.drawPath(segment, paint)
        canvas.restore()
    }

    /**
     * Vuelca el trazo sobre la capa y libera el bitmap temporal.
     *
     * El borrador usa DST_OUT: el trazo se convierte en agujero en vez de en pintura.
     */
    fun commit(target: Bitmap) {
        if (!dirty) {
            layer.recycle()
            return
        }
        selection?.let { sel ->
            if (!sel.isEmpty) {
                val mask = sel.toAlphaBitmap()
                Canvas(layer).drawBitmap(
                    mask, 0f, 0f,
                    Paint().apply { blendMode = android.graphics.BlendMode.DST_IN },
                )
                mask.recycle()
            }
        }
        val paintOut = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (settings.opacity.coerceIn(0f, 1f) * 255).roundToInt()
            if (settings.kind == BrushKind.ERASER) {
                blendMode = android.graphics.BlendMode.DST_OUT
            }
        }
        Canvas(target).drawBitmap(layer, 0f, 0f, paintOut)
        layer.recycle()
    }

    /** Descarta el trazo (gesto cancelado). */
    fun discard() {
        if (!layer.isRecycled) layer.recycle()
    }
}

object BrushEngine {

    /**
     * Bote de pintura (requisito 11).
     *
     * Se reutiliza la varita magica para decidir la zona y se expande un pixel: sin eso
     * queda un halo del color viejo en el borde, porque los pixeles intermedios del
     * antialias no llegan a la tolerancia.
     */
    fun bucketFill(
        src: Bitmap,
        x: Int,
        y: Int,
        color: Int,
        tolerance: Float = 0.12f,
        contiguous: Boolean = true,
    ): Bitmap {
        val area = SelectionTools.magicWand(src, x, y, tolerance, contiguous).grown(1)
        if (area.isEmpty) return BitmapUtils.mutableCopy(src)
        return SelectionTools.fill(src, area, color)
    }

    /** Degradado dibujado a mano: el usuario marca inicio y fin. */
    fun drawGradient(
        target: Bitmap,
        shader: Shader,
        selection: Selection? = null,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        if (selection == null || selection.isEmpty) {
            Canvas(target).drawRect(
                0f, 0f, target.width.toFloat(), target.height.toFloat(), paint,
            )
            return
        }
        val layer = BitmapUtils.create(target.width, target.height)
        Canvas(layer).drawRect(0f, 0f, layer.width.toFloat(), layer.height.toFloat(), paint)
        val mask = selection.toAlphaBitmap()
        Canvas(layer).drawBitmap(
            mask, 0f, 0f,
            Paint().apply { blendMode = android.graphics.BlendMode.DST_IN },
        )
        Canvas(target).drawBitmap(layer, 0f, 0f, null)
        mask.recycle()
        layer.recycle()
    }

    /** Cuentagotas: color del pixel, o transparente si cae fuera. */
    fun pickColor(src: Bitmap, x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= src.width || y >= src.height) return Color.TRANSPARENT
        return src.getPixel(x, y)
    }

    /**
     * Cuentagotas con promedio de un area pequena. Mas fiable sobre fotos con ruido que
     * leer un unico pixel.
     */
    fun pickColorAveraged(src: Bitmap, x: Int, y: Int, radius: Int = 2): Int {
        val x0 = (x - radius).coerceIn(0, src.width - 1)
        val y0 = (y - radius).coerceIn(0, src.height - 1)
        val x1 = (x + radius + 1).coerceIn(1, src.width)
        val y1 = (y + radius + 1).coerceIn(1, src.height)
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 0 || h <= 0) return Color.TRANSPARENT
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, x0, y0, w, h)
        var a = 0L
        var r = 0L
        var g = 0L
        var b = 0L
        for (c in px) {
            a += c ushr 24 and 0xFF
            r += Color.red(c)
            g += Color.green(c)
            b += Color.blue(c)
        }
        val n = px.size
        return Color.argb((a / n).toInt(), (r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
}
