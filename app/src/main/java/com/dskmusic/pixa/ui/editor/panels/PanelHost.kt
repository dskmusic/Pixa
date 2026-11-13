package com.dskmusic.pixa.ui.editor.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.pixa
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.dskmusic.pixa.R
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import com.dskmusic.pixa.core.selection.SelectionTools
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ui.common.MinimizedPanelBar
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.editor.EditorPanel
import com.dskmusic.pixa.ui.editor.EditorState
import com.dskmusic.pixa.ui.editor.EditorUi
import com.dskmusic.pixa.ui.editor.EditorViewModel

/**
 * Contenido del panel abierto (requisitos 1 y 25).
 *
 * El desplazamiento vertical vive aqui y no dentro de cada panel: asi ninguno tiene que
 * repetirlo y todos empiezan con el mismo titulo y el mismo boton de ayuda contextual.
 * Cada panel recibe solo lo que necesita, de modo que recomponer el documento no vuelve a
 * dibujar los paneles que no lo miran.
 */
@Composable
fun PanelHost(
    panel: EditorPanel,
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUi,
    settings: PixaSettings,
    modifier: Modifier = Modifier,
) {
    // Un estado de desplazamiento por panel: al cambiar de panel se empieza arriba, que
    // es lo que se espera, en vez de heredar la posicion del panel anterior.
    val scroll = remember(panel) { ScrollState(0) }
    val doc = state.doc
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Ajustes, Filtros, Efectos, Curvas y Recortar gestionan su propia cabecera
    // (maximizada y minimizada) y su propio desplazamiento, porque son los que tienen un
    // "Aplicar" que depende de su estado interno y necesitan seguir mostrandolo incluso
    // minimizados (requisito del usuario: aplicar un cambio pendiente sin desplegar el
    // panel solo para eso). El resto sigue con la cabecera y el desplazamiento genericos.
    val ownHeader = panel == EditorPanel.ADJUST || panel == EditorPanel.FILTERS ||
        panel == EditorPanel.EFFECTS || panel == EditorPanel.TONE ||
        panel == EditorPanel.CROP

    // Quitar fondo: "Aplicar" en la cabecera guarda la vista previa en curso y cierra el panel.
    val applyBg: (() -> Unit)? = when (panel) {
        EditorPanel.BACKGROUND -> ({
            vm.pendingApply?.invoke()
            ui.closePanel()
        })
        // Degradado: los cambios ya estan aplicados; confirmar cierra el panel y deselecciona.
        // Paneles de anadir elemento (texto, dibujo, formas, QR, marcos, bocadillo, degradado):
        // los cambios ya estan aplicados; confirmar cierra el panel y deselecciona.
        EditorPanel.TEXT, EditorPanel.DRAW, EditorPanel.SHAPES, EditorPanel.QR,
        EditorPanel.FRAMES, EditorPanel.BUBBLE, EditorPanel.GRADIENT -> ({
            // Marcos: aplica antes la vista previa en curso (en el resto no hay ninguna).
            vm.pendingApply?.invoke()
            ui.closePanel()
            vm.selectLayer(null)
        })
        // Seleccion: confirmar cierra el poligono en curso, conserva la seleccion y
        // vuelve a desplegar el panel para trabajar con ella.
        EditorPanel.SELECT -> ({
            if (ui.polyPoints.size >= 3) {
                val coords = ui.polyPoints.flatMap { listOf(it.x, it.y) }
                vm.combineSelection(
                    SelectionTools.polygon(vm.doc.width, vm.doc.height, coords),
                    ui.selectionMode,
                )
                ui.resetPolygon()
                ui.resetGesture()
            }
            ui.panelMinimized = false
        })
        else -> null
    }
    // Seleccion: boton pequeno a la izquierda de "Aplicar" que descarta la seleccion activa
    // y el poligono a medias para empezar de nuevo sin abrir el panel.
    val resetSelection: (@Composable () -> Unit)? = if (panel == EditorPanel.SELECT) {
        {
            IconButton(
                onClick = {
                    vm.clearSelection()
                    ui.resetPolygon()
                    ui.resetGesture()
                },
                enabled = state.selection != null || ui.polyPoints.isNotEmpty(),
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.select_deselect),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    } else {
        null
    }
    // Bocadillo: anadir uno (sin deteccion de caras) y borrar el elegido, en la cabecera.
    val bubbleActions: (@Composable () -> Unit)? = if (panel == EditorPanel.BUBBLE) {
        {
            val sample = stringResource(R.string.comic_default_text)
            val name = stringResource(R.string.bubble_title)
            val bubbleLayer = doc.selected?.takeIf { it.content is LayerContent.SpeechBubble }
            IconButton(
                onClick = {
                    scope.launch {
                        val app = context.pixa
                        insertBubbles(vm, app.rasterizer, app.fonts, sample, name, detectFaces = false)
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.comic_add_bubble), Modifier.size(20.dp))
            }
            IconButton(
                onClick = { bubbleLayer?.let { vm.deleteLayer(it.id) } },
                enabled = bubbleLayer != null,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), Modifier.size(20.dp))
            }
        }
    } else {
        resetSelection
    }
    val applyEnabled = when (panel) {
        EditorPanel.BACKGROUND -> vm.previewActive
        EditorPanel.SELECT -> state.selection != null || ui.polyPoints.size >= 3
        else -> true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (ownHeader) {
            when (panel) {
                EditorPanel.ADJUST -> AdjustPanel(
                    vm, doc,
                    title = stringResource(panel.titleRes),
                    help = stringResource(panel.helpRes),
                    onClose = ui::closePanel,
                    onMinimize = { ui.panelMinimized = true },
                    minimized = ui.panelMinimized,
                    onMaximize = { ui.panelMinimized = false },
                    scroll = scroll,
                    onOpenAi = { ui.openPanelDirect(EditorPanel.AI) },
                )
                EditorPanel.FILTERS -> FilterPanel(
                    vm, doc,
                    title = stringResource(panel.titleRes),
                    help = stringResource(panel.helpRes),
                    onClose = ui::closePanel,
                    onMinimize = { ui.panelMinimized = true },
                    minimized = ui.panelMinimized,
                    onMaximize = { ui.panelMinimized = false },
                    scroll = scroll,
                )
                EditorPanel.EFFECTS -> EffectPanel(
                    vm, doc, settings,
                    title = stringResource(panel.titleRes),
                    help = stringResource(panel.helpRes),
                    onClose = ui::closePanel,
                    onMinimize = { ui.panelMinimized = true },
                    minimized = ui.panelMinimized,
                    onMaximize = { ui.panelMinimized = false },
                    scroll = scroll,
                )
                EditorPanel.TONE -> TonePanel(
                    vm, doc, settings,
                    title = stringResource(panel.titleRes),
                    help = stringResource(panel.helpRes),
                    onClose = ui::closePanel,
                    onMinimize = { ui.panelMinimized = true },
                    minimized = ui.panelMinimized,
                    onMaximize = { ui.panelMinimized = false },
                    scroll = scroll,
                )
                EditorPanel.CROP -> CropPanel(
                    vm, doc, ui,
                    title = stringResource(panel.titleRes),
                    help = stringResource(panel.helpRes),
                    onClose = ui::closePanel,
                    onMinimize = { ui.panelMinimized = true },
                    minimized = ui.panelMinimized,
                    onMaximize = { ui.panelMinimized = false },
                    scroll = scroll,
                )
                else -> Unit
            }
        } else {
            // Requisito del usuario: minimizar a una barra fina sin cerrar el panel,
            // para seguir usando la herramienta activa con mas sitio de lienzo.
            if (ui.panelMinimized) {
                MinimizedPanelBar(
                    text = stringResource(panel.titleRes),
                    onMaximize = { ui.panelMinimized = false },
                    onClose = ui::closePanel,
                    onApply = applyBg,
                    applyEnabled = applyEnabled,
                    extra = bubbleActions,
                )
            }
            // El contenido de verdad se sigue montando aunque este minimizado (solo con
            // alto cero): asi no se pierde su estado si lo tuviera.
            Column(
                modifier = (if (ui.panelMinimized) Modifier.height(0.dp) else Modifier)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(bottom = 4.dp),
            ) {
                SectionTitle(
                    text = stringResource(panel.titleRes),
                    help = stringResource(panel.helpRes),
                    onClose = ui::closePanel,
                    onApply = applyBg,
                    applyEnabled = applyEnabled,
                    extra = bubbleActions,
                    onMinimize = { ui.panelMinimized = true },
                )
                when (panel) {
                    EditorPanel.TRANSFORM -> TransformPanel(vm, doc, ui)
                    EditorPanel.CANVAS -> CanvasSizePanel(vm, doc, settings)
                    EditorPanel.SELECT -> SelectPanel(vm, state.selection, ui, settings)
                    EditorPanel.AI -> AiPanel(vm, state, ui, settings)
                    EditorPanel.BACKGROUND -> BackgroundPanel(vm, ui, settings)
                    EditorPanel.TEXT -> TextPanel(vm, doc, state.composite, ui, settings)
                    EditorPanel.DRAW -> DrawPanel(vm, doc, ui, settings)
                    EditorPanel.SHAPES -> ShapePanel(vm, doc, ui, settings)
                    EditorPanel.QR -> QrPanel(vm, doc, settings)
                    EditorPanel.BUBBLE -> BubblePanel(vm, doc, settings)
                    EditorPanel.GRADIENT -> GradientLayerPanel(vm, doc, settings)
                    EditorPanel.RETOUCH -> RetouchPanel(vm, ui)
                    EditorPanel.PRIVACY -> PrivacyPanel(vm, ui)
                    EditorPanel.FACES -> FacesPanel(vm, ui)
                    EditorPanel.WATERMARK -> WatermarkPanel(vm, doc, settings)
                    EditorPanel.FRAMES -> FramesPanel(vm, doc, settings)
                    EditorPanel.MEASURE -> MeasurePanel(vm, doc, ui)
                    EditorPanel.HISTORY -> HistoryPanel(vm, state, settings)
                    EditorPanel.EXPORT -> ExportPanel(vm, state)
                    EditorPanel.IMAGE -> ImagePanel(vm, state)
                    else -> Unit
                }
            }
        }
    }
}
