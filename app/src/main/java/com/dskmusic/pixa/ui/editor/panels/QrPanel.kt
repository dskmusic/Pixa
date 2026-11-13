package com.dskmusic.pixa.ui.editor.panels

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.QrErrorLevel
import com.dskmusic.pixa.core.model.QrPayloadKind
import com.dskmusic.pixa.core.model.QrSpec
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ui.common.IntSliderRow
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Panel del codigo QR (requisito 12).
 *
 * La capa guarda el [QrSpec], no una imagen: cambiar el contenido o los colores lo vuelve
 * a generar, y el codigo sigue siendo editable despues de guardar y reabrir el proyecto.
 */
@Composable
fun QrPanel(vm: EditorViewModel, doc: PixaDocument, settings: PixaSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = doc.selected
    val content = layer?.content as? LayerContent.Qr
    val kindNames = remember(context) {
        QrPayloadKind.entries.associateWith { context.getString(EditorLabels.qrKind(it)) }
    }
    val levelNames = remember {
        QrErrorLevel.entries.associateWith { EditorLabels.qrLevel(it) }
    }

    if (layer == null || content == null) {
        // Sin capa QR no se edita nada: se crea una primero. Si no, cada deslizador
        // tocado sin capa seleccionada iria creando codigos nuevos.
        Column {
            Text(
                stringResource(R.string.qr_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            PanelActions {
                PrimaryPanelButton(
                    text = stringResource(R.string.qr_title),
                    icon = Icons.Filled.Add,
                    onClick = {
                        val side = minOf(doc.width, doc.height) * 0.6f
                        vm.addLayer(
                            R.string.qr_title,
                            PixaLayer(
                                name = context.getString(R.string.layer_qr),
                                content = LayerContent.Qr(
                                    QrSpec(sizePx = side.toInt().coerceIn(128, 2048)),
                                ),
                                transform = Xf(
                                    tx = (doc.width - side) / 2f,
                                    ty = (doc.height - side) / 2f,
                                ),
                            ),
                        )
                    },
                )
            }
        }
        return
    }
    val spec = content.spec

    fun change(block: (QrSpec) -> QrSpec) {
        vm.updateLayer(R.string.qr_title, layer.id) {
            val current = it.content
            if (current is LayerContent.Qr) {
                it.copy(content = LayerContent.Qr(block(current.spec)))
            } else {
                it
            }
        }
    }

    fun field(key: String, value: String) {
        change { it.copy(fields = it.fields + (key to value)) }
    }

    val logoPicker = rememberImagePicker { uri ->
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { BitmapUtils.decodeUri(context, uri, 512) }.getOrNull()
            }
            if (bitmap == null) {
                vm.post(R.string.error_load_image)
            } else {
                change { it.copy(logoBitmapId = vm.putBitmap(bitmap)) }
            }
        }
    }

    Column {
        OptionRow(
            options = QrPayloadKind.entries,
            selected = spec.kind,
            onSelect = { kind -> change { it.copy(kind = kind) } },
            label = { kindNames[it].orEmpty() },
        )

        when (spec.kind) {
            QrPayloadKind.URL -> QrField(R.string.qr_type_url, spec, "url", { k, v -> field(k, v) })
            QrPayloadKind.TEXT -> QrField(R.string.qr_type_text, spec, "text", { k, v -> field(k, v) }, lines = false)
            QrPayloadKind.PHONE -> QrField(R.string.qr_type_phone, spec, "phone", { k, v -> field(k, v) })
            QrPayloadKind.EMAIL -> {
                QrField(R.string.qr_type_email, spec, "email", { k, v -> field(k, v) })
                QrField(R.string.qr_email_subject, spec, "subject", { k, v -> field(k, v) })
                QrField(R.string.qr_email_body, spec, "body", { k, v -> field(k, v) }, lines = false)
            }
            QrPayloadKind.WIFI -> {
                QrField(R.string.qr_wifi_ssid, spec, "ssid", { k, v -> field(k, v) })
                QrField(R.string.qr_wifi_password, spec, "password", { k, v -> field(k, v) })
                QrField(R.string.qr_wifi_security, spec, "security", { k, v -> field(k, v) })
                ToggleRow(
                    label = stringResource(R.string.qr_wifi_hidden),
                    checked = spec.fields["hidden"] == "true",
                    onCheckedChange = { on -> field("hidden", if (on) "true" else "false") },
                )
            }
            QrPayloadKind.VCARD -> {
                QrField(R.string.qr_contact_name, spec, "name", { k, v -> field(k, v) })
                QrField(R.string.qr_contact_org, spec, "org", { k, v -> field(k, v) })
                QrField(R.string.qr_contact_title, spec, "title", { k, v -> field(k, v) })
                QrField(R.string.qr_contact_phone, spec, "phone", { k, v -> field(k, v) })
                QrField(R.string.qr_contact_email, spec, "email", { k, v -> field(k, v) })
                QrField(R.string.qr_contact_url, spec, "url", { k, v -> field(k, v) })
                QrField(R.string.qr_contact_address, spec, "address", { k, v -> field(k, v) })
                QrField(R.string.qr_contact_note, spec, "note", { k, v -> field(k, v) }, lines = false)
            }
        }

        ColorRow(
            label = stringResource(R.string.qr_fg_color),
            color = spec.fgColor,
            settings = settings,
            onPick = { picked -> change { it.copy(fgColor = picked) } },
        )
        ColorRow(
            label = stringResource(R.string.qr_bg_color),
            color = spec.bgColor,
            settings = settings,
            onPick = { picked -> change { it.copy(bgColor = picked) } },
        )
        Text(
            stringResource(R.string.qr_error_level),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        OptionRow(
            options = QrErrorLevel.entries,
            selected = spec.errorLevel,
            onSelect = { level -> change { it.copy(errorLevel = level) } },
            label = { levelNames[it].orEmpty() },
        )
        IntSliderRow(
            label = stringResource(R.string.qr_margin),
            value = spec.marginModules,
            onValueChange = { value -> change { it.copy(marginModules = value) } },
            range = 0..8,
            defaultValue = 2,
        )
        ToggleRow(
            label = stringResource(
                if (spec.roundedModules) R.string.qr_modules_round else R.string.qr_modules_square,
            ),
            checked = spec.roundedModules,
            onCheckedChange = { on -> change { it.copy(roundedModules = on) } },
        )
        IntSliderRow(
            label = stringResource(R.string.label_size),
            value = spec.sizePx,
            onValueChange = { value -> change { it.copy(sizePx = value) } },
            range = 128..2048,
            suffix = " px",
            defaultValue = 600,
        )
        PanelActions {
            TextButton(onClick = logoPicker) { Text(stringResource(R.string.qr_logo)) }
            if (spec.logoBitmapId != null) {
                TextButton(onClick = { change { it.copy(logoBitmapId = null) } }) {
                    Text(stringResource(R.string.action_remove))
                }
            }
        }
    }
}

@Composable
private fun QrField(
    @StringRes labelRes: Int,
    spec: QrSpec,
    key: String,
    onChange: (String, String) -> Unit,
    lines: Boolean = true,
) {
    TextFieldRow(
        label = stringResource(labelRes),
        value = spec.fields[key].orEmpty(),
        onValueChange = { onChange(key, it) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = lines,
    )
}
