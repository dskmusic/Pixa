package com.dskmusic.pixa.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.model.LayerStyle
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.formatValue
import com.dskmusic.pixa.ui.editor.panels.ColorRow
import kotlin.math.roundToInt

/** Un look de capa listo: el nombre y como se construye segun el tamano del documento. */
private class StylePreset(val nameRes: Int, val build: (Float) -> LayerStyle)

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/**
 * Looks de capa. [u] es el tamano del documento respecto a 1080 px: los grosores y radios
 * se escalan para que un preset se vea igual en una miniatura que en una foto de 12 MP.
 */
private val PRESETS = listOf(
    StylePreset(R.string.ls_none) { LayerStyle() },
    StylePreset(R.string.ls_sticker) { u ->
        LayerStyle(
            strokeEnabled = true, strokeColor = WHITE, strokeWidth = 14f * u,
            shadowEnabled = true, shadowColor = 0x59000000, shadowRadius = 10f * u,
            shadowDx = 0f, shadowDy = 6f * u,
        )
    },
    StylePreset(R.string.ls_soft_shadow) { u ->
        LayerStyle(
            shadowEnabled = true, shadowColor = 0x66000000, shadowRadius = 24f * u,
            shadowDx = 0f, shadowDy = 12f * u,
        )
    },
    StylePreset(R.string.ls_float) { u ->
        LayerStyle(
            shadowEnabled = true, shadowColor = 0x55000000, shadowRadius = 40f * u,
            shadowDx = 0f, shadowDy = 28f * u,
        )
    },
    StylePreset(R.string.ls_hard_shadow) { u ->
        LayerStyle(
            shadowEnabled = true, shadowColor = BLACK, shadowRadius = 0.5f,
            shadowDx = 14f * u, shadowDy = 14f * u,
        )
    },
    StylePreset(R.string.ls_retro) { u ->
        LayerStyle(
            strokeEnabled = true, strokeColor = BLACK, strokeWidth = 6f * u,
            shadowEnabled = true, shadowColor = BLACK, shadowRadius = 0.5f,
            shadowDx = 12f * u, shadowDy = 12f * u,
        )
    },
    StylePreset(R.string.ls_outline_white) { u ->
        LayerStyle(strokeEnabled = true, strokeColor = WHITE, strokeWidth = 10f * u)
    },
    StylePreset(R.string.ls_outline_black) { u ->
        LayerStyle(strokeEnabled = true, strokeColor = BLACK, strokeWidth = 10f * u)
    },
    StylePreset(R.string.ls_neon_cyan) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFF00E5FF.toInt(), glowRadius = 34f * u)
    },
    StylePreset(R.string.ls_neon_pink) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFFFF2D95.toInt(), glowRadius = 34f * u)
    },
    StylePreset(R.string.ls_gold_glow) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFFFFC107.toInt(), glowRadius = 28f * u)
    },
    StylePreset(R.string.ls_neon_green) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFF39FF14.toInt(), glowRadius = 34f * u)
    },
    StylePreset(R.string.ls_neon_yellow) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFFFFEA00.toInt(), glowRadius = 34f * u)
    },
    StylePreset(R.string.ls_neon_purple) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFFB026FF.toInt(), glowRadius = 34f * u)
    },
    StylePreset(R.string.ls_fire_glow) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFFFF6D00.toInt(), glowRadius = 38f * u)
    },
    StylePreset(R.string.ls_ice_glow) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFF80D8FF.toInt(), glowRadius = 30f * u)
    },
    StylePreset(R.string.ls_white_glow) { u ->
        LayerStyle(glowEnabled = true, glowColor = WHITE, glowRadius = 26f * u)
    },
    StylePreset(R.string.ls_sticker_black) { u ->
        LayerStyle(
            strokeEnabled = true, strokeColor = BLACK, strokeWidth = 12f * u,
            shadowEnabled = true, shadowColor = 0x59000000, shadowRadius = 10f * u,
            shadowDx = 0f, shadowDy = 6f * u,
        )
    },
    StylePreset(R.string.ls_sticker_pink) { u ->
        LayerStyle(
            strokeEnabled = true, strokeColor = 0xFFFF4081.toInt(), strokeWidth = 14f * u,
            shadowEnabled = true, shadowColor = 0x59000000, shadowRadius = 10f * u,
            shadowDx = 0f, shadowDy = 6f * u,
        )
    },
    StylePreset(R.string.ls_pop_shadow) { u ->
        LayerStyle(
            strokeEnabled = true, strokeColor = BLACK, strokeWidth = 5f * u,
            shadowEnabled = true, shadowColor = 0xFFFF1F8E.toInt(), shadowRadius = 0.5f,
            shadowDx = 12f * u, shadowDy = 12f * u,
        )
    },
    StylePreset(R.string.ls_blue_shadow) { u ->
        LayerStyle(
            shadowEnabled = true, shadowColor = 0xFF1565C0.toInt(), shadowRadius = 0.5f,
            shadowDx = 14f * u, shadowDy = 14f * u,
        )
    },
    StylePreset(R.string.ls_gold_outline) { u ->
        LayerStyle(
            strokeEnabled = true, strokeColor = 0xFFFFC107.toInt(), strokeWidth = 10f * u,
            glowEnabled = true, glowColor = 0xFFFFC107.toInt(), glowRadius = 22f * u,
        )
    },
    StylePreset(R.string.ls_long_shadow) { u ->
        LayerStyle(
            shadowEnabled = true, shadowColor = 0x99000000.toInt(), shadowRadius = 0.5f,
            shadowDx = 40f * u, shadowDy = 40f * u,
        )
    },
    StylePreset(R.string.ls_polaroid) { u ->
        LayerStyle(
            strokeEnabled = true, strokeColor = WHITE, strokeWidth = 26f * u,
            shadowEnabled = true, shadowColor = 0x66000000, shadowRadius = 18f * u,
            shadowDx = 0f, shadowDy = 10f * u,
        )
    },
    StylePreset(R.string.ls_glow_stroke) { u ->
        LayerStyle(
            strokeEnabled = true, strokeColor = 0xFF00E5FF.toInt(), strokeWidth = 6f * u,
            glowEnabled = true, glowColor = 0xFF00E5FF.toInt(), glowRadius = 30f * u,
        )
    },
    StylePreset(R.string.ls_deep_shadow) { u ->
        LayerStyle(
            shadowEnabled = true, shadowColor = 0x99000000.toInt(), shadowRadius = 60f * u,
            shadowDx = 0f, shadowDy = 36f * u,
        )
    },
    StylePreset(R.string.ls_pixel_shadow) { u ->
        LayerStyle(
            shadowEnabled = true, shadowColor = 0xCC000000.toInt(), shadowRadius = 0.5f,
            shadowDx = 8f * u, shadowDy = 8f * u,
            strokeEnabled = true, strokeColor = BLACK, strokeWidth = 3f * u,
        )
    },
    StylePreset(R.string.ls_neon_red) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFFFF1744.toInt(), glowRadius = 34f * u)
    },
    StylePreset(R.string.ls_neon_blue) { u ->
        LayerStyle(glowEnabled = true, glowColor = 0xFF2979FF.toInt(), glowRadius = 34f * u)
    },
    StylePreset(R.string.ls_sticker_yellow) { u ->
        LayerStyle(
            strokeEnabled = true, strokeColor = 0xFFFFEB3B.toInt(), strokeWidth = 14f * u,
            shadowEnabled = true, shadowColor = 0x59000000, shadowRadius = 10f * u,
            shadowDx = 0f, shadowDy = 6f * u,
        )
    },
)

/**
 * Efectos de la capa seleccionada: sombra, resplandor y contorno sobre la silueta real
 * (con transparencia incluida), mas looks listos. El dibujo ya lo hacia el renderizador;
 * esto es solo la interfaz para editarlo.
 */
@Composable
internal fun LayerStyleSection(layer: PixaLayer, docSize: Int, vm: EditorViewModel) {
    val context = LocalContext.current
    val settings by context.pixa.settings.settings.collectAsStateWithLifecycle(PixaSettings())
    val style = layer.style ?: LayerStyle()
    // Desplegado siempre (requisito del usuario): antes arrancaba plegado si no habia estilo.
    var open by remember(layer.id) { mutableStateOf(true) }
    val u = (docSize / 1080f).coerceIn(0.4f, 8f)

    fun set(next: LayerStyle) = vm.setLayerStyle(layer.id, next.takeUnless { it.isEmpty })

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.layer_style_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!open) return

    val names = remember(context) { PRESETS.associateWith { context.getString(it.nameRes) } }
    OptionRow(
        options = PRESETS,
        selected = null,
        onSelect = { preset -> set(preset.build(u)) },
        label = { names[it].orEmpty() },
    )

    ToggleRow(
        label = stringResource(R.string.ls_shadow),
        checked = style.shadowEnabled,
        onCheckedChange = { set(style.copy(shadowEnabled = it)) },
    )
    if (style.shadowEnabled) {
        SliderRow(
            label = stringResource(R.string.ls_blur),
            value = style.shadowRadius,
            onValueChange = { set(style.copy(shadowRadius = it)) },
            valueRange = 0f..(80f * u),
            valueText = formatValue(style.shadowRadius),
            defaultValue = 12f * u,
        )
        SliderRow(
            label = stringResource(R.string.ls_offset_x),
            value = style.shadowDx,
            onValueChange = { set(style.copy(shadowDx = it)) },
            valueRange = (-80f * u)..(80f * u),
            valueText = formatValue(style.shadowDx),
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.ls_offset_y),
            value = style.shadowDy,
            onValueChange = { set(style.copy(shadowDy = it)) },
            valueRange = (-80f * u)..(80f * u),
            valueText = formatValue(style.shadowDy),
            defaultValue = 0f,
        )
        ColorRow(
            label = stringResource(R.string.label_color),
            color = style.shadowColor,
            settings = settings,
            onPick = { set(style.copy(shadowColor = it)) },
        )
    }

    ToggleRow(
        label = stringResource(R.string.ls_glow),
        checked = style.glowEnabled,
        onCheckedChange = { set(style.copy(glowEnabled = it)) },
    )
    if (style.glowEnabled) {
        SliderRow(
            label = stringResource(R.string.ls_blur),
            value = style.glowRadius,
            onValueChange = { set(style.copy(glowRadius = it)) },
            valueRange = 1f..(100f * u),
            valueText = formatValue(style.glowRadius),
            defaultValue = 18f * u,
        )
        ColorRow(
            label = stringResource(R.string.label_color),
            color = style.glowColor,
            settings = settings,
            onPick = { set(style.copy(glowColor = it)) },
            withAlpha = false,
        )
    }

    ToggleRow(
        label = stringResource(R.string.ls_outline),
        checked = style.strokeEnabled,
        onCheckedChange = { set(style.copy(strokeEnabled = it)) },
    )
    if (style.strokeEnabled) {
        SliderRow(
            label = stringResource(R.string.ls_thickness),
            value = style.strokeWidth,
            onValueChange = { set(style.copy(strokeWidth = it)) },
            valueRange = 1f..(60f * u),
            valueText = "${style.strokeWidth.roundToInt()} px",
            defaultValue = 8f * u,
        )
        ColorRow(
            label = stringResource(R.string.label_color),
            color = style.strokeColor,
            settings = settings,
            onPick = { set(style.copy(strokeColor = it)) },
            withAlpha = false,
        )
    }
}
