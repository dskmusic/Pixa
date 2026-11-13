package com.dskmusic.pixa.ui.editor.panels

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.Anchor9
import com.dskmusic.pixa.core.model.FrameKind
import com.dskmusic.pixa.core.model.FrameSpec
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.WatermarkKind
import com.dskmusic.pixa.core.model.WatermarkSpec
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.render.Overlays
import com.dskmusic.pixa.core.text.FontRepository
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.IntSliderRow
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.TextPromptDialog
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

// ======================================================================================
// Marca de agua (requisito 15)
// ======================================================================================

/**
 * Marca de agua de texto o logo, con presets reutilizables.
 *
 * La marca se anade como una capa mas y no se quema en la foto: asi se puede mover,
 * cambiar de opacidad o borrar despues, y el preset solo guarda los ajustes (el tamano va
 * en porcentaje, de modo que la misma marca vale para cualquier resolucion).
 */
@Composable
fun WatermarkPanel(vm: EditorViewModel, doc: PixaDocument, settings: PixaSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { context.pixa.userData }
    val fonts = remember(context) { context.pixa.fonts }
    val userData by store.data.collectAsStateWithLifecycle()
    var spec by remember { mutableStateOf(WatermarkSpec(id = UUID.randomUUID().toString(), name = "")) }
    var namingPreset by remember { mutableStateOf(false) }
    var pickingFont by remember { mutableStateOf(false) }

    val kindNames = remember(context) {
        WatermarkKind.entries.associateWith { context.getString(EditorLabels.watermarkKind(it)) }
    }
    val anchorNames = remember(context) {
        Anchor9.entries.associateWith { context.getString(EditorLabels.anchor(it)) }
    }

    // Vista previa en vivo: la fuente se carga aparte (puede estar descargandose) y la
    // marca se pinta en una capa temporal que se sustituye a si misma en cada cambio.
    val bitmaps = remember(context) { context.pixa.bitmaps }
    val previewLayerId = remember { PixaLayer.newId() }
    var typeface by remember { mutableStateOf<Typeface?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(spec.font) {
        typeface = fonts.load(spec.font)
    }

    fun updatePreview(next: WatermarkSpec) {
        spec = next
        // El render anterior se descarta: solo interesa el ultimo estado del slider.
        previewJob?.cancel()
        // Sin contenido no hay nada que previsualizar: se limpia la preview anterior.
        val hasContent = when (next.kind) {
            WatermarkKind.TEXT -> next.text.isNotBlank()
            WatermarkKind.LOGO -> next.logoFile != null
        }
        if (!hasContent) {
            vm.cancelPreview()
            return
        }
        val logoDir = store.logoDir()
        val targetTypeface = typeface
        previewJob = scope.launch {
            val stampBitmap = withContext(Dispatchers.Default) {
                runCatching {
                    val out = BitmapUtils.create(doc.width, doc.height)
                    Overlays.drawWatermark(
                        canvas = Canvas(out),
                        width = doc.width,
                        height = doc.height,
                        spec = next,
                        typeface = targetTypeface,
                        logoDir = logoDir,
                    )
                    out
                }.getOrNull()
            }
            if (stampBitmap == null) {
                vm.cancelPreview()
                return@launch
            }
            bitmaps.put(previewLayerId, stampBitmap)
            vm.previewDocument(
                doc.addLayerOnTop(
                    PixaLayer(
                        name = context.getString(R.string.watermark_title),
                        content = LayerContent.Raster(previewLayerId),
                    ),
                ),
            )
        }
    }

    val logoPicker = rememberImagePicker { uri ->
        scope.launch {
            val name = runCatching { store.importLogo(uri) }.getOrNull()
            if (name == null) {
                vm.post(R.string.error_load_image)
            } else {
                updatePreview(spec.copy(kind = WatermarkKind.LOGO, logoFile = name))
            }
        }
    }

    // Con preview activa (p.e. al cambiar de panel) se limpia la capa temporal.
    DisposableEffect(Unit) { onDispose { vm.cancelPreview() } }

    Column {
        if (userData.watermarks.isNotEmpty()) {
            Text(
                stringResource(R.string.watermark_presets),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
            OptionRow(
                options = userData.watermarks,
                selected = userData.watermarks.firstOrNull { it.id == spec.id },
                onSelect = { preset -> updatePreview(preset) },
                label = { it.name },
            )
        }

        OptionRow(
            options = WatermarkKind.entries,
            selected = spec.kind,
            onSelect = { kind -> updatePreview(spec.copy(kind = kind)) },
            label = { kindNames[it].orEmpty() },
        )

        if (spec.kind == WatermarkKind.TEXT) {
            TextFieldRow(
                label = stringResource(R.string.label_text),
                value = spec.text,
                onValueChange = { updatePreview(spec.copy(text = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PanelActions {
                Text(
                    stringResource(R.string.font_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { pickingFont = true }) { Text(spec.font.family) }
            }
            ColorRow(
                label = stringResource(R.string.label_color),
                color = spec.color,
                settings = settings,
                onPick = { updatePreview(spec.copy(color = it)) },
            )
        } else {
            PanelActions {
                TextButton(onClick = logoPicker) { Text(stringResource(R.string.watermark_image)) }
            }
            if (spec.logoFile != null) {
                PanelHint(R.string.watermark_image)
            }
        }

        SliderRow(
            label = stringResource(R.string.label_size),
            value = spec.sizePercent,
            onValueChange = { updatePreview(spec.copy(sizePercent = it)) },
            valueRange = 1f..60f,
            valueText = "${spec.sizePercent.roundToInt()} %",
            defaultValue = 12f,
        )
        SliderRow(
            label = stringResource(R.string.label_opacity),
            value = spec.opacity,
            onValueChange = { updatePreview(spec.copy(opacity = it)) },
            valueRange = 0.05f..1f,
            valueText = formatPercent(spec.opacity),
            defaultValue = 0.6f,
        )
        IntSliderRow(
            label = stringResource(R.string.label_rotation),
            value = spec.rotation.roundToInt(),
            onValueChange = { updatePreview(spec.copy(rotation = it.toFloat())) },
            range = -90..90,
            suffix = "°",
            defaultValue = 0,
        )

        Text(
            stringResource(R.string.watermark_position),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        OptionRow(
            options = Anchor9.entries,
            selected = spec.anchor,
            onSelect = { updatePreview(spec.copy(anchor = it)) },
            label = { anchorNames[it].orEmpty() },
        )
        if (spec.anchor == Anchor9.FREE) {
            PanelHint(R.string.watermark_free_position)
        }
        // Con las nueve posiciones el desplazamiento es el margen al borde; con posicion
        // libre es la posicion en si. El mismo par de deslizadores sirve para las dos.
        SliderRow(
            label = "X",
            value = spec.offsetX,
            onValueChange = { updatePreview(spec.copy(offsetX = it)) },
            valueText = formatPercent(spec.offsetX),
            defaultValue = 0.04f,
        )
        SliderRow(
            label = "Y",
            value = spec.offsetY,
            onValueChange = { updatePreview(spec.copy(offsetY = it)) },
            valueText = formatPercent(spec.offsetY),
            defaultValue = 0.04f,
        )

        ToggleRow(
            label = stringResource(R.string.text_shadow),
            checked = spec.shadow,
            onCheckedChange = { updatePreview(spec.copy(shadow = it)) },
        )
        ToggleRow(
            label = stringResource(R.string.watermark_tile),
            checked = spec.tiled,
            onCheckedChange = { updatePreview(spec.copy(tiled = it)) },
        )
        if (spec.tiled) {
            SliderRow(
                label = stringResource(R.string.label_spacing),
                value = spec.tileSpacing,
                onValueChange = { updatePreview(spec.copy(tileSpacing = it)) },
                valueRange = 5f..60f,
                valueText = "${spec.tileSpacing.roundToInt()} %",
                defaultValue = 25f,
            )
        }

        PanelActions {
            if (userData.watermarks.any { it.id == spec.id }) {
                TextButton(
                    onClick = { scope.launch { store.deleteWatermark(spec.id) } },
                ) { Text(stringResource(R.string.action_delete)) }
            }
            TextButton(onClick = { namingPreset = true }) {
                Text(stringResource(R.string.action_save))
            }
            PrimaryPanelButton(
                text = stringResource(R.string.action_add),
                enabled = spec.kind == WatermarkKind.LOGO || spec.text.isNotBlank(),
                onClick = {
                    addWatermarkLayer(
                        vm = vm,
                        doc = doc,
                        spec = spec,
                        fonts = fonts,
                        logoDir = store.logoDir(),
                        name = context.getString(R.string.watermark_title),
                    )
                },
            )
        }
    }

    if (namingPreset) {
        TextPromptDialog(
            title = stringResource(R.string.watermark_presets),
            initial = spec.name,
            onConfirm = { name ->
                // Un preset nuevo cada vez que se guarda con otro nombre; guardar con el
                // mismo nombre actualiza el que ya estaba.
                val saved = spec.copy(
                    id = userData.watermarks.firstOrNull { it.name == name }?.id ?: spec.id,
                    name = name.ifBlank { context.getString(R.string.watermark_title) },
                )
                spec = saved
                scope.launch { store.saveWatermark(saved) }
            },
            onDismiss = { namingPreset = false },
        )
    }
    if (pickingFont) {
        FontPickerDialog(
            current = spec.font,
            onDismiss = { pickingFont = false },
            onPick = { ref -> updatePreview(spec.copy(font = ref)) },
        )
    }
}

/**
 * Pinta la marca en una capa transparente del tamano del lienzo.
 *
 * Se usa [Overlays.drawWatermark] sobre un bitmap vacio en vez de [Overlays.applyWatermark]
 * sobre la foto justamente para que la marca quede como capa aparte.
 */
private fun addWatermarkLayer(
    vm: EditorViewModel,
    doc: PixaDocument,
    spec: WatermarkSpec,
    fonts: FontRepository,
    logoDir: File,
    name: String,
) {
    vm.busy(R.string.label_progress)
    vm.viewModelScope.launch {
        // La fuente se pide aqui y no al pintar: si todavia no esta descargada, load la
        // trae antes de dibujar en vez de dejar la marca con la tipografia del sistema.
        val typeface = fonts.load(spec.font)
        val bitmap = withContext(Dispatchers.Default) {
            runCatching {
                val out = BitmapUtils.create(doc.width, doc.height)
                Overlays.drawWatermark(
                    canvas = Canvas(out),
                    width = doc.width,
                    height = doc.height,
                    spec = spec,
                    typeface = typeface,
                    logoDir = logoDir,
                )
                out
            }.getOrNull()
        }
        vm.idle()
        if (bitmap == null) {
            vm.post(R.string.error_generic)
        } else {
            vm.addLayer(
                R.string.watermark_title,
                PixaLayer(name = name, content = LayerContent.Raster(vm.putBitmap(bitmap))),
            )
        }
    }
}

// ======================================================================================
// Marcos (requisito 15)
// ======================================================================================

/**
 * Marcos y bordes decorativos.
 *
 * Requisito del usuario: el marco se adapta al tamano real de la capa seleccionada (por
 * ejemplo una foto cuadrada dentro de un lienzo rectangular), no al lienzo entero. Por eso
 * trabaja sobre el bitmap propio de la capa raster activa y crece hacia fuera desde su
 * centro, en vez de aplanar todo el documento.
 */
@Composable
fun FramesPanel(vm: EditorViewModel, doc: PixaDocument, settings: PixaSettings) {
    val context = LocalContext.current
    val bitmaps = remember(context) { context.pixa.bitmaps }
    val layer = doc.selected
    val source = layer?.takeIf { it.isRaster }?.let { vm.bitmapOf(it) }
    if (layer == null || source == null) {
        NeedsRasterNotice(vm)
        return
    }

    var spec by remember(layer.id) { mutableStateOf(FrameSpec(kind = FrameKind.SOLID)) }
    val kindNames = remember(context) {
        FrameKind.entries.associateWith { context.getString(EditorLabels.frame(it)) }
    }
    val twoColors = spec.kind == FrameKind.DOUBLE || spec.kind == FrameKind.FILM ||
        spec.kind == FrameKind.GRADIENT || spec.kind == FrameKind.SHADOW

    // Un unico id de capa y de bitmap reutilizado: cada marco nuevo sustituye al anterior
    // en la previsualizacion, nunca se apilan.
    val previewLayerId = remember { PixaLayer.newId() }

    fun updatePreview(next: FrameSpec) {
        spec = next
        // ponytail: sin miniatura de proxy, el bitmap de una sola capa ya es mucho mas
        // pequeno que el lienzo compuesto completo; si se nota lento, anadir una como en
        // LivePreview.
        val base = source
        if (next.kind == FrameKind.NONE) {
            vm.cancelPreview()
            return
        }
        val framed = runCatching { Overlays.applyFrame(base, next) }.getOrNull() ?: return
        bitmaps.put(previewLayerId, framed)
        val previewXf = growTransform(layer.transform, base.width, base.height, framed.width, framed.height)
        vm.previewDocument(
            doc.updateLayer(layer.id) {
                it.copy(content = LayerContent.Raster(previewLayerId), transform = previewXf)
            },
        )
        // El check de la cabecera (y el aviso al salir) aplican el marco en curso.
        vm.pendingApply = { applyFrame(vm, doc, layer.id, source, next) }
    }

    DisposableEffect(Unit) { onDispose { vm.cancelPreview() } }

    Column {
        OptionRow(
            options = FrameKind.entries,
            selected = spec.kind,
            onSelect = { updatePreview(spec.copy(kind = it)) },
            label = { kindNames[it].orEmpty() },
        )
        PanelHint(R.string.frames_flatten_hint)
        HorizontalDivider()

        if (spec.kind != FrameKind.NONE) {
            SliderRow(
                label = stringResource(R.string.label_thickness),
                value = spec.widthPercent,
                onValueChange = { updatePreview(spec.copy(widthPercent = it)) },
                valueRange = 0.5f..30f,
                valueText = "${spec.widthPercent.roundToInt()} %",
                defaultValue = 4f,
            )
            ColorRow(
                label = stringResource(R.string.label_color),
                color = spec.color,
                settings = settings,
                onPick = { updatePreview(spec.copy(color = it)) },
            )
            if (twoColors) {
                ColorRow(
                    label = stringResource(R.string.frame_pattern),
                    color = spec.secondaryColor,
                    settings = settings,
                    onPick = { updatePreview(spec.copy(secondaryColor = it)) },
                )
            }
            if (spec.kind == FrameKind.ROUNDED || spec.kind == FrameKind.INSET) {
                SliderRow(
                    label = stringResource(R.string.label_radius),
                    value = spec.cornerRadiusPercent,
                    onValueChange = { updatePreview(spec.copy(cornerRadiusPercent = it)) },
                    valueRange = 0f..50f,
                    valueText = "${spec.cornerRadiusPercent.roundToInt()} %",
                    defaultValue = 6f,
                )
            }
            // El "Aplicar" esta en la cabecera del panel (pendingApply).
        }
    }
}

/**
 * Compensa el cambio de tamano intrinseco al anadir un marco: el modelo coloca el
 * contenido por su centro ([Xf.toMatrix]), asi que al crecer el bitmap hay que retroceder
 * la traslacion la mitad de lo que ha crecido para que el contenido original se quede
 * donde estaba y el marco crezca hacia fuera (igual que [LivePreview.proxyTransform], pero
 * en sentido contrario: aqui el bitmap crece en vez de encoger).
 */
private fun growTransform(base: Xf, oldW: Int, oldH: Int, newW: Int, newH: Int): Xf = base.copy(
    tx = base.tx - (newW - oldW) / 2f,
    ty = base.ty - (newH - oldH) / 2f,
)

/** Aplica el marco al bitmap propio de la capa, sin tocar el resto del documento. */
private fun applyFrame(
    vm: EditorViewModel,
    doc: PixaDocument,
    layerId: String,
    source: Bitmap,
    spec: FrameSpec,
) {
    vm.busy(R.string.label_progress)
    vm.viewModelScope.launch {
        val framed = withContext(Dispatchers.Default) {
            runCatching { Overlays.applyFrame(source, spec) }.getOrNull()
        }
        vm.idle()
        if (framed == null) {
            vm.post(R.string.error_generic)
            return@launch
        }
        val bitmapId = vm.putBitmap(framed)
        val framedDoc = doc.updateLayer(layerId) {
            it.copy(
                content = LayerContent.Raster(bitmapId),
                transform = growTransform(it.transform, source.width, source.height, framed.width, framed.height),
            )
        }
        val rasterizer = vm.getApplication<android.app.Application>().pixa.rasterizer
        vm.commit(R.string.frames_title, expandedToFit(framedDoc, layerId, rasterizer))
    }
}

/**
 * Si el marco hace que la capa se salga del lienzo (una foto abierta que ocupa todo el
 * lienzo, por ejemplo), el lienzo crece para contenerla y las capas se desplazan lo justo:
 * si no, el marco quedaria fuera y no se veria.
 */
private fun expandedToFit(
    doc: PixaDocument,
    layerId: String,
    rasterizer: com.dskmusic.pixa.core.render.LayerRasterizer,
): PixaDocument {
    val layer = doc.layer(layerId) ?: return doc
    val b = runCatching {
        com.dskmusic.pixa.ui.editor.LayerGeometry.bounds(layer, rasterizer, doc)
    }.getOrNull() ?: return doc
    val left = minOf(0f, b.left)
    val top = minOf(0f, b.top)
    val right = maxOf(doc.width.toFloat(), b.right)
    val bottom = maxOf(doc.height.toFloat(), b.bottom)
    if (left == 0f && top == 0f && right == doc.width.toFloat() && bottom == doc.height.toFloat()) return doc
    val dx = -kotlin.math.floor(left)
    val dy = -kotlin.math.floor(top)
    return doc.copy(
        width = kotlin.math.ceil(right + dx).toInt(),
        height = kotlin.math.ceil(bottom + dy).toInt(),
        layers = doc.layers.map {
            it.copy(transform = it.transform.copy(tx = it.transform.tx + dx, ty = it.transform.ty + dy))
        },
    )
}
