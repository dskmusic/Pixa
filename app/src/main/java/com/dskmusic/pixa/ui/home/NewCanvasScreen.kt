package com.dskmusic.pixa.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.ColorPickerDialog
import com.dskmusic.pixa.ui.common.ColorSwatch
import com.dskmusic.pixa.ui.common.NumberField
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.ToggleRow

/** Preset de lienzo nuevo (requisito 5). */
private data class CanvasPreset(val labelRes: Int, val width: Int, val height: Int)

private val Presets = listOf(
    CanvasPreset(R.string.preset_square, 1080, 1080),
    CanvasPreset(R.string.preset_portrait, 1080, 1350),
    CanvasPreset(R.string.preset_story, 1080, 1920),
    CanvasPreset(R.string.preset_landscape, 1920, 1080),
    CanvasPreset(R.string.preset_thumb, 1280, 720),
    CanvasPreset(R.string.preset_banner, 1500, 500),
    CanvasPreset(R.string.preset_cover, 1640, 924),
    CanvasPreset(R.string.preset_a4, 2480, 3508),
    CanvasPreset(R.string.preset_hd, 1920, 1080),
    CanvasPreset(R.string.preset_4k, 3840, 2160),
)

@Composable
fun NewCanvasScreen(
    onBack: () -> Unit,
    onCreate: (width: Int, height: Int, background: CanvasBackground) -> Unit,
) {
    val container = LocalContext.current.pixa
    val settings by container.settings.settings.collectAsState(initial = PixaSettings())
    val maxSide = settings.maxResolution

    var width by remember { mutableStateOf(1080) }
    var height by remember { mutableStateOf(1080) }
    var transparent by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(0xFFFFFFFF.toInt()) }
    var pickingColor by remember { mutableStateOf(false) }

    val valid = width in 16..maxSide && height in 16..maxSide

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(
                title = stringResource(R.string.canvas_new_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.canvas_presets))
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Presets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            PresetCard(
                                label = stringResource(preset.labelRes),
                                width = preset.width,
                                height = preset.height,
                                selected = width == preset.width && height == preset.height,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    width = preset.width.coerceAtMost(maxSide)
                                    height = preset.height.coerceAtMost(maxSide)
                                },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionTitle(stringResource(R.string.label_custom))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberField(
                    label = stringResource(R.string.label_width),
                    value = width,
                    onValueChange = { width = it },
                    range = 16..maxSide,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.label_height),
                    value = height,
                    onValueChange = { height = it },
                    range = 16..maxSide,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!valid) {
                Text(
                    stringResource(R.string.canvas_size_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            SectionTitle(stringResource(R.string.label_background))
            ToggleRow(
                label = stringResource(R.string.canvas_bg_transparent),
                checked = transparent,
                onCheckedChange = { transparent = it },
            )
            if (!transparent) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.canvas_bg_color),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    ColorSwatch(color = Color(color), onClick = { pickingColor = true })
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onCreate(
                        width,
                        height,
                        CanvasBackground(transparent = transparent, color = color),
                    )
                },
                enabled = valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.canvas_create))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickingColor) {
        ColorPickerDialog(
            initial = color,
            palette = settings.palette,
            recents = settings.recentColors,
            withAlpha = false,
            onDismiss = { pickingColor = false },
            onConfirm = { picked -> color = picked },
        )
    }
}

@Composable
private fun PresetCard(
    label: String,
    width: Int,
    height: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                "$width x $height",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
