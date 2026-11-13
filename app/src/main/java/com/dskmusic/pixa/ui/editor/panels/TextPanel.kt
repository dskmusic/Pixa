package com.dskmusic.pixa.ui.editor.panels

import com.dskmusic.pixa.core.render.TextTextures
import com.dskmusic.pixa.core.render.TextTexture
import com.dskmusic.pixa.core.model.TextShape
import com.dskmusic.pixa.core.model.TextOutline
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.GradientKind
import com.dskmusic.pixa.core.model.GradientStop
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.TextAlign
import com.dskmusic.pixa.core.model.TextSpec
import com.dskmusic.pixa.core.render.LayerRasterizer
import com.dskmusic.pixa.core.text.FontRepository
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.data.userdata.ImportedFont
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.IntSliderRow
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.GradientPresetGrid
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.formatValue
import com.dskmusic.pixa.ui.common.rememberDocumentPicker
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.editor.CanvasTool
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorUi
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.LayerGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import java.io.File
import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Panel de texto (requisito 10).
 *
 * El texto nunca se convierte en pixeles por su cuenta: la capa guarda el [TextSpec] y se
 * vuelve a dibujar cada vez, asi que se puede reeditar siempre. Rasterizar es una accion
 * explicita del usuario.
 */
@Composable
fun TextPanel(
    vm: EditorViewModel,
    doc: PixaDocument,
    composite: Bitmap?,
    ui: EditorUi,
    settings: PixaSettings,
) {
    val layer = doc.selected
    val content = layer?.content
    if (layer == null || content !is LayerContent.Text) {
        TextCreateRow(vm = vm, doc = doc, composite = composite)
        return
    }
    TextEditor(vm = vm, doc = doc, ui = ui, settings = settings, layer = layer, spec = content.spec)
}

// ======================================================================================
// Crear capas de texto y emoji
// ======================================================================================

@Composable
private fun TextCreateRow(vm: EditorViewModel, doc: PixaDocument, composite: Bitmap?) {
    val context = LocalContext.current
    val rasterizer = remember(context) { context.pixa.rasterizer }

    Column {
        Text(
            stringResource(R.string.text_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        PanelActions {
            PrimaryPanelButton(
                text = stringResource(R.string.text_add),
                // Un + y no el check: el check de verdad (confirmar y cerrar) esta en la cabecera.
                icon = Icons.Filled.Add,
                onClick = {
                    val placeholder = context.getString(R.string.text_placeholder)
                    val sizePx = (doc.height * 0.08f).coerceIn(24f, 400f)
                    // La caja se ajusta al texto inicial, no a un ancho fijo del lienzo:
                    // una palabra o una letra sola ya no sale con un marco enorme vacio.
                    val measured = android.graphics.Paint().apply { textSize = sizePx }
                        .measureText(placeholder)
                    val spec = TextSpec(
                        text = placeholder,
                        boxWidth = (measured + sizePx).coerceIn(sizePx * 2f, doc.width * 0.9f),
                        sizePx = sizePx,
                        // Negro sobre fondos claros, blanco sobre fondos oscuros: evita un
                        // texto blanco invisible sobre un lienzo blanco (requisito del
                        // usuario).
                        color = readableTextColor(doc, composite),
                    )
                    addCentered(
                        vm = vm,
                        doc = doc,
                        name = context.getString(R.string.layer_text),
                        content = LayerContent.Text(spec),
                        rasterizer = rasterizer,
                    )
                },
            )
        }
    }
}

/**
 * Insertar emoji como elemento independiente del menu rapido "+" (requisito del usuario):
 * ya no vive anidado dentro de la creacion de texto.
 */
@Composable
fun EmojiInsertDialog(vm: EditorViewModel, doc: PixaDocument, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val rasterizer = remember(context) { context.pixa.rasterizer }
    EmojiPickerDialog(
        onDismiss = onDismiss,
        onPick = { emoji ->
            addCentered(
                vm = vm,
                doc = doc,
                name = context.getString(R.string.text_emoji),
                content = LayerContent.Emoji(
                    emoji = emoji,
                    sizePx = (doc.height * 0.25f).coerceIn(64f, 1024f),
                ),
                rasterizer = rasterizer,
            )
        },
    )
}

/**
 * Negro o blanco segun lo que haya detras: una muestra pequena del compuesto actual (o,
 * si no hay, el color de fondo del lienzo) decide cual de los dos se lee mejor.
 */
private fun readableTextColor(doc: PixaDocument, composite: Bitmap?): Int {
    val sample = if (composite != null && !composite.isRecycled) {
        val thumb = BitmapUtils.thumbnail(composite, 8)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (y in 0 until thumb.height) {
            for (x in 0 until thumb.width) {
                val c = thumb.getPixel(x, y)
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
            }
        }
        if (n == 0) doc.background.color else Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    } else {
        doc.background.color
    }
    val luminance = 0.299 * Color.red(sample) + 0.587 * Color.green(sample) + 0.114 * Color.blue(sample)
    return if (luminance > 160) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}

/** Anade la capa centrada en el lienzo y la deja seleccionada. */
private fun addCentered(
    vm: EditorViewModel,
    doc: PixaDocument,
    name: String,
    content: LayerContent,
    rasterizer: LayerRasterizer,
) {
    val layer = PixaLayer(name = name, content = content)
    vm.addLayer(R.string.layer_add, layer.copy(transform = LayerGeometry.centered(layer, rasterizer, doc)))
}

/**
 * Buscador de emoji (requisito del usuario): nada de campo que meta texto literal como si
 * fuera un emoji, solo un buscador que filtra el catalogo al vuelo en espanol e ingles.
 */
@Composable
private fun EmojiPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        val needle = normalizeForSearch(query)
        if (needle.isBlank()) {
            // Requisito del usuario: sin busqueda solo el catalogo curado (ligero), no
            // los ~1150 extra — eso es lo que colgaba el telefono al desplazar.
            CuratedEmoji
        } else {
            EmojiCatalog.filter { normalizeForSearch(it.keywords).contains(needle) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_emoji)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 560.dp)) {
                TextFieldRow(
                    label = stringResource(R.string.emoji_search_hint),
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (results.isEmpty()) {
                    Text(
                        stringResource(R.string.emoji_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = ComposeTextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.heightIn(max = 460.dp),
                    ) {
                        gridItems(results, key = { it.char }) { entry ->
                            Text(
                                text = entry.char,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = ComposeTextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPick(entry.char)
                                        onDismiss()
                                    }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** Minusculas y sin acentos: asi "camara" encuentra "cámara" y viceversa. */
private fun normalizeForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{M}"), "")
}

// ======================================================================================
// Edicion del texto seleccionado
// ======================================================================================

@Composable
private fun TextEditor(
    vm: EditorViewModel,
    doc: PixaDocument,
    ui: EditorUi,
    settings: PixaSettings,
    layer: PixaLayer,
    spec: TextSpec,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fontsOpen by remember { mutableStateOf(false) }
    val alignNames = remember(context) {
        TextAlign.entries.associateWith { context.getString(EditorLabels.textAlign(it)) }
    }
    val gradient = spec.fill as? FillSpec.Gradient
    val gradientNames = remember(context) {
        GradientKind.entries.associateWith { context.getString(EditorLabels.gradient(it)) }
    }

    fun change(block: (TextSpec) -> TextSpec) {
        vm.updateLayer(R.string.text_title, layer.id) {
            val current = it.content
            if (current is LayerContent.Text) {
                it.copy(content = LayerContent.Text(block(current.spec)))
            } else {
                it
            }
        }
    }

    val imagePicker = rememberImagePicker { uri ->
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { BitmapUtils.decodeUri(context, uri, 2048) }.getOrNull()
            }
            if (bitmap == null) {
                vm.post(R.string.error_load_image)
            } else {
                change { it.copy(fill = FillSpec.Image(bitmapId = vm.putBitmap(bitmap))) }
                vm.post(R.string.text_image_fill_hint)
            }
        }
    }

    Column {
        TextFieldRow(
            label = stringResource(R.string.text_title),
            value = spec.text,
            onValueChange = { value -> change { it.copy(text = value) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            singleLine = false,
            placeholder = stringResource(R.string.text_placeholder),
        )
        Text(
            stringResource(R.string.text_multiline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        PanelActions {
            TextButton(onClick = { vm.rasterizeLayer(layer.id) }) {
                Text(stringResource(R.string.text_rasterize))
            }
        }

        TextPresetsSection(vm) { block -> change(block) }

        // Requisito del usuario: la fuente (con su nombre resaltado para que se note que
        // se puede tocar) a la izquierda, y la alineacion del texto a la derecha de la
        // misma fila, en vez de dos filas separadas.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.font_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                spec.font.family,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { fontsOpen = true },
            )
            Spacer(Modifier.weight(1f))
            TextAlign.entries.forEach { align ->
                val selected = spec.align == align
                IconButton(onClick = { change { it.copy(align = align) } }) {
                    Icon(
                        when (align) {
                            TextAlign.LEFT -> Icons.Filled.FormatAlignLeft
                            TextAlign.CENTER -> Icons.Filled.FormatAlignCenter
                            TextAlign.RIGHT -> Icons.Filled.FormatAlignRight
                        },
                        contentDescription = alignNames[align],
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        SliderRow(
            label = stringResource(R.string.label_size),
            value = spec.sizePx,
            onValueChange = { value -> change { it.copy(sizePx = value) } },
            valueRange = 8f..(doc.height * 0.9f).coerceAtLeast(64f),
            valueText = "${spec.sizePx.roundToInt()} px",
        )
        ColorRow(
            label = stringResource(R.string.label_color),
            color = spec.color,
            settings = settings,
            onPick = { picked -> change { it.copy(color = picked) } },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                StyleToggle(R.string.text_bold, spec.bold) { v -> change { it.copy(bold = v) } }
                StyleToggle(R.string.text_italic, spec.italic) { v -> change { it.copy(italic = v) } }
                StyleToggle(R.string.text_uppercase, spec.allCaps) { v -> change { it.copy(allCaps = v) } }
            }
            Column(modifier = Modifier.weight(1f)) {
                StyleToggle(R.string.text_underline, spec.underline) { v -> change { it.copy(underline = v) } }
                StyleToggle(R.string.text_strike, spec.strikethrough) { v -> change { it.copy(strikethrough = v) } }
            }
        }

        SliderRow(
            label = stringResource(R.string.text_line_height),
            value = spec.lineHeightMul,
            onValueChange = { value -> change { it.copy(lineHeightMul = value) } },
            valueRange = 0.7f..3f,
            valueText = formatValue(spec.lineHeightMul),
            defaultValue = 1.15f,
        )
        SliderRow(
            label = stringResource(R.string.text_letter_spacing),
            value = spec.letterSpacingEm,
            onValueChange = { value -> change { it.copy(letterSpacingEm = value) } },
            valueRange = -0.1f..0.5f,
            valueText = formatValue(spec.letterSpacingEm),
            defaultValue = 0f,
        )
        IntSliderRow(
            label = stringResource(R.string.label_width),
            value = spec.boxWidth.roundToInt(),
            onValueChange = { value -> change { it.copy(boxWidth = value.toFloat()) } },
            range = 32..(doc.width * 2),
            suffix = " px",
        )

        SectionLabel(R.string.text_outline)
        SliderRow(
            label = stringResource(R.string.text_outline),
            value = spec.outlineWidth,
            onValueChange = { value -> change { it.copy(outlineWidth = value) } },
            valueRange = 0f..40f,
            valueText = formatValue(spec.outlineWidth),
            defaultValue = 0f,
        )
        if (spec.outlineWidth > 0f) {
            ColorRow(
                label = stringResource(R.string.label_color),
                color = spec.outlineColor,
                settings = settings,
                onPick = { picked -> change { it.copy(outlineColor = picked) } },
            )
        }
        // Contornos adicionales, apilados hacia fuera (por ejemplo, blanco y luego negro).
        spec.extraOutlines.forEachIndexed { index, outline ->
            Text(
                stringResource(R.string.text_outline_n, index + 2),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
            SliderRow(
                label = stringResource(R.string.label_size),
                value = outline.width,
                onValueChange = { value ->
                    change { s -> s.copy(extraOutlines = s.extraOutlines.mapIndexed { i, o -> if (i == index) o.copy(width = value) else o }) }
                },
                valueRange = 0f..30f,
                valueText = formatValue(outline.width),
                defaultValue = 6f,
            )
            ColorRow(
                label = stringResource(R.string.label_color),
                color = outline.color,
                settings = settings,
                onPick = { picked ->
                    change { s -> s.copy(extraOutlines = s.extraOutlines.mapIndexed { i, o -> if (i == index) o.copy(color = picked) else o }) }
                },
            )
            PanelActions {
                TextButton(onClick = {
                    change { s -> s.copy(extraOutlines = s.extraOutlines.filterIndexed { i, _ -> i != index }) }
                }) { Text(stringResource(R.string.action_remove)) }
            }
        }
        PanelActions {
            TextButton(
                enabled = spec.extraOutlines.size < 3,
                onClick = {
                    change { s ->
                        s.copy(
                            outlineWidth = if (s.outlineWidth <= 0f) 6f else s.outlineWidth,
                            extraOutlines = s.extraOutlines + TextOutline(width = 6f, color = 0xFFFFFFFF.toInt()),
                        )
                    }
                },
            ) { Text(stringResource(R.string.text_outline_add)) }
        }

        SectionLabel(R.string.text_shadow)
        SliderRow(
            label = stringResource(R.string.label_radius),
            value = spec.shadowRadius,
            onValueChange = { value -> change { it.copy(shadowRadius = value) } },
            valueRange = 0f..60f,
            valueText = formatValue(spec.shadowRadius),
            defaultValue = 0f,
        )
        if (spec.shadowRadius > 0f) {
            SliderRow(
                label = "X",
                value = spec.shadowDx,
                onValueChange = { value -> change { it.copy(shadowDx = value) } },
                valueRange = -60f..60f,
                valueText = formatValue(spec.shadowDx),
                defaultValue = 0f,
            )
            SliderRow(
                label = "Y",
                value = spec.shadowDy,
                onValueChange = { value -> change { it.copy(shadowDy = value) } },
                valueRange = -60f..60f,
                valueText = formatValue(spec.shadowDy),
                defaultValue = 0f,
            )
            ColorRow(
                label = stringResource(R.string.label_color),
                color = spec.shadowColor,
                settings = settings,
                onPick = { picked -> change { it.copy(shadowColor = picked) } },
            )
        }

        SectionLabel(R.string.text_box)
        ColorRow(
            label = stringResource(R.string.label_color),
            color = spec.boxColor,
            settings = settings,
            onPick = { picked -> change { it.copy(boxColor = picked) } },
        )
        SliderRow(
            label = stringResource(R.string.label_padding),
            value = spec.boxPadding,
            onValueChange = { value -> change { it.copy(boxPadding = value) } },
            valueRange = 0f..120f,
            valueText = formatValue(spec.boxPadding),
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.label_radius),
            value = spec.boxCornerRadius,
            onValueChange = { value -> change { it.copy(boxCornerRadius = value) } },
            valueRange = 0f..120f,
            valueText = formatValue(spec.boxCornerRadius),
            defaultValue = 0f,
        )

        SectionLabel(R.string.label_gradient)
        ToggleRow(
            label = stringResource(R.string.text_gradient_fill),
            checked = gradient != null,
            onCheckedChange = { on ->
                change {
                    it.copy(
                        fill = if (on) {
                            FillSpec.Gradient(
                                stops = listOf(
                                    GradientStop(0f, it.color),
                                    GradientStop(1f, 0xFFFFFFFF.toInt()),
                                ),
                            )
                        } else {
                            null
                        },
                    )
                }
            },
        )
        if (gradient != null) {
            OptionRow(
                options = GradientKind.entries,
                selected = gradient.kind,
                onSelect = { kind -> change { it.copy(fill = gradient.copy(kind = kind)) } },
                label = { gradientNames[it].orEmpty() },
            )
            GradientStopsEditor(
                gradient = gradient,
                settings = settings,
                onChange = { next -> change { it.copy(fill = next) } },
            )
        }
        SectionLabel(R.string.text_texture)
        OptionRow(
            options = TextTexture.entries,
            selected = null,
            onSelect = { tex ->
                scope.launch {
                    val bmp = withContext(Dispatchers.Default) { TextTextures.make(tex) }
                    change { it.copy(fill = FillSpec.Image(bitmapId = vm.putBitmap(bmp))) }
                }
            },
            label = { context.getString(textureName(it)) },
        )
        PanelActions {
            TextButton(onClick = imagePicker) {
                Text(stringResource(R.string.text_image_fill))
            }
            if (spec.fill is FillSpec.Image) {
                TextButton(onClick = { change { it.copy(fill = null) } }) {
                    Text(stringResource(R.string.action_remove))
                }
            }
        }

        SectionLabel(R.string.text_shape)
        OptionRow(
            options = TextShape.entries,
            selected = spec.shape,
            onSelect = { shape -> change { it.copy(shape = shape, path = null) } },
            label = { context.getString(shapeName(it)) },
        )
        if (spec.shape != TextShape.NONE) {
            SliderRow(
                label = stringResource(R.string.label_amount),
                value = spec.shapeAmount,
                onValueChange = { value -> change { it.copy(shapeAmount = value) } },
                valueText = formatValue(spec.shapeAmount),
                defaultValue = 0.6f,
            )
        }

        SectionLabel(R.string.text_arc)
        SliderRow(
            label = stringResource(R.string.text_arc_amount),
            value = spec.arc,
            onValueChange = { value -> change { it.copy(arc = value, shape = TextShape.NONE) } },
            valueRange = -1f..1f,
            valueText = formatValue(spec.arc),
            defaultValue = 0f,
        )
        Text(
            stringResource(R.string.text_on_path_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        PanelActions {
            TextButton(
                onClick = {
                    ui.tool = CanvasTool.TEXT_PATH
                    ui.resetPolygon()
                },
            ) { Text(stringResource(R.string.text_on_path)) }
            if (spec.path != null) {
                TextButton(onClick = { change { it.copy(path = null) } }) {
                    Text(stringResource(R.string.action_remove))
                }
            }
        }
    }

    if (fontsOpen) {
        FontPickerDialog(
            current = spec.font,
            onDismiss = { fontsOpen = false },
            onPick = { ref -> change { it.copy(font = ref) } },
        )
    }
}

@Composable
private fun SectionLabel(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun StyleToggle(
    res: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ToggleRow(
        label = stringResource(res),
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
    )
}

/**
 * Editor de paradas del degradado (requisito 11, compartido con formas y rellenos).
 *
 * Las paradas se mantienen ordenadas por posicion porque el shader de Android exige
 * posiciones crecientes; si no, el degradado sale invertido o lanza excepcion.
 */
@Composable
fun GradientStopsEditor(
    gradient: FillSpec.Gradient,
    settings: PixaSettings,
    onChange: (FillSpec.Gradient) -> Unit,
) {
    Column {
        GradientPresetGrid { colors ->
            onChange(
                gradient.copy(
                    stops = colors.mapIndexed { i, c ->
                        GradientStop(i / (colors.size - 1).coerceAtLeast(1).toFloat(), c)
                    },
                ),
            )
        }
        gradient.stops.forEachIndexed { index, stop ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    ColorRow(
                        label = "${index + 1}",
                        color = stop.color,
                        settings = settings,
                        onPick = { picked ->
                            onChange(gradient.withStop(index, stop.copy(color = picked)))
                        },
                    )
                    SliderRow(
                        label = stringResource(R.string.label_spacing),
                        value = stop.position,
                        onValueChange = { value ->
                            onChange(gradient.withStop(index, stop.copy(position = value)))
                        },
                        valueText = formatValue(stop.position),
                    )
                }
                IconButton(
                    enabled = gradient.stops.size > 2,
                    onClick = {
                        onChange(
                            gradient.copy(
                                stops = gradient.stops.filterIndexed { i, _ -> i != index },
                            ),
                        )
                    },
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_remove),
                    )
                }
            }
        }
        SliderRow(
            label = stringResource(R.string.label_angle),
            value = gradient.angle,
            onValueChange = { onChange(gradient.copy(angle = it)) },
            valueRange = 0f..360f,
            valueText = "${gradient.angle.roundToInt()}°",
            defaultValue = 0f,
        )
        PanelActions {
            TextButton(
                enabled = gradient.stops.size < 8,
                onClick = {
                    val last = gradient.stops.last()
                    onChange(
                        gradient.copy(
                            stops = (gradient.stops + GradientStop(1f, last.color)).sortedBy { it.position },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_add)) }
        }
    }
}

private fun FillSpec.Gradient.withStop(index: Int, stop: GradientStop): FillSpec.Gradient = copy(
    stops = stops.toMutableList().also { it[index] = stop }.sortedBy { it.position },
)

// ======================================================================================
// Selector de fuentes
// ======================================================================================

@Composable
fun FontPickerDialog(
    current: FontRef,
    onDismiss: () -> Unit,
    onPick: (FontRef) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { context.pixa.userData }
    val fonts = remember(context) { context.pixa.fonts }
    val userData by store.data.collectAsStateWithLifecycle()
    var importing by remember { mutableStateOf(false) }
    var browsingGoogleFonts by remember { mutableStateOf(false) }

    // Un solo selector para .ttf/.otf sueltos o un .zip con varios (requisito del
    // usuario: un unico boton "Importar", no dos separados segun el tipo de archivo).
    val picker = rememberDocumentPicker(
        mimeTypes = arrayOf(
            "font/ttf",
            "font/otf",
            "application/x-font-ttf",
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream",
        ),
    ) { uri ->
        importing = true
        scope.launch {
            val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
            val isZip = name.substringAfterLast('.', "").equals("zip", ignoreCase = true)
            if (isZip) {
                val refs = runCatching { store.importFontsFromZip(uri) }.getOrNull()
                importing = false
                if (refs.isNullOrEmpty()) onDismiss() else onPick(refs.first())
            } else {
                val ref = runCatching {
                    store.importFont(uri, name.ifBlank { "fuente.ttf" })
                }.getOrNull()
                importing = false
                if (ref == null) onDismiss() else onPick(ref)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                    }
                    Text(
                        stringResource(R.string.text_font_picker),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { browsingGoogleFonts = true }) {
                        Text(stringResource(R.string.font_browse_button))
                    }
                    TextButton(enabled = !importing, onClick = picker) {
                        Text(stringResource(R.string.text_font_import_zip))
                    }
                }
                // Requisito del usuario: con el catalogo completo deja de caber en
                // pantalla, asi que un aviso arriba deja claro que hay que desplazar.
                Text(
                    stringResource(R.string.font_scroll_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    // Margen inferior para que la ultima fuente no quede oculta detras
                    // de la barra de navegacion de Android.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(FontRepository.CATALOG) { family ->
                        FontRowItem(
                            label = family.label,
                            family = fonts.composeFamily(family.query),
                            selected = current.source == FontRef.Source.GOOGLE &&
                                current.family == family.query,
                            onClick = {
                                // Se pide la descarga al elegirla: el renderizador dibuja
                                // con la fuente del sistema hasta que llega y se repinta
                                // solo.
                                val ref = FontRef(
                                    family = family.query,
                                    source = FontRef.Source.GOOGLE,
                                )
                                scope.launch { fonts.load(ref) }
                                onPick(ref)
                                onDismiss()
                            },
                        )
                    }
                    if (userData.fonts.isNotEmpty()) {
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                        items(userData.fonts) { font ->
                            ImportedFontRow(
                                font = font,
                                fontFile = store.fontFile(font.fileName),
                                selected = current.source == FontRef.Source.IMPORTED &&
                                    current.family == font.family,
                                onClick = {
                                    onPick(store.fontRefFor(font))
                                    onDismiss()
                                },
                                onDelete = { scope.launch { store.deleteFont(font.fileName) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (browsingGoogleFonts) {
        GoogleFontsBrowserDialog(
            onDismiss = { browsingGoogleFonts = false },
            onPick = { ref ->
                browsingGoogleFonts = false
                onPick(ref)
                onDismiss()
            },
        )
    }
}

@Composable
fun FontRowItem(
    label: String,
    family: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        fontFamily = family,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun ImportedFontRow(
    font: ImportedFont,
    fontFile: File,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    // Requisito del usuario: tambien con vista previa, igual que las de Google.
    val preview = remember(fontFile) {
        runCatching { FontFamily(Font(fontFile)) }.getOrNull()
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = font.family,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = preview,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
            )
        }
    }
}

@androidx.annotation.StringRes
private fun textureName(t: TextTexture): Int = when (t) {
    TextTexture.GOLD -> R.string.tex_gold
    TextTexture.SILVER -> R.string.tex_silver
    TextTexture.WOOD -> R.string.tex_wood
    TextTexture.MARBLE -> R.string.tex_marble
    TextTexture.FIRE -> R.string.tex_fire
    TextTexture.ICE -> R.string.tex_ice
    TextTexture.COPPER -> R.string.tex_copper
    TextTexture.BRONZE -> R.string.tex_bronze
    TextTexture.ROSE_GOLD -> R.string.tex_rose_gold
    TextTexture.CHROME -> R.string.tex_chrome
    TextTexture.EMERALD -> R.string.tex_emerald
    TextTexture.LAVA -> R.string.tex_lava
    TextTexture.CARBON -> R.string.tex_carbon
    TextTexture.GALAXY -> R.string.tex_galaxy
    TextTexture.CONCRETE -> R.string.tex_concrete
    TextTexture.CAMO -> R.string.tex_camo
    TextTexture.WATER -> R.string.tex_water
    TextTexture.GRASS -> R.string.tex_grass
    TextTexture.HOLO -> R.string.tex_holo
    TextTexture.LEATHER -> R.string.tex_leather
}

@androidx.annotation.StringRes
private fun shapeName(s: TextShape): Int = when (s) {
    TextShape.NONE -> R.string.text_shape_none
    TextShape.CIRCLE -> R.string.text_shape_circle
    TextShape.WAVE -> R.string.text_shape_wave
}
