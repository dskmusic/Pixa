package com.dskmusic.pixa.ui.editor.panels

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.image.BgCompose
import com.dskmusic.pixa.core.image.BgOutput
import com.dskmusic.pixa.core.image.BgParams
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.core.export.ImageInfoReader
import com.dskmusic.pixa.ml.AiModel
import com.dskmusic.pixa.ml.BgSegmenter
import com.dskmusic.pixa.ml.MediaPipeCutout
import com.dskmusic.pixa.ml.SubjectCutout
import com.dskmusic.pixa.ml.U2NetCutout
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.editor.EditorUi
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.launch

/**
 * Modelos de recorte de fondo, con una leyenda de para que sirve mejor cada uno.
 * ML Kit lo entrega Google Play Services; los demas se descargan con [AiModel].
 */
private enum class BgChoice(
    val model: AiModel?,
    @StringRes val nameRes: Int,
    @StringRes val legendRes: Int,
) {
    ML_KIT(null, R.string.bg_method_mlkit, R.string.bg_legend_mlkit),
    MEDIAPIPE(AiModel.BG_MEDIAPIPE, R.string.bg_method_mediapipe, R.string.bg_legend_mediapipe),
    U2NETP(AiModel.BG_U2NETP, R.string.bg_method_u2netp, R.string.bg_legend_u2netp),
    SILUETA(AiModel.BG_SILUETA, R.string.bg_method_silueta, R.string.bg_legend_silueta),
    U2NET(AiModel.BG_U2NET, R.string.bg_method_u2net_full, R.string.bg_legend_u2net),
    ISNET(AiModel.BG_ISNET, R.string.bg_method_isnet, R.string.bg_legend_isnet),
    ISNET_ANIME(AiModel.BG_ISNET_ANIME, R.string.bg_method_isnet_anime, R.string.bg_legend_isnet_anime),
    MODNET(AiModel.BG_MODNET, R.string.bg_method_modnet, R.string.bg_legend_modnet),
    RMBG(AiModel.BG_RMBG, R.string.bg_method_rmbg, R.string.bg_legend_rmbg),
}

/**
 * Recorte automatico de fondo en dos pasos: primero se analiza la imagen con el modelo
 * elegido y despues los parametros (umbral, dureza, expandir, refinado de pelo, limpieza de
 * halo, resultado...) se ajustan con vista previa en directo hasta pulsar Aplicar.
 */
@Composable
fun BgAutoSection(vm: EditorViewModel, ui: EditorUi, settings: PixaSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = rememberLivePreview(vm)
    var method by remember { mutableStateOf(BgChoice.ML_KIT) }
    var mask by remember(preview) { mutableStateOf<SubjectCutout.Mask?>(null) }
    var params by remember(preview) { mutableStateOf(BgParams()) }
    var pending by remember { mutableStateOf<AiAction?>(null) }
    var availability by remember { mutableStateOf<SubjectCutout.Availability?>(null) }
    var downloading by remember { mutableStateOf<Int?>(null) }
    var refresh by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        vm.resetCurtain()
        onDispose { vm.releaseCurtain() }
    }
    LaunchedEffect(Unit) {
        availability = SubjectCutout.availability(context)
    }
    // La vista previa sigue a los parametros: cada cambio recalcula sobre la copia reducida.
    LaunchedEffect(mask, params) {
        val m = mask
        val p = params
        if (m != null && preview != null) {
            preview.update(R.string.bg_remove_auto) { BgCompose.compose(it, m, p) }
        }
    }
    LaunchedEffect(mask) { refresh++ }

    if (preview == null) {
        NeedsRasterNotice(vm)
        return
    }

    pending?.let { action ->
        AiPreflightOrRun(
            action = action,
            onRun = {
                pending = null
                vm.runAi(context, action)
            },
            onDismiss = { pending = null },
        )
    }

    Column {
        OptionRow(
            options = BgChoice.entries,
            selected = method,
            onSelect = {
                if (it != method) {
                    // Otro modelo: se descarta el analisis y los parametros vuelven a su valor inicial.
                    method = it
                    mask = null
                    params = BgParams()
                    preview.cancel()
                    vm.resetCurtain()
                }
            },
            label = { context.getString(it.nameRes) },
        )
        Legend(method.legendRes, method.model, refresh)

        // ---- Paso 1: analizar ----
        if (method == BgChoice.ML_KIT) {
            when (availability) {
                SubjectCutout.Availability.READY -> Unit // el boton "Analizar" va junto a la descripcion

                SubjectCutout.Availability.NEEDS_DOWNLOAD -> Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (downloading != null) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                        Text(
                            stringResource(R.string.bg_model_downloading, downloading ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        TextButton(
                            onClick = {
                                downloading = 0
                                scope.launch {
                                    val ok = SubjectCutout.download(context) { update ->
                                        if (update is SubjectCutout.Download.Running) {
                                            downloading = update.percent
                                        }
                                    }
                                    downloading = null
                                    availability = SubjectCutout.availability(context)
                                    if (!ok) vm.post(R.string.bg_model_unavailable)
                                }
                            },
                        ) { Text(stringResource(R.string.bg_remove_auto)) }
                    }
                }

                SubjectCutout.Availability.UNSUPPORTED -> Text(
                    stringResource(R.string.bg_model_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )

                null -> Text(
                    stringResource(R.string.label_loading),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // ---- Paso 2: parametros con vista previa ----
        if (mask == null) {
            // Boton propio (tonal, con texto) a la derecha de la descripcion, distinto del
            // check de "Aplicar" de la cabecera: este solo ejecuta el modelo.
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.bg_analyze_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (method != BgChoice.ML_KIT || availability == SubjectCutout.Availability.READY) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { pending = analysisAction(preview.source, method) { mask = it } },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.bg_analyze_short))
                    }
                }
            }
        } else {
            SliderRow(
                label = stringResource(R.string.bg_threshold),
                value = params.threshold,
                onValueChange = { params = params.copy(threshold = it) },
                valueRange = 0.1f..0.9f,
                valueText = formatPercent(params.threshold),
                defaultValue = 0.5f,
            )
            SliderRow(
                label = stringResource(R.string.bg_hardness),
                value = params.hardness,
                onValueChange = { params = params.copy(hardness = it) },
                valueText = formatPercent(params.hardness),
                defaultValue = 0.6f,
            )
            SliderRow(
                label = stringResource(R.string.bg_grow),
                value = params.grow,
                onValueChange = { params = params.copy(grow = it) },
                valueRange = -12f..12f,
                valueText = "${params.grow.toInt()} px",
                defaultValue = 0f,
            )
            SliderRow(
                label = stringResource(R.string.bg_edge_smooth),
                value = params.feather,
                onValueChange = { params = params.copy(feather = it) },
                valueRange = 0f..8f,
                valueText = "${params.feather.toInt()} px",
                defaultValue = 1.5f,
            )
            SliderRow(
                label = stringResource(R.string.bg_refine),
                value = params.refine,
                onValueChange = { params = params.copy(refine = it) },
                valueText = formatPercent(params.refine),
                defaultValue = 0f,
            )
            SliderRow(
                label = stringResource(R.string.bg_decontam),
                value = params.decontaminate,
                onValueChange = { params = params.copy(decontaminate = it) },
                valueText = formatPercent(params.decontaminate),
                defaultValue = 0f,
            )
            val outputNames = mapOf(
                BgOutput.TRANSPARENT to stringResource(R.string.bg_out_transparent),
                BgOutput.COLOR to stringResource(R.string.bg_out_color),
                BgOutput.BLUR to stringResource(R.string.bg_out_blur),
            )
            Text(
                stringResource(R.string.bg_output),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
            OptionRow(
                options = BgOutput.entries,
                selected = params.output,
                onSelect = { params = params.copy(output = it) },
                label = { outputNames[it].orEmpty() },
            )
            if (params.output == BgOutput.COLOR) {
                ColorRow(
                    label = stringResource(R.string.canvas_bg_color),
                    color = params.bgColor,
                    settings = settings,
                    onPick = { params = params.copy(bgColor = it) },
                )
            }
            if (params.output == BgOutput.BLUR) {
                SliderRow(
                    label = stringResource(R.string.bg_blur_amount),
                    value = params.blur,
                    onValueChange = { params = params.copy(blur = it) },
                    valueText = formatPercent(params.blur),
                    defaultValue = 0.5f,
                )
            }
            ToggleRow(
                label = stringResource(R.string.bg_invert),
                subtitle = stringResource(R.string.bg_invert_hint),
                checked = params.invert,
                onCheckedChange = { params = params.copy(invert = it) },
            )
            CompareControls(vm, enabled = true)
            // El "Aplicar" esta en la cabecera del panel.
        }
    }
}

/** Leyenda de para que sirve mejor el modelo, con su peso o estado de descarga. */
@Composable
private fun Legend(@StringRes legendRes: Int, model: AiModel?, refreshKey: Int) {
    val context = LocalContext.current
    val ready = remember(model, refreshKey) { model?.isReady(context) ?: true }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(stringResource(legendRes), style = MaterialTheme.typography.bodySmall)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Analiza [src] con el modelo elegido y entrega la mascara a [onMask]; no toca el documento. */
private fun analysisAction(
    src: android.graphics.Bitmap,
    choice: BgChoice,
    onMask: (SubjectCutout.Mask) -> Unit,
): AiAction = AiAction(
    id = "bg_" + choice.name.lowercase(),
    titleRes = R.string.bg_analyze,
    model = choice.model,
    units = src.width.toLong() * src.height / 1_000_000f,
) { ctx, progress ->
    val mask = when (choice) {
        BgChoice.ML_KIT -> SubjectCutout.segment(src)
        BgChoice.MEDIAPIPE -> MediaPipeCutout.segment(ctx, src)
        BgChoice.U2NETP -> U2NetCutout.segment(ctx, src)
        else -> BgSegmenter.segment(ctx, choice.model!!, src, progress)
    } ?: return@AiAction false
    onMask(mask)
    null
}
