package com.dskmusic.pixa.ui.wordart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.wordart.WordArtCredits
import com.dskmusic.pixa.ui.common.BusyOverlay
import com.dskmusic.pixa.ui.common.ConfirmDialog
import com.dskmusic.pixa.ui.common.FullscreenImageDialog
import com.dskmusic.pixa.ui.common.PixaToastHost
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.launch

/**
 * Nube de palabras en forma: vista previa arriba, seis pestanas debajo (forma, palabras,
 * colores y fuentes, fondo, efectos y presets). El resultado se exporta como PNG o pasa al
 * editor como una capa de imagen.
 */
@Composable
fun WordArtScreen(
    editor: EditorViewModel,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    vm: WordArtViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val cfg by vm.config.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    // Solo para repintar los botones de deshacer y rehacer cuando cambia el historial.
    val historyTick by vm.canUndo.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var creditsDialog by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    // Hay cambios cuando el historial tiene algun paso: se pide confirmacion al salir.
    val dirty = historyTick >= 0 && vm.hasUndo
    BackHandler(enabled = dirty) { confirmExit = true }

    val title = stringResource(R.string.wa_title)
    val savedMsg = stringResource(R.string.wa_saved)
    val errorMsg = stringResource(R.string.wa_export_error)
    val msg: (Int) -> Unit = { res -> scope.launch { snackbar.showSnackbar(vm.msgText(res)) } }

    Scaffold(
        snackbarHost = { PixaToastHost(snackbar) },
        topBar = {
            PixaTopBar(
                title = title,
                onBack = { if (dirty) confirmExit = true else onBack() },
                actions = {
                    IconButton(enabled = historyTick >= 0 && vm.hasUndo, onClick = vm::undo) {
                        Icon(Icons.Filled.Undo, stringResource(R.string.action_undo))
                    }
                    IconButton(enabled = historyTick >= 0 && vm.hasRedo, onClick = vm::redo) {
                        Icon(Icons.Filled.Redo, stringResource(R.string.action_redo))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.wa_reset)) },
                                onClick = { menu = false; vm.reset() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.wa_credits)) },
                                onClick = { menu = false; creditsDialog = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                // ---- vista previa
                Box(
                    Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(cfg.aspect, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = preview != null) { fullscreen = true },
                    ) {
                        Checker(Modifier.fillMaxSize())
                        preview?.let {
                            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                }
                // ---- acciones
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::randomize) { Icon(Icons.Filled.Shuffle, stringResource(R.string.wa_surprise)) }
                    IconButton(onClick = vm::regenerate) { Icon(Icons.Filled.Casino, stringResource(R.string.wa_regenerate)) }
                    IconButton(enabled = preview != null && !busy, onClick = { exportDialog = true }) {
                        Icon(Icons.Filled.Download, stringResource(R.string.action_export))
                    }
                    IconButton(enabled = preview != null && !busy, onClick = {
                        scope.launch {
                            busy = true
                            val bm = runCatching { vm.renderFull(1600) }.getOrNull()
                            busy = false
                            if (bm == null) {
                                snackbar.showSnackbar(errorMsg)
                            } else {
                                val layer = PixaLayer(name = title, content = LayerContent.Raster(editor.putBitmap(bm)))
                                editor.install(
                                    PixaDocument(
                                        width = bm.width,
                                        height = bm.height,
                                        background = CanvasBackground(transparent = true, color = 0),
                                        layers = listOf(layer),
                                        selectedLayerId = layer.id,
                                    ),
                                    projectId = null,
                                    name = title,
                                    initialLabel = R.string.history_initial,
                                )
                                onOpenEditor()
                            }
                        }
                    }) { Icon(Icons.Filled.Layers, stringResource(R.string.wa_open_editor)) }
                }

                // ---- pestanas
                val tabs = listOf(
                    R.string.wa_tab_shape, R.string.wa_tab_words, R.string.wa_tab_colors,
                    R.string.wa_tab_background, R.string.wa_tab_effects, R.string.wa_tab_presets,
                )
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    tabs.forEachIndexed { i, r ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r), maxLines = 1) })
                    }
                }
                if (tab == 0) {
                    // La rejilla de formas tiene su propio scroll.
                    ShapeTab(vm, cfg, msg, Modifier.weight(1f))
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        when (tab) {
                            1 -> WordsTab(vm, cfg)
                            2 -> ColorsTab(vm, cfg)
                            3 -> BackgroundTab(vm, cfg, msg)
                            4 -> EffectsTab(vm, cfg)
                            else -> PresetsTab(vm, cfg, msg)
                        }
                    }
                }
            }
            BusyOverlay(visible = busy || (working && preview == null))
        }
    }

    if (confirmExit) {
        ConfirmDialog(
            title = stringResource(R.string.wa_exit_title),
            body = stringResource(R.string.wa_exit_body),
            confirmText = stringResource(R.string.wa_exit_confirm),
            dismissText = stringResource(R.string.wa_exit_stay),
            onConfirm = { confirmExit = false; onBack() },
            onDismiss = { confirmExit = false },
        )
    }

    if (fullscreen) preview?.let { FullscreenImageDialog(it, null) { fullscreen = false } }

    if (exportDialog) {
        var choice by remember { mutableIntStateOf(1) }
        val sizes = listOf(1080, 2048, 2560)
        AlertDialog(
            onDismissRequest = { exportDialog = false },
            title = { Text(stringResource(R.string.wa_export_title)) },
            text = {
                Column {
                    sizes.forEachIndexed { i, px ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = choice == i, role = Role.RadioButton) { choice = i },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == i, onClick = null)
                            Text("  $px px", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        stringResource(R.string.wa_export_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exportDialog = false
                    val px = sizes[choice]
                    scope.launch {
                        busy = true
                        val ok = vm.saveToGallery(px)
                        busy = false
                        snackbar.showSnackbar(if (ok) savedMsg else errorMsg)
                    }
                }) { Text(stringResource(R.string.action_export)) }
            },
            dismissButton = { TextButton(onClick = { exportDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (creditsDialog) {
        AlertDialog(
            onDismissRequest = { creditsDialog = false },
            title = { Text(stringResource(R.string.wa_credits)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.wa_credits_intro), style = MaterialTheme.typography.bodySmall)
                    WordArtCredits.lines.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { creditsDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
