package com.dskmusic.pixa.ui.editor.panels

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.export.ImageInfoReader
import com.dskmusic.pixa.ml.AiModel
import com.dskmusic.pixa.ml.AiStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Nombre legible de cada modelo, para la lista de modelos y los avisos. */
@StringRes
fun AiModel.nameRes(): Int = when (this) {
    AiModel.SUPER_RES -> R.string.ai_model_super_res
    AiModel.SUPER_RES_X4 -> R.string.ai_model_super_res_x4
    AiModel.DENOISE -> R.string.ai_model_denoise
    AiModel.SEGMENT -> R.string.ai_model_segment
    AiModel.DEPTH -> R.string.ai_model_depth
    AiModel.COLORIZE -> R.string.ai_model_colorize
    AiModel.INPAINT -> R.string.ai_model_inpaint
    AiModel.FACE -> R.string.ai_model_face
    AiModel.BG_U2NETP -> R.string.ai_model_bg_u2netp
    AiModel.BG_MEDIAPIPE -> R.string.ai_model_bg_mediapipe
    AiModel.BG_SILUETA -> R.string.ai_model_bg_silueta
    AiModel.BG_U2NET -> R.string.ai_model_bg_u2net
    AiModel.BG_ISNET -> R.string.ai_model_bg_isnet
    AiModel.BG_ISNET_ANIME -> R.string.ai_model_bg_isnet_anime
    AiModel.BG_MODNET -> R.string.ai_model_bg_modnet
    AiModel.BG_RMBG -> R.string.ai_model_bg_rmbg
}

/** Tarjeta de una funcion de IA: icono, titulo, descripcion y peso o estado del modelo. */
@Composable
fun AiCard(
    icon: ImageVector,
    title: String,
    description: String,
    model: AiModel?,
    refreshKey: Int,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val ready = remember(model, refreshKey) { model?.isReady(context) ?: true }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (model != null) {
                        Text(
                            if (ready) {
                                stringResource(R.string.ai_model_ready)
                            } else {
                                stringResource(
                                    R.string.ai_model_to_download,
                                    ImageInfoReader.formatSize(model.totalBytes),
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ready) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            content?.invoke()
        }
    }
}

/**
 * Aviso previo a una operacion de IA: lo que se va a descargar, cuanto puede tardar la
 * descarga y el procesado, segun lo medido en este dispositivo en ocasiones anteriores.
 */
@Composable
fun AiPreflightDialog(action: AiAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val models = remember(action) { listOfNotNull(action.model) + action.extraModels }
    val model = models.firstOrNull()
    val missing = remember(action) { models.sumOf { it.missingBytes(context) } }
    val downloadEta = remember(action) {
        if (missing > 0L) AiStats.estimateDownloadSeconds(context, missing) else null
    }
    val processEta = remember(action) { AiStats.estimateSeconds(context, action.id, action.units) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(action.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (model != null) {
                    if (missing > 0L) {
                        InfoLine(
                            Icons.Filled.Download,
                            stringResource(R.string.ai_preflight_download, ImageInfoReader.formatSize(missing)),
                            if (downloadEta != null) {
                                stringResource(R.string.ai_preflight_download_eta, formatDuration(context, downloadEta))
                            } else {
                                stringResource(R.string.ai_preflight_download_unknown)
                            },
                        )
                        InfoLine(Icons.Filled.Wifi, stringResource(R.string.ai_preflight_wifi), null)
                    } else {
                        InfoLine(Icons.Filled.Download, stringResource(R.string.ai_preflight_ready), null)
                    }
                }
                if (action.units > 0f) InfoLine(
                    Icons.Filled.Timer,
                    if (processEta != null) {
                        stringResource(R.string.ai_preflight_time, formatDuration(context, processEta))
                    } else {
                        stringResource(R.string.ai_preflight_time_unknown_title)
                    },
                    if (processEta != null) null else stringResource(R.string.ai_preflight_time_unknown),
                )
                Text(
                    stringResource(R.string.ai_preflight_cancel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ai_preflight_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun InfoLine(icon: ImageVector, title: String, subtitle: String?) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Lista de modelos de IA: peso, si estan descargados, y botones para descargar (con
 * progreso y cancelar) o borrar y recuperar el espacio.
 */
@Composable
fun AiModelsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var downloading by remember { mutableStateOf<AiModel?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_models_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.ai_models_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                AiModel.entries.forEachIndexed { index, model ->
                    if (index > 0) HorizontalDivider()
                    val ready = remember(tick) { model.isReady(context) }
                    val used = remember(tick) { model.sizeOnDisk(context) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(model.nameRes()), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (ready) {
                                    stringResource(R.string.ai_models_used, ImageInfoReader.formatSize(used))
                                } else {
                                    stringResource(
                                        R.string.ai_model_to_download,
                                        ImageInfoReader.formatSize(model.totalBytes),
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            downloading == model -> {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                )
                                IconButton(onClick = { job?.cancel() }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.action_cancel))
                                }
                            }
                            ready -> IconButton(
                                onClick = {
                                    model.delete(context)
                                    tick++
                                },
                            ) { Icon(Icons.Filled.Delete, stringResource(R.string.ai_models_delete)) }
                            else -> IconButton(
                                enabled = downloading == null,
                                onClick = {
                                    job = scope.launch {
                                        downloading = model
                                        progress = 0f
                                        try {
                                            val ok = model.download(context) { progress = it }
                                            if (!ok) {
                                                Toast.makeText(context, R.string.ai_failed_download, Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: CancellationException) {
                                            // Cancelado por el usuario: ya se borro lo parcial.
                                        } finally {
                                            downloading = null
                                            tick++
                                        }
                                    }
                                },
                            ) { Icon(Icons.Filled.Download, stringResource(R.string.ai_models_download)) }
                        }
                    }
                }
                HorizontalDivider()
                val total = remember(tick) { AiModel.entries.sumOf { it.sizeOnDisk(context) } }
                Text(
                    stringResource(R.string.ai_models_total, ImageInfoReader.formatSize(total)),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/**
 * Aviso previo de una tarea de IA, pero solo cuando hace falta descargar algo: con todos los
 * modelos ya en el dispositivo la tarea arranca directamente, sin preguntar cada vez.
 */
@Composable
fun AiPreflightOrRun(action: AiAction, onRun: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val needsDownload = remember(action) {
        (listOfNotNull(action.model) + action.extraModels).any { !it.isReady(context) }
    }
    if (needsDownload) {
        AiPreflightDialog(action = action, onConfirm = onRun, onDismiss = onDismiss)
    } else {
        androidx.compose.runtime.LaunchedEffect(action) { onRun() }
    }
}
