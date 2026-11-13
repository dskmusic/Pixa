package com.dskmusic.pixa.ui.editor.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.image.ToneOps
import com.dskmusic.pixa.core.model.CurvePoints
import com.dskmusic.pixa.core.model.CurvesSpec
import com.dskmusic.pixa.core.model.HslAdjust
import com.dskmusic.pixa.core.model.HslBand
import com.dskmusic.pixa.core.model.LevelsSpec
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ui.common.MinimizedPanelBar
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.common.formatValue
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** La pestana activa publica aqui su accion de aplicar, sin disparar recomposicion. */
private class ApplyHolder {
    var action: (() -> Unit)? = null
}

/** Las cuatro herramientas de color fino del requisito 13. */
private enum class ToneTab { CURVES, LEVELS, HSL, REPLACE }

/** Canal de la curva tonal. */
private enum class Channel { RGB, RED, GREEN, BLUE }

/**
 * [doc] no se lee aqui dentro: su unico trabajo es forzar la recomposicion al cambiar de
 * capa seleccionada. `vm` no cambia de identidad cuando cambia el documento, asi que un
 * panel que solo recibiera `vm` podia quedarse ensenando "elige una capa" para siempre tras
 * elegir una.
 */
@Composable
fun TonePanel(
    vm: EditorViewModel,
    doc: PixaDocument,
    settings: PixaSettings,
    title: String,
    help: String,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    minimized: Boolean,
    onMaximize: () -> Unit,
    scroll: ScrollState,
) {
    val preview = rememberLivePreview(vm)
    if (preview == null) {
        NeedsRasterNotice(vm)
        return
    }
    var tab by remember { mutableStateOf(ToneTab.CURVES) }
    val context = LocalContext.current
    val tabNames = remember(context) {
        ToneTab.entries.associateWith {
            context.getString(
                when (it) {
                    ToneTab.CURVES -> R.string.adjust_curves
                    ToneTab.LEVELS -> R.string.adjust_levels
                    ToneTab.HSL -> R.string.adjust_hsl
                    ToneTab.REPLACE -> R.string.adjust_replace_color
                },
            )
        }
    }

    // Requisito del usuario: el "Aplicar" sube a la cabecera, junto a ayuda y cerrar. Cada
    // pestana registra aqui su accion de aplicar y si tiene cambios pendientes, de modo que
    // el boton de arriba aplica siempre la pestana activa (igual que en filtros/efectos).
    //
    // La accion vive en un holder plano (sin estado Compose) para no forzar recomposiciones
    // en bucle; el booleano "hay cambios" es lo unico reactivo. El cierre del boton lee
    // holder.action en el momento de pulsarlo, asi nunca se queda con una version vieja.
    val applyHolder = remember { ApplyHolder() }
    var applyEnabled by remember { mutableStateOf(false) }
    val registerApply: ((() -> Unit)?, Boolean) -> Unit = { action, enabled ->
        applyHolder.action = action
        applyEnabled = enabled
    }
    val applyOnClick: (() -> Unit)? =
        if (applyEnabled) ({ applyHolder.action?.invoke() }) else null

    if (minimized) {
        MinimizedPanelBar(
            text = title,
            onMaximize = onMaximize,
            onClose = onClose,
            onApply = applyOnClick,
            applyEnabled = applyEnabled,
        )
    } else {
        SectionTitle(
            text = title,
            help = help,
            onClose = onClose,
            onMinimize = onMinimize,
            onApply = applyOnClick,
            applyEnabled = applyEnabled,
        )
    }

    Column(
        modifier = (if (minimized) Modifier.height(0.dp) else Modifier)
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(bottom = 4.dp),
    ) {
        OptionRow(
            options = ToneTab.entries,
            selected = tab,
            onSelect = {
                preview.cancel()
                tab = it
            },
            label = { tabNames[it].orEmpty() },
        )
        when (tab) {
            ToneTab.CURVES -> CurvesTab(preview, registerApply)
            ToneTab.LEVELS -> LevelsTab(preview, registerApply)
            ToneTab.HSL -> HslTab(preview, registerApply)
            ToneTab.REPLACE -> ReplaceColorTab(preview, settings, registerApply)
        }
    }
}

// ======================================================================================
// Curvas
// ======================================================================================

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun CurvesTab(preview: LivePreview, registerApply: (action: (() -> Unit)?, enabled: Boolean) -> Unit) {
    var spec by remember(preview) { mutableStateOf(CurvesSpec()) }
    var channel by remember { mutableStateOf(Channel.RGB) }
    val context = LocalContext.current
    val channelNames = remember(context) {
        Channel.entries.associateWith {
            context.getString(
                when (it) {
                    Channel.RGB -> R.string.channel_rgb
                    Channel.RED -> R.string.channel_red
                    Channel.GREEN -> R.string.channel_green
                    Channel.BLUE -> R.string.channel_blue
                },
            )
        }
    }
    val current = when (channel) {
        Channel.RGB -> spec.rgb
        Channel.RED -> spec.red
        Channel.GREEN -> spec.green
        Channel.BLUE -> spec.blue
    }

    fun change(points: CurvePoints) {
        spec = when (channel) {
            Channel.RGB -> spec.copy(rgb = points)
            Channel.RED -> spec.copy(red = points)
            Channel.GREEN -> spec.copy(green = points)
            Channel.BLUE -> spec.copy(blue = points)
        }
        val next = spec
        preview.update(R.string.adjust_curves) { ToneOps.applyCurves(it, next) }
    }

    // Publica la accion de aplicar en la cabecera del panel (requisito del usuario).
    SideEffect {
        if (spec.isIdentity) {
            registerApply(null, false)
        } else {
            registerApply(
                {
                    val applied = spec
                    preview.apply(R.string.adjust_curves) { ToneOps.applyCurves(it, applied) }
                    spec = CurvesSpec()
                },
                true,
            )
        }
    }

    // Compacto: curva a la izquierda y, a su derecha, canales, ayuda y reiniciar.
    val tint = when (channel) {
        Channel.RGB -> MaterialTheme.colorScheme.primary
        Channel.RED -> Color(0xFFE53935)
        Channel.GREEN -> Color(0xFF43A047)
        Channel.BLUE -> Color(0xFF1E88E5)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CurveEditor(points = current, tint = tint, onChange = ::change, sideDp = 176.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Channel.entries.forEach { c ->
                    androidx.compose.material3.FilterChip(
                        selected = c == channel,
                        onClick = { channel = c },
                        label = { Text(channelNames[c].orEmpty(), style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Text(
                stringResource(R.string.curve_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { change(CurvePoints()) }) {
                Text(stringResource(R.string.action_reset))
            }
        }
    }
}

/**
 * Editor de curva con puntos arrastrables.
 *
 * El lienzo es cuadrado y de lado fijo, asi que el paso de pixeles a 0..1 se puede hacer
 * con la densidad sin esperar a la medida: eso permite que el mismo tamano sirva para
 * dibujar y para interpretar los gestos.
 */
@Composable
private fun CurveEditor(
    points: CurvePoints,
    tint: Color,
    onChange: (CurvePoints) -> Unit,
    sideDp: androidx.compose.ui.unit.Dp = 220.dp,
) {
    val side = with(LocalDensity.current) { sideDp.toPx() }
    val grabRadius = with(LocalDensity.current) { 22.dp.toPx() }
    var dragIndex by remember { mutableStateOf(-1) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val description = stringResource(R.string.adjust_curves)

    fun toUnit(offset: Offset): Offset = Offset(
        (offset.x / side).coerceIn(0f, 1f),
        (1f - offset.y / side).coerceIn(0f, 1f),
    )

    Canvas(
        modifier = Modifier
            .size(sideDp)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { start ->
                        val p = toUnit(start)
                        dragIndex = nearestPoint(points, p, grabRadius / side)
                    },
                    onDragEnd = { dragIndex = -1 },
                    onDragCancel = { dragIndex = -1 },
                ) { change, _ ->
                    val index = dragIndex
                    if (index >= 0) onChange(points.moved(index, toUnit(change.position)))
                }
            }
            .pointerInput(points) {
                detectTapGestures(
                    onDoubleTap = { onChange(CurvePoints()) },
                    onTap = { tap ->
                        val p = toUnit(tap)
                        if (nearestPoint(points, p, grabRadius / side) < 0) {
                            onChange(points.added(p))
                        }
                    },
                )
            },
    ) {
        drawRect(color = surface)
        for (i in 1..3) {
            val f = i / 4f
            drawLine(grid, Offset(size.width * f, 0f), Offset(size.width * f, size.height))
            drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f))
        }
        // La curva se dibuja con el mismo muestreo que usa el motor (256 pasos de LUT),
        // para que lo que se ve coincida con lo que se aplica.
        val lut = ToneOps.curveLut(points)
        val path = Path()
        for (i in 0..255) {
            val x = i / 255f * size.width
            val y = (1f - lut[i] / 255f) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = tint, style = Stroke(width = 3f))
        for (i in 0 until points.points.size / 2) {
            val cx = points.points[i * 2] * size.width
            val cy = (1f - points.points[i * 2 + 1]) * size.height
            drawCircle(tint, radius = 9f, center = Offset(cx, cy))
        }
    }
}

/** Indice del punto de control mas cercano dentro del radio, o -1. */
private fun nearestPoint(points: CurvePoints, at: Offset, radius: Float): Int {
    var best = -1
    var bestDist = radius
    for (i in 0 until points.points.size / 2) {
        val dx = points.points[i * 2] - at.x
        val dy = points.points[i * 2 + 1] - at.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist <= bestDist) {
            bestDist = dist
            best = i
        }
    }
    return best
}

/**
 * Mueve un punto manteniendo el orden por x.
 *
 * Los extremos no cambian de x: si pudieran, la curva dejaria de cubrir todo el rango y
 * el motor tendria que extrapolar.
 */
private fun CurvePoints.moved(index: Int, to: Offset): CurvePoints {
    val list = points.toMutableList()
    val count = list.size / 2
    val y = to.y.coerceIn(0f, 1f)
    val x = when (index) {
        0 -> 0f
        count - 1 -> 1f
        else -> to.x.coerceIn(list[(index - 1) * 2] + 0.01f, list[(index + 1) * 2] - 0.01f)
    }
    list[index * 2] = x
    list[index * 2 + 1] = y
    return CurvePoints(list)
}

/** Inserta un punto en su sitio por x; ignora los que caerian pegados a otro. */
private fun CurvePoints.added(at: Offset): CurvePoints {
    val count = points.size / 2
    if (count >= 12) return this
    var insert = count
    for (i in 0 until count) {
        if (points[i * 2] > at.x) {
            insert = i
            break
        }
    }
    if (insert == 0 || insert == count) return this
    if (abs(points[(insert - 1) * 2] - at.x) < 0.02f) return this
    val list = points.toMutableList()
    list.add(insert * 2, at.y.coerceIn(0f, 1f))
    list.add(insert * 2, at.x)
    return CurvePoints(list)
}

// ======================================================================================
// Niveles
// ======================================================================================

@Composable
private fun LevelsTab(preview: LivePreview, registerApply: (action: (() -> Unit)?, enabled: Boolean) -> Unit) {
    var spec by remember(preview) { mutableStateOf(LevelsSpec()) }
    val histogram by produceState<ToneOps.Histogram?>(initialValue = null, key1 = preview) {
        value = withContext(Dispatchers.Default) { ToneOps.histogram(preview.proxy) }
    }

    fun change(next: LevelsSpec) {
        spec = next
        preview.update(R.string.adjust_levels) { ToneOps.applyLevels(it, next) }
    }

    SideEffect {
        if (spec.isIdentity) {
            registerApply(null, false)
        } else {
            registerApply(
                {
                    val applied = spec
                    preview.apply(R.string.adjust_levels) { ToneOps.applyLevels(it, applied) }
                    spec = LevelsSpec()
                },
                true,
            )
        }
    }

    Column {
        histogram?.let { HistogramView(it) }
        SliderRow(
            label = stringResource(R.string.levels_black),
            value = spec.black,
            onValueChange = { change(spec.copy(black = it.coerceAtMost(spec.white - 0.02f))) },
            valueText = formatPercent(spec.black),
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.levels_white),
            value = spec.white,
            onValueChange = { change(spec.copy(white = it.coerceAtLeast(spec.black + 0.02f))) },
            valueText = formatPercent(spec.white),
            defaultValue = 1f,
        )
        SliderRow(
            label = stringResource(R.string.levels_gamma),
            value = spec.gamma,
            onValueChange = { change(spec.copy(gamma = it)) },
            valueRange = 0.2f..3f,
            valueText = formatValue(spec.gamma),
            defaultValue = 1f,
        )
        PanelActions {
            TextButton(onClick = { change(LevelsSpec()) }) {
                Text(stringResource(R.string.action_reset))
            }
        }
    }
}

/** Histograma de los tres canales superpuestos, como referencia para los niveles. */
@Composable
private fun HistogramView(histogram: ToneOps.Histogram) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val description = stringResource(R.string.adjust_levels)
    Canvas(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(90.dp)
            .semantics { contentDescription = description },
    ) {
        drawRect(surface)
        val top = histogram.maxBin.coerceAtLeast(1).toFloat()
        val step = size.width / 256f
        listOf(
            histogram.red to Color(0x66E53935),
            histogram.green to Color(0x6643A047),
            histogram.blue to Color(0x661E88E5),
        ).forEach { (bins, color) ->
            for (i in 0 until 256) {
                val h = bins[i] / top * size.height
                drawRect(
                    color = color,
                    topLeft = Offset(i * step, size.height - h),
                    size = Size(step + 0.5f, h),
                )
            }
        }
    }
}

// ======================================================================================
// HSL por color
// ======================================================================================

@Composable
private fun HslTab(preview: LivePreview, registerApply: (action: (() -> Unit)?, enabled: Boolean) -> Unit) {
    var bands by remember(preview) { mutableStateOf(mapOf<HslBand, HslAdjust>()) }
    var band by remember { mutableStateOf(HslBand.RED) }
    val context = LocalContext.current
    val bandNames = remember(context) {
        HslBand.entries.associateWith { context.getString(EditorLabels.hslBand(it)) }
    }
    val current = bands[band] ?: HslAdjust()

    fun change(next: HslAdjust) {
        bands = if (next.isIdentity) bands - band else bands + (band to next)
        val snapshot = bands
        preview.update(R.string.adjust_hsl) { ToneOps.applyHsl(it, snapshot) }
    }

    SideEffect {
        if (bands.isEmpty()) {
            registerApply(null, false)
        } else {
            registerApply(
                {
                    val applied = bands
                    preview.apply(R.string.adjust_hsl) { ToneOps.applyHsl(it, applied) }
                    bands = emptyMap()
                },
                true,
            )
        }
    }

    Column {
        OptionRow(
            options = HslBand.entries,
            selected = band,
            onSelect = { band = it },
            label = { bandNames[it].orEmpty() },
        )
        SliderRow(
            label = stringResource(R.string.label_hue),
            value = current.hueShift,
            onValueChange = { change(current.copy(hueShift = it)) },
            valueRange = -60f..60f,
            valueText = "${current.hueShift.toInt()}°",
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.label_saturation),
            value = current.satScale,
            onValueChange = { change(current.copy(satScale = it)) },
            valueRange = -1f..1f,
            valueText = formatPercent(current.satScale, signed = true),
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.label_lightness),
            value = current.lumScale,
            onValueChange = { change(current.copy(lumScale = it)) },
            valueRange = -1f..1f,
            valueText = formatPercent(current.lumScale, signed = true),
            defaultValue = 0f,
        )
        PanelActions {
            TextButton(
                onClick = {
                    bands = emptyMap()
                    preview.cancel()
                },
            ) { Text(stringResource(R.string.action_reset)) }
        }
    }
}

// ======================================================================================
// Reemplazar color
// ======================================================================================

@Composable
private fun ReplaceColorTab(
    preview: LivePreview,
    settings: PixaSettings,
    registerApply: (action: (() -> Unit)?, enabled: Boolean) -> Unit,
) {
    var from by remember(preview) { mutableStateOf(0xFFFFFFFF.toInt()) }
    var to by remember(preview) { mutableStateOf(0xFF000000.toInt()) }
    var tolerance by remember(preview) { mutableStateOf(0.2f) }

    fun change(newFrom: Int, newTo: Int, newTolerance: Float) {
        from = newFrom
        to = newTo
        tolerance = newTolerance
        preview.update(R.string.adjust_replace_color) {
            ToneOps.replaceColor(it, newFrom, newTo, newTolerance)
        }
    }

    SideEffect {
        registerApply(
            {
                val f = from
                val t = to
                val tol = tolerance
                preview.apply(R.string.adjust_replace_color) {
                    ToneOps.replaceColor(it, f, t, tol)
                }
            },
            true,
        )
    }

    Column {
        ColorRow(
            label = stringResource(R.string.replace_from),
            color = from,
            settings = settings,
            onPick = { change(it, to, tolerance) },
            withAlpha = false,
        )
        ColorRow(
            label = stringResource(R.string.replace_to),
            color = to,
            settings = settings,
            onPick = { change(from, it, tolerance) },
        )
        SliderRow(
            label = stringResource(R.string.label_tolerance),
            value = tolerance,
            onValueChange = { change(from, to, it) },
            valueText = formatPercent(tolerance),
            defaultValue = 0.2f,
        )
        PanelActions {
            TextButton(onClick = { preview.cancel() }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}
