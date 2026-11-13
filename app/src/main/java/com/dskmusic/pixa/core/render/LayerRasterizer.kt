package com.dskmusic.pixa.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Size
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.collage.BubbleDraw
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.ShapeSpec
import com.dskmusic.pixa.core.model.StrokeStyle
import com.dskmusic.pixa.core.model.TextSpec
import com.dskmusic.pixa.core.qr.QrGenerator
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Convierte el contenido de una capa en pixeles.
 *
 * Las capas raster ya son pixeles y se devuelven tal cual. Las capas generadas (texto,
 * forma, QR, emoji) se dibujan a partir de su especificacion y se guardan en una cache
 * pequena, porque el lienzo se recompone en cada gesto y volver a montar un StaticLayout
 * o un QR en cada fotograma se notaria.
 *
 * ponytail: la cache no recicla los bitmaps que expulsa. Desde API 26 los pixeles viven
 * en el heap de Java y los recoge el GC, y no reciclar evita el unico fallo grave posible
 * aqui: liberar un bitmap que otro hilo todavia esta pintando.
 */
private const val BUBBLE_REF = 1080

class LayerRasterizer(
    private val store: BitmapStore,
    /** Resolucion de fuentes; la aporta FontRepository, que gestiona las descargables. */
    private val typefaceFor: (FontRef) -> Typeface? = { null },
    private val cacheEntries: Int = 24,
) {

    private val cache = object : android.util.LruCache<String, Bitmap>(cacheEntries) {}

    fun clearCache() = cache.evictAll()

    /** Invalida la entrada de una capa concreta, tras editar su texto o su forma. */
    fun invalidate(layer: PixaLayer) {
        keyOf(layer)?.let { cache.remove(it) }
    }

    private fun keyOf(layer: PixaLayer): String? = when (val c = layer.content) {
        is LayerContent.Text -> "t:" + c.spec.toString() + "|" + c.spec.font.family
        is LayerContent.Shape -> "s:" + c.spec.toString()
        is LayerContent.Qr -> "q:" + c.spec.toString()
        is LayerContent.Emoji -> "e:" + c.emoji + "@" + c.sizePx
        // La disponibilidad de la fuente entra en la clave: al llegar, se vuelve a dibujar.
        is LayerContent.SpeechBubble ->
            "b:" + c.spec.toString() + "|" + (bubbleFont(c.spec.font) != null)
        else -> null
    }

    /**
     * Bitmap del contenido en su tamano intrinseco, sin la transformacion de la capa.
     * Devuelve null para las capas de relleno, que las pinta DocumentRenderer a tamano de
     * lienzo, y cuando el contenido no se puede resolver (bitmap ausente, QR invalido).
     */
    fun rasterize(layer: PixaLayer): Bitmap? {
        val content = layer.content
        if (content is LayerContent.Raster) return store.get(content.bitmapId)
        if (content is LayerContent.Fill) return null

        val key = keyOf(layer)
        if (key != null) {
            cache.get(key)?.let { if (!it.isRecycled) return it }
        }

        val bmp = try {
            when (content) {
                is LayerContent.Text -> TextRenderer.render(
                    content.spec, typefaceFor(content.spec.font), store,
                )
                is LayerContent.Shape -> renderShape(content.spec)
                is LayerContent.Qr -> QrGenerator.generate(content.spec, store)
                is LayerContent.Emoji -> renderEmoji(content.emoji, content.sizePx)
                is LayerContent.SpeechBubble ->
                    BubbleDraw.rasterize(content.spec, BUBBLE_REF, BUBBLE_REF, ::bubbleFont).first
                else -> null
            }
        } catch (e: QrGenerator.QrException) {
            // Un QR con datos invalidos no debe tumbar el render del documento.
            null
        } catch (e: BitmapUtils.LoadException) {
            null
        } ?: return null

        if (key != null) cache.put(key, bmp)
        return bmp
    }

    /**
     * Miniatura de una capa de texto para la lista de capas: siempre negro sobre blanco,
     * sin importar el color, el degradado, el contorno o la caja que tenga configurados
     * (requisito del usuario). En modo oscuro la miniatura normal se volvia ilegible
     * porque el texto podia llevar cualquier color sobre un fondo tambien oscuro.
     */
    fun rasterizeTextPreview(spec: TextSpec): Bitmap = TextRenderer.render(
        spec.copy(
            color = Color.BLACK,
            fill = null,
            outlineWidth = 0f,
            shadowRadius = 0f,
            boxColor = Color.TRANSPARENT,
        ),
        typefaceFor(spec.font),
        store,
    )

    /** Tamano intrinseco sin rasterizar, para colocar y seleccionar capas. */
    fun intrinsicSize(layer: PixaLayer, docWidth: Int, docHeight: Int): Size {
        return when (val c = layer.content) {
            is LayerContent.Raster -> store.get(c.bitmapId)
                ?.let { Size(it.width, it.height) } ?: Size(1, 1)
            is LayerContent.Text -> TextRenderer.measure(c.spec, typefaceFor(c.spec.font))
            is LayerContent.Shape -> shapeSize(c.spec)
            is LayerContent.Qr -> Size(c.spec.sizePx, c.spec.sizePx)
            is LayerContent.Emoji -> emojiSize(c.sizePx)
            is LayerContent.Fill -> Size(docWidth, docHeight)
            is LayerContent.SpeechBubble -> rasterize(layer)
                ?.let { Size(it.width, it.height) } ?: Size(1, 1)
        }
    }

    fun bubbleFont(family: String): Typeface? =
        typefaceFor(FontRef(family, FontRef.Source.GOOGLE))

    // ----------------------------------------------------------------------------------
    // Formas vectoriales
    // ----------------------------------------------------------------------------------

    private fun shapeSize(spec: ShapeSpec): Size {
        // El trazo se dibuja centrado en el contorno, asi que sobresale la mitad por lado.
        val extra = ceil(spec.strokeWidth).toInt()
        return Size(
            (spec.width.roundToInt() + extra).coerceAtLeast(1),
            (spec.height.roundToInt() + extra).coerceAtLeast(1),
        )
    }

    private fun renderShape(spec: ShapeSpec): Bitmap {
        val size = shapeSize(spec)
        val out = BitmapUtils.create(size.width, size.height)
        val canvas = Canvas(out)
        val half = spec.strokeWidth / 2f
        canvas.translate(half, half)

        val path = PathShapes.shapePath(spec)

        FillPainter.paintFor(spec.fill, spec.width, spec.height, store)?.let { paint ->
            paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)
        }
        if (spec.strokeWidth > 0f && Color.alpha(spec.strokeColor) > 0) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = spec.strokeWidth
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = spec.strokeColor
                pathEffect = when (spec.strokeStyle) {
                    StrokeStyle.SOLID -> null
                    StrokeStyle.DASHED -> DashPathEffect(
                        floatArrayOf(spec.strokeWidth * 2.5f, spec.strokeWidth * 1.5f),
                        0f,
                    )
                    // ponytail: "on" casi cero + cabo redondo dibuja puntos, no rayas.
                    StrokeStyle.DOTTED -> DashPathEffect(
                        floatArrayOf(0.1f, spec.strokeWidth * 1.6f),
                        0f,
                    )
                }
            }
            canvas.drawPath(path, stroke)
        }
        return out
    }

    // ----------------------------------------------------------------------------------
    // Emojis
    // ----------------------------------------------------------------------------------

    private fun emojiPaint(sizePx: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx.coerceIn(8f, 2048f)
        typeface = Typeface.DEFAULT
    }

    private fun emojiSize(sizePx: Float): Size {
        val s = sizePx.coerceIn(8f, 2048f).roundToInt()
        // Los emojis de color son cuadrados; un margen del 12 % evita recortes en
        // fuentes que dibujan algo mas grande que el avance declarado.
        val side = (s * 1.12f).roundToInt().coerceAtLeast(1)
        return Size(side, side)
    }

    private fun renderEmoji(emoji: String, sizePx: Float): Bitmap {
        val size = emojiSize(sizePx)
        val out = BitmapUtils.create(size.width, size.height)
        val paint = emojiPaint(sizePx)
        val bounds = Rect()
        paint.getTextBounds(emoji, 0, emoji.length, bounds)
        val x = (size.width - paint.measureText(emoji)) / 2f
        val y = size.height / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(out).drawText(emoji, x, y, paint)
        return out
    }
}
