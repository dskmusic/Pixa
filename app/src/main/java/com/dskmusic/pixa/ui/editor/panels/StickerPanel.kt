package com.dskmusic.pixa.ui.editor.panels

import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.stock.KlipyApi
import com.dskmusic.pixa.core.stock.KlipySticker
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.LayerGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Buscador de stickers de Klipy. Solo stickers (sin GIF ni video) y se insertan como
 * imagen estatica con transparencia: una capa normal que se mueve, escala, recorta, lleva
 * filtros, etc. Sin busqueda ensena los del momento.
 */
@Composable
fun StickerDialog(
    vm: EditorViewModel,
    doc: PixaDocument?,
    settings: PixaSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rasterizer = remember(context) { context.pixa.rasterizer }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<KlipySticker>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var inserting by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    suspend fun loadPage(reset: Boolean) {
        if (loading || (endReached && !reset)) return
        loading = true
        error = false
        val target = if (reset) 1 else page
        val got = runCatching { KlipyApi.stickers(query, target) }.getOrNull()
        loading = false
        if (got == null) {
            error = true
            return
        }
        results = if (reset) got.items else results + got.items
        endReached = !got.hasNext || got.items.isEmpty()
        page = target + 1
    }

    LaunchedEffect(query) {
        delay(350)
        page = 1
        endReached = false
        loadPage(reset = true)
    }

    LaunchedEffect(gridState, results) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= results.size - 6) loadPage(reset = false)
            }
    }

    fun insert(sticker: KlipySticker) {
        if (inserting || doc == null) return
        inserting = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL(sticker.fullUrl).openConnection() as HttpURLConnection
                    c.connectTimeout = 10_000
                    c.readTimeout = 20_000
                    try {
                        c.inputStream.use { BitmapFactory.decodeStream(it) }
                    } finally {
                        c.disconnect()
                    }
                }.getOrNull()
            }
            inserting = false
            if (bitmap == null) {
                vm.post(R.string.error_load_image)
                return@launch
            }
            // Un sticker es pequeno: se ajusta a un tercio del lado corto del lienzo.
            val target = minOf(doc.width, doc.height) / 3f
            val k = target / maxOf(bitmap.width, bitmap.height)
            val clamped = BitmapUtils.clampToMax(bitmap, settings.maxResolution)
            val layer = PixaLayer(
                name = context.getString(R.string.stickers_title),
                content = LayerContent.Raster(vm.putBitmap(clamped)),
            )
            val centered = LayerGeometry.centered(layer, rasterizer, doc)
            vm.addLayer(
                R.string.stickers_title,
                layer.copy(transform = centered.copy(scaleX = k, scaleY = k)),
            )
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                    }
                    Text(
                        stringResource(R.string.stickers_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextFieldRow(
                    label = stringResource(R.string.stickers_search_hint),
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        !KlipyApi.hasKey -> Text(
                            stringResource(R.string.stickers_no_key),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                        error && results.isEmpty() -> Text(
                            stringResource(R.string.stock_images_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                        results.isEmpty() && loading -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                        results.isEmpty() -> Text(
                            stringResource(R.string.emoji_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(96.dp),
                            state = gridState,
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(results, key = { it.id }) { sticker ->
                                AsyncImage(
                                    model = sticker.previewUrl,
                                    contentDescription = sticker.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable { insert(sticker) },
                                )
                            }
                            if (loading && results.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.align(Alignment.Center).height(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (inserting) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}
