package com.dskmusic.pixa.ui.editor

import com.dskmusic.pixa.ui.common.panelSwipeToToggle
import com.dskmusic.pixa.ui.common.MinimizedPanelBar
import androidx.compose.material.icons.filled.UnfoldLess
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TagFaces
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaBlend
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.editor.panels.gradientLayer
import com.dskmusic.pixa.ui.common.ColorPickerDialog
import com.dskmusic.pixa.ui.common.ConfirmDialog
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.TextPromptDialog
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.common.rememberCameraCapture
import com.dskmusic.pixa.ui.common.rememberImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Boton flotante de capas que se despliega hacia arriba (requisito 7).
 *
 * Es el acceso rapido: ensena las capas de arriba abajo como en la pila real y permite
 * cambiar de capa o esconderla sin abrir el panel completo. El panel completo sigue a un
 * toque de distancia para lo demas.
 */
@Composable
fun LayersFab(
    doc: PixaDocument,
    vm: EditorViewModel,
    expanded: Boolean,
    alignEnd: Boolean = true,
    onToggleExpanded: () -> Unit,
    onSelect: (String) -> Unit,
    onToggleVisible: (PixaLayer) -> Unit,
    onOpenPanel: () -> Unit,
    onGradientAdded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Alineado al mismo lado que el FAB colapsado: si se centrara, el boton saltaria al
    // desplegarse porque la columna se ensancha con las fichas de capa.
    val side = if (alignEnd) Alignment.End else Alignment.Start
    var deleting by remember { mutableStateOf<PixaLayer?>(null) }
    Column(
        modifier = modifier,
        horizontalAlignment = side,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                horizontalAlignment = side,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Iconos, no texto: anadir capa y abrir el panel completo.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AddLayerMenu(doc = doc, vm = vm, onGradientAdded = onGradientAdded)
                        IconButton(onClick = onOpenPanel) {
                            Icon(Icons.Filled.Tune, stringResource(R.string.layers_panel))
                        }
                    }
                }
                // De arriba abajo igual que la pila: la ultima capa es la de encima.
                doc.layers.asReversed().forEach { layer ->
                    LayerChip(
                        layer = layer,
                        doc = doc,
                        selected = layer.id == doc.selectedLayerId,
                        onClick = { onSelect(layer.id) },
                        onToggleVisible = { onToggleVisible(layer) },
                        onToggleLock = { vm.setLayerLocked(layer.id, !layer.locked) },
                        onDuplicate = { vm.duplicateLayer(layer.id) },
                        onDelete = { deleting = layer },
                        onDragBy = { steps ->
                            val from = doc.indexOf(layer.id)
                            if (from >= 0) vm.moveLayer(from, from - steps)
                        },
                    )
                }
            }
        }
        FloatingActionButton(onClick = onToggleExpanded) {
            Icon(Icons.Filled.Layers, stringResource(R.string.cd_layers_button))
        }
    }

    deleting?.let { layer ->
        ConfirmDialog(
            title = stringResource(R.string.action_confirm_delete),
            body = layer.name,
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { vm.deleteLayer(layer.id) },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * Icono "+" junto al gestor rapido de capas (requisito del usuario): acceso directo a
 * insertar texto, forma o QR sin recorrer toda la barra inferior de herramientas.
 */
@Composable
fun QuickInsertFab(
    ui: EditorUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onInsertEmoji: () -> Unit,
    onInsertStock: () -> Unit,
    onInsertSticker: () -> Unit,
    onInsertBubbles: () -> Unit,
    onInsertGradient: () -> Unit,
    alignEnd: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val side = if (alignEnd) Alignment.End else Alignment.Start
    Column(
        modifier = modifier,
        horizontalAlignment = side,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(horizontalAlignment = side, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Los mas cortos arriba (segun el idioma en uso).
                val entries = listOf(
                    QuickEntry(Icons.Filled.TextFields, stringResource(R.string.text_title)) {
                        ui.openPanelDirect(EditorPanel.TEXT)
                    },
                    QuickEntry(Icons.Filled.TagFaces, stringResource(R.string.text_emoji)) {
                        onInsertEmoji()
                    },
                    QuickEntry(Icons.Filled.Category, stringResource(R.string.tool_shape)) {
                        ui.openPanelDirect(EditorPanel.SHAPES)
                    },
                    QuickEntry(Icons.Filled.QrCode2, stringResource(R.string.qr_title)) {
                        ui.openPanelDirect(EditorPanel.QR)
                    },
                    QuickEntry(Icons.Filled.Brush, stringResource(R.string.draw_title)) {
                        ui.openPanelDirect(EditorPanel.DRAW)
                    },
                    QuickEntry(Icons.Filled.FilterFrames, stringResource(R.string.frames_title)) {
                        ui.openPanelDirect(EditorPanel.FRAMES)
                    },
                    QuickEntry(Icons.Filled.Gradient, stringResource(R.string.label_gradient)) {
                        onInsertGradient()
                    },
                    QuickEntry(Icons.Filled.Collections, stringResource(R.string.stock_images_title)) {
                        onInsertStock()
                    },
                    QuickEntry(Icons.Filled.EmojiEmotions, stringResource(R.string.stickers_title)) {
                        onInsertSticker()
                    },
                    QuickEntry(Icons.Filled.ChatBubble, stringResource(R.string.bubble_add)) {
                        onInsertBubbles()
                    },
                ).sortedBy { it.label.length }.toMutableList().also { list ->
                    // Dibujo siempre justo encima de Formas, aunque su texto sea mas largo.
                    val draw = list.indexOfFirst { it.icon == Icons.Filled.Brush }
                    val shapes = list.indexOfFirst { it.icon == Icons.Filled.Category }
                    if (draw >= 0 && shapes >= 0) {
                        val item = list.removeAt(draw)
                        list.add(list.indexOfFirst { it.icon == Icons.Filled.Category }, item)
                    }
                }
                entries.forEach { QuickInsertItem(it.icon, it.label, it.onClick) }
            }
        }
        FloatingActionButton(
            onClick = onToggleExpanded,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
        }
    }
}

private class QuickEntry(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun QuickInsertItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Boton "anadir capa" con sus cuatro variantes (requisito 7).
 *
 * Lo usan el panel completo y el acceso rapido, de modo que anadir una capa nunca obliga
 * a abrir el panel avanzado. El color del relleno sale del selector global, con su paleta
 * y sus recientes, igual que en el resto de la app.
 */
@Composable
fun AddLayerMenu(
    doc: PixaDocument,
    vm: EditorViewModel,
    modifier: Modifier = Modifier,
    /** Se llama al anadir un degradado: quien lo usa abre sus ajustes. */
    onGradientAdded: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by context.pixa.settings.settings
        .collectAsStateWithLifecycle(PixaSettings())
    var open by remember { mutableStateOf(false) }
    var pickingColor by remember { mutableStateOf(false) }

    // La imagen se decodifica al tamano maximo del documento: una capa mas grande que el
    // lienzo solo gasta memoria.
    val addImage: (Uri) -> Unit = { uri ->
        scope.launch {
            val side = maxOf(doc.width, doc.height).coerceAtLeast(16)
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { BitmapUtils.decodeUri(context, uri, side) }.getOrNull()
            }
            if (bitmap == null) {
                vm.post(R.string.error_load_image)
            } else {
                vm.addRasterLayer(
                    bitmap = bitmap,
                    name = context.getString(R.string.layer_image),
                    labelRes = R.string.layer_add,
                )
            }
        }
    }
    val pickImage = rememberImagePicker { uri -> addImage(uri) }
    // Si la captura se cancela llega null y no se hace nada: no es un error.
    val takePhoto = rememberCameraCapture { uri -> uri?.let(addImage) }

    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, stringResource(R.string.layer_add))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layer_add_empty)) },
                leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null) },
                onClick = {
                    open = false
                    // Un lienzo grande puede no caber en memoria: mejor un aviso que un
                    // cierre de la app.
                    val blank = runCatching {
                        BitmapUtils.create(doc.width, doc.height)
                    }.getOrNull()
                    if (blank == null) {
                        vm.post(R.string.error_generic)
                    } else {
                        vm.addRasterLayer(
                            bitmap = blank,
                            name = context.getString(R.string.layer_add_empty),
                            labelRes = R.string.layer_add,
                        )
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layer_add_solid)) },
                leadingIcon = {
                    Icon(Icons.Filled.FormatColorFill, contentDescription = null)
                },
                onClick = {
                    open = false
                    pickingColor = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.label_gradient)) },
                leadingIcon = { Icon(Icons.Filled.Gradient, contentDescription = null) },
                onClick = {
                    open = false
                    // Capa de degradado (se edita luego con doble toque o desde su panel).
                    vm.addLayer(
                        R.string.layer_add,
                        gradientLayer(context.getString(R.string.label_gradient)),
                    )
                    onGradientAdded()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layer_from_gallery)) },
                leadingIcon = {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                },
                onClick = {
                    open = false
                    pickImage()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layer_from_camera)) },
                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                onClick = {
                    open = false
                    takePhoto()
                },
            )
        }
    }

    if (pickingColor) {
        ColorPickerDialog(
            initial = 0xFFFFFFFF.toInt(),
            palette = settings.palette,
            recents = settings.recentColors,
            withAlpha = false,
            onDismiss = { pickingColor = false },
            onConfirm = { picked ->
                // Una capa de relleno cubre el lienzo entero y no gasta memoria de
                // bitmap: se pinta al vuelo desde el color guardado.
                vm.addLayer(
                    R.string.layer_add,
                    PixaLayer(
                        name = context.getString(R.string.layer_add_solid),
                        content = LayerContent.Fill(FillSpec.Solid(picked)),
                    ),
                )
                scope.launch { context.pixa.settings.pushRecentColor(picked) }
            },
            onAddToPalette = { added ->
                scope.launch { context.pixa.settings.addToPalette(added) }
            },
        )
    }
}

@Composable
private fun LayerChip(
    layer: PixaLayer,
    doc: PixaDocument,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLock: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDragBy: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val chipHeightPx = remember(density) { with(density) { 48.dp.toPx() } }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.border(
            width = if (selected) 2.dp else 0.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp),
        ) {
            // Tirador para reordenar (requisito del usuario): mismo gesto que en el panel
            // completo de capas, acumulado en pasos de una fila por arrastre.
            var accumulated by remember(layer.id) { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(layer.id) {
                        detectDragGestures(
                            onDragEnd = { accumulated = 0f },
                            onDragCancel = { accumulated = 0f },
                        ) { _, delta ->
                            accumulated += delta.y
                            val steps = (accumulated / chipHeightPx).toInt()
                            if (steps != 0) {
                                accumulated -= steps * chipHeightPx
                                onDragBy(steps)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.cd_drag_handle),
                    modifier = Modifier.size(18.dp),
                )
            }
            LayerThumb(
                layer = layer,
                doc = doc,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick),
            )
            IconButton(onClick = onToggleVisible, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = stringResource(R.string.cd_toggle_visibility),
                    modifier = Modifier.size(18.dp),
                )
            }
            // Candado junto al ojo: desbloquear aqui es el camino rapido para volver a
            // tocar una capa que se bloqueo sola al ocultarla.
            IconButton(onClick = onToggleLock, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (layer.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = stringResource(R.string.cd_toggle_lock),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.action_duplicate),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Panel completo de capas (requisito 7).
 *
 * Permite renombrar, cambiar opacidad y modo de fusion, duplicar, borrar, combinar hacia
 * abajo, acoplar, agrupar y recortar con la capa inferior. Se reordena arrastrando el
 * tirador de la izquierda.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayersList(
    doc: PixaDocument,
    vm: EditorViewModel,
    modifier: Modifier = Modifier,
    onGradientAdded: () -> Unit = {},
    /** Si se indica, la cabecera gana un boton (a la derecha del todo) y el gesto de deslizar hacia abajo para minimizar. */
    onMinimize: (() -> Unit)? = null,
) {
    var renaming by remember { mutableStateOf<PixaLayer?>(null) }
    var deleting by remember { mutableStateOf<PixaLayer?>(null) }
    var confirmFlatten by remember { mutableStateOf(false) }
    var multiSelect by remember { mutableStateOf<Set<String>>(emptySet()) }

    val rowHeight = with(LocalDensity.current) { 64.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Deslizar la cabecera hacia abajo minimiza, como en los paneles del editor.
                .then(
                    if (onMinimize != null) {
                        Modifier.panelSwipeToToggle(onSwipeDown = onMinimize, onSwipeUp = {})
                    } else {
                        Modifier
                    },
                )
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.layers_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (multiSelect.size > 1) {
                IconButton(onClick = {
                    vm.groupSelected(multiSelect)
                    multiSelect = emptySet()
                }) {
                    Icon(Icons.Filled.Folder, stringResource(R.string.layer_group))
                }
            }
            AddLayerMenu(doc = doc, vm = vm, onGradientAdded = onGradientAdded)
            IconButton(onClick = { confirmFlatten = true }) {
                Icon(Icons.Filled.MergeType, stringResource(R.string.layer_flatten))
            }
            // A la derecha del todo: minimizar el gestor.
            if (onMinimize != null) {
                IconButton(onClick = onMinimize) {
                    Icon(
                        Icons.Filled.UnfoldLess,
                        contentDescription = stringResource(R.string.action_minimize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.layer_reorder_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            doc.layers.asReversed().forEach { layer ->
              key(layer.id) {
                val group = layer.groupId?.let { id -> doc.groups.firstOrNull { it.id == id } }
                // Al abrir el editor de capas (o al llegar una capa nueva elegida) la fila de la
                // capa seleccionada se desplaza hasta verse entera en el espacio de arriba.
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(Unit) {
                    if (layer.id == doc.selectedLayerId) {
                        delay(150)
                        bringIntoView.bringIntoView()
                    }
                }
                Box(Modifier.bringIntoViewRequester(bringIntoView)) {
                LayerRow(
                    layer = layer,
                    doc = doc,
                    selected = layer.id == doc.selectedLayerId,
                    marked = layer.id in multiSelect,
                    groupName = group?.name,
                    rowHeight = rowHeight,
                    onClick = { vm.selectLayer(layer.id) },
                    onMark = {
                        multiSelect = if (layer.id in multiSelect) {
                            multiSelect - layer.id
                        } else {
                            multiSelect + layer.id
                        }
                    },
                    onToggleVisible = { vm.setLayerVisible(layer.id, !layer.visible) },
                    onToggleLock = { vm.setLayerLocked(layer.id, !layer.locked) },
                    onDuplicate = { vm.duplicateLayer(layer.id) },
                    onDragBy = { steps ->
                        val from = doc.indexOf(layer.id)
                        if (from >= 0) vm.moveLayer(from, from - steps)
                    },
                )
                }
              }
            }

            // Las opciones van detras de la lista entera, no intercaladas: si se dibujan
            // justo debajo de la capa elegida, parten la pila y las demas capas se
            // quedan escondidas por debajo.
            doc.selected?.let { layer ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SelectedLayerControls(
                    layer = layer,
                    doc = doc,
                    vm = vm,
                    onRename = { renaming = layer },
                    onDelete = { deleting = layer },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            doc.groups.firstOrNull { group -> doc.layers.any { it.groupId == group.id } }
                ?.let { group ->
                    TextButton(onClick = { vm.ungroup(group.id) }) {
                        Icon(Icons.Filled.FolderOff, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.layer_ungroup))
                    }
                }
        }
    }

    renaming?.let { layer ->
        TextPromptDialog(
            title = stringResource(R.string.action_rename),
            initial = layer.name,
            onConfirm = { vm.renameLayer(layer.id, it) },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { layer ->
        ConfirmDialog(
            title = stringResource(R.string.action_confirm_delete),
            body = layer.name,
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { vm.deleteLayer(layer.id) },
            onDismiss = { deleting = null },
        )
    }
    if (confirmFlatten) {
        ConfirmDialog(
            title = stringResource(R.string.layer_flatten),
            confirmText = stringResource(R.string.action_apply),
            onConfirm = { vm.flatten() },
            onDismiss = { confirmFlatten = false },
        )
    }
}

@Composable
private fun LayerRow(
    layer: PixaLayer,
    doc: PixaDocument,
    selected: Boolean,
    marked: Boolean,
    groupName: String?,
    rowHeight: Float,
    onClick: () -> Unit,
    onMark: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLock: () -> Unit,
    onDuplicate: () -> Unit,
    onDragBy: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    marked -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El tirador acumula el arrastre y mueve la capa una posicion por cada fila
        // recorrida: asi el reordenado no depende de medir el hueco de la lista.
        var accumulated by remember(layer.id) { mutableStateOf(0f) }
        Box(
            // 48 dp de area tactil aunque el icono sea pequeno: es el tirador que se arrastra.
            modifier = Modifier
                .size(48.dp)
                .pointerInput(layer.id) {
                    detectDragGestures(
                        onDragEnd = { accumulated = 0f },
                        onDragCancel = { accumulated = 0f },
                    ) { _, delta ->
                        accumulated += delta.y
                        val steps = (accumulated / rowHeight).toInt()
                        if (steps != 0) {
                            accumulated -= steps * rowHeight
                            onDragBy(steps)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.cd_drag_handle),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        LayerThumb(
            layer = layer,
            doc = doc,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onMark),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                layer.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = buildList {
                add(formatPercent(layer.opacity))
                if (layer.blend != PixaBlend.NORMAL) add(layer.blend.name.lowercase())
                // Requisito 14: de un vistazo se ve cual no admite pincel ni seleccion.
                if (!layer.isRaster) add(stringResource(R.string.layer_vector_badge))
                if (layer.mask != null) add("mask")
                if (layer.clipToBelow) add("clip")
                groupName?.let { add(it) }
            }.joinToString(" · ")
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleVisible) {
            Icon(
                if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = stringResource(R.string.cd_toggle_visibility),
            )
        }
        IconButton(onClick = onToggleLock) {
            Icon(
                if (layer.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = stringResource(R.string.cd_toggle_lock),
            )
        }
        IconButton(onClick = onDuplicate) {
            Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_duplicate))
        }
    }
}

/** Controles de la capa activa, desplegados bajo su fila para no abrir otro dialogo. */
@Composable
private fun SelectedLayerControls(
    layer: PixaLayer,
    doc: PixaDocument,
    vm: EditorViewModel,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(bottom = 6.dp),
    ) {
        SliderRow(
            label = stringResource(R.string.label_opacity),
            value = layer.opacity,
            onValueChange = { vm.setLayerOpacity(layer.id, it) },
            valueText = formatPercent(layer.opacity),
            defaultValue = 1f,
        )
        Text(
            stringResource(R.string.layer_blend_mode),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
        val context = LocalContext.current
        val blendNames = remember(context) {
            PixaBlend.entries.associateWith { context.getString(EditorLabels.blend(it)) }
        }
        OptionRow(
            options = PixaBlend.entries,
            selected = layer.blend,
            onSelect = { vm.setLayerBlend(layer.id, it) },
            label = { blendNames[it].orEmpty() },
        )
        LayerStyleSection(layer, maxOf(doc.width, doc.height), vm)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, stringResource(R.string.action_rename))
            }
            IconButton(onClick = { vm.duplicateLayer(layer.id) }) {
                Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_duplicate))
            }
            IconButton(
                onClick = { vm.mergeDown(layer.id) },
                enabled = doc.indexOf(layer.id) > 0,
            ) {
                Icon(Icons.Filled.MergeType, stringResource(R.string.layer_merge_down))
            }
            IconButton(
                onClick = {
                    vm.updateLayer(R.string.mask_clip, layer.id) {
                        it.copy(clipToBelow = !it.clipToBelow)
                    }
                },
                enabled = doc.indexOf(layer.id) > 0,
            ) {
                Icon(
                    Icons.Filled.ContentCut,
                    stringResource(R.string.mask_clip),
                    modifier = Modifier.alpha(if (layer.clipToBelow) 1f else 0.5f),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
            }
        }
    }
}

/**
 * Miniatura de una capa.
 *
 * Se usan los pixeles que ya tiene el rasterizador en cache en vez de recomponer la capa
 * sobre el lienzo: la lista de capas se redibuja en cada cambio de opacidad y volver a
 * montar un render completo por fila seria inaceptable.
 */
@Composable
fun LayerThumb(layer: PixaLayer, doc: PixaDocument, modifier: Modifier = Modifier) {
    val rasterizer = LocalContext.current.pixa.rasterizer
    val textContent = layer.content as? LayerContent.Text
    val bitmap = remember(layer.id, layer.content) {
        runCatching {
            // Requisito del usuario: en texto, siempre negro sobre blanco en la
            // miniatura, sin importar el color/estilo real de la capa (en modo oscuro se
            // volvia ilegible si el texto llevaba un color oscuro o claro cualquiera).
            if (textContent != null) {
                rasterizer.rasterizeTextPreview(textContent.spec)
            } else {
                rasterizer.rasterize(layer)
            }
        }.getOrNull()
    }
    // Los degradados se ven tal cual en la miniatura (con su tipo, colores y angulo).
    val gradientThumb = remember(layer.content) {
        val gradient = (layer.content as? LayerContent.Fill)?.spec
            as? com.dskmusic.pixa.core.model.FillSpec.Gradient
        gradient?.let { g ->
            runCatching {
                val side = 96
                val bmp = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
                val paint = com.dskmusic.pixa.core.render.FillPainter.paintFor(g, side.toFloat(), side.toFloat(), null)
                if (paint != null) {
                    android.graphics.Canvas(bmp).drawRect(0f, 0f, side.toFloat(), side.toFloat(), paint)
                }
                bmp
            }.getOrNull()
        }
    }
    val fillColor = (layer.content as? LayerContent.Fill)?.let { content ->
        (content.spec as? com.dskmusic.pixa.core.model.FillSpec.Solid)?.color
    }
    Box(
        modifier = modifier.background(
            if (textContent != null) Color.White else MaterialTheme.colorScheme.surfaceVariant,
        ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null && !bitmap.isRecycled -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = layer.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            gradientThumb != null -> Image(
            bitmap = gradientThumb.asImageBitmap(),
            contentDescription = layer.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        fillColor != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(fillColor)),
            )

            else -> Icon(
                Icons.Filled.Layers,
                contentDescription = layer.name,
                modifier = Modifier.size(18.dp),
            )
        }
        if (!doc.isEffectivelyVisible(layer)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            )
        }
    }
}

/**
 * Panel completo de capas como parte del diseno: ocupa el sitio del panel y la barra de
 * herramientas, y el lienzo se reduce al espacio que queda arriba (sin tapar nada), para
 * ver los cambios mientras se editan las capas.
 */
@Composable
fun LayersSheet(
    doc: PixaDocument,
    vm: EditorViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onGradientAdded: () -> Unit = {},
    minimized: Boolean = false,
    onMinimizedChange: (Boolean) -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        if (minimized) {
            // Barra fina: se maximiza tocandola, con su boton o deslizando hacia arriba.
            MinimizedPanelBar(
                text = stringResource(R.string.layers_title),
                onMaximize = { onMinimizedChange(false) },
                onClose = onDismiss,
            )
            return@Surface
        }
        Column {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                LayersList(
                    doc = doc,
                    vm = vm,
                    onGradientAdded = onGradientAdded,
                    onMinimize = { onMinimizedChange(true) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}

/** Panel de capas en superposicion, con velo para cerrarlo tocando fuera. */
@Composable
fun LayersOverlay(
    doc: PixaDocument,
    vm: EditorViewModel,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                LayersList(doc = doc, vm = vm)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}
