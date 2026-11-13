package com.dskmusic.pixa.core.selection

import com.dskmusic.pixa.core.image.Parallel
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.image.Adjust
import java.nio.ByteBuffer

/**
 * Herramientas que construyen selecciones (requisito 8): rectangulo, elipse, lazo libre,
 * lazo poligonal, varita magica y seleccion por color.
 *
 * Las figuras se rasterizan con Canvas, asi que el suavizado de bordes y las curvas del
 * lazo salen gratis y con la misma calidad que el resto del dibujo.
 */
object SelectionTools {

    // ----------------------------------------------------------------------------------
    // Figuras
    // ----------------------------------------------------------------------------------

    fun rect(width: Int, height: Int, r: RectF): Selection =
        fromPath(width, height, Path().apply { addRect(r, Path.Direction.CW) })

    fun ellipse(width: Int, height: Int, r: RectF): Selection =
        fromPath(width, height, Path().apply { addOval(r, Path.Direction.CW) })

    /** Lazo libre o poligonal: en ambos casos llega un trayecto ya cerrado. */
    fun fromPath(width: Int, height: Int, path: Path): Selection {
        val closed = Path(path).apply { close() }
        return rasterize(width, height) { canvas ->
            canvas.drawPath(closed, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            })
        }
    }

    fun polygon(width: Int, height: Int, points: List<Float>): Selection {
        if (points.size < 6) return Selection.empty(width, height)
        val path = Path()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i + 1 < points.size) {
            path.lineTo(points[i], points[i + 1])
            i += 2
        }
        path.close()
        return fromPath(width, height, path)
    }

    /**
     * Rasteriza en una mascara de un byte por pixel.
     *
     * Se dibuja sobre ALPHA_8 para no gastar cuatro veces la memoria en lienzos grandes,
     * con vuelta a ARGB_8888 si el sistema rellena las filas y el volcado directo no
     * cuadraria.
     */
    private inline fun rasterize(width: Int, height: Int, draw: (Canvas) -> Unit): Selection {
        val alpha = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        if (alpha.rowBytes == width) {
            draw(Canvas(alpha))
            val mask = ByteArray(width * height)
            alpha.copyPixelsToBuffer(ByteBuffer.wrap(mask))
            alpha.recycle()
            return Selection.of(width, height, mask)
        }
        alpha.recycle()
        val argb = BitmapUtils.create(width, height)
        draw(Canvas(argb))
        val out = Selection.fromAlphaArgb(argb)
        argb.recycle()
        return out
    }

    // ----------------------------------------------------------------------------------
    // Varita magica y seleccion por color
    // ----------------------------------------------------------------------------------

    /**
     * Varita magica: selecciona los pixeles parecidos al que se ha tocado.
     *
     * Con [contiguous] activado se propaga solo por la zona conectada, con un relleno por
     * lineas de barrido iterativo (nunca recursivo: una region grande desbordaria la pila).
     */
    fun magicWand(
        src: Bitmap,
        x: Int,
        y: Int,
        tolerance: Float,
        contiguous: Boolean = true,
    ): Selection {
        val w = src.width
        val h = src.height
        if (x < 0 || y < 0 || x >= w || y >= h) return Selection.empty(w, h)

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val target = px[y * w + x]
        val tol = tolerance.coerceIn(0f, 1f)
        val mask = ByteArray(w * h)

        if (!contiguous) {
            for (i in px.indices) {
                if (Adjust.colorDistance(px[i], target) <= tol) mask[i] = 255.toByte()
            }
            return Selection.of(w, h, mask)
        }

        val stack = ArrayDeque<Int>()
        stack.addLast(y * w + x)
        while (stack.isNotEmpty()) {
            val start = stack.removeLast()
            val py = start / w
            val rowStart = py * w

            // Se extiende la linea a izquierda y derecha desde el punto semilla.
            var left = start
            while (left > rowStart && mask[left - 1] == 0.toByte() &&
                Adjust.colorDistance(px[left - 1], target) <= tol
            ) {
                left--
            }
            var right = start
            while (right < rowStart + w - 1 && mask[right + 1] == 0.toByte() &&
                Adjust.colorDistance(px[right + 1], target) <= tol
            ) {
                right++
            }
            if (mask[start] != 0.toByte()) continue

            for (i in left..right) mask[i] = 255.toByte()

            // Semillas para las filas de arriba y de abajo.
            for (i in left..right) {
                if (py > 0) {
                    val up = i - w
                    if (mask[up] == 0.toByte() && Adjust.colorDistance(px[up], target) <= tol) {
                        stack.addLast(up)
                    }
                }
                if (py < h - 1) {
                    val down = i + w
                    if (mask[down] == 0.toByte() && Adjust.colorDistance(px[down], target) <= tol) {
                        stack.addLast(down)
                    }
                }
            }
        }
        return Selection.of(w, h, mask)
    }

    /** Selecciona todos los pixeles parecidos a un color, sin importar donde esten. */
    fun byColor(src: Bitmap, color: Int, tolerance: Float): Selection {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val tol = tolerance.coerceIn(0f, 1f)
        val mask = ByteArray(w * h)
        for (i in px.indices) {
            if (Adjust.colorDistance(px[i], color) <= tol) mask[i] = 255.toByte()
        }
        return Selection.of(w, h, mask)
    }

    // ----------------------------------------------------------------------------------
    // Operaciones con la seleccion
    // ----------------------------------------------------------------------------------

    /**
     * Pinta la mascara de la seleccion sobre un lienzo en coordenadas de la capa.
     *
     * La seleccion vive en coordenadas del documento, mientras que los pixeles de una capa
     * estan en las suyas: si la capa se ha movido, girado o escalado, los dos espacios no
     * coinciden. [docToLayer] es la matriz que los une; con null se asume que ya coinciden,
     * que es el caso del relleno por zona contigua, donde la mascara se acaba de calcular
     * sobre el mismo bitmap.
     */
    private fun drawMask(
        canvas: Canvas,
        selection: Selection,
        docToLayer: Matrix?,
        paint: Paint,
    ) {
        val maskBmp = selection.toAlphaBitmap()
        if (docToLayer == null) {
            canvas.drawBitmap(maskBmp, 0f, 0f, paint)
        } else {
            canvas.drawBitmap(maskBmp, docToLayer, paint)
        }
        maskBmp.recycle()
    }

    /**
     * Copia el contenido seleccionado a un bitmap nuevo del tamano de la seleccion.
     *
     * Con [layerToDoc] el resultado queda en coordenadas de documento, que es lo que espera
     * el pegado: la capa nueva se coloca en la esquina de la seleccion.
     */
    fun extract(src: Bitmap, selection: Selection, layerToDoc: Matrix? = null): Bitmap? {
        val b = selection.bounds
        if (b.isEmpty) return null
        val rect = Rect(b)
        val limit = if (layerToDoc == null) {
            Rect(0, 0, src.width, src.height)
        } else {
            Rect(0, 0, selection.width, selection.height)
        }
        if (!rect.intersect(limit)) return null

        val out = BitmapUtils.create(rect.width(), rect.height())
        val canvas = Canvas(out)
        canvas.translate(-rect.left.toFloat(), -rect.top.toFloat())
        if (layerToDoc == null) {
            canvas.drawBitmap(src, 0f, 0f, null)
        } else {
            canvas.drawBitmap(src, layerToDoc, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        // La forma exacta de la seleccion se aplica pixel a pixel sobre el alfa (antes un
        // DST_IN con la mascara dejaba pasar todo el rectangulo contenedor). La mascara esta
        // en coordenadas de documento y el resultado empieza en la esquina de [rect].
        val w = out.width
        val h = out.height
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val cov = selection.coverageAt(x + rect.left, y + rect.top)
                px[i] = when {
                    cov == 0 -> 0
                    cov >= 255 -> px[i]
                    else -> (((px[i] ushr 24) * cov + 127) / 255 shl 24) or (px[i] and 0xFFFFFF)
                }
            }
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Rellena la seleccion con un color o un Paint ya configurado. */
    fun fill(
        src: Bitmap,
        selection: Selection,
        paint: Paint,
        docToLayer: Matrix? = null,
    ): Bitmap {
        // Camino rapido del bote de pintura (requisito del usuario: iba lento): sin matriz
        // ni shader la mascara ya coincide pixel a pixel con el bitmap, asi que se puede
        // pintar directamente sobre el array en vez de montar dos bitmaps intermedios y
        // componerlos con Canvas.
        if (docToLayer == null && paint.shader == null) {
            return fillDirect(src, selection, paint.color)
        }
        val out = BitmapUtils.mutableCopy(src)
        val layer = BitmapUtils.create(src.width, src.height)
        val layerCanvas = Canvas(layer)
        // Primero la mascara y luego el color con SRC_IN, no al contrario: DST_IN solo
        // afecta al rectangulo que ocupa lo que se dibuja, asi que con la mascara
        // transformada el relleno se quedaria fuera de su zona.
        drawMask(layerCanvas, selection, docToLayer, Paint(Paint.FILTER_BITMAP_FLAG))
        layerCanvas.drawRect(
            0f, 0f, src.width.toFloat(), src.height.toFloat(),
            Paint(paint).apply { blendMode = android.graphics.BlendMode.SRC_IN },
        )
        Canvas(out).drawBitmap(layer, 0f, 0f, null)
        layer.recycle()
        return out
    }

    private fun fillDirect(src: Bitmap, selection: Selection, color: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val mask = selection.mask
        val cr = Color.red(color)
        val cg = Color.green(color)
        val cb = Color.blue(color)
        val ca = Color.alpha(color)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val coverage = mask[i].toInt() and 0xFF
            if (coverage == 0) continue
            px[i] = if (coverage >= 255) {
                Color.argb(ca, cr, cg, cb)
            } else {
                // Borde calado: mezcla con lo que ya habia segun la cobertura, para que no
                // quede dentado.
                val a = coverage / 255f
                val ia = 1f - a
                val base = px[i]
                Color.argb(
                    (Color.alpha(base) * ia + ca * a).toInt(),
                    (Color.red(base) * ia + cr * a).toInt(),
                    (Color.green(base) * ia + cg * a).toInt(),
                    (Color.blue(base) * ia + cb * a).toInt(),
                )
            }
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    fun fill(
        src: Bitmap,
        selection: Selection,
        color: Int,
        docToLayer: Matrix? = null,
    ): Bitmap = fill(
        src,
        selection,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
        docToLayer,
    )

    /** Borra el contenido seleccionado dejandolo transparente. */
    fun erase(src: Bitmap, selection: Selection, docToLayer: Matrix? = null): Bitmap {
        val out = BitmapUtils.mutableCopy(src)
        val w = out.width
        val h = out.height
        // Cobertura de la seleccion en el espacio de la capa, con una pasada normal (sin
        // modos de mezcla raros) y aplicada pixel a pixel.
        val cover = IntArray(w * h)
        if (docToLayer == null) {
            for (y in 0 until h) for (x in 0 until w) cover[y * w + x] = selection.coverageAt(x, y)
        } else {
            val maskBmp = selection.toAlphaArgb()
            val layerMask = BitmapUtils.create(w, h)
            Canvas(layerMask).drawBitmap(maskBmp, docToLayer, Paint(Paint.FILTER_BITMAP_FLAG))
            layerMask.getPixels(cover, 0, w, 0, 0, w, h)
            maskBmp.recycle()
            layerMask.recycle()
            for (i in cover.indices) cover[i] = cover[i] ushr 24
        }
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val cov = cover[i]
            if (cov == 0) continue
            px[i] = if (cov >= 255) 0 else
                (((px[i] ushr 24) * (255 - cov) + 127) / 255 shl 24) or (px[i] and 0xFFFFFF)
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
