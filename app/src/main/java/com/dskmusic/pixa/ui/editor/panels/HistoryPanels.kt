package com.dskmusic.pixa.ui.editor.panels

import kotlinx.coroutines.delay
import java.io.OutputStream
import android.graphics.Bitmap
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.export.ExportFormat
import com.dskmusic.pixa.core.export.ExportOptions
import com.dskmusic.pixa.core.export.Exporter
import com.dskmusic.pixa.core.export.ImageInfo
import com.dskmusic.pixa.core.export.ImageInfoReader
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ui.common.EmptyState
import com.dskmusic.pixa.ui.common.IntSliderRow
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.semanticsLabel
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorState
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ======================================================================================
// Historial (requisito 16)
// ======================================================================================

/**
 * Lista de pasos con salto directo.
 *
 * El paso donde esta el cursor se marca en negrita; tocar cualquier otro salta a ese
 * momento sin perder los siguientes, igual que un historial de Photoshop: solo se
 * descartan cuando se hace una edicion nueva estando atras.
 */
@Composable
fun HistoryPanel(vm: EditorViewModel, state: EditorState, settings: PixaSettings) {
    Column {
        if (state.history.isEmpty()) {
            EmptyState(stringResource(R.string.history_empty))
        } else {
            state.history.forEach { entry ->
                val current = entry.index == state.historyCursor
                val label = stringResource(R.string.history_step, entry.index + 1)
                val step = stringResource(entry.labelRes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { vm.jumpToHistory(entry.index) }
                        .background(
                            if (current) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .semanticsLabel("$label, $step"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$label · $step",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        HorizontalDivider()
        IntSliderRow(
            label = stringResource(R.string.history_limit),
            value = settings.historyLimit,
            onValueChange = { vm.setHistoryLimit(it) },
            range = 10..100,
            defaultValue = 40,
        )
        PanelHint(R.string.help_history)
    }
}

// ======================================================================================
// Exportar (requisito 5)
// ======================================================================================

/**
 * Exportar o compartir la imagen final.
 *
 * El panel insiste en la diferencia del requisito 3: aqui se escribe una imagen plana en
 * la galeria, y para seguir editando hay que guardar el proyecto.
 */
@Composable
fun ExportPanel(vm: EditorViewModel, state: EditorState) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(ExportOptions()) }
    var targetSize by remember { mutableStateOf(false) }
    var targetKb by remember { mutableStateOf(500) }
    var onlyLayer by remember { mutableStateOf(false) }
    var onlySelection by remember { mutableStateOf(false) }
    // Requisito del usuario: opcion de sobrescribir la imagen original de la que viene el
    // documento, como alternativa a "guardar como". Solo aparece si hay una imagen origen
    // y nunca esta marcada por defecto.
    var replaceOriginal by remember { mutableStateOf(false) }
    // Solo se puede "reemplazar el original" si la uri apunta a una foto real (no a una
    // copia temporal de cache que otra app mando al compartir, p.ej. la camara de Xiaomi).
    val canReplace = state.sourceUri != null &&
        Exporter.isReplaceable(context, state.sourceUri!!)
    val formatNames = remember(context) {
        ExportFormat.entries.associateWith { context.getString(EditorLabels.exportFormat(it)) }
    }

    // Formato del archivo original (solo se usa al reemplazar). Se detecta por el MIME
    // del content resolver, que es barato y no lee pixeles; si no es reconocible (HEIC,
    // GIF...) se cae a JPEG, el mas universal.
    val originalFormat = remember(state.sourceUri) {
        val mime = state.sourceUri?.let { runCatching { context.contentResolver.getType(it) }.getOrNull() }
        ExportFormat.fromMime(mime) ?: ExportFormat.JPEG
    }

    // Al reemplazar, el formato de salida se fija al del original (no se elige), y el
    // tamano objetivo/calidad solo aparece si el formato los admite.
    val outputFormat = if (replaceOriginal) originalFormat else options.format
    val canTarget = outputFormat.hasQuality
    val effective = options.copy(
        format = outputFormat,
        targetSizeKb = if (targetSize && canTarget) targetKb else null,
    )

    // --- Reemplazar el original: permiso de escritura ----------------------------------
    // Android 11+ usa acceso total a archivos (MANAGE_EXTERNAL_STORAGE, se concede una sola
    // vez en Ajustes); Android 10 usa WRITE_EXTERNAL_STORAGE en runtime. Ambos se resuelven
    // aqui antes de escribir, sin pedir consentimiento puntual por cada archivo.

    /** Llama al ViewModel para escribir el resultado final (reemplazando o no). */
    fun runExport(replace: Boolean) {
        vm.exportImage(
            options = effective,
            onlyLayerId = state.doc.selectedLayerId.takeIf { onlyLayer },
            cropToSelection = onlySelection && state.selection != null,
            replaceOriginal = replace,
        )
    }

    // Android 10 pide WRITE_EXTERNAL_STORAGE en runtime; Android 11+ usa acceso total a
    // archivos (se abre en Ajustes una sola vez y queda concedido).
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) runExport(replace = true) else replaceOriginal = false
    }

    // true mientras se espera a que el usuario conceda el acceso total en Ajustes, para
    // reintentar la escritura al volver.
    var waitingForPermission by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && waitingForPermission) {
                waitingForPermission = false
                if (Exporter.canAccessAllFiles()) runExport(replace = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /**
     * Punto de entrada del boton "Exportar". Si se pide reemplazar, se asegura el permiso
     * de escritura sobre el almacen y solo entonces escribe; si no lo tiene, lo pide (abre
     * Ajustes una sola vez, y al volver concedido reintenta).
     */
    fun doExport(replace: Boolean) {
        Log.d("PixaReplace", "doExport replace=$replace")
        if (!replace) {
            runExport(replace = false)
            return
        }
        if (state.sourceUri == null) {
            replaceOriginal = false
            vm.post(R.string.export_failed)
            return
        }

        // Con acceso total a archivos (Android 11+) o WRITE_EXTERNAL_STORAGE (Android 10) se
        // escribe directo, sin consentimiento puntual.
        if (Exporter.canAccessAllFiles()) {
            runExport(replace = true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            waitingForPermission = true
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            context.startActivity(intent)
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) runExport(replace = true)
            else storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Column {
        PanelHint(R.string.help_export)
        if (replaceOriginal) {
            // Al reemplazar el formato se fija al del original y no se puede cambiar ni
            // renombrar: se sobrescribe el archivo tal cual estaba.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.export_replace_format, stringResource(EditorLabels.exportFormat(originalFormat))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            OptionRow(
                options = ExportFormat.entries,
                selected = outputFormat,
                onSelect = { options = options.copy(format = it) },
                label = { formatNames[it].orEmpty() },
            )
            TextFieldRow(
                label = stringResource(R.string.label_name),
                value = options.baseName,
                onValueChange = { options = options.copy(baseName = it.ifBlank { "pixa" }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        IntSliderRow(
            label = stringResource(R.string.export_scale),
            value = options.scalePercent,
            onValueChange = { options = options.copy(scalePercent = it) },
            range = 25..400,
            suffix = " %",
            defaultValue = 100,
        )
        if (canTarget) {
            IntSliderRow(
                label = stringResource(R.string.label_quality),
                value = options.quality,
                onValueChange = { options = options.copy(quality = it) },
                range = 1..100,
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
                    range = 20..5000,
                    defaultValue = 500,
                )
            }
        }
        if (!replaceOriginal) {
            ToggleRow(
                label = stringResource(
                    if (options.keepExif) R.string.export_keep_exif else R.string.export_strip_exif,
                ),
                checked = options.keepExif,
                onCheckedChange = { options = options.copy(keepExif = it) },
            )

            HorizontalDivider()
            ToggleRow(
                label = stringResource(R.string.export_layer_only),
                checked = onlyLayer,
                onCheckedChange = { onlyLayer = it },
                enabled = state.doc.selectedLayerId != null,
            )
            ToggleRow(
                label = stringResource(R.string.export_selection_only),
                checked = onlySelection,
                onCheckedChange = { onlySelection = it },
                enabled = state.selection != null,
            )
        }
        if (canReplace) {
            HorizontalDivider()
            ToggleRow(
                label = stringResource(R.string.export_replace_original),
                subtitle = stringResource(R.string.export_replace_original_hint),
                checked = replaceOriginal,
                onCheckedChange = { replaceOriginal = it },
            )
        }

        PanelActions {
            TextButton(
                onClick = { doExport(replaceOriginal && canReplace) },
            ) {
                Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_export))
            }
            TextButton(
                onClick = {
                    vm.shareImage(effective) { uri, mime ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                context.getString(R.string.share_image),
                            ),
                        )
                    }
                },
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_share))
            }
        }
    }
}

// ======================================================================================
// Informacion de imagen (requisito 5)
// ======================================================================================

/**
 * Datos del lienzo y, si el documento viene de una foto, del archivo original.
 *
 * El EXIF se lee del archivo y no del documento: una vez editada, la imagen en memoria ya
 * no lleva esos datos, asi que mostrarlos del original es lo unico veraz.
 */
@Composable
fun ImagePanel(vm: EditorViewModel, state: EditorState) {
    val context = LocalContext.current
    val uri = state.sourceUri

    // Peso estimado del resultado actual: se recalcula con cada cambio del documento, asi
    // sigue la mejora con IA y los pasos de deshacer y rehacer. El del archivo original
    // (mas abajo) no cambia nunca: es el del archivo en disco.
    var estimate by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.doc) {
        estimate = null
        delay(300)
        estimate = withContext(Dispatchers.Default) {
            runCatching {
                val bmp = vm.compositeNow()
                val counter = object : OutputStream() {
                    var count = 0L
                    override fun write(b: Int) { count++ }
                    override fun write(b: ByteArray, off: Int, len: Int) { count += len }
                }
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, counter)
                bmp.recycle()
                counter.count
            }.getOrNull()
        }
    }
    var info by remember(uri) { mutableStateOf<ImageInfo?>(null) }

    LaunchedEffect(uri) {
        info = if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) { runCatching { ImageInfoReader.read(context, uri) }.getOrNull() }
        }
    }

    Column {
        InfoRow(stringResource(R.string.image_info_canvas), canvasText(state.doc))
        InfoRow(stringResource(R.string.image_info_layers), state.doc.layers.size.toString())
        // Lee los pixeles reales de la capa elegida, no el tamano del archivo original:
        // cambia con la mejora con IA y con cada deshacer o rehacer.
        val layerBitmap = state.doc.selected?.let { vm.bitmapOf(it) }
        InfoRow(
            stringResource(R.string.image_info_layer_px),
            layerBitmap?.let {
                "${it.width} x ${it.height} px · ${String.format("%.1f", it.width * it.height / 1_000_000f)} MP"
            } ?: "—",
        )
        InfoRow(
            stringResource(R.string.image_info_estimate),
            estimate?.let { ImageInfoReader.formatSize(it) }
                ?: stringResource(R.string.image_info_calculating),
        )

        val data = info
        if (data == null) {
            PanelHint(R.string.image_info_no_exif)
            return@Column
        }
        HorizontalDivider()
        Text(
            stringResource(R.string.image_info_original_file),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        InfoRow(stringResource(R.string.image_info_source), data.name ?: "—")
        InfoRow(stringResource(R.string.label_format), data.mime ?: "—")
        InfoRow(
            stringResource(R.string.image_info_dimensions),
            "${data.width} x ${data.height} px · ${String.format("%.1f", data.megapixels)} MP",
        )
        InfoRow(
            stringResource(R.string.image_info_weight),
            ImageInfoReader.formatSize(data.sizeBytes),
        )
        HorizontalDivider()
        val exif = listOf(
            stringResource(R.string.image_info_date) to data.dateTime,
            stringResource(R.string.image_info_camera) to data.camera,
            stringResource(R.string.image_info_lens) to data.lens,
        ).filter { it.second != null }
        if (exif.isEmpty() && !data.hasGps) {
            InfoRow(stringResource(R.string.image_info_exif), stringResource(R.string.image_info_no_exif))
        } else {
            exif.forEach { (label, value) -> InfoRow(label, value.orEmpty()) }
            if (data.hasGps) {
                InfoRow(
                    stringResource(R.string.image_info_gps),
                    stringResource(R.string.image_info_gps_present),
                )
            }
        }
    }
}

private fun canvasText(doc: PixaDocument): String {
    val mp = doc.width * doc.height / 1_000_000f
    return "${doc.width} x ${doc.height} px · ${String.format("%.1f", mp)} MP"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
