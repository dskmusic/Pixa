package com.dskmusic.pixa.ui.editor.panels

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.image.Retouch
import com.dskmusic.pixa.core.ml.FaceFinder
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.ui.common.IntSliderRow
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.ToolButton
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.editor.CanvasTool
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorUi
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.PrivacyStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ======================================================================================
// Retoque (requisito 14)
// ======================================================================================

/**
 * Tampon de clonar, pinceles de desenfocar y enfocar, y correccion de ojos rojos.
 *
 * El panel solo elige herramienta y parametros; el trazo lo aplica el lienzo, que es
 * quien conoce las coordenadas del dedo. Por eso todo lo que se toca aqui vive en
 * [EditorUi] y no en estado local.
 */
@Composable
fun RetouchPanel(vm: EditorViewModel, ui: EditorUi) {
    val brush = ui.brush
    val tools = listOf(
        Triple(CanvasTool.CLONE_SOURCE, Icons.Filled.TouchApp, R.string.retouch_clone_source),
        Triple(CanvasTool.CLONE, Icons.Filled.ContentCopy, R.string.retouch_clone),
        Triple(CanvasTool.BLUR_BRUSH, Icons.Filled.BlurOn, R.string.retouch_blur_brush),
        Triple(CanvasTool.SHARPEN_BRUSH, Icons.Filled.AutoFixHigh, R.string.retouch_sharpen_brush),
        Triple(CanvasTool.RED_EYE, Icons.Filled.Visibility, R.string.retouch_red_eye),
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tools.forEach { (tool, icon, labelRes) ->
                ToolButton(
                    icon = icon,
                    label = stringResource(labelRes),
                    selected = ui.tool == tool,
                    onClick = { ui.tool = tool },
                )
            }
        }

        val hint = when (ui.tool) {
            CanvasTool.CLONE_SOURCE -> R.string.clone_pick_source
            CanvasTool.CLONE ->
                if (ui.cloneSource == null) R.string.clone_pick_source else R.string.retouch_clone_ready
            CanvasTool.RED_EYE -> R.string.retouch_red_eye_hint
            else -> null
        }
        if (hint != null) PanelHint(hint)

        // El radio del pincel tambien marca el circulo del toque de ojos rojos, asi que el
        // deslizador de tamano se ensena con todas las herramientas del panel.
        IntSliderRow(
            label = stringResource(R.string.label_size),
            value = brush.size.roundToInt(),
            onValueChange = { ui.brush = brush.copy(size = it.toFloat()) },
            range = 1..400,
            suffix = " px",
            defaultValue = 24,
        )
        if (ui.tool != CanvasTool.RED_EYE) {
            SliderRow(
                label = stringResource(R.string.brush_hardness),
                value = brush.hardness,
                onValueChange = { ui.brush = brush.copy(hardness = it) },
                valueText = formatPercent(brush.hardness),
                defaultValue = 0.7f,
            )
        }
        if (ui.tool != CanvasTool.CLONE_SOURCE) {
            SliderRow(
                label = stringResource(R.string.label_amount),
                value = ui.retouchAmount,
                onValueChange = { ui.retouchAmount = it },
                valueRange = 0.05f..1f,
                valueText = formatPercent(ui.retouchAmount),
                defaultValue = 0.6f,
            )
        }

        if (vm.selectedLayer?.isRaster != true) NeedsRasterNotice(vm)
    }
}

// ======================================================================================
// Privacidad (requisito 14)
// ======================================================================================

/**
 * Pixelar o desenfocar zonas para tapar datos.
 *
 * El pincel es destructivo sobre la capa (no hay forma razonable de guardar un trazo
 * libre como parche recolocable), mientras que el rectangulo se monta como capa aparte
 * para poder moverlo o quitarlo despues.
 */
@Composable
fun PrivacyPanel(vm: EditorViewModel, ui: EditorUi) {
    val context = LocalContext.current
    val brush = ui.brush
    val modeNames = remember(context) {
        mapOf(
            CanvasTool.PRIVACY_BRUSH to context.getString(R.string.privacy_mode_brush),
            CanvasTool.PRIVACY_RECT to context.getString(R.string.privacy_mode_rect),
        )
    }
    val styleNames = remember(context) {
        PrivacyStyle.entries.associateWith { context.getString(EditorLabels.privacy(it)) }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolButton(
                icon = Icons.Filled.Brush,
                label = modeNames.getValue(CanvasTool.PRIVACY_BRUSH),
                selected = ui.tool == CanvasTool.PRIVACY_BRUSH,
                onClick = { ui.tool = CanvasTool.PRIVACY_BRUSH },
            )
            ToolButton(
                icon = Icons.Filled.Square,
                label = modeNames.getValue(CanvasTool.PRIVACY_RECT),
                selected = ui.tool == CanvasTool.PRIVACY_RECT,
                onClick = { ui.tool = CanvasTool.PRIVACY_RECT },
            )
        }
        PanelHint(R.string.privacy_hint)

        OptionRow(
            options = PrivacyStyle.entries,
            selected = ui.privacyStyle,
            onSelect = { ui.privacyStyle = it },
            label = { styleNames[it].orEmpty() },
        )
        if (ui.tool == CanvasTool.PRIVACY_BRUSH) {
            IntSliderRow(
                label = stringResource(R.string.label_size),
                value = brush.size.roundToInt(),
                onValueChange = { ui.brush = brush.copy(size = it.toFloat()) },
                range = 4..400,
                suffix = " px",
                defaultValue = 48,
            )
        }
        SliderRow(
            label = stringResource(R.string.label_intensity),
            value = ui.retouchAmount,
            onValueChange = { ui.retouchAmount = it },
            valueRange = 0.05f..1f,
            valueText = formatPercent(ui.retouchAmount),
            defaultValue = 0.6f,
        )

        if (ui.tool == CanvasTool.PRIVACY_BRUSH && vm.selectedLayer?.isRaster != true) {
            NeedsRasterNotice(vm)
        }
    }
}

// ======================================================================================
// Censura de caras (requisito 14)
// ======================================================================================

/** Como se tapa una cara: parche de pixeles o emoji. */
private enum class CoverStyle(
    @StringRes val labelRes: Int,
    val censor: Retouch.CensorStyle?,
) {
    BAR(R.string.faces_cover_bar, Retouch.CensorStyle.BAR),
    PIXELATE(R.string.faces_cover_pixelate, Retouch.CensorStyle.PIXELATE),
    BLUR(R.string.faces_cover_blur, Retouch.CensorStyle.BLUR),
    EMOJI(R.string.faces_cover_emoji, null),
}

/** Emojis con los que tapar una cara; el resto se puede poner como capa de emoji. */
private val COVER_EMOJI = listOf(
    "🙂", "😎", "😶", "🤖", "🙈", "⭐",
    "😀", "😄", "😁", "😂", "🤣", "😊",
    "😇", "😉", "😍", "🥰", "😘", "😜",
    "🤪", "🤩", "🥳", "😳", "🥴", "🤫",
    "🤔", "🙃", "😐", "😬", "😴", "🤐",
    "🤠", "🥸", "🤡", "👻", "👽", "💀",
    "👾", "🐱", "🐶", "🐼", "🦊", "🐸",
    "🦁", "🐵", "🐷", "🍕", "🍔", "🌈",
)

/**
 * Deteccion y censura de caras.
 *
 * Se detecta sobre la imagen compuesta y no sobre la capa activa: asi las coordenadas ya
 * estan en el lienzo, el parche copia exactamente lo que se ve y funciona aunque las
 * caras vengan de varias capas. Cada cara tapada es una capa independiente, de modo que
 * se puede mover, cambiar de estilo o borrar despues.
 */
@Composable
fun FacesPanel(vm: EditorViewModel, ui: EditorUi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var faces by remember { mutableStateOf<List<FaceFinder.Face>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var style by remember { mutableStateOf(CoverStyle.PIXELATE) }
    var emoji by remember { mutableStateOf(COVER_EMOJI.first()) }
    // Compuesto del que se detectaron las caras: sirve para recortar la miniatura de cada una.
    var source by remember { mutableStateOf<Bitmap?>(null) }
    val styleNames = remember(context) {
        CoverStyle.entries.associateWith { context.getString(it.labelRes) }
    }

    // Vista previa en vivo: monta las capas de censura de las caras marcadas sobre el
    // documento cada vez que cambia el tipo, el emoji o la intensidad.
    fun updatePreview() {
        val composite = source
        val selected = faces.filter { it.index in checked }
        if (composite == null || selected.isEmpty()) {
            vm.cancelPreview()
            return
        }
        val layers = coverLayers(
            vm = vm,
            source = composite,
            faces = selected,
            style = style,
            emoji = emoji,
            strength = ui.retouchAmount,
            name = { index -> context.getString(R.string.faces_item, index + 1) },
        )
        if (layers.isEmpty()) {
            vm.cancelPreview()
            return
        }
        vm.previewDocument(
            vm.doc.copy(
                layers = vm.doc.layers + layers,
                selectedLayerId = layers.last().id,
            ),
        )
    }

    DisposableEffect(Unit) { onDispose { vm.cancelPreview() } }

    Column {
        PanelActions {
            TextButton(
                onClick = {
                    scope.launch {
                        vm.busy(R.string.faces_detecting)
                        val composite = vm.compositeNow()
                        val found = runCatching { FaceFinder.detect(composite) }
                            .getOrDefault(emptyList())
                        vm.idle()
                        source = composite
                        faces = found
                        scanned = true
                        checked = found.map { it.index }.toSet()
                        updatePreview()
                        if (found.isEmpty()) vm.post(R.string.faces_none)
                    }
                },
            ) { Text(stringResource(R.string.faces_scan)) }
            TextButton(
                onClick = {
                    ui.tool = CanvasTool.PRIVACY_RECT
                    vm.post(R.string.privacy_hint)
                },
            ) { Text(stringResource(R.string.faces_add_manual)) }
        }

        if (scanned) {
            Text(
                if (faces.isEmpty()) {
                    stringResource(R.string.faces_none)
                } else {
                    context.getString(R.string.faces_found, faces.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        faces.forEach { face ->
            val thumb = source?.let { src ->
                val area = face.area
                runCatching {
                    Bitmap.createBitmap(src, area.left, area.top, area.width(), area.height())
                }.getOrNull()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Miniatura de la cara para identificarla entre muchas.
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                ToggleRow(
                    label = context.getString(R.string.faces_item, face.index + 1),
                    checked = face.index in checked,
                    onCheckedChange = { on ->
                        checked = if (on) checked + face.index else checked - face.index
                        updatePreview()
                    },
                    subtitle = "${face.area.width()} x ${face.area.height()} px",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        OptionRow(
            options = CoverStyle.entries,
            selected = style,
            onSelect = {
                style = it
                updatePreview()
            },
            label = { styleNames[it].orEmpty() },
        )
        if (style == CoverStyle.EMOJI) {
            OptionRow(
                options = COVER_EMOJI,
                selected = emoji,
                onSelect = {
                    emoji = it
                    updatePreview()
                },
                label = { it },
            )
        } else if (style != CoverStyle.BAR) {
            SliderRow(
                label = stringResource(R.string.label_intensity),
                value = ui.retouchAmount,
                onValueChange = {
                    ui.retouchAmount = it
                    updatePreview()
                },
                valueRange = 0.05f..1f,
                valueText = formatPercent(ui.retouchAmount),
                defaultValue = 0.6f,
            )
        }

        PanelActions {
            TextButton(
                enabled = checked.isNotEmpty(),
                onClick = {
                    val selected = faces.filter { it.index in checked }
                    scope.launch {
                        vm.busy(R.string.label_progress)
                        val source = vm.compositeNow()
                        val layers = coverLayers(
                            vm = vm,
                            source = source,
                            faces = selected,
                            style = style,
                            emoji = emoji,
                            strength = ui.retouchAmount,
                            name = { index -> context.getString(R.string.faces_item, index + 1) },
                        )
                        vm.idle()
                        if (layers.isEmpty()) {
                            vm.post(R.string.error_generic)
                        } else {
                            // Un solo paso de historial: tapar seis caras es una accion, y
                            // deshacerla seis veces seria absurdo.
                            vm.edit(R.string.faces_title) { document ->
                                document.copy(
                                    layers = document.layers + layers,
                                    selectedLayerId = layers.last().id,
                                )
                            }
                            faces = emptyList()
                            checked = emptySet()
                            scanned = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.faces_apply)) }
        }
    }
}

/** Monta una capa por cara marcada. */
private fun coverLayers(
    vm: EditorViewModel,
    source: Bitmap,
    faces: List<FaceFinder.Face>,
    style: CoverStyle,
    emoji: String,
    strength: Float,
    name: (Int) -> String,
): List<PixaLayer> = faces.mapNotNull { face ->
    val area = face.area
    val content = if (style.censor == null) {
        LayerContent.Emoji(
            emoji = emoji,
            sizePx = minOf(area.width(), area.height()).toFloat(),
        )
    } else {
        val patch = Retouch.censorPatch(
            src = source,
            region = area,
            style = style.censor,
            strength = strength,
        ) ?: return@mapNotNull null
        LayerContent.Raster(vm.putBitmap(patch))
    }
    PixaLayer(
        name = name(face.index),
        content = content,
        transform = Xf(tx = area.left.toFloat(), ty = area.top.toFloat()),
    )
}
