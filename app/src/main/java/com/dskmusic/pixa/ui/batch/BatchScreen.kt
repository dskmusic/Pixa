package com.dskmusic.pixa.ui.batch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.dskmusic.pixa.ui.common.PixaToastHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.export.ExportFormat
import com.dskmusic.pixa.core.image.Filters
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.IntSliderRow
import com.dskmusic.pixa.ui.common.NumberField
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.common.rememberLeaveGuard
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.rememberMultiImagePicker
import com.dskmusic.pixa.work.BatchPlan
import com.dskmusic.pixa.work.BatchResize
import com.dskmusic.pixa.work.BatchWorker
import kotlinx.coroutines.launch

/** Proporciones de recorte del lote; la primera deja la imagen como esta. */
private val Ratios = listOf(
    Triple(R.string.label_none, 0f, 0f),
    Triple(R.string.preset_square, 1f, 1f),
    Triple(R.string.preset_portrait, 4f, 5f),
    Triple(R.string.preset_story, 9f, 16f),
    Triple(R.string.preset_landscape, 16f, 9f),
    Triple(R.string.preset_cover, 3f, 2f),
    Triple(R.string.preset_a4, 210f, 297f),
)

private val Formats = listOf(ExportFormat.JPEG, ExportFormat.PNG, ExportFormat.WEBP)

/**
 * Editor por lotes (requisito 22).
 *
 * La pantalla solo arma la receta y la entrega a [BatchWorker]; el trabajo ocurre fuera,
 * de modo que salir de aqui no corta el lote. El progreso y el resultado se leen del
 * propio WorkManager, asi que al volver a entrar se sigue viendo lo que esta pasando.
 */
@Composable
fun BatchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val work = remember(context) { WorkManager.getInstance(context) }
    val userData by context.pixa.userData.data.collectAsStateWithLifecycle()

    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var plan by remember { mutableStateOf(BatchPlan()) }
    var ratioIndex by remember { mutableStateOf(0) }
    var targetSize by remember { mutableStateOf(false) }
    var targetKb by remember { mutableStateOf(500) }
    // Accion aplazada mientras se pide el permiso de notificaciones.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Solo se reacciona al final de un lote lanzado desde aqui: WorkManager conserva
    // el resultado del ultimo trabajo y al volver a entrar se volveria a compartir.
    var launchedId by remember { mutableStateOf<String?>(null) }
    var handledId by remember { mutableStateOf<String?>(null) }

    val infos by work.getWorkInfosForUniqueWorkFlow(BatchWorker.UNIQUE_NAME)
        .collectAsStateWithLifecycle(emptyList())
    val info = infos.lastOrNull()
    val running = info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED
    val total = info?.progress?.getInt(BatchWorker.KEY_TOTAL, 0) ?: 0
    val progressDone = info?.progress?.getInt(BatchWorker.KEY_DONE, 0) ?: 0

    LaunchedEffect(Unit) { context.pixa.userData.refresh() }

    val pickImages = rememberMultiImagePicker(BatchWorker.MAX_IMAGES) { picked ->
        if (picked.isNotEmpty()) uris = picked
    }

    // Con un lote en marcha se puede salir: sigue en segundo plano. Solo se pregunta si hay
    // fotos elegidas que aun no se han procesado.
    val guardedBack = rememberLeaveGuard(dirty = uris.isNotEmpty() && launchedId == null && !running, onLeave = onBack)

    val toast: (Int) -> Unit = { res ->
        scope.launch { snackbar.showSnackbar(context.getString(res)) }
    }

    val effectivePlan: () -> BatchPlan = {
        plan.copy(
            ratioW = Ratios[ratioIndex].second,
            ratioH = Ratios[ratioIndex].third,
            targetSizeKb = if (targetSize) targetKb else null,
        )
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { tree ->
        if (tree != null) {
            // Permiso persistente: asi el mismo destino sirve en lotes posteriores.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    tree,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            launchedId = BatchWorker.enqueue(context, uris, effectivePlan(), tree).toString()
        }
    }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) toast(R.string.error_permission_notifications)
        pending?.invoke()
        pending = null
    }

    /** Valida, pide el permiso de aviso si hace falta y lanza [action]. */
    fun start(action: () -> Unit) {
        if (uris.isEmpty()) {
            toast(R.string.batch_no_images)
            return
        }
        if (!effectivePlan().hasWork) {
            toast(R.string.batch_no_steps)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = action
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    // Fin del lote: aviso y, si no habia carpeta de destino, selector para compartir.
    LaunchedEffect(info?.id, info?.state) {
        val finished = info ?: return@LaunchedEffect
        val id = finished.id.toString()
        if (id != launchedId || id == handledId) return@LaunchedEffect
        if (finished.state != WorkInfo.State.SUCCEEDED &&
            finished.state != WorkInfo.State.FAILED
        ) {
            return@LaunchedEffect
        }
        handledId = id
        val output = finished.outputData
        if (output.getBoolean(BatchWorker.KEY_ERROR, false)) {
            snackbar.showSnackbar(context.getString(R.string.batch_folder_failed))
            return@LaunchedEffect
        }
        val count = output.getInt(BatchWorker.KEY_DONE, 0)
        snackbar.showSnackbar(context.getString(R.string.batch_done, count))
        val results = output.getStringArray(BatchWorker.KEY_RESULTS)
        if (results != null && results.isNotEmpty()) {
            val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(results.map(Uri::parse)),
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(
                    Intent.createChooser(share, context.getString(R.string.batch_share_all)),
                )
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(
                title = stringResource(R.string.batch_title),
                onBack = guardedBack,
                actions = {
                    TextButton(onClick = pickImages, enabled = !running) {
                        Icon(
                            Icons.Filled.AddCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.action_select_image))
                    }
                },
            )
        },
        snackbarHost = { PixaToastHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.batch_images_selected, uris.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (uris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = stringResource(R.string.cd_image_thumb),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.batch_steps))

            // --- Redimensionar ---------------------------------------------------------
            SectionTitle(stringResource(R.string.batch_resize))
            val resizeLabels = remember(context) {
                mapOf(
                    BatchResize.OFF to context.getString(R.string.label_none),
                    BatchResize.WIDTH to context.getString(R.string.batch_resize_by_width),
                    BatchResize.HEIGHT to context.getString(R.string.batch_resize_by_height),
                    BatchResize.PERCENT to context.getString(R.string.batch_resize_by_percent),
                )
            }
            OptionRow(
                options = BatchResize.entries,
                selected = plan.resize,
                onSelect = { mode ->
                    plan = plan.copy(
                        resize = mode,
                        resizeValue = if (mode == BatchResize.PERCENT) 50 else 1920,
                    )
                },
                label = { resizeLabels[it].orEmpty() },
            )
            when (plan.resize) {
                BatchResize.OFF -> Unit
                BatchResize.PERCENT -> IntSliderRow(
                    label = stringResource(R.string.label_scale),
                    value = plan.resizeValue,
                    onValueChange = { plan = plan.copy(resizeValue = it) },
                    range = 5..200,
                    suffix = " %",
                    defaultValue = 50,
                )
                BatchResize.WIDTH, BatchResize.HEIGHT -> Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    NumberField(
                        label = stringResource(
                            if (plan.resize == BatchResize.WIDTH) {
                                R.string.label_width
                            } else {
                                R.string.label_height
                            },
                        ),
                        value = plan.resizeValue,
                        onValueChange = { plan = plan.copy(resizeValue = it) },
                        range = 16..8192,
                    )
                }
            }

            // --- Recortar a proporcion -------------------------------------------------
            SectionTitle(stringResource(R.string.batch_crop_ratio))
            val ratioLabels = remember(context) { Ratios.map { context.getString(it.first) } }
            OptionRow(
                options = Ratios.indices.toList(),
                selected = ratioIndex,
                onSelect = { ratioIndex = it },
                label = { ratioLabels[it] },
            )

            // --- Preset o filtro -------------------------------------------------------
            SectionTitle(stringResource(R.string.batch_apply_preset))
            val looks = remember(userData.presets, context) {
                buildList {
                    add(Look(null, null, context.getString(R.string.label_none)))
                    Filters.all.filterNot { it.id == "original" }.forEach { filter ->
                        add(Look(filter.id, null, context.getString(filter.labelRes)))
                    }
                    userData.presets.forEach { preset ->
                        add(Look(null, preset.id, preset.name))
                    }
                }
            }
            val currentLook = looks.firstOrNull {
                it.filterId == plan.filterId && it.presetId == plan.presetId
            } ?: looks.first()
            OptionRow(
                options = looks,
                selected = currentLook,
                onSelect = { look ->
                    plan = plan.copy(filterId = look.filterId, presetId = look.presetId)
                },
                label = { it.label },
            )

            // --- Marca de agua ---------------------------------------------------------
            SectionTitle(stringResource(R.string.batch_watermark))
            if (userData.watermarks.isEmpty()) {
                Text(
                    text = stringResource(R.string.watermark_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                val marks = remember(userData.watermarks, context) {
                    listOf<String?>(null) + userData.watermarks.map { it.id }
                }
                val markLabels = remember(userData.watermarks, context) {
                    mapOf<String?, String>(null to context.getString(R.string.label_none)) +
                        userData.watermarks.associate { it.id to it.name }
                }
                OptionRow(
                    options = marks,
                    selected = plan.watermarkId,
                    onSelect = { plan = plan.copy(watermarkId = it) },
                    label = { markLabels[it].orEmpty() },
                )
            }

            // --- Formato y calidad -----------------------------------------------------
            SectionTitle(stringResource(R.string.batch_convert))
            ToggleRow(
                label = stringResource(R.string.batch_convert),
                checked = plan.convert,
                onCheckedChange = { plan = plan.copy(convert = it) },
            )
            if (plan.convert) {
                val formatLabels = remember(context) {
                    mapOf(
                        ExportFormat.JPEG to context.getString(R.string.export_format_jpeg),
                        ExportFormat.PNG to context.getString(R.string.export_format_png),
                        ExportFormat.WEBP to context.getString(R.string.export_format_webp_lossy),
                    )
                }
                OptionRow(
                    options = Formats,
                    selected = plan.format,
                    onSelect = { plan = plan.copy(format = it) },
                    label = { formatLabels[it].orEmpty() },
                )
            }
            val lossy = !plan.convert || plan.format.hasQuality
            if (lossy) {
                IntSliderRow(
                    label = stringResource(R.string.label_quality),
                    value = plan.quality,
                    onValueChange = { plan = plan.copy(quality = it) },
                    range = 40..100,
                    suffix = " %",
                    defaultValue = 92,
                )
                ToggleRow(
                    label = stringResource(R.string.export_target_size),
                    checked = targetSize,
                    onCheckedChange = { targetSize = it },
                )
                if (targetSize) {
                    IntSliderRow(
                        label = stringResource(R.string.export_target_size_kb, targetKb),
                        value = targetKb,
                        onValueChange = { targetKb = it },
                        range = 50..5000,
                        defaultValue = 500,
                    )
                }
            }

            // --- EXIF y nombres --------------------------------------------------------
            ToggleRow(
                label = stringResource(R.string.batch_strip_exif),
                checked = !plan.keepExif,
                onCheckedChange = { plan = plan.copy(keepExif = !it) },
            )
            TextFieldRow(
                label = stringResource(R.string.batch_rename),
                value = plan.pattern,
                onValueChange = { plan = plan.copy(pattern = it) },
                placeholder = stringResource(R.string.batch_rename_hint),
            )

            HorizontalDivider()

            if (running) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(
                            R.string.batch_running,
                            progressDone,
                            if (total > 0) total else uris.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val fraction = if (total > 0) progressDone / total.toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { BatchWorker.cancel(context) }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            start {
                                // Sin carpeta: el worker deja los archivos en cache y al
                                // terminar se abre el selector para compartirlos.
                                launchedId =
                                    BatchWorker.enqueue(context, uris, effectivePlan(), null)
                                        .toString()
                            }
                        },
                    ) { Text(stringResource(R.string.batch_share_all)) }
                    Button(
                        onClick = { start { pickFolder.launch(null) } },
                    ) { Text(stringResource(R.string.batch_save_folder)) }
                }
            }
        }
    }
}

/** Una entrada de la lista de aspecto: filtro predefinido, preset propio o ninguno. */
private data class Look(
    val filterId: String?,
    val presetId: String?,
    val label: String,
)
