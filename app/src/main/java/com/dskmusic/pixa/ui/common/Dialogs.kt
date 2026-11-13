package com.dskmusic.pixa.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R

/** Dialogo informativo de un solo boton. */
@Composable
fun InfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.action_got_it),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(confirmText) }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String? = null,
    confirmText: String = stringResource(R.string.action_ok),
    dismissText: String = stringResource(R.string.action_cancel),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        },
    )
}

/** Dialogo de una sola linea de texto: renombrar, nombre de preset, nombre de archivo. */
@Composable
fun TextPromptDialog(
    title: String,
    initial: String,
    label: String = stringResource(R.string.label_name),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(60) },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = {
                    onConfirm(value.trim())
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Explicacion de proyecto frente a imagen (requisito 3).
 *
 * Se muestra la primera vez que se va a guardar y lleva "no volver a mostrar", porque es
 * la confusion que mas molesta en estas apps: creer que se ha guardado la foto cuando lo
 * que se guardo fue el proyecto editable, o al contrario.
 */
@Composable
fun ProjectVsImageDialog(
    onDismiss: (dontShowAgain: Boolean) -> Unit,
) {
    var dontShow by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDismiss(dontShow) },
        title = { Text(stringResource(R.string.diff_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.diff_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                ExplainRow(
                    icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                    title = stringResource(R.string.save_project),
                    body = stringResource(R.string.save_project_sub),
                )
                Spacer(Modifier.height(10.dp))
                ExplainRow(
                    icon = { Icon(Icons.Filled.Image, contentDescription = null) },
                    title = stringResource(R.string.export_image),
                    body = stringResource(R.string.export_image_sub),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { dontShow = !dontShow },
                ) {
                    Checkbox(checked = dontShow, onCheckedChange = { dontShow = it })
                    Text(
                        stringResource(R.string.action_dont_show_again),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(dontShow) }) {
                Text(stringResource(R.string.action_got_it))
            }
        },
    )
}

@Composable
private fun ExplainRow(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.size(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Salir con cambios sin guardar (requisito 3): cuatro salidas claras, ninguna destructiva
 * por descuido.
 */
@Composable
fun ExitWithChangesDialog(
    onSaveProject: () -> Unit,
    onExportImage: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exit_title)) },
        text = { Text(stringResource(R.string.exit_body)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = {
                    onDismiss()
                    onSaveProject()
                }) { Text(stringResource(R.string.save_project)) }
                TextButton(onClick = {
                    onDismiss()
                    onExportImage()
                }) { Text(stringResource(R.string.export_image)) }
                TextButton(onClick = {
                    onDismiss()
                    onDiscard()
                }) { Text(stringResource(R.string.exit_discard)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Dialogo con lista de opciones simples. */
@Composable
fun <T> ChooserDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { option ->
                    Text(
                        label(option),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(option)
                                onDismiss()
                            }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Confirmacion al salir de un generador (post social, meme) con trabajo sin guardar.
 * Devuelve la accion de "atras" a usar tanto en la barra como en el gesto del sistema:
 * si [dirty] pregunta primero; si no, sale directo.
 */
@Composable
fun rememberLeaveGuard(
    dirty: Boolean,
    onLeave: () -> Unit,
    /**
     * Si se indican [vm] y [build], se usa el mismo dialogo que el editor (guardar proyecto,
     * exportar imagen, descartar): el resultado actual pasa al editor y alli se guarda.
     */
    vm: com.dskmusic.pixa.ui.editor.EditorViewModel? = null,
    defaultName: String = "",
    build: (() -> com.dskmusic.pixa.core.model.PixaDocument?)? = null,
): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { asking = true }
    if (naming && vm != null) {
        TextPromptDialog(
            title = stringResource(R.string.save_project),
            initial = defaultName,
            onConfirm = { name ->
                vm.saveProject(name)
                onLeave()
            },
            onDismiss = { naming = false },
        )
    }
    if (asking && vm != null && build != null) {
        val install: (() -> Unit) -> Unit = { then ->
            val doc = build()
            if (doc != null) {
                vm.install(
                    document = doc,
                    projectId = null,
                    name = defaultName,
                    initialLabel = R.string.history_initial,
                )
                then()
            }
        }
        ExitWithChangesDialog(
            onSaveProject = { install { naming = true } },
            onExportImage = {
                install { vm.exportImage(com.dskmusic.pixa.core.export.ExportOptions(baseName = defaultName)) }
            },
            onDiscard = onLeave,
            onDismiss = { asking = false },
        )
    } else if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.leave_work_title),
            body = stringResource(R.string.leave_work_body),
            confirmText = stringResource(R.string.leave_work_leave),
            onConfirm = onLeave,
            onDismiss = { asking = false },
        )
    }
    return { if (dirty) asking = true else onLeave() }
}
