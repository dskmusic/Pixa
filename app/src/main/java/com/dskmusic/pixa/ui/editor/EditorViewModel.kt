package com.dskmusic.pixa.ui.editor

import com.dskmusic.pixa.ml.Segmenter
import com.dskmusic.pixa.ml.SamSession
import com.dskmusic.pixa.ml.SamMask
import kotlinx.coroutines.flow.update
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.export.ExportFormat
import com.dskmusic.pixa.core.export.ExportException
import com.dskmusic.pixa.core.export.ExportOptions
import com.dskmusic.pixa.core.export.Exporter
import com.dskmusic.pixa.core.history.HistoryStack
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.LayerGroup
import com.dskmusic.pixa.core.model.LayerStyle
import com.dskmusic.pixa.core.model.PixaBlend
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.project.ProjectEntry
import com.dskmusic.pixa.core.render.Curtain
import com.dskmusic.pixa.core.selection.Selection
import com.dskmusic.pixa.core.selection.SelectionTools
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.widget.PixaWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Un paso del historial tal y como lo ve la UI. */
data class HistoryEntryUi(@StringRes val labelRes: Int, val index: Int)

/** Aviso corto de una sola vez (snackbar). */
data class EditorMessage(
    val id: Long,
    @StringRes val textRes: Int,
    val arg: String? = null,
    /** Uri a abrir si el aviso ofrece "Ver". */
    val viewUri: Uri? = null,
    val viewMime: String? = null,
)

/**
 * Estado del editor completo (requisitos 6, 7, 16).
 *
 * El documento es inmutable: cada operacion produce uno nuevo y lo mete en el historial.
 * El bitmap compuesto se recalcula aparte en segundo plano, de modo que mover un control
 * nunca espera a que acabe un render.
 */
data class EditorState(
    val doc: PixaDocument = PixaDocument(width = 1080, height = 1080),
    val composite: Bitmap? = null,
    /** true mientras el compuesto mostrado es de resolucion reducida (gestos). */
    val compositeIsPreview: Boolean = false,
    val selection: Selection? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val history: List<HistoryEntryUi> = emptyList(),
    val historyCursor: Int = -1,
    val projectId: String? = null,
    val projectName: String = "",
    val dirty: Boolean = false,
    val busyText: Int? = null,
    /** true si el aviso de ocupado ofrece cancelar (IA, descargas). */
    val busyCancellable: Boolean = false,
    /** Segunda linea del aviso de ocupado (tiempo restante estimado, etc.). */
    val busyDetail: String? = null,
    /** 0..1, o null para indeterminado mientras [busyText] no sea null. */
    val busyProgress: Float? = null,
    val message: EditorMessage? = null,
    val ready: Boolean = false,
    /**
     * Uri de la imagen que se abrio, si el documento viene de una.
     *
     * Solo la usa el panel de informacion, que lee EXIF del archivo original: una vez
     * editada, la imagen en memoria ya no tiene esos datos.
     */
    val sourceUri: Uri? = null,
    /**
     * Sube cada vez que [EditorViewModel.install] carga un documento nuevo (lienzo,
     * imagen o proyecto). La UI del editor lo observa para resetear herramienta y panel:
     * sin esto, abrir una imagen nueva hereda la herramienta con la que se dejo la
     * anterior (por ejemplo Recortar), y entonces un dedo ya no mueve la vista porque
     * [EditorTool.CanvasTool.MOVE] no es la herramienta activa.
     */
    val documentGeneration: Int = 0,
    /**
     * Panel a abrir nada mas cargar el documento (requisito del usuario: los accesos
     * directos de Home a "quitar fondo" abren el editor ya con ese panel desplegado, sin
     * que el usuario tenga que navegar hasta el). Se consume una vez y se limpia solo.
     */
    val pendingPanel: EditorPanel? = null,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.pixa
    private val bitmaps = container.bitmaps
    private val renderer = container.renderer
    private val projects = container.projects
    private val recents = container.recents

    private val history = HistoryStack(bitmaps)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val doc: PixaDocument get() = _state.value.doc

    /**
     * true si el editor se lanzo desde otra app como editor/visor (ACTION_EDIT/ACTION_VIEW,
     * p.ej. "Editar con Pixa" desde la Galeria). En ese caso, al guardar/cerrar la app debe
     * finalizar para devolver el control a la app que la invoco, en vez de quedarse en su
     * pantalla de inicio.
     */
    var launchedExternally: Boolean = false

    /** Accion de IA que el panel debe lanzar al abrirse desde Inicio ("mark" o "restore"); la consume el panel. */
    var aiLaunch: String? = null

    /**
     * Peticiones de render pendientes, como maximo una.
     *
     * El render no es suspendible, asi que cancelar su Job no lo detiene: antes cada
     * movimiento de deslizador dejaba otro compuesto de pantalla completa calculandose
     * hasta el final y la imagen llegaba segundos tarde. Con un unico trabajador y una
     * cola de un hueco solo se calcula el ultimo estado pedido, que es el que se ve.
     */
    private val renderRequests = Channel<Boolean>(Channel.CONFLATED)
    private var renderLoop: Job? = null
    private var autosaveJob: Job? = null
    private var autosaveEnabled = true
    private var maxResolution = 4096
    private var messageSeq = 0L

    /** Documento temporal de vista previa (sliders): se pinta pero no entra al historial. */
    private var previewDoc: PixaDocument? = null
        set(value) {
            field = value
            previewActive = value != null
            if (value == null) pendingApply = null
        }

    /** true mientras un panel muestra una vista previa que aun no se ha aplicado. */
    var previewActive by mutableStateOf(false)
        private set

    /**
     * Aplica la vista previa en curso, si la hay. Lo registra la propia vista previa del
     * panel; sirve para el aviso de "cambios sin aplicar" al salir del panel.
     */
    var pendingApply: (() -> Unit)? = null

    /** Cortina antes/despues: preferencia del usuario y posicion (0..1 del ancho). */
    var curtainOn by mutableStateOf(false)
        private set
    var curtainPos by mutableStateOf(0.5f)
        private set

    /** Valor por defecto de la cortina al entrar en un panel (viene de los ajustes). */
    var curtainDefault: Boolean = false

    /** Cortina a su estado inicial: segun los ajustes y en el centro. */
    fun resetCurtain() {
        curtainOn = curtainDefault
        curtainPos = 0.5f
        if (previewDoc != null) rerender(preview = true)
    }

    /** Al salir de un panel con cortina: no debe quedar activa en el resto de herramientas. */
    fun releaseCurtain() {
        curtainOn = false
    }

    fun toggleCurtain(on: Boolean) {
        if (curtainOn == on) return
        curtainOn = on
        if (previewDoc != null) rerender(preview = true)
    }

    fun moveCurtain(pos: Float) {
        val p = pos.coerceIn(0f, 1f)
        if (curtainPos == p) return
        curtainPos = p
        if (previewDoc != null && curtainOn) rerender(preview = true)
    }

    /** Mientras se mantiene pulsado el lienzo se pinta el documento sin la vista previa. */
    private var peeking = false

    init {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            history.setLimit(settings.historyLimit)
            autosaveEnabled = settings.autosave
            maxResolution = settings.maxResolution
        }
    }

    // ----------------------------------------------------------------------------------
    // Arranque del documento
    // ----------------------------------------------------------------------------------

    val hasDocument: Boolean get() = _state.value.ready

    /**
     * Lienzo nuevo. Requisito del usuario: ya con una capa vacia lista para pintar o
     * editar, en vez de un documento sin ninguna capa que obligara a anadir una primero.
     */
    fun startNew(width: Int, height: Int, background: CanvasBackground) {
        val w = width.coerceIn(16, maxResolution)
        val h = height.coerceIn(16, maxResolution)
        val blank = runCatching { BitmapUtils.create(w, h) }.getOrNull()
        val layer = blank?.let {
            PixaLayer(
                name = appString(R.string.layer_add_empty),
                content = LayerContent.Raster(bitmaps.put(it)),
                // Requisito del usuario: como el fondo de Photoshop al crear un documento
                // en blanco, no se mueve ni se redimensiona, pero se sigue pintando y
                // editando con normalidad (por eso transformLocked, no locked).
                transformLocked = true,
            )
        }
        val document = PixaDocument(
            width = w,
            height = h,
            background = background,
            layers = listOfNotNull(layer),
            selectedLayerId = layer?.id,
        )
        install(document, projectId = null, name = "", initialLabel = R.string.history_initial)
    }

    /** Documento de una sola capa a partir de un bitmap ya decodificado. */
    fun startFromBitmap(bitmap: Bitmap, name: String = "") {
        val clamped = BitmapUtils.clampToMax(bitmap, maxResolution)
        val id = bitmaps.put(clamped)
        // Requisito del usuario: la foto abierta es la capa base y no se mueve ni redimensiona
        // sola, pero sigue aceptando filtros/ajustes/retoque: por eso transformLocked, no locked.
        val layer = PixaLayer(
            name = appString(R.string.layer_image),
            content = LayerContent.Raster(id),
            transformLocked = true,
        )
        val document = PixaDocument(
            width = clamped.width,
            height = clamped.height,
            background = CanvasBackground(transparent = true),
            layers = listOf(layer),
            selectedLayerId = layer.id,
        )
        install(document, projectId = null, name = name, initialLabel = R.string.history_initial)
    }

    /**
     * Abrir una imagen de la galeria o recibida de otra app.
     *
     * [openPanel], si llega, se guarda para que el editor lo abra solo nada mas montarse
     * (accesos directos de Home como "quitar fondo", que llevan directos a ese panel).
     */
    fun startFromUri(uri: Uri, openPanel: EditorPanel? = null) {
        busy(R.string.label_loading)
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                // Nombre del archivo original y bitmap, todo en el hilo de I/O.
                val name = displayNameFor(uri)
                val bitmap = runCatching { BitmapUtils.decodeUri(getApplication(), uri, maxResolution) }.getOrNull()
                if (bitmap == null) null else name to bitmap
            }
            idle()
            if (resolved == null) {
                post(R.string.error_load_image)
                return@launch
            }
            startFromBitmap(resolved.second, resolved.first)
            // Despues de startFromBitmap, que pasa por install y limpia el origen.
            _state.value = _state.value.copy(sourceUri = uri, pendingPanel = openPanel)
            // Requisito del usuario: queda en Recientes solo con abrirla, aunque no se
            // edite ni se guarde nada todavia. Va aqui y no dentro de startFromBitmap
            // porque hace falta la uri, que todavia no se conoce alli.
            addToRecents()
        }
    }

    /** Nombre legible del archivo de una uri; si no se puede saber, solo el tramo final. */
    private fun displayNameFor(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        Log.d("PixaReplace", "displayNameFor input=$uri")

        // Con acceso total a archivos se puede leer _data (ruta real) de la uri real de
        // MediaStore y sacar de ahi el nombre de la foto original.
        if (Exporter.canAccessAllFiles()) {
            val realUri = Exporter.resolveMediaUri(getApplication(), uri) ?: uri
            val dataPath = runCatching {
                resolver.query(
                    realUri,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    null, null, null,
                )?.use { c ->
                    val i = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (c.moveToFirst() && i >= 0 && !c.isNull(i)) c.getString(i).orEmpty() else ""
                }.orEmpty()
            }.getOrDefault("")
            Log.d("PixaReplace", "  _data='$dataPath'")
            if (dataPath.isNotBlank()) {
                // Copia temporal de cache de otra app (p.ej. la camara de Xiaomi comparte
                // `Android/data/.../cache/seguridad/1793555838849.jpg`): no tiene nombre real
                // que mostrar, asi que se usa un nombre generico en vez del timestamp.
                val norm = dataPath.replace('\\', '/')
                if (norm.contains("/Android/data/")) {
                    Log.d("PixaReplace", "  -> cache de otra app, nombre generico")
                    return appString(R.string.layer_image)
                }
                val name = dataPath.substringAfterLast('/')
                Log.d("PixaReplace", "  -> nombre desde _data='$name'")
                return name
            }
        }

        // Las uris del Photo Picker esconden el nombre real: se resuelve la uri REAL.
        val isPicker = uri.authority == "media" && uri.path.orEmpty().contains("picker")
        if (isPicker) {
            val realUri = Exporter.resolveMediaUri(getApplication(), uri)
            if (realUri != null && realUri != uri) {
                val real = runCatching {
                    resolver.query(
                        realUri,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                        null, null, null,
                    )?.use { c ->
                        val i = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && i >= 0 && !c.isNull(i)) c.getString(i).orEmpty() else ""
                    }.orEmpty()
                }.getOrDefault("")
                Log.d("PixaReplace", "  nombre desde realUri='$real'")
                if (real.isNotBlank()) return real
            }
        }

        // Nombre directo (para uris reales de MediaStore o de otra app).
        val fromProvider = runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val a = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val b = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val first = if (a >= 0 && !cursor.isNull(a)) cursor.getString(a).orEmpty() else ""
                    val second = if (b >= 0 && !cursor.isNull(b)) cursor.getString(b).orEmpty() else ""
                    Log.d("PixaReplace", "  displayName directa: openable='$first' media='$second'")
                    first.ifBlank { second }
                } else ""
            }.orEmpty()
        }.getOrDefault("")
        if (fromProvider.isNotBlank()) return fromProvider

        val fallback = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        Log.d("PixaReplace", "  -> fallback final='$fallback'")
        return fallback
    }

    /** Se llama una vez consumido [EditorState.pendingPanel], para no reabrirlo despues. */
    fun consumePendingPanel() {
        _state.value = _state.value.copy(pendingPanel = null)
    }

    /** Varias imagenes como capas de un mismo documento. */
    fun startFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busy(R.string.label_loading)
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { BitmapUtils.decodeUri(getApplication(), uri, maxResolution) }.getOrNull()
                }
            }
            idle()
            if (loaded.isEmpty()) {
                post(R.string.error_load_image)
                return@launch
            }
            val width = loaded.maxOf { it.width }
            val height = loaded.maxOf { it.height }
            val layers = loaded.mapIndexed { index, bitmap ->
                PixaLayer(
                    name = "${appString(R.string.layer_image)} ${index + 1}",
                    content = LayerContent.Raster(bitmaps.put(bitmap)),
                    transform = Xf(
                        tx = (width - bitmap.width) / 2f,
                        ty = (height - bitmap.height) / 2f,
                    ),
                )
            }
            val document = PixaDocument(
                width = width,
                height = height,
                background = CanvasBackground(transparent = true),
                layers = layers,
                selectedLayerId = layers.last().id,
            )
            install(document, null, "", R.string.history_initial)
        }
    }

    fun openProject(id: String) {
        busy(R.string.label_loading)
        viewModelScope.launch {
            val entry = projects.entry(id)
            val loaded = runCatching { projects.open(id, bitmaps) }.getOrNull()
            idle()
            if (loaded == null) {
                post(R.string.error_generic)
                return@launch
            }
            install(loaded.document, id, entry?.name ?: "", R.string.history_initial)
            container.settings.setLastProjectId(id)
            PixaWidget.refresh(getApplication())
        }
    }

    fun importProject(uri: Uri, fallbackName: String) {
        busy(R.string.label_loading)
        viewModelScope.launch {
            val entry = runCatching { projects.import(uri, bitmaps, fallbackName) }.getOrNull()
            idle()
            if (entry == null) {
                post(R.string.pixa_import_failed)
                return@launch
            }
            post(R.string.pixa_import_ok)
            openProject(entry.id)
        }
    }

    /** Recuperacion tras un cierre inesperado (requisito 5). */
    fun hasAutosave(): Boolean = projects.hasAutosave()

    fun restoreAutosave() {
        busy(R.string.label_loading)
        viewModelScope.launch {
            val document = runCatching { projects.loadAutosave(bitmaps) }.getOrNull()
            idle()
            if (document == null) {
                post(R.string.error_generic)
                return@launch
            }
            install(document, null, "", R.string.history_initial)
            markDirty()
        }
    }

    fun discardAutosave() = projects.clearAutosave()

    /** Instala un documento como estado inicial, reiniciando historial y seleccion. */
    fun install(
        document: PixaDocument,
        projectId: String?,
        name: String,
        @StringRes initialLabel: Int,
    ) {
        history.clear()
        history.push(initialLabel, document)
        previewDoc = null
        _state.value = _state.value.copy(
            doc = document,
            selection = null,
            projectId = projectId,
            projectName = name,
            dirty = false,
            ready = true,
            sourceUri = null,
            // Un aviso del documento anterior no tiene sentido en este: sin esto, el
            // "Auto-mejora aplicada" de una foto salia al abrir la siguiente.
            message = null,
            documentGeneration = _state.value.documentGeneration + 1,
        )
        syncHistory()
        rerender()
    }

    // ----------------------------------------------------------------------------------
    // Historial
    // ----------------------------------------------------------------------------------

    /** Aplica una operacion sobre el documento y la registra en el historial. */
    fun edit(@StringRes labelRes: Int, block: (PixaDocument) -> PixaDocument) {
        val next = block(doc)
        if (next === doc) return
        commit(labelRes, next)
    }

    fun commit(@StringRes labelRes: Int, next: PixaDocument) {
        previewDoc = null
        history.push(labelRes, next)
        _state.value = _state.value.copy(doc = next, dirty = true)
        syncHistory()
        rerender()
        scheduleAutosave()
    }

    /**
     * Cambio sin historial: lo usan los sliders mientras se arrastran y los gestos del
     * lienzo. El estado del documento no se toca hasta que se confirma con [commit].
     */
    fun previewDocument(next: PixaDocument, fast: Boolean = false) {
        previewDoc = next
        // En gestos con el dedo (mover, estirar) se renderiza a menos resolucion para que
        // vaya fluido; al soltar, el commit repinta a la calidad normal.
        previewBudget = if (fast) FAST_PREVIEW_PX else PREVIEW_PX
        rerender(preview = true)
    }

    private var previewBudget = PREVIEW_PX

    fun cancelPreview() {
        if (previewDoc == null) return
        previewDoc = null
        rerender()
    }

    fun undo() {
        history.undo()?.let { applyHistory(it) }
    }

    fun redo() {
        history.redo()?.let { applyHistory(it) }
    }

    fun jumpToHistory(index: Int) {
        history.jumpTo(index)?.let { applyHistory(it) }
    }

    private fun applyHistory(document: PixaDocument) {
        previewDoc = null
        _state.value = _state.value.copy(doc = document, dirty = true)
        syncHistory()
        rerender()
    }

    fun setHistoryLimit(limit: Int) {
        history.setLimit(limit)
        syncHistory()
    }

    private fun syncHistory() {
        _state.value = _state.value.copy(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            history = history.steps.mapIndexed { index, entry ->
                HistoryEntryUi(entry.labelRes, index)
            },
            historyCursor = history.cursor,
        )
    }

    // ----------------------------------------------------------------------------------
    // Render
    // ----------------------------------------------------------------------------------

    /**
     * Recompone el lienzo en segundo plano. Con [preview] se usa resolucion reducida:
     * es lo que mantiene fluidos el pinza-zoom y los sliders en documentos grandes
     * (requisito 6).
     */
    fun rerender(preview: Boolean = false) {
        if (renderLoop == null) {
            renderLoop = viewModelScope.launch {
                for (requested in renderRequests) renderOnce(requested)
            }
        }
        renderRequests.trySend(preview)
    }

    /**
     * Un render. El documento se lee aqui y no al pedirlo: si mientras se calculaba el
     * anterior han llegado mas cambios, se pinta directamente el estado final.
     */
    private suspend fun renderOnce(preview: Boolean) {
        val target = if (peeking) doc else (previewDoc ?: doc)
        // Cortina antes/despues: solo con una vista previa activa y sin el "mantener
        // pulsado" (que ya ensena el original entero).
        val split = curtainPos.takeIf { curtainOn && !peeking && previewDoc != null }
        val bitmap = withContext(Dispatchers.Default) {
            runCatching {
                val after = if (preview) renderer.renderPreview(target, previewBudget) else renderer.render(target)
                if (split == null) {
                    after
                } else {
                    val before = if (preview) renderer.renderPreview(doc, previewBudget) else renderer.render(doc)
                    Curtain.compose(before, after, split)
                }
            }.getOrNull()
        }
        if (bitmap == null) {
            post(R.string.out_of_memory)
            return
        }
        val old = _state.value.composite
        _state.value = _state.value.copy(composite = bitmap, compositeIsPreview = preview)
        // El compuesto anterior no lo referencia nadie mas: se libera a mano para no
        // dejar varios bitmaps de pantalla completa esperando al recolector.
        if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
    }

    /**
     * Comparar antes y despues (requisito 13): mientras esta activo se ignora la vista
     * previa del panel. Sin vista previa activa no hay nada que comparar y no se
     * recompone nada, que es lo barato y lo correcto.
     */
    fun setPeek(on: Boolean) {
        if (peeking == on) return
        peeking = on
        if (previewDoc != null) rerender(preview = true)
    }

    /** Pixeles actuales del lienzo completo, para herramientas que necesitan leerlos. */
    suspend fun compositeNow(): Bitmap = withContext(Dispatchers.Default) {
        renderer.render(doc)
    }

    fun bitmapOf(layer: PixaLayer): Bitmap? =
        (layer.content as? LayerContent.Raster)?.let { bitmaps.get(it.bitmapId) }

    /** Guarda pixeles nuevos y devuelve el id para meterlo en una capa. */
    fun putBitmap(bitmap: Bitmap): String = bitmaps.put(bitmap)

    /** Devuelve al almacen un bitmap con su id original (recuperacion de borradores). */
    fun putBitmap(id: String, bitmap: Bitmap) = bitmaps.put(id, bitmap)

    fun getBitmap(id: String?): Bitmap? = bitmaps.get(id)

    // ----------------------------------------------------------------------------------
    // Capas
    // ----------------------------------------------------------------------------------

    /**
     * Sube cada vez que una capa cambia de naturaleza (p. ej. al rasterizarla): los paneles
     * que leen [selectedLayer] se recomponen y vuelven a evaluar si ya pueden usar la capa.
     */
    var layerEpoch by mutableStateOf(0)
        private set

    val selectedLayer: PixaLayer?
        get() {
            // Lectura de estado de Compose: suscribe al panel que llama a este getter.
            layerEpoch
            return doc.selected
        }

    fun selectLayer(layerId: String?) {
        _state.value = _state.value.copy(doc = doc.copy(selectedLayerId = layerId))
    }

    fun addLayer(@StringRes labelRes: Int, layer: PixaLayer) {
        commit(labelRes, doc.addLayerOnTop(layer).copy(selectedLayerId = layer.id))
    }

    fun addRasterLayer(bitmap: Bitmap, name: String, @StringRes labelRes: Int = R.string.layer_add) {
        val centered = Xf(
            tx = (doc.width - bitmap.width) / 2f,
            ty = (doc.height - bitmap.height) / 2f,
        )
        addLayer(
            labelRes,
            PixaLayer(
                name = name,
                content = LayerContent.Raster(bitmaps.put(bitmap)),
                transform = centered,
            ),
        )
    }

    /** Reemplaza los pixeles de una capa raster. Usa un id nuevo para no romper el historial. */
    fun replaceRaster(layerId: String, bitmap: Bitmap, @StringRes labelRes: Int) {
        val id = bitmaps.put(bitmap)
        // Quitar el fondo libera el bloqueo de mover/transformar de la capa base.
        val unlock = labelRes in BG_REMOVE_LABELS
        commit(labelRes, doc.updateLayer(layerId) {
            it.copy(
                content = LayerContent.Raster(id),
                transformLocked = it.transformLocked && !unlock,
            )
        })
    }


    /**
     * Sustituye los pixeles de una capa por una version al DOBLE de resolucion y duplica
     * el lienzo con todas sus capas, en un solo paso de historial. El resultado es una
     * imagen del doble de ancho y alto con la misma composicion, y asi el tamano del
     * lienzo (y el de un archivo exportado) refleja de verdad la ampliacion.
     *
     * Las demas capas no cambian de pixeles: se escalan sus transformaciones, igual que
     * hace "Incluir capas" al redimensionar el lienzo. La capa ampliada conserva su escala
     * porque su contenido ya es el doble de grande.
     */
    fun replaceRasterDoubleCanvas(
        layerId: String,
        bitmap: Bitmap,
        @StringRes labelRes: Int,
        factor: Int = 2,
    ) {
        val f = factor.toFloat()
        val d = doc
        val rasterizer = getApplication<Application>().pixa.rasterizer
        val id = bitmaps.put(bitmap)
        val layers = d.layers.map { layer ->
            val xf = layer.transform
            val persp = xf.perspective
            if (persp != null) {
                // La perspectiva son esquinas en coordenadas de documento: basta duplicarlas.
                val scaled = xf.copy(perspective = persp.map { it * f })
                return@map if (layer.id == layerId) {
                    layer.copy(content = LayerContent.Raster(id), transform = scaled)
                } else {
                    layer.copy(transform = scaled)
                }
            }
            val (w, h) = LayerGeometry.intrinsic(layer, rasterizer, d)
            if (layer.id == layerId) {
                layer.copy(
                    content = LayerContent.Raster(id),
                    transform = xf.copy(tx = xf.tx * f, ty = xf.ty * f),
                )
            } else {
                layer.copy(
                    transform = xf.copy(
                        scaleX = xf.scaleX * f,
                        scaleY = xf.scaleY * f,
                        tx = (xf.tx + w / 2f) * f - w / 2f,
                        ty = (xf.ty + h / 2f) * f - h / 2f,
                    ),
                )
            }
        }
        commit(labelRes, d.copy(width = d.width * factor, height = d.height * factor, layers = layers))
    }

    fun updateLayer(@StringRes labelRes: Int, layerId: String, block: (PixaLayer) -> PixaLayer) {
        commit(labelRes, doc.updateLayer(layerId, block))
    }

    /**
     * Al ocultar, la capa se bloquea sola para que no se edite por error mientras no se ve;
     * al volver a mostrarla se desbloquea. Es un unico paso de historial, no dos.
     */
    fun setLayerVisible(layerId: String, visible: Boolean) =
        updateLayer(R.string.layer_visible, layerId) {
            it.copy(visible = visible, locked = !visible)
        }

    fun setLayerLocked(layerId: String, locked: Boolean) =
        updateLayer(R.string.layer_locked, layerId) { it.copy(locked = locked) }

    fun setLayerOpacity(layerId: String, opacity: Float) =
        updateLayer(R.string.label_opacity, layerId) { it.copy(opacity = opacity.coerceIn(0f, 1f)) }

    fun setLayerBlend(layerId: String, blend: PixaBlend) =
        updateLayer(R.string.layer_blend_mode, layerId) { it.copy(blend = blend) }

    fun setLayerStyle(layerId: String, style: LayerStyle?) =
        updateLayer(R.string.layer_style_title, layerId) { it.copy(style = style) }

    fun renameLayer(layerId: String, name: String) =
        updateLayer(R.string.action_rename, layerId) { it.copy(name = name) }

    fun deleteLayer(layerId: String) {
        commit(R.string.action_delete, doc.removeLayer(layerId))
    }

    fun duplicateLayer(layerId: String) {
        val layer = doc.layer(layerId) ?: return
        val copy = layer.copy(
            id = PixaLayer.newId(),
            name = "${layer.name} 2",
            content = duplicateContent(layer.content),
            // El bloqueo de mover / escalar es de la capa base de una imagen abierta; la copia
            // es una capa nueva y se puede transformar.
            transformLocked = false,
            mask = layer.mask?.let { mask ->
                bitmaps.get(mask.bitmapId)?.let { bmp ->
                    mask.copy(bitmapId = bitmaps.put(BitmapUtils.mutableCopy(bmp)))
                } ?: mask
            },
        )
        val index = doc.indexOf(layerId)
        val layers = doc.layers.toMutableList().apply { add(index + 1, copy) }
        commit(R.string.action_duplicate, doc.copy(layers = layers, selectedLayerId = copy.id))
    }

    private fun duplicateContent(content: LayerContent): LayerContent = when (content) {
        is LayerContent.Raster -> {
            val source = bitmaps.get(content.bitmapId)
            if (source == null) content
            else content.copy(bitmapId = bitmaps.put(BitmapUtils.mutableCopy(source)))
        }
        else -> content
    }

    fun moveLayer(from: Int, to: Int) {
        commit(R.string.layer_reorder_hint, doc.moveLayer(from, to))
    }

    /** Fusiona la capa seleccionada con la de debajo rasterizando las dos. */
    fun mergeDown(layerId: String) {
        val index = doc.indexOf(layerId)
        if (index <= 0) return
        val below = doc.layers[index - 1]
        busy(R.string.layer_merge_down)
        viewModelScope.launch {
            val merged = withContext(Dispatchers.Default) {
                renderer.render(
                    doc,
                    includeBackground = false,
                    onlyLayerIds = setOf(below.id, layerId),
                )
            }
            idle()
            val id = bitmaps.put(merged)
            val replacement = PixaLayer(
                id = below.id,
                name = below.name,
                content = LayerContent.Raster(id),
            )
            val layers = doc.layers.toMutableList()
            layers[index - 1] = replacement
            layers.removeAt(index)
            commit(
                R.string.layer_merge_down,
                doc.copy(layers = layers, selectedLayerId = replacement.id),
            )
        }
    }

    /** Aplana todo a una sola capa, conservando el fondo. */
    fun flatten() {
        busy(R.string.layer_flatten)
        viewModelScope.launch {
            val flat = withContext(Dispatchers.Default) { renderer.render(doc) }
            idle()
            val layer = PixaLayer(
                name = appString(R.string.layer_flatten),
                content = LayerContent.Raster(bitmaps.put(flat)),
            )
            commit(
                R.string.layer_flatten,
                doc.copy(
                    layers = listOf(layer),
                    groups = emptyList(),
                    selectedLayerId = layer.id,
                    background = CanvasBackground(transparent = true),
                ),
            )
        }
    }

    /**
     * Capa no raster que espera confirmacion para rasterizarse (requisito 14).
     *
     * Antes las herramientas de pixeles se limitaban a avisar con "selecciona una capa de
     * imagen", que no dice que se puede hacer al respecto. Ahora el aviso es una pregunta,
     * porque rasterizar no se puede deshacer sin el historial: el texto deja de ser
     * editable.
     */
    var rasterizeAsk by mutableStateOf<PixaLayer?>(null)
        private set

    fun askRasterize(layer: PixaLayer) {
        rasterizeAsk = layer
    }

    fun dismissRasterize() {
        rasterizeAsk = null
    }

    fun confirmRasterize() {
        val layer = rasterizeAsk ?: return
        rasterizeAsk = null
        rasterizeLayer(layer.id)
    }

    /** Rasteriza una capa vectorial o de texto para poder pintar sobre ella. */
    fun rasterizeLayer(layerId: String) {
        val layer = doc.layer(layerId) ?: return
        if (layer.isRaster) return
        busy(R.string.text_rasterize)
        viewModelScope.launch {
            // Se rasteriza el contenido a su propio tamano (el texto, la forma...) y la capa
            // conserva su transformacion, su estilo, su opacidad y su modo de fusion: asi el
            // resultado solo ocupa lo que ocupa el contenido y se le pueden aplicar efectos.
            // Solo las capas de relleno, que cubren el lienzo, pasan por el render completo.
            val tight = withContext(Dispatchers.Default) {
                if (layer.content is LayerContent.Fill) {
                    null
                } else {
                    getApplication<Application>().pixa.rasterizer.rasterize(layer)
                        ?.let { BitmapUtils.mutableCopy(it) }
                }
            }
            val bitmap = tight ?: withContext(Dispatchers.Default) {
                renderer.renderSingleLayer(doc, layerId)
            }
            idle()
            commit(
                R.string.text_rasterize,
                doc.updateLayer(layerId) {
                    it.copy(
                        content = LayerContent.Raster(bitmaps.put(bitmap)),
                        transform = if (tight != null) it.transform else Xf(),
                        style = if (tight != null) it.style else null,
                    )
                }.copy(selectedLayerId = layerId),
            )
            // La capa ya es de imagen y sigue seleccionada: los paneles abiertos se refrescan
            // solos y muestran sus opciones (efectos, filtros, ajustes...) sin reseleccionar.
            layerEpoch++
        }
    }

    fun groupSelected(layerIds: Set<String>) {
        if (layerIds.size < 2) return
        val group = LayerGroup(id = PixaLayer.newId(), name = appString(R.string.layer_group))
        val layers = doc.layers.map { layer ->
            if (layer.id in layerIds) layer.copy(groupId = group.id) else layer
        }
        commit(R.string.layer_group, doc.copy(layers = layers, groups = doc.groups + group))
    }

    fun ungroup(groupId: String) {
        val layers = doc.layers.map { if (it.groupId == groupId) it.copy(groupId = null) else it }
        commit(
            R.string.layer_ungroup,
            doc.copy(layers = layers, groups = doc.groups.filterNot { it.id == groupId }),
        )
    }

    fun setGroupVisible(groupId: String, visible: Boolean) {
        commit(
            R.string.layer_visible,
            doc.copy(
                groups = doc.groups.map {
                    if (it.id == groupId) it.copy(visible = visible) else it
                },
            ),
        )
    }

    fun setGroupCollapsed(groupId: String, collapsed: Boolean) {
        _state.value = _state.value.copy(
            doc = doc.copy(
                groups = doc.groups.map {
                    if (it.id == groupId) it.copy(collapsed = collapsed) else it
                },
            ),
        )
    }

    fun setBackground(background: CanvasBackground) {
        commit(R.string.label_background, doc.copy(background = background))
    }

    fun resizeDocument(width: Int, height: Int, layers: List<PixaLayer>) {
        commit(R.string.canvas_size, doc.copy(width = width, height = height, layers = layers))
    }

    // ----------------------------------------------------------------------------------
    // Seleccion
    // ----------------------------------------------------------------------------------

    fun setSelection(selection: Selection?) {
        val cleaned = selection?.takeIf { !it.isEmpty }
        _state.value = _state.value.copy(selection = cleaned)
    }

    fun combineSelection(other: Selection, mode: Selection.Mode) {
        val current = _state.value.selection
        val next = if (current == null || mode == Selection.Mode.NEW) {
            other
        } else {
            current.combine(other, mode)
        }
        setSelection(next)
    }

    fun selectAll() {
        setSelection(SelectionTools.rect(doc.width, doc.height, android.graphics.RectF(
            0f, 0f, doc.width.toFloat(), doc.height.toFloat(),
        )))
    }

    fun invertSelection() {
        val current = _state.value.selection ?: return
        setSelection(current.inverted())
    }

    fun clearSelection() = setSelection(null)

    /**
     * Portapapeles interno de la seleccion (requisito 8).
     *
     * Es del editor y no del sistema a proposito: copiar un trozo de capa y pegarlo como
     * capa nueva debe conservar la transparencia exacta, y el portapapeles de Android no
     * garantiza eso al pasar por un PNG comprimido.
     */
    var clipboard: Bitmap? = null

    // ----------------------------------------------------------------------------------
    // Guardar y exportar
    // ----------------------------------------------------------------------------------

    fun saveProject(name: String, onSaved: (ProjectEntry) -> Unit = {}) {
        busy(R.string.save_project)
        viewModelScope.launch {
            val thumb = withContext(Dispatchers.Default) {
                runCatching { BitmapUtils.thumbnail(renderer.render(doc), 512) }.getOrNull()
            }
            val entry = runCatching {
                projects.save(_state.value.projectId, name, doc, bitmaps, thumb)
            }.getOrNull()
            idle()
            if (entry == null) {
                post(R.string.error_save)
                return@launch
            }
            projects.clearAutosave()
            container.settings.setLastProjectId(entry.id)
            // El widget ensena el nombre de la ultima edicion: se repinta al guardar.
            PixaWidget.refresh(getApplication())
            _state.value = _state.value.copy(
                projectId = entry.id,
                projectName = entry.name,
                dirty = false,
            )
            post(R.string.project_saved)
            onSaved(entry)
        }
    }

    /**
     * Guarda una copia numerada "v2", "v3"... del proyecto abierto (requisito 2).
     * Si todavia no se habia guardado nunca, no hay version de la que partir y se guarda
     * normal con el nombre propuesto.
     */
    fun saveAsVersion(onSaved: (ProjectEntry) -> Unit = {}) {
        val current = _state.value
        val base = current.projectName.ifBlank { appString(R.string.app_name_short) }
        val sourceId = current.projectId
        if (sourceId == null) {
            saveProject(base, onSaved)
            return
        }
        busy(R.string.save_as_version)
        viewModelScope.launch {
            val thumb = withContext(Dispatchers.Default) {
                runCatching { BitmapUtils.thumbnail(renderer.render(doc), 512) }.getOrNull()
            }
            val entry = runCatching {
                projects.saveAsVersion(sourceId, doc, bitmaps, thumb)
            }.getOrNull()
            idle()
            if (entry == null) {
                post(R.string.error_save)
                return@launch
            }
            _state.value = _state.value.copy(
                projectId = entry.id,
                projectName = entry.name,
                dirty = false,
            )
            post(R.string.project_saved)
            onSaved(entry)
        }
    }

    /**
     * Exporta a la galeria (requisito 3: esto si aparece en Galeria > Pixa, al contrario
     * que guardar el proyecto).
     */
    fun exportImage(
        options: ExportOptions,
        onlyLayerId: String? = null,
        cropToSelection: Boolean = false,
        replaceOriginal: Boolean = false,
    ) {
        val sourceUri = _state.value.sourceUri
        // Requisito del usuario: "reemplazar el original" solo tiene sentido si el
        // documento viene de una imagen abierta. Si se pide sin origen, se ignora el
        // flag y se sigue el camino normal de "guardar como".
        val replace = replaceOriginal && sourceUri != null
        busy(if (replace) R.string.export_replace else R.string.export_title)
        // La seleccion se lee aqui, en el hilo principal, para no tocar el estado dentro
        // de la corrutina de trabajo.
        val bounds = if (cropToSelection) _state.value.selection?.bounds else null
        // El render completo se reutiliza para "Recientes" (solo si es el documento entero):
        // antes se volvia a renderizar y codificar aparte despues de exportar.
        var forRecents: Bitmap? = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val rendered = if (onlyLayerId != null) {
                        renderer.renderSingleLayer(doc, onlyLayerId)
                    } else {
                        renderer.render(doc)
                    }
                    val source = if (bounds != null) {
                        BitmapUtils.crop(rendered, bounds).also { rendered.recycle() }
                    } else {
                        rendered
                    }
                    if (replace) {
                        // Sobrescribe la imagen original en su sitio. Devuelve la uri de
                        // origen para el aviso "Ver"; si no se puede, ExportException hace
                        // que el resultado falle y se avisa al usuario.
                        val ok = Exporter.replaceOriginal(getApplication(), sourceUri!!, source, options)
                        if (ok && onlyLayerId == null && bounds == null) forRecents = source else source.recycle()
                        if (!ok) throw ExportException("No se pudo sobrescribir el original")
                        sourceUri
                    } else {
                        // saveToGallery ya aplica la escala y el aplanado de ExportOptions.
                        val uri = Exporter.saveToGallery(getApplication(), source, options)
                        if (onlyLayerId == null && bounds == null) forRecents = source else source.recycle()
                        uri
                    }
                }
            }
            idle()
            val uri = result.getOrNull()
            if (uri == null) {
                forRecents?.recycle()
                post(R.string.export_failed)
                return@launch
            }
            addToRecents(forRecents)
            // Para "Ver", con reemplazo se abre la uri real de MediaStore (no la del Photo
            // Picker, que es efimera/opaca y el visor no puede abrir); si no es reemplazo,
            // la uri recien creada en Pictures/Pixa.
            val viewUri = if (replace) {
                Exporter.resolveMediaUri(getApplication(), uri) ?: uri
            } else {
                uri
            }
            postWithView(if (replace) R.string.export_replaced else R.string.image_saved, viewUri, options.format.mime)
        }
    }

    /** Prepara el archivo para compartir y devuelve su uri por callback. */
    fun shareImage(options: ExportOptions, onReady: (Uri, String) -> Unit) {
        busy(R.string.share_image)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val source = renderer.render(doc)
                    val uri = Exporter.writeShareFile(getApplication(), source, options)
                    source.recycle()
                    uri
                }
            }
            idle()
            val uri = result.getOrNull()
            if (uri == null) {
                post(R.string.export_failed)
                return@launch
            }
            onReady(uri, options.format.mime)
        }
    }

    /** Copia el .pixa a la cache y lo entrega para compartirlo con otro telefono. */
    fun shareProjectFile(onReady: (File) -> Unit) {
        val id = _state.value.projectId
        if (id == null) {
            post(R.string.save_project)
            return
        }
        viewModelScope.launch {
            val file = runCatching { projects.exportFile(id) }.getOrNull()
            if (file == null) post(R.string.error_generic) else onReady(file)
        }
    }

    /** Renderiza el documento a JPEG y entrega el archivo listo para imprimir. */
    fun printImage(onReady: (File) -> Unit) {
        busy(R.string.action_print)
        viewModelScope.launch {
            val file = withContext(Dispatchers.Default) {
                runCatching {
                    val source = renderer.render(doc)
                    val options = ExportOptions(format = ExportFormat.JPEG)
                    val prepared = Exporter.prepare(source, options)
                    val bytes = try {
                        Exporter.encode(prepared, options)
                    } finally {
                        if (prepared !== source) prepared.recycle()
                        source.recycle()
                    }
                    val dir = File(getApplication<Application>().cacheDir, "print").apply { mkdirs() }
                    val out = File(dir, Exporter.fileName(options))
                    out.writeBytes(bytes)
                    out
                }
            }
            idle()
            val result = file.getOrNull()
            if (result == null) {
                post(R.string.export_failed)
                return@launch
            }
            onReady(result)
        }
    }

    private fun addToRecents(rendered: Bitmap? = null) {
        viewModelScope.launch {
            // El compuesto completo, no una miniatura: RecentsStore guarda su propia
            // copia editable a partir de el, que es lo que hace que reabrir funcione
            // siempre (ver comentario en RecentsStore.add). Si la exportacion ya lo
            // renderizo, se reutiliza en vez de renderizar otra vez.
            val full = rendered ?: withContext(Dispatchers.Default) {
                runCatching { renderer.render(doc) }.getOrNull()
            } ?: return@launch
            runCatching {
                recents.add(
                    label = _state.value.projectName.ifBlank { appString(R.string.app_name_short) },
                    full = full,
                    sourceUri = _state.value.sourceUri?.toString(),
                    projectId = _state.value.projectId,
                )
            }
            full.recycle()
        }
    }

    /**
     * Reabre un reciente desde su copia local, sin depender de ningun permiso externo.
     *
     * [sourceUri] es la Uri original de la que venia la imagen (si se conoce): se conserva
     * en el estado para que "reemplazar el original" siga disponible aunque la imagen se
     * hubiera abierto por la copia local y no directamente por la Uri.
     */
    fun startFromRecentFile(file: File, name: String = "", sourceUri: Uri? = null) {
        busy(R.string.label_loading)
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { BitmapUtils.decodeFile(file, maxResolution) }.getOrNull()
            }
            idle()
            if (bitmap == null) {
                post(R.string.error_load_image)
                return@launch
            }
            startFromBitmap(bitmap, name)
            // Despues de startFromBitmap (que pasa por install y limpia el origen) se
            // restaura la Uri original para no perder la opcion de reemplazar.
            if (sourceUri != null) _state.value = _state.value.copy(sourceUri = sourceUri)
        }
    }

    // ----------------------------------------------------------------------------------
    // Autoguardado
    // ----------------------------------------------------------------------------------

    private fun scheduleAutosave() {
        if (!autosaveEnabled) return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(AUTOSAVE_DELAY_MS)
            runCatching { projects.autosave(doc, bitmaps) }
        }
    }

    fun markDirty() {
        _state.value = _state.value.copy(dirty = true)
    }

    fun clearAutosaveAfterExit() {
        autosaveJob?.cancel()
        projects.clearAutosave()
    }

    // ----------------------------------------------------------------------------------
    // Ocupado y avisos
    // ----------------------------------------------------------------------------------

    fun busy(@StringRes textRes: Int, progress: Float? = null) {
        _state.value = _state.value.copy(busyText = textRes, busyProgress = progress)
    }

    private var busyJob: Job? = null

    fun busyDetail(text: String?) {
        _state.update { it.copy(busyDetail = text) }
    }

    // ----------------------------------------------------------------------------------
    // Seleccion por toque con IA (MobileSAM)
    // ----------------------------------------------------------------------------------

    private var samSession: SamSession? = null
    private var samDoc: PixaDocument? = null
    private val samPoints = mutableListOf<FloatArray>()
    private val samLabels = mutableListOf<Int>()

    /** true mientras hay una imagen analizada lista para recibir toques. */
    var samActive by mutableStateOf(false)
        private set
    var samPointCount by mutableStateOf(0)
        private set

    /** Zona dibujada alrededor del objeto (x0, y0, x1, y1 en documento); los toques la afinan. */
    private var samBoxHint: FloatArray? = null

    fun samBegin(session: SamSession) {
        samSession = session
        samDoc = doc
        samPoints.clear()
        samLabels.clear()
        samBoxHint = null
        samPointCount = 0
        samActive = true
    }

    fun samEnd() {
        samSession = null
        samDoc = null
        samPoints.clear()
        samLabels.clear()
        samBoxHint = null
        samPointCount = 0
        samActive = false
    }

    /** Quita todos los toques y la seleccion que produjeron. */
    fun samClear() {
        samPoints.clear()
        samLabels.clear()
        samBoxHint = null
        samPointCount = 0
        setSelection(null)
    }

    /** Toque en coordenadas de documento: anade (o quita) al objeto y recalcula la seleccion. */
    fun samTap(x: Float, y: Float, positive: Boolean) {
        samPoints += floatArrayOf(x, y)
        samLabels += if (positive) 1 else 0
        samPointCount = samPoints.size
        samDecode()
    }

    /** Dibujar alrededor del objeto: la caja del trazo es la indicacion; sustituye a la anterior. */
    fun samBox(l: Float, t: Float, r: Float, b: Float) {
        samBoxHint = floatArrayOf(
            l.coerceIn(0f, doc.width - 2f), t.coerceIn(0f, doc.height - 2f),
            r.coerceIn(2f, doc.width.toFloat()), b.coerceIn(2f, doc.height.toFloat()),
        )
        samDecode()
    }

    private fun samDecode() {
        val session = samSession ?: return
        // Si el documento cambio desde el analisis, la mascara ya no casaria con la imagen.
        if (samDoc !== doc) {
            post(R.string.ai_sam_stale)
            samEnd()
            return
        }
        val points = samPoints.toList()
        val labels = samLabels.toList()
        val box = samBoxHint
        val d = doc
        viewModelScope.launch {
            val mask = Segmenter.decode(getApplication(), session, points, labels, box = box)
            if (mask == null) {
                post(R.string.ai_failed)
                return@launch
            }
            val bytes = withContext(Dispatchers.Default) { upsampleMask(mask, d.width, d.height) }
            setSelection(Selection.of(d.width, d.height, bytes))
        }
    }

    /** Escala la mascara del modelo (1024 px) al tamano del documento con interpolacion bilineal. */
    fun upsampleMask(m: SamMask, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        val sx = m.width.toFloat() / w
        val sy = m.height.toFloat() / h
        for (y in 0 until h) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, m.height - 1f)
            val y0 = fy.toInt()
            val y1 = minOf(y0 + 1, m.height - 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, m.width - 1f)
                val x0 = fx.toInt()
                val x1 = minOf(x0 + 1, m.width - 1)
                val tx = fx - x0
                val a = m.coverage[y0 * m.width + x0].toInt() and 0xFF
                val b = m.coverage[y0 * m.width + x1].toInt() and 0xFF
                val c = m.coverage[y1 * m.width + x0].toInt() and 0xFF
                val e = m.coverage[y1 * m.width + x1].toInt() and 0xFF
                val top = a + (b - a) * tx
                val bot = c + (e - c) * tx
                out[y * w + x] = (top + (bot - top) * ty).toInt().coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    /** Marca [job] como la tarea que el aviso de ocupado puede cancelar. */
    fun makeBusyCancellable(job: Job) {
        busyJob = job
        _state.value = _state.value.copy(busyCancellable = true)
    }

    /** Cancela la tarea en curso (IA, descarga de modelo) y apaga el aviso. */
    fun cancelBusy() {
        busyJob?.cancel()
        busyJob = null
        idle()
        post(R.string.ai_cancelled)
    }

    fun progress(value: Float) {
        _state.update { it.copy(busyProgress = value.coerceIn(0f, 1f)) }
    }

    fun idle() {
        busyJob = null
        _state.value = _state.value.copy(busyText = null, busyProgress = null, busyCancellable = false, busyDetail = null)
    }

    /**
     * Espera al compuesto a resolucion completa que sigue a un [commit].
     *
     * El bitmap de pixeles se calcula aparte, en el bucle de [rerender]: sin esperar aqui,
     * [idle] apagaba el aviso de "procesando" justo antes de que el lienzo recompusiera
     * todas las capas a tamano completo, y la app parecia congelarse un instante con el
     * aviso ya apagado. No es que el filtro se calculara dos veces (sigue siendo una sola
     * pasada a resolucion completa); es que esa segunda fase no tenia aviso propio.
     */
    suspend fun awaitFullRender() {
        val before = _state.value.composite
        state.first { it.composite !== before && !it.compositeIsPreview }
    }

    fun post(@StringRes textRes: Int, arg: String? = null) {
        _state.value = _state.value.copy(
            message = EditorMessage(++messageSeq, textRes, arg),
        )
    }

    private fun postWithView(@StringRes textRes: Int, uri: Uri, mime: String) {
        _state.value = _state.value.copy(
            message = EditorMessage(++messageSeq, textRes, viewUri = uri, viewMime = mime),
        )
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun appString(@StringRes res: Int): String =
        getApplication<Application>().getString(res)

    override fun onCleared() {
        super.onCleared()
        renderLoop?.cancel()
        _state.value.composite?.takeIf { !it.isRecycled }?.recycle()
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 2_000L
        const val PREVIEW_PX = 1_600_000
        const val FAST_PREVIEW_PX = 600_000
        val BG_REMOVE_LABELS = setOf(
            R.string.bg_remove_auto,
            R.string.bg_remove_white,
            R.string.bg_remove_by_color,
            R.string.bg_brush_erase,
        )
    }
}
