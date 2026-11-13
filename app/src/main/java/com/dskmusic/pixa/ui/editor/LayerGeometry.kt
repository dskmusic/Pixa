package com.dskmusic.pixa.ui.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.render.LayerRasterizer

/**
 * Geometria de las capas en coordenadas de documento (requisitos 6 y 9).
 *
 * El modelo guarda una transformacion, no un rectangulo: la posicion real de una capa hay
 * que calcularla con su tamano intrinseco. Estas funciones son las que permiten dibujar
 * los tiradores, saber a que capa se ha tocado y mostrar el panel de medidas.
 */
object LayerGeometry {

    /** Las cuatro esquinas de la capa, en orden, como 8 floats (x,y). */
    fun quad(layer: PixaLayer, rasterizer: LayerRasterizer, doc: PixaDocument): FloatArray {
        val size = rasterizer.intrinsicSize(layer, doc.width, doc.height)
        val w = size.width.coerceAtLeast(1).toFloat()
        val h = size.height.coerceAtLeast(1).toFloat()
        val pts = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        layer.transform.toMatrix(size.width, size.height).mapPoints(pts)
        return pts
    }

    /** Caja englobante alineada a los ejes, que es lo que ensena el panel de medidas. */
    fun bounds(layer: PixaLayer, rasterizer: LayerRasterizer, doc: PixaDocument): RectF {
        val pts = quad(layer, rasterizer, doc)
        var minX = pts[0]
        var maxX = pts[0]
        var minY = pts[1]
        var maxY = pts[1]
        for (i in 1 until 4) {
            minX = minOf(minX, pts[i * 2])
            maxX = maxOf(maxX, pts[i * 2])
            minY = minOf(minY, pts[i * 2 + 1])
            maxY = maxOf(maxY, pts[i * 2 + 1])
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /** Tamano intrinseco de la capa sin transformar. */
    fun intrinsic(layer: PixaLayer, rasterizer: LayerRasterizer, doc: PixaDocument): Pair<Int, Int> {
        val size = rasterizer.intrinsicSize(layer, doc.width, doc.height)
        return size.width.coerceAtLeast(1) to size.height.coerceAtLeast(1)
    }

    /**
     * Comprueba si un punto cae dentro de la capa.
     *
     * Se invierte la transformacion y se mira el punto en el espacio del contenido: asi
     * funciona igual con capas giradas, sesgadas o con perspectiva, sin repetir la
     * geometria para cada caso.
     */
    fun contains(
        layer: PixaLayer,
        rasterizer: LayerRasterizer,
        doc: PixaDocument,
        point: Offset,
    ): Boolean {
        val size = rasterizer.intrinsicSize(layer, doc.width, doc.height)
        val inverse = Matrix()
        if (!layer.transform.toMatrix(size.width, size.height).invert(inverse)) return false
        val pts = floatArrayOf(point.x, point.y)
        inverse.mapPoints(pts)
        return pts[0] >= 0f && pts[1] >= 0f &&
            pts[0] <= size.width.toFloat() && pts[1] <= size.height.toFloat()
    }

    /** Capa visible mas alta que contiene el punto, para seleccionar tocando el lienzo. */
    fun pick(
        doc: PixaDocument,
        rasterizer: LayerRasterizer,
        point: Offset,
        /**
         * Las capas de relleno (color, degradado) cubren todo el lienzo: contarlas siempre
         * impediria deseleccionar al tocar fuera. Solo se cuentan si se piden (doble toque).
         */
        includeFills: Boolean = false,
    ): PixaLayer? =
        doc.layers.lastOrNull { layer ->
            doc.isEffectivelyVisible(layer) && !layer.locked &&
                (includeFills || layer.content !is com.dskmusic.pixa.core.model.LayerContent.Fill) &&
                contains(layer, rasterizer, doc, point)
        }

    /** El cuadrilatero [q] (8 valores) mas los puntos medios de sus lados (16 valores). */
    fun withEdgeMidpoints(q: FloatArray): FloatArray {
        if (q.size < 8) return q
        return q.copyOf(8) + floatArrayOf(
            (q[0] + q[2]) / 2f, (q[1] + q[3]) / 2f, // 0: arriba
            (q[2] + q[4]) / 2f, (q[3] + q[5]) / 2f, // 1: derecha
            (q[4] + q[6]) / 2f, (q[5] + q[7]) / 2f, // 2: abajo
            (q[6] + q[0]) / 2f, (q[7] + q[1]) / 2f, // 3: izquierda
        )
    }

    /** Indice del tirador tocado (0..3 esquinas) o -1 si el toque no cae en ninguno. */
    fun handleAt(quad: FloatArray, point: Offset, radius: Float): Int {
        for (i in 0 until 4) {
            val dx = quad[i * 2] - point.x
            val dy = quad[i * 2 + 1] - point.y
            if (dx * dx + dy * dy <= radius * radius) return i
        }
        return -1
    }

    /**
     * Matriz pixeles de la capa -> documento para una capa de imagen.
     *
     * El bitmap es el contenido intrinseco de la capa, asi que su tamano es el que espera
     * [Xf.toMatrix]; no hace falta el rasterizador.
     */
    fun layerToDoc(layer: PixaLayer, bitmap: Bitmap): Matrix =
        layer.transform.toMatrix(bitmap.width, bitmap.height)

    /**
     * Inversa de [layerToDoc], o null si la capa esta aplastada (escala cero).
     *
     * ponytail: quien la pide trata el null como "sin transformar". Una capa que no se
     * puede invertir no se ve, de modo que da igual donde caiga el recorte.
     */
    fun docToLayer(layer: PixaLayer, bitmap: Bitmap): Matrix? {
        val inverse = Matrix()
        return if (layerToDoc(layer, bitmap).invert(inverse)) inverse else null
    }

    /** Coloca la capa centrada en el documento manteniendo escala y giro. */
    fun centered(layer: PixaLayer, rasterizer: LayerRasterizer, doc: PixaDocument): Xf {
        val size = rasterizer.intrinsicSize(layer, doc.width, doc.height)
        return layer.transform.copy(
            tx = (doc.width - size.width) / 2f,
            ty = (doc.height - size.height) / 2f,
        )
    }
}
