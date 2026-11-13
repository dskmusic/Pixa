package com.dskmusic.pixa.core.render

import android.graphics.Path
import android.graphics.RectF
import com.dskmusic.pixa.core.model.ArrowHeads
import com.dskmusic.pixa.core.model.ShapeKind
import com.dskmusic.pixa.core.model.ShapeSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Generacion por codigo de todas las siluetas que usa la app: las formas vectoriales del
 * editor (requisito 11) y las formas de la nube de palabras (requisito 18).
 *
 * Todo se construye como Path dentro de un rectangulo normalizado, asi que cualquier
 * forma escala sin perdida y sirve igual para dibujar o como mascara de colisiones.
 */
object PathShapes {

    // ----------------------------------------------------------------------------------
    // Formas del editor
    // ----------------------------------------------------------------------------------

    fun shapePath(spec: ShapeSpec): Path {
        val w = spec.width
        val h = spec.height
        val r = RectF(0f, 0f, w, h)
        return when (spec.kind) {
            ShapeKind.RECT -> Path().apply {
                if (spec.cornerRadius > 0f) {
                    val rad = min(spec.cornerRadius, min(w, h) / 2f)
                    addRoundRect(r, rad, rad, Path.Direction.CW)
                } else {
                    addRect(r, Path.Direction.CW)
                }
            }
            ShapeKind.ELLIPSE -> Path().apply { addOval(r, Path.Direction.CW) }
            ShapeKind.LINE -> Path().apply {
                moveTo(0f, h / 2f)
                lineTo(w, h / 2f)
            }
            ShapeKind.ARROW -> arrow(w, h, spec.arrowHeads)
            ShapeKind.POLYGON -> polygon(w, h, spec.sides.coerceIn(3, 24))
            ShapeKind.STAR -> star(w, h, spec.sides.coerceIn(3, 24), spec.innerRatio.coerceIn(0.1f, 0.95f))
            ShapeKind.HEART -> heart(w, h)
        }
    }

    /** Flecha horizontal con cuerpo y punta(s) proporcionales (requisito del usuario: el
     * extremo con punta se elige, izquierda, derecha o ambas). */
    fun arrow(w: Float, h: Float, heads: ArrowHeads = ArrowHeads.END): Path = Path().apply {
        val headW = w * 0.38f
        val bodyH = h * 0.34f
        val top = (h - bodyH) / 2f
        val bottom = top + bodyH
        val bodyStart = if (heads == ArrowHeads.START || heads == ArrowHeads.BOTH) headW else 0f
        val bodyEnd = if (heads == ArrowHeads.END || heads == ArrowHeads.BOTH) w - headW else w

        moveTo(bodyStart, top)
        lineTo(bodyEnd, top)
        if (heads == ArrowHeads.END || heads == ArrowHeads.BOTH) {
            lineTo(bodyEnd, 0f)
            lineTo(w, h / 2f)
            lineTo(bodyEnd, h)
        }
        lineTo(bodyEnd, bottom)
        lineTo(bodyStart, bottom)
        if (heads == ArrowHeads.START || heads == ArrowHeads.BOTH) {
            lineTo(bodyStart, h)
            lineTo(0f, h / 2f)
            lineTo(bodyStart, 0f)
        }
        close()
    }

    fun polygon(w: Float, h: Float, sides: Int): Path = Path().apply {
        val cx = w / 2f
        val cy = h / 2f
        for (i in 0 until sides) {
            // Se arranca en -90 grados para que el vertice quede arriba.
            val a = -PI / 2.0 + 2.0 * PI * i / sides
            val x = cx + cx * cos(a).toFloat()
            val y = cy + cy * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun star(w: Float, h: Float, points: Int, innerRatio: Float): Path = Path().apply {
        val cx = w / 2f
        val cy = h / 2f
        val total = points * 2
        for (i in 0 until total) {
            val a = -PI / 2.0 + PI * i / points
            val k = if (i % 2 == 0) 1f else innerRatio
            val x = cx + cx * k * cos(a).toFloat()
            val y = cy + cy * k * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun heart(w: Float, h: Float): Path = Path().apply {
        // Dos curvas cubicas simetricas que bajan hasta la punta inferior.
        moveTo(w / 2f, h * 0.97f)
        cubicTo(w * -0.12f, h * 0.60f, w * 0.14f, h * 0.02f, w / 2f, h * 0.28f)
        cubicTo(w * 0.86f, h * 0.02f, w * 1.12f, h * 0.60f, w / 2f, h * 0.97f)
        close()
    }

    // ----------------------------------------------------------------------------------
    // Formas de la nube de palabras
    // ----------------------------------------------------------------------------------

    /** Identificadores de las siluetas disponibles para la nube de palabras. */
    enum class CloudShape {
        CIRCLE, HEART, STAR, CLOUD, DIAMOND, HEXAGON, TRIANGLE,
        ARROW, BUBBLE, DROP, BOLT, MOON, SUN, HOUSE,
    }

    fun cloudPath(shape: CloudShape, w: Float, h: Float): Path = when (shape) {
        CloudShape.CIRCLE -> Path().apply { addOval(RectF(0f, 0f, w, h), Path.Direction.CW) }
        CloudShape.HEART -> heart(w, h)
        CloudShape.STAR -> star(w, h, 5, 0.42f)
        CloudShape.CLOUD -> cloud(w, h)
        CloudShape.DIAMOND -> Path().apply {
            moveTo(w / 2f, 0f); lineTo(w, h / 2f); lineTo(w / 2f, h); lineTo(0f, h / 2f); close()
        }
        CloudShape.HEXAGON -> polygon(w, h, 6)
        CloudShape.TRIANGLE -> Path().apply {
            moveTo(w / 2f, 0f); lineTo(w, h); lineTo(0f, h); close()
        }
        CloudShape.ARROW -> arrow(w, h)
        CloudShape.BUBBLE -> bubble(w, h)
        CloudShape.DROP -> drop(w, h)
        CloudShape.BOLT -> bolt(w, h)
        CloudShape.MOON -> moon(w, h)
        CloudShape.SUN -> sun(w, h)
        CloudShape.HOUSE -> house(w, h)
    }

    private fun cloud(w: Float, h: Float): Path = Path().apply {
        // Union de circulos sobre una base recta.
        addOval(RectF(0.00f * w, 0.42f * h, 0.42f * w, 0.92f * h), Path.Direction.CW)
        addOval(RectF(0.18f * w, 0.18f * h, 0.66f * w, 0.84f * h), Path.Direction.CW)
        addOval(RectF(0.52f * w, 0.30f * h, 0.92f * w, 0.86f * h), Path.Direction.CW)
        addOval(RectF(0.66f * w, 0.46f * h, 1.00f * w, 0.92f * h), Path.Direction.CW)
        addRect(RectF(0.06f * w, 0.66f * h, 0.94f * w, 0.90f * h), Path.Direction.CW)
    }

    private fun bubble(w: Float, h: Float): Path = Path().apply {
        val body = RectF(0f, 0f, w, h * 0.78f)
        addRoundRect(body, w * 0.14f, w * 0.14f, Path.Direction.CW)
        moveTo(w * 0.24f, h * 0.74f)
        lineTo(w * 0.20f, h)
        lineTo(w * 0.44f, h * 0.74f)
        close()
    }

    private fun drop(w: Float, h: Float): Path = Path().apply {
        moveTo(w / 2f, 0f)
        cubicTo(w * 1.05f, h * 0.44f, w * 0.92f, h, w / 2f, h)
        cubicTo(w * 0.08f, h, w * -0.05f, h * 0.44f, w / 2f, 0f)
        close()
    }

    private fun bolt(w: Float, h: Float): Path = Path().apply {
        moveTo(w * 0.58f, 0f)
        lineTo(w * 0.10f, h * 0.56f)
        lineTo(w * 0.44f, h * 0.56f)
        lineTo(w * 0.34f, h)
        lineTo(w * 0.92f, h * 0.38f)
        lineTo(w * 0.52f, h * 0.38f)
        close()
    }

    private fun moon(w: Float, h: Float): Path = Path().apply {
        // Luna creciente: circulo grande menos circulo desplazado.
        val outer = Path().apply { addOval(RectF(0f, 0f, w, h), Path.Direction.CW) }
        val inner = Path().apply {
            addOval(RectF(w * 0.28f, -h * 0.08f, w * 1.18f, h * 1.08f), Path.Direction.CW)
        }
        op(outer, inner, Path.Op.DIFFERENCE)
    }

    private fun sun(w: Float, h: Float): Path = Path().apply {
        val cx = w / 2f
        val cy = h / 2f
        val rCore = min(w, h) * 0.30f
        addOval(RectF(cx - rCore, cy - rCore, cx + rCore, cy + rCore), Path.Direction.CW)
        val rays = 12
        for (i in 0 until rays) {
            val a = 2.0 * PI * i / rays
            val ray = Path()
            val len = min(w, h) * 0.5f
            val half = (min(w, h) * 0.05f)
            ray.addRect(RectF(cx + rCore * 0.9f, cy - half, cx + len, cy + half), Path.Direction.CW)
            val m = android.graphics.Matrix().apply {
                postRotate(Math.toDegrees(a).toFloat(), cx, cy)
            }
            ray.transform(m)
            op(ray, Path.Op.UNION)
        }
    }

    private fun house(w: Float, h: Float): Path = Path().apply {
        moveTo(w / 2f, 0f)
        lineTo(w, h * 0.42f)
        lineTo(w * 0.86f, h * 0.42f)
        lineTo(w * 0.86f, h)
        lineTo(w * 0.14f, h)
        lineTo(w * 0.14f, h * 0.42f)
        lineTo(0f, h * 0.42f)
        close()
    }
}
