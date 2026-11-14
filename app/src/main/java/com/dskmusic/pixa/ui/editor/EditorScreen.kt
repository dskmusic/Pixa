package com.dskmusic.pixa.ui.editor

import com.dskmusic.pixa.ui.editor.panels.removeMarksAction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.FilterChip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import com.dskmusic.pixa.ui.editor.panels.runAi
import com.dskmusic.pixa.ui.editor.panels.removeStrokeDetectAction
import com.dskmusic.pixa.ui.editor.panels.removeStrokeAction
import android.content.Context
import androidx.compose.runtime.DisposableEffect
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentAdapter.LayoutResultCallback
import android.print.PrintDocumentAdapter.WriteResultCallback
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.graphics.Path as AndroidPath
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.draw.BrushEngine
import com.dskmusic.pixa.core.draw.BrushKind
import com.dskmusic.pixa.core.draw.BrushStroke
import com.dskmusic.pixa.core.export.ExportOptions
import com.dskmusic.pixa.core.image.Cutout
import com.dskmusic.pixa.core.image.Retouch
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.TextSpec
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.render.FillPainter
import com.dskmusic.pixa.core.render.LayerRasterizer
import com.dskmusic.pixa.core.render.PathShapes
import com.dskmusic.pixa.core.selection.Selection
import com.dskmusic.pixa.core.selection.MagneticLasso
import com.dskmusic.pixa.core.selection.SelectionTools
import com.dskmusic.pixa.data.settings.Handedness
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ui.common.ConfirmDialog
import com.dskmusic.pixa.ui.common.EditorMessages
import com.dskmusic.pixa.ui.common.ExitWithChangesDialog
import com.dskmusic.pixa.ui.common.BusyOverlay
import com.dskmusic.pixa.ui.common.PixaToastHost
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.common.ProjectVsImageDialog
import com.dskmusic.pixa.ui.common.TextPromptDialog
import com.dskmusic.pixa.ui.common.ToolButton
import com.dskmusic.pixa.ui.editor.panels.EmojiInsertDialog
import com.dskmusic.pixa.ui.editor.panels.PanelHint
import com.dskmusic.pixa.ui.editor.panels.PanelHost
import com.dskmusic.pixa.ui.editor.panels.StockImageDialog
import com.dskmusic.pixa.ui.editor.panels.StickerDialog
import com.dskmusic.pixa.ui.editor.panels.gradientLayer
import com.dskmusic.pixa.ui.editor.panels.bubbleHandleQuad
import com.dskmusic.pixa.ui.editor.panels.bubbleTipDoc
import com.dskmusic.pixa.ui.editor.panels.bubbleWithTail
import com.dskmusic.pixa.ui.editor.panels.insertBubbles
import com.dskmusic.pixa.ui.editor.panels.align
import com.dskmusic.pixa.ui.editor.panels.rasterOrWarn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Editor completo (requisitos 1, 3, 6, 7 y 16).
 *
 * La pantalla no hace nada de imagen por si misma: compone el lienzo, la barra de
 * herramientas, el panel abierto y las capas, y reparte los gestos a [ToolHandler].
 * Toda la edicion pasa por [EditorViewModel], que es el unico que toca el documento y
 * el historial.
 */
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    settings: PixaSettings,
    onExit: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rasterizer = remember(context) { context.pixa.rasterizer }
    val settingsRepo = remember(context) { context.pixa.settings }

    // Las fuentes descargables no estan al elegirlas: el texto se dibuja de momento con
    // la de reserva. Cuando una llega hay que tirar ese bitmap y repintar, o la capa se
    // queda para siempre con la letra equivocada.
    val fontsLoaded by context.pixa.fonts.loaded.collectAsStateWithLifecycle()
    LaunchedEffect(fontsLoaded) {
        if (fontsLoaded > 0) {
            rasterizer.clearCache()
            vm.rerender()
        }
    }

    val ui = rememberEditorUi(
        grid = settings.gridByDefault,
        snap = settings.snapping,
        magnifier = settings.magnifier,
    )
    val controller = remember { CanvasController() }
    LaunchedEffect(settings.curtainByDefault) { vm.curtainDefault = settings.curtainByDefault }
    LaunchedEffect(settings.magnifierZoom) { ui.magnifierZoom = settings.magnifierZoom }
    LaunchedEffect(settings.lensCrossSize, settings.lensCrossWidth, settings.selectionLineWidth) {
        ui.lensCrossSize = settings.lensCrossSize
        ui.lensCrossWidth = settings.lensCrossWidth
        ui.selectionLineWidth = settings.selectionLineWidth
    }
    LaunchedEffect(settings.magnifierAvoid, settings.magnifierMargin) {
        ui.magnifierAvoid = settings.magnifierAvoid
        ui.magnifierMargin = settings.magnifierMargin
    }
    LaunchedEffect(settings.magnifierCorner, settings.magnifierShape, settings.magnifierSize) {
        ui.magnifierCorner = settings.magnifierCorner
        ui.magnifierShape = settings.magnifierShape
        ui.magnifierSize = settings.magnifierSize
    }
    LaunchedEffect(settings.handedness) { ui.fabOnRight = settings.handedness != Handedness.LEFT }

    // Aviso de cambios sin aplicar al salir de un panel (ver EditorUi.leaveGuard).
    var leaveProceed by remember { mutableStateOf<(() -> Unit)?>(null) }
    DisposableEffect(ui, vm, settings.confirmDiscard) {
        ui.leaveGuard = { proceed ->
            if (settings.confirmDiscard && vm.previewActive && vm.pendingApply != null) {
                leaveProceed = proceed
                true
            } else {
                false
            }
        }
        onDispose { ui.leaveGuard = null }
    }
    val snackbar = remember { SnackbarHostState() }

    // Bug encontrado (requisito del usuario): abrir una imagen nueva heredaba la
    // herramienta de la sesion anterior (p.ej. Recortar en vez de Mover), y con otra
    // herramienta activa un dedo ya no movia la vista. ui vive en el composable, no en el
    // ViewModel, asi que un documento nuevo no la reseteaba sola.
    LaunchedEffect(state.documentGeneration) {
        // Requisito del usuario: los accesos directos de Home (quitar fondo) dejan dicho
        // que panel abrir nada mas cargar; si no hay ninguno, el reseteo de siempre.
        val pending = state.pendingPanel
        if (pending != null) {
            ui.panel = pending
            // Recortar necesita su herramienta (el recuadro) activa desde el primer momento.
            if (pending == EditorPanel.CROP) ui.tool = CanvasTool.CROP
            vm.consumePendingPanel()
        } else {
            ui.closePanel()
        }
    }

    // Requisito del usuario: minimizar es por panel, no permanente; abrir o cambiar de
    // panel vuelve a empezar maximizado.
    LaunchedEffect(ui.panel) { ui.panelMinimized = false }

    var naming by remember { mutableStateOf(false) }
    var exitAfterSave by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var layersExpanded by remember { mutableStateOf(false) }
    var insertExpanded by remember { mutableStateOf(false) }
    var layersPanel by remember { mutableStateOf(false) }
    // Vista (zoom y desplazamiento) que habia al abrir el gestor de capas, para devolverla al cerrarlo.
    var viewBeforeLayers by remember { mutableStateOf<Pair<Float, Offset>?>(null) }
    var sizeBeforePanel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var layersMinimized by remember { mutableStateOf(false) }
    LaunchedEffect(layersPanel) { if (layersPanel) layersMinimized = false }
    var deleteAsk by remember { mutableStateOf<PixaLayer?>(null) }

    // Vista al abrir paneles y el gestor completo de capas (Ajustes > "Vista al abrir
    // paneles"): 0 encaja todo el lienzo, 1 no toca el zoom y 2 (por defecto) encaja el
    // objeto elegido en la zona visible y se actualiza al cambiar de capa; sin nada elegido
    // se respeta el zoom actual. Al cerrar se devuelve la vista que habia antes de abrir.
    LaunchedEffect(
        ui.panel, ui.panelMinimized, layersPanel, layersMinimized, settings.panelViewMode,
        state.doc.selectedLayerId,
    ) {
        val mode = settings.panelViewMode
        val open = ui.panel != null || layersPanel
        if (mode == 1) return@LaunchedEffect
        if (open && viewBeforeLayers == null) {
            viewBeforeLayers = controller.zoom to controller.pan
            sizeBeforePanel = state.doc.width to state.doc.height
        }
        // El delay deja que Compose recalcule el alto del lienzo antes de reencajar.
        delay(120)
        val layer = state.doc.selected
        if (!open) {
            val before = viewBeforeLayers
            // Si el lienzo cambio de tamano (recorte), la vista guardada ya no vale: encajar.
            if (mode == 0 || sizeBeforePanel != (state.doc.width to state.doc.height)) {
                controller.fit()
            } else if (before != null) {
                controller.restoreView(before.first, before.second)
            }
            viewBeforeLayers = null
            return@LaunchedEffect
        }
        if (mode == 0) {
            controller.fit()
        } else if (layer != null) {
            val box = runCatching { LayerGeometry.bounds(layer, rasterizer, state.doc) }.getOrNull()
            if (box != null) controller.fitRect(box.left, box.top, box.right, box.bottom)
        }
    }

    var explainThen by remember { mutableStateOf<(() -> Unit)?>(null) }
    var liveQuad by remember { mutableStateOf<FloatArray?>(null) }
    var alignOpen by remember { mutableStateOf(false) }
    var emojiPickerOpen by remember { mutableStateOf(false) }
    var stockDialogOpen by remember { mutableStateOf(false) }
    var stickerDialogOpen by remember { mutableStateOf(false) }
    // Requisito del usuario: elegir el modo de relleno antes de imprimir.
    var printDialogOpen by remember { mutableStateOf(false) }
    // Requisito del usuario: los dos menus rapidos (capas y "+") se cierran entre si, y
    // ambos se cierran solos si se abre un panel inferior.
    LaunchedEffect(ui.panel) {
        if (ui.panel != null) {
            layersExpanded = false
            insertExpanded = false
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val handlePx = with(density) { 22.dp.toPx() }
    val snapPx = with(density) { 12.dp.toPx() }

    // El lienzo se reencaja cuando cambia el tamano del documento, no en cada edicion.
    LaunchedEffect(state.doc.width, state.doc.height) {
        controller.setDocumentSize(state.doc.width, state.doc.height, refit = true)
    }

    // Cambiar de herramienta deja el poligono a medias sin terminar: se descarta.
    LaunchedEffect(ui.tool) {
        if (ui.tool != CanvasTool.SELECT_POLY) ui.resetPolygon()
    }

    val handler = remember(vm, ui, controller, rasterizer) {
        ToolHandler(vm, ui, controller, rasterizer, scope)
    }
    handler.state = state
    handler.snapEnabled = ui.snap
    handler.snapTolerancePx = snapPx
    handler.handleRadiusPx = handlePx
    handler.str = { res -> context.getString(res) }
    handler.appContext = context.applicationContext
    handler.onHaptic = {
        if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    handler.onQuad = { quad -> liveQuad = quad }
    handler.onColorPicked = { argb ->
        ui.color = argb
        scope.launch { settingsRepo.pushRecentColor(argb) }
    }

    // Requisito 12: el rectangulo, la elipse y el recorte aparecen con el recuadro ya
    // puesto. Con el dedo se mueve y se cambia de tamano; dibujar es cosa del lazo.
    LaunchedEffect(ui.tool, state.doc.width, state.doc.height) { handler.ensureBox() }

    // Snapshot del fondo original: se toma al abrir el panel "Quitar fondo" para que el
    // pincel de restaurar tenga de donde recuperar pixeles (requisito 8).
    LaunchedEffect(ui.panel, state.doc.selectedLayerId, ui.aiBackgroundOpen) {
        if (ui.panel == EditorPanel.BACKGROUND || ui.aiBackgroundOpen) handler.snapshotOriginal()
    }

    // Mensajes del ViewModel: un aviso corto y, si se ha escrito un archivo, un "Ver".
    EditorMessages(vm, snackbar)

    // Requisito 3: la primera vez se explica la diferencia entre guardar y exportar, y
    // solo despues se hace lo que se habia pedido.
    val guarded: (() -> Unit) -> Unit = { action ->
        if (settings.hintProjectVsImageShown) action() else explainThen = action
    }
    val requestSave: () -> Unit = {
        guarded {
            // Requisito del usuario: al guardar siempre se muestra el dialogo de nombre, ya
            // pre-rellenado con el actual (o el del archivo), editable antes de confirmar.
            naming = true
        }
    }
    val leave: () -> Unit = {
        vm.clearAutosaveAfterExit()
        onExit()
    }

    BackHandler {
        when {
            layersPanel -> layersPanel = false
            ui.panel != null -> ui.openPanel(null)
            state.dirty -> exiting = true
            else -> leave()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(
                title = state.projectName.ifBlank { stringResource(R.string.app_name_short) },
                onBack = { if (state.dirty) exiting = true else leave() },
                actions = {
                    IconButton(onClick = { vm.undo() }, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.cd_undo))
                    }
                    IconButton(onClick = { vm.redo() }, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.cd_redo))
                    }
                    IconButton(onClick = requestSave) {
                        Icon(Icons.Filled.Save, stringResource(R.string.save_project))
                    }
                    IconButton(onClick = { guarded { ui.openPanel(EditorPanel.EXPORT) } }) {
                        // El mismo icono que distingue "exportar imagen" de "guardar
                        // proyecto" en el aviso de requisito 3, para que sean reconocibles
                        // como la misma accion en los dos sitios.
                        Icon(Icons.Filled.SaveAlt, stringResource(R.string.export_title))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                stringResource(R.string.cd_more_options),
                            )
                        }
                        EditorMenu(
                            vm = vm,
                            expanded = menuOpen,
                            ui = ui,
                            onDismiss = { menuOpen = false },
                            onRasterize = {
                                // Pide confirmacion (dialogo comun) antes de rasterizar la capa elegida.
                                val layer = vm.selectedLayer
                                if (layer == null || layer.isRaster) {
                                    vm.post(R.string.rasterize_none)
                                } else {
                                    vm.askRasterize(layer)
                                }
                            },
                            onFlatten = { vm.flatten() },
                            onSaveVersion = { guarded { vm.saveAsVersion() } },
                            onShareProject = {
                                vm.shareProjectFile { file ->
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            intent,
                                            context.getString(R.string.project_share_file),
                                        ),
                                    )
                                }
                            },
                            onShareImage = {
                                vm.shareImage(ExportOptions()) { uri, mime ->
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = mime
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(intent, context.getString(R.string.share_image)),
                                    )
                                }
                            },
                            onPrint = {
                                printDialogOpen = true
                            },
                            onAlign = { alignOpen = true },
                            onOpenImageInfo = { ui.openPanelDirect(EditorPanel.IMAGE) },
                        )
                    }
                },
            )
        },
        snackbarHost = { PixaToastHost(snackbar) },
    ) { padding ->
        val wide = LocalConfiguration.current.screenWidthDp >= 600
        val leftHanded = settings.handedness == Handedness.LEFT

        val selected = state.doc.selected
        val showHandles = ui.tool == CanvasTool.MOVE || ui.tool == CanvasTool.PERSPECTIVE
        // Requisito 9: con la herramienta de mover y una capa elegida, los dos dedos la
        // amplian y la giran a ella y el lienzo se queda quieto. Tocar fuera de la capa
        // deselecciona, y entonces los dos dedos vuelven a mover y ampliar la vista.
        val objectGesture = !ui.handPan && ui.tool == CanvasTool.MOVE && selected != null &&
            !selected.locked && !selected.transformLocked
        // Los tiradores se dibujan al mover o en perspectiva. En perspectiva se permiten
        // tambien sobre la capa base de una imagen abierta (transformLocked): es un gesto
        // deliberado, no un movimiento accidental (requisito del usuario).
        val staticQuad = remember(state.doc, selected?.id, ui.tool, controller.zoom) {
            val canTransform = showHandles && selected != null && !selected.locked &&
                (ui.tool == CanvasTool.PERSPECTIVE || !selected.transformLocked)
            if (canTransform) {
                runCatching { LayerGeometry.quad(selected, rasterizer, state.doc) }.getOrNull()
                    ?.let { q ->
                        if (ui.tool == CanvasTool.MOVE && selected.content is LayerContent.SpeechBubble) {
                            bubbleHandleQuad(selected, rasterizer, q, controller.viewLengthToDoc(HANDLE_GAP_PX))
                        } else {
                            q
                        }
                    }
            } else {
                null
            }
        }

        val canvas: @Composable (Modifier) -> Unit = { mod ->
            Box(mod) {
                CanvasHost(
                    controller = controller,
                    doc = state.doc,
                    composite = state.composite,
                    selection = state.selection,
                    ui = ui,
                    modifier = Modifier.fillMaxSize(),
                    handleQuad = liveQuad ?: staticQuad,
                    onTap = { point ->
                        // Tocar el lienzo cierra el panel abierto, pero solo cuando el
                        // toque no es ya el gesto de otra herramienta (pincel, cuentagotas,
                        // lazo...): esas necesitan el toque para si mismas.
                        if (ui.panel != null && ui.panel != EditorPanel.SELECT &&
                            (ui.tool == CanvasTool.MOVE || ui.tool == CanvasTool.NONE)
                        ) {
                            ui.closePanel()
                        }
                        // Requisito del usuario: los menus rapidos de capas e insertar se
                        // quedaban abiertos al tocar el lienzo; ahora se cierran igual que
                        // el panel.
                        layersExpanded = false
                        insertExpanded = false
                        handler.onTap(point)
                    },
                    onDoubleTap = handler::onDoubleTap,
                    onLongPress = { vm.setPeek(true) },
                    onLongPressRelease = { vm.setPeek(false) },
                    onLongHold = { point ->
                        // 1 s sobre un elemento: pregunta si quieres eliminarlo.
                        LayerGeometry.pick(state.doc, rasterizer, point, includeFills = true)?.let { layer ->
                            vm.setPeek(false)
                            vm.selectLayer(layer.id)
                            deleteAsk = layer
                        }
                    },
                    onStrokeStart = handler::onStrokeStart,
                    onStrokeMove = handler::onStrokeMove,
                    onStrokeEnd = handler::onStrokeEnd,
                    onStrokeCancel = handler::onStrokeCancel,
                    onUndoGesture = { if (state.canUndo) vm.undo() },
                    onRedoGesture = { if (state.canRedo) vm.redo() },
                    objectGesture = objectGesture,
                    onObjectGesture = handler::onObjectGesture,
                    onObjectGestureEnd = handler::onObjectGestureEnd,
                    overlay = { ctrl ->
                        // Asa de la cola del bocadillo elegido (herramienta Mover).
                        val tail = if (ui.tool == CanvasTool.MOVE) {
                            selected?.takeIf { it.content is LayerContent.SpeechBubble }
                                ?.let { runCatching { bubbleTipDoc(it, rasterizer) }.getOrNull() }
                        } else {
                            null
                        }
                        drawToolOverlay(ctrl, ui, state.doc, state.composite, accent, tail)
                    },
                )
                // Cortina antes/despues: franja para arrastrarla desde la propia linea.
                if (vm.curtainOn && vm.previewActive) {
                    CurtainHandle(
                        controller = controller,
                        docWidth = state.doc.width,
                        pos = vm.curtainPos,
                        onPos = { vm.moveCurtain(it) },
                    )
                }
                // Deshacer, rehacer, borrar marcas y grosor, siempre a mano mientras se marca.
                if (ui.tool == CanvasTool.AI_MARK) {
                    // Al tocar fuera del selector de grosor se cierra solo.
                    if (ui.markPopup) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) { detectTapGestures { ui.markPopup = false } },
                        )
                    }
                    MarkToolbar(
                        ui = ui,
                        controller = controller,
                        onApply = { removeMarksAction(vm, ui)?.let { vm.runAi(context, it) } },
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    )
                }
                // Atajo sobre el lienzo para alternar entre marcar y mover la vista.
                if (ui.tool.isAiTool && ui.tool != CanvasTool.AI_MARK) {
                    FilterChip(
                        selected = ui.panMode,
                        onClick = { ui.panMode = !ui.panMode },
                        label = { Text(stringResource(R.string.ai_pan_mode)) },
                        leadingIcon = {
                            Icon(Icons.Filled.PanTool, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
                // Con el gestor completo de capas abierto (aunque este minimizado) no se muestran
                // ni las capas rapidas ni el boton de anadir elementos.
                // Con un panel desplegado tampoco: vuelven al minimizarlo o cerrarlo.
                if (!wide && !layersPanel && (ui.panel == null || ui.panelMinimized)) {
                    LayersFab(
                        doc = state.doc,
                        vm = vm,
                        expanded = layersExpanded,
                        alignEnd = !leftHanded,
                        onToggleExpanded = {
                            layersExpanded = !layersExpanded
                            if (layersExpanded) {
                                insertExpanded = false
                                ui.closePanel()
                            }
                        },
                        onSelect = { vm.selectLayer(it) },
                        onToggleVisible = { vm.setLayerVisible(it.id, !it.visible) },
                        onOpenPanel = {
                            layersExpanded = false
                            layersPanel = true
                        },
                        onGradientAdded = {
                            layersExpanded = false
                            ui.openPanelDirect(EditorPanel.GRADIENT)
                        },
                        modifier = Modifier
                            .align(if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                            .padding(16.dp),
                    )
                    QuickInsertFab(
                        ui = ui,
                        expanded = insertExpanded,
                        onToggleExpanded = {
                            insertExpanded = !insertExpanded
                            if (insertExpanded) {
                                layersExpanded = false
                                ui.closePanel()
                            }
                        },
                        onInsertEmoji = {
                            insertExpanded = false
                            emojiPickerOpen = true
                        },
                        onInsertStock = {
                            insertExpanded = false
                            stockDialogOpen = true
                        },
                        onInsertGradient = {
                            insertExpanded = false
                            vm.addLayer(
                                R.string.layer_add,
                                gradientLayer(context.getString(R.string.label_gradient)),
                            )
                            ui.openPanelDirect(EditorPanel.GRADIENT)
                        },
                        onInsertSticker = {
                            insertExpanded = false
                            stickerDialogOpen = true
                        },
                        onInsertBubbles = {
                            insertExpanded = false
                            vm.busy(R.string.label_progress)
                            scope.launch {
                                try {
                                    val app = context.pixa
                                    val found = insertBubbles(
                                        vm, app.rasterizer, app.fonts,
                                        context.getString(R.string.comic_default_text),
                                        context.getString(R.string.bubble_title),
                                    )
                                    if (found == 0) vm.post(R.string.bubble_no_faces)
                                    ui.openPanelDirect(EditorPanel.BUBBLE)
                                } finally {
                                    vm.idle()
                                }
                            }
                        },
                        alignEnd = !leftHanded,
                        modifier = Modifier
                            .align(if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                            .padding(
                                start = if (leftHanded) 84.dp else 16.dp,
                                end = if (leftHanded) 16.dp else 84.dp,
                                bottom = 16.dp,
                            ),
                    )
                    // PRUEBA: acceso a IA fuera de la barra inferior, a la izquierda del "+".
                    // Para revertir: quitar este boton y el filtro de AI en BOTTOM_BAR_PANELS.
                    FloatingActionButton(
                        onClick = {
                            insertExpanded = false
                            layersExpanded = false
                            ui.openPanel(EditorPanel.AI)
                        },
                        modifier = Modifier
                            .align(if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                            .padding(
                                start = if (leftHanded) 136.dp else 16.dp,
                                end = if (leftHanded) 16.dp else 136.dp,
                                bottom = 16.dp,
                            )
                            .size(40.dp),
                    ) {
                        Icon(Icons.Filled.Psychology, contentDescription = stringResource(R.string.ai_panel_title))
                    }
                }
            }
        }

        val panelSurface: @Composable (Modifier) -> Unit = { mod ->
            val panel = ui.panel
            if (panel != null) {
                Surface(
                    modifier = mod,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    PanelHost(
                        panel = panel,
                        vm = vm,
                        state = state,
                        ui = ui,
                        settings = settings,
                        modifier = if (wide) Modifier.fillMaxHeight() else Modifier.heightIn(max = 300.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (wide) {
                // Apaisado: barra de herramientas al lado de la mano y capas siempre a la
                // vista, que es lo que pide el requisito 1 cuando sobra ancho.
                Row(modifier = Modifier.fillMaxSize()) {
                    // `weight` solo existe dentro de la fila, asi que el orden segun la
                    // mano se escribe aqui mismo en vez de en una lista de bloques.
                    if (leftHanded) {
                        ToolColumn(ui, Modifier.width(84.dp))
                        panelSurface(Modifier.width(300.dp).fillMaxHeight())
                        canvas(Modifier.weight(1f).fillMaxHeight())
                        LayersSide(state, vm, Modifier.width(230.dp)) { ui.openPanelDirect(EditorPanel.GRADIENT) }
                    } else {
                        LayersSide(state, vm, Modifier.width(230.dp)) { ui.openPanelDirect(EditorPanel.GRADIENT) }
                        canvas(Modifier.weight(1f).fillMaxHeight())
                        panelSurface(Modifier.width(300.dp).fillMaxHeight())
                        ToolColumn(ui, Modifier.width(84.dp))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    canvas(Modifier.weight(1f).fillMaxWidth())
                    if (layersPanel) {
                        // El gestor completo de capas ocupa el sitio del panel y la barra; el
                        // lienzo se reduce al espacio de arriba para ver los cambios.
                        LayersSheet(
                            doc = state.doc,
                            vm = vm,
                            onDismiss = { layersPanel = false },
                            modifier = Modifier.fillMaxWidth(),
                            minimized = layersMinimized,
                            onMinimizedChange = { layersMinimized = it },
                            onGradientAdded = {
                                layersPanel = false
                                ui.openPanelDirect(EditorPanel.GRADIENT)
                            },
                        )
                    } else {
                        panelSurface(Modifier.fillMaxWidth())
                        ToolBar(ui, Modifier.fillMaxWidth())
                    }
                }
            }

            BusyOverlay(
                visible = state.busyText != null,
                text = state.busyText?.let { stringResource(it) },
                progress = state.busyProgress,
                onCancel = if (state.busyCancellable) ({ vm.cancelBusy() }) else null,
                detail = state.busyDetail,
            )
        }
    }

    leaveProceed?.let { proceed ->
        DiscardChangesDialog(
            onApply = {
                leaveProceed = null
                vm.pendingApply?.invoke()
                proceed()
            },
            onDiscard = {
                leaveProceed = null
                proceed()
            },
            onKeepEditing = { leaveProceed = null },
        )
    }

    deleteAsk?.let { layer ->
        ConfirmDialog(
            title = stringResource(R.string.action_confirm_delete),
            body = layer.name,
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { vm.deleteLayer(layer.id) },
            onDismiss = { deleteAsk = null },
        )
    }

    // Requisito 14: antes de tocar los pixeles de una capa de texto, forma, QR o relleno
    // hay que rasterizarla, y eso el usuario tiene que decidirlo.
    vm.rasterizeAsk?.let { layer ->
        ConfirmDialog(
            title = stringResource(R.string.raster_ask_title),
            body = stringResource(R.string.raster_ask_body, layer.name),
            confirmText = stringResource(R.string.action_rasterize),
            onConfirm = { vm.confirmRasterize() },
            onDismiss = { vm.dismissRasterize() },
        )
    }

    if (alignOpen) {
        AlignDialog(
            vm = vm,
            rasterizer = rasterizer,
            doc = state.doc,
            layer = state.doc.selected,
            onDismiss = { alignOpen = false },
        )
    }

    if (printDialogOpen) {
        PrintModeDialog(
            onProportional = {
                printDialogOpen = false
                vm.printImage { file -> printImage(context, file, keepAspect = true) }
            },
            onFill = {
                printDialogOpen = false
                vm.printImage { file -> printImage(context, file, keepAspect = false) }
            },
            onDismiss = { printDialogOpen = false },
        )
    }

    if (emojiPickerOpen) {
        EmojiInsertDialog(
            vm = vm,
            doc = state.doc,
            onDismiss = { emojiPickerOpen = false },
        )
    }

    if (stickerDialogOpen) {
        StickerDialog(
            vm = vm,
            doc = state.doc,
            settings = settings,
            onDismiss = { stickerDialogOpen = false },
        )
    }

    if (stockDialogOpen) {
        StockImageDialog(
            vm = vm,
            doc = state.doc,
            settings = settings,
            onDismiss = { stockDialogOpen = false },
        )
    }

    explainThen?.let { pending ->
        ProjectVsImageDialog(
            onDismiss = { dontShowAgain ->
                explainThen = null
                if (dontShowAgain) {
                    scope.launch { settingsRepo.setHintProjectVsImageShown(true) }
                }
                pending()
            },
        )
    }

    if (naming) {
        TextPromptDialog(
            title = stringResource(R.string.save_project),
            initial = state.projectName.ifBlank { stringResource(R.string.app_name_short) },
            onConfirm = { name ->
                vm.saveProject(name) { if (exitAfterSave) onExit() }
            },
            onDismiss = { naming = false },
        )
    }

    if (exiting) {
        ExitWithChangesDialog(
            onSaveProject = {
                exiting = false
                exitAfterSave = true
                requestSave()
            },
            onExportImage = {
                exiting = false
                guarded { ui.openPanel(EditorPanel.EXPORT) }
            },
            onDiscard = {
                exiting = false
                leave()
            },
            onDismiss = { exiting = false },
        )
    }
}

// ======================================================================================
// Barras de herramientas y menu
// ======================================================================================

/**
 * Paneles de la barra inferior.
 *
 * Texto, forma y QR se insertan ahora desde el "+" junto al gestor rapido de capas;
 * medidas e historial se han quitado (la alineacion vive en el menu de tres puntos y
 * deshacer/rehacer ya estan en la barra superior); exportar e informacion tienen su
 * propio acceso (el icono de descarga y el menu de tres puntos). Repetirlos aqui solo
 * alargaria la barra sin anadir nada que no se pueda hacer ya.
 */
private val BOTTOM_BAR_PANELS = EditorPanel.entries.filterNot {
    it == EditorPanel.TEXT || it == EditorPanel.SHAPES || it == EditorPanel.QR ||
        it == EditorPanel.BUBBLE || it == EditorPanel.GRADIENT ||
        it == EditorPanel.DRAW || it == EditorPanel.FRAMES ||
        it == EditorPanel.MEASURE || it == EditorPanel.HISTORY ||
        it == EditorPanel.EXPORT || it == EditorPanel.IMAGE ||
        it == EditorPanel.AI // PRUEBA: la IA vive en su propio boton junto al "+"
}

/**
 * Requisito del usuario: en los botones inferiores "Eliminar fondo" se queda corto a
 * "Fondo" (el titulo largo se queda para la cabecera del panel y el acceso de Home).
 */
private fun toolBarLabel(panel: EditorPanel): Int =
    if (panel == EditorPanel.BACKGROUND) R.string.bg_remove_short else panel.titleRes

/** Barra inferior con los paneles. En vertical se desplaza en horizontal. */
@Composable
private fun ToolBar(ui: EditorUi, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    // Al entrar al editor la barra rebota un poco hacia la izquierda y vuelve, para que se
    // vea que hay mas opciones deslizando. Una sola vez por editor abierto.
    LaunchedEffect(Unit) {
        if (ui.toolbarHintDone) return@LaunchedEffect
        delay(700)
        if (scroll.maxValue > 0) {
            ui.toolbarHintDone = true
            scroll.animateScrollTo(minOf(scroll.maxValue, 220), tween(380))
            scroll.animateScrollTo(0, tween(420))
        }
    }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BOTTOM_BAR_PANELS.forEach { panel ->
                ToolButton(
                    icon = panel.icon,
                    label = stringResource(toolBarLabel(panel)),
                    selected = ui.panel == panel,
                    onClick = { ui.openPanel(panel) },
                )
            }
        }
    }
}

/** La misma barra en vertical para pantallas anchas. */
@Composable
private fun ToolColumn(ui: EditorUi, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BOTTOM_BAR_PANELS.forEach { panel ->
                ToolButton(
                    icon = panel.icon,
                    label = stringResource(toolBarLabel(panel)),
                    selected = ui.panel == panel,
                    onClick = { ui.openPanel(panel) },
                )
            }
        }
    }
}

/** Panel de capas fijo del modo apaisado (requisito 1). */
@Composable
private fun LayersSide(
    state: EditorState,
    vm: EditorViewModel,
    modifier: Modifier = Modifier,
    onGradientAdded: () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                stringResource(R.string.layers_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            LayersList(
                doc = state.doc,
                vm = vm,
                modifier = Modifier.fillMaxWidth(),
                onGradientAdded = onGradientAdded,
            )
        }
    }
}

@Composable
private fun EditorMenu(
    expanded: Boolean,
    ui: EditorUi,
    vm: EditorViewModel,
    onDismiss: () -> Unit,
    onRasterize: () -> Unit,
    onFlatten: () -> Unit,
    onSaveVersion: () -> Unit,
    onShareProject: () -> Unit,
    onShareImage: () -> Unit,
    onPrint: () -> Unit,
    onAlign: () -> Unit,
    onOpenImageInfo: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        CheckableItem(R.string.canvas_grid, ui.showGrid) { ui.showGrid = !ui.showGrid }
        CheckableItem(R.string.canvas_rulers, ui.showRulers) { ui.showRulers = !ui.showRulers }
        CheckableItem(R.string.canvas_magnifier, ui.magnifier) { ui.magnifier = !ui.magnifier }
        CheckableItem(R.string.canvas_guides, ui.showGuides) { ui.showGuides = !ui.showGuides }
        CheckableItem(R.string.settings_snap, ui.snap) { ui.snap = !ui.snap }
        // Cortina antes/despues: solo aparece mientras hay algo en previsualizacion.
        if (vm.previewActive) {
            CheckableItem(R.string.compare_curtain, vm.curtainOn) { vm.toggleCurtain(!vm.curtainOn) }
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.layer_rasterize)) },
            onClick = {
                onRasterize()
                onDismiss()
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.layer_flatten)) },
            onClick = {
                onFlatten()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.save_as_version)) },
            onClick = {
                onSaveVersion()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.project_share_file)) },
            onClick = {
                onShareProject()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share_image)) },
            onClick = {
                onShareImage()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_print)) },
            onClick = {
                onPrint()
                onDismiss()
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.canvas_align)) },
            onClick = {
                onAlign()
                onDismiss()
            },
        )
        // Ultima opcion a proposito: es consulta, no edicion, y por eso va al final.
        DropdownMenuItem(
            text = { Text(stringResource(R.string.image_info)) },
            onClick = {
                onOpenImageInfo()
                onDismiss()
            },
        )
    }
}

/** Dialogo compacto con las seis alineaciones: izquierda/centro/derecha, arriba/medio/abajo. */
@Composable
private fun AlignDialog(
    vm: EditorViewModel,
    rasterizer: LayerRasterizer,
    doc: PixaDocument,
    layer: PixaLayer?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.canvas_align)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { align(vm, rasterizer, doc, layer, true, 0f) }) {
                        Icon(Icons.Filled.FormatAlignLeft, stringResource(R.string.align_left))
                    }
                    IconButton(onClick = { align(vm, rasterizer, doc, layer, true, 0.5f) }) {
                        Icon(Icons.Filled.FormatAlignCenter, stringResource(R.string.align_center_h))
                    }
                    IconButton(onClick = { align(vm, rasterizer, doc, layer, true, 1f) }) {
                        Icon(Icons.Filled.FormatAlignRight, stringResource(R.string.align_right))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { align(vm, rasterizer, doc, layer, false, 0f) }) {
                        Icon(Icons.Filled.VerticalAlignTop, stringResource(R.string.align_top))
                    }
                    IconButton(onClick = { align(vm, rasterizer, doc, layer, false, 0.5f) }) {
                        Icon(Icons.Filled.VerticalAlignCenter, stringResource(R.string.align_center_v))
                    }
                    IconButton(onClick = { align(vm, rasterizer, doc, layer, false, 1f) }) {
                        Icon(Icons.Filled.VerticalAlignBottom, stringResource(R.string.align_bottom))
                    }
                }
                if (layer == null) PanelHint(R.string.layer_pick_any)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** Entrada de menu que se queda abierta: son interruptores de vista, no acciones. */
@Composable
private fun CheckableItem(labelRes: Int, checked: Boolean, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        trailingIcon = {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null)
        },
        onClick = onToggle,
    )
}

// ======================================================================================
// Dibujo de las ayudas de las herramientas
// ======================================================================================

/**
 * Pinta lo que esta en curso: el trazo, el lazo, el rectangulo de seleccion, la linea del
 * degradado, el origen del tampon y los puntos del poligono.
 *
 * Es solo feedback: nada de esto toca el documento, por eso vive aqui y no en el motor.
 */
internal fun DrawScope.drawToolOverlay(
    controller: CanvasController,
    ui: EditorUi,
    doc: PixaDocument,
    composite: Bitmap?,
    accent: Color,
    bubbleTail: Offset? = null,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
    (if (ui.tailDragging) ui.dragCurrent else bubbleTail)?.let { p ->
        val v = controller.docToView(p)
        drawCircle(Color.White, radius = 17f, center = v)
        drawCircle(accent, radius = 12f, center = v)
    }
    val thin = Stroke(width = 2f, pathEffect = dash)
    val start = ui.dragStart?.let { controller.docToView(it) }
    val current = ui.dragCurrent?.let { controller.docToView(it) }
    if (ui.tool == CanvasTool.AI_MARK) drawMarks(ui, controller)

    // Trazo del pincel en curso: se ve mientras se pinta y se aplica al soltar.
    if (ui.livePoints.size > 1 && (ui.tool.isBrushLike || ui.tool == CanvasTool.TEXT_PATH)) {
        val path = viewPath(ui.livePoints, controller)
        val width = if (ui.tool == CanvasTool.TEXT_PATH) {
            3f
        } else {
            (ui.brush.size * controller.zoom).coerceAtLeast(2f)
        }
        // El pincel se ve mientras se dibuja con su color y opacidad reales: lo que hay en
        // pantalla al soltar el dedo es exactamente lo que ya se vio, sin una previa
        // generica de otro color que cambie de golpe al terminar el trazo. Los demas
        // pinceles (clonar, desenfocar...) no pintan un color y siguen con la guia neutra.
        //
        // Bug encontrado (requisito del usuario): aqui se leia ui.brush.color directo, que
        // no es el color real del trazo — el color de verdad sale de brushColorSynced (el
        // selector de color global, ui.color), que es justo lo que usa startBrush() al
        // aplicar. Por eso se veia un color generico mientras se dibujaba y el correcto
        // "aparecia" de golpe al soltar el dedo.
        val (strokeColor, strokeAlpha) = if (ui.tool == CanvasTool.PAINT && ui.brush.kind != BrushKind.ERASER) {
            Color(ui.brushColorSynced.color) to ui.brush.opacity
        } else if (ui.tool == CanvasTool.AI_ERASE) {
            Color(0xFFFF2D55) to 0.55f
        } else {
            accent to 0.45f
        }
        drawPath(
            path = path,
            color = strokeColor.copy(alpha = strokeAlpha),
            style = Stroke(width = width),
        )
    }

    when (ui.tool) {
        // Como en Photoshop: el trazo va abierto y continuo por donde pasa el dedo, y una
        // linea de puntos marca el cierre hacia el punto de partida (antes se pintaba ya
        // cerrado y parecia un circulo).
        CanvasTool.AI_SELECT -> if (ui.livePoints.size > 1) {
            drawPath(viewPath(ui.livePoints, controller), accent, style = Stroke(width = 3f, pathEffect = dash))
        }

        CanvasTool.SELECT_LASSO, CanvasTool.SELECT_MAGNETIC -> if (ui.livePoints.size > 1) {
            val pts = ui.livePoints
            drawPath(viewPath(pts, controller), accent, style = Stroke(width = 3f))
            drawLine(accent, controller.docToView(pts.last()), controller.docToView(pts.first()), strokeWidth = 2f, pathEffect = dash)
            drawCircle(accent, radius = 7f, center = controller.docToView(pts.first()))
        }

        CanvasTool.SELECT_POLY -> {
            val points = ui.polyPoints
            if (points.isNotEmpty()) {
                val live = ui.dragCurrent?.let { points + it } ?: points
                drawPath(viewPath(live, controller), accent, style = Stroke(width = 3f))
                if (points.size > 2) {
                    drawLine(accent, controller.docToView(live.last()), controller.docToView(points.first()), strokeWidth = 2f, pathEffect = dash)
                }
                points.forEachIndexed { index, point ->
                    // El primer punto es mas grande: tocarlo cierra la forma.
                    drawCircle(accent, radius = if (index == 0) 11f else 6f, center = controller.docToView(point))
                }
            }
        }

        CanvasTool.CROP -> if (start != null && current != null) {
            val left = min(start.x, current.x)
            val top = min(start.y, current.y)
            val right = max(start.x, current.x)
            val bottom = max(start.y, current.y)
            // Contraste adaptativo: negro sobre fondos claros, blanco sobre oscuros, para
            // que el marco y los tiradores se distingan tambien en fotos muy blancas o muy
            // negras (requisito del usuario).
            val cropColor = cropOutlineColor(composite, doc)
            // La zona que queda fuera del recorte se vela con una mascara gris, y solo la
            // seleccion se ve limpia, como en el resto de editores (requisito del usuario).
            drawCropMask(
                viewDocRect(doc, controller),
                androidx.compose.ui.geometry.Rect(left, top, right, bottom),
            )
            drawRect(
                color = cropColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = thin,
            )
            drawBoxHandles(
                Offset(left, top),
                Offset(right, bottom),
                cropColor,
                fill = cropColor,
                radius = CROP_HANDLE_RADIUS,
                square = true,
            )
        }

        CanvasTool.SELECT_RECT, CanvasTool.PRIVACY_RECT ->
            if (start != null && current != null) {
                drawRect(
                    color = accent,
                    topLeft = Offset(min(start.x, current.x), min(start.y, current.y)),
                    size = androidx.compose.ui.geometry.Size(
                        abs(current.x - start.x),
                        abs(current.y - start.y),
                    ),
                    style = thin,
                )
                if (SelectionBox.isBoxTool(ui.tool)) drawBoxHandles(start, current, accent)
            }

        CanvasTool.SELECT_ELLIPSE -> if (start != null && current != null) {
            val topLeft = Offset(min(start.x, current.x), min(start.y, current.y))
            val size = androidx.compose.ui.geometry.Size(
                abs(current.x - start.x),
                abs(current.y - start.y),
            )
            drawOval(color = accent, topLeft = topLeft, size = size, style = thin)
            // El marco se pinta tambien en la elipse: es lo que se agarra para moverla.
            drawRect(
                color = accent.copy(alpha = 0.4f),
                topLeft = topLeft,
                size = size,
                style = thin,
            )
            drawBoxHandles(start, current, accent)
        }

        CanvasTool.GRADIENT -> if (start != null && current != null) {
            drawLine(accent, start, current, strokeWidth = 3f)
            drawCircle(accent, radius = 8f, center = start)
            drawCircle(accent, radius = 8f, center = current)
        }

        // La forma real se ve mientras se arrastra, no un rectangulo generico: una
        // estrella o una flecha se reconocen desde el primer momento, no solo al soltar.
        CanvasTool.SHAPE_DRAW -> if (start != null && current != null) {
            val left = min(start.x, current.x)
            val top = min(start.y, current.y)
            val w = abs(current.x - start.x).coerceAtLeast(1f)
            val h = abs(current.y - start.y).coerceAtLeast(1f)
            val shapePath = PathShapes.shapePath(ui.shape.copy(width = w, height = h)).asComposePath()


            translate(left, top) {
                drawPath(shapePath, color = accent.copy(alpha = 0.25f))
                drawPath(shapePath, color = accent, style = Stroke(width = 3f))
            }
        }

        CanvasTool.CLONE, CanvasTool.CLONE_SOURCE -> ui.cloneSource?.let { source ->
            val p = controller.docToView(source)
            drawCircle(accent, radius = 14f, center = p, style = Stroke(width = 2f))
            drawLine(accent, Offset(p.x - 20f, p.y), Offset(p.x + 20f, p.y), strokeWidth = 2f)
            drawLine(accent, Offset(p.x, p.y - 20f), Offset(p.x, p.y + 20f), strokeWidth = 2f)
        }

        else -> Unit
    }
}

/** Tiradores del recuadro: lo que se agarra para cambiarlo de tamano (requisito 12). */
private fun DrawScope.drawBoxHandles(
    start: Offset,
    current: Offset,
    accent: Color,
    fill: Color = Color.White,
    radius: Float = 11f,
    square: Boolean = false,
) {
    val box = RectF(
        min(start.x, current.x),
        min(start.y, current.y),
        max(start.x, current.x),
        max(start.y, current.y),
    )
    SelectionBox.handles(box).forEach { handle ->
        if (square) {
            // Tiradores cuadrados centrados en el vertice: mas grandes y faciles de ver
            // sobre cualquier fondo (requisito del usuario).
            val half = radius
            drawRect(
                fill,
                topLeft = Offset(handle.x - half, handle.y - half),
                size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f),
            )
            drawRect(
                accent,
                topLeft = Offset(handle.x - half, handle.y - half),
                size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f),
                style = Stroke(width = 3f),
            )
        } else {
            drawCircle(fill, radius = radius, center = handle)
            drawCircle(accent, radius = radius, center = handle, style = Stroke(width = 3f))
        }
    }
}

/**
 * Color de contraste para el recuadro de recorte: negro sobre fondos claros y blanco sobre
 * oscuros, decidido por la luminosidad media de una muestra pequena del compuesto (o del
 * fondo del lienzo si no hay compuesto). Requisito del usuario.
 */
private fun cropOutlineColor(composite: Bitmap?, doc: PixaDocument): Color {
    val sample = if (composite != null && !composite.isRecycled) {
        BitmapUtils.thumbnail(composite, 8)
    } else {
        null
    }
    val luminance = if (sample != null) {
        var sum = 0.0
        var n = 0
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val c = sample.getPixel(x, y)
                sum += 0.299 * ((c shr 16) and 0xFF) +
                    0.587 * ((c shr 8) and 0xFF) +
                    0.114 * (c and 0xFF)
                n++
            }
        }
        if (n == 0) 255.0 else sum / n
    } else {
        val c = doc.background.color
        0.299 * ((c shr 16) and 0xFF) +
            0.587 * ((c shr 8) and 0xFF) +
            0.114 * (c and 0xFF)
    }
    return if (luminance > 160) Color.Black else Color.White
}

/** Radio fijo de los tiradores de recorte: grande para que se agarren bien (requisito del usuario). */
private const val CROP_HANDLE_RADIUS = 18f

/** Rectangulo del documento proyectado a coordenadas de vista. */
private fun viewDocRect(doc: PixaDocument, controller: CanvasController): androidx.compose.ui.geometry.Rect {
    val tl = controller.docToView(Offset.Zero)
    val br = controller.docToView(Offset(doc.width.toFloat(), doc.height.toFloat()))
    return androidx.compose.ui.geometry.Rect(tl.x, tl.y, br.x, br.y)
}

/** Vela con gris todo el documento salvo la zona de recorte (requisito del usuario). */
private fun DrawScope.drawCropMask(
    docRect: androidx.compose.ui.geometry.Rect,
    cropRect: androidx.compose.ui.geometry.Rect,
) {
    val gray = Color.Black.copy(alpha = 0.55f)
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(docRect)
        addRect(cropRect)
    }
    drawPath(path, gray)
}

/** Convierte puntos de documento en un trazado de pantalla. */
private fun viewPath(
    points: List<Offset>,
    controller: CanvasController,
    close: Boolean = false,
): Path {
    val path = Path()
    points.forEachIndexed { index, point ->
        val p = controller.docToView(point)
        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    if (close && points.size > 2) path.close()
    return path
}

// ======================================================================================
// Reparto de gestos entre herramientas
// ======================================================================================

/**
 * Traduce los gestos del lienzo en ediciones concretas (requisitos 6 a 14).
 *
 * Reglas que se repiten en todas las herramientas y que explican la forma del codigo:
 *
 *  - [CanvasHost] entrega siempre coordenadas de documento. Lo que pinta sobre una capa
 *    hay que pasarlo al espacio de la capa con la inversa de su matriz.
 *  - Un trazo entero es un unico paso de historial: se acumulan los puntos y se aplica al
 *    soltar. Asi deshacer no se llena de cien pasos y no se filtra la imagen en cada
 *    movimiento del dedo.
 *  - Mientras se arrastra se usa `previewDocument`, que pinta sin tocar el historial, y
 *    solo al soltar se hace `commit`.
 */
internal class ToolHandler(
    private val vm: EditorViewModel,
    private val ui: EditorUi,
    private val controller: CanvasController,
    private val rasterizer: LayerRasterizer,
    private val scope: CoroutineScope,
) {
    var state: EditorState = EditorState()
    var snapEnabled: Boolean = true
    var snapTolerancePx: Float = 24f
    var handleRadiusPx: Float = 48f
    var str: (Int) -> String = { "" }
    var onColorPicked: (Int) -> Unit = {}
    var onQuad: (FloatArray?) -> Unit = {}
    var onHaptic: () -> Unit = {}
    var appContext: Context? = null

    private val doc: PixaDocument get() = state.doc

    // Estado del arrastre en curso. Se limpia siempre en onStrokeEnd / onStrokeCancel.
    private var moveLayerId: String? = null
    private var tailLayerId: String? = null

    // Estirado de un bocadillo desde el punto medio de un lado (deformacion libre).
    private var edgeLayerId: String? = null
    private var edgeIndex = -1
    private var edgeBase: Xf? = null
    private var edgeCenter = Offset.Zero
    private var edgeAxis = Offset.Zero
    private var edgeHalf = 1f
    private var moveHandle: Int = -1
    private var startXf: Xf? = null
    private var startBounds: RectF? = null
    private var startDistance: Float = 1f
    private var startTextSpec: TextSpec? = null
    private var perspectiveStart: List<Float>? = null
    private var objectLayerId: String? = null
    private var objectBase: Xf? = null
    private var objectDegrees: Float = 0f
    private var objectScale: Float = 1f
    private var objectPan: Offset = Offset.Zero
    private var objectBlocked = false
    private var stroke: BrushStroke? = null
    private var strokeLayerId: String? = null
    private var busy = false

    // Arrastre del recuadro de seleccion y de recorte (requisito 12).
    private var boxHandle: Int = -1
    private var boxStart: RectF? = null
    private var boxGrab: Offset? = null

    /** Pixeles de la capa antes de tocar el fondo, para el pincel de restaurar. */
    private val originals = HashMap<String, String>()

    fun snapshotOriginal() {
        val layer = doc.selected ?: return
        val content = layer.content
        if (content is LayerContent.Raster) originals.putIfAbsent(layer.id, content.bitmapId)
    }

    // ----------------------------------------------------------------------------------
    // Toques
    // ----------------------------------------------------------------------------------

    /** Fuera de estos limites no hay lienzo, asi que ningun toque debe interactuar con nada. */
    private fun insideCanvas(point: Offset): Boolean =
        point.x >= 0f && point.y >= 0f && point.x <= doc.width.toFloat() && point.y <= doc.height.toFloat()

    fun onTap(point: Offset) {
        if (!insideCanvas(point)) {
            // Tocar la zona negra de fuera del lienzo deselecciona lo que haya seleccionado.
            if (ui.tool == CanvasTool.MOVE) vm.selectLayer(null)
            return
        }
        // En modo "mover vista" un toque no marca ni selecciona nada.
        if (ui.panMode && ui.tool.isAiTool) return
        when (ui.tool) {
            CanvasTool.MOVE -> {
                // Capa de arriba bajo el dedo, rellenos incluidos. Un degradado / color cubre
                // todo el lienzo: un toque lo selecciona si no hay nada seleccionado y otro
                // toque lo deselecciona (con otra capa seleccionada, el toque deselecciona).
                val top = LayerGeometry.pick(doc, rasterizer, point, includeFills = true)
                val selectedId = doc.selected?.id
                val id = when {
                    top == null -> null
                    top.content is LayerContent.Fill ->
                        if (selectedId == null) top.id else null
                    else -> top.id
                }
                vm.selectLayer(id)
            }

            CanvasTool.EYEDROPPER -> pickFromComposite(point)?.let(onColorPicked)

            CanvasTool.BUCKET -> onRaster { layer, bitmap ->
                val p = toLayer(layer, bitmap, point) ?: return@onRaster
                if (!inside(p, bitmap)) return@onRaster
                val filled = BrushEngine.bucketFill(
                    src = bitmap,
                    x = p.x.roundToInt(),
                    y = p.y.roundToInt(),
                    color = ui.color,
                    tolerance = ui.wandTolerance,
                    contiguous = ui.wandContiguous,
                )
                vm.replaceRaster(layer.id, filled, R.string.tool_bucket)
            }

            CanvasTool.RED_EYE -> onRaster { layer, bitmap ->
                val p = toLayer(layer, bitmap, point) ?: return@onRaster
                val r = (ui.brush.size * 0.75f).coerceAtLeast(8f)
                val area = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
                val fixed = Retouch.fixRedEye(bitmap, area, ui.retouchAmount)
                vm.replaceRaster(layer.id, fixed, R.string.retouch_red_eye)
            }

            CanvasTool.CLONE_SOURCE -> {
                ui.cloneSource = point
                ui.tool = CanvasTool.CLONE
                onHaptic()
                vm.post(R.string.retouch_clone_ready)
            }

            CanvasTool.SELECT_POLY -> {
                val first = ui.polyPoints.firstOrNull()
                val closeRadius = controller.viewLengthToDoc(28f)
                if (first != null && ui.polyPoints.size >= 3 &&
                    (point - first).getDistance() <= closeRadius
                ) {
                    commitPolygon(ui.polyPoints)
                } else {
                    ui.polyPoints = ui.polyPoints + point
                }
            }

            CanvasTool.SELECT_WAND -> wandSelect(point, byColor = false)
            CanvasTool.SELECT_BY_COLOR -> wandSelect(point, byColor = true)

            CanvasTool.AI_SELECT -> vm.samTap(point.x, point.y, ui.aiPositive)

            // Un toque con el pincel deja un punto: lunares, motas, manchas pequenas.
            CanvasTool.AI_MARK -> if (ui.markShape == MarkShape.BRUSH) {
                ui.addMark(EraseMark(MarkShape.BRUSH, listOf(point), ui.brush.size / 2f))
            }

            CanvasTool.BG_PICK_COLOR -> onRaster { layer, bitmap ->
                val p = toLayer(layer, bitmap, point) ?: return@onRaster
                if (!inside(p, bitmap)) return@onRaster
                val color = BrushEngine.pickColor(bitmap, p.x.roundToInt(), p.y.roundToInt())
                val erased = Cutout.eraseColor(bitmap, color, ui.wandTolerance)
                originals.putIfAbsent(layer.id, (layer.content as LayerContent.Raster).bitmapId)
                vm.replaceRaster(layer.id, erased, R.string.bg_remove_by_color)
                ui.tool = CanvasTool.BG_ERASE
            }

            else -> Unit
        }
    }

    /** Devuelve true cuando la herramienta se queda el doble toque para ella. */
    fun onDoubleTap(point: Offset): Boolean = if (!insideCanvas(point)) false else when (ui.tool) {
        CanvasTool.SELECT_POLY -> {
            val points = ui.polyPoints
            if (points.size >= 3) {
                commitPolygon(points)
                true
            } else {
                false
            }
        }

        // Doble toque sobre texto, forma o QR: abre su panel para editarlo sin tener que
        // ir a buscarlo en la barra de herramientas.
        else -> {
            val layer = LayerGeometry.pick(doc, rasterizer, point, includeFills = true)
            val panel = layer?.let(::panelFor)
            if (layer != null && panel != null) {
                vm.selectLayer(layer.id)
                ui.openPanelDirect(panel)
                if (panel == EditorPanel.SHAPES) ui.tool = CanvasTool.MOVE
                true
            } else {
                false
            }
        }
    }

    private fun panelFor(layer: PixaLayer): EditorPanel? = when (layer.content) {
        is LayerContent.Text -> EditorPanel.TEXT
        is LayerContent.Shape -> EditorPanel.SHAPES
        is LayerContent.Qr -> EditorPanel.QR
        is LayerContent.SpeechBubble -> EditorPanel.BUBBLE
        is LayerContent.Fill -> if (layer.content.spec is FillSpec.Gradient) EditorPanel.GRADIENT else null
        else -> null
    }

    // ----------------------------------------------------------------------------------
    // Arrastres
    // ----------------------------------------------------------------------------------

    fun onStrokeStart(point: Offset) {
        // Requisito 12: las herramientas de recuadro no se dibujan, se arrastran, asi que
        // ni se toca el recuadro que ya hay ni se empieza uno nuevo.
        if (SelectionBox.isBoxTool(ui.tool)) {
            startBox(point)
            return
        }
        ui.dragStart = point
        ui.dragCurrent = point

        when {
            ui.tool == CanvasTool.MOVE -> startMove(point)
            ui.tool == CanvasTool.PERSPECTIVE -> beginPerspective(point)
            ui.tool == CanvasTool.PAINT -> startBrush(point)
            ui.tool.isBrushLike ||
                ui.tool == CanvasTool.SELECT_LASSO ||
                ui.tool == CanvasTool.SELECT_MAGNETIC ||
                ui.tool == CanvasTool.TEXT_PATH ||
                ui.tool == CanvasTool.AI_SELECT ||
                ui.tool == CanvasTool.AI_MARK -> ui.livePoints = listOf(point)
            else -> Unit
        }
    }

    fun onStrokeMove(point: Offset) {
        if (SelectionBox.isBoxTool(ui.tool)) {
            dragBox(point)
            return
        }
        ui.dragCurrent = point
        when {
            ui.tool == CanvasTool.MOVE -> dragMove(point)
            ui.tool == CanvasTool.PERSPECTIVE -> dragPerspective(point)
            ui.tool == CanvasTool.PAINT -> {
                val layerId = strokeLayerId
                val active = stroke
                if (layerId != null && active != null) {
                    val layer = doc.layer(layerId)
                    val target = targetBitmap(layer)
                    if (layer != null && target != null) {
                        val p = toLayer(layer, target, point)
                        if (p != null) active.moveTo(p.x, p.y)
                    }
                }
                ui.livePoints = ui.livePoints + point
            }

            ui.tool.isBrushLike ||
                ui.tool == CanvasTool.SELECT_LASSO ||
                ui.tool == CanvasTool.SELECT_MAGNETIC ||
                ui.tool == CanvasTool.TEXT_PATH ||
                ui.tool == CanvasTool.AI_SELECT ||
                ui.tool == CanvasTool.AI_MARK -> ui.livePoints = ui.livePoints + point

            else -> Unit
        }
    }

    fun onStrokeEnd(point: Offset) {
        if (SelectionBox.isBoxTool(ui.tool)) {
            finishBox(point)
            return
        }
        ui.dragCurrent = point
        val tool = ui.tool
        val points = ui.livePoints
        val from = ui.dragStart

        when (tool) {
            CanvasTool.MOVE -> finishMove()
            CanvasTool.PERSPECTIVE -> finishPerspective()

            // Arrastrar con la lupa y soltar: el color es el del punto donde se levanta
            // el dedo, no el del punto donde se apoyo.
            CanvasTool.EYEDROPPER -> pickFromComposite(point)?.let(onColorPicked)

            CanvasTool.PAINT -> finishBrush()

            CanvasTool.CLONE -> finishPathTool(points) { layer, target, path, radius ->
                val source = ui.cloneSource
                if (source == null) {
                    vm.post(R.string.retouch_clone_source)
                    return@finishPathTool null
                }
                val first = points.firstOrNull() ?: return@finishPathTool null
                val sourceLayer = toLayer(layer, target, source) ?: return@finishPathTool null
                val firstLayer = toLayer(layer, target, first) ?: return@finishPathTool null
                val copy = BitmapUtils.mutableCopy(target)
                Retouch.cloneStroke(
                    target = copy,
                    source = target,
                    stroke = path,
                    offsetX = sourceLayer.x - firstLayer.x,
                    offsetY = sourceLayer.y - firstLayer.y,
                    radius = radius,
                    hardness = ui.brush.hardness,
                    opacity = ui.brush.opacity,
                )
                copy to R.string.retouch_clone
            }

            CanvasTool.BLUR_BRUSH -> finishPathTool(points) { _, target, path, radius ->
                val copy = BitmapUtils.mutableCopy(target)
                Retouch.blurStroke(copy, path, radius, ui.retouchAmount, ui.brush.hardness)
                copy to R.string.retouch_blur_brush
            }

            CanvasTool.SHARPEN_BRUSH -> finishPathTool(points) { _, target, path, radius ->
                val copy = BitmapUtils.mutableCopy(target)
                Retouch.sharpenStroke(copy, path, radius, ui.retouchAmount, ui.brush.hardness)
                copy to R.string.retouch_sharpen_brush
            }

            CanvasTool.PRIVACY_BRUSH -> finishPathTool(points) { _, target, path, radius ->
                val copy = BitmapUtils.mutableCopy(target)
                if (ui.privacyStyle == PrivacyStyle.PIXELATE) {
                    val block = (radius / 2f).roundToInt().coerceIn(3, 64)
                    Retouch.pixelateStroke(copy, path, radius, block, ui.brush.hardness)
                } else {
                    Retouch.blurStroke(copy, path, radius, 1f, ui.brush.hardness)
                }
                copy to R.string.privacy_title
            }

            CanvasTool.BG_ERASE -> finishPathTool(points) { layer, target, path, radius ->
                originals.putIfAbsent(layer.id, (layer.content as LayerContent.Raster).bitmapId)
                val copy = BitmapUtils.mutableCopy(target)
                Cutout.eraseStroke(copy, path, radius, ui.brush.hardness)
                copy to R.string.bg_brush_erase
            }

            CanvasTool.BG_RESTORE -> finishPathTool(points) { layer, target, path, radius ->
                val original = vm.getBitmap(originals[layer.id])
                if (original == null || original.width != target.width ||
                    original.height != target.height
                ) {
                    vm.post(R.string.bg_brush_restore)
                    return@finishPathTool null
                }
                val copy = BitmapUtils.mutableCopy(target)
                Cutout.restoreStroke(copy, original, path, radius, ui.brush.hardness)
                copy to R.string.bg_brush_restore
            }

            CanvasTool.AI_ERASE -> finishAiErase(points)

            CanvasTool.AI_MARK -> finishMark(points, from, point)

            // Dibujar alrededor del objeto: su caja es la indicacion para el modelo.
            CanvasTool.AI_SELECT -> if (points.size >= 3) {
                vm.samBox(
                    points.minOf { it.x }, points.minOf { it.y },
                    points.maxOf { it.x }, points.maxOf { it.y },
                )
            }

            CanvasTool.SELECT_LASSO -> if (points.size >= 3) commitPolygon(points)

            CanvasTool.SELECT_MAGNETIC -> if (points.size >= 3) magneticSelect(points)

            CanvasTool.GRADIENT -> if (from != null) applyGradient(from, point)

            CanvasTool.SHAPE_DRAW -> if (from != null) addShape(from, point)

            CanvasTool.PRIVACY_RECT -> if (from != null) censorRect(from, point)

            CanvasTool.TEXT_PATH -> if (points.size >= 2) applyTextPath(points)

            else -> Unit
        }

        if (tool != CanvasTool.SELECT_POLY) ui.resetGesture()
        onQuad(null)
    }

    fun onStrokeCancel() {
        stroke?.discard()
        stroke = null
        strokeLayerId = null
        moveLayerId = null
        tailLayerId = null
        edgeLayerId = null
        edgeBase = null
        ui.tailDragging = false
        moveHandle = -1
        startXf = null
        startBounds = null
        perspectiveStart = null
        vm.cancelPreview()
        cancelBox()
        // El recuadro no se borra: es el estado de la herramienta, no del gesto.
        if (!SelectionBox.isBoxTool(ui.tool)) ui.resetGesture()
        onQuad(null)
    }

    // ----------------------------------------------------------------------------------
    // Recuadro de seleccion y de recorte
    // ----------------------------------------------------------------------------------

    /** Pone el recuadro si la herramienta lo usa y todavia no hay ninguno. */
    fun ensureBox() {
        if (!SelectionBox.isBoxTool(ui.tool)) return
        if (SelectionBox.of(ui) != null) return
        val docWidth = doc.width.toFloat()
        val docHeight = doc.height.toFloat()
        // Si ya habia una seleccion se reaprovecha su marco: el recuadro aparece donde se
        // estaba trabajando en vez de en medio del lienzo.
        val previous = state.selection?.bounds?.takeIf { !it.isEmpty }
        val box = if (ui.tool == CanvasTool.CROP) {
            // Recortar: arranca seleccionando toda la imagen y en modo libre.
            ui.boxRatio = null
            RectF(0f, 0f, docWidth, docHeight)
        } else if (previous != null) {
            RectF(
                previous.left.toFloat(),
                previous.top.toFloat(),
                previous.right.toFloat(),
                previous.bottom.toFloat(),
            )
        } else {
            SelectionBox.default(docWidth, docHeight, ui.boxRatio)
        }
        SelectionBox.set(ui, SelectionBox.moved(box, Offset.Zero, docWidth, docHeight))
    }

    private fun startBox(point: Offset) {
        ensureBox()
        val box = SelectionBox.of(ui) ?: return
        boxStart = box
        boxGrab = point
        boxHandle = SelectionBox.handleAt(box, point, controller.viewLengthToDoc(handleRadiusPx))
        if (boxHandle >= 0) onHaptic()
    }

    /**
     * Agarrar un tirador cambia el tamano; agarrar en cualquier otro sitio mueve el
     * recuadro entero. Nunca se dibuja uno nuevo: el que hay se arrastra.
     *
     * Requisito del usuario: con las guias inteligentes y el imantado activos, el recuadro
     * de recorte/seleccion tambien se pega a los bordes y al centro del lienzo, igual que
     * al mover una capa.
     */
    private fun dragBox(point: Offset) {
        val base = boxStart ?: return
        val grab = boxGrab ?: return
        val docWidth = doc.width.toFloat()
        val docHeight = doc.height.toFloat()
        val next = if (boxHandle >= 0) {
            SelectionBox.resized(base, boxHandle, point, ui.boxRatio, docWidth, docHeight)
        } else {
            SelectionBox.moved(base, point - grab, docWidth, docHeight)
        }
        SelectionBox.set(ui, snapBoxToCanvas(next, docWidth, docHeight))
    }

    /**
     * Pega los bordes activos del recuadro a las referencias del lienzo (bordes y centro),
     * devolviendo el recuadro corregido y dejando en [EditorUi.guides] las guias a dibujar.
     * Con la proporcion fijada no se pegan los bordes sueltos (romperia la proporcion):
     * en ese caso solo se recuadra el gesto sin iman.
     */
    private fun snapBoxToCanvas(box: RectF, docWidth: Float, docHeight: Float): RectF {
        ui.guides = emptyList()
        if (!snapEnabled) return box
        val tolerance = controller.viewLengthToDoc(snapTolerancePx)
        val guides = mutableListOf<SmartGuide>()

        // Al mover (sin tirador) se pega el recuadro entero como en [dragMove].
        if (boxHandle < 0) {
            val snap = Snapping.snapBounds(
                left = box.left,
                top = box.top,
                width = box.width(),
                height = box.height(),
                docWidth = docWidth,
                docHeight = docHeight,
                tolerance = tolerance,
                enabled = true,
            )
            ui.guides = snap.guides
            val dx = snap.offset.x - box.left
            val dy = snap.offset.y - box.top
            return RectF(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy)
        }

        // Con proporcion fijada no se pega el borde (cambiaria el alto/ancho).
        if (ui.boxRatio != null && ui.boxRatio!! > 0f) return box

        // Se pegan solo los bordes que mueve el tirador agarrado.
        var left = box.left
        var top = box.top
        var right = box.right
        var bottom = box.bottom
        if (boxHandle == 0 || boxHandle == 6 || boxHandle == 7) {
            val (v, g) = Snapping.snapEdge(left, docWidth, vertical = true, tolerance, enabled = true)
            left = v; g?.let { guides += it }
        }
        if (boxHandle == 2 || boxHandle == 3 || boxHandle == 4) {
            val (v, g) = Snapping.snapEdge(right, docWidth, vertical = true, tolerance, enabled = true)
            right = v; g?.let { guides += it }
        }
        if (boxHandle == 0 || boxHandle == 1 || boxHandle == 2) {
            val (v, g) = Snapping.snapEdge(top, docHeight, vertical = false, tolerance, enabled = true)
            top = v; g?.let { guides += it }
        }
        if (boxHandle == 4 || boxHandle == 5 || boxHandle == 6) {
            val (v, g) = Snapping.snapEdge(bottom, docHeight, vertical = false, tolerance, enabled = true)
            bottom = v; g?.let { guides += it }
        }
        ui.guides = guides
        return RectF(left, top, right, bottom)
    }

    private fun finishBox(point: Offset) {
        val dragged = boxStart != null
        dragBox(point)
        cancelBox()
        val box = SelectionBox.of(ui)
        if (!dragged || box == null) return
        // El recuadro se queda puesto. En recorte lo confirma el panel; en seleccion se
        // aplica al soltar y se puede seguir ajustando sin volver a dibujarlo.
        when (ui.tool) {
            CanvasTool.SELECT_RECT ->
                applySelection(SelectionTools.rect(doc.width, doc.height, box))

            CanvasTool.SELECT_ELLIPSE ->
                applySelection(SelectionTools.ellipse(doc.width, doc.height, box))

            else -> Unit
        }
    }

    private fun cancelBox() {
        boxStart = null
        boxGrab = null
        boxHandle = -1
        ui.guides = emptyList()
    }

    // ----------------------------------------------------------------------------------
    // Mover y escalar
    // ----------------------------------------------------------------------------------

    private fun startMove(point: Offset) {
        // Punta de la cola de un bocadillo: arrastrarla cambia la cola, no mueve la capa.
        val bubble = doc.selected?.takeIf { !it.locked && it.content is LayerContent.SpeechBubble }
        if (bubble != null) {
            val tip = runCatching { bubbleTipDoc(bubble, rasterizer) }.getOrNull()
            if (tip != null && (point - tip).getDistance() <= controller.viewLengthToDoc(handleRadiusPx * 0.8f)) {
                tailLayerId = bubble.id
                ui.tailDragging = true
                onHaptic()
                return
            }
        }
        if (bubble != null && startEdgeStretch(bubble, point)) return
        // Sin nada seleccionado (o bloqueado) no hay capa que mover: [CanvasHost] ya ha
        // decidido que este arrastre mueve la vista en su lugar, asi que aqui no hace
        // falta ni aviso ni recoger la capa de debajo del dedo.
        val layer = doc.selected?.takeIf { !it.locked && !it.transformLocked } ?: return

        val quad = runCatching { LayerGeometry.quad(layer, rasterizer, doc) }.getOrNull() ?: return
        val radius = controller.viewLengthToDoc(handleRadiusPx)
        moveLayerId = layer.id
        moveHandle = LayerGeometry.handleAt(quad, point, radius)
        startXf = layer.transform
        startTextSpec = (layer.content as? LayerContent.Text)?.spec
        startBounds = runCatching { LayerGeometry.bounds(layer, rasterizer, doc) }.getOrNull()
        val bounds = startBounds
        startDistance = if (bounds == null) {
            1f
        } else {
            hypot(point.x - bounds.centerX(), point.y - bounds.centerY()).coerceAtLeast(1f)
        }
        if (moveHandle >= 0) onHaptic()
    }

    /**
     * Requisito del usuario: en texto, los tiradores cambian el ancho de la caja (el
     * texto se reajusta dentro, con el mismo tamano de letra) en vez de estirar la letra
     * como si fuera una imagen. El centro se mantiene fijo, igual que la escala uniforme
     * de las demas capas.
     */
    private fun resizeTextBox(spec: TextSpec, base: Xf, factor: Float): Pair<TextSpec, Xf> {
        val oldSize = rasterizer.intrinsicSize(PixaLayer(name = "", content = LayerContent.Text(spec)), doc.width, doc.height)
        val newSpec = spec.copy(boxWidth = (spec.boxWidth * factor).coerceIn(32f, doc.width * 4f))
        val newSize = rasterizer.intrinsicSize(PixaLayer(name = "", content = LayerContent.Text(newSpec)), doc.width, doc.height)
        val newXf = base.copy(
            scaleX = 1f,
            scaleY = 1f,
            tx = base.tx + (oldSize.width - newSize.width) / 2f,
            ty = base.ty + (oldSize.height - newSize.height) / 2f,
        )
        return newSpec to newXf
    }

    /** true si [point] cae en el tirador de un lado y empieza a estirar el bocadillo. */
    private fun startEdgeStretch(layer: PixaLayer, point: Offset): Boolean {
        val q = runCatching { LayerGeometry.quad(layer, rasterizer, doc) }.getOrNull() ?: return false
        val radius = controller.viewLengthToDoc(handleRadiusPx)
        // Las esquinas mandan: escalan en proporcion como hasta ahora.
        if (LayerGeometry.handleAt(q, point, radius) >= 0) return false
        val edgeRadius = controller.viewLengthToDoc(handleRadiusPx * 0.75f)
        val mids = bubbleHandleQuad(layer, rasterizer, q, controller.viewLengthToDoc(HANDLE_GAP_PX))
        for (i in 0 until 4) {
            val mx = mids[8 + i * 2]
            val my = mids[9 + i * 2]
            if (hypot(point.x - mx, point.y - my) <= edgeRadius) {
                val cx = (q[0] + q[4]) / 2f
                val cy = (q[1] + q[5]) / 2f
                // El eje usa el punto medio real del lado, no el tirador (que puede estar desplazado).
                val tmx = (q[(i * 2) % 8] + q[((i + 1) * 2) % 8]) / 2f
                val tmy = (q[(i * 2 + 1) % 8] + q[((i + 1) * 2 + 1) % 8]) / 2f
                val ax = tmx - cx
                val ay = tmy - cy
                val len = hypot(ax, ay).coerceAtLeast(1f)
                edgeLayerId = layer.id
                edgeIndex = i
                edgeBase = layer.transform
                edgeCenter = Offset(cx, cy)
                edgeAxis = Offset(ax / len, ay / len)
                edgeHalf = len
                onHaptic()
                return true
            }
        }
        return false
    }

    /**
     * Transformacion con el lado arrastrado en [point] y el lado opuesto fijo: la capa se
     * estira solo en ese eje (arriba / abajo = alto, izquierda / derecha = ancho).
     */
    private fun edgeStretched(point: Offset): Xf? {
        val base = edgeBase ?: return null
        val along = (point.x - edgeCenter.x) * edgeAxis.x + (point.y - edgeCenter.y) * edgeAxis.y
        // Medida nueva = distancia del lado fijo (-half) al punto; nunca menos de un 5 %.
        val extent = (along + edgeHalf).coerceAtLeast(0.1f * edgeHalf)
        val factor = extent / (2f * edgeHalf)
        val shift = (extent - 2f * edgeHalf) / 2f
        val horizontal = edgeIndex == 1 || edgeIndex == 3
        return base.copy(
            scaleX = if (horizontal) (base.scaleX * factor) else base.scaleX,
            scaleY = if (horizontal) base.scaleY else (base.scaleY * factor),
            tx = base.tx + edgeAxis.x * shift,
            ty = base.ty + edgeAxis.y * shift,
        )
    }

    private fun dragMove(point: Offset) {
        edgeLayerId?.let { id ->
            val next = edgeStretched(point) ?: return
            val preview = doc.updateLayer(id) { it.copy(transform = next) }
            vm.previewDocument(preview, fast = true)
            preview.layer(id)?.let { live ->
                onQuad(
                    runCatching { LayerGeometry.quad(live, rasterizer, preview) }.getOrNull()
                        ?.let { bubbleHandleQuad(live, rasterizer, it, controller.viewLengthToDoc(HANDLE_GAP_PX)) },
                )
            }
            return
        }
        tailLayerId?.let { id ->
            val layer = doc.layer(id) ?: return
            val next = bubbleWithTail(layer, rasterizer, point) ?: return
            vm.previewDocument(doc.updateLayer(id) { next }, fast = true)
            return
        }
        val layerId = moveLayerId ?: return
        val base = startXf ?: return
        val bounds = startBounds ?: return
        val from = ui.dragStart ?: return
        val textSpec = startTextSpec

        var nextSpec: TextSpec? = null
        val next = if (moveHandle >= 0) {
            // ponytail: escala uniforme respecto al centro. La escala por ejes y el
            // sesgado tienen campos numericos en el panel de transformacion, que es
            // donde se puede ser preciso; con el dedo lo util es agrandar y reducir.
            val distance = hypot(point.x - bounds.centerX(), point.y - bounds.centerY())
            val factor = (distance / startDistance).coerceIn(0.05f, 20f)
            ui.guides = emptyList()
            if (textSpec != null) {
                val (spec, xf) = resizeTextBox(textSpec, base, factor)
                nextSpec = spec
                xf
            } else {
                base.copy(
                    scaleX = (base.scaleX * factor).coerceAtLeast(0.01f),
                    scaleY = (base.scaleY * factor).coerceAtLeast(0.01f),
                )
            }
        } else {
            val tolerance = controller.viewLengthToDoc(snapTolerancePx)
            val snap = Snapping.snapBounds(
                left = bounds.left + (point.x - from.x),
                top = bounds.top + (point.y - from.y),
                width = bounds.width(),
                height = bounds.height(),
                docWidth = doc.width.toFloat(),
                docHeight = doc.height.toFloat(),
                tolerance = tolerance,
                enabled = snapEnabled,
            )
            ui.guides = snap.guides
            base.copy(
                tx = base.tx + (snap.offset.x - bounds.left),
                ty = base.ty + (snap.offset.y - bounds.top),
            )
        }

        val preview = doc.updateLayer(layerId) { live ->
            val withTransform = live.copy(transform = next)
            val spec = nextSpec
            if (spec != null) withTransform.copy(content = LayerContent.Text(spec)) else withTransform
        }
        vm.previewDocument(preview, fast = true)
        preview.layer(layerId)?.let { live ->
            onQuad(runCatching { LayerGeometry.quad(live, rasterizer, preview) }.getOrNull())
        }
    }

    private fun finishMove() {
        edgeLayerId?.let { id ->
            edgeLayerId = null
            val end = ui.dragCurrent
            val next = if (end != null) edgeStretched(end) else null
            edgeBase = null
            if (next == null) {
                vm.cancelPreview()
            } else {
                vm.commit(R.string.transform_scale, doc.updateLayer(id) { it.copy(transform = next) })
            }
            onQuad(null)
            return
        }
        tailLayerId?.let { id ->
            tailLayerId = null
            ui.tailDragging = false
            val end = ui.dragCurrent
            val layer = doc.layer(id)
            val next = if (end != null && layer != null) bubbleWithTail(layer, rasterizer, end) else null
            if (next == null) {
                vm.cancelPreview()
            } else {
                vm.commit(R.string.bubble_title, doc.updateLayer(id) { next })
            }
            return
        }
        val layerId = moveLayerId
        val base = startXf
        val textSpec = startTextSpec
        moveLayerId = null
        startXf = null
        startTextSpec = null
        startBounds = null
        val handle = moveHandle
        moveHandle = -1
        if (layerId == null || base == null) {
            vm.cancelPreview()
            return
        }
        val from = ui.dragStart
        val to = ui.dragCurrent
        if (from == null || to == null || (to - from).getDistance() < 0.5f) {
            vm.cancelPreview()
            return
        }

        // Se recalcula en vez de reutilizar la vista previa: asi el paso de historial
        // sale del documento real y no de un estado intermedio.
        val bounds = doc.layer(layerId)?.let { live ->
            runCatching { LayerGeometry.bounds(live, rasterizer, doc) }.getOrNull()
        }
        var finalSpec: TextSpec? = null
        val next = if (handle >= 0 && bounds != null) {
            val distance = hypot(to.x - bounds.centerX(), to.y - bounds.centerY())
            val factor = (distance / startDistance).coerceIn(0.05f, 20f)
            if (textSpec != null) {
                val (spec, xf) = resizeTextBox(textSpec, base, factor)
                finalSpec = spec
                xf
            } else {
                base.copy(
                    scaleX = (base.scaleX * factor).coerceAtLeast(0.01f),
                    scaleY = (base.scaleY * factor).coerceAtLeast(0.01f),
                )
            }
        } else if (bounds == null) {
            base.copy(tx = base.tx + (to.x - from.x), ty = base.ty + (to.y - from.y))
        } else {
            // Mismo ajuste magnetico que durante el arrastre: sin esto, al soltar la capa
            // saltaba de la posicion imantada a la del dedo.
            val snap = Snapping.snapBounds(
                left = bounds.left + (to.x - from.x),
                top = bounds.top + (to.y - from.y),
                width = bounds.width(),
                height = bounds.height(),
                docWidth = doc.width.toFloat(),
                docHeight = doc.height.toFloat(),
                tolerance = controller.viewLengthToDoc(snapTolerancePx),
                enabled = snapEnabled,
            )
            base.copy(
                tx = base.tx + (snap.offset.x - bounds.left),
                ty = base.ty + (snap.offset.y - bounds.top),
            )
        }
        val label = if (handle >= 0) R.string.transform_scale else R.string.transform_move
        vm.commit(
            label,
            doc.updateLayer(layerId) { live ->
                val withTransform = live.copy(transform = next)
                val spec = finalSpec
                if (spec != null) withTransform.copy(content = LayerContent.Text(spec)) else withTransform
            },
        )
        ui.guides = emptyList()
    }

    // ----------------------------------------------------------------------------------
    // Transformar con dos dedos
    // ----------------------------------------------------------------------------------

    /**
     * Amplia, mueve y gira la capa seleccionada (requisito 9).
     *
     * Los tres valores son totales desde que entro el segundo dedo, no incrementos: asi la
     * transformacion sale siempre de la original y no se acumula error de redondeo durante
     * el gesto. [pan] llega ya en unidades de documento.
     */
    fun onObjectGesture(scale: Float, pan: Offset, degrees: Float) {
        if (objectLayerId == null && !objectBlocked) {
            val layer = doc.selected
            when {
                layer == null -> {
                    vm.post(R.string.layer_pick_any)
                    objectBlocked = true
                }

                layer.locked || layer.transformLocked -> {
                    vm.post(R.string.layer_locked_warning)
                    objectBlocked = true
                }

                else -> {
                    objectLayerId = layer.id
                    objectBase = layer.transform
                    onHaptic()
                }
            }
        }
        val layerId = objectLayerId ?: return
        val base = objectBase ?: return
        objectScale = scale
        objectPan = pan
        objectDegrees = degrees
        val preview = doc.updateLayer(layerId) {
            it.copy(transform = gesturedXf(base, scale, pan, degrees))
        }
        vm.previewDocument(preview, fast = true)
        preview.layer(layerId)?.let { live ->
            onQuad(runCatching { LayerGeometry.quad(live, rasterizer, preview) }.getOrNull())
        }
    }

    fun onObjectGestureEnd() {
        val layerId = objectLayerId
        val base = objectBase
        objectLayerId = null
        objectBase = null
        objectBlocked = false
        val degrees = objectDegrees
        val scale = objectScale
        val pan = objectPan
        objectDegrees = 0f
        objectScale = 1f
        objectPan = Offset.Zero
        val moved = degrees != 0f || scale != 1f || pan != Offset.Zero
        if (layerId == null || base == null || !moved) {
            vm.cancelPreview()
            return
        }
        // Igual que al mover: el paso de historial se calcula desde el documento real.
        vm.commit(
            R.string.transform_title,
            doc.updateLayer(layerId) { it.copy(transform = gesturedXf(base, scale, pan, degrees)) },
        )
        onQuad(null)
    }

    private fun gesturedXf(base: Xf, scale: Float, pan: Offset, degrees: Float): Xf = base.copy(
        scaleX = scaleLimited(base.scaleX * scale),
        scaleY = scaleLimited(base.scaleY * scale),
        rotation = Snapping.snapAngle(base.rotation + degrees, snapEnabled),
        tx = base.tx + pan.x,
        ty = base.ty + pan.y,
    )

    /** Respeta el signo (una capa volteada tiene escala negativa) y nunca llega a cero. */
    private fun scaleLimited(value: Float): Float {
        val magnitude = abs(value).coerceIn(0.02f, 40f)
        return if (value < 0f) -magnitude else magnitude
    }

    // ----------------------------------------------------------------------------------
    // Perspectiva de cuatro esquinas
    // ----------------------------------------------------------------------------------

    private fun beginPerspective(point: Offset) {
        val selected = doc.selected
        if (selected == null) {
            vm.post(R.string.layer_pick_any)
            return
        }
        // Requisito del usuario: la perspectiva si se permite sobre la capa base de una
        // imagen abierta (transformLocked), porque es un gesto deliberado con herramienta
        // dedicada; mover/redimensionar siguen bloqueados por error accidental. Solo se
        // respeta el bloqueo total (locked), que implica "no tocar esta capa".
        if (selected.locked) {
            vm.post(R.string.layer_locked_warning)
            return
        }
        val layer = selected
        val quad = runCatching { LayerGeometry.quad(layer, rasterizer, doc) }.getOrNull() ?: return
        val radius = controller.viewLengthToDoc(handleRadiusPx)
        moveHandle = LayerGeometry.handleAt(quad, point, radius)
        if (moveHandle < 0) {
            vm.post(R.string.transform_perspective_hint)
            return
        }
        moveLayerId = layer.id
        perspectiveStart = layer.transform.perspective ?: quad.toList()
        onHaptic()
    }

    private fun dragPerspective(point: Offset) {
        val layerId = moveLayerId ?: return
        val corners = perspectiveStart ?: return
        if (moveHandle < 0) return
        val next = corners.toMutableList()
        next[moveHandle * 2] = point.x
        next[moveHandle * 2 + 1] = point.y
        // La perspectiva manda sobre el resto de la transformacion, asi que al entrar en
        // este modo se dejan los demas campos en su valor neutro.
        val preview = doc.updateLayer(layerId) {
            it.copy(transform = Xf(perspective = next))
        }
        vm.previewDocument(preview, fast = true)
        onQuad(next.toFloatArray())
    }

    private fun finishPerspective() {
        val layerId = moveLayerId
        val corners = perspectiveStart
        moveLayerId = null
        perspectiveStart = null
        val handle = moveHandle
        moveHandle = -1
        val to = ui.dragCurrent
        if (layerId == null || corners == null || handle < 0 || to == null) {
            vm.cancelPreview()
            return
        }
        val next = corners.toMutableList()
        next[handle * 2] = to.x
        next[handle * 2 + 1] = to.y
        vm.commit(
            R.string.transform_perspective,
            doc.updateLayer(layerId) { it.copy(transform = Xf(perspective = next)) },
        )
    }

    // ----------------------------------------------------------------------------------
    // Pinceles
    // ----------------------------------------------------------------------------------

    private fun startBrush(point: Offset) {
        val pair = vm.rasterOrWarn() ?: return
        val (layer, bitmap) = pair
        if (layer.locked) {
            vm.post(R.string.layer_locked_warning)
            return
        }
        val p = toLayer(layer, bitmap, point) ?: return
        strokeLayerId = layer.id
        stroke = BrushStroke(
            width = bitmap.width,
            height = bitmap.height,
            settings = ui.brushColorSynced,
            selection = selectionFor(bitmap),
        ).also { it.start(p.x, p.y) }
        ui.livePoints = listOf(point)
    }

    private fun finishBrush() {
        val active = stroke
        val layerId = strokeLayerId
        stroke = null
        strokeLayerId = null
        if (active == null || layerId == null) return
        val layer = doc.layer(layerId)
        val target = targetBitmap(layer)
        if (layer == null || target == null) {
            active.discard()
            return
        }
        val copy = BitmapUtils.mutableCopy(target)
        active.commit(copy)
        vm.replaceRaster(layerId, copy, R.string.draw_title)
    }

    /**
     * Herramientas que trabajan con el trazo completo: clonar, desenfocar, enfocar,
     * ocultar y los pinceles de fondo. [build] recibe el trazo ya en espacio de la capa y
     * devuelve el bitmap resultante con su etiqueta de historial, o null para no hacer nada.
     */
    private fun finishPathTool(
        points: List<Offset>,
        build: (PixaLayer, Bitmap, AndroidPath, Float) -> Pair<Bitmap, Int>?,
    ) {
        if (points.isEmpty()) return
        val pair = vm.rasterOrWarn() ?: return
        val (layer, bitmap) = pair
        if (layer.locked) {
            vm.post(R.string.layer_locked_warning)
            return
        }
        val matrix = layer.transform.toMatrix(bitmap.width, bitmap.height)
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return
        val coords = FloatArray(points.size * 2)
        points.forEachIndexed { index, point ->
            coords[index * 2] = point.x
            coords[index * 2 + 1] = point.y
        }
        inverse.mapPoints(coords)
        val path = AndroidPath()
        path.moveTo(coords[0], coords[1])
        for (i in 1 until points.size) path.lineTo(coords[i * 2], coords[i * 2 + 1])
        if (points.size == 1) path.lineTo(coords[0] + 0.5f, coords[1] + 0.5f)

        // El radio se mide en la capa: si la capa esta escalada, el pincel tiene que
        // tapar en pantalla lo que se ve, no lo que mide el bitmap.
        val values = FloatArray(9)
        inverse.getValues(values)
        val factor = hypot(values[Matrix.MSCALE_X], values[Matrix.MSKEW_Y]).coerceAtLeast(0.01f)
        val radius = (ui.brush.size / 2f * factor).coerceIn(1f, 2048f)

        val result = build(layer, bitmap, path, radius) ?: return
        vm.replaceRaster(layer.id, result.first, result.second)
    }

    /**
     * Borrador magico: al soltar el dedo se borra lo pintado o, con la deteccion activada,
     * el objeto que haya dentro de la zona del trazo (si no hay ninguno, avisa).
     */
    private fun finishAiErase(points: List<Offset>) {
        val ctx = appContext ?: return
        if (points.isEmpty()) return
        val radius = ui.brush.size / 2f
        val action = (
            if (ui.aiEraseDetect) {
                removeStrokeDetectAction(vm, points, radius)
            } else {
                removeStrokeAction(vm, points, radius)
            }
            ) ?: return
        vm.runAi(ctx, action)
    }

    /** Fin de un gesto del borrador magico manual: se anade la marca (no se borra nada todavia). */
    private fun finishMark(points: List<Offset>, from: Offset?, to: Offset) {
        val mark = when (ui.markShape) {
            MarkShape.BRUSH ->
                if (points.isNotEmpty()) EraseMark(MarkShape.BRUSH, points, ui.brush.size / 2f) else null
            MarkShape.RECT, MarkShape.ELLIPSE ->
                if (from != null && (to - from).getDistance() > 4f) {
                    EraseMark(ui.markShape, listOf(from, to), 0f)
                } else {
                    null
                }
            MarkShape.LASSO ->
                if (points.size >= 3) EraseMark(MarkShape.LASSO, points, 0f) else null
        }
        if (mark != null) ui.addMark(mark)
    }

    // ----------------------------------------------------------------------------------
    // Seleccion
    // ----------------------------------------------------------------------------------

    private fun applySelection(selection: Selection) {
        if (selection.isEmpty && ui.selectionMode == Selection.Mode.NEW) {
            vm.setSelection(null)
        } else {
            vm.combineSelection(selection, ui.selectionMode)
        }
    }

    private fun commitPolygon(points: List<Offset>) {
        val coords = FloatArray(points.size * 2)
        points.forEachIndexed { index, point ->
            coords[index * 2] = point.x
            coords[index * 2 + 1] = point.y
        }
        applySelection(SelectionTools.polygon(doc.width, doc.height, coords.toList()))
        ui.resetPolygon()
        ui.resetGesture()
    }

    /** Lazo magnetico: ajusta el trazo a los bordes de la imagen compuesta y selecciona. */
    private fun magneticSelect(points: List<Offset>) {
        if (busy) return
        busy = true
        val radius = controller.viewLengthToDoc(22f).roundToInt().coerceIn(4, 40)
        scope.launch {
            try {
                val source = vm.compositeNow()
                val coords = FloatArray(points.size * 2)
                points.forEachIndexed { i, p ->
                    coords[i * 2] = p.x
                    coords[i * 2 + 1] = p.y
                }
                val snapped = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    MagneticLasso.snap(source, coords, radius)
                }
                applySelection(SelectionTools.polygon(doc.width, doc.height, snapped.toList()))
                ui.resetGesture()
            } finally {
                busy = false
            }
        }
    }

    private fun wandSelect(point: Offset, byColor: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val source = vm.compositeNow()
                val x = point.x.roundToInt().coerceIn(0, source.width - 1)
                val y = point.y.roundToInt().coerceIn(0, source.height - 1)
                val selection = if (byColor) {
                    SelectionTools.byColor(
                        source,
                        BrushEngine.pickColor(source, x, y),
                        ui.wandTolerance,
                    )
                } else {
                    SelectionTools.magicWand(source, x, y, ui.wandTolerance, ui.wandContiguous)
                }
                applySelection(selection)
            } finally {
                busy = false
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // Degradado, formas y censura por rectangulo
    // ----------------------------------------------------------------------------------

    private fun applyGradient(from: Offset, to: Offset) {
        onRaster { layer, bitmap ->
            val a = toLayer(layer, bitmap, from) ?: return@onRaster
            val b = toLayer(layer, bitmap, to) ?: return@onRaster
            val degrees = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()))
            val spec = ui.gradient.copy(angle = degrees.toFloat())
            ui.gradient = spec
            val copy = BitmapUtils.mutableCopy(bitmap)
            BrushEngine.drawGradient(
                target = copy,
                shader = FillPainter.gradientShader(
                    spec = spec,
                    width = copy.width.toFloat(),
                    height = copy.height.toFloat(),
                ),
                selection = selectionFor(copy),
            )
            vm.replaceRaster(layer.id, copy, R.string.tool_gradient)
        }
    }

    private fun addShape(from: Offset, to: Offset) {
        val rect = rectOf(from, to)
        if (rect.width() < 2f || rect.height() < 2f) return
        val spec = ui.shape.copy(width = rect.width(), height = rect.height())
        ui.shape = spec
        vm.addLayer(
            R.string.shape_title,
            PixaLayer(
                name = str(R.string.layer_shape),
                content = LayerContent.Shape(spec),
                transform = Xf(tx = rect.left, ty = rect.top),
            ),
        )
        // La forma queda seleccionada y la herramienta pasa a Mover: otro arrastre la mueve o
        // la estira, no dibuja una nueva encima por error ("Dibujar otra forma" en el panel).
        ui.tool = CanvasTool.MOVE
    }

    private fun censorRect(from: Offset, to: Offset) {
        val rect = rectOf(from, to)
        val region = Rect(
            rect.left.roundToInt().coerceIn(0, doc.width),
            rect.top.roundToInt().coerceIn(0, doc.height),
            rect.right.roundToInt().coerceIn(0, doc.width),
            rect.bottom.roundToInt().coerceIn(0, doc.height),
        )
        if (region.width() < 4 || region.height() < 4) return
        if (busy) return
        busy = true
        val style = if (ui.privacyStyle == PrivacyStyle.PIXELATE) {
            Retouch.CensorStyle.PIXELATE
        } else {
            Retouch.CensorStyle.BLUR
        }
        scope.launch {
            try {
                val source = vm.compositeNow()
                val patch = Retouch.censorPatch(
                    src = source,
                    region = region,
                    style = style,
                    strength = ui.retouchAmount,
                ) ?: return@launch
                vm.addLayer(
                    R.string.privacy_title,
                    PixaLayer(
                        name = str(R.string.privacy_title),
                        content = LayerContent.Raster(vm.putBitmap(patch)),
                        transform = Xf(
                            tx = region.left.toFloat(),
                            ty = region.top.toFloat(),
                        ),
                    ),
                )
            } finally {
                busy = false
            }
        }
    }

    /** Guarda en la capa de texto seleccionada el trayecto dibujado (requisito 10). */
    private fun applyTextPath(points: List<Offset>) {
        val layer = doc.selected
        val content = layer?.content
        if (layer == null || content !is LayerContent.Text) {
            vm.post(R.string.layer_pick_any)
            return
        }
        val (w, h) = LayerGeometry.intrinsic(layer, rasterizer, doc)
        val matrix = layer.transform.toMatrix(w, h)
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return
        val coords = FloatArray(points.size * 2)
        points.forEachIndexed { index, point ->
            coords[index * 2] = point.x
            coords[index * 2 + 1] = point.y
        }
        inverse.mapPoints(coords)
        vm.updateLayer(R.string.text_on_path, layer.id) {
            it.copy(content = content.copy(spec = content.spec.copy(path = coords.toList())))
        }
    }

    // ----------------------------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------------------------

    private fun onRaster(block: (PixaLayer, Bitmap) -> Unit) {
        val pair = vm.rasterOrWarn() ?: return
        if (pair.first.locked) {
            vm.post(R.string.layer_locked_warning)
            return
        }
        block(pair.first, pair.second)
    }

    /** Bitmap sobre el que pinta el trazo. */
    private fun targetBitmap(layer: PixaLayer?): Bitmap? =
        if (layer == null) null else vm.bitmapOf(layer)

    /**
     * La seleccion solo se puede usar como recorte del trazo si mide igual que el bitmap:
     * [BrushStroke] la pega en el origen, de modo que con tamanos distintos taparia mal.
     */
    private fun selectionFor(bitmap: Bitmap): Selection? =
        state.selection?.takeIf { it.width == bitmap.width && it.height == bitmap.height }

    private fun toLayer(layer: PixaLayer, bitmap: Bitmap, point: Offset): Offset? {
        val matrix = layer.transform.toMatrix(bitmap.width, bitmap.height)
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return null
        val coords = floatArrayOf(point.x, point.y)
        inverse.mapPoints(coords)
        return Offset(coords[0], coords[1])
    }

    private fun inside(point: Offset, bitmap: Bitmap): Boolean =
        point.x >= 0f && point.y >= 0f &&
            point.x < bitmap.width && point.y < bitmap.height

    private fun pickFromComposite(point: Offset): Int? {
        val composite = state.composite ?: return null
        val x = (point.x * composite.width / doc.width).roundToInt()
            .coerceIn(0, composite.width - 1)
        val y = (point.y * composite.height / doc.height).roundToInt()
            .coerceIn(0, composite.height - 1)
        return BrushEngine.pickColorAveraged(composite, x, y, 2)
    }

    private fun rectOf(a: Offset, b: Offset): RectF = RectF(
        min(a.x, b.x).coerceIn(0f, doc.width.toFloat()),
        min(a.y, b.y).coerceIn(0f, doc.height.toFloat()),
        max(a.x, b.x).coerceIn(0f, doc.width.toFloat()),
        max(a.y, b.y).coerceIn(0f, doc.height.toFloat()),
    )
}

/** Lanza el dialogo de impresion del sistema con la imagen ya exportada. */
private fun printImage(context: android.content.Context, file: java.io.File, keepAspect: Boolean) {
    val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        ?: return
    val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
    val attributes = PrintAttributes.Builder()
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .build()
    // Tamano de pagina decidido por el sistema en onLayout; se guarda para pintar encima.
    var pageAttrs = attributes
    printManager.print(
        "Pixa",
        object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?,
            ) {
                if (newAttributes != null) pageAttrs = newAttributes
                val result = PrintDocumentInfo.Builder("pixa_print")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(result, true)
            }

            override fun onWrite(
                pages: Array<PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                runCatching {
                    val doc = PdfDocument()
                    // Tamano de la pagina en puntos (1 pt = 1/72 pulgada). Si el sistema no
                    // informa del papel, se usa la propia proporcion de la imagen.
                    val media = pageAttrs.mediaSize
                    val margins = pageAttrs.minMargins
                    val pageW: Int
                    val pageH: Int
                    if (media != null && margins != null) {
                        pageW = ((media.widthMils - margins.leftMils - margins.rightMils) * 72 / 1000).coerceAtLeast(1)
                        pageH = ((media.heightMils - margins.topMils - margins.bottomMils) * 72 / 1000).coerceAtLeast(1)
                    } else {
                        pageW = bitmap.width
                        pageH = bitmap.height
                    }
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
                    val page = doc.startPage(pageInfo)
                    val target = android.graphics.RectF(0f, 0f, pageInfo.pageWidth.toFloat(), pageInfo.pageHeight.toFloat())
                    val src = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                    val matrix = Matrix()
                    // CENTER mantiene la proporcion (recorta lo que sobra, sin deformar);
                    // FILL estira la imagen para cubrir toda la pagina.
                    matrix.setRectToRect(
                        src,
                        target,
                        if (keepAspect) Matrix.ScaleToFit.CENTER else Matrix.ScaleToFit.FILL,
                    )
                    page.canvas.drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    doc.finishPage(page)
                    doc.writeTo(java.io.FileOutputStream(destination.fileDescriptor))
                    doc.close()
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        },
        attributes,
    )
}

/** Dialogo para elegir el modo de relleno de la pagina antes de imprimir. */
@Composable
private fun PrintModeDialog(
    onProportional: () -> Unit,
    onFill: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_print)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrintOptionCard(
                    label = stringResource(R.string.print_fill_prop),
                    icon = Icons.Outlined.CropFree,
                    modifier = Modifier.weight(1f),
                    onClick = onProportional,
                )
                PrintOptionCard(
                    label = stringResource(R.string.print_fill_full),
                    icon = Icons.Outlined.AspectRatio,
                    modifier = Modifier.weight(1f),
                    onClick = onFill,
                )
            }
        },
        confirmButton = {},
    )
}

/** Tarjeta de opcion del dialogo de impresion: texto a la izquierda e icono a la derecha. */
@Composable
private fun PrintOptionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Marcas del borrador magico manual en rojo translucido: las ya hechas y, mientras se arrastra,
 * la que esta en curso con su forma (rectangulo y elipse se ven como tales antes de soltar).
 */
private fun DrawScope.drawMarks(ui: EditorUi, controller: CanvasController) {
    val red = Color(0xFFFF2D55)
    fun one(shape: MarkShape, pts: List<Offset>, radius: Float) {
        if (pts.isEmpty()) return
        when (shape) {
            MarkShape.BRUSH ->
                if (pts.size == 1) {
                    drawCircle(red.copy(alpha = 0.55f), radius * controller.zoom, controller.docToView(pts[0]))
                } else {
                    drawPath(
                        viewPath(pts, controller),
                        red.copy(alpha = 0.55f),
                        style = Stroke(
                            width = (radius * 2f * controller.zoom).coerceAtLeast(2f),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            MarkShape.RECT, MarkShape.ELLIPSE -> if (pts.size >= 2) {
                val a = controller.docToView(pts.first())
                val b = controller.docToView(pts.last())
                val topLeft = Offset(min(a.x, b.x), min(a.y, b.y))
                val size = Size(abs(b.x - a.x), abs(b.y - a.y))
                if (shape == MarkShape.RECT) {
                    drawRect(red.copy(alpha = 0.45f), topLeft, size)
                } else {
                    drawOval(red.copy(alpha = 0.45f), topLeft, size)
                }
            }
            MarkShape.LASSO -> if (pts.size >= 3) {
                drawPath(viewPath(pts, controller, close = true), red.copy(alpha = 0.45f))
            }
        }
    }
    for (m in ui.marks) one(m.shape, m.points, m.radius)
    val live = ui.livePoints
    if (live.isNotEmpty()) {
        val pts = if (ui.markShape == MarkShape.RECT || ui.markShape == MarkShape.ELLIPSE) {
            listOf(ui.dragStart ?: live.first(), live.last())
        } else {
            live
        }
        one(ui.markShape, pts, ui.brush.size / 2f)
    }
}

/** Separacion minima (px de pantalla) entre la punta de la cola y los tiradores de los lados. */
private const val HANDLE_GAP_PX = 80f
