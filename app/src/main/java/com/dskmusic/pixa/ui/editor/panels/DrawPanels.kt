package com.dskmusic.pixa.ui.editor.panels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.draw.BrushKind
import com.dskmusic.pixa.core.draw.SymmetryMode
import com.dskmusic.pixa.core.model.ArrowHeads
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.GradientKind
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.ShapeKind
import com.dskmusic.pixa.core.model.ShapeSpec
import com.dskmusic.pixa.core.model.StrokeStyle
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.data.settings.PixaSettings
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================================================
// Dibujo (requisito 11)
// ======================================================================================

@Composable
fun DrawPanel(vm: EditorViewModel, doc: PixaDocument, ui: EditorUi, settings: PixaSettings) {
    val context = LocalContext.current
    val brush = ui.brush
    val symmetryNames = remember(context) {
        SymmetryMode.entries.associateWith { context.getString(EditorLabels.symmetry(it)) }
    }
    val gradientNames = remember(context) {
        GradientKind.entries.associateWith { context.getString(EditorLabels.gradient(it)) }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val kindIcons = mapOf(
                BrushKind.BRUSH to Icons.Filled.Brush,
                BrushKind.PENCIL to Icons.Filled.Create,
                BrushKind.AIRBRUSH to Icons.Filled.Grain,
                BrushKind.ERASER to Icons.Filled.Backspace,
            )
            BrushKind.entries.forEach { kind ->
                ToolButton(
                    icon = kindIcons.getValue(kind),
                    label = stringResource(EditorLabels.brush(kind)),
                    selected = ui.tool == CanvasTool.PAINT && brush.kind == kind,
                    onClick = {
                        ui.brush = brush.copy(kind = kind)
                        ui.tool = CanvasTool.PAINT
                    },
                )
            }
            ToolButton(
                icon = Icons.Filled.Colorize,
                label = stringResource(R.string.tool_eyedropper),
                selected = ui.tool == CanvasTool.EYEDROPPER,
                onClick = { ui.tool = CanvasTool.EYEDROPPER },
            )
            ToolButton(
                icon = Icons.Filled.FormatColorFill,
                label = stringResource(R.string.tool_bucket),
                selected = ui.tool == CanvasTool.BUCKET,
                onClick = { ui.tool = CanvasTool.BUCKET },
            )
            ToolButton(
                icon = Icons.Filled.Gradient,
                label = stringResource(R.string.tool_gradient),
                selected = ui.tool == CanvasTool.GRADIENT,
                onClick = { ui.tool = CanvasTool.GRADIENT },
            )
        }

        if (ui.tool == CanvasTool.PAINT) {
            IntSliderRow(
                label = stringResource(R.string.label_size),
                value = brush.size.toInt(),
                onValueChange = { ui.brush = brush.copy(size = it.toFloat()) },
                // Requisito del usuario: el tope del pincel baja de 400 a 100 px.
                range = 1..100,
                suffix = " px",
            )
            SliderRow(
                label = stringResource(R.string.brush_hardness),
                value = brush.hardness,
                onValueChange = { ui.brush = brush.copy(hardness = it) },
                valueText = formatPercent(brush.hardness),
                defaultValue = 0.8f,
            )
            SliderRow(
                label = stringResource(R.string.label_opacity),
                value = brush.opacity,
                onValueChange = { ui.brush = brush.copy(opacity = it) },
                valueText = formatPercent(brush.opacity),
                defaultValue = 1f,
            )
            SliderRow(
                label = stringResource(R.string.brush_flow),
                value = brush.flow,
                onValueChange = { ui.brush = brush.copy(flow = it) },
                valueText = formatPercent(brush.flow),
                defaultValue = 1f,
            )
            SliderRow(
                label = stringResource(R.string.brush_smoothing),
                value = brush.smoothing,
                onValueChange = { ui.brush = brush.copy(smoothing = it) },
                valueText = formatPercent(brush.smoothing),
                defaultValue = 0.5f,
            )
            Text(
                stringResource(R.string.brush_symmetry),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            OptionRow(
                options = SymmetryMode.entries,
                selected = brush.symmetry,
                onSelect = { ui.brush = brush.copy(symmetry = it) },
                label = { symmetryNames[it].orEmpty() },
            )
            if (brush.symmetry == SymmetryMode.RADIAL) {
                IntSliderRow(
                    label = stringResource(R.string.symmetry_axes),
                    value = brush.radialSegments,
                    onValueChange = { ui.brush = brush.copy(radialSegments = it) },
                    range = 2..24,
                    defaultValue = 6,
                )
            }
        }

        if (ui.tool == CanvasTool.BUCKET) {
            SliderRow(
                label = stringResource(R.string.label_tolerance),
                value = ui.wandTolerance,
                onValueChange = { ui.wandTolerance = it },
                valueRange = 0f..0.6f,
                valueText = formatPercent(ui.wandTolerance),
                defaultValue = 0.12f,
            )
            ToggleRow(
                label = stringResource(R.string.select_contiguous),
                checked = ui.wandContiguous,
                onCheckedChange = { ui.wandContiguous = it },
            )
        }

        if (ui.tool == CanvasTool.GRADIENT) {
            Text(
                stringResource(R.string.gradient_stops),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            OptionRow(
                options = GradientKind.entries,
                selected = ui.gradient.kind,
                onSelect = { ui.gradient = ui.gradient.copy(kind = it) },
                label = { gradientNames[it].orEmpty() },
            )
            GradientStopsEditor(
                gradient = ui.gradient,
                settings = settings,
                onChange = { ui.gradient = it },
            )
        }

        if (brush.kind != BrushKind.ERASER || ui.tool != CanvasTool.PAINT) {
            ColorRow(
                label = stringResource(R.string.label_color),
                color = ui.color,
                settings = settings,
                onPick = { ui.color = it },
            )
        }

        PanelActions {
            TextButton(
                onClick = {
                    // Capa de dibujo propia: pintar sobre una foto es destructivo y lo
                    // normal es querer el trazo aparte para poder borrarlo luego.
                    val empty = BitmapUtils.create(doc.width, doc.height)
                    vm.addRasterLayer(
                        bitmap = empty,
                        name = context.getString(R.string.layer_draw),
                        labelRes = R.string.layer_add,
                    )
                    ui.tool = CanvasTool.PAINT
                },
            ) { Text(stringResource(R.string.layer_draw)) }
        }
    }
}

// ======================================================================================
// Formas vectoriales (requisito 11)
// ======================================================================================

@Composable
fun ShapePanel(vm: EditorViewModel, doc: PixaDocument, ui: EditorUi, settings: PixaSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = ui.shape
    val kindNames = remember(context) {
        ShapeKind.entries.associateWith { context.getString(EditorLabels.shape(it)) }
    }
    val gradientNames = remember(context) {
        GradientKind.entries.associateWith { context.getString(EditorLabels.gradient(it)) }
    }
    val selectedShape = doc.selected?.content as? LayerContent.Shape

    // Las ediciones afectan a la forma seleccionada si hay una, y si no a la plantilla que
    // se usara al dibujar la siguiente. Asi el panel sirve para crear y para retocar.
    fun change(block: (ShapeSpec) -> ShapeSpec) {
        ui.shape = block(ui.shape)
        val layer = doc.selected
        if (layer != null && selectedShape != null) {
            vm.updateLayer(R.string.shape_title, layer.id) {
                val current = it.content
                if (current is LayerContent.Shape) {
                    it.copy(content = LayerContent.Shape(block(current.spec)))
                } else {
                    it
                }
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
            }
        }
    }

    val effective = selectedShape?.spec ?: spec
    val shapeGradient = effective.fill as? FillSpec.Gradient

    Column {
        OptionRow(
            options = ShapeKind.entries,
            selected = effective.kind,
            onSelect = { kind ->
                change {
                    val needsStroke = kind == ShapeKind.LINE || kind == ShapeKind.ARROW
                    it.copy(
                        kind = kind,
                        // Sin area que rellenar, la linea (y la flecha, si se deja sin
                        // relleno) dependen del trazo: sin esto quedarian invisibles con
                        // el strokeWidth por defecto en 0.
                        strokeWidth = if (needsStroke && it.strokeWidth <= 0f) 6f else it.strokeWidth,
                    )
                }
                ui.tool = CanvasTool.SHAPE_DRAW
            },
            label = { kindNames[it].orEmpty() },
        )
        if (effective.kind == ShapeKind.ARROW) {
            val headNames = remember(context) {
                mapOf(
                    ArrowHeads.END to context.getString(R.string.arrow_head_end),
                    ArrowHeads.START to context.getString(R.string.arrow_head_start),
                    ArrowHeads.BOTH to context.getString(R.string.arrow_head_both),
                )
            }
            OptionRow(
                options = ArrowHeads.entries,
                selected = effective.arrowHeads,
                onSelect = { heads -> change { it.copy(arrowHeads = heads) } },
                label = { headNames[it].orEmpty() },
            )
        }
        // Tras dibujar o tocar dos veces una forma queda seleccionada y la herramienta es
        // Mover; este boton vuelve a dibujar para crear otra.
        if (ui.tool != CanvasTool.SHAPE_DRAW) {
            PanelActions {
                TextButton(
                    onClick = {
                        vm.selectLayer(null)
                        ui.tool = CanvasTool.SHAPE_DRAW
                    },
                ) { Text(stringResource(R.string.shape_draw_another)) }
            }
        }
        Text(
            stringResource(R.string.shape_drag_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (effective.kind == ShapeKind.POLYGON || effective.kind == ShapeKind.STAR) {
            IntSliderRow(
                label = stringResource(R.string.shape_sides),
                value = effective.sides,
                onValueChange = { value -> change { it.copy(sides = value) } },
                range = 3..24,
                defaultValue = 5,
            )
        }
        if (effective.kind == ShapeKind.STAR) {
            SliderRow(
                label = stringResource(R.string.label_radius),
                value = effective.innerRatio,
                onValueChange = { value -> change { it.copy(innerRatio = value) } },
                valueRange = 0.1f..0.9f,
                valueText = formatPercent(effective.innerRatio),
                defaultValue = 0.5f,
            )
        }
        if (effective.kind == ShapeKind.RECT) {
            SliderRow(
                label = stringResource(R.string.label_radius),
                value = effective.cornerRadius,
                onValueChange = { value -> change { it.copy(cornerRadius = value) } },
                valueRange = 0f..200f,
                valueText = "${effective.cornerRadius.toInt()} px",
                defaultValue = 0f,
            )
        }

        Text(
            stringResource(R.string.shape_fill),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        ColorRow(
            label = stringResource(R.string.label_color),
            color = (effective.fill as? FillSpec.Solid)?.color ?: ui.color,
            settings = settings,
            onPick = { picked -> change { it.copy(fill = FillSpec.Solid(picked)) } },
        )
        ToggleRow(
            label = stringResource(R.string.label_gradient),
            checked = shapeGradient != null,
            onCheckedChange = { on ->
                change { it.copy(fill = if (on) ui.gradient else FillSpec.Solid(ui.color)) }
            },
        )
        if (shapeGradient != null) {
            OptionRow(
                options = GradientKind.entries,
                selected = shapeGradient.kind,
                onSelect = { kind -> change { it.copy(fill = shapeGradient.copy(kind = kind)) } },
                label = { gradientNames[it].orEmpty() },
            )
            GradientStopsEditor(
                gradient = shapeGradient,
                settings = settings,
                onChange = { next -> change { it.copy(fill = next) } },
            )
        }
        PanelActions {
            TextButton(onClick = imagePicker) {
                Text(stringResource(R.string.shape_image_fill))
            }
        }

        Text(
            stringResource(R.string.shape_stroke),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        SliderRow(
            label = stringResource(R.string.label_width),
            value = effective.strokeWidth,
            onValueChange = { value -> change { it.copy(strokeWidth = value) } },
            valueRange = 0f..60f,
            valueText = "${effective.strokeWidth.toInt()} px",
            defaultValue = 0f,
        )
        if (effective.strokeWidth > 0f) {
            ColorRow(
                label = stringResource(R.string.label_color),
                color = effective.strokeColor,
                settings = settings,
                onPick = { picked -> change { it.copy(strokeColor = picked) } },
            )
            val strokeStyleNames = remember(context) {
                mapOf(
                    StrokeStyle.SOLID to context.getString(R.string.stroke_style_solid),
                    StrokeStyle.DASHED to context.getString(R.string.stroke_style_dashed),
                    StrokeStyle.DOTTED to context.getString(R.string.stroke_style_dotted),
                )
            }
            OptionRow(
                options = StrokeStyle.entries,
                selected = effective.strokeStyle,
                onSelect = { style -> change { it.copy(strokeStyle = style) } },
                label = { strokeStyleNames[it].orEmpty() },
            )
        }

        PanelActions {
            PrimaryPanelButton(
                text = stringResource(R.string.action_add),
                onClick = {
                    val side = minOf(doc.width, doc.height) * 0.5f
                    addShapeLayer(
                        vm = vm,
                        doc = doc,
                        name = context.getString(R.string.layer_shape),
                        spec = ui.shape.copy(width = side, height = side),
                    )
                },
            )
        }
    }
}

/** Coloca una forma centrada en el lienzo, sin necesidad de arrastrar. */
private fun addShapeLayer(vm: EditorViewModel, doc: PixaDocument, name: String, spec: ShapeSpec) {
    vm.addLayer(
        R.string.layer_add,
        PixaLayer(
            name = name,
            content = LayerContent.Shape(spec),
            transform = Xf(
                tx = (doc.width - spec.width) / 2f,
                ty = (doc.height - spec.height) / 2f,
            ),
        ),
    )
}
