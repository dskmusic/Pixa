package com.dskmusic.pixa.ui.editor.panels

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconToggleButton
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Close
import com.dskmusic.pixa.core.model.FilterCategory
import com.dskmusic.pixa.core.image.LutStore
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.image.Adjust
import com.dskmusic.pixa.core.image.Effects
import com.dskmusic.pixa.core.image.Filters
import com.dskmusic.pixa.core.image.ToneOps
import com.dskmusic.pixa.core.model.AdjustPreset
import com.dskmusic.pixa.core.model.Adjustments
import com.dskmusic.pixa.core.model.BuiltInFilter
import com.dskmusic.pixa.core.model.EffectKind
import com.dskmusic.pixa.core.model.EffectSpec
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.ConfirmDialog
import com.dskmusic.pixa.ui.common.MinimizedPanelBar
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.TextPromptDialog
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ======================================================================================
// Ajustes basicos (requisito 13)
// ======================================================================================

/**
 * Los trece ajustes, con previsualizacion viva y un boton "Aplicar" que los graba.
 *
 * Mientras se mueve un deslizador el documento no cambia: lo que se ve es la
 * previsualizacion a resolucion reducida. Eso mantiene el historial limpio (un unico
 * paso por tanda de ajustes) y la interfaz fluida.
 */
/** [doc] no se usa aqui dentro: solo fuerza la recomposicion al cambiar de capa elegida,
 * que de lo contrario puede quedarse ensenando el aviso de "elige una capa" (vm no cambia
 * de identidad y Compose puede saltarse la llamada). */
@Composable
fun AdjustPanel(
    vm: EditorViewModel,
    doc: PixaDocument,
    title: String,
    help: String,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    minimized: Boolean,
    onMaximize: () -> Unit,
    scroll: ScrollState,
    /** Abre la seccion IA (donde viven ahora la mejora de detalle y la ampliacion). */
    onOpenAi: () -> Unit = {},
) {
    val preview = rememberLivePreview(vm)
    var adj by remember(preview) { mutableStateOf(Adjustments()) }
    var savingPreset by remember { mutableStateOf(false) }
    // Al entrar, cortina y deslizadores en su valor por defecto.
    DisposableEffect(Unit) {
        vm.resetCurtain()
        onDispose { vm.releaseCurtain() }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun change(next: Adjustments) {
        adj = next
        preview?.update(R.string.adjust_title) { Adjust.apply(it, next) }
    }

    // Requisito del usuario: el "Aplicar" homogeneizado sube a la cabecera, junto a ayuda
    // y cerrar, para ahorrar la fila que ocupaba abajo; y se sigue viendo minimizado, para
    // poder aplicar sin tener que desplegar el panel solo para eso.
    val applyAction: (() -> Unit)? = preview?.let { p ->
        {
            val applied = adj
            p.apply(R.string.adjust_title) { Adjust.apply(it, applied) }
            adj = Adjustments()
        }
    }
    if (minimized) {
        MinimizedPanelBar(
            text = title,
            onMaximize = onMaximize,
            onClose = onClose,
            onApply = applyAction,
            applyEnabled = !adj.isIdentity,
        )
    } else {
        SectionTitle(
            text = title,
            help = help,
            onClose = onClose,
            onMinimize = onMinimize,
            onApply = applyAction,
            applyEnabled = !adj.isIdentity,
        )
    }
    if (preview == null) {
        NeedsRasterNotice(vm)
        return
    }

    Column(
        modifier = (if (minimized) Modifier.height(0.dp) else Modifier)
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    val source = preview.source
                    scope.launch {
                        val auto = withContext(Dispatchers.Default) { ToneOps.autoEnhance(source) }
                        // ponytail: sin aviso. El resultado se ve en el lienzo y en los
                        // deslizadores, que se colocan solos en su valor nuevo.
                        change(auto)
                    }
                },
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.adjust_auto))
            }
            // Requisito del usuario: "Mejorar con IA" alineado a la derecha del todo.
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenAi) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ai_enhance))
            }
        }

        AdjustSlider(R.string.adjust_brightness, adj.brightness) { change(adj.copy(brightness = it)) }
        AdjustSlider(R.string.adjust_contrast, adj.contrast) { change(adj.copy(contrast = it)) }
        AdjustSlider(R.string.adjust_exposure, adj.exposure) { change(adj.copy(exposure = it)) }
        AdjustSlider(R.string.adjust_saturation, adj.saturation) { change(adj.copy(saturation = it)) }
        AdjustSlider(R.string.adjust_vibrance, adj.vibrance) { change(adj.copy(vibrance = it)) }
        AdjustSlider(R.string.adjust_temperature, adj.temperature) {
            change(adj.copy(temperature = it))
        }
        AdjustSlider(R.string.adjust_tint, adj.tint) { change(adj.copy(tint = it)) }
        AdjustSlider(R.string.adjust_shadows, adj.shadows) { change(adj.copy(shadows = it)) }
        AdjustSlider(R.string.adjust_highlights, adj.highlights) {
            change(adj.copy(highlights = it))
        }
        AdjustSlider(R.string.adjust_lens, adj.lens) { change(adj.copy(lens = it)) }
        AdjustSlider(R.string.adjust_clarity, adj.clarity) { change(adj.copy(clarity = it)) }
        AdjustSlider(R.string.adjust_texture, adj.texture) { change(adj.copy(texture = it)) }
        AdjustSlider(R.string.adjust_dehaze, adj.dehaze) { change(adj.copy(dehaze = it)) }
        AdjustSlider(R.string.adjust_sharpen, adj.sharpen, positiveOnly = true) {
            change(adj.copy(sharpen = it))
        }
        AdjustSlider(R.string.adjust_sharpen_edges, adj.sharpenEdges, positiveOnly = true) {
            change(adj.copy(sharpenEdges = it))
        }
        AdjustSlider(R.string.adjust_denoise, adj.denoise, positiveOnly = true) {
            change(adj.copy(denoise = it))
        }
        AdjustSlider(R.string.adjust_blur, adj.blur, positiveOnly = true) { change(adj.copy(blur = it)) }
        // Centrado: a la izquierda vineta negra, a la derecha blanca.
        AdjustSlider(R.string.adjust_vignette, adj.vignette) {
            change(adj.copy(vignette = it))
        }
        AdjustSlider(R.string.adjust_grain, adj.grain, positiveOnly = true) {
            change(adj.copy(grain = it))
        }

        Text(
            stringResource(R.string.preview_hold_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        CompareControls(vm, enabled = !adj.isIdentity)
        MyPresets(vm = vm)
        // Requisito del usuario: guardar preset al pie del panel.
        PanelActions {
            TextButton(onClick = { savingPreset = true }, enabled = !adj.isIdentity) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.preset_save))
            }
        }
    }

    if (savingPreset) {
        TextPromptDialog(
            title = stringResource(R.string.preset_save),
            initial = "",
            label = stringResource(R.string.preset_name_hint),
            onConfirm = { name ->
                val preset = AdjustPreset(
                    id = PixaLayer.newId(),
                    name = name,
                    adjustments = adj,
                )
                scope.launch {
                    context.pixa.userData.savePreset(preset)
                    vm.post(R.string.preset_saved)
                }
            },
            onDismiss = { savingPreset = false },
        )
    }
}

@Composable
private fun AdjustSlider(
    labelRes: Int,
    value: Float,
    positiveOnly: Boolean = false,
    onChange: (Float) -> Unit,
) {
    SliderRow(
        label = stringResource(labelRes),
        value = value,
        onValueChange = onChange,
        valueRange = if (positiveOnly) 0f..1f else -1f..1f,
        valueText = formatPercent(value, signed = !positiveOnly),
        defaultValue = 0f,
    )
}

/** Presets propios del usuario, aplicables de un toque (requisito 13). */
@Composable
private fun MyPresets(vm: EditorViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userData by context.pixa.userData.data.collectAsStateWithLifecycle()
    if (userData.presets.isEmpty()) return
    Text(
        stringResource(R.string.preset_my_presets),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        userData.presets.forEach { preset ->
            TextButton(
                onClick = {
                    vm.applyToRaster(R.string.preset_my_presets) {
                        Filters.applyPreset(it, preset)
                    }
                },
            ) { Text(preset.name) }
            IconButton(onClick = { scope.launch { context.pixa.userData.deletePreset(preset.id) } }) {
                Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete_item))
            }
        }
    }
}

// ======================================================================================
// Filtros predefinidos (requisito 13)
// ======================================================================================

/**
 * Los dieciocho filtros con miniatura real de la capa y deslizador de intensidad.
 *
 * Las miniaturas se calculan una vez por capa sobre una copia de 72 px: con eso el
 * usuario ve el efecto de verdad en lugar de un nombre, y el coste es despreciable.
 */
/** Ver el comentario de [AdjustPanel] sobre por que [doc] viaja sin usarse. */
@Composable
fun FilterPanel(
    vm: EditorViewModel,
    doc: PixaDocument,
    title: String,
    help: String,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    minimized: Boolean,
    onMaximize: () -> Unit,
    scroll: ScrollState,
) {
    val preview = rememberLivePreview(vm)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userData by context.pixa.userData.data.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf(FilterCategory.BASIC) }
    var pick by remember(preview) { mutableStateOf<LookPick?>(null) }
    var strength by remember(preview) { mutableStateOf(1f) }
    // Ajustes (intensidad) ocultos hasta tocar otra vez el look ya elegido.
    var showParams by remember(preview) { mutableStateOf(false) }
    // Sube al importar o borrar una LUT para que la lista de miniaturas se recalcule.
    var lutVersion by remember { mutableStateOf(0) }
    val thumbScroll = rememberScrollState()
    LaunchedEffect(category) { thumbScroll.scrollTo(0) }
    val appSettings by context.pixa.settings.settings.collectAsStateWithLifecycle(PixaSettings())
    val confirmLutDelete = appSettings.confirmLutDelete
    var confirmingLut by remember { mutableStateOf<String?>(null) }
    fun deleteLut(path: String) {
        LutStore.list(context).firstOrNull { it.path == path }?.let { LutStore.delete(it) }
        pick = null
        preview?.cancel()
        lutVersion++
    }
    confirmingLut?.let { path ->
        ConfirmDialog(
            title = stringResource(R.string.lut_delete),
            body = LutStore.list(context).firstOrNull { it.path == path }
                ?.let { LutStore.displayName(it) }.orEmpty(),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                confirmingLut = null
                deleteLut(path)
            },
            onDismiss = { confirmingLut = null },
        )
    }
    DisposableEffect(Unit) {
        vm.resetCurtain()
        onDispose { vm.releaseCurtain() }
    }

    // Requisito del usuario: "Aplicar" homogeneizado arriba junto a ayuda/cerrar, y sin
    // "Cancelar" abajo (cerrar el panel ya descarta el filtro elegido, no hace falta otro
    // boton para lo mismo); se sigue viendo minimizado, para poder aplicar sin desplegar.
    val applyAction: (() -> Unit)? = if (preview != null && pick != null) {
        {
            val chosen = pick!!
            val k = strength
            preview.apply(chosen.labelRes) { chosen.op(it, k) }
            pick = null
        }
    } else {
        null
    }
    if (minimized) {
        MinimizedPanelBar(
            text = title,
            onMaximize = onMaximize,
            onClose = onClose,
            onApply = applyAction,
        )
    } else {
        SectionTitle(
            text = title,
            help = help,
            onClose = onClose,
            onMinimize = onMinimize,
            onApply = applyAction,
        )
    }

    val importLut = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = LutStore.import(context, uri)
                vm.post(if (saved != null) R.string.lut_imported else R.string.lut_invalid)
                lutVersion++
            }
        }
    }

    if (preview == null) {
        NeedsRasterNotice(vm)
        return
    }

    val thumbs by produceState<List<LookThumb>>(
        emptyList(),
        preview, category, lutVersion, userData.presets,
    ) {
        value = emptyList()
        val base = withContext(Dispatchers.Default) { BitmapUtils.thumbnail(preview.source, 72) }
        value = withContext(Dispatchers.Default) { buildThumbs(context, category, base, userData.presets) }
    }

    Column(
        modifier = (if (minimized) Modifier.height(0.dp) else Modifier)
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(bottom = 4.dp),
    ) {
        OptionRow(
            options = FilterCategory.entries,
            selected = category,
            onSelect = {
                // Otra categoria: vuelve la fila al principio y se descarta el look elegido.
                if (it != category) {
                    category = it
                    pick = null
                    preview.cancel()
                    vm.resetCurtain()
                }
            },
            label = { context.getString(it.labelRes()) },
        )
        if (category == FilterCategory.LUT) {
            PanelActions {
                val current = pick?.key
                if (current != null && thumbs.any { it.key == current }) {
                    TextButton(
                        onClick = {
                            // Segun Ajustes: pregunta antes de borrar o borra directamente.
                            if (confirmLutDelete) confirmingLut = current else deleteLut(current)
                        },
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.lut_delete))
                    }
                }
                TextButton(onClick = { importLut.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.lut_import))
                }
            }
        }
        if (thumbs.isEmpty()) {
            val empty = (category == FilterCategory.LUT && LutStore.list(context).isEmpty()) ||
                (category == FilterCategory.MY_PRESETS && userData.presets.isEmpty())
            Text(
                stringResource(
                    when {
                        !empty -> R.string.label_loading
                        category == FilterCategory.LUT -> R.string.lut_empty_hint
                        else -> R.string.preset_empty_hint
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(thumbScroll)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            thumbs.forEach { thumb ->
                FilterThumb(
                    label = thumb.label,
                    bitmap = thumb.bitmap,
                    selected = pick?.key == thumb.key,
                    editable = true,
                    onClick = {
                        if (pick?.key == thumb.key) {
                            showParams = !showParams
                            return@FilterThumb
                        }
                        showParams = false
                        pick = thumb.pick
                        strength = 1f
                        vm.resetCurtain()
                        preview.update(thumb.pick.labelRes) { thumb.pick.op(it, 1f) }
                    },
                )
            }
        }
        pick?.takeIf { showParams }?.let { chosen ->
            SliderRow(
                label = stringResource(R.string.label_intensity),
                value = strength,
                onValueChange = {
                    strength = it
                    preview.update(chosen.labelRes) { src -> chosen.op(src, it) }
                },
                valueText = formatPercent(strength),
                defaultValue = 1f,
            )
        }
        CompareControls(vm, enabled = pick != null)
    }
}

/** Un look elegido en el panel: lo que se aplica con la intensidad dada (0..1). */
private class LookPick(
    val key: String,
    @StringRes val labelRes: Int,
    val op: (Bitmap, Float) -> Bitmap,
)

/** Miniatura de la lista de looks, con el look que se aplica al tocarla. */
private class LookThumb(val label: String, val bitmap: Bitmap, val pick: LookPick) {
    val key: String get() = pick.key
}

/**
 * Miniaturas de una categoria sobre la copia de 72 px de la capa: se calculan solo las de
 * la categoria visible, no las de todo el catalogo, para que cambiar de pestana sea rapido.
 */
private fun buildThumbs(
    context: Context,
    category: FilterCategory,
    base: Bitmap,
    presets: List<AdjustPreset>,
): List<LookThumb> = when (category) {
    FilterCategory.LUT -> LutStore.list(context).mapNotNull { file ->
        val lut = LutStore.load(file) ?: return@mapNotNull null
        runCatching {
            LookThumb(
                LutStore.displayName(file),
                lut.apply(base, 1f),
                LookPick(file.path, R.string.lut_title) { b, k -> lut.apply(b, k) },
            )
        }.getOrNull()
    }

    FilterCategory.MY_PRESETS -> presets.mapNotNull { preset ->
        runCatching {
            LookThumb(
                preset.name,
                Filters.applyPreset(base, preset),
                LookPick(preset.id, R.string.preset_my_presets) { b, k ->
                    Filters.mix(b, Filters.applyPreset(b, preset), k)
                },
            )
        }.getOrNull()
    }

    else -> Filters.byCategory(category).mapNotNull { filter ->
        runCatching {
            LookThumb(
                context.getString(filter.labelRes),
                Filters.apply(base, filter, 1f),
                LookPick(filter.id, filter.labelRes) { b, k -> Filters.apply(b, filter, k) },
            )
        }.getOrNull()
    }
}

@StringRes
private fun FilterCategory.labelRes(): Int = when (this) {
    FilterCategory.BASIC -> R.string.fcat_basic
    FilterCategory.PORTRAIT -> R.string.fcat_portrait
    FilterCategory.LANDSCAPE -> R.string.fcat_landscape
    FilterCategory.FOOD -> R.string.fcat_food
    FilterCategory.CINEMA -> R.string.fcat_cinema
    FilterCategory.ANALOG -> R.string.fcat_analog
    FilterCategory.RETRO -> R.string.fcat_retro
    FilterCategory.NEON -> R.string.fcat_neon
    FilterCategory.SKY -> R.string.fcat_sky
    FilterCategory.LUT -> R.string.fcat_lut
    FilterCategory.MY_PRESETS -> R.string.preset_my_presets
}

@Composable
private fun FilterThumb(
    label: String,
    bitmap: Bitmap,
    selected: Boolean,
    onClick: () -> Unit,
    // Con ajustes: un lapiz sobre la miniatura elegida; tocarla otra vez los despliega.
    editable: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onClick),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (selected && editable) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp)
                        .size(14.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ======================================================================================
// Efectos (requisito 13)
// ======================================================================================

/** Efectos creativos: byn, duotono, glitch, semitono, desenfoques direccionales. */
/** Ver el comentario de [AdjustPanel] sobre por que [doc] viaja sin usarse. */
@Composable
fun EffectPanel(
    vm: EditorViewModel,
    doc: PixaDocument,
    settings: PixaSettings,
    title: String,
    help: String,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    minimized: Boolean,
    onMaximize: () -> Unit,
    scroll: ScrollState,
) {
    val preview = rememberLivePreview(vm)
    var spec by remember(preview) { mutableStateOf(EffectSpec()) }
    var group by remember { mutableStateOf(EffectGroup.CLASSIC) }
    var showParams by remember(preview) { mutableStateOf(false) }
    DisposableEffect(Unit) {
        vm.resetCurtain()
        onDispose { vm.releaseCurtain() }
    }
    val context = LocalContext.current
    val names = remember(context) {
        EffectKind.entries.associateWith { context.getString(EditorLabels.effect(it)) }
    }

    fun change(next: EffectSpec) {
        spec = next
        if (next.kind == EffectKind.NONE) {
            preview?.cancel()
        } else {
            preview?.update(EditorLabels.effect(next.kind)) { Effects.apply(it, next) }
        }
    }

    // Requisito del usuario: "Aplicar" homogeneizado arriba junto a ayuda/cerrar, y sin
    // "Cancelar" abajo (cerrar el panel ya descarta el efecto elegido); se sigue viendo
    // minimizado, para poder aplicar sin desplegar el panel solo para eso.
    val applyAction: (() -> Unit)? = if (preview != null && spec.kind != EffectKind.NONE) {
        {
            val applied = spec
            preview.apply(EditorLabels.effect(applied.kind)) { Effects.apply(it, applied) }
            spec = EffectSpec()
        }
    } else {
        null
    }
    if (minimized) {
        MinimizedPanelBar(
            text = title,
            onMaximize = onMaximize,
            onClose = onClose,
            onApply = applyAction,
        )
    } else {
        SectionTitle(
            text = title,
            help = help,
            onClose = onClose,
            onMinimize = onMinimize,
            onApply = applyAction,
        )
    }
    if (preview == null) {
        NeedsRasterNotice(vm)
        return
    }

    Column(
        modifier = (if (minimized) Modifier.height(0.dp) else Modifier)
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(bottom = 4.dp),
    ) {
        OptionRow(
            options = EffectGroup.entries,
            selected = group,
            onSelect = { group = it },
            label = { context.getString(it.labelRes) },
        )
        // Miniaturas de cada efecto sobre una copia de 72 px de la capa, como en Filtros.
        val thumbs by produceState<List<Pair<EffectKind, Bitmap>>>(emptyList(), preview, group) {
            value = emptyList()
            value = withContext(Dispatchers.Default) {
                val base = BitmapUtils.thumbnail(preview.source, 72)
                group.kinds.map { kind ->
                    val made = if (kind == EffectKind.NONE) {
                        null
                    } else {
                        runCatching { Effects.apply(base, EffectSpec(kind = kind)) }.getOrNull()
                    }
                    kind to (made ?: base)
                }
            }
        }
        val thumbScroll = rememberScrollState()
        LaunchedEffect(group) { thumbScroll.scrollTo(0) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(thumbScroll)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            thumbs.forEach { (kind, bitmap) ->
                FilterThumb(
                    label = names[kind].orEmpty(),
                    bitmap = bitmap,
                    selected = spec.kind == kind,
                    editable = kind != EffectKind.NONE,
                    // Otro efecto: parametros y cortina vuelven a su valor inicial. Tocar el
                    // ya elegido despliega o pliega sus ajustes.
                    onClick = {
                        if (spec.kind == kind && kind != EffectKind.NONE) {
                            showParams = !showParams
                            return@FilterThumb
                        }
                        showParams = false
                        change(EffectSpec(kind = kind))
                        vm.resetCurtain()
                    },
                )
            }
        }
        if (showParams && spec.kind != EffectKind.NONE) {
            SliderRow(
                label = stringResource(R.string.label_amount),
                value = spec.amount,
                onValueChange = { change(spec.copy(amount = it)) },
                valueText = formatPercent(spec.amount),
                defaultValue = 0.5f,
            )
        }
        when (if (showParams) spec.kind else EffectKind.NONE) {
            EffectKind.POSTERIZE -> SliderRow(
                label = stringResource(R.string.fx_posterize),
                value = spec.levels.toFloat(),
                onValueChange = { change(spec.copy(levels = it.toInt().coerceIn(2, 32))) },
                valueRange = 2f..32f,
                steps = 29,
                valueText = spec.levels.toString(),
                defaultValue = 5f,
            )

            EffectKind.DUOTONE, EffectKind.DOUBLE_EXPOSURE -> ColorPairRow(
                labelA = stringResource(R.string.label_background),
                colorA = spec.colorA,
                onPickA = { change(spec.copy(colorA = it)) },
                labelB = stringResource(R.string.label_foreground),
                colorB = spec.colorB,
                onPickB = { change(spec.copy(colorB = it)) },
                settings = settings,
            )

            EffectKind.SPLIT_TONE -> ColorPairRow(
                labelA = stringResource(R.string.adjust_shadows),
                colorA = spec.colorA,
                onPickA = { change(spec.copy(colorA = it)) },
                labelB = stringResource(R.string.adjust_highlights),
                colorB = spec.colorB,
                onPickB = { change(spec.copy(colorB = it)) },
                settings = settings,
            )

            EffectKind.MOTION_BLUR, EffectKind.RGB_SPLIT, EffectKind.MIRROR,
            EffectKind.TILT_SHIFT, EffectKind.LIGHT_LEAK, EffectKind.RAIN -> {
                SliderRow(
                    label = stringResource(R.string.label_angle),
                    value = spec.angle,
                    onValueChange = { change(spec.copy(angle = it)) },
                    valueRange = 0f..360f,
                    valueText = "${spec.angle.toInt()}°",
                    defaultValue = 0f,
                )
                if (spec.kind == EffectKind.RAIN) {
                    RandomizeButton { change(spec.copy(seed = Random.nextLong())) }
                }
            }

            // Foto antigua: desgaste (cantidad), dano, tono y semilla.
            EffectKind.OLD_PHOTO -> {
                SliderRow(
                    label = stringResource(R.string.fx_damage),
                    value = spec.damage,
                    onValueChange = { change(spec.copy(damage = it)) },
                    valueText = formatPercent(spec.damage),
                    defaultValue = 0.5f,
                )
                SliderRow(
                    label = stringResource(R.string.fx_tone),
                    value = spec.tone,
                    onValueChange = { change(spec.copy(tone = it)) },
                    valueText = formatPercent(spec.tone),
                    defaultValue = 0.5f,
                )
                RandomizeButton { change(spec.copy(seed = Random.nextLong())) }
            }

            // Efectos con azar: el boton cambia la semilla y da otra distribucion.
            EffectKind.FILM_GRAIN, EffectKind.SENSOR_NOISE, EffectKind.WATERCOLOR,
            EffectKind.MOSAIC, EffectKind.SCATTER, EffectKind.SPEED_LINES,
            EffectKind.SNOW, EffectKind.SPARKLES, EffectKind.SKY_NIGHT ->
                RandomizeButton { change(spec.copy(seed = Random.nextLong())) }

            // Requisito del usuario: varios parametros y un boton de aleatorio para
            // glitch y television estropeada. Reutiliza "levels" como numero de bandas de
            // interferencia, en vez de anadir un campo nuevo solo para esto.
            EffectKind.GLITCH, EffectKind.BROKEN_TV -> {
                SliderRow(
                    label = stringResource(R.string.fx_interference),
                    value = spec.levels.toFloat(),
                    onValueChange = { change(spec.copy(levels = it.toInt().coerceIn(1, 40))) },
                    valueRange = 1f..40f,
                    steps = 38,
                    valueText = spec.levels.toString(),
                    defaultValue = 12f,
                )
                PanelActions {
                    TextButton(
                        onClick = { change(spec.copy(seed = Random.nextLong())) },
                    ) {
                        Icon(Icons.Filled.Casino, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.fx_randomize))
                    }
                }
            }

            else -> Unit
        }
        CompareControls(vm, enabled = spec.kind != EffectKind.NONE)
    }
}

/** Grupos de efectos del panel; NONE va en todos para poder quitar el efecto desde cualquiera. */
private enum class EffectGroup(@StringRes val labelRes: Int, val kinds: List<EffectKind>) {
    CLASSIC(
        R.string.fxg_classic,
        listOf(
            EffectKind.NONE, EffectKind.OLD_PHOTO, EffectKind.GLITCH, EffectKind.BROKEN_TV, EffectKind.PIXELATE,
            EffectKind.BW, EffectKind.SEPIA, EffectKind.INVERT, EffectKind.THRESHOLD,
            EffectKind.POSTERIZE, EffectKind.DUOTONE, EffectKind.RGB_SPLIT, EffectKind.HALFTONE,
            EffectKind.VINTAGE, EffectKind.GAUSSIAN_BLUR, EffectKind.MOTION_BLUR, EffectKind.RADIAL_BLUR,
            EffectKind.SOLARIZE, EffectKind.SCANLINES, EffectKind.MIRROR,
        ),
    ),
    LENS(
        R.string.fxg_lens,
        listOf(
            EffectKind.NONE, EffectKind.FILM_GRAIN, EffectKind.VIGNETTE, EffectKind.CHROMATIC,
            EffectKind.BLOOM, EffectKind.TILT_SHIFT, EffectKind.BOKEH, EffectKind.LIGHT_LEAK,
            EffectKind.DREAMY, EffectKind.LOMO, EffectKind.CROSS_PROCESS,
            EffectKind.BURNED_EDGES, EffectKind.SENSOR_NOISE, EffectKind.SPLIT_TONE,
        ),
    ),
    ART(
        R.string.fxg_art,
        listOf(
            EffectKind.NONE, EffectKind.SKETCH, EffectKind.COMIC, EffectKind.OIL,
            EffectKind.WATERCOLOR, EffectKind.MOSAIC, EffectKind.SCATTER, EffectKind.HEATMAP,
            EffectKind.DOUBLE_EXPOSURE, EffectKind.ANAGLYPH, EffectKind.EMBOSS, EffectKind.SWIRL,
        ),
    ),
    WEATHER(
        R.string.fxg_weather,
        listOf(
            EffectKind.NONE, EffectKind.RAIN, EffectKind.SNOW, EffectKind.SPARKLES,
            EffectKind.SPEED_LINES,
        ),
    ),
    SKY(
        R.string.fxg_sky,
        listOf(
            EffectKind.NONE, EffectKind.SKY_GOLDEN, EffectKind.SKY_NIGHT,
            EffectKind.SKY_DRAMATIC, EffectKind.SKY_BLUE,
        ),
    ),
}

@Composable
private fun RandomizeButton(onClick: () -> Unit) {
    PanelActions {
        TextButton(onClick = onClick) {
            Icon(Icons.Filled.Casino, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.fx_randomize))
        }
    }
}

