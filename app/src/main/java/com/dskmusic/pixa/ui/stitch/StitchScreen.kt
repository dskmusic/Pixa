package com.dskmusic.pixa.ui.stitch

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.dskmusic.pixa.ui.common.PixaToastHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.EditorMessages
import com.dskmusic.pixa.ui.common.GeneratorActions
import com.dskmusic.pixa.ui.common.GeneratorPreview
import com.dskmusic.pixa.ui.common.IntSliderRow
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.common.rememberLeaveGuard
import com.dskmusic.pixa.core.project.DraftStore
import com.dskmusic.pixa.ui.common.DraftSnapshot
import com.dskmusic.pixa.ui.common.rememberDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.rememberMultiImagePicker
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.panels.ColorRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Foto de la tira: el bitmap para la miniatura y su id para la capa. */
private class StitchPhoto(val bitmap: Bitmap, val bitmapId: String) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

private val DRAFT_JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/** Estado de "unir fotos" para recuperarlo tras un cierre inesperado. */
@Serializable
private class StitchDraft(
    val photoIds: List<String>,
    val horizontal: Boolean,
    val match: Boolean,
    val gap: Int,
    val background: Int,
)

/** Tope del lado mayor del resultado, igual que el del lienzo del editor. */
private const val MAX_SIDE = 8192f

/** Maximo de fotos por tira: mas que esto no se distingue en pantalla. */
private const val MAX_PHOTOS = 20

/**
 * Unir fotos en una tira (requisito 23).
 *
 * Las fotos no se reescalan en memoria: se colocan con la escala del propio
 * transform de cada capa, asi que ajustar al alto comun o cambiar la separacion no
 * vuelve a decodificar nada y el documento que sale al editor sigue teniendo las fotos
 * a resolucion completa, cada una en su capa.
 */
@Composable
fun StitchScreen(
    editor: EditorViewModel,
    initialPhotos: List<Uri>,
    onPhotosConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val vm = editor
    val context = LocalContext.current
    val renderer = remember(context) { context.pixa.renderer }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by context.pixa.settings.settings.collectAsStateWithLifecycle(PixaSettings())

    val photos = remember { mutableStateListOf<StitchPhoto>() }
    var horizontal by remember { mutableStateOf(true) }
    var match by remember { mutableStateOf(true) }
    var gap by remember { mutableStateOf(0) }
    var background by remember { mutableStateOf(0xFFFFFFFF.toInt()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    val addUris: (List<Uri>) -> Unit = { uris ->
        scope.launch {
            busy = true
            val free = MAX_PHOTOS - photos.size
            val loaded = withContext(Dispatchers.IO) {
                uris.take(free.coerceAtLeast(0)).mapNotNull { uri ->
                    runCatching { BitmapUtils.decodeUri(context, uri, 4096) }.getOrNull()
                }
            }
            loaded.forEach { bitmap -> photos.add(StitchPhoto(bitmap, vm.putBitmap(bitmap))) }
            busy = false
        }
    }

    // Las fotos que llegan desde el inicio se consumen una sola vez: si no, volver a esta
    // pantalla las volveria a anadir.
    LaunchedEffect(initialPhotos) {
        if (initialPhotos.isNotEmpty()) {
            addUris(initialPhotos)
            onPhotosConsumed()
        }
    }

    val pickPhotos = rememberMultiImagePicker(max = MAX_PHOTOS, onPicked = addUris)

    val layerName = stringResource(R.string.layer_image)

    val build: () -> PixaDocument? = {
        buildStitch(
            photos = photos.toList(),
            horizontal = horizontal,
            match = match,
            gapPx = gap,
            background = background,
            layerName = layerName,
        )
    }

    LaunchedEffect(renderer) {
        snapshotFlow {
            listOf(photos.map { it.bitmapId }, horizontal, match, gap, background)
        }
            .collectLatest {
                // Mismo antirrebote que en el resto de generadores: collectLatest cancela
                // el render anterior, asi que arrastrar el deslizador no encola renders.
                delay(160)
                val doc = build()
                preview = if (doc == null) {
                    null
                } else {
                    withContext(Dispatchers.Default) {
                        runCatching { renderer.renderPreview(doc, budgetPx = 1_200_000) }.getOrNull()
                    }
                }
            }
    }

    EditorMessages(vm, snackbar)
    val draft = rememberDraft(
        kind = DraftStore.STITCH,
        vm = vm,
        snapshot = {
            if (photos.isEmpty()) {
                null
            } else {
                val ids = photos.map { it.bitmapId }
                DraftSnapshot(
                    DRAFT_JSON.encodeToString(StitchDraft(ids, horizontal, match, gap, background)),
                    ids,
                )
            }
        },
        restore = { json, loadImage ->
            val d = DRAFT_JSON.decodeFromString<StitchDraft>(json)
            val loaded = d.photoIds.filter { loadImage(it) }
            photos.clear()
            loaded.forEach { id -> vm.getBitmap(id)?.let { photos.add(StitchPhoto(it, id)) } }
            horizontal = d.horizontal; match = d.match; gap = d.gap; background = d.background
        },
    )
    val guardedBack = rememberLeaveGuard(
        dirty = photos.isNotEmpty(),
        onLeave = { draft.discard(); onBack() },
        vm = vm,
        defaultName = stringResource(R.string.stitch_title),
        build = build,
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(
                title = stringResource(R.string.stitch_title),
                onBack = guardedBack,
                actions = {
                    TextButton(onClick = pickPhotos, enabled = photos.size < MAX_PHOTOS) {
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
                .padding(padding),
        ) {
            GeneratorPreview(
                bitmap = preview,
                emptyText = stringResource(R.string.stitch_add_photos),
                contentDescription = stringResource(R.string.label_preview),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                PhotoStrip(
                    photos = photos,
                    onRemove = { index -> photos.removeAt(index) },
                    onMoveBy = { id, steps ->
                        // El arrastre identifica la foto por id, no por posicion: mientras
                        // se arrastra la posicion cambia y el indice quedaria obsoleto.
                        val from = photos.indexOfFirst { it.bitmapId == id }
                        val target = (from + steps).coerceIn(0, photos.lastIndex)
                        if (from >= 0 && target != from) photos.add(target, photos.removeAt(from))
                    },
                )
                SectionTitle(stringResource(R.string.stitch_direction))
                OptionRow(
                    options = listOf(true, false),
                    selected = horizontal,
                    onSelect = { horizontal = it },
                    label = { value ->
                        context.getString(
                            if (value) R.string.label_horizontal else R.string.label_vertical,
                        )
                    },
                )
                ToggleRow(
                    label = stringResource(
                        if (horizontal) R.string.stitch_match_height else R.string.stitch_match_width,
                    ),
                    checked = match,
                    onCheckedChange = { match = it },
                )
                IntSliderRow(
                    label = stringResource(R.string.stitch_gap),
                    value = gap,
                    onValueChange = { gap = it },
                    range = 0..200,
                    suffix = " px",
                    defaultValue = 0,
                )
                ColorRow(
                    label = stringResource(R.string.label_background),
                    color = background,
                    settings = settings,
                    onPick = { background = it },
                    withAlpha = false,
                )
                HorizontalDivider()
                GeneratorActions(
                    vm = vm,
                    defaultName = stringResource(R.string.stitch_title),
                    onOpenEditor = { draft.discard(); onOpenEditor() },
                    enabled = photos.isNotEmpty() && !busy,
                    build = build,
                )
            }
        }
    }
}

/** Tira de miniaturas: arrastrar el asa reordena, el aspa quita la foto. */
@Composable
private fun PhotoStrip(
    photos: List<StitchPhoto>,
    onRemove: (Int) -> Unit,
    onMoveBy: (String, Int) -> Unit,
) {
    if (photos.isEmpty()) return
    val itemWidth = 88.dp
    val stepPx = with(LocalDensity.current) { itemWidth.toPx() }

    Text(
        text = stringResource(R.string.stitch_reorder_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEachIndexed { index, photo ->
            Column(
                modifier = Modifier.width(itemWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = photo.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(itemWidth),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mismo gesto que la lista de capas: se acumula el arrastre y cada vez
                    // que suma el ancho de una miniatura se mueve una posicion.
                    var accumulated by remember(photo.bitmapId) { mutableStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pointerInput(photo.bitmapId) {
                                detectDragGestures(
                                    onDragEnd = { accumulated = 0f },
                                    onDragCancel = { accumulated = 0f },
                                ) { _, delta ->
                                    accumulated += delta.x
                                    val steps = (accumulated / stepPx).toInt()
                                    if (steps != 0) {
                                        accumulated -= steps * stepPx
                                        onMoveBy(photo.bitmapId, steps)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = stringResource(R.string.cd_drag_handle),
                        )
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_remove),
                        )
                    }
                }
            }
        }
    }
}

// ======================================================================================
// Montaje del documento
// ======================================================================================

/**
 * Coloca las fotos una detras de otra en el eje elegido.
 *
 * Con [match] todas se llevan a la medida comun del eje corto (el alto si la tira es
 * horizontal), tomando la menor de las fotos para no ampliar ninguna. Sin [match] cada
 * foto conserva su tamano y se centra en el eje corto.
 *
 * El escalado se hace con el transform de la capa, no tocando bitmaps, de modo que la
 * posicion hay que corregirla: al escalar respecto al centro del contenido, la esquina
 * superior izquierda se desplaza (w/2)*(1-s).
 */
private fun buildStitch(
    photos: List<StitchPhoto>,
    horizontal: Boolean,
    match: Boolean,
    gapPx: Int,
    background: Int,
    layerName: String,
): PixaDocument? {
    if (photos.isEmpty()) return null

    val common = if (!match) {
        0f
    } else if (horizontal) {
        photos.minOf { it.height }.toFloat()
    } else {
        photos.minOf { it.width }.toFloat()
    }

    val scales = photos.map { photo ->
        when {
            !match -> 1f
            horizontal -> common / photo.height
            else -> common / photo.width
        }
    }

    val widths = photos.mapIndexed { i, photo -> photo.width * scales[i] }
    val heights = photos.mapIndexed { i, photo -> photo.height * scales[i] }
    val gaps = gapPx.toFloat() * (photos.size - 1)

    val rawWidth = if (horizontal) widths.sum() + gaps else widths.max()
    val rawHeight = if (horizontal) heights.max() else heights.sum() + gaps

    // Una tira de muchas fotos grandes se pasa de cualquier limite razonable, asi que se
    // reduce entera manteniendo la proporcion en vez de recortar la ultima foto.
    val fit = min(1f, MAX_SIDE / max(rawWidth, rawHeight))
    val docWidth = (rawWidth * fit).roundToInt().coerceAtLeast(1)
    val docHeight = (rawHeight * fit).roundToInt().coerceAtLeast(1)

    var cursor = 0f
    val layers = photos.mapIndexed { i, photo ->
        val scale = scales[i] * fit
        val width = widths[i] * fit
        val height = heights[i] * fit
        val x = if (horizontal) cursor else (docWidth - width) / 2f
        val y = if (horizontal) (docHeight - height) / 2f else cursor
        cursor += (if (horizontal) width else height) + gapPx * fit

        PixaLayer(
            name = "$layerName ${i + 1}",
            content = LayerContent.Raster(photo.bitmapId),
            transform = Xf(
                tx = x - photo.width / 2f * (1f - scale),
                ty = y - photo.height / 2f * (1f - scale),
                scaleX = scale,
                scaleY = scale,
            ),
        )
    }

    return PixaDocument(
        width = docWidth,
        height = docHeight,
        background = CanvasBackground(color = background),
        layers = layers,
        selectedLayerId = layers.last().id,
    )
}
