package com.dskmusic.pixa.ui.editor.panels

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.model.Anchor9
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.render.LayerRasterizer
import com.dskmusic.pixa.core.transform.TransformOps
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.IntSliderRow
import com.dskmusic.pixa.ui.common.MinimizedPanelBar
import com.dskmusic.pixa.ui.common.NumberField
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.ToolButton
import com.dskmusic.pixa.ui.common.formatValue
import com.dskmusic.pixa.ui.editor.CanvasTool
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorUi
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.LayerGeometry
import com.dskmusic.pixa.ui.editor.SelectionBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ======================================================================================
// Recortar (requisito 9)
// ======================================================================================

/** Proporciones rapidas de recorte; 0 significa libre. */
private val CropRatios = listOf(
    Triple(R.string.crop_free, 0f, 0f),
    Triple(R.string.preset_square, 1f, 1f),
    Triple(R.string.preset_portrait, 4f, 5f),
    Triple(R.string.preset_story, 9f, 16f),
    Triple(R.string.preset_landscape, 16f, 9f),
    Triple(R.string.preset_banner, 3f, 1f),
    Triple(R.string.preset_cover, 3f, 2f),
    Triple(R.string.preset_a4, 210f, 297f),
)

@Composable
fun CropPanel(
    vm: EditorViewModel,
    doc: PixaDocument,
    ui: EditorUi,
    title: String,
    help: String,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    minimized: Boolean,
    onMaximize: () -> Unit,
    scroll: ScrollState,
) {
    var straighten by remember { mutableStateOf(0f) }
    var wholeProject by remember { mutableStateOf(true) }
    val start = ui.dragStart
    val current = ui.dragCurrent
    val rect = if (start != null && current != null) rectOf(start.x, start.y, current.x, current.y) else null

    // Requisito del usuario: el "Aplicar" (recorte) sube a la cabecera, junto a ayuda y
    // cerrar; solo esta activo cuando hay un recuadro pendiente. Al aplicarlo se cierra el
    // panel, porque ya no hay nada mas que recortar.
    // Previsualizacion en vivo del enderezado: se gira la capa activa sin tocar sus pixeles;
    // el recorte de las esquinas vacias solo se aplica al confirmar.
    DisposableEffect(Unit) { onDispose { vm.cancelPreview() } }
    val applyAction: (() -> Unit)? = if (rect != null || straighten != 0f) {
        {
            if (straighten != 0f) {
                // ponytail: si hay enderezado pendiente se aplica solo el enderezado; el
                // recuadro se descarta porque sus coordenadas ya no valdrian tras girar.
                val deg = straighten
                vm.cancelPreview()
                straightenLayer(vm, deg, wholeProject)
                straighten = 0f
            } else if (rect != null) {
                if (wholeProject) cropProject(vm, doc, rect) else cropDocument(vm, rect)
            }
            ui.dragStart = null
            ui.dragCurrent = null
            onClose()
        }
    } else null
    if (minimized) {
        MinimizedPanelBar(
            text = title,
            onMaximize = onMaximize,
            onClose = onClose,
            onApply = applyAction,
        )
    } else {
        SectionTitle(
            text = title,
            help = help,
            onClose = onClose,
            onMinimize = onMinimize,
            onApply = applyAction,
        )
    }

    Column(
        modifier = (if (minimized) Modifier.height(0.dp) else Modifier)
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CropRatios.forEach { (label, rw, rh) ->
                TextButton(
                    onClick = {
                        ui.tool = CanvasTool.CROP
                        if (rw <= 0f) {
                            // Libre: el recuadro se queda donde esta y lo que se suelta es
                            // la proporcion, para poder estirarlo a cualquier forma.
                            ui.boxRatio = null
                        } else {
                            val r = TransformOps.aspectCrop(doc.width, doc.height, rw, rh)
                            val box = RectF(
                                r.left.toFloat(),
                                r.top.toFloat(),
                                r.right.toFloat(),
                                r.bottom.toFloat(),
                            )
                            ui.boxRatio = box.width() / box.height()
                            SelectionBox.set(ui, box)
                        }
                    },
                ) { Text(stringResource(label)) }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Checkbox(checked = wholeProject, onCheckedChange = { wholeProject = it })
            Text(stringResource(R.string.crop_whole_project), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = rect?.let { "${it.width()} × ${it.height()} px" }
                ?: stringResource(R.string.crop_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        ) {
            Text(stringResource(R.string.crop_straighten), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = straighten,
                onValueChange = {
                    straighten = it
                    val layer = doc.selected
                    if (layer != null && layer.content is LayerContent.Raster) {
                        vm.previewDocument(
                            doc.updateLayer(layer.id) { l ->
                                l.copy(transform = l.transform.copy(rotation = l.transform.rotation + it))
                            },
                            fast = true,
                        )
                    }
                },
                valueRange = -45f..45f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(
                "${straighten.roundToInt()}°",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
            )
            IconButton(
                onClick = {
                    straighten = 0f
                    vm.cancelPreview()
                },
                enabled = straighten != 0f,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.action_reset),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun rectOf(x1: Float, y1: Float, x2: Float, y2: Float): Rect = Rect(
    minOf(x1, x2).roundToInt(),
    minOf(y1, y2).roundToInt(),
    maxOf(x1, x2).roundToInt(),
    maxOf(y1, y2).roundToInt(),
)

/**
 * Recorta el proyecto entero (como Photoshop): el lienzo pasa a medir el rectangulo y todas las
 * capas se desplazan para seguir alineadas. Trasladar tx/ty es exacto con cualquier
 * rotacion/escala porque el pivote es el centro de cada capa.
 */
internal fun cropProject(vm: EditorViewModel, doc: PixaDocument, requested: Rect) {
    val next = croppedProject(doc, requested)
    if (next == null) {
        vm.post(R.string.error_generic)
        return
    }
    vm.setSelection(null)
    vm.resizeDocument(next.width, next.height, next.layers)
}

/** El documento con lienzo = [requested] (recortado al lienzo actual) y las capas desplazadas. */
private fun croppedProject(doc: PixaDocument, requested: Rect): PixaDocument? {
    val r = Rect(requested)
    if (!r.intersect(0, 0, doc.width, doc.height) || r.isEmpty) return null
    return doc.copy(
        width = r.width(),
        height = r.height(),
        layers = doc.layers.map {
            it.copy(transform = it.transform.copy(tx = it.transform.tx - r.left, ty = it.transform.ty - r.top))
        },
    )
}

/**
 * Endereza la capa activa. La capa mantiene su centro (antes se quedaba anclada arriba a la
 * izquierda y dejaba vacio a su derecha/abajo). Con [wholeProject], si la capa cubria el
 * lienzo (sin escala ni giro), el lienzo se recorta tambien a la imagen enderezada.
 */
private fun straightenLayer(vm: EditorViewModel, degrees: Float, wholeProject: Boolean) {
    val (layer, src) = vm.rasterOrWarn() ?: return
    val before = vm.doc
    vm.busy(R.string.label_progress)
    vm.viewModelScope.launch {
        val out = withContext(Dispatchers.Default) {
            runCatching { TransformOps.straighten(src, degrees) }.getOrNull()
        }
        if (out == null) {
            vm.idle()
            vm.post(R.string.error_generic)
            return@launch
        }
        val xf = layer.transform
        val moved = xf.copy(
            tx = xf.tx + (src.width - out.width) / 2f,
            ty = xf.ty + (src.height - out.height) / 2f,
        )
        var next = before.updateLayer(layer.id) {
            it.copy(content = LayerContent.Raster(vm.putBitmap(out)), transform = moved)
        }
        val plain = xf.copy(tx = 0f, ty = 0f) == Xf()
        val coversCanvas = plain && xf.tx.roundToInt() == 0 && xf.ty.roundToInt() == 0 &&
            src.width == before.width && src.height == before.height
        if (wholeProject && coversCanvas) {
            val r = Rect(moved.tx.roundToInt(), moved.ty.roundToInt(),
                moved.tx.roundToInt() + out.width, moved.ty.roundToInt() + out.height)
            croppedProject(next, r)?.let { next = it; vm.setSelection(null) }
        }
        vm.commit(R.string.crop_straighten, next)
        vm.idle()
    }
}

/**
 * Recorta la capa activa al rectangulo (coordenadas de documento). El lienzo no cambia: los
 * pixeles de fuera se descartan de verdad y deshacer funciona porque el historial conserva
 * el bitmap anterior.
 */
internal fun cropDocument(vm: EditorViewModel, requested: Rect) {
    val (layer, src) = vm.rasterOrWarn() ?: return
    val xf = layer.transform
    val sx = xf.scaleX
    val sy = xf.scaleY
    // Solo traslacion + escala positiva: con rotacion/sesgo/volteo/perspectiva o mascara
    // el recorte exacto exigiria rasterizar la capa.
    if (layer.mask != null || sx <= 0f || sy <= 0f ||
        xf.copy(tx = 0f, ty = 0f, scaleX = 1f, scaleY = 1f) != Xf()
    ) {
        vm.post(R.string.error_generic)
        return
    }
    val cx = src.width / 2f
    val cy = src.height / 2f
    // Rectangulo del documento -> coordenadas del bitmap (el pivote es el centro).
    val local = Rect(
        (cx + (requested.left - xf.tx - cx) / sx).toInt(),
        (cy + (requested.top - xf.ty - cy) / sy).toInt(),
        (cx + (requested.right - xf.tx - cx) / sx).roundToInt(),
        (cy + (requested.bottom - xf.ty - cy) / sy).roundToInt(),
    )
    if (!local.intersect(0, 0, src.width, src.height) || local.isEmpty) {
        vm.post(R.string.error_generic)
        return
    }
    val cut = BitmapUtils.crop(src, local)
    val ncx = cut.width / 2f
    val ncy = cut.height / 2f
    // La esquina superior izquierda del recorte debe quedar donde estaba en el lienzo.
    val tx = xf.tx + cx + (local.left - cx) * sx - ncx * (1f - sx)
    val ty = xf.ty + cy + (local.top - cy) * sy - ncy * (1f - sy)
    vm.updateLayer(R.string.crop_title, layer.id) {
        it.copy(
            content = LayerContent.Raster(vm.putBitmap(cut), (it.content as LayerContent.Raster).isDrawing),
            transform = xf.copy(tx = tx, ty = ty),
        )
    }
}

/**
 * Rota el lienzo completo 90 grados en sentido horario (requisito del usuario).
 *
 * Cambia el ancho por el alto y rota todas las capas a la vez, manteniendolas en su sitio
 * relativo. La formula recoloca cada capa usando su centro: un punto (x, y) del documento
 * viejo pasa a (alto - y, x) en el nuevo, y la capa suma 90 grados a su rotacion.
 */
internal fun rotateCanvas90(vm: EditorViewModel, rasterizer: LayerRasterizer, doc: PixaDocument) {
    val oldHeight = doc.height.toFloat()
    val layers = doc.layers.map { layer ->
        val (w, h) = LayerGeometry.intrinsic(layer, rasterizer, doc)
        val cx = w / 2f
        val cy = h / 2f
        val xf = layer.transform
        layer.copy(
            transform = xf.copy(
                tx = oldHeight - cy - xf.ty - cx,
                ty = cx + xf.tx - cy,
                rotation = xf.rotation + 90f,
            ),
        )
    }
    vm.resizeDocument(doc.height, doc.width, layers)
}

/**
 * Devuelve la capa a su estado inicial: centrada, sin escala, rotacion, sesgo, volteos ni
 * perspectiva (requisito del usuario).
 */
internal fun resetLayerTransform(
    vm: EditorViewModel,
    rasterizer: LayerRasterizer,
    doc: PixaDocument,
    layer: PixaLayer,
) {
    val (w, h) = LayerGeometry.intrinsic(layer, rasterizer, doc)
    val centered = Xf(
        tx = (doc.width - w) / 2f,
        ty = (doc.height - h) / 2f,
    )
    vm.updateLayer(R.string.action_reset, layer.id) { it.copy(transform = centered) }
}

/**
 * Aplica el caleidoscopio a la capa activa y re-centra el resultado.
 *
 * El caleidoscopio produce un cuadrado (de lado el lado corto de la imagen), asi que,
 * ademas de reemplazar los pixeles, se re-centra la capa para que el cuadrado quede en
 * medio del lienzo y no desplazado hacia una esquina (requisito del usuario).
 */
internal fun applyKaleidoscope(vm: EditorViewModel, doc: PixaDocument, segments: Int) {
    val pair = vm.rasterOrWarn() ?: return
    val (layer, src) = pair
    vm.busy(R.string.label_progress)
    vm.viewModelScope.launch {
        val out = withContext(Dispatchers.Default) {
            runCatching { TransformOps.kaleidoscope(src, segments) }.getOrNull()
        }
        if (out == null) {
            vm.idle()
            vm.post(R.string.error_generic)
            return@launch
        }
        val id = vm.putBitmap(out)
        val transform = Xf(
            tx = (doc.width - out.width) / 2f,
            ty = (doc.height - out.height) / 2f,
        )
        vm.commit(
            R.string.mirror_segments,
            doc.updateLayer(layer.id) {
                it.copy(content = LayerContent.Raster(id), transform = transform)
            },
        )
        vm.awaitFullRender()
        vm.idle()
    }
}

// ======================================================================================
// Transformar la capa (requisito 9)
// ======================================================================================

@Composable
fun TransformPanel(vm: EditorViewModel, doc: PixaDocument, ui: EditorUi) {
    val layer = doc.selected
    if (layer == null) {
        NeedsRasterNotice(vm)
        return
    }
    val xf = layer.transform
    val context = LocalContext.current
    val rasterizer = remember(context) { context.pixa.rasterizer }
    var segments by remember { mutableStateOf(6) }
    val axisNames = remember(context) {
        TransformOps.MirrorAxis.entries.associateWith {
            context.getString(EditorLabels.mirrorAxis(it))
        }
    }
    var axis by remember { mutableStateOf(TransformOps.MirrorAxis.LEFT) }

    fun update(block: (Xf) -> Xf) {
        vm.updateLayer(R.string.transform_title, layer.id) { it.copy(transform = block(it.transform)) }
    }

    // Vista previa viva para el espejo y el caleidoscopio (operaciones de pixeles en la
    // capa activa). Se cancela sola al salir del panel.
    val preview = rememberLivePreview(vm)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolButton(
                icon = Icons.Filled.OpenWith,
                label = stringResource(R.string.transform_move),
                selected = ui.tool == CanvasTool.MOVE,
                onClick = { ui.tool = CanvasTool.MOVE },
            )
            ToolButton(
                icon = Icons.Filled.Flip,
                label = stringResource(R.string.transform_flip_h),
                onClick = { update { it.copy(flipH = !it.flipH) } },
            )
            ToolButton(
                icon = Icons.Filled.Flip,
                label = stringResource(R.string.transform_flip_v),
                onClick = { update { it.copy(flipV = !it.flipV) } },
            )
            ToolButton(
                icon = Icons.Filled.Rotate90DegreesCw,
                label = stringResource(R.string.transform_rotate_90),
                onClick = { rotateCanvas90(vm, rasterizer, doc) },
            )
            ToolButton(
                icon = Icons.Filled.CropFree,
                label = stringResource(R.string.transform_perspective),
                selected = ui.tool == CanvasTool.PERSPECTIVE,
                onClick = {
                    ui.tool = CanvasTool.PERSPECTIVE
                    vm.post(R.string.transform_perspective_hint)
                },
            )
            ToolButton(
                icon = Icons.Filled.Refresh,
                label = stringResource(R.string.action_reset),
                onClick = { resetLayerTransform(vm, rasterizer, doc, layer) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NumberField(
                label = "X",
                value = xf.tx.roundToInt(),
                onValueChange = { v -> update { it.copy(tx = v.toFloat()) } },
                modifier = Modifier.weight(1f),
                range = -8192..8192,
            )
            NumberField(
                label = "Y",
                value = xf.ty.roundToInt(),
                onValueChange = { v -> update { it.copy(ty = v.toFloat()) } },
                modifier = Modifier.weight(1f),
                range = -8192..8192,
            )
        }
        SliderRow(
            label = stringResource(R.string.transform_scale),
            value = xf.scaleX,
            onValueChange = { v -> update { it.copy(scaleX = v, scaleY = v) } },
            valueRange = 0.05f..4f,
            valueText = formatValue(xf.scaleX),
            defaultValue = 1f,
        )
        SliderRow(
            label = stringResource(R.string.label_rotation),
            value = xf.rotation,
            onValueChange = { v -> update { it.copy(rotation = v) } },
            valueRange = -180f..180f,
            valueText = "${xf.rotation.roundToInt()}°",
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.transform_skew) + " X",
            value = xf.skewX,
            onValueChange = { v -> update { it.copy(skewX = v) } },
            valueRange = -45f..45f,
            valueText = "${xf.skewX.roundToInt()}°",
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.transform_skew) + " Y",
            value = xf.skewY,
            onValueChange = { v -> update { it.copy(skewY = v) } },
            valueRange = -45f..45f,
            valueText = "${xf.skewY.roundToInt()}°",
            defaultValue = 0f,
        )

        // --- Espejo ---------------------------------------------------------------
        // Trabaja sobre los pixeles y refracta media imagen sobre la otra mitad.
        Text(
            stringResource(R.string.mirror_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        Text(
            stringResource(R.string.mirror_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        )
        OptionRow(
            options = TransformOps.MirrorAxis.entries,
            selected = axis,
            onSelect = { axis = it },
            label = { axisNames[it].orEmpty() },
        )
        PanelActions {
            PrimaryPanelButton(
                text = stringResource(R.string.action_apply),
                onClick = {
                    val a = axis
                    val p = preview
                    if (p != null) {
                        p.apply(R.string.mirror_title) { TransformOps.mirror(it, a) }
                    } else {
                        vm.applyToRaster(R.string.mirror_title) { TransformOps.mirror(it, a) }
                    }
                },
            )
        }

        // --- Caleidoscopio ----------------------------------------------------------
        // Repite un sector circular el numero de "segmentos" indicado, girandolo.
        Text(
            stringResource(R.string.kaleidoscope_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        Text(
            stringResource(R.string.kaleidoscope_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        )
        IntSliderRow(
            label = stringResource(R.string.mirror_segments),
            value = segments,
            onValueChange = { value ->
                segments = value
                // Vista previa en vivo mientras se arrastra el slider (requisito del
                // usuario): se calcula sobre una copia reducida y se pinta al instante.
                val p = preview
                val side = vm.bitmapOf(layer)?.let { b -> minOf(b.width, b.height) }
                if (p != null && side != null) {
                    p.updateResized(R.string.mirror_segments, side, side) {
                        TransformOps.kaleidoscope(it, value)
                    }
                }
            },
            range = 2..24,
            defaultValue = 6,
        )
        PanelActions {
            PrimaryPanelButton(
                text = stringResource(R.string.action_apply),
                onClick = {
                    preview?.cancel()
                    applyKaleidoscope(vm, doc, segments)
                },
            )
        }

        // Correccion automatica de perspectiva, giro y lente.
        GeoCorrectSection(vm)
    }
}

// ======================================================================================
// Tamano de imagen y de lienzo (requisito 9)
// ======================================================================================

@Composable
fun CanvasSizePanel(vm: EditorViewModel, doc: PixaDocument, settings: PixaSettings) {
    val context = LocalContext.current
    val rasterizer = remember(context) { context.pixa.rasterizer }
    var width by remember(doc.width) { mutableStateOf(doc.width) }
    var height by remember(doc.height) { mutableStateOf(doc.height) }
    var anchor by remember { mutableStateOf(Anchor9.CENTER) }
    var fill by remember { mutableStateOf(TransformOps.CanvasFill.TRANSPARENT) }
    var includeLayers by remember { mutableStateOf(false) }
    var keepRatio by remember { mutableStateOf(true) }
    val ratio = doc.width.toFloat() / doc.height.toFloat()
    val anchorNames = remember(context) {
        ANCHORS.associateWith { context.getString(EditorLabels.anchor(it)) }
    }
    val fillNames = remember(context) {
        TransformOps.CanvasFill.entries.associateWith {
            context.getString(EditorLabels.canvasFill(it))
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NumberField(
                label = stringResource(R.string.label_width),
                value = width,
                onValueChange = {
                    width = it
                    if (keepRatio) height = (it / ratio).roundToInt().coerceAtLeast(1)
                },
                modifier = Modifier.weight(1f),
                range = 1..settings.maxResolution,
            )
            NumberField(
                label = stringResource(R.string.label_height),
                value = height,
                onValueChange = {
                    height = it
                    if (keepRatio) width = (it * ratio).roundToInt().coerceAtLeast(1)
                },
                modifier = Modifier.weight(1f),
                range = 1..settings.maxResolution,
            )
        }
        Text(
            stringResource(R.string.canvas_size),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp),
        )
        Text(
            stringResource(R.string.canvas_anchor),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
        OptionRow(
            options = ANCHORS,
            selected = anchor,
            onSelect = { anchor = it },
            label = { anchorNames[it].orEmpty() },
        )
        OptionRow(
            options = TransformOps.CanvasFill.entries,
            selected = fill,
            onSelect = { fill = it },
            label = { fillNames[it].orEmpty() },
        )
        ToggleRow(
            label = stringResource(R.string.canvas_include_layers),
            subtitle = stringResource(R.string.canvas_include_layers_hint),
            checked = includeLayers,
            onCheckedChange = { includeLayers = it },
        )
        PanelActions {
            PrimaryPanelButton(
                text = stringResource(R.string.action_apply),
                onClick = {
                    resizeCanvasDocument(vm, rasterizer, doc, width, height, anchor, fill, includeLayers)
                },
            )
        }
    }
}

/** Los nueve puntos de anclaje reales; FREE no tiene sentido al cambiar el lienzo. */
private val ANCHORS = listOf(
    Anchor9.CENTER,
    Anchor9.TOP_LEFT, Anchor9.TOP_CENTER, Anchor9.TOP_RIGHT,
    Anchor9.MIDDLE_LEFT, Anchor9.MIDDLE_RIGHT,
    Anchor9.BOTTOM_LEFT, Anchor9.BOTTOM_CENTER, Anchor9.BOTTOM_RIGHT,
)

/**
 * Cambia el tamano de la imagen escalando el contenido.
 *
 * Se escalan las transformaciones, no los pixeles: el documento cambia de tamano y las
 * capas conservan su resolucion original, asi que repetir la operacion no degrada nada.
 */
private fun scaleDocument(
    vm: EditorViewModel,
    rasterizer: LayerRasterizer,
    doc: PixaDocument,
    width: Int,
    height: Int,
) {
    if (width == doc.width && height == doc.height) return
    val kx = width.toFloat() / doc.width
    val ky = height.toFloat() / doc.height
    val layers = doc.layers.map { layer ->
        val (w, h) = LayerGeometry.intrinsic(layer, rasterizer, doc)
        val xf = layer.transform
        // El modelo coloca por el centro, asi que se escala el centro y luego se vuelve
        // a restar media capa ya escalada.
        val cx = (xf.tx + w / 2f) * kx
        val cy = (xf.ty + h / 2f) * ky
        layer.copy(
            transform = xf.copy(
                scaleX = xf.scaleX * kx,
                scaleY = xf.scaleY * ky,
                tx = cx - w / 2f,
                ty = cy - h / 2f,
            ),
        )
    }
    vm.resizeDocument(width, height, layers)
}

/** Cambia el lienzo sin escalar el contenido, colocandolo segun el ancla. */
private fun resizeCanvasDocument(
    vm: EditorViewModel,
    rasterizer: LayerRasterizer,
    doc: PixaDocument,
    width: Int,
    height: Int,
    anchor: Anchor9,
    fill: TransformOps.CanvasFill,
    includeLayers: Boolean,
) {
    // "Incluir capas" activo (o relleno por estiramiento) significa redimensionar todo:
    // las capas se escalan para llenar el nuevo lienzo.
    if (includeLayers || fill == TransformOps.CanvasFill.STRETCH) {
        scaleDocument(vm, rasterizer, doc, width, height)
        return
    }
    val dx = (width - doc.width) * anchor.fractionX
    val dy = (height - doc.height) * anchor.fractionY
    val background = doc.background.copy(
        transparent = fill == TransformOps.CanvasFill.TRANSPARENT,
    )
    // Un solo commit: redimensionar y pintar el relleno son la misma accion para quien
    // pulsa "Aplicar", asi que deshacer tiene que devolver las dos cosas de golpe.
    // Las capas siempre se recolocan segun el ancla (traslacion, no escala): sin marcar
    // "incluir capas" solo cambia el lienzo, el contenido conserva su tamano y se mueve
    // al punto de anclaje elegido.
    vm.edit(R.string.canvas_size) { document ->
        document.copy(
            width = width,
            height = height,
            background = background,
            layers = document.layers.map {
                it.copy(
                    transform = it.transform.copy(
                        tx = it.transform.tx + dx,
                        ty = it.transform.ty + dy,
                    ),
                )
            },
        )
    }
}

// ======================================================================================
// Medidas, alineacion y ayudas visuales (requisito 6)
// ======================================================================================

@Composable
fun MeasurePanel(vm: EditorViewModel, doc: PixaDocument, ui: EditorUi) {
    val context = LocalContext.current
    val rasterizer = remember(context) { context.pixa.rasterizer }
    val layer = doc.selected

    Column {
        if (layer != null) {
            val bounds = LayerGeometry.bounds(layer, rasterizer, doc)
            Text(
                text = "X ${bounds.left.roundToInt()}  Y ${bounds.top.roundToInt()}  " +
                    "W ${bounds.width().roundToInt()}  H ${bounds.height().roundToInt()}  " +
                    "∠ ${layer.transform.rotation.roundToInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        ToggleRow(
            label = stringResource(R.string.canvas_grid),
            checked = ui.showGrid,
            onCheckedChange = { ui.showGrid = it },
        )
        ToggleRow(
            label = stringResource(R.string.canvas_rulers),
            checked = ui.showRulers,
            onCheckedChange = { ui.showRulers = it },
        )
        ToggleRow(
            label = stringResource(R.string.canvas_guides),
            checked = ui.showGuides,
            onCheckedChange = { ui.showGuides = it },
        )
        ToggleRow(
            label = stringResource(R.string.settings_snap),
            checked = ui.snap,
            onCheckedChange = { ui.snap = it },
        )

        Text(
            stringResource(R.string.canvas_align),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp),
        )
        PanelActions {
            TextButton(
                enabled = layer != null,
                onClick = { align(vm, rasterizer, doc, layer, horizontal = true, fraction = 0f) },
            ) { Text(stringResource(R.string.align_left)) }
            TextButton(
                enabled = layer != null,
                onClick = { align(vm, rasterizer, doc, layer, horizontal = true, fraction = 0.5f) },
            ) { Text(stringResource(R.string.align_center_h)) }
            TextButton(
                enabled = layer != null,
                onClick = { align(vm, rasterizer, doc, layer, horizontal = true, fraction = 1f) },
            ) { Text(stringResource(R.string.align_right)) }
        }
        PanelActions {
            TextButton(
                enabled = layer != null,
                onClick = { align(vm, rasterizer, doc, layer, horizontal = false, fraction = 0f) },
            ) { Text(stringResource(R.string.align_top)) }
            TextButton(
                enabled = layer != null,
                onClick = { align(vm, rasterizer, doc, layer, horizontal = false, fraction = 0.5f) },
            ) { Text(stringResource(R.string.align_center_v)) }
            TextButton(
                enabled = layer != null,
                onClick = { align(vm, rasterizer, doc, layer, horizontal = false, fraction = 1f) },
            ) { Text(stringResource(R.string.align_bottom)) }
        }
        Text(
            stringResource(R.string.canvas_distribute),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp),
        )
        PanelActions {
            TextButton(
                enabled = doc.layers.size > 2,
                onClick = { distribute(vm, rasterizer, doc, horizontal = true) },
            ) { Text(stringResource(R.string.label_horizontal)) }
            TextButton(
                enabled = doc.layers.size > 2,
                onClick = { distribute(vm, rasterizer, doc, horizontal = false) },
            ) { Text(stringResource(R.string.label_vertical)) }
        }
    }
}

/** Alinea la capa con el lienzo: 0 = borde inicial, 0,5 = centro, 1 = borde final. */
fun align(
    vm: EditorViewModel,
    rasterizer: LayerRasterizer,
    doc: PixaDocument,
    layer: PixaLayer?,
    horizontal: Boolean,
    fraction: Float,
) {
    if (layer == null) return
    val bounds = LayerGeometry.bounds(layer, rasterizer, doc)
    val target = if (horizontal) {
        (doc.width - bounds.width()) * fraction
    } else {
        (doc.height - bounds.height()) * fraction
    }
    val delta = target - if (horizontal) bounds.left else bounds.top
    vm.updateLayer(R.string.canvas_align, layer.id) {
        it.copy(
            transform = if (horizontal) {
                it.transform.copy(tx = it.transform.tx + delta)
            } else {
                it.transform.copy(ty = it.transform.ty + delta)
            },
        )
    }
}

/**
 * Reparte las capas a distancias iguales entre la primera y la ultima.
 *
 * Se ordenan por su posicion actual para que el reparto no las reordene visualmente, que
 * es lo que uno espera al distribuir.
 */
private fun distribute(
    vm: EditorViewModel,
    rasterizer: LayerRasterizer,
    doc: PixaDocument,
    horizontal: Boolean,
) {
    val entries = doc.layers.map { it to LayerGeometry.bounds(it, rasterizer, doc) }
        .sortedBy { if (horizontal) it.second.left else it.second.top }
    if (entries.size < 3) return
    val first = entries.first().second
    val last = entries.last().second
    val from = if (horizontal) first.left else first.top
    val to = if (horizontal) last.left else last.top
    val step = (to - from) / (entries.size - 1)
    val moved = entries.mapIndexed { index, (layer, bounds) ->
        val target = from + step * index
        val delta = target - if (horizontal) bounds.left else bounds.top
        layer.copy(
            transform = if (horizontal) {
                layer.transform.copy(tx = layer.transform.tx + delta)
            } else {
                layer.transform.copy(ty = layer.transform.ty + delta)
            },
        )
    }
    val byId = moved.associateBy { it.id }
    vm.edit(R.string.canvas_distribute) { document ->
        document.copy(layers = document.layers.map { byId[it.id] ?: it })
    }
}
