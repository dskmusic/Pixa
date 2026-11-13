package com.dskmusic.pixa.ui.editor

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * Recuadro de seleccion y de recorte: se mueve y se cambia de tamano con ocho tiradores
 * (requisito 12).
 *
 * Con el dedo solo se dibuja en el lazo y en el poligono. El rectangulo, la elipse y el
 * recorte aparecen con el recuadro ya puesto, y lo que hace el dedo es arrastrarlo o
 * arrastrar uno de sus tiradores; asi no desaparece en cuanto se va a ajustar.
 *
 * El recuadro vive en [EditorUi.dragStart] y [EditorUi.dragCurrent], en coordenadas de
 * documento, que es de donde ya lo leian el panel de recorte y la guia que se pinta.
 * Se trabaja con [RectF] porque es lo que esperan `SelectionTools` y el resto del editor.
 *
 * Los tiradores se numeran desde arriba a la izquierda y girando en el sentido del reloj:
 * 0 esquina superior izquierda, 1 arriba, 2 superior derecha, 3 derecha, 4 inferior
 * derecha, 5 abajo, 6 inferior izquierda, 7 izquierda.
 */
internal object SelectionBox {

    /** Lado minimo en pixeles de documento: por debajo el recuadro no se puede agarrar. */
    const val MIN_SIZE = 8f

    /** Parte del documento que ocupa el recuadro cuando aparece solo. */
    private const val DEFAULT_FRACTION = 0.6f

    /** Herramientas que se manejan con el recuadro en vez de dibujando. */
    fun isBoxTool(tool: CanvasTool): Boolean =
        tool == CanvasTool.SELECT_RECT ||
            tool == CanvasTool.SELECT_ELLIPSE ||
            tool == CanvasTool.CROP

    /** Recuadro actual, normalizado, o null si todavia no hay ninguno. */
    fun of(ui: EditorUi): RectF? {
        val a = ui.dragStart ?: return null
        val b = ui.dragCurrent ?: return null
        return RectF(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    fun set(ui: EditorUi, box: RectF) {
        ui.dragStart = Offset(box.left, box.top)
        ui.dragCurrent = Offset(box.right, box.bottom)
    }

    /** Recuadro centrado con el que aparece la herramienta. [ratio] es ancho / alto. */
    fun default(docWidth: Float, docHeight: Float, ratio: Float?): RectF {
        var w = docWidth * DEFAULT_FRACTION
        var h = docHeight * DEFAULT_FRACTION
        if (ratio != null && ratio > 0f) {
            if (w / h > ratio) w = h * ratio else h = w / ratio
        }
        val cx = docWidth / 2f
        val cy = docHeight / 2f
        return RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    fun handles(box: RectF): List<Offset> = listOf(
        Offset(box.left, box.top),
        Offset(box.centerX(), box.top),
        Offset(box.right, box.top),
        Offset(box.right, box.centerY()),
        Offset(box.right, box.bottom),
        Offset(box.centerX(), box.bottom),
        Offset(box.left, box.bottom),
        Offset(box.left, box.centerY()),
    )

    /** Tirador mas cercano a [point] dentro de [radius], o -1 si no hay ninguno. */
    fun handleAt(box: RectF, point: Offset, radius: Float): Int {
        var best = -1
        var bestDistance = radius
        handles(box).forEachIndexed { index, handle ->
            val distance = (point - handle).getDistance()
            if (distance <= bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * Desplaza el recuadro entero sin dejarlo salir del documento.
     *
     * Con [Offset.Zero] sirve de paso final para meter dentro un recuadro que se habia
     * quedado fuera, que es justo lo que hace falta tras ajustar la proporcion.
     */
    fun moved(box: RectF, delta: Offset, docWidth: Float, docHeight: Float): RectF {
        val dx = clamp(delta.x, -box.left, docWidth - box.right)
        val dy = clamp(delta.y, -box.top, docHeight - box.bottom)
        return RectF(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy)
    }

    /** Arrastra un tirador. [ratio] (ancho / alto) fija la proporcion; null = libre. */
    fun resized(
        box: RectF,
        handle: Int,
        point: Offset,
        ratio: Float?,
        docWidth: Float,
        docHeight: Float,
    ): RectF {
        val x = point.x.coerceIn(0f, docWidth)
        val y = point.y.coerceIn(0f, docHeight)
        var left = box.left
        var top = box.top
        var right = box.right
        var bottom = box.bottom
        if (movesLeft(handle)) left = min(x, right - MIN_SIZE)
        if (movesRight(handle)) right = max(x, left + MIN_SIZE)
        if (movesTop(handle)) top = min(y, bottom - MIN_SIZE)
        if (movesBottom(handle)) bottom = max(y, top + MIN_SIZE)
        val out = RectF(left, top, right, bottom)
        return if (ratio == null || ratio <= 0f) {
            out
        } else {
            withRatio(out, handle, ratio, docWidth, docHeight)
        }
    }

    /**
     * Rehace el recuadro con la proporcion pedida.
     *
     * Se queda quieto el lado opuesto al tirador que se arrastro, que es lo que espera
     * quien tira de una esquina; con los tiradores del centro de un lado el recuadro crece
     * hacia los dos lados para que no se vaya de sitio.
     */
    private fun withRatio(
        box: RectF,
        handle: Int,
        ratio: Float,
        docWidth: Float,
        docHeight: Float,
    ): RectF {
        // Arriba y abajo manda el alto; a los lados manda el ancho; en las esquinas manda
        // el lado que mas ha crecido, que es el que esta siguiendo el dedo.
        val byHeight = handle == 1 || handle == 5 ||
            (!sideHandle(handle) && box.height() * ratio > box.width())
        var w = if (byHeight) box.height() * ratio else box.width()
        var h = if (byHeight) box.height() else box.width() / ratio
        if (w < MIN_SIZE) {
            w = MIN_SIZE
            h = w / ratio
        }
        if (h < MIN_SIZE) {
            h = MIN_SIZE
            w = h * ratio
        }
        val fit = min(1f, min(docWidth / w, docHeight / h))
        w *= fit
        h *= fit
        val left = when {
            movesLeft(handle) -> box.right - w
            movesRight(handle) -> box.left
            else -> box.centerX() - w / 2f
        }
        val top = when {
            movesTop(handle) -> box.bottom - h
            movesBottom(handle) -> box.top
            else -> box.centerY() - h / 2f
        }
        return moved(RectF(left, top, left + w, top + h), Offset.Zero, docWidth, docHeight)
    }

    private fun movesLeft(handle: Int) = handle == 0 || handle == 6 || handle == 7

    private fun movesRight(handle: Int) = handle == 2 || handle == 3 || handle == 4

    private fun movesTop(handle: Int) = handle == 0 || handle == 1 || handle == 2

    private fun movesBottom(handle: Int) = handle == 4 || handle == 5 || handle == 6

    /** Tiradores del centro de los lados verticales: solo cambian el ancho. */
    private fun sideHandle(handle: Int) = handle == 3 || handle == 7

    /**
     * ponytail: un recuadro mas grande que el documento no se puede colocar dentro, asi
     * que se deja donde esta en vez de inventar un desplazamiento.
     */
    private fun clamp(value: Float, low: Float, high: Float): Float =
        if (low > high) 0f else value.coerceIn(low, high)
}
