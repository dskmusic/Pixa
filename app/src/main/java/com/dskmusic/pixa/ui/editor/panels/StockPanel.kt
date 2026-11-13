package com.dskmusic.pixa.ui.editor.panels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Row
import coil.compose.AsyncImage
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.stock.PixabayApi
import com.dskmusic.pixa.core.stock.PixabayHit
import com.dskmusic.pixa.core.stock.PixabayType
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
 * Buscador de imagenes de stock por Pixabay (requisito del usuario): fotos, ilustraciones
 * y vectores, con filtro por tipo y scroll infinito; sin busqueda ensena lo mas popular de
 * Pixabay a modo de destacadas.
 */
@Composable
fun StockImageDialog(
    vm: EditorViewModel,
    doc: PixaDocument?,
    settings: PixaSettings,
    onDismiss: () -> Unit,
    // Requisito del usuario: acceso directo desde Home ("Imagenes de stock"), sin
    // documento abierto todavia. Si llega, la foto elegida abre un documento nuevo en vez
    // de insertarse como capa en uno que no existe.
    onPickForNewDocument: ((Bitmap) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rasterizer = remember(context) { context.pixa.rasterizer }

    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PixabayType.PHOTO) }
    var results by remember { mutableStateOf<List<PixabayHit>>(emptyList()) }
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
        val targetPage = if (reset) 1 else page
        val hits = runCatching { PixabayApi.search(query, type, targetPage) }.getOrNull()
        loading = false
        if (hits == null) {
            error = true
            return
        }
        results = if (reset) hits else results + hits
        endReached = hits.isEmpty()
        page = targetPage + 1
    }

    // Busqueda y filtro con un pequeno debounce, para no lanzar una peticion por tecla.
    LaunchedEffect(query, type) {
        delay(350)
        page = 1
        endReached = false
        loadPage(reset = true)
    }

    // Scroll infinito: al acercarse al final de lo ya cargado, se pide la pagina siguiente.
    LaunchedEffect(gridState, results) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= results.size - 6) {
                    loadPage(reset = false)
                }
            }
    }

    fun insert(hit: PixabayHit) {
        if (inserting) return
        inserting = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(hit.largeImageURL.ifBlank { hit.webformatURL })
                        .openConnection() as HttpURLConnection
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 20_000
                    try {
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
            }
            inserting = false
            if (bitmap == null) {
                vm.post(R.string.error_load_image)
                return@launch
            }
            val clamped = BitmapUtils.clampToMax(bitmap, settings.maxResolution)
            if (onPickForNewDocument != null) {
                onPickForNewDocument(clamped)
            } else if (doc != null) {
                val layer = PixaLayer(
                    name = context.getString(R.string.stock_images_title),
                    content = LayerContent.Raster(vm.putBitmap(clamped)),
                )
                vm.addLayer(
                    R.string.stock_images_title,
                    layer.copy(transform = LayerGeometry.centered(layer, rasterizer, doc)),
                )
            }
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
                        stringResource(R.string.stock_images_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextFieldRow(
                    label = stringResource(R.string.stock_images_search_hint),
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                // Requisito del usuario: sin "Todos" (con 4 pestanas "Ilustraciones" se
                // cortaba); con 3 cabe de sobra y "Fotografias" es la primera por defecto.
                val types = PixabayType.entries.filter { it != PixabayType.ALL }
                TabRow(selectedTabIndex = types.indexOf(type)) {
                    types.forEach { t ->
                        Tab(
                            selected = t == type,
                            onClick = { type = t },
                            text = { Text(stringResource(stockTypeLabel(t))) },
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        !PixabayApi.hasKey -> Text(
                            stringResource(R.string.stock_images_no_key),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                        error && results.isEmpty() -> Text(
                            stringResource(R.string.stock_images_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                        results.isEmpty() && loading -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                        results.isEmpty() -> Text(
                            stringResource(R.string.emoji_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(110.dp),
                            state = gridState,
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(results, key = { it.id }) { hit ->
                                AsyncImage(
                                    model = hit.previewURL.ifBlank { hit.webformatURL },
                                    contentDescription = hit.tags,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable { insert(hit) },
                                )
                            }
                            if (loading && results.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .height(24.dp),
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

private fun stockTypeLabel(type: PixabayType) = when (type) {
    PixabayType.ALL -> R.string.stock_images_type_all
    PixabayType.PHOTO -> R.string.stock_images_type_photo
    PixabayType.ILLUSTRATION -> R.string.stock_images_type_illustration
    PixabayType.VECTOR -> R.string.stock_images_type_vector
}
