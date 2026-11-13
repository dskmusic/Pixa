package com.dskmusic.pixa.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.ui.theme.AccentPresets
import com.dskmusic.pixa.ui.theme.hsvToColor
import com.dskmusic.pixa.ui.theme.toHsv

/**
 * Selector de color HSV con HEX y paleta global (requisito 1).
 *
 * Es el unico selector de color de la app: lo usan el acento del tema, el pincel, los
 * textos, los rellenos, el QR y los fondos. Por eso incluye la paleta guardada y los
 * colores recientes, que se alimentan solos desde aqui.
 *
 * @param withAlpha el canal alfa solo tiene sentido en algunos sitios (un pincel si, el
 *   acento del tema no), asi que se puede ocultar.
 */
@Composable
fun ColorPickerDialog(
    initial: Int,
    palette: List<Int> = emptyList(),
    recents: List<Int> = emptyList(),
    withAlpha: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onAddToPalette: ((Int) -> Unit)? = null,
) {
    val initialColor = Color(initial)
    val startHsv = remember(initial) { initialColor.toHsv() }
    var hue by remember(initial) { mutableFloatStateOf(startHsv[0]) }
    var sat by remember(initial) { mutableFloatStateOf(startHsv[1]) }
    var value by remember(initial) { mutableFloatStateOf(startHsv[2]) }
    var alpha by remember(initial) { mutableFloatStateOf(initialColor.alpha) }

    // Mientras se escribe el HEX manda el texto; el resto del tiempo manda el color.
    var hexDraft by remember { mutableStateOf<String?>(null) }
    var hexError by remember { mutableStateOf(false) }

    val current = hsvToColor(hue, sat, value, if (withAlpha) alpha else 1f)

    fun applyColor(color: Color) {
        val parts = color.toHsv()
        hue = parts[0]
        sat = parts[1]
        value = parts[2]
        alpha = color.alpha
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SaturationValueSquare(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { s, v ->
                        sat = s
                        value = v
                        hexDraft = null
                    },
                )
                Spacer(Modifier.height(12.dp))
                HueSlider(hue = hue) {
                    hue = it
                    hexDraft = null
                }
                if (withAlpha) {
                    Spacer(Modifier.height(10.dp))
                    AlphaSlider(base = hsvToColor(hue, sat, value), alpha = alpha) {
                        alpha = it
                        hexDraft = null
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(checkerBrush())
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(10.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(current),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexDraft ?: current.toHex(withAlpha),
                        onValueChange = { raw ->
                            val text = raw.take(9)
                            hexDraft = text
                            val parsed = parseHex(text)
                            hexError = parsed == null
                            if (parsed != null) applyColor(Color(parsed))
                        },
                        label = { Text(stringResource(R.string.color_hex)) },
                        isError = hexError,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (onAddToPalette != null) {
                        IconButton(onClick = { onAddToPalette(current.toArgb()) }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.settings_palette_add),
                            )
                        }
                    }
                }
                if (hexError) {
                    Text(
                        stringResource(R.string.color_invalid_hex),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                SwatchStrip(
                    title = stringResource(R.string.color_presets),
                    colors = AccentPresets.colors.map { it.toArgb() },
                    current = current.toArgb(),
                ) {
                    applyColor(Color(it))
                    hexDraft = null
                }
                if (palette.isNotEmpty()) {
                    SwatchStrip(
                        title = stringResource(R.string.color_my_palette),
                        colors = palette,
                        current = current.toArgb(),
                    ) {
                        applyColor(Color(it))
                        hexDraft = null
                    }
                }
                if (recents.isNotEmpty()) {
                    SwatchStrip(
                        title = stringResource(R.string.color_recent),
                        colors = recents,
                        current = current.toArgb(),
                    ) {
                        applyColor(Color(it))
                        hexDraft = null
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(current.toArgb())
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SwatchStrip(
    title: String,
    colors: List<Int>,
    current: Int,
    onPick: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            colors.forEach { argb ->
                ColorSwatch(
                    color = Color(argb),
                    selected = argb == current,
                    size = 32,
                    onClick = { onPick(argb) },
                )
            }
        }
    }
}

/**
 * Cuadrado de saturacion y brillo.
 *
 * Se pinta con dos degradados superpuestos (blanco a tono en horizontal, transparente a
 * negro en vertical) en vez de rellenar pixel a pixel: el resultado es identico y lo
 * dibuja la GPU.
 */
@Composable
private fun SaturationValueSquare(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(RoundedCornerShape(14.dp))
            .trackTouch { position, size ->
                onChange(
                    (position.x / size.width).coerceIn(0f, 1f),
                    1f - (position.y / size.height).coerceIn(0f, 1f),
                )
            }
            .semanticsLabel(
                stringResource(R.string.color_saturation) + " / " +
                    stringResource(R.string.color_value),
            ),
    ) {
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hsvToColor(hue, 1f, 1f))))
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(sat * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 11f, center = center, style = RingStroke)
        drawCircle(Color.Black, radius = 14f, center = center, style = RingStroke)
    }
}

@Composable
private fun HueSlider(hue: Float, onChange: (Float) -> Unit) {
    val hueColors = remember { (0..12).map { hsvToColor(it * 30f, 1f, 1f) } }
    GradientTrack(
        brush = Brush.horizontalGradient(hueColors),
        fraction = hue / 360f,
        label = stringResource(R.string.color_hue),
        onChange = { onChange((it * 360f).coerceIn(0f, 360f)) },
    )
}

@Composable
private fun AlphaSlider(base: Color, alpha: Float, onChange: (Float) -> Unit) {
    GradientTrack(
        brush = Brush.horizontalGradient(listOf(base.copy(alpha = 0f), base.copy(alpha = 1f))),
        fraction = alpha,
        label = stringResource(R.string.color_alpha),
        checker = true,
        onChange = onChange,
    )
}

@Composable
private fun GradientTrack(
    brush: Brush,
    fraction: Float,
    label: String,
    checker: Boolean = false,
    onChange: (Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .then(if (checker) Modifier.background(checkerBrush()) else Modifier)
            .trackTouch { position, size ->
                onChange((position.x / size.width).coerceIn(0f, 1f))
            }
            .semanticsLabel(label),
    ) {
        drawRect(brush = brush)
        val center = Offset(fraction.coerceIn(0f, 1f) * size.width, size.height / 2f)
        drawCircle(Color.White, radius = 10f, center = center, style = RingStroke)
        drawCircle(Color.Black, radius = 13f, center = center, style = RingStroke)
    }
}

/**
 * Sigue el dedo desde el primer toque hasta que se levanta.
 *
 * No se usan detectTapGestures y detectDragGestures juntos porque el toque simple no
 * supera el umbral de arrastre y se perderia: aqui el mismo codigo atiende los dos casos.
 */
private fun Modifier.trackTouch(
    onPosition: (Offset, androidx.compose.ui.geometry.Size) -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown()
            val area = androidx.compose.ui.geometry.Size(
                size.width.toFloat().coerceAtLeast(1f),
                size.height.toFloat().coerceAtLeast(1f),
            )
            onPosition(down.position, area)
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.pressed) {
                    onPosition(change.position, area)
                    change.consume()
                } else {
                    pressed = false
                }
            }
        }
    }
}

private val RingStroke = Stroke(width = 3f)

/** Tablero de ajedrez para que se vea la transparencia. */
private fun checkerBrush(): Brush = Brush.linearGradient(
    0f to Color(0xFFBDBDBD),
    0.49f to Color(0xFFBDBDBD),
    0.5f to Color(0xFFF2F2F2),
    1f to Color(0xFFF2F2F2),
    start = Offset(0f, 0f),
    end = Offset(16f, 16f),
    tileMode = TileMode.Repeated,
)

/** HEX legible, con alfa solo cuando se esta usando. */
fun Color.toHex(withAlpha: Boolean = false): String {
    val argb = toArgb()
    return if (withAlpha && alpha < 1f) {
        String.format("#%08X", argb)
    } else {
        String.format("#%06X", argb and 0xFFFFFF)
    }
}

/** Acepta #RGB, #RRGGBB y #AARRGGBB, con o sin almohadilla. */
fun parseHex(raw: String): Int? {
    val hex = raw.trim().removePrefix("#")
    if (hex.isEmpty() || hex.any { it.digitToIntOrNull(16) == null }) return null
    return when (hex.length) {
        3 -> {
            val r = hex[0].digitToInt(16) * 17
            val g = hex[1].digitToInt(16) * 17
            val b = hex[2].digitToInt(16) * 17
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        6 -> (0xFF shl 24) or hex.toInt(16)
        8 -> hex.toLong(16).toInt()
        else -> null
    }
}
