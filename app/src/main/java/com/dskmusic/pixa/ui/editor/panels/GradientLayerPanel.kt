package com.dskmusic.pixa.ui.editor.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.GradientKind
import com.dskmusic.pixa.core.model.GradientStop
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorViewModel

/** Degradado de partida al anadir una capa: el primero de los presets, en diagonal. */
fun defaultGradient(): FillSpec.Gradient = FillSpec.Gradient(
    kind = GradientKind.LINEAR,
    stops = listOf(
        GradientStop(0f, 0xFFFF9A9E.toInt()),
        GradientStop(1f, 0xFFFECFEF.toInt()),
    ),
    angle = 45f,
)

/**
 * Edicion de una capa de degradado: tipo (lineal, radial, angular), presets, paradas con
 * color y transparencia, posicion de cada parada y angulo. Es la capa [LayerContent.Fill]
 * con un [FillSpec.Gradient]; su opacidad y modo de mezcla se cambian desde el gestor de
 * capas como en cualquier otra.
 */
@Composable
fun GradientLayerPanel(vm: EditorViewModel, doc: PixaDocument, settings: PixaSettings) {
    val layer = doc.selected
    val gradient = (layer?.content as? LayerContent.Fill)?.spec as? FillSpec.Gradient
    if (layer == null || gradient == null) {
        Text(
            stringResource(R.string.gradient_pick),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    val context = LocalContext.current
    val kindNames = remember(context) {
        GradientKind.entries.associateWith { context.getString(EditorLabels.gradient(it)) }
    }

    fun change(next: FillSpec.Gradient) {
        vm.updateLayer(R.string.label_gradient, layer.id) { it.copy(content = LayerContent.Fill(next)) }
    }

    Column {
        OptionRow(
            options = GradientKind.entries,
            selected = gradient.kind,
            onSelect = { change(gradient.copy(kind = it)) },
            label = { kindNames[it].orEmpty() },
        )
        GradientStopsEditor(gradient = gradient, settings = settings, onChange = ::change)
        PanelActions {
            TextButton(
                onClick = {
                    change(
                        gradient.copy(
                            stops = gradient.stops
                                .map { it.copy(position = 1f - it.position) }
                                .sortedBy { it.position },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.gradient_reverse)) }
        }
    }
}

/** Capa de degradado nueva, lista para anadir. */
fun gradientLayer(name: String): PixaLayer = PixaLayer(
    name = name,
    content = LayerContent.Fill(defaultGradient()),
)
