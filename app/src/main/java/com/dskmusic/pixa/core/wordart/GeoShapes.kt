package com.dskmusic.pixa.core.wordart

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Formas geometricas propias, sin licencias ni assets: se construyen en codigo. */
object GeoShapes {

    /** id, nombre es, nombre en, etiquetas es, etiquetas en */
    class Def(val id: String, val es: String, val en: String, val tagsEs: String, val tagsEn: String)

    val defs: List<Def> = listOf(
        Def("geo:circle", "Círculo", "Circle", "redondo disco bola esfera", "round disc ball sphere"),
        Def("geo:square", "Cuadrado", "Square", "caja rectángulo", "box rectangle"),
        Def("geo:rsquare", "Cuadrado redondeado", "Rounded square", "app icono esquinas", "app icon corners"),
        Def("geo:heart", "Corazón", "Heart", "amor san valentín cariño", "love valentine"),
        Def("geo:star5", "Estrella", "Star", "estrellas cinco puntas favorito", "stars five points favorite"),
        Def("geo:star6", "Estrella de seis puntas", "Six-point star", "estrella david hexagrama", "star david hexagram"),
        Def("geo:star8", "Estrella de ocho puntas", "Eight-point star", "estrella brillante", "star shiny"),
        Def("geo:burst", "Sol radiante", "Sunburst", "sol rayos explosión", "sun rays explosion"),
        Def("geo:triangle", "Triángulo", "Triangle", "pirámide", "pyramid"),
        Def("geo:diamond", "Rombo", "Diamond", "diamante losange", "rhombus"),
        Def("geo:pentagon", "Pentágono", "Pentagon", "polígono", "polygon"),
        Def("geo:hexagon", "Hexágono", "Hexagon", "polígono panal", "polygon honeycomb"),
        Def("geo:octagon", "Octágono", "Octagon", "polígono stop", "polygon stop"),
        Def("geo:cloud", "Nube", "Cloud", "cielo tiempo", "sky weather"),
        Def("geo:drop", "Gota", "Drop", "agua lágrima líquido", "water tear liquid"),
        Def("geo:moon", "Luna creciente", "Crescent moon", "luna noche", "moon night"),
        Def("geo:cross", "Cruz", "Plus", "más suma hospital", "add plus hospital"),
        Def("geo:shield", "Escudo", "Shield", "protección seguridad", "protection security"),
        Def("geo:bubble", "Globo de diálogo", "Speech bubble", "mensaje chat hablar", "message chat talk"),
        Def("geo:bolt", "Rayo", "Lightning bolt", "relámpago energía tormenta", "thunder energy storm"),
        Def("geo:leaf", "Hoja", "Leaf", "planta árbol ecología", "plant tree eco"),
        Def("geo:arrow", "Flecha", "Arrow", "dirección derecha", "direction right"),
        Def("geo:flower", "Flor", "Flower", "pétalos jardín primavera", "petals garden spring"),
        Def("geo:ring", "Anillo", "Ring", "aro corona círculo", "hoop crown circle"),
    )

    fun build(id: String): Path? {
        val p = Path()
        when (id) {
            "geo:circle" -> p.addOval(RectF(0f, 0f, 1f, 1f), Path.Direction.CW)
            "geo:square" -> p.addRect(0f, 0f, 1f, 1f, Path.Direction.CW)
            "geo:rsquare" -> p.addRoundRect(RectF(0f, 0f, 1f, 1f), 0.22f, 0.22f, Path.Direction.CW)
            "geo:heart" -> poly(p, (0 until 180).map {
                val t = it * 2 * PI / 180
                (16 * sin(t).let { s -> s * s * s }).toFloat() to
                    -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)).toFloat()
            })
            "geo:star5" -> star(p, 5, 0.40f)
            "geo:star6" -> star(p, 6, 0.55f)
            "geo:star8" -> star(p, 8, 0.62f)
            "geo:burst" -> star(p, 18, 0.78f)
            "geo:triangle" -> polyReg(p, 3, -90.0)
            "geo:diamond" -> polyReg(p, 4, -90.0)
            "geo:pentagon" -> polyReg(p, 5, -90.0)
            "geo:hexagon" -> polyReg(p, 6, 0.0)
            "geo:octagon" -> polyReg(p, 8, 22.5)
            "geo:cloud" -> {
                p.addCircle(0.28f, 0.62f, 0.22f, Path.Direction.CW)
                p.addCircle(0.5f, 0.42f, 0.28f, Path.Direction.CW)
                p.addCircle(0.74f, 0.6f, 0.24f, Path.Direction.CW)
                p.addRoundRect(RectF(0.2f, 0.58f, 0.8f, 0.84f), 0.12f, 0.12f, Path.Direction.CW)
            }
            "geo:drop" -> {
                p.moveTo(0.5f, 0f)
                p.cubicTo(0.65f, 0.2f, 0.88f, 0.4f, 0.88f, 0.65f)
                p.cubicTo(0.88f, 0.87f, 0.7f, 1f, 0.5f, 1f)
                p.cubicTo(0.3f, 1f, 0.12f, 0.87f, 0.12f, 0.65f)
                p.cubicTo(0.12f, 0.4f, 0.35f, 0.2f, 0.5f, 0f)
                p.close()
            }
            "geo:moon" -> {
                val a = Path().apply { addCircle(0.5f, 0.5f, 0.5f, Path.Direction.CW) }
                val b = Path().apply { addCircle(0.72f, 0.4f, 0.4f, Path.Direction.CW) }
                p.op(a, b, Path.Op.DIFFERENCE)
            }
            "geo:cross" -> poly(p, listOf(
                0.35f to 0f, 0.65f to 0f, 0.65f to 0.35f, 1f to 0.35f, 1f to 0.65f, 0.65f to 0.65f,
                0.65f to 1f, 0.35f to 1f, 0.35f to 0.65f, 0f to 0.65f, 0f to 0.35f, 0.35f to 0.35f,
            ))
            "geo:shield" -> {
                p.moveTo(0.5f, 0f)
                p.cubicTo(0.7f, 0.12f, 0.85f, 0.15f, 0.95f, 0.15f)
                p.lineTo(0.95f, 0.5f)
                p.cubicTo(0.95f, 0.78f, 0.75f, 0.93f, 0.5f, 1f)
                p.cubicTo(0.25f, 0.93f, 0.05f, 0.78f, 0.05f, 0.5f)
                p.lineTo(0.05f, 0.15f)
                p.cubicTo(0.15f, 0.15f, 0.3f, 0.12f, 0.5f, 0f)
                p.close()
            }
            "geo:bubble" -> {
                p.addRoundRect(RectF(0f, 0f, 1f, 0.8f), 0.16f, 0.16f, Path.Direction.CW)
                val tail = Path()
                poly(tail, listOf(0.18f to 0.7f, 0.18f to 1f, 0.5f to 0.7f))
                p.op(tail, Path.Op.UNION)
            }
            "geo:bolt" -> poly(p, listOf(
                0.58f to 0f, 0.12f to 0.56f, 0.44f to 0.56f, 0.34f to 1f, 0.88f to 0.4f, 0.55f to 0.4f, 0.75f to 0f,
            ))
            "geo:leaf" -> {
                p.moveTo(0.08f, 0.92f)
                p.cubicTo(0f, 0.3f, 0.4f, 0f, 0.96f, 0.04f)
                p.cubicTo(1f, 0.6f, 0.7f, 1f, 0.08f, 0.92f)
                p.close()
            }
            "geo:arrow" -> poly(p, listOf(
                0f to 0.35f, 0.55f to 0.35f, 0.55f to 0.05f, 1f to 0.5f, 0.55f to 0.95f, 0.55f to 0.65f, 0f to 0.65f,
            ))
            "geo:flower" -> {
                for (i in 0 until 6) {
                    val a = i * PI / 3
                    p.addCircle(0.5f + 0.27f * cos(a).toFloat(), 0.5f + 0.27f * sin(a).toFloat(), 0.23f, Path.Direction.CW)
                }
                p.addCircle(0.5f, 0.5f, 0.2f, Path.Direction.CW)
            }
            "geo:ring" -> {
                p.addCircle(0.5f, 0.5f, 0.5f, Path.Direction.CW)
                p.addCircle(0.5f, 0.5f, 0.28f, Path.Direction.CCW)
            }
            else -> return null
        }
        return normalize(p, 0f).first
    }

    private fun poly(p: Path, pts: List<Pair<Float, Float>>) {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
        p.close()
    }

    private fun polyReg(p: Path, n: Int, startDeg: Double) {
        poly(p, (0 until n).map {
            val a = Math.toRadians(startDeg + it * 360.0 / n)
            (0.5 + 0.5 * cos(a)).toFloat() to (0.5 + 0.5 * sin(a)).toFloat()
        })
    }

    private fun star(p: Path, points: Int, inner: Float) {
        poly(p, (0 until points * 2).map {
            val a = Math.toRadians(-90.0 + it * 180.0 / points)
            val r = if (it % 2 == 0) 0.5 else 0.5 * inner
            (0.5 + r * cos(a)).toFloat() to (0.5 + r * sin(a)).toFloat()
        })
    }

    /**
     * Ajusta el trazado a la caja unidad conservando la proporcion y lo centra. El segundo
     * valor es el grosor de trazo, ya normalizado, para las formas dibujadas con lineas.
     */
    fun normalize(src: Path, strokeW: Float): Pair<Path, Float> {
        val b = RectF()
        src.computeBounds(b, true)
        val dim = max(b.width(), b.height()) + strokeW
        if (dim <= 0f) return src to 0f
        val s = 1f / dim
        val m = Matrix().apply {
            postTranslate(-b.centerX(), -b.centerY())
            postScale(s, s)
            postTranslate(0.5f, 0.5f)
        }
        val out = Path(src)
        out.transform(m)
        return out to strokeW * s
    }
}
