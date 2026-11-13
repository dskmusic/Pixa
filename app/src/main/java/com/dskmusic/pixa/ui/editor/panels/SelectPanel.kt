package com.dskmusic.pixa.ui.editor.panels

import android.graphics.Rect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.image.Cutout
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.selection.Selection
import com.dskmusic.pixa.core.selection.SelectionTools
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ml.MediaPipeCutout
import com.dskmusic.pixa.ml.SubjectCutout
import com.dskmusic.pixa.ml.U2NetCutout
import com.dskmusic.pixa.ui.common.IntSliderRow
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.ToolButton
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.editor.CanvasTool
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorUi
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.LayerGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================================================
// Seleccion (requisito 8)
// ======================================================================================

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun SelectPanel(vm: EditorViewModel, selection: Selection?, ui: EditorUi, settings: PixaSettings) {
    // Al salir del panel la seleccion se desactiva: si no, el marco de hormigas se queda
    // puesto y visible aunque ya no haya nada que lo use (requisito del usuario).
    // La lupa arranca activa en este panel para ver con precision lo que se selecciona; se
    // puede apagar desde aqui y al salir vuelve a como estaba.
    DisposableEffect(Unit) {
        val previousMagnifier = ui.magnifier
        ui.magnifier = true
        onDispose {
            ui.magnifier = previousMagnifier
            vm.samEnd()
            vm.setSelection(null)
        }
    }

    val context = LocalContext.current
    val pasteName = stringResource(R.string.layer_image)
    var amount by remember { mutableStateOf(8) }
    var pendingAi by remember { mutableStateOf<AiAction?>(null) }
    pendingAi?.let { action ->
        AiPreflightOrRun(
            action = action,
            onRun = {
                pendingAi = null
                vm.runAi(context, action)
            },
            onDismiss = { pendingAi = null },
        )
    }
    val modeNames = remember(context) {
        Selection.Mode.entries.associateWith { context.getString(EditorLabels.selectionMode(it)) }
    }
    // Orden pedido: los tres lazos, IA, varita, forma basica y, por ultimo, por color.
    val tools = listOf(
        Triple(CanvasTool.SELECT_LASSO, Icons.Filled.Gesture, R.string.select_lasso),
        Triple(CanvasTool.SELECT_MAGNETIC, Icons.Filled.AutoFixHigh, R.string.select_magnetic),
        Triple(CanvasTool.SELECT_POLY, Icons.Filled.Polyline, R.string.select_poly),
        Triple(CanvasTool.AI_SELECT, Icons.Filled.TouchApp, R.string.select_ai),
        Triple(CanvasTool.SELECT_WAND, Icons.Filled.AutoFixHigh, R.string.select_wand),
        Triple(CanvasTool.SELECT_RECT, Icons.Filled.Square, R.string.select_rect),
        Triple(CanvasTool.SELECT_ELLIPSE, Icons.Filled.Circle, R.string.select_ellipse),
        Triple(CanvasTool.SELECT_BY_COLOR, Icons.Filled.Colorize, R.string.select_by_color),
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            tools.forEach { (tool, icon, label) ->
                ToolButton(
                    icon = icon,
                    label = stringResource(label),
                    selected = ui.tool == tool,
                    width = 66.dp,
                    onClick = {
                        // IA: analiza la imagen una vez (con aviso si falta el modelo) y
                        // despues cada toque o trazo alrededor es instantaneo.
                        if (tool == CanvasTool.AI_SELECT && !vm.samActive) {
                            pendingAi = selectAction(vm, ui)
                            return@ToolButton
                        }
                        ui.tool = tool
                        ui.resetPolygon()
                        // El recuadro de seleccion es libre: las proporciones fijas son
                        // cosa del panel de recorte.
                        ui.boxRatio = null
                    },
                )
            }
        }
        OptionRow(
            options = Selection.Mode.entries,
            selected = ui.selectionMode,
            onSelect = { ui.selectionMode = it },
            label = { modeNames[it].orEmpty() },
        )
        if (ui.tool == CanvasTool.SELECT_MAGNETIC) PanelHint(R.string.select_magnetic_hint)
        if (ui.tool == CanvasTool.AI_SELECT) {
            PanelHint(R.string.select_ai_hint)
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = ui.aiPositive,
                    onClick = { ui.aiPositive = true },
                    label = { Text(stringResource(R.string.ai_sam_add)) },
                    leadingIcon = { Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = !ui.aiPositive,
                    onClick = { ui.aiPositive = false },
                    label = { Text(stringResource(R.string.ai_sam_remove)) },
                    leadingIcon = { Icon(Icons.Filled.RemoveCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                TextButton(onClick = { vm.samClear() }, enabled = vm.samPointCount > 0 || selection != null) {
                    Text(stringResource(R.string.ai_sam_clear))
                }
            }
        }
        if (ui.tool == CanvasTool.SELECT_POLY) {
            PanelHint(R.string.select_poly_hint)
            PanelActions {
                TextButton(
                    enabled = ui.polyPoints.isNotEmpty(),
                    onClick = { ui.polyPoints = ui.polyPoints.dropLast(1) },
                ) { Text(stringResource(R.string.select_undo_point)) }
                TextButton(
                    enabled = ui.polyPoints.size >= 3,
                    onClick = {
                        val coords = ui.polyPoints.flatMap { listOf(it.x, it.y) }
                        vm.combineSelection(
                            SelectionTools.polygon(vm.doc.width, vm.doc.height, coords),
                            ui.selectionMode,
                        )
                        ui.resetPolygon()
                        ui.resetGesture()
                    },
                ) { Text(stringResource(R.string.select_close_shape)) }
            }
        }
        if (ui.tool == CanvasTool.SELECT_WAND || ui.tool == CanvasTool.SELECT_BY_COLOR) {
            SliderRow(
                label = stringResource(R.string.label_tolerance),
                value = ui.wandTolerance,
                onValueChange = { ui.wandTolerance = it },
                valueRange = 0f..0.8f,
                valueText = formatPercent(ui.wandTolerance),
                defaultValue = 0.12f,
            )
            if (ui.tool == CanvasTool.SELECT_WAND) {
                ToggleRow(
                    label = stringResource(R.string.select_contiguous),
                    checked = ui.wandContiguous,
                    onCheckedChange = { ui.wandContiguous = it },
                )
            }
        }

        // Seleccion rapida: tres botones iguales en una sola fila.
        SelRow {
            SelButton(R.string.action_select_all, true) { vm.selectAll() }
            SelButton(R.string.select_deselect, selection != null) { vm.clearSelection() }
            SelButton(R.string.select_invert, selection != null) { vm.invertSelection() }
        }
        // Borde: la cantidad va pegada a los tres botones que la usan.
        IntSliderRow(
            label = stringResource(R.string.label_amount),
            value = amount,
            onValueChange = { amount = it },
            range = 1..64,
            suffix = " px",
            defaultValue = 8,
        )
        SelRow {
            SelButton(R.string.select_expand, selection != null) {
                selection?.let { vm.setSelection(it.grown(amount)) }
            }
            SelButton(R.string.select_contract, selection != null) {
                selection?.let { vm.setSelection(it.grown(-amount)) }
            }
            SelButton(R.string.select_feather, selection != null) {
                selection?.let { vm.setSelection(it.feathered(amount.toFloat())) }
            }
        }
        // Edicion sobre lo seleccionado: chips compactos que fluyen en las lineas que haga falta.
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SelChip(R.string.select_copy, selection != null) {
                selection?.let { copySelection(vm, it, cut = false) }
            }
            SelChip(R.string.select_cut, selection != null) {
                selection?.let { copySelection(vm, it, cut = true) }
            }
            SelChip(R.string.select_paste_as_layer, vm.clipboard != null) {
                pasteClipboard(vm, selection, pasteName)
            }
            SelChip(R.string.select_duplicate_layer, selection != null) {
                selection?.let { duplicateSelection(vm, it, pasteName) }
            }
            SelChip(R.string.select_fill, selection != null) {
                val sel = selection ?: return@SelChip
                val color = ui.color
                vm.applyToRasterLayer(R.string.select_fill) { layer, bitmap ->
                    SelectionTools.fill(bitmap, sel, color, LayerGeometry.docToLayer(layer, bitmap))
                }
            }
            SelChip(R.string.action_delete, selection != null) {
                val sel = selection ?: return@SelChip
                vm.applyToRasterLayer(R.string.action_delete) { layer, bitmap ->
                    SelectionTools.erase(bitmap, sel, LayerGeometry.docToLayer(layer, bitmap))
                }
            }
            SelChip(R.string.select_crop_to, selection != null) {
                selection?.let { cropToSelection(vm, it) }
            }
        }
        if (selection == null) {
            Text(
                stringResource(R.string.select_none_active),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        // El color de relleno es el color activo del editor, el mismo que usan pincel y
        // texto; se cambia desde aqui para no tener que abrir el panel de dibujo.
        ColorRow(
            label = stringResource(R.string.label_color),
            color = ui.color,
            settings = settings,
            onPick = { ui.color = it },
        )
    }
}

@Composable
private fun SelRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SelButton(
    @androidx.annotation.StringRes res: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).height(36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(
            stringResource(res),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SelChip(@androidx.annotation.StringRes res: Int, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(stringResource(res), style = MaterialTheme.typography.labelMedium) },
    )
}

/** Copia (o corta) el contenido seleccionado de la capa activa. */
private fun copySelection(vm: EditorViewModel, selection: Selection, cut: Boolean) {
    val (layer, bitmap) = vm.rasterOrWarn() ?: return
    // La seleccion esta en coordenadas del documento: para recortar los pixeles de la capa
    // hay que ir y volver por su transformacion, o el trozo saldria de otro sitio.
    val patch = SelectionTools.extract(bitmap, selection, LayerGeometry.layerToDoc(layer, bitmap))
    if (patch == null) {
        vm.post(R.string.select_none_active)
        return
    }
    vm.clipboard = patch
    if (cut) {
        vm.replaceRaster(
            layer.id,
            SelectionTools.erase(bitmap, selection, LayerGeometry.docToLayer(layer, bitmap)),
            R.string.select_cut,
        )
    }
}

/**
 * Copia lo seleccionado a una capa nueva en el mismo sitio, en un solo paso. Funciona
 * tambien sobre la capa base de una imagen abierta (solo bloqueada para mover/escalar).
 */
private fun duplicateSelection(vm: EditorViewModel, selection: Selection, name: String) {
    val (layer, bitmap) = vm.rasterOrWarn() ?: return
    val patch = SelectionTools.extract(bitmap, selection, LayerGeometry.layerToDoc(layer, bitmap))
    if (patch == null) {
        vm.post(R.string.select_none_active)
        return
    }
    addPatchLayer(vm, patch, selection.bounds, name)
}

/** Pega el portapapeles como capa nueva, en el sitio de donde se copio. */
private fun pasteClipboard(vm: EditorViewModel, selection: Selection?, name: String) {
    val patch = vm.clipboard ?: return
    addPatchLayer(vm, patch, selection?.bounds, name)
}

private fun addPatchLayer(vm: EditorViewModel, patch: android.graphics.Bitmap, bounds: Rect?, name: String) {
    val layer = PixaLayer(
        id = PixaLayer.newId(),
        name = name,
        content = LayerContent.Raster(vm.putBitmap(patch)),
        transform = Xf(
            tx = bounds?.left?.toFloat() ?: ((vm.doc.width - patch.width) / 2f),
            ty = bounds?.top?.toFloat() ?: ((vm.doc.height - patch.height) / 2f),
        ),
    )
    vm.addLayer(R.string.select_paste_as_layer, layer)
}

/** Recorta el documento a la seleccion; la logica de recorte vive en [cropDocument]. */
private fun cropToSelection(vm: EditorViewModel, selection: Selection) {
    cropDocument(vm, Rect(selection.bounds))
    vm.clearSelection()
}

// ======================================================================================
// Fondos (requisito 8)
// ======================================================================================

/**
 * Metodos de recorte automatico (requisito del usuario): ML Kit ya estaba, descargado
 * aparte por Play Services; MediaPipe y el recorte general van empaquetados en el APK, asi
 * que no necesitan descarga ni tienen estado de "no disponible" propio.
 */
private enum class BgMethod { ML_KIT, MEDIAPIPE, U2NET }

@Composable
fun BackgroundPanel(vm: EditorViewModel, ui: EditorUi, settings: PixaSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf(BgMethod.ML_KIT) }
    var availability by remember { mutableStateOf<SubjectCutout.Availability?>(null) }
    var downloading by remember { mutableStateOf<Int?>(null) }
    var feather by remember { mutableStateOf(1.5f) }
    // ponytail: la tolerancia vive en EditorUi y no aqui porque la herramienta
    // BG_PICK_COLOR del lienzo tiene que leer el mismo valor que mueve el slider.

    LaunchedEffect(Unit) {
        availability = SubjectCutout.availability(context)
    }

    val picker = rememberImagePicker { uri ->
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { BitmapUtils.decodeUri(context, uri, 4096) }.getOrNull()
            }
            if (bitmap == null) {
                vm.post(R.string.error_load_image)
            } else {
                addBackgroundLayer(
                    vm,
                    PixaLayer(
                        id = PixaLayer.newId(),
                        name = context.getString(R.string.label_background),
                        content = LayerContent.Raster(vm.putBitmap(bitmap)),
                        transform = Xf(
                            tx = (vm.doc.width - bitmap.width) / 2f,
                            ty = (vm.doc.height - bitmap.height) / 2f,
                        ),
                    ),
                )
            }
        }
    }

    Column {
        // Recorte automatico con modelos, parametros y vista previa: ver BgAutoSection.
        BgAutoSection(vm, ui, settings)

        PanelActions {
            TextButton(
                onClick = {
                    val tol = ui.wandTolerance
                    vm.applyToRaster(R.string.bg_remove_white) {
                        Cutout.eraseColor(it, 0xFFFFFFFF.toInt(), tol)
                    }
                },
            ) { Text(stringResource(R.string.bg_remove_white)) }
            TextButton(
                onClick = { ui.tool = CanvasTool.BG_PICK_COLOR },
            ) { Text(stringResource(R.string.bg_remove_by_color)) }
        }
        if (ui.tool == CanvasTool.BG_PICK_COLOR) {
            Text(
                stringResource(R.string.bg_tap_color_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        SliderRow(
            label = stringResource(R.string.label_tolerance),
            value = ui.wandTolerance,
            onValueChange = { ui.wandTolerance = it },
            valueRange = 0f..0.8f,
            valueText = formatPercent(ui.wandTolerance),
            defaultValue = 0.18f,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolButton(
                icon = Icons.Filled.Gesture,
                label = stringResource(R.string.bg_brush_erase),
                selected = ui.tool == CanvasTool.BG_ERASE,
                onClick = { ui.tool = CanvasTool.BG_ERASE },
            )
            ToolButton(
                icon = Icons.Filled.AutoFixHigh,
                label = stringResource(R.string.bg_brush_restore),
                selected = ui.tool == CanvasTool.BG_RESTORE,
                onClick = { ui.tool = CanvasTool.BG_RESTORE },
            )
            ToolButton(
                icon = Icons.Filled.Image,
                label = stringResource(R.string.bg_change),
                onClick = { picker() },
            )
            ToolButton(
                icon = Icons.Filled.Crop,
                label = stringResource(R.string.canvas_bg_transparent),
                onClick = { vm.setBackground(CanvasBackground(transparent = true)) },
            )
        }
        IntSliderRow(
            label = stringResource(R.string.label_size),
            value = ui.brush.size.toInt(),
            onValueChange = { ui.brush = ui.brush.copy(size = it.toFloat()) },
            range = 4..200,
            suffix = " px",
            defaultValue = 24,
        )
        ColorRow(
            label = stringResource(R.string.canvas_bg_color),
            color = ui.color,
            settings = settings,
            onPick = { picked ->
                ui.color = picked
                addBackgroundLayer(
                    vm,
                    PixaLayer(
                        id = PixaLayer.newId(),
                        name = context.getString(R.string.label_background),
                        content = LayerContent.Fill(FillSpec.Solid(picked)),
                    ),
                )
            },
            withAlpha = false,
        )
    }
}

/** Inserta la capa debajo de todas: es lo que significa "cambiar el fondo". */
private fun addBackgroundLayer(vm: EditorViewModel, layer: PixaLayer) {
    vm.edit(R.string.bg_change) { doc ->
        doc.copy(layers = listOf(layer) + doc.layers, selectedLayerId = layer.id)
    }
}
