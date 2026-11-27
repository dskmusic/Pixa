package com.dskmusic.pixa.ui.wordart

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.export.ExportFormat
import com.dskmusic.pixa.core.export.ExportOptions
import com.dskmusic.pixa.core.export.Exporter
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.wordart.BackgroundSpec
import com.dskmusic.pixa.core.wordart.BuiltinPresets
import com.dskmusic.pixa.core.wordart.ColorMode
import com.dskmusic.pixa.core.wordart.GeoShapes
import com.dskmusic.pixa.core.wordart.RotMode
import com.dskmusic.pixa.core.wordart.Geom
import com.dskmusic.pixa.core.wordart.Layout
import com.dskmusic.pixa.core.wordart.NamedBg
import com.dskmusic.pixa.core.wordart.NamedConfig
import com.dskmusic.pixa.core.wordart.NamedFx
import com.dskmusic.pixa.core.wordart.NamedText
import com.dskmusic.pixa.core.wordart.PresetBundle
import com.dskmusic.pixa.core.wordart.PresetStore
import com.dskmusic.pixa.core.wordart.ShapeFx
import com.dskmusic.pixa.core.wordart.ShapeItem
import com.dskmusic.pixa.core.wordart.ShapeLibrary
import com.dskmusic.pixa.core.wordart.TextBlock
import com.dskmusic.pixa.core.wordart.WordArtConfig
import com.dskmusic.pixa.core.wordart.WordArtEngine
import com.dskmusic.pixa.core.wordart.WordLists
import com.dskmusic.pixa.pixa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Estado de la nube de palabras: configuracion con historial, vista previa en segundo plano,
 * biblioteca de formas y presets.
 */
@OptIn(FlowPreview::class)
class WordArtViewModel(private val app: Application) : AndroidViewModel(app) {

    val es: Boolean = app.resources.configuration.locales[0].language == "es"
    private val fonts = app.pixa.fonts
    private val root = File(app.filesDir, "wordart").apply { mkdirs() }
    private val lastFile = File(root, "last.json")
    private val store = PresetStore(app)

    // Cada vez que se entra en la herramienta se parte de una combinacion nueva.
    private val _config = MutableStateFlow(randomConfig(null))
    private val startConfig = _config.value
    val config: StateFlow<WordArtConfig> = _config.asStateFlow()

    // ---------------------------------------------------------------- historial
    private val undo = ArrayList<WordArtConfig>()
    private val redo = ArrayList<WordArtConfig>()
    private var committed = _config.value
    private val _history = MutableStateFlow(0)
    val canUndo: StateFlow<Int> = _history.asStateFlow()
    val hasUndo get() = undo.isNotEmpty()
    val hasRedo get() = redo.isNotEmpty()

    /** Aplica un cambio. Con [commit] false (arrastrar un slider) no abre paso de historial todavia. */
    fun edit(commit: Boolean = true, f: (WordArtConfig) -> WordArtConfig) {
        _config.update(f)
        if (commit) commit()
    }

    fun commit() {
        val now = _config.value
        if (now == committed) return
        undo.add(committed)
        if (undo.size > 80) undo.removeAt(0)
        redo.clear()
        committed = now
        _history.update { it + 1 }
    }

    fun undo() {
        if (undo.isEmpty()) return
        redo.add(_config.value)
        _config.value = undo.removeAt(undo.lastIndex)
        committed = _config.value
        _history.update { it + 1 }
    }

    fun redo() {
        if (redo.isEmpty()) return
        undo.add(_config.value)
        _config.value = redo.removeAt(redo.lastIndex)
        committed = _config.value
        _history.update { it + 1 }
    }

    fun msgText(res: Int): String = app.getString(res)

    fun regenerate() = edit { it.copy(seed = Random.nextLong(1, 1_000_000_000)) }

    fun reset() = edit { initial() }

    /** Otra combinacion al azar de forma, palabras, colores, tipografia, fondo y efectos. */
    fun randomize() = edit { randomConfig(pickLibShape()) }

    private fun pickLibShape(): String? =
        _library.value.filter { !it.id.startsWith("glyph:") && it.strokeW == 0f }.randomOrNull()?.id

    private fun randomConfig(shape: String?): WordArtConfig {
        val r = Random(System.nanoTime())
        val bgs = BuiltinPresets.bgs
        val bgI = r.nextInt(bgs.size)
        val dark = bgI == 2
        val names = when {
            dark -> listOf("vivid", "gold", "pastel", "neon", "sunset")
            bgI == 0 -> listOf("vivid", "love", "ocean", "forest", "sunset", "neon")
            else -> listOf("vivid", "love", "ocean", "forest", "sunset", "mono")
        }
        val pal = WordArtConfig.PALETTES.first { it.first == names.random(r) }.second
        val text = BuiltinPresets.texts.filter { dark || it.nameRes != R.string.wa_t_neon }.random(r).block
        val inverted = r.nextInt(100) < 15
        val pastel = listOf(0xFFFFCDD2, 0xFFBBDEFB, 0xFFC8E6C9, 0xFFFFF9C4, 0xFFE1BEE7).random(r).toInt()
        return WordArtConfig(
            shapeId = shape ?: GeoShapes.defs.random(r).id,
            inverted = inverted,
            wordsText = WordLists.presets.random(r).text(es),
            density = 55f + r.nextInt(30),
            rotMode = listOf(RotMode.MIXED, RotMode.MIXED, RotMode.HORIZONTAL, RotMode.RANDOM, RotMode.ANGLES).random(r),
            rotAngles = "0,-30,30",
            seed = r.nextLong(1, 1_000_000_000),
        ).withTextBlock(text).copy(
            palette = pal,
            colorMode = listOf(ColorMode.PALETTE, ColorMode.PALETTE, ColorMode.RANDOM, ColorMode.BY_WEIGHT, ColorMode.GRADIENT).random(r),
            color1 = pal.first(),
            color2 = pal.last(),
            gradAngle = r.nextInt(360).toFloat(),
            fontMode = if (r.nextBoolean()) 1 else 0,
            fontPool = r.nextInt(3),
            bgWords = bgs[bgI].words,
            bgOther = if (inverted) BackgroundSpec(com.dskmusic.pixa.core.wordart.BgKind.SOLID, color = pastel) else BackgroundSpec(),
            fx = BuiltinPresets.fxs.random(r).fx,
        )
    }

    private fun initial() = WordArtConfig(
        wordsText = WordLists.presets.first { it.id == "love" }.text(es),
        seed = Random.nextLong(1, 1_000_000),
    )

    private fun loadLast(): WordArtConfig = runCatching {
        WordArtConfig.json.decodeFromString<WordArtConfig>(lastFile.readText())
    }.getOrNull() ?: initial()

    // ---------------------------------------------------------------- vista previa
    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()
    private val fontTick = MutableStateFlow(0)

    private var lastKey: List<Any?>? = null
    private var lastLayout: Layout? = null
    private val geomCache = HashMap<String, Geom>()
    private val imgCache = ConcurrentHashMap<String, Bitmap>()
    private val renderLock = Mutex()
    private val imageFn: (String, Int) -> Bitmap? = { p, s -> imageOf(p, s) }

    init {
        viewModelScope.launch {
            fonts.loaded.collect { fontTick.update { it + 1 } }
        }
        viewModelScope.launch {
            combine(_config, fontTick) { c, t -> c to t }.debounce(120).collectLatest { (cfg, _) ->
                _working.value = true
                try {
                    val (geom, tfs, layout) = produce(cfg, WordArtEngine.LAYOUT_SIDE)
                    val (pw, ph) = WordArtEngine.dims(cfg.aspect, PREVIEW_SIDE)
                    _preview.value = withContext(Dispatchers.Default) {
                        WordArtEngine.render(cfg, geom, layout, tfs, pw, ph, imageFn)
                    }
                    withContext(Dispatchers.IO) {
                        runCatching { lastFile.writeText(WordArtConfig.json.encodeToString(cfg)) }
                    }
                } finally {
                    _working.value = false
                }
            }
        }
    }

    private fun tfsFor(cfg: WordArtConfig): List<Typeface> =
        if (cfg.fontMode == 0) listOf(fonts.cached(cfg.font) ?: Typeface.DEFAULT)
        else WordArtConfig.FONT_POOLS[cfg.fontPool.coerceIn(0, WordArtConfig.POOL_COUNT - 1)]
            .map { fonts.cached(it) ?: Typeface.DEFAULT }

    private suspend fun buildGeom(cfg: WordArtConfig): Geom? {
        val id = cfg.shapeId
        return when {
            id == "text" -> ShapeLibrary.textGeom(cfg.shapeText, fonts.load(cfg.shapeFont) ?: Typeface.DEFAULT_BOLD, true)
            id.startsWith("glyph:") -> ShapeLibrary.textGeom(id.removePrefix("glyph:"), Typeface.DEFAULT_BOLD, true)
            id.startsWith("img:") -> withContext(Dispatchers.IO) {
                ShapeLibrary.loadImported(app, id, cfg.invertMask)?.let { Geom(null, it) }
            }
            else -> {
                ShapeLibrary.all(app)
                ShapeLibrary.find(id)?.let { ShapeLibrary.unitPath(it) }?.let { (p, s) -> Geom(p, null, s) }
            }
        }
    }

    private suspend fun geomFor(cfg: WordArtConfig): Geom {
        val key = when {
            cfg.shapeId == "text" -> "text|${cfg.shapeText}|${cfg.shapeFont}"
            cfg.shapeId.startsWith("img:") -> "${cfg.shapeId}|${cfg.invertMask}"
            else -> cfg.shapeId
        }
        synchronized(geomCache) { geomCache[key] }?.let { return it }
        val g = buildGeom(cfg) ?: buildGeom(cfg.copy(shapeId = "geo:circle"))!!
        synchronized(geomCache) { geomCache[key] = g }
        return g
    }

    private data class Produced(val geom: Geom, val tfs: List<Typeface>, val layout: Layout)

    private suspend fun produce(cfg: WordArtConfig, side: Int): Produced {
        val geom = geomFor(cfg)
        val tfs = tfsFor(cfg)
        val key = cfg.layoutKey(listOf(fontTick.value, side))
        val cached = lastLayout
        if (cached != null && key == lastKey) return Produced(geom, tfs, cached)
        val layout = WordArtEngine.layout(cfg, geom, tfs, side)
        lastKey = key
        lastLayout = layout
        return Produced(geom, tfs, layout)
    }

    /** Imagen de fondo ya decodificada al tamano necesario; las pequenas se guardan en cache. */
    fun imageOf(path: String, maxSide: Int): Bitmap? {
        val key = "$path|$maxSide"
        imgCache[key]?.let { return it }
        val file = File(path)
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bm = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (maxSide <= PREVIEW_SIDE + 100) imgCache[key] = bm
        return bm
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b

    // ---------------------------------------------------------------- exportacion
    suspend fun renderFull(px: Int): Bitmap = renderLock.withLock {
        val cfg = _config.value
        val (geom, tfs, layout) = produce(cfg, WordArtEngine.LAYOUT_SIDE)
        val (w, h) = WordArtEngine.dims(cfg.aspect, px)
        withContext(Dispatchers.Default) { WordArtEngine.render(cfg, geom, layout, tfs, w, h, imageFn) }
    }

    suspend fun saveToGallery(px: Int): Boolean {
        val bm = runCatching { renderFull(px) }.getOrNull() ?: return false
        val ok = withContext(Dispatchers.IO) {
            runCatching { Exporter.saveToGallery(app, bm, ExportOptions(ExportFormat.PNG, baseName = "nube_formas")) }.isSuccess
        }
        bm.recycle()
        return ok
    }

    // ---------------------------------------------------------------- formas
    private val _library = MutableStateFlow<List<ShapeItem>>(emptyList())
    val library: StateFlow<List<ShapeItem>> = _library.asStateFlow()
    private val _favorites = MutableStateFlow(ShapeLibrary.favorites(app))
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    private val _recents = MutableStateFlow(ShapeLibrary.recents(app))
    val recents: StateFlow<List<String>> = _recents.asStateFlow()
    private val _imported = MutableStateFlow(ShapeLibrary.importedIds(app))
    val imported: StateFlow<List<String>> = _imported.asStateFlow()

    init {
        viewModelScope.launch {
            _library.value = ShapeLibrary.all(app)
            // Si el usuario aun no ha tocado nada, la forma inicial pasa a ser una de la biblioteca.
            if (_config.value == startConfig) pickLibShape()?.let { id ->
                _config.update { it.copy(shapeId = id) }
                committed = _config.value
            }
        }
    }

    fun pickShape(id: String) {
        edit { it.copy(shapeId = id) }
        _recents.value = ShapeLibrary.addRecent(app, id)
    }

    fun toggleFavorite(id: String) {
        _favorites.value = ShapeLibrary.toggleFavorite(app, id)
    }

    fun importShape(uri: Uri, onFail: () -> Unit) {
        viewModelScope.launch {
            val id = ShapeLibrary.importFrom(app, uri)
            if (id == null) {
                onFail()
            } else {
                _imported.value = ShapeLibrary.importedIds(app)
                pickShape(id)
            }
        }
    }

    fun deleteImported(id: String) {
        ShapeLibrary.deleteImported(app, id)
        _imported.value = ShapeLibrary.importedIds(app)
        if (_config.value.shapeId == id) pickShape("geo:circle")
    }

    /** Copia la imagen elegida a la app (reducida) para que el fondo no dependa de la galeria. */
    fun importBackground(uri: Uri, target: Int, onFail: () -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val bm = BitmapUtils.decodeUri(app, uri, 2048)
                    val f = File(File(root, "bg").apply { mkdirs() }, UUID.randomUUID().toString().take(8) + ".jpg")
                    f.outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    bm.recycle()
                    f.absolutePath
                }.getOrNull()
            }
            if (path == null) onFail() else edit { c ->
                when (target) {
                    2 -> c.copy(bgCanvas = c.bgCanvas.copy(imagePath = path))
                    0 -> c.copy(bgWords = c.bgWords.copy(imagePath = path))
                    else -> c.copy(bgOther = c.bgOther.copy(imagePath = path))
                }
            }
        }
    }

    // ---------------------------------------------------------------- presets
    private val _presets = MutableStateFlow(store.load())
    val presets: StateFlow<PresetBundle> = _presets.asStateFlow()
    private val thumbs = ConcurrentHashMap<String, Bitmap>()
    private val thumbLock = Mutex()

    private fun savePresets(b: PresetBundle) {
        _presets.value = b
        store.save(b)
    }

    fun saveFull(name: String) {
        val b = _presets.value
        savePresets(b.copy(full = b.full + NamedConfig(store.newId(), name, _config.value)))
    }

    fun saveText(name: String) {
        val b = _presets.value
        savePresets(b.copy(text = b.text + NamedText(store.newId(), name, _config.value.toTextBlock())))
    }

    fun saveBg(name: String) {
        val b = _presets.value
        val c = _config.value
        savePresets(b.copy(bg = b.bg + NamedBg(store.newId(), name, c.bgWords, c.bgOther)))
    }

    fun saveShape(name: String) {
        val b = _presets.value
        savePresets(b.copy(shape = b.shape + NamedFx(store.newId(), name, _config.value.fx)))
    }

    fun deletePreset(kind: Int, id: String) {
        val b = _presets.value
        savePresets(when (kind) {
            0 -> b.copy(full = b.full.filter { it.id != id })
            1 -> b.copy(text = b.text.filter { it.id != id })
            2 -> b.copy(bg = b.bg.filter { it.id != id })
            else -> b.copy(shape = b.shape.filter { it.id != id })
        })
        thumbs.remove(id)
    }

    fun applyFull(c: WordArtConfig) = edit { c }
    fun applyText(b: TextBlock) = edit { it.withTextBlock(b) }
    fun applyBg(w: BackgroundSpec, o: BackgroundSpec) = edit { it.copy(bgWords = w, bgOther = o) }
    fun applyFx(fx: ShapeFx) = edit { it.copy(fx = fx) }

    fun exportPresets(): String = store.export(_presets.value)

    fun importPresets(text: String): Boolean {
        val merged = store.merge(_presets.value, text) ?: return false
        savePresets(merged)
        return true
    }

    /** Miniatura de un preset completo. Se calcula una vez, de uno en uno, para no saturar el movil. */
    suspend fun thumb(key: String, cfg: WordArtConfig): Bitmap? {
        thumbs[key]?.let { return it }
        return thumbLock.withLock {
            thumbs[key] ?: runCatching {
                val geom = geomFor(cfg)
                val tfs = tfsFor(cfg)
                val layout = WordArtEngine.layout(cfg, geom, tfs, 320)
                val (w, h) = WordArtEngine.dims(cfg.aspect, 240)
                withContext(Dispatchers.Default) { WordArtEngine.render(cfg, geom, layout, tfs, w, h, imageFn) }
            }.getOrNull()?.also { thumbs[key] = it }
        }
    }

    companion object {
        const val PREVIEW_SIDE = 720
    }
}
