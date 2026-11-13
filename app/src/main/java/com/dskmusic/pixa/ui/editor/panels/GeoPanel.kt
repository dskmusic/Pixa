package com.dskmusic.pixa.ui.editor.panels

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.image.GeoCorrect
import com.dskmusic.pixa.core.image.GeoParams
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Correccion geometrica de la capa: enderezar, perspectiva vertical y horizontal y lente.
 * El boton "Automatico" estima los cuatro valores a partir de las lineas rectas de la foto
 * y los deja en los deslizadores para retocarlos; todo con vista previa en directo.
 */
@Composable
fun GeoCorrectSection(vm: EditorViewModel) {
    val preview = rememberLivePreview(vm) ?: return
    val scope = rememberCoroutineScope()
    var p by remember(preview) { mutableStateOf(GeoParams()) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(p) {
        if (p.isIdentity) {
            preview.cancel()
        } else {
            val params = p
            preview.update(R.string.geo_title) { GeoCorrect.apply(it, params) }
        }
    }

    Text(
        stringResource(R.string.geo_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
    )
    Text(
        stringResource(R.string.geo_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
    PanelActions {
        TextButton(
            enabled = !working,
            onClick = {
                working = true
                scope.launch {
                    val found = withContext(Dispatchers.Default) { GeoCorrect.estimate(preview.source) }
                    working = false
                    if (found == null) vm.post(R.string.geo_none) else p = found
                }
            },
        ) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (working) R.string.label_loading else R.string.geo_auto))
        }
        TextButton(enabled = !p.isIdentity, onClick = { p = GeoParams() }) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.geo_reset))
        }
    }
    SliderRow(
        label = stringResource(R.string.geo_rotation),
        value = p.rotation,
        onValueChange = { p = p.copy(rotation = it) },
        valueRange = -15f..15f,
        valueText = String.format("%.1f°", p.rotation),
        defaultValue = 0f,
    )
    SliderRow(
        label = stringResource(R.string.geo_key_v),
        value = p.keystoneV,
        onValueChange = { p = p.copy(keystoneV = it) },
        valueRange = -1f..1f,
        valueText = String.format("%.2f", p.keystoneV),
        defaultValue = 0f,
    )
    SliderRow(
        label = stringResource(R.string.geo_key_h),
        value = p.keystoneH,
        onValueChange = { p = p.copy(keystoneH = it) },
        valueRange = -1f..1f,
        valueText = String.format("%.2f", p.keystoneH),
        defaultValue = 0f,
    )
    SliderRow(
        label = stringResource(R.string.geo_lens),
        value = p.lens,
        onValueChange = { p = p.copy(lens = it) },
        valueRange = -1f..1f,
        valueText = String.format("%.2f", p.lens),
        defaultValue = 0f,
    )
    if (!p.isIdentity) {
        CompareControls(vm, enabled = true)
        PanelActions {
            PrimaryPanelButton(
                text = stringResource(R.string.action_apply),
                onClick = {
                    val params = p
                    preview.apply(R.string.geo_title) { GeoCorrect.apply(it, params) }
                    p = GeoParams()
                },
            )
        }
    }
}
