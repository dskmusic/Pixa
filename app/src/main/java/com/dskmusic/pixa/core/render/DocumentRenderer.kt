package com.dskmusic.pixa.core.render

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.LayerStyle
import com.dskmusic.pixa.core.model.MaskRef
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Composicion del documento (requisitos 6 y 7).
 *
 * Recorre las capas de abajo arriba aplicando, en este orden: estilos de capa (sombra,
 * resplandor, contorno), el contenido, la mascara de la capa, la mascara de recorte, la
 * opacidad y el modo de fusion.
 *
 * Decisiones que explican la forma del codigo:
 *
 *  - La fusion y la opacidad viajan en el Paint con el que se dibuja la capa, asi que el
 *    caso normal no necesita ningun bitmap intermedio.
 *  - Solo se crea un bitmap del tamano del lienzo cuando hace falta de verdad: cuando la
 *    capa tiene mascara propia, o cuando es la base de un grupo de recorte y hay que
 *    conservar su alfa para las capas que se recortan contra ella.
 *  - Una mascara se interpreta por su canal alfa, no por su luminosidad. Las herramientas
 *    de mascara pintan alfa (blanco = opaco, negro = borrar), de modo que el recorte se
 *    resuelve con un simple DST_IN y "invertir" es una matriz de color sobre el alfa.
 */
class DocumentRenderer(
    private val store: BitmapStore,
    private val rasterizer: LayerRasterizer,
) {

    /**
     * Compone el documento a [scale] (1 = tamano real).
     * [onlyLayerIds] permite exportar una sola capa o la seleccion (requisito 5).
     */
    fun render(
        doc: PixaDocument,
        scale: Float = 1f,
        includeBackground: Boolean = true,
        onlyLayerIds: Set<String>? = null,
    ): Bitmap {
        val w = max(1, (doc.width * scale).roundToInt())
        val h = max(1, (doc.height * scale).roundToInt())
        val out = BitmapUtils.create(w, h)
        val canvas = Canvas(out)

        if (includeBackground && !doc.background.transparent) {
            canvas.drawColor(doc.background.color)
        }
        drawLayers(canvas, doc, scale, onlyLayerIds)
        return out
    }

    /** Render rapido para los gestos: resolucion reducida con presupuesto de pixeles. */
    fun renderPreview(doc: PixaDocument, budgetPx: Int = 1_600_000): Bitmap {
        val scale = BitmapUtils.previewScale(doc.width, doc.height, budgetPx)
        return render(doc, scale)
    }

    /** Una sola capa sobre fondo transparente, para exportarla por separado. */
    fun renderSingleLayer(doc: PixaDocument, layerId: String, scale: Float = 1f): Bitmap =
        render(doc, scale, includeBackground = false, onlyLayerIds = setOf(layerId))

    // ----------------------------------------------------------------------------------
    // Recorrido de capas
    // ----------------------------------------------------------------------------------

    private fun drawLayers(
        canvas: Canvas,
        doc: PixaDocument,
        scale: Float,
        onlyLayerIds: Set<String>?,
    ) {
        val visible = doc.layers.filter { layer ->
            doc.isEffectivelyVisible(layer) &&
                layer.opacity > 0f &&
                (onlyLayerIds == null || layer.id in onlyLayerIds)
        }

        // Alfa de la capa base del grupo de recorte activo, si hay alguno.
        var clipSource: Bitmap? = null

        for ((i, layer) in visible.withIndex()) {
            val base = clipSource
            if (layer.clipToBelow && base != null) {
                // Capa recortada: se compone aparte y se recorta al alfa de la base.
                val tmp = renderLayerToCanvas(layer, doc, scale) ?: continue
                applyAlphaMask(tmp, base, inverted = false)
                canvas.drawBitmap(tmp, 0f, 0f, composePaint(layer))
                // Bitmap de trabajo del tamano del lienzo: se libera al instante. Con un
                // collage de nueve fotos serian decenas de bitmaps enormes sin reciclar.
                tmp.recycle()
                // La base se conserva: varias capas pueden recortarse contra la misma.
                continue
            }

            val nextClips = visible.getOrNull(i + 1)?.clipToBelow == true
            if (nextClips) {
                // Esta capa va a servir de base de recorte, asi que se necesita su alfa.
                val tmp = renderLayerToCanvas(layer, doc, scale)
                clipSource?.recycle()
                clipSource = tmp
                if (tmp != null) canvas.drawBitmap(tmp, 0f, 0f, composePaint(layer))
            } else {
                clipSource?.recycle()
                clipSource = null
                drawLayerDirect(canvas, layer, doc, scale)
            }
        }
        clipSource?.recycle()
    }

    /** Paint con la opacidad y el modo de fusion de la capa. */
    private fun composePaint(layer: PixaLayer): Paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        blendMode = layer.blend.androidMode
    }

    /**
     * Dibuja la capa directamente sobre el lienzo. Si tiene mascara propia se abre una
     * capa fuera de pantalla con saveLayer, que es lo que permite aplicar DST_IN sin
     * afectar a lo que ya hay pintado debajo.
     */
    private fun drawLayerDirect(canvas: Canvas, layer: PixaLayer, doc: PixaDocument, scale: Float) {
        val mask = layer.mask?.takeIf { it.enabled && store.get(it.bitmapId) != null }
        if (mask == null) {
            drawContent(canvas, layer, doc, scale, composePaint(layer))
            return
        }

        val saved = canvas.saveLayer(null, composePaint(layer))
        drawContent(canvas, layer, doc, scale, Paint(Paint.FILTER_BITMAP_FLAG))
        drawMask(canvas, layer, doc, scale, mask)
        canvas.restoreToCount(saved)
    }

    /** Compone la capa en un bitmap del tamano de salida, con sus estilos y su mascara. */
    private fun renderLayerToCanvas(layer: PixaLayer, doc: PixaDocument, scale: Float): Bitmap? {
        val w = max(1, (doc.width * scale).roundToInt())
        val h = max(1, (doc.height * scale).roundToInt())
        val out = BitmapUtils.create(w, h)
        val canvas = Canvas(out)

        drawContent(canvas, layer, doc, scale, Paint(Paint.FILTER_BITMAP_FLAG))
        layer.mask
            ?.takeIf { it.enabled && store.get(it.bitmapId) != null }
            ?.let { drawMask(canvas, layer, doc, scale, it) }
        return out
    }

    /** Matriz que lleva el contenido de la capa a coordenadas de salida. */
    private fun outputMatrix(layer: PixaLayer, w: Int, h: Int, scale: Float): Matrix =
        layer.transform.toMatrix(w, h).apply {
            if (scale != 1f) postScale(scale, scale)
        }

    /** Contenido de la capa, con los estilos de capa pintados antes para que queden detras. */
    private fun drawContent(
        canvas: Canvas,
        layer: PixaLayer,
        doc: PixaDocument,
        scale: Float,
        paint: Paint,
    ) {
        val content = layer.content
        if (content is LayerContent.Fill) {
            // Una capa de relleno cubre el lienzo entero: no hay bitmap que rasterizar.
            val fillPaint = FillPainter.paintFor(
                content.spec, doc.width.toFloat(), doc.height.toFloat(), store,
            ) ?: return
            fillPaint.alpha = paint.alpha
            fillPaint.blendMode = paint.blendMode
            val saved = canvas.save()
            if (scale != 1f) canvas.scale(scale, scale)
            canvas.concat(layer.transform.toMatrix(doc.width, doc.height))
            canvas.drawRect(0f, 0f, doc.width.toFloat(), doc.height.toFloat(), fillPaint)
            canvas.restoreToCount(saved)
            return
        }

        val bmp = rasterizer.rasterize(layer) ?: return
        val matrix = outputMatrix(layer, bmp.width, bmp.height, scale)

        layer.style?.takeIf { !it.isEmpty }?.let { style ->
            drawStyles(canvas, bmp, matrix, style, scale, paint.alpha)
        }
        canvas.drawBitmap(bmp, matrix, paint)
    }

    private fun drawMask(
        canvas: Canvas,
        layer: PixaLayer,
        doc: PixaDocument,
        scale: Float,
        mask: MaskRef,
    ) {
        val maskBmp = store.get(mask.bitmapId) ?: return
        // La mascara comparte sistema de coordenadas con el contenido de la capa.
        val reference = if (layer.content is LayerContent.Fill) {
            Size2(doc.width, doc.height)
        } else {
            rasterizer.rasterize(layer)?.let { Size2(it.width, it.height) }
                ?: Size2(maskBmp.width, maskBmp.height)
        }
        val matrix = outputMatrix(layer, reference.w, reference.h, scale)
        // Si el tamano de la mascara no coincide con el del contenido, se estira a el.
        if (maskBmp.width != reference.w || maskBmp.height != reference.h) {
            matrix.preScale(
                reference.w.toFloat() / maskBmp.width,
                reference.h.toFloat() / maskBmp.height,
            )
        }
        canvas.drawBitmap(maskBmp, matrix, maskPaint(mask.inverted))
    }

    private data class Size2(val w: Int, val h: Int)

    /** Aplica el alfa de [mask] sobre [target], ambos del tamano de salida. */
    private fun applyAlphaMask(target: Bitmap, mask: Bitmap, inverted: Boolean) {
        Canvas(target).drawBitmap(mask, 0f, 0f, maskPaint(inverted))
    }

    private fun maskPaint(inverted: Boolean): Paint = Paint().apply {
        blendMode = BlendMode.DST_IN
        if (inverted) {
            // Solo se invierte el canal alfa; el color de la mascara es irrelevante.
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, -1f, 255f,
            )))
        }
    }

    // ----------------------------------------------------------------------------------
    // Estilos de capa
    // ----------------------------------------------------------------------------------

    /**
     * Sombra, resplandor y contorno (requisito 7).
     *
     * Los tres se construyen a partir del alfa del contenido con extractAlpha: se obtiene
     * la silueta exacta de la capa, incluido el texto y las formas con transparencia, sin
     * tener que volver a dibujar el contenido.
     */
    private fun drawStyles(
        canvas: Canvas,
        bmp: Bitmap,
        matrix: Matrix,
        style: LayerStyle,
        scale: Float,
        layerAlpha: Int,
    ) {
        // Orden de atras hacia delante: sombra, resplandor y contorno.
        val k = matrix.mapRadius(1f).coerceAtLeast(1e-4f)
        if (style.shadowEnabled) {
            val offset = IntArray(2)
            val blur = Paint().apply {
                maskFilter = BlurMaskFilter(
                    (style.shadowRadius.coerceAtLeast(0.5f) * scale / k).coerceAtLeast(0.1f),
                    BlurMaskFilter.Blur.NORMAL,
                )
            }
            val alpha = bmp.extractAlpha(blur, offset)
            val m = Matrix(matrix).apply {
                preTranslate(offset[0].toFloat(), offset[1].toFloat())
                postTranslate(style.shadowDx * scale, style.shadowDy * scale)
            }
            canvas.drawBitmap(alpha, m, Paint().apply {
                color = style.shadowColor
                this.alpha = layerAlpha
            })
        }

        if (style.glowEnabled && style.glowRadius > 0f) {
            val offset = IntArray(2)
            val blur = Paint().apply {
                maskFilter = BlurMaskFilter(
                    (style.glowRadius.coerceAtLeast(0.5f) * scale / k).coerceAtLeast(0.1f),
                    BlurMaskFilter.Blur.NORMAL,
                )
            }
            val alpha = bmp.extractAlpha(blur, offset)
            val m = Matrix(matrix).apply { preTranslate(offset[0].toFloat(), offset[1].toFloat()) }
            val paint = Paint().apply {
                color = style.glowColor
                this.alpha = layerAlpha
            }
            // Dos pasadas: un resplandor de una sola capa queda demasiado debil.
            canvas.drawBitmap(alpha, m, paint)
            canvas.drawBitmap(alpha, m, paint)
        }

        if (style.strokeEnabled && style.strokeWidth > 0f) {
            val alpha = bmp.extractAlpha()
            val paint = Paint().apply {
                color = style.strokeColor
                this.alpha = layerAlpha
            }
            // El contorno se forma repitiendo la silueta alrededor: 24 direcciones no
            // dejan huecos visibles y es mucho mas barato que dilatar el alfa a mano.
            val r = style.strokeWidth * scale
            for (i in 0 until 24) {
                val a = 2.0 * Math.PI * i / 24.0
                val m = Matrix(matrix).apply {
                    postTranslate((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
                }
                canvas.drawBitmap(alpha, m, paint)
            }
        }
    }
}
