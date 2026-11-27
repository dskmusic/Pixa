package com.dskmusic.pixa.core.wordart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.PathParser
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.File
import java.text.Normalizer
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

/** Una forma del catalogo (assets, geometrica propia o letra). */
class ShapeItem(
    val id: String,
    val coll: String,
    val cat: String,
    val nameEn: String,
    val nameEs: String,
    /** Palabras del indice, ya normalizadas (sin tildes, en singular). */
    val tokens: List<String>,
    val nameTokens: Set<String>,
    val d: String?,
    val strokeW: Float,
) {
    fun label(es: Boolean) = if (es) nameEs else nameEn
}

/** Geometria lista para dibujar: un trazado en la caja unidad, o una mascara de mapa de bits. */
class Geom(val path: Path?, val bitmap: Bitmap?, val strokeNorm: Float = 0f)

object ShapeLibrary {

    val categories = listOf(
        "animals", "nature", "food", "music", "sport", "symbols", "shapes", "people",
        "transport", "objects", "emoji", "letters",
    )
    val collections = listOf("ph", "tb", "lu", "ms", "pixa")

    @Serializable
    private class Raw(
        val i: String, val c: String, val k: String, val n: String, val e: String, val s: String,
        val d: String, val w: Float = 0f,
    )

    @Volatile private var items: List<ShapeItem>? = null
    @Volatile private var byId: Map<String, ShapeItem> = emptyMap()
    private val pathCache = LruCache<String, Pair<Path, Float>>(500)

    suspend fun all(context: Context): List<ShapeItem> {
        items?.let { return it }
        return withContext(Dispatchers.IO) {
            val text = context.assets.open("wordart/shapes.json").bufferedReader().use { it.readText() }
            val raw = WordArtConfig.json.decodeFromString<List<Raw>>(text)
            val lib = raw.map {
                val es = it.s.substringBefore(' ')
                ShapeItem(
                    id = it.i, coll = it.c, cat = it.k, nameEn = it.n,
                    nameEs = es.replaceFirstChar { c -> c.uppercase() },
                    tokens = tokenize("${it.n} ${it.e} ${it.s}"),
                    nameTokens = tokenize(it.n).toSet(),
                    d = it.d, strokeW = it.w,
                )
            }
            val geo = GeoShapes.defs.map {
                ShapeItem(
                    it.id, "pixa", "shapes", it.en, it.es,
                    tokenize("${it.en} ${it.es} ${it.tagsEn} ${it.tagsEs} formas shapes geometria geometry"),
                    tokenize("${it.en} ${it.es}").toSet(), null, 0f,
                )
            }
            val glyphs = (('A'..'Z') + ('0'..'9')).map {
                ShapeItem(
                    "glyph:$it", "pixa", "letters", "Letter $it", "Letra $it",
                    tokenize("letra letras letter letters numero numeros number numbers $it"),
                    setOf(it.lowercase()), null, 0f,
                )
            }
            (geo + lib + glyphs).also {
                items = it
                byId = it.associateBy { s -> s.id }
            }
        }
    }

    fun find(id: String): ShapeItem? = byId[id]

    // ------------------------------------------------------------------ busqueda

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Quita plurales sencillos para que "gatos" encuentre "gato" y "flores" a "flor". */
    private fun stem(t: String): String = when {
        t.length > 4 && t.endsWith("ces") -> t.dropLast(3) + "z"
        t.length > 4 && t.endsWith("es") -> t.dropLast(2)
        t.length > 3 && t.endsWith("s") -> t.dropLast(1)
        else -> t
    }

    fun tokenize(s: String): List<String> =
        norm(s).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.map(::stem).distinct()

    private fun lev(a: String, b: String, max: Int): Int {
        if (abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** Puntuacion de un termino de busqueda contra una forma; 0 = no coincide. */
    private fun score(q: String, s: ShapeItem): Float {
        var best = 0f
        for (t in s.tokens) {
            val v = when {
                t == q -> 4f
                q.length >= 2 && t.startsWith(q) -> 3f
                q.length >= 4 && lev(q, t, if (q.length >= 8) 2 else 1) <= (if (q.length >= 8) 2 else 1) -> 1.5f
                else -> 0f
            }
            if (v > best) best = v
        }
        if (best > 0f && q in s.nameTokens) best += 1f
        return best
    }

    fun search(all: List<ShapeItem>, query: String, cat: String?, coll: String?): List<ShapeItem> {
        val filtered = all.filter { (cat == null || it.cat == cat) && (coll == null || it.coll == coll) }
        val qs = tokenize(query)
        if (qs.isEmpty()) return filtered
        return filtered.mapNotNull { s ->
            var total = 0f
            for (q in qs) {
                val v = score(q, s)
                if (v == 0f) return@mapNotNull null
                total += v
            }
            s to total
        }.sortedByDescending { it.second }.map { it.first }
    }

    // ------------------------------------------------------------------ trazados

    /** Trazado de la forma en la caja unidad; null si no se puede interpretar. */
    fun unitPath(item: ShapeItem): Pair<Path, Float>? {
        pathCache.get(item.id)?.let { return it }
        val result: Pair<Path, Float>? = when {
            item.id.startsWith("geo:") -> GeoShapes.build(item.id)?.let { it to 0f }
            item.d != null -> runCatching {
                GeoShapes.normalize(PathParser.createPathFromPathData(item.d), item.strokeW)
            }.getOrNull()
            else -> null
        }
        if (result != null) pathCache.put(item.id, result)
        return result
    }

    fun textGeom(text: String, face: Typeface, bold: Boolean): Geom? {
        val lines = text.split('\n').filter { it.isNotEmpty() }.take(6)
        if (lines.isEmpty()) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 100f }
        val path = Path()
        var y = 0f
        for (line in lines) {
            val p = Path()
            paint.getTextPath(line, 0, line.length, 0f, 0f, p)
            p.transform(Matrix().apply { setTranslate(-paint.measureText(line) / 2f, y) })
            path.addPath(p)
            y += paint.fontSpacing
        }
        val (norm, stroke) = GeoShapes.normalize(path, if (bold) 7f else 0f)
        return Geom(norm, null, stroke)
    }

    // ------------------------------------------------------------------ favoritas y recientes

    private fun prefs(context: Context) = context.getSharedPreferences("wordart", Context.MODE_PRIVATE)

    fun favorites(context: Context): Set<String> =
        prefs(context).getString("favs", "").orEmpty().split('|').filter { it.isNotEmpty() }.toSet()

    fun toggleFavorite(context: Context, id: String): Set<String> {
        val cur = favorites(context)
        val next = if (id in cur) cur - id else cur + id
        prefs(context).edit().putString("favs", next.joinToString("|")).apply()
        return next
    }

    fun recents(context: Context): List<String> =
        prefs(context).getString("recents", "").orEmpty().split('|').filter { it.isNotEmpty() }

    fun addRecent(context: Context, id: String): List<String> {
        val next = (listOf(id) + recents(context).filter { it != id }).take(30)
        prefs(context).edit().putString("recents", next.joinToString("|")).apply()
        return next
    }

    // ------------------------------------------------------------------ formas propias

    private fun importDir(context: Context) = File(context.filesDir, "wordart/shapes").apply { mkdirs() }

    fun importedIds(context: Context): List<String> =
        (importDir(context).listFiles()?.filter { it.extension == "png" } ?: emptyList())
            .sortedByDescending { it.lastModified() }.map { "img:${it.nameWithoutExtension}" }

    fun importedFile(context: Context, id: String) = File(importDir(context), id.removePrefix("img:") + ".png")

    fun deleteImported(context: Context, id: String) {
        importedFile(context, id).delete()
    }

    fun loadImported(context: Context, id: String, invert: Boolean): Bitmap? {
        val src = BitmapFactory.decodeFile(importedFile(context, id).absolutePath) ?: return null
        if (!invert) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, -1f, 255f,
        ))
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        src.recycle()
        return out
    }

    /** Importa un PNG/JPG/SVG como mascara blanca con alfa. Devuelve el id, o null si falla. */
    suspend fun importFrom(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = uri.lastPathSegment.orEmpty().lowercase()
            val type = context.contentResolver.getType(uri).orEmpty()
            val bmp = if (type.contains("svg") || name.endsWith(".svg")) {
                val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                svgToBitmap(text) ?: return@runCatching null
            } else {
                BitmapUtils.decodeUri(context, uri, 1024)
            }
            val mask = toMask(bmp)
            val id = UUID.randomUUID().toString().take(8)
            File(importDir(context), "$id.png").outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
            mask.recycle()
            "img:$id"
        }.getOrNull()
    }

    /** Con transparencia manda el alfa; sin ella, lo oscuro es la forma (silueta negra sobre blanco). */
    private fun toMask(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var transparent = false
        for (p in px) if ((p ushr 24) < 250) { transparent = true; break }
        for (i in px.indices) {
            val p = px[i]
            val a = if (transparent) {
                p ushr 24
            } else {
                val l = (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p))
                ((200f - l) * 4f).toInt().coerceIn(0, 255)
            }
            px[i] = (a shl 24) or 0xFFFFFF
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** SVG sencillo: path, circle, ellipse, rect y polygon. Sin transformaciones ni estilos. */
    private fun svgToBitmap(svg: String): Bitmap? {
        val path = Path()
        fun attr(tag: String, name: String) =
            Regex("""\s$name\s*=\s*["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
        fun f(tag: String, name: String) = attr(tag, name)?.toFloatOrNull() ?: 0f
        Regex("<(path|circle|ellipse|rect|polygon)\\b[^>]*>").findAll(svg).forEach { m ->
            val tag = m.value
            when (m.groupValues[1]) {
                "path" -> attr(tag, "d")?.let { d -> runCatching { path.addPath(PathParser.createPathFromPathData(d)) } }
                "circle" -> path.addCircle(f(tag, "cx"), f(tag, "cy"), f(tag, "r"), Path.Direction.CW)
                "ellipse" -> path.addOval(
                    RectF(f(tag, "cx") - f(tag, "rx"), f(tag, "cy") - f(tag, "ry"), f(tag, "cx") + f(tag, "rx"), f(tag, "cy") + f(tag, "ry")),
                    Path.Direction.CW,
                )
                "rect" -> path.addRect(f(tag, "x"), f(tag, "y"), f(tag, "x") + f(tag, "width"), f(tag, "y") + f(tag, "height"), Path.Direction.CW)
                "polygon" -> attr(tag, "points")?.let { pts ->
                    val n = Regex("-?[\\d.]+").findAll(pts).map { it.value.toFloat() }.toList()
                    for (i in 0 until n.size / 2) if (i == 0) path.moveTo(n[0], n[1]) else path.lineTo(n[2 * i], n[2 * i + 1])
                    path.close()
                }
            }
        }
        if (path.isEmpty) return null
        val (unit, _) = GeoShapes.normalize(path, 0f)
        val size = 1024
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        unit.transform(Matrix().apply { setScale(size * 0.96f, size * 0.96f); postTranslate(size * 0.02f, size * 0.02f) })
        Canvas(bmp).drawPath(unit, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
        return bmp
    }
}
