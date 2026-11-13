package com.dskmusic.pixa.ui.editor.panels

import kotlinx.coroutines.delay
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Restore
import com.dskmusic.pixa.ui.editor.MarkShape
import com.dskmusic.pixa.ui.editor.EraseMark
import com.dskmusic.pixa.ui.common.OptionRow
import androidx.compose.material.icons.filled.AutoFixNormal
import kotlin.math.min
import kotlin.math.max
import kotlin.math.hypot
import java.nio.ByteBuffer
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.IntSliderRow
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Brush
import com.dskmusic.pixa.data.settings.PixaSettings
import androidx.compose.material.icons.filled.ContentCut
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.image.DepthFx
import com.dskmusic.pixa.core.image.Filters
import com.dskmusic.pixa.core.ml.FaceRestore
import com.dskmusic.pixa.core.ml.TextOcr
import com.dskmusic.pixa.core.selection.Selection
import com.dskmusic.pixa.ml.AiModel
import com.dskmusic.pixa.ml.Colorizer
import com.dskmusic.pixa.ml.DepthEstimator
import com.dskmusic.pixa.ml.Denoiser
import com.dskmusic.pixa.ml.Inpainter
import com.dskmusic.pixa.ml.Segmenter
import com.dskmusic.pixa.ml.SuperResolution
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.editor.CanvasTool
import com.dskmusic.pixa.ui.editor.EditorState
import com.dskmusic.pixa.ui.editor.EditorUi
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.LayerGeometry
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Seccion IA: todas las herramientas que usan modelos en el dispositivo. Cada tarjeta dice
 * cuanto hay que descargar; al pulsarla, un aviso previo cuenta cuanto puede tardar la
 * descarga y el procesado, y el proceso se puede cancelar en cualquier momento.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiPanel(vm: EditorViewModel, state: EditorState, ui: EditorUi, settings: PixaSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var pending by remember { mutableStateOf<AiAction?>(null) }
    var showModels by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var showRestore by remember { mutableStateOf(false) }
    // La seccion del borrador magico: el panel se desplaza hasta ella al abrirla o activarla.
    val markRequester = remember { BringIntoViewRequester() }
    var denoiseK by remember { mutableStateOf(1f) }
    var portraitK by remember { mutableStateOf(0.6f) }
    var fogK by remember { mutableStateOf(0.5f) }

    // Libera la imagen analizada al salir y refresca el estado de los modelos al terminar.
    DisposableEffect(Unit) {
        onDispose {
            vm.samEnd()
            ui.aiBackgroundOpen = false
            ui.clearMarks()
        }
    }
    LaunchedEffect(state.busyText) { if (state.busyText == null) refresh++ }
    LaunchedEffect(ui.tool) {
        if (ui.tool == CanvasTool.AI_MARK) {
            delay(80)
            markRequester.bringIntoView()
        }
    }
    // Abierto desde un acceso directo de Inicio: lanza lo pedido (borrador magico o restaurar).
    LaunchedEffect(Unit) {
        when (vm.aiLaunch) {
            "mark" -> {
                pending = markModeAction(ui)
                delay(150)
                markRequester.bringIntoView()
                // Desde el acceso directo el panel arranca minimizado: se maneja desde la barra del lienzo.
                ui.panelMinimized = true
            }
            "restore" -> showRestore = true
        }
        vm.aiLaunch = null
    }

    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty()
        scope.launch {
            var added = 0
            for (page in pages) {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching { BitmapUtils.decodeUri(context, page.imageUri, 4096) }.getOrNull()
                } ?: continue
                vm.addRasterLayer(bmp, context.getString(R.string.ai_card_scan_t))
                added++
            }
            vm.post(if (added > 0) R.string.ai_scan_added else R.string.ai_scan_failed)
        }
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
    if (showModels) AiModelsDialog(onDismiss = { showModels = false; refresh++ })
    if (showRestore) {
        RestoreDialog(
            onDismiss = { showRestore = false },
            onStart = { options ->
                showRestore = false
                restoreAction(vm, options)?.let { pending = it }
            },
        )
    }
    ocrText?.let { text ->
        AlertDialog(
            onDismissRequest = { ocrText = null },
            title = { Text(stringResource(R.string.ai_ocr_title)) },
            text = {
                SelectionContainer {
                    Text(text, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(text))
                        vm.post(R.string.ai_copied)
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_copy))
                    }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_share))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { ocrText = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.ai_panel_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // ---- Modo "seleccionar por toque": controles mientras la imagen esta analizada ----
        if (vm.samActive && ui.tool == CanvasTool.AI_SELECT) {
            SectionLabel(stringResource(R.string.ai_card_select_t))
            Text(
                stringResource(R.string.ai_sam_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PanToggle(ui)
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = ui.aiPositive,
                    onClick = { ui.aiPositive = true },
                    label = { Text(stringResource(R.string.ai_sam_add)) },
                    leadingIcon = { Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = !ui.aiPositive,
                    onClick = { ui.aiPositive = false },
                    label = { Text(stringResource(R.string.ai_sam_remove)) },
                    leadingIcon = { Icon(Icons.Filled.RemoveCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { vm.samClear() }, enabled = vm.samPointCount > 0) {
                    Text(stringResource(R.string.ai_sam_clear))
                }
                OutlinedButton(
                    onClick = { removeAction(vm)?.let { pending = it } },
                    enabled = state.selection?.isEmpty == false,
                ) {
                    Icon(Icons.Filled.AutoDelete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_card_remove_t))
                }
                TextButton(onClick = {
                    vm.samEnd()
                    ui.tool = CanvasTool.MOVE
                }) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_sam_done))
                }
            }
        }

        // ---- Calidad ----
        SectionLabel(stringResource(R.string.ai_group_quality))
        AiCard(
            Icons.Filled.Restore, stringResource(R.string.restore_title), stringResource(R.string.restore_card_d),
            null, refresh,
        ) { showRestore = true }
        AiCard(
            Icons.Filled.AutoFixHigh, stringResource(R.string.ai_same_size), stringResource(R.string.ai_same_size_hint),
            AiModel.SUPER_RES, refresh,
        ) { enhanceAction(vm, sameSize = true)?.let { pending = it } }
        AiCard(
            Icons.Filled.ZoomOutMap, stringResource(R.string.ai_double), stringResource(R.string.ai_double_hint),
            AiModel.SUPER_RES, refresh,
        ) { enhanceAction(vm, sameSize = false)?.let { pending = it } }
        AiCard(
            Icons.Filled.ZoomOutMap, stringResource(R.string.ai_x4), stringResource(R.string.ai_x4_hint),
            AiModel.SUPER_RES_X4, refresh,
        ) { enhanceX4Action(vm)?.let { pending = it } }
        AiCard(
            Icons.Filled.Grain, stringResource(R.string.ai_card_denoise_t), stringResource(R.string.ai_card_denoise_d),
            AiModel.DENOISE, refresh,
        ) { denoiseAction(vm, denoiseK)?.let { pending = it } }
        SliderRow(
            label = stringResource(R.string.label_intensity),
            value = denoiseK,
            onValueChange = { denoiseK = it },
            valueText = formatPercent(denoiseK),
            defaultValue = 1f,
        )
        AiCard(
            Icons.Filled.Face, stringResource(R.string.ai_card_face_t), stringResource(R.string.ai_card_face_d),
            AiModel.FACE, refresh,
        ) { faceAction(vm)?.let { pending = it } }

        // ---- Color y profundidad ----
        SectionLabel(stringResource(R.string.ai_group_color))
        AiCard(
            Icons.Filled.Palette, stringResource(R.string.ai_card_colorize_t), stringResource(R.string.ai_card_colorize_d),
            AiModel.COLORIZE, refresh,
        ) { colorizeAction(vm)?.let { pending = it } }
        AiCard(
            Icons.Filled.BlurOn, stringResource(R.string.ai_card_portrait_t), stringResource(R.string.ai_card_portrait_d),
            AiModel.DEPTH, refresh,
        ) { depthAction(vm, DepthKind.PORTRAIT, portraitK)?.let { pending = it } }
        SliderRow(
            label = stringResource(R.string.label_intensity),
            value = portraitK,
            onValueChange = { portraitK = it },
            valueText = formatPercent(portraitK),
            defaultValue = 0.6f,
        )
        AiCard(
            Icons.Filled.Cloud, stringResource(R.string.ai_card_fog_t), stringResource(R.string.ai_card_fog_d),
            AiModel.DEPTH, refresh,
        ) { depthAction(vm, DepthKind.FOG, fogK)?.let { pending = it } }
        SliderRow(
            label = stringResource(R.string.label_intensity),
            value = fogK,
            onValueChange = { fogK = it },
            valueText = formatPercent(fogK),
            defaultValue = 0.5f,
        )

        // ---- Seleccion y objetos ----
        SectionLabel(stringResource(R.string.ai_group_select))
        // ---- Borrador magico manual: marcar y aplicar. Va primero ----
        Column(modifier = Modifier.bringIntoViewRequester(markRequester)) {
        if (ui.tool == CanvasTool.AI_MARK) {
            Text(
                stringResource(R.string.ai_mark_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val markNames = MarkShape.entries.associateWith { context.getString(markShapeName(it)) }
            OptionRow(
                options = MarkShape.entries,
                selected = ui.markShape,
                onSelect = { ui.markShape = it },
                label = { markNames[it].orEmpty() },
            )
            PanToggle(ui)
            if (ui.markShape == MarkShape.BRUSH) {
                IntSliderRow(
                    label = stringResource(R.string.label_size),
                    value = ui.brush.size.toInt(),
                    onValueChange = { ui.brush = ui.brush.copy(size = it.toFloat()) },
                    range = 4..160,
                    suffix = " px",
                    defaultValue = 24,
                )
            }
            Text(
                stringResource(R.string.ai_mark_count, ui.marks.size),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { ui.undoMark() },
                    enabled = ui.marks.isNotEmpty(),
                ) { Text(stringResource(R.string.ai_mark_undo)) }
                OutlinedButton(
                    onClick = { ui.clearMarks() },
                    enabled = ui.marks.isNotEmpty(),
                ) { Text(stringResource(R.string.ai_mark_clear)) }
            }
            PanelActions {
                TextButton(onClick = {
                    ui.clearMarks()
                    ui.tool = CanvasTool.MOVE
                }) { Text(stringResource(R.string.ai_sam_done)) }
                PrimaryPanelButton(
                    text = stringResource(R.string.action_apply),
                    enabled = ui.marks.isNotEmpty(),
                    onClick = {
                        removeMarksAction(vm, ui)?.let { action ->
                            // Con el modelo ya descargado no se vuelve a preguntar en cada retoque pequeno.
                            if (AiModel.INPAINT.isReady(context)) vm.runAi(context, action) else pending = action
                        }
                    },
                )
            }
        }
        AiCard(
            Icons.Filled.AutoFixNormal, stringResource(R.string.ai_card_mark_t), stringResource(R.string.ai_card_mark_d),
            AiModel.INPAINT, refresh,
        ) { pending = markModeAction(ui) }
        }

        // ---- Borrador de objetos (con deteccion): controles mientras esta activo ----
        if (ui.tool == CanvasTool.AI_ERASE) {
            Text(
                stringResource(R.string.ai_eraser_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PanToggle(ui)
            ToggleRow(
                label = stringResource(R.string.ai_eraser_detect),
                subtitle = stringResource(R.string.ai_eraser_detect_hint),
                checked = ui.aiEraseDetect,
                onCheckedChange = { ui.aiEraseDetect = it },
            )
            IntSliderRow(
                label = stringResource(R.string.label_size),
                value = ui.brush.size.toInt(),
                onValueChange = { ui.brush = ui.brush.copy(size = it.toFloat()) },
                range = 8..240,
                suffix = " px",
                defaultValue = 48,
            )
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                TextButton(onClick = { ui.tool = CanvasTool.MOVE }) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ai_sam_done))
                }
            }
        }
        AiCard(
            Icons.Filled.Brush, stringResource(R.string.ai_card_eraser_t), stringResource(R.string.ai_card_eraser_d),
            AiModel.INPAINT, refresh,
        ) { pending = eraserModeAction(ui) }
        AiCard(
            Icons.Filled.CenterFocusStrong, stringResource(R.string.ai_card_zone_t), stringResource(R.string.ai_card_zone_d),
            AiModel.INPAINT, refresh,
        ) { removeInSelectionAction(vm)?.let { pending = it } }
        AiCard(
            Icons.Filled.TouchApp, stringResource(R.string.ai_card_select_t), stringResource(R.string.ai_card_select_d),
            AiModel.SEGMENT, refresh,
        ) { pending = selectAction(vm, ui) }
        AiCard(
            Icons.Filled.AutoDelete, stringResource(R.string.ai_card_remove_t), stringResource(R.string.ai_card_remove_d),
            AiModel.INPAINT, refresh,
        ) { removeAction(vm)?.let { pending = it } }

        // ---- Texto y documentos ----
        SectionLabel(stringResource(R.string.ai_group_text))
        AiCard(
            Icons.Filled.TextSnippet, stringResource(R.string.ai_card_ocr_t), stringResource(R.string.ai_card_ocr_d),
            null, refresh,
        ) {
            scope.launch {
                vm.busy(R.string.ai_busy_text)
                val composite = vm.compositeNow()
                val scaled = BitmapUtils.clampToMax(composite, 3000)
                val text = TextOcr.recognize(scaled)
                vm.idle()
                when {
                    text == null -> vm.post(R.string.ai_ocr_failed)
                    text.isBlank() -> vm.post(R.string.ai_ocr_empty)
                    else -> ocrText = text
                }
            }
        }
        AiCard(
            Icons.Filled.DocumentScanner, stringResource(R.string.ai_card_scan_t), stringResource(R.string.ai_card_scan_d),
            null, refresh,
        ) {
            val activity = context.findActivity()
            if (activity == null) {
                vm.post(R.string.ai_scan_failed)
            } else {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(3)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { sender ->
                        scanner.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    .addOnFailureListener { vm.post(R.string.ai_scan_failed) }
            }
        }

        // ---- Gestion de modelos ----
        OutlinedButton(
            onClick = { showModels = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ai_models_manage))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ======================================================================================
// Acciones
// ======================================================================================

private fun mpOf(b: Bitmap): Float = b.width.toLong() * b.height / 1_000_000f

private fun enhanceAction(vm: EditorViewModel, sameSize: Boolean): AiAction? {
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    return AiAction(
        id = "super_res",
        titleRes = if (sameSize) R.string.ai_same_size else R.string.ai_double,
        model = AiModel.SUPER_RES,
        units = mpOf(bmp),
    ) { ctx, progress ->
        val out = SuperResolution.enhance(ctx, bmp, sameSize, onProgress = progress) ?: return@AiAction false
        if (sameSize) {
            replaceRaster(layer.id, out, R.string.ai_title)
        } else {
            replaceRasterDoubleCanvas(layer.id, out, R.string.ai_title)
        }
        true
    }
}

/** x4 con el modelo de imagenes reales. El lienzo resultante no puede pasar de ~48 Mpx. */
private fun enhanceX4Action(vm: EditorViewModel): AiAction? {
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    val d = vm.doc
    if (d.width.toLong() * d.height * 16 > 48_000_000L || bmp.width.toLong() * bmp.height * 16 > 48_000_000L) {
        vm.post(R.string.ai_x4_too_big)
        return null
    }
    return AiAction(
        id = "super_res_x4",
        titleRes = R.string.ai_x4,
        model = AiModel.SUPER_RES_X4,
        units = mpOf(bmp),
    ) { ctx, progress ->
        val out = SuperResolution.enhance(ctx, bmp, sameSize = false, onProgress = progress, factor = 4)
            ?: return@AiAction false
        replaceRasterDoubleCanvas(layer.id, out, R.string.ai_title, factor = 4)
        true
    }
}

private fun denoiseAction(vm: EditorViewModel, strength: Float): AiAction? {
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    return AiAction("denoise", R.string.ai_card_denoise_t, AiModel.DENOISE, mpOf(bmp)) { ctx, progress ->
        val out = Denoiser.denoise(ctx, bmp, progress) ?: return@AiAction false
        val result = if (strength < 0.99f) {
            withContext(Dispatchers.Default) { Filters.mix(bmp, out, strength) }
        } else {
            out
        }
        replaceRaster(layer.id, result, R.string.ai_card_denoise_t)
        true
    }
}

private fun colorizeAction(vm: EditorViewModel): AiAction? {
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    return AiAction("colorize", R.string.ai_card_colorize_t, AiModel.COLORIZE, mpOf(bmp)) { ctx, progress ->
        val out = Colorizer.colorize(ctx, bmp, progress) ?: return@AiAction false
        replaceRaster(layer.id, out, R.string.ai_card_colorize_t)
        true
    }
}

private enum class DepthKind { PORTRAIT, FOG }

private fun depthAction(vm: EditorViewModel, kind: DepthKind, strength: Float): AiAction? {
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    val title = if (kind == DepthKind.PORTRAIT) R.string.ai_card_portrait_t else R.string.ai_card_fog_t
    return AiAction("depth", title, AiModel.DEPTH, mpOf(bmp)) { ctx, progress ->
        val depth = DepthEstimator.estimate(ctx, bmp, progress) ?: return@AiAction false
        val out = withContext(Dispatchers.Default) {
            if (kind == DepthKind.PORTRAIT) {
                DepthFx.portraitBlur(bmp, depth, strength, DepthFx.autoFocus(depth))
            } else {
                DepthFx.fog(bmp, depth, strength)
            }
        }
        replaceRaster(layer.id, out, title)
        true
    }
}

private fun faceAction(vm: EditorViewModel): AiAction? {
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    return AiAction("face", R.string.ai_card_face_t, AiModel.FACE, 1f) { ctx, progress ->
        val result = FaceRestore.restoreAll(ctx, bmp, progress) ?: return@AiAction false
        val out = result.bitmap
        if (out == null) {
            post(R.string.ai_no_faces)
            null
        } else {
            replaceRaster(layer.id, out, R.string.ai_card_face_t)
            post(R.string.ai_faces_done, result.faces.toString())
            true
        }
    }
}

/** Rellena la seleccion actual con lo que el modelo cree que habia debajo. */
private fun removeAction(vm: EditorViewModel): AiAction? {
    val selection = vm.state.value.selection
    if (selection == null || selection.isEmpty) {
        vm.post(R.string.ai_no_selection)
        return null
    }
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    val docToLayer = LayerGeometry.docToLayer(layer, bmp)
    return AiAction("inpaint", R.string.ai_card_remove_t, AiModel.INPAINT, 1f) { ctx, progress ->
        val hole = withContext(Dispatchers.Default) { holeInLayer(selection, bmp.width, bmp.height, docToLayer) }
        val out = Inpainter.inpaint(ctx, bmp, hole, progress) ?: return@AiAction false
        replaceRaster(layer.id, out, R.string.ai_card_remove_t)
        setSelection(null)
        true
    }
}

/** La seleccion vive en coordenadas de documento y los pixeles de la capa en las suyas. */
private fun holeInLayer(sel: Selection, w: Int, h: Int, docToLayer: Matrix?): ByteArray {
    val maskBmp = sel.toAlphaBitmap()
    val layerBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(layerBmp)
    if (docToLayer == null) {
        canvas.drawBitmap(maskBmp, 0f, 0f, null)
    } else {
        canvas.drawBitmap(maskBmp, docToLayer, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    maskBmp.recycle()
    val px = IntArray(w * h)
    layerBmp.getPixels(px, 0, w, 0, 0, w, h)
    layerBmp.recycle()
    return ByteArray(w * h) { (px[it] ushr 24).toByte() }
}

/** Analiza la imagen una vez; despues cada toque en el lienzo es instantaneo. */
internal fun selectAction(vm: EditorViewModel, ui: EditorUi): AiAction {
    val doc = vm.doc
    return AiAction(
        id = "segment",
        titleRes = R.string.ai_card_select_t,
        model = AiModel.SEGMENT,
        units = doc.width.toLong() * doc.height / 1_000_000f,
        onDone = { ui.tool = CanvasTool.AI_SELECT },
    ) { ctx, progress ->
        val composite = compositeNow()
        val session = Segmenter.encode(ctx, composite, progress)
        composite.recycle()
        if (session == null) {
            false
        } else {
            samBegin(session)
            null
        }
    }
}

// ======================================================================================
// Borrador magico: pincel y deteccion de objetos
// ======================================================================================

/** Resultado de buscar un objeto dentro de una zona. */
private sealed interface Detection {
    object Failed : Detection
    object NotFound : Detection
    class Found(val selection: Selection) : Detection
}

/** Entrar en el modo pincel: asegura los modelos (con aviso de descarga) y activa la herramienta. */
private fun eraserModeAction(ui: EditorUi): AiAction = AiAction(
    id = "eraser_prep",
    titleRes = R.string.ai_card_eraser_t,
    model = AiModel.INPAINT,
    units = 0f,
    extraModels = listOf(AiModel.SEGMENT),
    onDone = { ui.tool = CanvasTool.AI_ERASE },
) { _, _ -> null }

/** Zona alrededor del trazo, en coordenadas de documento: su caja mas el radio del pincel y algo de aire. */
private fun strokeZone(points: List<Offset>, radius: Float, docW: Int, docH: Int): RectF {
    var l = Float.MAX_VALUE
    var t = Float.MAX_VALUE
    var r = -Float.MAX_VALUE
    var b = -Float.MAX_VALUE
    for (p in points) {
        l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y)
    }
    val air = radius + 0.06f * max(r - l, b - t)
    return RectF(
        (l - air).coerceIn(0f, docW - 1f),
        (t - air).coerceIn(0f, docH - 1f),
        (r + air).coerceIn(1f, docW.toFloat()),
        (b + air).coerceIn(1f, docH.toFloat()),
    )
}

/**
 * Busca con MobileSAM el objeto que hay dentro de [zone] (coordenadas de documento).
 *
 * Con una caja como indicacion, un objeto claro da una confianza (IoU predicho) de ~1 y su
 * mascara no llena la caja; una zona de fondo liso da confianza menor y una mascara que la
 * llena entera y se sale de ella. Comprobado con el modelo real: objetos 0,99-1,01 y fondo
 * 0,79-0,85; el umbral esta en 0,88. Es una heuristica: ante un caso dudoso avisa de que no
 * ha encontrado nada en vez de borrar a ciegas.
 */
private suspend fun EditorViewModel.detectInZone(
    ctx: Context,
    zone: RectF,
    progress: (Float) -> Unit,
): Detection {
    val d = doc
    val composite = compositeNow()
    val session = Segmenter.encode(ctx, composite) { progress(it * 0.45f) }
    composite.recycle()
    if (session == null) return Detection.Failed
    val box = floatArrayOf(
        zone.left.coerceIn(0f, d.width - 2f),
        zone.top.coerceIn(0f, d.height - 2f),
        zone.right.coerceIn(2f, d.width.toFloat()),
        zone.bottom.coerceIn(2f, d.height.toFloat()),
    )
    val mask = Segmenter.decode(ctx, session, emptyList(), emptyList(), box = box)
        ?: return Detection.Failed
    // Cobertura de la mascara dentro de la caja, a la resolucion del modelo.
    val sx = mask.width.toFloat() / d.width
    val sy = mask.height.toFloat() / d.height
    val x0 = (box[0] * sx).toInt().coerceIn(0, mask.width - 1)
    val x1 = (box[2] * sx).toInt().coerceIn(x0 + 1, mask.width)
    val y0 = (box[1] * sy).toInt().coerceIn(0, mask.height - 1)
    val y1 = (box[3] * sy).toInt().coerceIn(y0 + 1, mask.height)
    var inside = 0
    for (y in y0 until y1) for (x in x0 until x1) {
        if ((mask.coverage[y * mask.width + x].toInt() and 0xFF) >= 128) inside++
    }
    val areaInBox = inside.toFloat() / ((x1 - x0) * (y1 - y0)).coerceAtLeast(1)
    if (mask.score < 0.88f || areaInBox < 0.02f || areaInBox > 0.97f) return Detection.NotFound
    val bytes = withContext(Dispatchers.Default) { upsampleMask(mask, d.width, d.height) }
    return Detection.Found(Selection.of(d.width, d.height, bytes))
}

/** Borra el objeto detectado dentro de [zone]; si no hay ninguno, avisa y no toca nada. */
private fun removeDetectedAction(vm: EditorViewModel, zone: RectF): AiAction? {
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    val docToLayer = LayerGeometry.docToLayer(layer, bmp)
    return AiAction(
        id = "inpaint_detect",
        titleRes = R.string.ai_card_zone_t,
        model = AiModel.INPAINT,
        units = 1f,
        extraModels = listOf(AiModel.SEGMENT),
    ) { ctx, progress ->
        when (val found = detectInZone(ctx, zone) { progress(it * 0.5f) }) {
            Detection.Failed -> false
            Detection.NotFound -> {
                post(R.string.ai_no_object)
                null
            }
            is Detection.Found -> {
                val hole = withContext(Dispatchers.Default) {
                    holeInLayer(found.selection, bmp.width, bmp.height, docToLayer)
                }
                val out = Inpainter.inpaint(ctx, bmp, hole) { progress(0.5f + it * 0.5f) }
                    ?: return@AiAction false
                replaceRaster(layer.id, out, R.string.ai_card_zone_t)
                true
            }
        }
    }
}

/** Boton "Borrar objeto de la seleccion": la zona es la caja de la seleccion actual. */
private fun removeInSelectionAction(vm: EditorViewModel): AiAction? {
    val selection = vm.state.value.selection
    if (selection == null || selection.isEmpty) {
        vm.post(R.string.ai_no_selection)
        return null
    }
    return removeDetectedAction(vm, RectF(selection.bounds))
}

/** Pincel con deteccion: la zona sale del trazo. */
internal fun removeStrokeDetectAction(vm: EditorViewModel, points: List<Offset>, radius: Float): AiAction? {
    val d = vm.doc
    return removeDetectedAction(vm, strokeZone(points, radius, d.width, d.height))
}

/** Pincel exacto: se borra justo lo pintado. */
internal fun removeStrokeAction(vm: EditorViewModel, points: List<Offset>, radius: Float): AiAction? {
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    val docToLayer = LayerGeometry.docToLayer(layer, bmp)
    return AiAction("inpaint", R.string.ai_card_eraser_t, AiModel.INPAINT, 1f) { ctx, progress ->
        val hole = withContext(Dispatchers.Default) {
            holeFromStroke(points, radius, bmp.width, bmp.height, docToLayer)
        }
        val out = Inpainter.inpaint(ctx, bmp, hole, progress) ?: return@AiAction false
        replaceRaster(layer.id, out, R.string.ai_card_eraser_t)
        true
    }
}

/** Trazo (en documento) a mascara en los pixeles de la capa, con el grosor escalado como la capa. */
private fun holeFromStroke(points: List<Offset>, radius: Float, w: Int, h: Int, docToLayer: Matrix?): ByteArray {
    val path = android.graphics.Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
    if (points.size == 1) path.lineTo(points[0].x + 0.5f, points[0].y + 0.5f)
    var width = radius * 2f
    if (docToLayer != null) {
        path.transform(docToLayer)
        val v = FloatArray(9)
        docToLayer.getValues(v)
        width *= hypot(v[Matrix.MSCALE_X], v[Matrix.MSKEW_Y])
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(2f, width)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.WHITE
    }
    Canvas(bmp).drawPath(path, paint)
    // ALPHA_8 puede rellenar cada fila: se lee con su paso real.
    val rowBytes = bmp.rowBytes
    val buffer = ByteBuffer.allocate(rowBytes * h)
    bmp.copyPixelsToBuffer(buffer)
    bmp.recycle()
    val raw = buffer.array()
    return ByteArray(w * h) { i -> raw[(i / w) * rowBytes + i % w] }
}

// ======================================================================================
// Borrador magico manual
// ======================================================================================

@androidx.annotation.StringRes
private fun markShapeName(shape: MarkShape): Int = when (shape) {
    MarkShape.BRUSH -> R.string.ai_mark_brush
    MarkShape.RECT -> R.string.ai_mark_rect
    MarkShape.ELLIPSE -> R.string.ai_mark_ellipse
    MarkShape.LASSO -> R.string.ai_mark_lasso
}

/** Entrar en el modo manual: asegura el modelo (con aviso de descarga) y activa la herramienta. */
private fun markModeAction(ui: EditorUi): AiAction = AiAction(
    id = "mark_prep",
    titleRes = R.string.ai_card_mark_t,
    model = AiModel.INPAINT,
    units = 0f,
    onDone = {
        ui.clearMarks()
        ui.tool = CanvasTool.AI_MARK
    },
) { _, _ -> null }

/**
 * Rellena todas las marcas a la vez con lo que hay alrededor (MI-GAN). No detecta objetos:
 * se borra exactamente lo marcado, que es lo que hace falta para lunares, motas o cables.
 */
internal fun removeMarksAction(vm: EditorViewModel, ui: EditorUi): AiAction? {
    val marks = ui.marks
    if (marks.isEmpty()) return null
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    val docToLayer = LayerGeometry.docToLayer(layer, bmp)
    return AiAction(
        id = "inpaint_marks",
        titleRes = R.string.ai_card_mark_t,
        model = AiModel.INPAINT,
        units = 1f,
        onDone = { ui.clearMarks() },
    ) { ctx, progress ->
        val hole = withContext(Dispatchers.Default) { holeFromMarks(marks, bmp.width, bmp.height, docToLayer) }
        if (hole.none { it.toInt() != 0 }) {
            post(R.string.ai_mark_outside)
            return@AiAction null
        }
        val out = Inpainter.inpaint(ctx, bmp, hole, progress) ?: return@AiAction false
        replaceRaster(layer.id, out, R.string.ai_card_mark_t)
        true
    }
}

/** Todas las marcas (en documento) a una mascara en los pixeles de la capa. */
private fun holeFromMarks(marks: List<EraseMark>, w: Int, h: Int, docToLayer: Matrix?): ByteArray {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
    val canvas = Canvas(bmp)
    var scale = 1f
    if (docToLayer != null) {
        val v = FloatArray(9)
        docToLayer.getValues(v)
        scale = hypot(v[Matrix.MSCALE_X], v[Matrix.MSKEW_Y])
    }
    for (m in marks) {
        if (m.points.isEmpty()) continue
        val path = android.graphics.Path()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        when (m.shape) {
            MarkShape.BRUSH -> {
                path.moveTo(m.points[0].x, m.points[0].y)
                for (i in 1 until m.points.size) path.lineTo(m.points[i].x, m.points[i].y)
                if (m.points.size == 1) path.lineTo(m.points[0].x + 0.5f, m.points[0].y + 0.5f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(2f, m.radius * 2f * scale)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            MarkShape.RECT, MarkShape.ELLIPSE -> {
                if (m.points.size < 2) continue
                val a = m.points.first()
                val b = m.points.last()
                val rect = RectF(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
                if (m.shape == MarkShape.RECT) {
                    path.addRect(rect, android.graphics.Path.Direction.CW)
                } else {
                    path.addOval(rect, android.graphics.Path.Direction.CW)
                }
            }
            MarkShape.LASSO -> {
                if (m.points.size < 3) continue
                path.moveTo(m.points[0].x, m.points[0].y)
                for (i in 1 until m.points.size) path.lineTo(m.points[i].x, m.points[i].y)
                path.close()
            }
        }
        if (docToLayer != null) path.transform(docToLayer)
        canvas.drawPath(path, paint)
    }
    val rowBytes = bmp.rowBytes
    val buffer = ByteBuffer.allocate(rowBytes * h)
    bmp.copyPixelsToBuffer(buffer)
    bmp.recycle()
    val raw = buffer.array()
    return ByteArray(w * h) { i -> raw[(i / w) * rowBytes + i % w] }
}

// ======================================================================================
// Mover vista y restaurar foto
// ======================================================================================

/** Alterna entre marcar y mover la vista con un dedo; sirve para recorrer una foto ampliada. */
@Composable
private fun PanToggle(ui: EditorUi) {
    FilterChip(
        selected = ui.panMode,
        onClick = { ui.panMode = !ui.panMode },
        label = { Text(stringResource(R.string.ai_pan_mode)) },
        leadingIcon = { Icon(Icons.Filled.PanTool, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/** Pasos del asistente de restauracion. */
private class RestoreOptions(
    val denoise: Boolean,
    val faces: Boolean,
    val colorize: Boolean,
    val upscale: Boolean,
)

/** Pregunta que pasos aplicar a una foto antigua; se ejecutan en orden: ruido, caras, color, ampliacion. */
@Composable
private fun RestoreDialog(onDismiss: () -> Unit, onStart: (RestoreOptions) -> Unit) {
    var denoise by remember { mutableStateOf(true) }
    var faces by remember { mutableStateOf(true) }
    var colorize by remember { mutableStateOf(false) }
    var upscale by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
        title = { Text(stringResource(R.string.restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.restore_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RestoreStep(denoise, stringResource(R.string.ai_card_denoise_t), null) { denoise = it }
                RestoreStep(faces, stringResource(R.string.ai_card_face_t), null) { faces = it }
                RestoreStep(
                    colorize, stringResource(R.string.ai_card_colorize_t),
                    stringResource(R.string.restore_colorize_hint),
                ) { colorize = it }
                RestoreStep(upscale, stringResource(R.string.ai_double), null) { upscale = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = denoise || faces || colorize || upscale,
                onClick = { onStart(RestoreOptions(denoise, faces, colorize, upscale)) },
            ) { Text(stringResource(R.string.restore_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RestoreStep(checked: Boolean, title: String, hint: String?, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Encadena los pasos elegidos sobre la misma imagen en memoria y la guarda una sola vez, asi
 * que deshacer devuelve la foto original de golpe. El progreso se reparte por lo que pesa
 * cada paso.
 */
private fun restoreAction(vm: EditorViewModel, o: RestoreOptions): AiAction? {
    val (layer, bmp) = vm.rasterOrWarn() ?: return null
    val models = buildList {
        if (o.denoise) add(AiModel.DENOISE)
        if (o.faces) add(AiModel.FACE)
        if (o.colorize) add(AiModel.COLORIZE)
        if (o.upscale) add(AiModel.SUPER_RES)
    }
    if (models.isEmpty()) return null
    val key = "restore_" + (if (o.denoise) "d" else "") + (if (o.faces) "f" else "") +
        (if (o.colorize) "c" else "") + (if (o.upscale) "u" else "")
    return AiAction(
        id = key,
        titleRes = R.string.restore_title,
        model = null,
        units = mpOf(bmp),
        extraModels = models,
    ) { ctx, progress ->
        val wD = if (o.denoise) 3f else 0f
        val wF = if (o.faces) 2f else 0f
        val wC = if (o.colorize) 1f else 0f
        val wU = if (o.upscale) 4f else 0f
        val total = wD + wF + wC + wU
        var done = 0f
        fun stage(w: Float): (Float) -> Unit = { p -> progress((done + p * w) / total) }
        var cur = bmp
        fun swap(next: Bitmap) {
            if (cur !== bmp) cur.recycle()
            cur = next
        }
        if (o.denoise) {
            swap(Denoiser.denoise(ctx, cur, stage(wD)) ?: return@AiAction false)
            done += wD
        }
        if (o.faces) {
            val r = FaceRestore.restoreAll(ctx, cur, stage(wF)) ?: return@AiAction false
            r.bitmap?.let { swap(it) }
            done += wF
        }
        if (o.colorize) {
            swap(Colorizer.colorize(ctx, cur, stage(wC)) ?: return@AiAction false)
            done += wC
        }
        if (o.upscale) {
            swap(SuperResolution.enhance(ctx, cur, sameSize = false, onProgress = stage(wU)) ?: return@AiAction false)
        }
        when {
            cur === bmp -> null
            o.upscale -> {
                replaceRasterDoubleCanvas(layer.id, cur, R.string.restore_title)
                true
            }
            else -> {
                replaceRaster(layer.id, cur, R.string.restore_title)
                true
            }
        }
    }
}
