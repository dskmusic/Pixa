package com.dskmusic.pixa.ui.icon

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.export.ExportFormat
import com.dskmusic.pixa.core.export.ExportOptions
import com.dskmusic.pixa.core.export.Exporter
import com.dskmusic.pixa.core.icon.BackStyle
import com.dskmusic.pixa.core.icon.Blend
import com.dskmusic.pixa.core.icon.Deco
import com.dskmusic.pixa.core.icon.Fit
import com.dskmusic.pixa.core.icon.Grad
import com.dskmusic.pixa.core.icon.IconCatalog
import com.dskmusic.pixa.core.icon.IconDoc
import com.dskmusic.pixa.core.icon.IconItem
import com.dskmusic.pixa.core.icon.IconRenderer
import com.dskmusic.pixa.core.icon.ImageItem
import com.dskmusic.pixa.core.icon.Plate
import com.dskmusic.pixa.core.icon.TextItem
import com.dskmusic.pixa.core.icon.Xf
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.BusyOverlay
import com.dskmusic.pixa.ui.common.ColorPickerDialog
import com.dskmusic.pixa.ui.common.FullscreenImageDialog
import com.dskmusic.pixa.ui.common.PixaToastHost
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.formatValue
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.panels.FontPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Creador de iconos: cuatro pestanas (icono, fondo, imagen, texto) con elementos
 * multiples, deshacer/rehacer y exportacion cuadrada en PNG.
 */
@Composable
fun IconMakerScreen(
    editor: EditorViewModel,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val fonts = remember(context) { context.pixa.fonts }

    // ---- estado con historial. Las ediciones seguidas (arrastrar un slider) se funden en un paso.
    var doc by remember { mutableStateOf(IconDoc()) }
    val undo = remember { ArrayList<IconDoc>() }
    val redo = remember { ArrayList<IconDoc>() }
    var lastEdit by remember { mutableStateOf(0L) }
    var stackVersion by remember { mutableIntStateOf(0) }
    fun edit(f: (IconDoc) -> IconDoc) {
        val now = System.currentTimeMillis()
        if (now - lastEdit > 700) {
            undo.add(doc)
            if (undo.size > 100) undo.removeAt(0)
            redo.clear()
        }
        lastEdit = now
        doc = f(doc)
        stackVersion++
    }

    var tab by remember { mutableIntStateOf(0) }
    var selI by remember { mutableIntStateOf(0) }
    var selM by remember { mutableIntStateOf(0) }
    var selT by remember { mutableIntStateOf(0) }
    val ii = selI.coerceIn(0, (doc.icons.size - 1).coerceAtLeast(0))
    val mi = selM.coerceIn(0, (doc.images.size - 1).coerceAtLeast(0))
    val ti = selT.coerceIn(0, (doc.texts.size - 1).coerceAtLeast(0))
    val icon = doc.icons.getOrNull(ii)
    val image = doc.images.getOrNull(mi)
    val text = doc.texts.getOrNull(ti)
    fun updI(f: (IconItem) -> IconItem) =
        edit { d -> d.copy(icons = d.icons.mapIndexed { i, x -> if (i == ii) f(x) else x }) }
    fun updM(f: (ImageItem) -> ImageItem) =
        edit { d -> d.copy(images = d.images.mapIndexed { i, x -> if (i == mi) f(x) else x }) }
    fun updT(f: (TextItem) -> TextItem) =
        edit { d -> d.copy(texts = d.texts.mapIndexed { i, x -> if (i == ti) f(x) else x }) }
    fun updB(f: (BackStyle) -> BackStyle) = edit { d -> d.copy(back = f(d.back)) }

    // ---- render
    val cache = remember { ConcurrentHashMap<String, Bitmap>() }
    val imageOf: (String) -> Bitmap? = { key ->
        cache[key] ?: runCatching { BitmapUtils.decodeUri(context, Uri.parse(key), 1024) }
            .getOrNull()?.also { cache[key] = it }
    }
    suspend fun render(d: IconDoc, px: Int): Bitmap {
        val tf = d.texts.map { it.font }.distinct().associateWith { fonts.load(it) }
        return withContext(Dispatchers.Default) { IconRenderer.render(d, px, imageOf) { tf[it] } }
    }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(doc) {
        delay(100)
        runCatching { render(doc, 512) }.getOrNull()?.let { preview = it }
    }

    var busy by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.ic_saved)
    val errorMsg = stringResource(R.string.ic_export_error)
    val title = stringResource(R.string.ic_title)

    val pickBackImage = rememberImagePicker { uri -> updB { it.copy(imageUri = uri.toString(), imageOn = true) } }
    val pickItemImage = rememberImagePicker { uri -> updM { it.copy(uri = uri.toString()) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { PixaToastHost(snackbar) },
        topBar = {
            PixaTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ic_reset_all)) },
                                onClick = { menu = false; edit { IconDoc() } },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                // ---- cabecera: vista previa y barra de acciones
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .clickable(enabled = preview != null) { fullscreen = true },
                    ) {
                        preview?.let {
                            Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { exportDialog = true }) {
                                Icon(Icons.Filled.Download, stringResource(R.string.action_export))
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    busy = true
                                    val bm = runCatching { render(doc, 1024) }.getOrNull()
                                    busy = false
                                    if (bm != null) {
                                        val layer = PixaLayer(
                                            name = title,
                                            content = LayerContent.Raster(editor.putBitmap(bm)),
                                        )
                                        editor.install(
                                            PixaDocument(
                                                width = bm.width,
                                                height = bm.height,
                                                background = CanvasBackground(transparent = true, color = 0),
                                                layers = listOf(layer),
                                                selectedLayerId = layer.id,
                                            ),
                                            projectId = null,
                                            name = title,
                                            initialLabel = R.string.history_initial,
                                        )
                                        onOpenEditor()
                                    }
                                }
                            }) { Icon(Icons.Filled.Layers, stringResource(R.string.ic_open_editor)) }
                            val canUndo = stackVersion >= 0 && undo.isNotEmpty()
                            val canRedo = stackVersion >= 0 && redo.isNotEmpty()
                            IconButton(enabled = canUndo, onClick = {
                                redo.add(doc); doc = undo.removeAt(undo.lastIndex); lastEdit = 0; stackVersion++
                            }) { Icon(Icons.Filled.Undo, stringResource(R.string.action_undo)) }
                            IconButton(enabled = canRedo, onClick = {
                                undo.add(doc); doc = redo.removeAt(redo.lastIndex); lastEdit = 0; stackVersion++
                            }) { Icon(Icons.Filled.Redo, stringResource(R.string.action_redo)) }
                        }
                    }
                }

                TabRow(selectedTabIndex = tab) {
                    listOf(R.string.ic_tab_icon, R.string.ic_tab_back, R.string.ic_tab_image, R.string.ic_tab_text)
                        .forEachIndexed { i, r ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r)) })
                        }
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    when (tab) {
                        0 -> {
                            ItemCard(
                                title = stringResource(R.string.ic_items_icon),
                                count = doc.icons.size, sel = ii, onSel = { selI = it },
                                label = { stringResource(R.string.ic_item_icon, it + 1) },
                                onAdd = { edit { d -> d.copy(icons = d.icons + IconItem()) }; selI = doc.icons.size - 1 },
                                onDuplicate = {
                                    icon?.let { c -> edit { d -> d.copy(icons = d.icons.toMutableList().apply { add(ii + 1, c) }) }; selI = ii + 1 }
                                },
                                onDelete = { if (icon != null) edit { d -> d.copy(icons = d.icons.filterIndexed { i, _ -> i != ii }) } },
                                thumb = { i ->
                                    val x = doc.icons[i]
                                    Icon(IconCatalog.find(x.icon), null, tint = Color(x.color), modifier = Modifier.size(32.dp))
                                },
                            )
                            if (icon != null) IconTab(icon, ::updI)
                        }
                        1 -> BackTab(doc.back, ::updB, onPickImage = pickBackImage)
                        2 -> {
                            ItemCard(
                                title = stringResource(R.string.ic_items_image),
                                count = doc.images.size, sel = mi, onSel = { selM = it },
                                label = { stringResource(R.string.ic_item_image, it + 1) },
                                onAdd = { edit { d -> d.copy(images = d.images + ImageItem()) }; selM = doc.images.size - 1 },
                                onDuplicate = {
                                    image?.let { c -> edit { d -> d.copy(images = d.images.toMutableList().apply { add(mi + 1, c) }) }; selM = mi + 1 }
                                },
                                onDelete = { if (image != null) edit { d -> d.copy(images = d.images.filterIndexed { i, _ -> i != mi }) } },
                                thumb = { i ->
                                    val u = doc.images[i].uri
                                    if (u == null) Icon(Icons.Filled.Photo, null, Modifier.size(28.dp))
                                    else AsyncImage(Uri.parse(u), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                },
                            )
                            if (image != null) ImageTab(image, ::updM, onPick = pickItemImage)
                        }
                        else -> {
                            ItemCard(
                                title = stringResource(R.string.ic_items_text),
                                count = doc.texts.size, sel = ti, onSel = { selT = it },
                                label = { stringResource(R.string.ic_item_text, it + 1) },
                                onAdd = { edit { d -> d.copy(texts = d.texts + TextItem()) }; selT = doc.texts.size - 1 },
                                onDuplicate = {
                                    text?.let { c -> edit { d -> d.copy(texts = d.texts.toMutableList().apply { add(ti + 1, c) }) }; selT = ti + 1 }
                                },
                                onDelete = { if (text != null) edit { d -> d.copy(texts = d.texts.filterIndexed { i, _ -> i != ti }) } },
                                thumb = { i ->
                                    val x = doc.texts[i]
                                    Text(x.text, fontSize = 10.sp, maxLines = 3, color = Color(x.color), modifier = Modifier.padding(4.dp))
                                },
                            )
                            if (text != null) TextTab(text, ::updT)
                        }
                    }
                }
            }
            BusyOverlay(visible = busy)
        }
    }

    if (fullscreen) preview?.let { FullscreenImageDialog(it, null) { fullscreen = false } }
    if (exportDialog) {
        ExportDialog(
            onDismiss = { exportDialog = false },
            onExport = { px ->
                exportDialog = false
                scope.launch {
                    busy = true
                    val ok = runCatching {
                        val bm = render(doc, px)
                        withContext(Dispatchers.IO) {
                            Exporter.saveToGallery(context, bm, ExportOptions(ExportFormat.PNG, baseName = "icon"))
                        }
                    }.isSuccess
                    busy = false
                    snackbar.showSnackbar(if (ok) savedMsg else errorMsg)
                }
            },
        )
    }
}

// ======================================================================================
// Pestanas
// ======================================================================================

@Composable
private fun IconTab(it: IconItem, upd: ((IconItem) -> IconItem) -> Unit) {
    Section(stringResource(R.string.ic_s_icon), it.on, { v -> upd { x -> x.copy(on = v) } }) {
        IconProp(it.icon) { v -> upd { x -> x.copy(icon = v) } }
        ColorProp(stringResource(R.string.ic_color), it.color) { v -> upd { x -> x.copy(color = v) } }
        SliderProp(stringResource(R.string.ic_size), it.size, 1f..100f, Icons.Filled.AspectRatio) { v -> upd { x -> x.copy(size = v) } }
        XfProps(it.xf) { v -> upd { x -> x.copy(xf = v) } }
    }
    GradSection(stringResource(R.string.ic_gradient), it.grad) { v -> upd { x -> x.copy(grad = v) } }
    val sh = it.shadow
    Section(stringResource(R.string.ic_shadow), sh.on, { v -> upd { x -> x.copy(shadow = sh.copy(on = v)) } }) {
        ColorProp(stringResource(R.string.ic_color), sh.color) { v -> upd { x -> x.copy(shadow = sh.copy(color = v)) } }
        NumsProp(stringResource(R.string.ic_position), listOf("X", "Y"), listOf(sh.dx, sh.dy), Icons.Filled.OpenWith) { v ->
            upd { x -> x.copy(shadow = sh.copy(dx = v[0], dy = v[1])) }
        }
        SliderProp(stringResource(R.string.ic_opacity), sh.opacity, 0f..100f, Icons.Filled.Opacity) { v -> upd { x -> x.copy(shadow = sh.copy(opacity = v)) } }
        SliderProp(stringResource(R.string.ic_blur), sh.blur, 0f..20f, Icons.Filled.BlurOn) { v -> upd { x -> x.copy(shadow = sh.copy(blur = v)) } }
    }
    val ls = it.longShadow
    Section(stringResource(R.string.ic_long_shadow), ls.on, { v -> upd { x -> x.copy(longShadow = ls.copy(on = v)) } }) {
        ColorProp(stringResource(R.string.ic_color), ls.color) { v -> upd { x -> x.copy(longShadow = ls.copy(color = v)) } }
        SliderProp(stringResource(R.string.ic_length), ls.length, 0f..150f, Icons.Filled.Straighten) { v -> upd { x -> x.copy(longShadow = ls.copy(length = v)) } }
        SliderProp(stringResource(R.string.ic_angle), ls.angle, 0f..360f, Icons.Filled.RotateRight) { v -> upd { x -> x.copy(longShadow = ls.copy(angle = v)) } }
        SliderProp(stringResource(R.string.ic_opacity), ls.opacity, 0f..100f, Icons.Filled.Opacity) { v -> upd { x -> x.copy(longShadow = ls.copy(opacity = v)) } }
        SliderProp(stringResource(R.string.ic_blur), ls.blur, 0f..30f, Icons.Filled.BlurOn) { v -> upd { x -> x.copy(longShadow = ls.copy(blur = v)) } }
    }
    PlateSection(it.plate) { v -> upd { x -> x.copy(plate = v) } }
}

@Composable
private fun BackTab(b: BackStyle, upd: ((BackStyle) -> BackStyle) -> Unit, onPickImage: () -> Unit) {
    Section(stringResource(R.string.ic_back_color), b.colorOn, { v -> upd { x -> x.copy(colorOn = v) } }) {
        ColorProp(stringResource(R.string.ic_color), b.color) { v -> upd { x -> x.copy(color = v) } }
        SliderProp(stringResource(R.string.ic_opacity), b.opacity, 0f..100f, Icons.Filled.Opacity) { v -> upd { x -> x.copy(opacity = v) } }
    }
    GradSection(stringResource(R.string.ic_gradient), b.grad) { v -> upd { x -> x.copy(grad = v) } }
    Section(stringResource(R.string.ic_back_image), b.imageOn, { v -> upd { x -> x.copy(imageOn = v) } }) {
        Prop(
            stringResource(R.string.ic_image),
            stringResource(if (b.imageUri == null) R.string.ic_tap_pick else R.string.ic_image_set),
            onPickImage,
        ) { Icon(Icons.Filled.Photo, null) }
        SliderProp(stringResource(R.string.ic_rotation), b.imageRot, -180f..180f, Icons.Filled.RotateRight) { v -> upd { x -> x.copy(imageRot = v) } }
        SliderProp(stringResource(R.string.ic_opacity), b.imageOpacity, 0f..100f, Icons.Filled.Opacity) { v -> upd { x -> x.copy(imageOpacity = v) } }
        FitProp(b.imageFit) { v -> upd { x -> x.copy(imageFit = v) } }
    }
    Section(stringResource(R.string.ic_border), b.borderOn, { v -> upd { x -> x.copy(borderOn = v) } }) {
        SliderProp(stringResource(R.string.ic_radius), b.radius, 0f..50f, Icons.Filled.RoundedCorner) { v -> upd { x -> x.copy(radius = v) } }
        ColorProp(stringResource(R.string.ic_border_color), b.borderColor) { v -> upd { x -> x.copy(borderColor = v) } }
        SliderProp(stringResource(R.string.ic_border_width), b.borderWidth, 0f..20f, Icons.Filled.BorderStyle) { v -> upd { x -> x.copy(borderWidth = v) } }
    }
}

@Composable
private fun ImageTab(it: ImageItem, upd: ((ImageItem) -> ImageItem) -> Unit, onPick: () -> Unit) {
    Section(stringResource(R.string.ic_image), it.on, { v -> upd { x -> x.copy(on = v) } }) {
        Prop(
            stringResource(R.string.ic_image),
            stringResource(if (it.uri == null) R.string.ic_tap_pick else R.string.ic_image_set),
            onPick,
        ) { Icon(Icons.Filled.Photo, null) }
        NumsProp(stringResource(R.string.ic_size), listOf(stringResource(R.string.ic_width), stringResource(R.string.ic_height)), listOf(it.w, it.h), Icons.Filled.AspectRatio) { v ->
            upd { x -> x.copy(w = v[0].coerceAtLeast(1f), h = v[1].coerceAtLeast(1f)) }
        }
        FitProp(it.fit) { v -> upd { x -> x.copy(fit = v) } }
        XfProps(it.xf) { v -> upd { x -> x.copy(xf = v) } }
    }
    Section(stringResource(R.string.ic_filter), it.filterOn, { v -> upd { x -> x.copy(filterOn = v) } }) {
        ColorProp(stringResource(R.string.ic_color), it.filterColor) { v -> upd { x -> x.copy(filterColor = v) } }
        val names = listOf(
            R.string.ic_bl_multiply, R.string.ic_bl_screen, R.string.ic_bl_overlay, R.string.ic_bl_darken,
            R.string.ic_bl_lighten, R.string.ic_bl_add, R.string.ic_bl_src_in, R.string.ic_bl_src_atop,
        ).map { r -> stringResource(r) }
        ChoiceProp(stringResource(R.string.ic_blend), names, it.blend.ordinal, Icons.Filled.Compare) { v ->
            upd { x -> x.copy(blend = Blend.entries[v]) }
        }
    }
    Section(stringResource(R.string.ic_border), it.borderOn, { v -> upd { x -> x.copy(borderOn = v) } }) {
        SliderProp(stringResource(R.string.ic_radius), it.radius, 0f..50f, Icons.Filled.RoundedCorner) { v -> upd { x -> x.copy(radius = v) } }
        ColorProp(stringResource(R.string.ic_border_color), it.borderColor) { v -> upd { x -> x.copy(borderColor = v) } }
        SliderProp(stringResource(R.string.ic_border_width), it.borderWidth, 0f..20f, Icons.Filled.BorderStyle) { v -> upd { x -> x.copy(borderWidth = v) } }
    }
}

@Composable
private fun TextTab(it: TextItem, upd: ((TextItem) -> TextItem) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Section(stringResource(R.string.ic_text), it.on, { v -> upd { x -> x.copy(on = v) } }) {
        Prop(stringResource(R.string.ic_text), it.text.replace("\n", " "), { editing = true }) { Icon(Icons.Filled.TextFields, null) }
        ColorProp(stringResource(R.string.ic_color), it.color) { v -> upd { x -> x.copy(color = v) } }
        SliderProp(stringResource(R.string.ic_size), it.size, 1f..100f, Icons.Filled.FormatSize) { v -> upd { x -> x.copy(size = v) } }
        Prop(stringResource(R.string.ic_font), it.font.family, { picking = true }) { Icon(Icons.Filled.FontDownload, null) }
        ChoiceProp(stringResource(R.string.ic_weight), (1..9).map { n -> "W${n * 100}" }, (it.weight / 100 - 1).coerceIn(0, 8), Icons.Filled.FormatBold) { v ->
            upd { x -> x.copy(weight = (v + 1) * 100) }
        }
        ChoiceProp(
            stringResource(R.string.ic_style),
            listOf(stringResource(R.string.ic_style_normal), stringResource(R.string.ic_style_italic)),
            if (it.italic) 1 else 0, Icons.Filled.FormatItalic,
        ) { v -> upd { x -> x.copy(italic = v == 1) } }
        SliderProp(stringResource(R.string.ic_letter_spacing), it.letter, 0f..5f, Icons.Filled.FormatLineSpacing) { v -> upd { x -> x.copy(letter = v) } }
        SliderProp(stringResource(R.string.ic_word_spacing), it.word, 0f..5f, Icons.Filled.SpaceBar) { v -> upd { x -> x.copy(word = v) } }
        XfProps(it.xf) { v -> upd { x -> x.copy(xf = v) } }
        ChoiceProp(
            stringResource(R.string.ic_line_align),
            listOf(stringResource(R.string.ic_left), stringResource(R.string.ic_center), stringResource(R.string.ic_right)),
            it.lineAlign, Icons.Filled.VerticalAlignCenter,
        ) { v -> upd { x -> x.copy(lineAlign = v) } }
        ChoiceProp(
            stringResource(R.string.ic_decoration),
            listOf(R.string.ic_deco_none, R.string.ic_deco_under, R.string.ic_deco_strike, R.string.ic_deco_over).map { r -> stringResource(r) },
            it.deco.ordinal, Icons.Filled.FormatUnderlined,
        ) { v -> upd { x -> x.copy(deco = Deco.entries[v]) } }
        SliderProp(stringResource(R.string.ic_thickness), it.decoThick, 0.2f..5f, Icons.Filled.LineWeight) { v -> upd { x -> x.copy(decoThick = v) } }
    }
    GradSection(stringResource(R.string.ic_gradient), it.grad) { v -> upd { x -> x.copy(grad = v) } }
    PlateSection(it.plate) { v -> upd { x -> x.copy(plate = v) } }

    if (editing) {
        var v by remember { mutableStateOf(it.text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.ic_text)) },
            text = { OutlinedTextField(v, { v = it }, minLines = 2, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { upd { x -> x.copy(text = v) }; editing = false }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (picking) {
        FontPickerDialog(
            current = it.font,
            onDismiss = { picking = false },
            onPick = { f -> upd { x -> x.copy(font = f) }; picking = false },
        )
    }
}

// ======================================================================================
// Grupos de propiedades compartidos
// ======================================================================================

@Composable
private fun alignNames() = listOf(
    R.string.ic_al_tl, R.string.ic_al_tc, R.string.ic_al_tr,
    R.string.ic_al_cl, R.string.ic_al_c, R.string.ic_al_cr,
    R.string.ic_al_bl, R.string.ic_al_bc, R.string.ic_al_br,
).map { stringResource(it) }

@Composable
private fun ColumnScope.XfProps(xf: Xf, onChange: (Xf) -> Unit) {
    SliderProp(stringResource(R.string.ic_rotation), xf.rot, -180f..180f, Icons.Filled.RotateRight) { v -> onChange(xf.copy(rot = v)) }
    ToggleProp(stringResource(R.string.ic_flip_h), xf.flipH, Icons.Filled.Flip) { v -> onChange(xf.copy(flipH = v)) }
    ToggleProp(stringResource(R.string.ic_flip_v), xf.flipV, Icons.Filled.SwapVert) { v -> onChange(xf.copy(flipV = v)) }
    ChoiceProp(stringResource(R.string.ic_alignment), alignNames(), xf.align, Icons.Filled.VerticalAlignCenter) { v -> onChange(xf.copy(align = v)) }
    NumsProp(stringResource(R.string.ic_position), listOf("X", "Y"), listOf(xf.px, xf.py), Icons.Filled.OpenWith) { v -> onChange(xf.copy(px = v[0], py = v[1])) }
    SliderProp(stringResource(R.string.ic_opacity), xf.opacity, 0f..100f, Icons.Filled.Opacity) { v -> onChange(xf.copy(opacity = v)) }
}

@Composable
private fun GradSection(title: String, g: Grad, onChange: (Grad) -> Unit) {
    Section(title, g.on, { v -> onChange(g.copy(on = v)) }) {
        ColorProp(stringResource(R.string.ic_first_color), g.c1) { v -> onChange(g.copy(c1 = v)) }
        ColorProp(stringResource(R.string.ic_second_color), g.c2) { v -> onChange(g.copy(c2 = v)) }
        SliderProp(stringResource(R.string.ic_first_opacity), g.o1, 0f..100f, Icons.Filled.Opacity) { v -> onChange(g.copy(o1 = v)) }
        SliderProp(stringResource(R.string.ic_second_opacity), g.o2, 0f..100f, Icons.Filled.Opacity) { v -> onChange(g.copy(o2 = v)) }
        ChoiceProp(stringResource(R.string.ic_first_align), alignNames(), g.a1, Icons.Filled.VerticalAlignCenter) { v -> onChange(g.copy(a1 = v)) }
        ChoiceProp(stringResource(R.string.ic_second_align), alignNames(), g.a2, Icons.Filled.VerticalAlignCenter) { v -> onChange(g.copy(a2 = v)) }
        SliderProp(stringResource(R.string.ic_color_stop), g.stop, 0f..1f, Icons.Filled.LinearScale) { v -> onChange(g.copy(stop = v)) }
    }
}

@Composable
private fun PlateSection(p: Plate, onChange: (Plate) -> Unit) {
    val wrap = stringResource(R.string.ic_wrap)
    Section(stringResource(R.string.ic_background), p.on, { v -> onChange(p.copy(on = v)) }) {
        ColorProp(stringResource(R.string.ic_color), p.color) { v -> onChange(p.copy(color = v)) }
        SliderProp(stringResource(R.string.ic_width), p.w, 0f..100f, Icons.Filled.SwapHoriz, { v -> if (v < 0.5f) wrap else formatValue(v) }) { v -> onChange(p.copy(w = v)) }
        SliderProp(stringResource(R.string.ic_height), p.h, 0f..100f, Icons.Filled.Height, { v -> if (v < 0.5f) wrap else formatValue(v) }) { v -> onChange(p.copy(h = v)) }
        NumsProp(
            stringResource(R.string.ic_padding),
            listOf(R.string.ic_left, R.string.ic_top, R.string.ic_right, R.string.ic_bottom).map { stringResource(it) },
            p.pad, Icons.Filled.Tune,
        ) { v -> onChange(p.copy(pad = v)) }
        SliderProp(stringResource(R.string.ic_opacity), p.opacity, 0f..100f, Icons.Filled.Opacity) { v -> onChange(p.copy(opacity = v)) }
        SliderProp(stringResource(R.string.ic_elevation), p.elevation, 0f..20f, Icons.Filled.Layers) { v -> onChange(p.copy(elevation = v)) }
        SliderProp(stringResource(R.string.ic_radius), p.radius, 0f..50f, Icons.Filled.RoundedCorner) { v -> onChange(p.copy(radius = v)) }
        ColorProp(stringResource(R.string.ic_border_color), p.borderColor) { v -> onChange(p.copy(borderColor = v)) }
        SliderProp(stringResource(R.string.ic_border_width), p.borderWidth, 0f..20f, Icons.Filled.BorderColor) { v -> onChange(p.copy(borderWidth = v)) }
    }
}

@Composable
private fun FitProp(fit: Fit, onChange: (Fit) -> Unit) {
    val names = listOf(
        R.string.ic_fit_cover, R.string.ic_fit_contain, R.string.ic_fit_fill, R.string.ic_fit_width, R.string.ic_fit_height,
    ).map { stringResource(it) }
    ChoiceProp(stringResource(R.string.ic_fit), names, fit.ordinal, Icons.Filled.FilterAlt) { v -> onChange(Fit.entries[v]) }
}

// ======================================================================================
// Filas y dialogos
// ======================================================================================

@Composable
private fun Section(title: String, on: Boolean?, onToggle: (Boolean) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = on != null) { onToggle(!(on ?: false)) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (on != null) Switch(checked = on, onCheckedChange = onToggle)
            }
            if (on != false) content()
        }
    }
}

@Composable
private fun Prop(title: String, value: String?, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
private fun SliderProp(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    text: (Float) -> String = { formatValue(it) },
    onChange: (Float) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Prop(title, text(value), { open = true }) { Icon(icon, null) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                var v by remember { mutableFloatStateOf(value) }
                SliderRow(label = "", value = v, onValueChange = { v = it; onChange(it) }, valueRange = range, valueText = text(v))
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_done)) } },
        )
    }
}

@Composable
private fun ColorProp(title: String, color: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Prop(title, "#%06X".format(color and 0xFFFFFF), { open = true }) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
    }
    if (open) {
        ColorPickerDialog(
            initial = color,
            withAlpha = false,
            onDismiss = { open = false },
            onConfirm = { onChange(it); open = false },
        )
    }
}

@Composable
private fun ChoiceProp(title: String, options: List<String>, index: Int, icon: ImageVector, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Prop(title, options.getOrNull(index), { open = true }) { Icon(icon, null) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEachIndexed { i, name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(i); open = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = i == index, onClick = { onPick(i); open = false })
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ToggleProp(title: String, on: Boolean, icon: ImageVector, onChange: (Boolean) -> Unit) {
    Prop(
        title,
        stringResource(if (on) R.string.ic_on else R.string.ic_off),
        { onChange(!on) },
    ) { Icon(icon, null) }
}

@Composable
private fun NumsProp(title: String, labels: List<String>, values: List<Float>, icon: ImageVector, onChange: (List<Float>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Prop(title, values.joinToString(", ") { formatValue(it) }, { open = true }) { Icon(icon, null) }
    if (open) {
        val drafts = remember { values.map { formatValue(it) }.toMutableList().let { l -> mutableStateOf(l.toList()) } }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEachIndexed { i, label ->
                        OutlinedTextField(
                            value = drafts.value[i],
                            onValueChange = { s -> drafts.value = drafts.value.toMutableList().also { it[i] = s } },
                            label = { Text(label) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(drafts.value.mapIndexed { i, s -> s.replace(',', '.').toFloatOrNull() ?: values[i] })
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun IconProp(name: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Prop(stringResource(R.string.ic_s_icon), name, { open = true }) { Icon(IconCatalog.find(name), null) }
    if (open) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.ic_pick_icon)) },
            text = {
                Column {
                    OutlinedTextField(
                        query, { query = it }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        label = { Text(stringResource(R.string.ic_search)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val list = remember(query) {
                        IconCatalog.all.filter { it.first.contains(query.trim(), ignoreCase = true) }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(48.dp),
                        modifier = Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp),
                    ) {
                        items(list) { (n, v) ->
                            IconButton(onClick = { onPick(n); open = false }) {
                                Icon(
                                    v, n,
                                    tint = if (n == name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ItemCard(
    title: String,
    count: Int,
    sel: Int,
    onSel: (Int) -> Unit,
    label: @Composable (Int) -> String,
    onAdd: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    thumb: @Composable (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed((0 until count).toList()) { _, i ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    BorderStroke(if (i == sel) 2.dp else 1.dp, if (i == sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                    RoundedCornerShape(10.dp),
                                )
                                .background(Color(0xFF444444))
                                .clickable { onSel(i) },
                            contentAlignment = Alignment.Center,
                        ) { thumb(i) }
                        Text(label(i), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (Int) -> Unit) {
    var choice by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf("256") }
    val px = when (choice) { 0 -> 512; 1 -> 1024; else -> (custom.toIntOrNull() ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ic_export_size)) },
        text = {
            Column {
                listOf("512 x 512", "1024 x 1024", stringResource(R.string.ic_size_custom)).forEachIndexed { i, name ->
                    Row(Modifier.fillMaxWidth().clickable { choice = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choice == i, onClick = { choice = i })
                        Text(name)
                    }
                }
                if (choice == 2) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.ic_size_side)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = px in 16..4096, onClick = { onExport(px) }) { Text(stringResource(R.string.action_export)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
