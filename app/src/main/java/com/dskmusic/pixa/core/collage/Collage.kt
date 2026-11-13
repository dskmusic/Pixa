package com.dskmusic.pixa.core.collage

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.RectF
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.GradientKind
import com.dskmusic.pixa.core.model.GradientStop
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.LayerStyle
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.ShapeKind
import com.dskmusic.pixa.core.model.ShapeSpec
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.render.PathShapes
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ======================================================================================
// Disenos
// ======================================================================================

/**
 * Un diseno de collage: los huecos en coordenadas normalizadas (0..1 de ancho y de alto
 * del area util). Valen para cualquier proporcion de lienzo porque los huecos se estiran
 * con el.
 */
class CollageLayout(
    val id: String,
    val rects: List<RectF>,
    /** Forma de cada hueco; los que faltan son rectangulos. */
    val shapes: List<SlotShape> = emptyList(),
) {
    val count: Int get() = rects.size
    fun shape(i: Int): SlotShape = shapes.getOrElse(i) { SlotShape.RECT }
}

/**
 * Forma del hueco. Todas menos RECT son cuadradas por dentro: el hueco se reduce al mayor
 * cuadrado centrado de su celda para que el circulo sea circulo y no un ovalo.
 */
enum class SlotShape(val kind: ShapeKind, val sides: Int = 5) {
    RECT(ShapeKind.RECT),
    CIRCLE(ShapeKind.ELLIPSE),
    HEX(ShapeKind.POLYGON, 6),
    DIAMOND(ShapeKind.POLYGON, 4),
    TRIANGLE(ShapeKind.POLYGON, 3),
    HEART(ShapeKind.HEART),
    STAR(ShapeKind.STAR, 5),
}

/** Arbol de divisiones: cada nodo es un hueco o una fila / columna de nodos con peso. */
private sealed interface Node

private data object Leaf : Node

private class Row(val items: List<Pair<Float, Node>>) : Node

private class Col(val items: List<Pair<Float, Node>>) : Node

/** Fila (reparte el ancho). Cada hijo es [Leaf] o un par peso a nodo. */
private fun row(vararg items: Any): Node = Row(weights(items))

/** Columna (reparte el alto). */
private fun col(vararg items: Any): Node = Col(weights(items))

@Suppress("UNCHECKED_CAST")
private fun weights(items: Array<out Any>): List<Pair<Float, Node>> = items.map {
    if (it is Node) 1f to it else it as Pair<Float, Node>
}

private fun walk(node: Node, r: RectF, out: MutableList<RectF>) {
    when (node) {
        Leaf -> out += r
        is Row -> {
            val total = node.items.sumOf { it.first.toDouble() }.toFloat()
            var x = r.left
            node.items.forEach { (weight, child) ->
                val w = r.width() * weight / total
                walk(child, RectF(x, r.top, x + w, r.bottom), out)
                x += w
            }
        }
        is Col -> {
            val total = node.items.sumOf { it.first.toDouble() }.toFloat()
            var y = r.top
            node.items.forEach { (weight, child) ->
                val h = r.height() * weight / total
                walk(child, RectF(r.left, y, r.right, y + h), out)
                y += h
            }
        }
    }
}

private val L: Node = Leaf

private infix fun Float.x(n: Node): Pair<Float, Node> = this to n

object CollageLayouts {

    private fun build(node: Node): List<RectF> {
        val out = ArrayList<RectF>()
        walk(node, RectF(0f, 0f, 1f, 1f), out)
        return out
    }

    private val trees: List<Node> = listOf(
        // ---- 1 foto ----
        L,
        // ---- 2 fotos ----
        row(L, L), col(L, L),
        row(2f x L, 1f x L), row(1f x L, 2f x L),
        col(2f x L, 1f x L), col(1f x L, 2f x L),
        row(1f x L, 3f x L), col(1f x L, 3f x L),
        // ---- 3 fotos ----
        row(L, L, L), col(L, L, L),
        row(2f x L, 1f x col(L, L)), row(1f x col(L, L), 2f x L),
        col(2f x L, 1f x row(L, L)), col(1f x row(L, L), 2f x L),
        row(1f x L, 1f x col(L, L)), col(1f x L, 1f x row(L, L)),
        row(1f x L, 1f x L, 2f x L), col(1f x L, 2f x L, 1f x L),
        // ---- 4 fotos ----
        row(col(L, L), col(L, L)),
        row(L, L, L, L), col(L, L, L, L),
        row(2f x L, 1f x col(L, L, L)), row(1f x col(L, L, L), 2f x L),
        col(2f x L, 1f x row(L, L, L)), col(1f x row(L, L, L), 2f x L),
        row(1f x L, 1f x col(L, row(L, L))),
        col(1f x L, 1f x row(L, L), 1f x L),
        row(1f x L, 1f x col(L, L, L)),
        // ---- 5 fotos ----
        row(col(L, L), col(L, L, L)), col(row(L, L), row(L, L, L)),
        row(2f x L, 1f x col(L, L, L, L)), col(2f x L, 1f x row(L, L, L, L)),
        row(1f x col(L, L), 1f x L, 1f x col(L, L)),
        col(1f x row(L, L), 1f x L, 1f x row(L, L)),
        col(2f x row(L, L), 1f x L, 2f x row(L, L)),
        // ---- 6 fotos ----
        row(col(L, L, L), col(L, L, L)), col(row(L, L, L), row(L, L, L)),
        row(col(L, L), col(L, L), col(L, L)),
        col(2f x row(2f x L, 1f x col(L, L)), 1f x row(L, L, L)),
        row(1f x col(L, L, L), 1f x col(L, L), 1f x L),
        // ---- 7 fotos ----
        col(row(L, L), row(L, L, L), row(L, L)),
        row(col(L, L, L), col(L, L, L, L)),
        col(2f x L, 1f x row(L, L, L, L, L, L)),
        // ---- 8 fotos ----
        row(col(L, L, L, L), col(L, L, L, L)), col(row(L, L, L, L), row(L, L, L, L)),
        col(row(L, L, L), row(L, L), row(L, L, L)),
        // ---- 9 fotos ----
        col(row(L, L, L), row(L, L, L), row(L, L, L)),
        col(row(L, L), row(L, L, L), row(L, L, L, L)),
        // ---- Mas variantes: franja central ancha, heroe con tira y filas desiguales ----
        row(1f x L, 2f x L, 1f x L), col(1f x L, 2f x L, 1f x L),
        row(1f x L, 2f x col(L, L), 1f x L), col(1f x L, 2f x row(L, L), 1f x L),
        row(1f x col(L, L), 2f x L, 1f x col(L, L)), col(1f x row(L, L), 2f x L, 1f x row(L, L)),
        row(2f x L, 1f x col(L, L, L, L, L)), col(2f x L, 1f x row(L, L, L, L, L)),
        row(2f x L, 1f x col(L, L, L, L, L, L)),
        col(2f x row(L, L), 1f x row(L, L, L, L, L, L)),
        col(2f x row(L, L, L), 1f x row(L, L, L), 1f x row(L, L, L)),
        // ---- Paginas de comic: filas desiguales, vinetas anchas y altas ----
        col(1f x row(2f x L, 1f x L), 1f x row(1f x L, 2f x L)),
        col(1f x L, 1f x row(L, L), 1f x L),
        row(1f x col(L, L, L), 1f x col(2f x L, 1f x L)),
        col(1f x row(1f x L, 2f x L), 1f x row(L, L), 1f x L),
        col(1f x row(L, L), 1f x L, 1f x row(1f x L, 2f x L)),
        col(1f x row(L, L), 1f x row(2f x L, 1f x L), 1f x row(L, L)),
        row(2f x col(L, L, L), 1f x col(L, L)),
        col(1f x L, 1f x row(L, L, L), 1f x row(L, L), 1f x L),
        col(1f x row(L, L), 1f x row(L, L, L), 1f x row(1f x L, 2f x L), 1f x L),
    )

    /** Un mismo hueco repetido: todos los huecos de la rejilla con la misma forma. */
    private val shaped: List<Pair<Node, SlotShape>> = listOf(
        // Circulos
        L to SlotShape.CIRCLE, row(L, L) to SlotShape.CIRCLE, col(L, L) to SlotShape.CIRCLE,
        row(L, L, L) to SlotShape.CIRCLE, col(L, L, L) to SlotShape.CIRCLE,
        row(col(L, L), col(L, L)) to SlotShape.CIRCLE,
        col(row(L, L), row(L, L, L)) to SlotShape.CIRCLE,
        row(col(L, L, L), col(L, L, L)) to SlotShape.CIRCLE,
        col(row(L, L, L), row(L, L, L)) to SlotShape.CIRCLE,
        col(row(L, L, L), row(L, L, L), row(L, L, L)) to SlotShape.CIRCLE,
        // Corazones
        L to SlotShape.HEART, row(L, L) to SlotShape.HEART, col(L, L) to SlotShape.HEART,
        row(L, L, L) to SlotShape.HEART, row(col(L, L), col(L, L)) to SlotShape.HEART,
        col(row(L, L), row(L, L, L)) to SlotShape.HEART,
        // Hexagonos
        L to SlotShape.HEX, row(L, L) to SlotShape.HEX, row(L, L, L) to SlotShape.HEX,
        row(col(L, L), col(L, L)) to SlotShape.HEX,
        col(row(L, L), row(L, L, L)) to SlotShape.HEX,
        col(row(L, L, L), row(L, L, L)) to SlotShape.HEX,
        col(row(L, L), row(L, L, L), row(L, L)) to SlotShape.HEX,
        // Estrellas
        L to SlotShape.STAR, row(L, L) to SlotShape.STAR, row(L, L, L) to SlotShape.STAR,
        row(col(L, L), col(L, L)) to SlotShape.STAR,
        col(row(L, L), row(L, L, L)) to SlotShape.STAR,
        // Rombos
        L to SlotShape.DIAMOND, row(L, L) to SlotShape.DIAMOND, row(L, L, L) to SlotShape.DIAMOND,
        row(col(L, L), col(L, L)) to SlotShape.DIAMOND,
        col(row(L, L), row(L, L, L)) to SlotShape.DIAMOND,
        col(row(L, L, L), row(L, L, L), row(L, L, L)) to SlotShape.DIAMOND,
        // Triangulos
        L to SlotShape.TRIANGLE, row(L, L) to SlotShape.TRIANGLE, row(L, L, L) to SlotShape.TRIANGLE,
        row(col(L, L), col(L, L)) to SlotShape.TRIANGLE,
    )

    /** Mezclas: un hueco protagonista de otra forma entre rectangulos. */
    private val mixed: List<Pair<Node, List<SlotShape>>> = run {
        val r = SlotShape.RECT
        val c = SlotShape.CIRCLE
        listOf(
            row(L, 2f x L, L) to listOf(r, c, r),
            col(L, 2f x L, L) to listOf(r, c, r),
            row(1f x col(L, L), 2f x L) to listOf(r, r, c),
            row(2f x L, 1f x col(L, L)) to listOf(SlotShape.HEART, c, c),
            col(2f x L, 1f x row(L, L, L)) to listOf(c, c, c, c),
            col(2f x L, 1f x row(L, L, L)) to listOf(SlotShape.HEART, r, r, r),
            row(1f x col(L, L), 2f x L, 1f x col(L, L)) to listOf(r, r, c, r, r),
            row(1f x col(L, L), 2f x L, 1f x col(L, L)) to listOf(c, c, SlotShape.HEART, c, c),
            col(1f x row(L, L), 2f x L, 1f x row(L, L)) to listOf(r, r, SlotShape.STAR, r, r),
        )
    }

    /** Disenos que no se pueden hacer con cortes rectos: el molinete (4 y 5 fotos). */
    private val manual: List<Pair<List<RectF>, List<SlotShape>>> = run {
        val pinwheel = listOf(
            RectF(0f, 0f, 0.62f, 0.38f), RectF(0.62f, 0f, 1f, 0.62f),
            RectF(0.38f, 0.62f, 1f, 1f), RectF(0f, 0.38f, 0.38f, 1f),
        )
        val centre = RectF(0.38f, 0.38f, 0.62f, 0.62f)
        val r = SlotShape.RECT
        listOf(
            pinwheel to emptyList<SlotShape>(),
            (pinwheel + centre) to emptyList<SlotShape>(),
            (pinwheel + centre) to listOf(r, r, r, r, SlotShape.CIRCLE),
            (pinwheel + centre) to listOf(r, r, r, r, SlotShape.HEART),
        )
    }

    val all: List<CollageLayout> by lazy {
        val perCount = HashMap<Int, Int>()
        val raw = trees.map { build(it) to emptyList<SlotShape>() } +
            shaped.map { (node, s) -> build(node).let { it to List(it.size) { s } } } +
            mixed.map { (node, s) -> build(node) to s } +
            manual
        raw.map { (rects, shapes) ->
            val n = perCount.merge(rects.size, 1, Int::plus) ?: 1
            CollageLayout(id = "c${rects.size}_$n", rects = rects, shapes = shapes)
        }
    }

    val counts: List<Int> by lazy { all.map { it.count }.distinct().sorted() }

    fun forCount(count: Int): List<CollageLayout> = all.filter { it.count == count }
}

// ======================================================================================
// Especificacion y montaje del documento
// ======================================================================================

/** Foto del collage con su encuadre dentro del hueco. */
@kotlinx.serialization.Serializable
data class CollagePhoto(
    val id: String,
    val width: Int,
    val height: Int,
    /** 1 = recubre justo el hueco; mas es acercar. */
    val zoom: Float = 1f,
    /** -1..1: desplazamiento como fraccion de lo que sobra de imagen por ese lado. */
    val panX: Float = 0f,
    val panY: Float = 0f,
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    /** Foto original; [id] pasa a ser la version con el efecto aplicado (o la misma). */
    val srcId: String = id,
) {
    /** Hueco sin foto: permite rellenar un hueco cualquiera sin seguir el orden. */
    val isEmpty: Boolean get() = id.isEmpty()

    companion object {
        val EMPTY = CollagePhoto(id = "", width = 0, height = 0)
    }
}

enum class CollageBgKind { SOLID, GRADIENT, BLUR }

/** Fondo desenfocado ya generado a partir de la primera foto, a tamano reducido. */
class CollageBlur(val bitmapId: String, val width: Int, val height: Int)

class CollageSpec(
    val layout: CollageLayout,
    /** Ancho / alto del lienzo. */
    val ratio: Float,
    /** Lado mayor en px. */
    val longSide: Int,
    /** Grosores en unidades de 1080 px del lado corto: se escalan al tamano real. */
    val gap: Float,
    val margin: Float,
    val radius: Float,
    val border: Float,
    val borderColor: Int,
    val shadow: Boolean,
    val bgKind: CollageBgKind,
    val bgColor: Int,
    val bgGradient: List<Int>,
    val blur: CollageBlur?,
    /** Bocadillos del modo comic (vacio en un collage normal). */
    val bubbles: List<Bubble> = emptyList(),
    /** Tipografia de un bocadillo por familia (null = aun sin descargar). */
    val fonts: (String) -> Typeface? = { null },
    /** Degradado: angulo (0 = izquierda a derecha, 90 = arriba a abajo) y donde empieza y acaba. */
    val bgAngle: Float = 90f,
    val bgStart: Float = 0f,
    val bgEnd: Float = 1f,
)

class CollageNames(
    val photo: (Int) -> String,
    val frame: (Int) -> String,
    val border: (Int) -> String,
    val background: String,
    val bubble: (Int) -> String = { "Bubble $it" },
)

object CollageGeometry {

    private const val EMPTY_SLOT = 0xFFCFD3D8.toInt()

    fun canvasSize(spec: CollageSpec): Pair<Int, Int> {
        val long = spec.longSide
        return if (spec.ratio >= 1f) {
            long to (long / spec.ratio).roundToInt().coerceAtLeast(16)
        } else {
            (long * spec.ratio).roundToInt().coerceAtLeast(16) to long
        }
    }

    /** Posicion (0..1) del color [i] de [n]: repartidos entre el inicio y el final elegidos. */
    fun gradientPosition(spec: CollageSpec, i: Int, n: Int): Float {
        val a = spec.bgStart.coerceIn(0f, 1f)
        val b = spec.bgEnd.coerceIn(a, 1f)
        return a + (b - a) * i / max(1f, n - 1f)
    }

    private fun unit(w: Int, h: Int): Float = min(w, h) / 1080f

    /** Rectangulos de los huecos en px del lienzo, con margen y separacion aplicados. */
    fun slotRects(spec: CollageSpec): List<RectF> {
        val (w, h) = canvasSize(spec)
        val f = unit(w, h)
        val gap = spec.gap * f
        // El hueco se encoge gap/2 por cada lado: el area se amplia esa mitad para que el
        // margen exterior sea justo el pedido.
        val m = max(0f, spec.margin * f - gap / 2f)
        val area = RectF(m, m, w - m, h - m)
        return spec.layout.rects.mapIndexed { i, n ->
            val l = (area.left + n.left * area.width() + gap / 2f).roundToInt().toFloat()
            val t = (area.top + n.top * area.height() + gap / 2f).roundToInt().toFloat()
            val r = (area.left + n.right * area.width() - gap / 2f).roundToInt().toFloat()
            val b = (area.top + n.bottom * area.height() - gap / 2f).roundToInt().toFloat()
            val cell = RectF(l, t, max(r, l + 4f), max(b, t + 4f))
            if (spec.layout.shape(i) == SlotShape.RECT) {
                cell
            } else {
                val side = min(cell.width(), cell.height()).roundToInt().toFloat()
                val sl = (cell.centerX() - side / 2f).roundToInt().toFloat()
                val st = (cell.centerY() - side / 2f).roundToInt().toFloat()
                RectF(sl, st, sl + side, st + side)
            }
        }
    }

    /** Silueta del hueco en px del lienzo (la usa la vista previa, igual que el documento). */
    fun slotPath(shape: SlotShape, r: RectF, radius: Float): Path {
        val spec = ShapeSpec(
            kind = shape.kind,
            width = r.width(),
            height = r.height(),
            cornerRadius = radius,
            sides = shape.sides,
        )
        return PathShapes.shapePath(spec).apply { offset(r.left, r.top) }
    }

    class Placement(
        val cx: Float,
        val cy: Float,
        val scale: Float,
        /** Cuanta imagen sobra a cada lado, en px: lo que se puede desplazar. */
        val overflowX: Float,
        val overflowY: Float,
    )

    fun place(slot: RectF, p: CollagePhoto): Placement {
        val odd = p.quarterTurns % 2 != 0
        val ew = (if (odd) p.height else p.width).toFloat()
        val eh = (if (odd) p.width else p.height).toFloat()
        val cover = max(slot.width() / ew, slot.height() / eh)
        val s = cover * p.zoom.coerceAtLeast(1f)
        val ox = max(0f, (ew * s - slot.width()) / 2f)
        val oy = max(0f, (eh * s - slot.height()) / 2f)
        return Placement(
            cx = slot.centerX() + p.panX.coerceIn(-1f, 1f) * ox,
            cy = slot.centerY() + p.panY.coerceIn(-1f, 1f) * oy,
            scale = s,
            overflowX = ox,
            overflowY = oy,
        )
    }

    /** Transformacion de la foto dentro de su hueco; la usan el documento y la vista previa. */
    fun photoTransform(slot: RectF, p: CollagePhoto): Xf {
        val pl = place(slot, p)
        return Xf(
            tx = pl.cx - p.width / 2f,
            ty = pl.cy - p.height / 2f,
            scaleX = pl.scale,
            scaleY = pl.scale,
            rotation = 90f * (p.quarterTurns % 4),
            flipH = p.flipH,
        )
    }

    /**
     * Monta el documento. Cada hueco son capas normales del editor, asi que despues se
     * puede mover una foto dentro de su marco, cambiar el borde o anadir efectos:
     * sombra (opcional), marco redondeado, la foto recortada al marco con la mascara de
     * recorte y, si hay borde, un contorno encima.
     *
     * La sombra va en una capa propia debajo del marco y no como estilo del marco: el
     * marco hace de base de recorte y un estilo suyo ampliaria el recorte a la sombra.
     */
    fun build(
        spec: CollageSpec,
        photos: List<CollagePhoto>,
        names: CollageNames,
        /** Guarda un bitmap en el almacen del editor y devuelve su id (bocadillos). */
        putBitmap: (Bitmap) -> String = { error("sin almacen de bitmaps") },
    ): PixaDocument {
        val (w, h) = canvasSize(spec)
        val f = unit(w, h)
        val layers = ArrayList<PixaLayer>()

        when (spec.bgKind) {
            CollageBgKind.GRADIENT -> {
                val colors = spec.bgGradient.ifEmpty { listOf(spec.bgColor, spec.bgColor) }
                layers += PixaLayer(
                    name = names.background,
                    content = LayerContent.Fill(
                        FillSpec.Gradient(
                            kind = GradientKind.LINEAR,
                            stops = colors.mapIndexed { i, c ->
                                GradientStop(gradientPosition(spec, i, colors.size), c)
                            },
                            angle = spec.bgAngle,
                        ),
                    ),
                )
            }
            CollageBgKind.BLUR -> spec.blur?.let { b ->
                val s = max(w / b.width.toFloat(), h / b.height.toFloat())
                layers += PixaLayer(
                    name = names.background,
                    content = LayerContent.Raster(b.bitmapId),
                    transform = Xf(
                        tx = (w - b.width) / 2f,
                        ty = (h - b.height) / 2f,
                        scaleX = s,
                        scaleY = s,
                    ),
                )
            }
            CollageBgKind.SOLID -> Unit
        }

        slotRects(spec).forEachIndexed { i, r ->
            val rw = r.width().roundToInt().coerceAtLeast(1)
            val rh = r.height().roundToInt().coerceAtLeast(1)
            val radius = min(spec.radius * f, min(rw, rh) / 2f)
            val shape = spec.layout.shape(i)
            val frame = ShapeSpec(
                kind = shape.kind,
                sides = shape.sides,
                width = rw.toFloat(),
                height = rh.toFloat(),
                cornerRadius = radius,
                fill = FillSpec.Solid(EMPTY_SLOT),
                strokeWidth = 0f,
            )
            val at = Xf(tx = r.left, ty = r.top)

            if (spec.shadow) {
                layers += PixaLayer(
                    name = names.frame(i + 1),
                    content = LayerContent.Shape(frame.copy(fill = FillSpec.Solid(0xFFFFFFFF.toInt()))),
                    transform = at,
                    style = LayerStyle(
                        shadowEnabled = true,
                        shadowColor = 0x73000000,
                        shadowRadius = 18f * f,
                        shadowDx = 0f,
                        shadowDy = 8f * f,
                    ),
                )
            }
            layers += PixaLayer(
                name = names.frame(i + 1),
                content = LayerContent.Shape(frame),
                transform = at,
            )

            photos.getOrNull(i)?.takeIf { !it.isEmpty }?.let { p ->
                layers += PixaLayer(
                    name = names.photo(i + 1),
                    content = LayerContent.Raster(p.id),
                    transform = photoTransform(r, p),
                    clipToBelow = true,
                )
            }

            if (spec.border > 0f) {
                // El trazo se centra en el contorno: la forma se encoge medio grosor por
                // lado para que el borde quede justo dentro del hueco.
                val bw = (spec.border * f).coerceIn(1f, max(1f, min(rw, rh) / 2f - 1f))
                layers += PixaLayer(
                    name = names.border(i + 1),
                    content = LayerContent.Shape(
                        ShapeSpec(
                            kind = shape.kind,
                            sides = shape.sides,
                            width = max(1f, rw - bw),
                            height = max(1f, rh - bw),
                            cornerRadius = max(0f, radius - bw / 2f),
                            fill = null,
                            strokeColor = spec.borderColor,
                            strokeWidth = bw,
                        ),
                    ),
                    transform = at,
                )
            }
        }

        // Cada bocadillo va pintado en una capa raster propia, encima de todo.
        spec.bubbles.forEachIndexed { i, b ->
            val (bmp, box) = BubbleDraw.rasterize(b, w, h, spec.fonts)
            layers += PixaLayer(
                name = names.bubble(i + 1),
                content = LayerContent.Raster(putBitmap(bmp)),
                transform = Xf(tx = box.left, ty = box.top),
            )
        }

        val solid = spec.bgKind == CollageBgKind.SOLID
        return PixaDocument(
            width = w,
            height = h,
            background = CanvasBackground(
                transparent = false,
                color = if (solid) spec.bgColor else 0xFFFFFFFF.toInt(),
            ),
            layers = layers,
            selectedLayerId = layers.lastOrNull()?.id,
        )
    }
}
