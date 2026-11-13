package com.dskmusic.pixa.core.selection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path

/**
 * Contorno animado de la seleccion, las clasicas "hormigas marchantes" (requisito 8).
 *
 * El contorno se extrae de la mascara de cobertura como una lista de aristas entre pixeles
 * dentro y fuera, y esas aristas se encadenan en bucles cerrados. Encadenarlas importa: un
 * trazo discontinuo reinicia su patron en cada subtrayecto, asi que con aristas sueltas de
 * un pixel no se veria ninguna hormiga, solo un borde gris.
 */
object MarchingAnts {

    /** Trayecto del borde de la seleccion, en coordenadas de documento. */
    fun contour(selection: Selection): Path {
        val path = Path()
        val w = selection.width
        val h = selection.height
        if (w <= 0 || h <= 0) return path

        val stride = w + 1
        // Vertices de la rejilla: cada punto es y * stride + x.
        val links = HashMap<Int, MutableList<Int>>()
        fun link(a: Int, b: Int) {
            links.getOrPut(a) { ArrayList(2) }.add(b)
            links.getOrPut(b) { ArrayList(2) }.add(a)
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!selection.contains(x, y)) continue
                val tl = y * stride + x
                val tr = tl + 1
                val bl = tl + stride
                val br = bl + 1
                if (!selection.contains(x, y - 1)) link(tl, tr)
                if (!selection.contains(x, y + 1)) link(bl, br)
                if (!selection.contains(x - 1, y)) link(tl, bl)
                if (!selection.contains(x + 1, y)) link(tr, br)
            }
        }
        if (links.isEmpty()) return path

        val used = HashSet<Long>()
        fun edgeKey(a: Int, b: Int): Long {
            val lo = minOf(a, b).toLong()
            val hi = maxOf(a, b).toLong()
            return lo * 0x1_0000_0000L + hi
        }

        for (start in links.keys.sorted()) {
            for (first in links[start].orEmpty()) {
                if (!used.add(edgeKey(start, first))) continue

                var prev = start
                var node = first
                path.moveTo((start % stride).toFloat(), (start / stride).toFloat())
                var lastDx = (first % stride) - (start % stride)
                var lastDy = (first / stride) - (start / stride)
                var pendingX = first % stride
                var pendingY = first / stride

                while (true) {
                    // En un vertice donde se tocan dos regiones en diagonal concurren
                    // cuatro aristas; seguir recto es la eleccion que no cruza el borde.
                    val options = links[node].orEmpty().filter { it != prev && edgeKey(node, it) !in used }
                    val next = options.firstOrNull { cand ->
                        (cand % stride) - (node % stride) == lastDx &&
                            (cand / stride) - (node / stride) == lastDy
                    } ?: options.firstOrNull()

                    if (next == null) {
                        path.lineTo(pendingX.toFloat(), pendingY.toFloat())
                        if (links[node].orEmpty().contains(start)) path.close()
                        break
                    }
                    used.add(edgeKey(node, next))
                    val dx = (next % stride) - (node % stride)
                    val dy = (next / stride) - (node / stride)
                    // Solo se emite un punto cuando el contorno gira: menos vertices y el
                    // mismo dibujo.
                    if (dx != lastDx || dy != lastDy) {
                        path.lineTo(pendingX.toFloat(), pendingY.toFloat())
                        lastDx = dx
                        lastDy = dy
                    }
                    pendingX = next % stride
                    pendingY = next / stride
                    prev = node
                    node = next
                    if (node == start) {
                        path.lineTo(pendingX.toFloat(), pendingY.toFloat())
                        path.close()
                        break
                    }
                }
            }
        }
        return path
    }

    private const val DASH = 6f

    /**
     * Dibuja el contorno sobre un canvas ya escalado al zoom del lienzo.
     *
     * [scale] es ese zoom: el grosor y el patron se dividen por el para que las hormigas
     * se vean siempre del mismo tamano en pantalla. [phase] avanza con la animacion.
     */
    fun draw(canvas: Canvas, contour: Path, scale: Float, phase: Float, thickness: Float = 1f) {
        if (contour.isEmpty) return
        val s = if (scale <= 0f) 1f else scale
        val dash = DASH / s
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f * thickness / s
            color = 0x66000000
        }
        // Halo oscuro ancho: la linea se distingue tambien sobre fotos claras u oscuras.
        canvas.drawPath(contour, base)
        base.strokeWidth = 3.5f * thickness / s
        base.color = Color.WHITE
        canvas.drawPath(contour, base)
        base.color = Color.BLACK
        base.pathEffect = DashPathEffect(floatArrayOf(dash, dash), -phase / s)
        canvas.drawPath(contour, base)
    }
}
