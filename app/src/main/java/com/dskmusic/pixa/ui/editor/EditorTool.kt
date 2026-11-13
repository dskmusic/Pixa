package com.dskmusic.pixa.ui.editor

import androidx.compose.material.icons.filled.Psychology
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.draw.BrushKind
import com.dskmusic.pixa.core.draw.BrushSettings
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.ShapeSpec
import com.dskmusic.pixa.core.selection.Selection

/**
 * Paneles del editor (requisito 7 y siguientes).
 *
 * Cada entrada es una pestana de la barra inferior. La lista es el indice de todo lo que
 * el editor sabe hacer, asi que vive en un solo sitio para que la barra, el titulo del
 * panel y la ayuda contextual no se puedan desincronizar.
 */
enum class EditorPanel(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    @StringRes val helpRes: Int,
) {
    ADJUST(R.string.adjust_title, Icons.Filled.Tune, R.string.help_adjust),
    FILTERS(R.string.filters_title, Icons.Filled.PhotoFilter, R.string.help_filters),
    EFFECTS(R.string.fx_title, Icons.Filled.AutoAwesome, R.string.help_effects),
    BACKGROUND(R.string.bg_remove_title, Icons.Filled.BrokenImage, R.string.help_background),
    AI(R.string.ai_panel_title, Icons.Filled.Psychology, R.string.help_ai),
    CROP(R.string.crop_title, Icons.Filled.Crop, R.string.help_crop),
    TRANSFORM(R.string.transform_title, Icons.Filled.Transform, R.string.help_transform),
    SELECT(R.string.select_title, Icons.Filled.Square, R.string.help_select),
    CANVAS(R.string.canvas_size, Icons.Filled.Straighten, R.string.help_canvas),
    TONE(R.string.adjust_curves, Icons.Filled.Hub, R.string.help_tone),
    TEXT(R.string.text_title, Icons.Filled.TextFields, R.string.help_text),
    DRAW(R.string.draw_title, Icons.Filled.Brush, R.string.help_draw),
    SHAPES(R.string.tool_shape, Icons.Filled.Category, R.string.help_shapes),
    QR(R.string.qr_title, Icons.Filled.QrCode2, R.string.help_qr),
    BUBBLE(R.string.bubble_title, Icons.Filled.ChatBubble, R.string.help_bubble),
    GRADIENT(R.string.label_gradient, Icons.Filled.Gradient, R.string.help_gradient),
    RETOUCH(R.string.retouch_title, Icons.Filled.Healing, R.string.help_retouch),
    PRIVACY(R.string.privacy_title, Icons.Filled.VisibilityOff, R.string.help_privacy),
    FACES(R.string.faces_title, Icons.Filled.Face, R.string.help_faces),
    WATERMARK(R.string.watermark_title, Icons.Filled.WaterDrop, R.string.help_watermark),
    FRAMES(R.string.frames_title, Icons.Filled.FilterFrames, R.string.help_frames),
    MEASURE(R.string.canvas_measures, Icons.Filled.Straighten, R.string.help_measure),
    HISTORY(R.string.history_title, Icons.Filled.History, R.string.help_history),
    EXPORT(R.string.export_title, Icons.Filled.Save, R.string.help_export),
    IMAGE(R.string.image_info, Icons.Filled.Photo, R.string.help_image),
}

/**
 * Que hace un dedo sobre el lienzo.
 *
 * Se separa del panel abierto a proposito: varios paneles comparten herramienta (los
 * pinceles de retoque se comportan igual entre si) y algunos paneles no tocan el lienzo
 * en absoluto. Dos o mas dedos nunca activan la herramienta.
 */
enum class CanvasTool {
    NONE,
    MOVE,
    PAINT,
    EYEDROPPER,
    BUCKET,
    GRADIENT,
    SHAPE_DRAW,
    SELECT_RECT,
    SELECT_ELLIPSE,
    SELECT_LASSO,
    SELECT_MAGNETIC,
    SELECT_POLY,
    SELECT_WAND,
    SELECT_BY_COLOR,
    CROP,
    PERSPECTIVE,
    CLONE_SOURCE,
    CLONE,
    BLUR_BRUSH,
    SHARPEN_BRUSH,
    RED_EYE,
    PRIVACY_BRUSH,
    PRIVACY_RECT,
    BG_ERASE,
    BG_RESTORE,
    BG_PICK_COLOR,
    /** Un toque pide a la IA el objeto bajo el dedo (MobileSAM). */
    AI_SELECT,
    /** Borrador magico: se pinta sobre un objeto y la IA lo elimina al soltar el dedo. */
    AI_ERASE,
    /** Borrador magico manual: se marcan zonas (puntos, trazos, formas) y despues se aplican a la vez. */
    AI_MARK,
    TEXT_PATH,
    ;

    /** Herramientas que se arrastran como un pincel sobre los pixeles. */
    val isBrushLike: Boolean
        get() = this == PAINT ||
            this == CLONE || this == BLUR_BRUSH || this == SHARPEN_BRUSH ||
            this == PRIVACY_BRUSH || this == BG_ERASE || this == BG_RESTORE || this == AI_ERASE

    /** Herramientas de IA que marcan o tocan sobre el lienzo: admiten el modo "mover vista". */
    val isAiTool: Boolean
        get() = this == AI_SELECT || this == AI_ERASE || this == AI_MARK
}

/** Forma con la que se marca en el borrador magico manual. */
enum class MarkShape { BRUSH, RECT, ELLIPSE, LASSO }

/** Una marca del borrador magico: puntos del trazo o esquinas de la forma, y radio si es pincel. */
data class EraseMark(val shape: MarkShape, val points: List<Offset>, val radius: Float)

/** Tipo de retoque de privacidad aplicado a una zona (requisito 14). */
enum class PrivacyStyle { PIXELATE, BLUR }

/**
 * Estado de interfaz del editor que no pertenece al documento.
 *
 * Vive aqui y no en el ViewModel porque nada de esto se guarda en el proyecto ni entra en
 * el historial: son reglas, rejilla, herramienta activa y los puntos del trazo en curso.
 * Se recuerda con rememberSaveable solo en lo que merece sobrevivir a un giro de pantalla.
 */
@Stable
class EditorUi(
    initialPanel: EditorPanel?,
    initialGrid: Boolean,
    initialSnap: Boolean,
    initialMagnifier: Boolean,
) {
    var panel by mutableStateOf(initialPanel)
    /** Requisito del usuario: minimizar el panel sin cerrarlo ni soltar la herramienta. */
    var panelMinimized by mutableStateOf(false)
    var tool by mutableStateOf(CanvasTool.MOVE)

    var showGrid by mutableStateOf(initialGrid)
    var showRulers by mutableStateOf(false)
    var showGuides by mutableStateOf(false)
    var snap by mutableStateOf(initialSnap)
    var magnifier by mutableStateOf(initialMagnifier)

    /** Aumento de la lupa (viene de los ajustes). */
    var magnifierZoom by mutableStateOf(2.5f)
    var magnifierAvoid by mutableStateOf(true)
    var magnifierMargin by mutableStateOf(0.25f)
    var magnifierCorner by mutableStateOf(3)
    var magnifierShape by mutableStateOf(0)
    var magnifierSize by mutableStateOf(1f)
    var lensCrossSize by mutableStateOf(1f)
    /** true mientras se arrastra la punta de la cola de un bocadillo. */
    var tailDragging by mutableStateOf(false)
    /** Modo mano: un dedo solo desplaza el lienzo, sin mover objetos ni usar herramientas. */
    var handPan by mutableStateOf(false)
    /** El rebote que ensena que la barra inferior se desplaza ya se hizo en este editor. */
    var toolbarHintDone: Boolean = false
    var lensCrossWidth by mutableStateOf(1f)
    var selectionLineWidth by mutableStateOf(1f)

    /** Los botones flotantes de capas e insertar estan a la derecha (diestros) o a la izquierda (zurdos). */
    var fabOnRight: Boolean = true

    /** Esquina actual de la lupa (0 sup. izq., 1 sup. der., 2 inf. izq., 3 inf. der.). -1 = la esquina de reposo; se restablece al soltar el dedo. */
    var lensCorner: Int = -1

    /** Color activo, compartido por pincel, texto, formas y relleno. */
    var color by mutableStateOf(0xFF000000.toInt())

    var brush by mutableStateOf(BrushSettings())

    /** Forma que se dibujara al arrastrar con la herramienta de formas (requisito 11). */
    var shape by mutableStateOf(ShapeSpec())

    /** Degradado activo del bote/herramienta de degradado y de los rellenos. */
    var gradient by mutableStateOf(FillSpec.Gradient())

    var selectionMode by mutableStateOf(Selection.Mode.NEW)
    var wandTolerance by mutableStateOf(0.12f)
    var wandContiguous by mutableStateOf(true)

    /**
     * Intensidad compartida por los pinceles de retoque y de privacidad (requisito 14).
     *
     * Es un solo valor porque todos significan lo mismo para quien lo mueve: cuanto se
     * nota el efecto. El lienzo lo traduce al parametro que toque (cantidad de desenfoque,
     * de enfoque, fuerza de los ojos rojos o tamano de bloque al pixelar).
     */
    var retouchAmount by mutableStateOf(0.6f)

    var privacyStyle by mutableStateOf(PrivacyStyle.PIXELATE)

    /** Origen del tampon de clonar, en coordenadas de documento (requisito 14). */
    var cloneSource by mutableStateOf<Offset?>(null)

    // --- Estado vivo del gesto en curso: solo sirve para dibujar la previsualizacion ---

    var dragStart by mutableStateOf<Offset?>(null)
    var dragCurrent by mutableStateOf<Offset?>(null)

    /**
     * Proporcion ancho/alto a la que se ata el recuadro de seleccion y de recorte, o null
     * si es libre (requisito 12). La ponen los botones de proporcion del panel de recorte.
     */
    var boxRatio by mutableStateOf<Float?>(null)

    /** Puntos del lazo o del trayecto de texto que se esta trazando. */
    var livePoints by mutableStateOf<List<Offset>>(emptyList())

    /** Vertices acumulados del lazo poligonal, que se cierra con un toque doble. */
    var polyPoints by mutableStateOf<List<Offset>>(emptyList())

    /** Posicion del dedo para la lupa; null cuando no hay dedo en el lienzo. */
    var touchPoint by mutableStateOf<Offset?>(null)

    /** Guias inteligentes que el ajuste magnetico pide pintar en este instante. */
    var guides by mutableStateOf<List<SmartGuide>>(emptyList())

    /** Mantener pulsado el lienzo oculta los ajustes en curso: antes y despues. */
    var showBefore by mutableStateOf(false)

    /** Seleccion por toque con IA: true anade al objeto, false lo quita. */
    var aiPositive by mutableStateOf(true)

    /** La seccion "Quitar fondo" de IA esta desplegada (activa los pinceles de fondo). */
    var aiBackgroundOpen by mutableStateOf(false)

    /** Borrador magico: true = detecta el objeto dentro del trazo; false = borra exactamente lo pintado. */
    var aiEraseDetect by mutableStateOf(true)

    /** Con una herramienta de IA activa, un dedo mueve la vista en vez de marcar (para recorrer una foto ampliada). */
    var panMode by mutableStateOf(false)

    /** Borrador magico manual: forma con la que se marca y marcas acumuladas, en coordenadas de documento. */
    var markShape by mutableStateOf(MarkShape.BRUSH)
    var marks by mutableStateOf<List<EraseMark>>(emptyList())
    var markRedo by mutableStateOf<List<EraseMark>>(emptyList())

    /** Selector de grosor y formas del borrador magico abierto sobre el lienzo. */
    var markPopup by mutableStateOf(false)

    fun addMark(mark: EraseMark) {
        marks = marks + mark
        markRedo = emptyList()
    }

    fun undoMark() {
        val last = marks.lastOrNull() ?: return
        marks = marks.dropLast(1)
        markRedo = markRedo + last
    }

    fun redoMark() {
        val next = markRedo.lastOrNull() ?: return
        markRedo = markRedo.dropLast(1)
        marks = marks + next
    }

    fun clearMarks() {
        marks = emptyList()
        markRedo = emptyList()
    }

    val brushColorSynced: BrushSettings
        get() = if (brush.kind == BrushKind.ERASER) brush else brush.copy(color = color)

    /**
     * Guarda de salida del panel. Si hay cambios sin aplicar, la pantalla del editor la
     * usa para preguntar (aplicar, descartar o seguir) en vez de tirarlos en silencio:
     * devuelve true si ha tomado el control y llamara a `proceed` cuando toque.
     */
    var leaveGuard: ((proceed: () -> Unit) -> Boolean)? = null

    private fun guarded(proceed: () -> Unit): Boolean = leaveGuard?.invoke(proceed) ?: false

    fun openPanel(next: EditorPanel?) {
        if (next == null || panel == next) closePanel() else openPanelDirect(next)
    }

    /** Como [openPanel] pero siempre abre, sin alternar si ya estaba abierto: lo usan los
     * accesos directos (insertar rapido, doble toque sobre un elemento). */
    fun openPanelDirect(next: EditorPanel) {
        if (guarded { openPanelNow(next) }) return
        openPanelNow(next)
    }

    private fun openPanelNow(next: EditorPanel) {
        panel = next
        tool = defaultToolFor(next)
        resetGesture()
    }

    fun closePanel() {
        if (guarded { closePanelNow() }) return
        closePanelNow()
    }

    private fun closePanelNow() {
        panMode = false
        markPopup = false
        panel = null
        panelMinimized = false
        tool = CanvasTool.MOVE
        resetGesture()
    }

    fun resetGesture() {
        dragStart = null
        dragCurrent = null
        livePoints = emptyList()
        guides = emptyList()
        touchPoint = null
    }

    fun resetPolygon() {
        polyPoints = emptyList()
    }

    private fun defaultToolFor(panel: EditorPanel?): CanvasTool = when (panel) {
        null -> CanvasTool.MOVE
        EditorPanel.DRAW -> CanvasTool.PAINT
        // Sin herramienta preelegida: la elige el usuario en el panel.
        EditorPanel.SELECT -> CanvasTool.NONE
        EditorPanel.CROP -> CanvasTool.CROP
        EditorPanel.SHAPES -> CanvasTool.SHAPE_DRAW
        EditorPanel.RETOUCH -> CanvasTool.CLONE_SOURCE
        EditorPanel.PRIVACY -> CanvasTool.PRIVACY_BRUSH
        EditorPanel.BACKGROUND -> CanvasTool.BG_ERASE
        else -> CanvasTool.MOVE
    }
}

@Composable
fun rememberEditorUi(
    grid: Boolean,
    snap: Boolean,
    magnifier: Boolean,
): EditorUi {
    // El panel abierto es lo unico que conviene conservar al girar la pantalla: el trazo
    // en curso no sobrevive al gesto y las demas opciones vienen de los ajustes.
    var savedPanel by rememberSaveable { mutableStateOf<String?>(null) }
    val ui = remember {
        EditorUi(
            initialPanel = savedPanel?.let { name ->
                EditorPanel.entries.firstOrNull { it.name == name }
            },
            initialGrid = grid,
            initialSnap = snap,
            initialMagnifier = magnifier,
        )
    }
    savedPanel = ui.panel?.name
    return ui
}
