package com.dskmusic.pixa.ui.editor

import android.graphics.Matrix
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Transformacion de la vista del lienzo (requisito 6).
 *
 * Guarda zoom y desplazamiento de la vista, y traduce puntos entre coordenadas de
 * pantalla y coordenadas de documento. Las herramientas trabajan siempre en coordenadas
 * de documento, de modo que pintar con zoom sale en el sitio correcto sin que cada
 * herramienta tenga que pensarlo.
 *
 * La vista no gira a proposito: girar el lienzo desorienta y deja los gestos de dos dedos
 * sin forma de girar el elemento seleccionado, que es lo que de verdad hace falta.
 */
@Stable
class CanvasController {

    var zoom by mutableFloatStateOf(1f)
        private set

    var pan by mutableStateOf(Offset.Zero)
        private set

    var viewSize by mutableStateOf(Size.Zero)
        private set

    var docWidth by mutableFloatStateOf(1f)
        private set

    var docHeight by mutableFloatStateOf(1f)
        private set

    /** Zoom al que el documento entra completo en la vista. */
    val fitScale: Float
        get() {
            if (viewSize.width <= 0f || viewSize.height <= 0f) return 1f
            // Margen negro alrededor: ~9 % del lado corto de la vista (minimo MARGIN_PX), para
            // que quede sitio donde tocar fuera del lienzo y mover la vista.
            val margin = maxOf(MARGIN_PX, 0.09f * min(viewSize.width, viewSize.height))
            return min(
                (viewSize.width - 2f * margin) / docWidth,
                (viewSize.height - 2f * margin) / docHeight,
            ).coerceAtLeast(0.01f)
        }

    fun setDocumentSize(width: Int, height: Int, refit: Boolean) {
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()
        val changed = w != docWidth || h != docHeight
        docWidth = w
        docHeight = h
        if (changed && refit) fit()
    }

    /**
     * No se llama setViewSize: [Size] es una value class y el nombre mangleado del
     * setter de [viewSize] seria el mismo, lo que choca en la JVM.
     */
    fun updateViewSize(size: Size) {
        val first = viewSize == Size.Zero
        viewSize = size
        if (first) fit()
    }

    fun fit() {
        zoom = fitScale
        pan = Offset.Zero
    }

    /** Vuelve a una vista guardada antes (zoom y desplazamiento). */
    fun restoreView(zoom: Float, pan: Offset) {
        this.zoom = zoom
        this.pan = pan
    }

    /**
     * Encaja un rectangulo del documento en la vista con el mismo margen que [fit]. No
     * amplia mas de unas 6 veces el encaje del documento, para que un objeto pequeno no
     * llene la pantalla de golpe.
     */
    fun fitRect(left: Float, top: Float, right: Float, bottom: Float) {
        val (z, p) = viewForRect(left, top, right, bottom) ?: return
        zoom = z
        pan = p
    }

    /** Zoom y desplazamiento que [fitRect] aplicaria, sin aplicarlos (para animar). */
    fun viewForRect(left: Float, top: Float, right: Float, bottom: Float): Pair<Float, Offset>? {
        if (viewSize.width <= 0f || viewSize.height <= 0f) return null
        val w = (right - left).coerceAtLeast(1f)
        val h = (bottom - top).coerceAtLeast(1f)
        val margin = maxOf(MARGIN_PX, 0.09f * min(viewSize.width, viewSize.height))
        val z = min((viewSize.width - 2f * margin) / w, (viewSize.height - 2f * margin) / h)
            .coerceIn(MIN_ZOOM, minOf(MAX_ZOOM, fitScale * 6f))
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        return z to Offset(-(cx - docWidth / 2f) * z, -(cy - docHeight / 2f) * z)
    }

    /** 100 %: un pixel de documento, un pixel de pantalla. */
    fun actualSize() {
        zoom = 1f
        pan = Offset.Zero
    }

    /** Alterna entre encajar y 100 % (doble toque, requisito 6). */
    fun toggleFit() {
        if (abs(zoom - fitScale) < 0.01f) actualSize() else fit()
    }

    /**
     * Aplica un gesto de dos dedos manteniendo fijo el punto del documento que hay bajo
     * el centroide: sin esto el contenido se escapa de los dedos al ampliar.
     */
    fun applyGesture(centroid: Offset, panDelta: Offset, zoomFactor: Float) {
        val anchorDoc = viewToDoc(centroid)
        zoom = (zoom * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        pan += panDelta
        val after = docToView(anchorDoc)
        pan += (centroid - after)
    }

    fun nudge(delta: Offset) {
        pan += delta
    }

    // ----------------------------------------------------------------------------------
    // Conversiones
    // ----------------------------------------------------------------------------------

    /** Matriz documento -> vista, la misma que se usa para pintar. */
    fun matrix(): Matrix = Matrix().apply {
        postTranslate(-docWidth / 2f, -docHeight / 2f)
        postScale(zoom, zoom)
        postTranslate(viewSize.width / 2f + pan.x, viewSize.height / 2f + pan.y)
    }

    private val tmp = FloatArray(2)

    fun docToView(point: Offset): Offset {
        tmp[0] = point.x
        tmp[1] = point.y
        matrix().mapPoints(tmp)
        return Offset(tmp[0], tmp[1])
    }

    fun viewToDoc(point: Offset): Offset {
        val inverse = Matrix()
        if (!matrix().invert(inverse)) return point
        tmp[0] = point.x
        tmp[1] = point.y
        inverse.mapPoints(tmp)
        return Offset(tmp[0], tmp[1])
    }

    /** Longitud en documento de una distancia en pantalla (radios de pincel, umbrales). */
    fun viewLengthToDoc(length: Float): Float = if (zoom <= 0f) length else length / zoom

    val zoomPercent: Int get() = (zoom * 100f).roundToInt()

    companion object {
        const val MIN_ZOOM = 0.05f
        const val MAX_ZOOM = 32f
        private const val MARGIN_PX = 24f
    }
}

// ======================================================================================
// Ajuste magnetico y guias inteligentes (requisito 6)
// ======================================================================================

/** Linea guia que la vista dibuja mientras un objeto se acerca a una referencia. */
data class SmartGuide(val vertical: Boolean, val position: Float)

/** Resultado de ajustar un desplazamiento: posicion corregida y guias a dibujar. */
data class SnapResult(
    val offset: Offset,
    val guides: List<SmartGuide> = emptyList(),
)

object Snapping {

    /** Angulos a los que se pega la rotacion: cada 15 grados. */
    fun snapAngle(degrees: Float, enabled: Boolean, toleranceDeg: Float = 4f): Float {
        if (!enabled) return degrees
        val step = 15f
        val nearest = (degrees / step).roundToInt() * step
        return if (abs(degrees - nearest) <= toleranceDeg) nearest else degrees
    }

    /**
     * Ajusta el centro de un objeto a los bordes y al centro del lienzo.
     *
     * [tolerance] viene en pixeles de documento, calculada desde pixeles de pantalla, de
     * modo que la fuerza del iman se siente igual con cualquier zoom.
     */
    fun snapBounds(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        docWidth: Float,
        docHeight: Float,
        tolerance: Float,
        enabled: Boolean,
    ): SnapResult {
        if (!enabled) return SnapResult(Offset(left, top))
        val guides = mutableListOf<SmartGuide>()
        var x = left
        var y = top

        val centerX = left + width / 2f
        val centerY = top + height / 2f
        val candidatesX = listOf(
            0f to left,
            docWidth / 2f to centerX,
            docWidth to left + width,
        )
        val candidatesY = listOf(
            0f to top,
            docHeight / 2f to centerY,
            docHeight to top + height,
        )

        var bestX: Pair<Float, Float>? = null
        candidatesX.forEach { (target, value) ->
            val d = abs(target - value)
            if (d <= tolerance && (bestX == null || d < abs(bestX!!.first - bestX!!.second))) {
                bestX = target to value
            }
        }
        bestX?.let { (target, value) ->
            x += target - value
            guides += SmartGuide(vertical = true, position = target)
        }

        var bestY: Pair<Float, Float>? = null
        candidatesY.forEach { (target, value) ->
            val d = abs(target - value)
            if (d <= tolerance && (bestY == null || d < abs(bestY!!.first - bestY!!.second))) {
                bestY = target to value
            }
        }
        bestY?.let { (target, value) ->
            y += target - value
            guides += SmartGuide(vertical = false, position = target)
        }

        return SnapResult(Offset(x, y), guides)
    }

    /** Separacion de la rejilla segun el tamano del documento, en pixeles de documento. */
    fun gridStep(docWidth: Float, docHeight: Float): Float {
        val side = max(docWidth, docHeight)
        return max(16f, side / 24f)
    }

    /**
     * Ajusta una coordenada de un borde del recuadro (corte o seleccion) a las referencias
     * del lienzo: borde, centro y extremo opuesto del eje. Devuelve el valor corregido y,
     * si se pego, la guia a dibujar (una guia vertical vive en una posicion x; una
     * horizontal en una posicion y).
     *
     * Se usa para los tiradores, donde lo que se arrastra es un borde suelto y no el
     * recuadro entero.
     */
    fun snapEdge(
        value: Float,
        axisLength: Float,
        vertical: Boolean,
        tolerance: Float,
        enabled: Boolean,
    ): Pair<Float, SmartGuide?> {
        if (!enabled) return value to null
        var best: Float? = null
        var bestDistance = tolerance
        listOf(0f, axisLength / 2f, axisLength).forEach { target ->
            val d = abs(target - value)
            if (d <= bestDistance) {
                best = target
                bestDistance = d
            }
        }
        return if (best == null) value to null
        else best to SmartGuide(vertical = vertical, position = best)
    }
}
