package com.dskmusic.pixa.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Degradados predefinidos (inicio, fin) que se ofrecen en todas partes donde se crea uno. */
val GRADIENT_PRESETS: List<List<Int>> = listOf(
    listOf(0xFFFF9A9E, 0xFFFECFEF), listOf(0xFFA18CD1, 0xFFFBC2EB), listOf(0xFF84FAB0, 0xFF8FD3F4),
    listOf(0xFFFDCB6E, 0xFFE17055), listOf(0xFF0F2027, 0xFF2C5364), listOf(0xFF667EEA, 0xFF764BA2),
    listOf(0xFFF093FB, 0xFFF5576C), listOf(0xFF43E97B, 0xFF38F9D7), listOf(0xFF232526, 0xFF414345),
    listOf(0xFFFFFFFF, 0xFFD9DEE3), listOf(0xFFFC466B, 0xFF3F5EFB), listOf(0xFF11998E, 0xFF38EF7D),
    listOf(0xFFFFE259, 0xFFFFA751),
).map { pair -> pair.map { it.toInt() } }

/** Una muestra redonda de degradado. [content] va centrado encima (p.ej. un icono). */
@Composable
fun GradientSwatch(
    colors: List<Int>,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors.map { Color(it) }))
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Presets en filas de siete. */
@Composable
fun GradientPresetGrid(onPick: (List<Int>) -> Unit) {
    Column {
        GRADIENT_PRESETS.chunked(7).forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chunk.forEach { colors -> GradientSwatch(colors, selected = false, onClick = { onPick(colors) }) }
            }
        }
    }
}
