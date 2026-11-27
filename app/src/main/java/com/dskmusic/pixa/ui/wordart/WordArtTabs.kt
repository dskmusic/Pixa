package com.dskmusic.pixa.ui.wordart

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.wordart.BackgroundSpec
import com.dskmusic.pixa.core.wordart.BgKind
import com.dskmusic.pixa.core.wordart.BorderStyle
import com.dskmusic.pixa.core.wordart.BuiltinPresets
import com.dskmusic.pixa.core.wordart.ColorMode
import com.dskmusic.pixa.core.wordart.ImgFit
import com.dskmusic.pixa.core.wordart.PatternKind
import com.dskmusic.pixa.core.wordart.RotMode
import com.dskmusic.pixa.core.wordart.ShapeFx
import com.dskmusic.pixa.core.wordart.ShapeItem
import com.dskmusic.pixa.core.wordart.ShapeLibrary
import com.dskmusic.pixa.core.wordart.WordArtConfig
import com.dskmusic.pixa.core.wordart.WordLists
import com.dskmusic.pixa.core.wordart.WordOrder
import com.dskmusic.pixa.core.wordart.Zone
import com.dskmusic.pixa.core.wordart.parseWords
import com.dskmusic.pixa.ui.common.ColorPickerDialog
import com.dskmusic.pixa.ui.common.ColorSwatch
import com.dskmusic.pixa.ui.common.ConfirmDialog
import com.dskmusic.pixa.ui.common.EmptyState
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.TextPromptDialog
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.formatValue
import com.dskmusic.pixa.ui.common.rememberDocumentPicker
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.editor.panels.FontPickerDialog
import java.io.File

// ======================================================================================
// Piezas comunes
// ======================================================================================

@Composable
internal fun Head(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** Slider que edita la configuracion en vivo y abre un paso de historial al soltarlo. */
@Composable
internal fun Sl(
    vm: WordArtViewModel,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    def: Float? = null,
    unit: String = "",
    set: (WordArtConfig, Float) -> WordArtConfig,
) = SliderRow(
    label = label,
    value = value,
    onValueChange = { v -> vm.edit(false) { set(it, v) } },
    valueRange = range,
    valueText = formatValue(value) + unit,
    defaultValue = def,
    onChangeFinished = vm::commit,
)

@Composable
internal fun Sw(vm: WordArtViewModel, label: String, checked: Boolean, subtitle: String? = null, set: (WordArtConfig, Boolean) -> WordArtConfig) =
    ToggleRow(label, checked, { v -> vm.edit { set(it, v) } }, subtitle = subtitle)

@Composable
internal fun ColorRow(label: String, color: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        ColorSwatch(Color(color), size = 30, onClick = { open = true })
    }
    if (open) ColorPickerDialog(initial = color, onDismiss = { open = false }, onConfirm = { onChange(it); open = false })
}

@Composable
internal fun ColorList(colors: List<Int>, min: Int, max: Int, onChange: (List<Int>) -> Unit) {
    var editing by remember { mutableStateOf<Int?>(null) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { i, c -> ColorSwatch(Color(c), size = 34, onClick = { editing = i }) }
        if (colors.size < max) IconButton(onClick = { onChange(colors + (colors.lastOrNull() ?: 0xFFFFFFFF.toInt())) }) {
            Icon(Icons.Filled.Add, stringResource(R.string.action_add))
        }
        if (colors.size > min) IconButton(onClick = { onChange(colors.dropLast(1)) }) {
            Icon(Icons.Filled.Remove, stringResource(R.string.action_delete))
        }
    }
    editing?.let { i ->
        val cur = colors.getOrNull(i)
        if (cur == null) editing = null else ColorPickerDialog(
            initial = cur,
            onDismiss = { editing = null },
            onConfirm = { c -> onChange(colors.toMutableList().also { it[i] = c }); editing = null },
        )
    }
}

@Composable
internal fun Checker(modifier: Modifier = Modifier) {
    val a = MaterialTheme.colorScheme.surfaceContainerHigh
    val b = MaterialTheme.colorScheme.surfaceContainerLowest
    Canvas(modifier) {
        val s = 14.dp.toPx()
        drawRect(b)
        var y = 0f
        var r = 0
        while (y < size.height) {
            var x = if (r % 2 == 0) 0f else s
            while (x < size.width) {
                drawRect(a, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(s, s))
                x += 2 * s
            }
            y += s
            r++
        }
    }
}

private fun catRes(id: String) = when (id) {
    "animals" -> R.string.wa_cat_animals
    "nature" -> R.string.wa_cat_nature
    "food" -> R.string.wa_cat_food
    "music" -> R.string.wa_cat_music
    "sport" -> R.string.wa_cat_sport
    "symbols" -> R.string.wa_cat_symbols
    "shapes" -> R.string.wa_cat_shapes
    "people" -> R.string.wa_cat_people
    "transport" -> R.string.wa_cat_transport
    "objects" -> R.string.wa_cat_objects
    "emoji" -> R.string.wa_cat_emoji
    else -> R.string.wa_cat_letters
}

@Composable
internal fun rememberStr(): (Int) -> String {
    val c = LocalContext.current
    return remember(c) { { id: Int -> c.getString(id) } }
}

// ======================================================================================
// Pestana Forma
// ======================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShapeBox(selected: Boolean, onClick: () -> Unit, onLong: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ShapeThumb(item: ShapeItem, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    if (item.id.startsWith("glyph:")) {
        Text(item.id.removePrefix("glyph:"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        return
    }
    val up = remember(item.id) { ShapeLibrary.unitPath(item) } ?: return
    Canvas(modifier.fillMaxSize()) {
        val s = size.minDimension
        drawIntoCanvas { c ->
            val n = c.nativeCanvas
            n.save()
            n.translate((size.width - s) / 2f, (size.height - s) / 2f)
            n.scale(s, s)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
            n.drawPath(up.first, p)
            if (up.second > 0f) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = up.second
                p.strokeJoin = Paint.Join.ROUND
                p.strokeCap = Paint.Cap.ROUND
                n.drawPath(up.first, p)
            }
            n.restore()
        }
    }
}

@Composable
internal fun ShapeTab(vm: WordArtViewModel, cfg: WordArtConfig, msg: (Int) -> Unit, modifier: Modifier = Modifier) {
    val str = rememberStr()
    val lib by vm.library.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val mine by vm.imported.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableIntStateOf(0) }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var coll by rememberSaveable { mutableStateOf<String?>(null) }
    var fontDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val es = vm.es
    val allLabel = stringResource(R.string.wa_all)

    val pickFile = rememberDocumentPicker(arrayOf("image/*", "image/svg+xml")) { uri ->
        vm.importShape(uri) { msg(R.string.wa_import_failed) }
    }

    val shapes = remember(lib, query, cat, coll, scope, favs, recents) {
        val base = when (scope) {
            1 -> lib.filter { it.id in favs }
            2 -> recents.mapNotNull { ShapeLibrary.find(it) }
            else -> lib
        }
        if (scope == 0) ShapeLibrary.search(base, query, cat, coll) else ShapeLibrary.search(base, query, null, null)
    }

    val currentName = when {
        cfg.shapeId == "text" -> stringResource(R.string.wa_scope_text)
        cfg.shapeId.startsWith("img:") -> stringResource(R.string.wa_scope_mine)
        else -> lib.firstOrNull { it.id == cfg.shapeId }?.label(es) ?: ""
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.wa_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.action_clear)) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 0.dp),
        )
        OptionRow(
            options = listOf(0, 1, 2, 3, 4),
            selected = scope,
            onSelect = { scope = it },
            label = {
                str(when (it) {
                    0 -> R.string.wa_all
                    1 -> R.string.wa_scope_fav
                    2 -> R.string.wa_scope_recent
                    3 -> R.string.wa_scope_mine
                    else -> R.string.wa_scope_text
                })
            },
        )
        if (scope == 0) {
            OptionRow(
                options = listOf<String?>(null) + ShapeLibrary.categories,
                selected = cat,
                onSelect = { cat = it },
                label = { if (it == null) allLabel else str(catRes(it)) },
            )
            OptionRow(
                options = listOf<String?>(null) + ShapeLibrary.collections,
                selected = coll,
                onSelect = { coll = it },
                label = {
                    when (it) {
                        null -> allLabel
                        "ph" -> "Phosphor"
                        "tb" -> "Tabler"
                        "lu" -> "Lucide"
                        "ms" -> "Material"
                        else -> "Pixa"
                    }
                },
            )
        }
        if (currentName.isNotEmpty()) {
            Text(
                stringResource(R.string.wa_current_shape, currentName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        when (scope) {
            3 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickFile) {
                        Icon(Icons.Filled.FileOpen, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wa_import_shape))
                    }
                }
                Text(
                    stringResource(R.string.wa_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ToggleRow(
                    stringResource(R.string.wa_invert_mask), cfg.invertMask, { v -> vm.edit { it.copy(invertMask = v) } },
                    subtitle = stringResource(R.string.wa_invert_mask_sub),
                )
                if (mine.isEmpty()) {
                    EmptyState(stringResource(R.string.wa_no_imports))
                } else {
                    // Rejilla propia (no lazy): esta dentro de una columna con scroll.
                    mine.chunked(6).forEach { row ->
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            row.forEach { id ->
                                Box(Modifier.weight(1f)) {
                                    ShapeBox(cfg.shapeId == id, { vm.pickShape(id) }, { deleting = id }) {
                                        AsyncImage(
                                            model = ShapeLibrary.importedFile(androidx.compose.ui.platform.LocalContext.current, id),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Text(
                        stringResource(R.string.wa_long_press_delete),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            4 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                TextFieldRow(
                    label = stringResource(R.string.wa_shape_text),
                    value = cfg.shapeText,
                    onValueChange = { v -> vm.edit(false) { it.copy(shapeText = v) } },
                    singleLine = false,
                )
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { fontDialog = true }) { Text(cfg.shapeFont.family) }
                    Button(onClick = { vm.edit { it.copy(shapeId = "text") } }) { Text(stringResource(R.string.wa_use_text)) }
                }
                Text(
                    stringResource(R.string.wa_text_shape_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            else -> if (shapes.isEmpty()) {
                EmptyState(stringResource(if (scope == 0) R.string.wa_no_results else R.string.wa_nothing_here))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(46.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(shapes, key = { it.id }) { s ->
                        ShapeBox(cfg.shapeId == s.id, { vm.pickShape(s.id) }, { vm.toggleFavorite(s.id) }) {
                            ShapeThumb(s)
                            if (s.id in favs) {
                                Icon(
                                    Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp).align(Alignment.TopEnd),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (scope in 0..2 && shapes.isNotEmpty()) {
            Text(
                stringResource(R.string.wa_long_press_fav),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }

    if (fontDialog) {
        FontPickerDialog(
            current = cfg.shapeFont,
            onDismiss = { fontDialog = false },
            onPick = { f -> fontDialog = false; vm.edit { it.copy(shapeFont = f) } },
        )
    }
    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            body = stringResource(R.string.wa_delete_shape_confirm),
            onConfirm = { vm.deleteImported(id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

// ======================================================================================
// Pestana Palabras
// ======================================================================================

@Composable
private fun ImportTextDialog(onDismiss: () -> Unit, onApply: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val pick = rememberDocumentPicker(arrayOf("text/*")) { uri ->
        runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
            .getOrNull()?.let { text = it.take(300_000) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wa_import_text)) },
        text = {
            Column {
                Text(stringResource(R.string.wa_import_text_sub), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, minLines = 5, maxLines = 8,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(onClick = pick) {
                    Icon(Icons.Filled.FileOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.wa_open_file))
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = text.isNotBlank(), onClick = { onApply(WordLists.frequencies(text), true); onDismiss() }) {
                    Text(stringResource(R.string.wa_replace))
                }
                TextButton(enabled = text.isNotBlank(), onClick = { onApply(WordLists.frequencies(text), false); onDismiss() }) {
                    Text(stringResource(R.string.action_add))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun rotRes(m: RotMode) = when (m) {
    RotMode.HORIZONTAL -> R.string.wa_rot_h
    RotMode.VERTICAL -> R.string.wa_rot_v
    RotMode.MIXED -> R.string.wa_rot_mixed
    RotMode.RANDOM -> R.string.wa_rot_random
    RotMode.ANGLES -> R.string.wa_rot_angles
}

@Composable
internal fun WordsTab(vm: WordArtViewModel, cfg: WordArtConfig) {
    val str = rememberStr()
    val es = vm.es
    val clip = LocalClipboardManager.current
    var listSel by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val count = remember(cfg.wordsText) { parseWords(cfg.wordsText).size }

    Sw(vm, stringResource(R.string.wa_inverted), cfg.inverted, stringResource(R.string.wa_inverted_sub)) { c, v -> c.copy(inverted = v) }

    Head(stringResource(R.string.wa_lists))
    OptionRow(
        options = WordLists.presets.map { it.id },
        selected = listSel,
        onSelect = { listSel = it },
        label = { id -> WordLists.presets.first { it.id == id }.name(es) },
    )
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = listSel != null, onClick = {
            val t = WordLists.presets.first { it.id == listSel }.text(es)
            vm.edit { it.copy(wordsText = t) }
        }) { Text(stringResource(R.string.wa_replace)) }
        OutlinedButton(enabled = listSel != null, onClick = {
            val t = WordLists.presets.first { it.id == listSel }.text(es)
            vm.edit { it.copy(wordsText = (it.wordsText.trimEnd() + "\n" + t).trim()) }
        }) { Text(stringResource(R.string.action_add)) }
    }

    Head(stringResource(R.string.wa_words_title, count))
    OutlinedTextField(
        value = cfg.wordsText,
        onValueChange = { v -> vm.edit(false) { it.copy(wordsText = v) } },
        placeholder = { Text(stringResource(R.string.wa_words_hint)) },
        minLines = 5,
        maxLines = 10,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .onFocusChanged { if (!it.isFocused) vm.commit() },
    )
    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            clip.getText()?.text?.takeIf { it.isNotBlank() }?.let { p ->
                vm.edit { it.copy(wordsText = (it.wordsText.trimEnd() + "\n" + p.trim()).trim()) }
            }
        }) {
            Icon(Icons.Filled.ContentPaste, null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.wa_paste))
        }
        OutlinedButton(onClick = { importing = true }) { Text(stringResource(R.string.wa_import_text)) }
        TextButton(onClick = { vm.edit { it.copy(wordsText = "") } }) { Text(stringResource(R.string.action_clear)) }
    }
    TextFieldRow(
        label = stringResource(R.string.wa_main_word),
        value = cfg.mainWord,
        onValueChange = { v -> vm.edit(false) { it.copy(mainWord = v) } },
        placeholder = stringResource(R.string.wa_main_word_hint),
    )

    Head(stringResource(R.string.wa_size_density))
    Sl(vm, stringResource(R.string.wa_min_size), cfg.minSize, 1f..12f, 2.4f, "%") { c, v -> c.copy(minSize = v, maxSize = maxOf(v, c.maxSize)) }
    Sl(vm, stringResource(R.string.wa_max_size), cfg.maxSize, 2f..30f, 9f, "%") { c, v -> c.copy(maxSize = v, minSize = minOf(v, c.minSize)) }
    Sl(vm, stringResource(R.string.wa_density), cfg.density, 0f..100f, 70f) { c, v -> c.copy(density = v) }
    Sl(vm, stringResource(R.string.wa_spacing), cfg.spacing, 0f..4f, 0.4f, "%") { c, v -> c.copy(spacing = v) }
    Sl(vm, stringResource(R.string.wa_margin), cfg.margin, 0f..10f, 1f, "%") { c, v -> c.copy(margin = v) }
    OptionRow(
        options = WordOrder.values().toList(),
        selected = cfg.order,
        onSelect = { o -> vm.edit { it.copy(order = o) } },
        label = { str(if (it == WordOrder.BY_WEIGHT) R.string.wa_order_weight else R.string.wa_order_random) },
    )
    Sw(vm, stringResource(R.string.wa_repeat), cfg.repeatWords, stringResource(R.string.wa_repeat_sub)) { c, v -> c.copy(repeatWords = v) }

    Head(stringResource(R.string.wa_rotation))
    OptionRow(
        options = RotMode.values().toList(),
        selected = cfg.rotMode,
        onSelect = { m -> vm.edit { it.copy(rotMode = m) } },
        label = { str(rotRes(it)) },
    )
    if (cfg.rotMode == RotMode.ANGLES) {
        TextFieldRow(
            label = stringResource(R.string.wa_rot_angles_field),
            value = cfg.rotAngles,
            onValueChange = { v -> vm.edit(false) { it.copy(rotAngles = v) } },
        )
    }

    Head(stringResource(R.string.wa_zones))
    cfg.zones.forEachIndexed { i, z ->
        fun upd(f: (Zone) -> Zone) = vm.edit(false) { c -> c.copy(zones = c.zones.mapIndexed { k, x -> if (k == i) f(x) else x }) }
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.wa_zone_n, i + 1), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    OptionRow(
                        options = listOf(true, false),
                        selected = z.ellipse,
                        onSelect = { e -> upd { it.copy(ellipse = e); }; vm.commit() },
                        label = { str(if (it) R.string.wa_zone_ellipse else R.string.wa_zone_rect) },
                        modifier = Modifier.weight(2f),
                    )
                    IconButton(onClick = { vm.edit { c -> c.copy(zones = c.zones.filterIndexed { k, _ -> k != i }) } }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                    }
                }
                SliderRow("X", z.cx, { v -> upd { it.copy(cx = v) } }, 0f..1f, valueText = formatValue(z.cx), onChangeFinished = vm::commit)
                SliderRow("Y", z.cy, { v -> upd { it.copy(cy = v) } }, 0f..1f, valueText = formatValue(z.cy), onChangeFinished = vm::commit)
                SliderRow(stringResource(R.string.wa_zone_w), z.rx, { v -> upd { it.copy(rx = v) } }, 0.02f..0.5f, valueText = formatValue(z.rx), onChangeFinished = vm::commit)
                SliderRow(stringResource(R.string.wa_zone_h), z.ry, { v -> upd { it.copy(ry = v) } }, 0.02f..0.5f, valueText = formatValue(z.ry), onChangeFinished = vm::commit)
            }
        }
    }
    OutlinedButton(
        onClick = { vm.edit { it.copy(zones = it.zones + Zone()) } },
        modifier = Modifier.padding(horizontal = 12.dp),
        enabled = cfg.zones.size < 6,
    ) {
        Icon(Icons.Filled.Add, null)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.wa_add_zone))
    }

    Head(stringResource(R.string.wa_seed))
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        var seedText by remember(cfg.seed) { mutableStateOf(cfg.seed.toString()) }
        OutlinedTextField(
            value = seedText,
            onValueChange = { raw ->
                seedText = raw.filter { it.isDigit() }.take(12)
                seedText.toLongOrNull()?.let { s -> vm.edit { it.copy(seed = s) } }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = vm::regenerate) { Icon(Icons.Filled.Casino, stringResource(R.string.wa_regenerate)) }
    }
    Spacer(Modifier.height(16.dp))

    if (importing) {
        ImportTextDialog(
            onDismiss = { importing = false },
            onApply = { freq, replace ->
                vm.edit { it.copy(wordsText = if (replace) freq else (it.wordsText.trimEnd() + "\n" + freq).trim()) }
            },
        )
    }
}

// ======================================================================================
// Pestana Colores / Fuentes
// ======================================================================================

@Composable
private fun PaletteChip(colors: List<Int>, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(4.dp)) { colors.take(6).forEach { Box(Modifier.size(width = 14.dp, height = 24.dp).background(Color(it))) } }
    }
}

private fun colorModeRes(m: ColorMode) = when (m) {
    ColorMode.SINGLE -> R.string.wa_cm_single
    ColorMode.PALETTE -> R.string.wa_cm_palette
    ColorMode.RANDOM -> R.string.wa_cm_random
    ColorMode.GRADIENT -> R.string.wa_cm_gradient
    ColorMode.BY_WEIGHT -> R.string.wa_cm_weight
}

@Composable
internal fun ColorsTab(vm: WordArtViewModel, cfg: WordArtConfig) {
    val str = rememberStr()
    var fontDialog by remember { mutableStateOf(false) }
    val st = cfg.textStyle
    fun updSt(commit: Boolean = true, f: (com.dskmusic.pixa.core.wordart.TextStyleSpec) -> com.dskmusic.pixa.core.wordart.TextStyleSpec) =
        vm.edit(commit) { it.copy(textStyle = f(it.textStyle)) }

    Head(stringResource(R.string.wa_colors))
    OptionRow(
        options = ColorMode.values().toList(),
        selected = cfg.colorMode,
        onSelect = { m -> vm.edit { it.copy(colorMode = m) } },
        label = { str(colorModeRes(it)) },
    )
    when (cfg.colorMode) {
        ColorMode.SINGLE -> ColorRow(stringResource(R.string.wa_color), cfg.color1) { c -> vm.edit { it.copy(color1 = c) } }
        ColorMode.PALETTE, ColorMode.BY_WEIGHT -> {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WordArtConfig.PALETTES.size) { i ->
                    val p = WordArtConfig.PALETTES[i].second
                    PaletteChip(p, cfg.palette == p) { vm.edit { it.copy(palette = p) } }
                }
            }
            ColorList(cfg.palette, 2, 8) { l -> vm.edit { it.copy(palette = l) } }
        }
        ColorMode.GRADIENT -> {
            ColorRow(stringResource(R.string.wa_color_start), cfg.color1) { c -> vm.edit { it.copy(color1 = c) } }
            ColorRow(stringResource(R.string.wa_color_end), cfg.color2) { c -> vm.edit { it.copy(color2 = c) } }
            Sl(vm, stringResource(R.string.wa_angle), cfg.gradAngle, 0f..360f, 45f, "°") { c, v -> c.copy(gradAngle = v) }
        }
        ColorMode.RANDOM -> Unit
    }

    Head(stringResource(R.string.wa_fonts))
    OptionRow(
        options = listOf(0, 1),
        selected = cfg.fontMode,
        onSelect = { m -> vm.edit { it.copy(fontMode = m) } },
        label = { str(if (it == 0) R.string.wa_font_fixed else R.string.wa_font_random) },
    )
    if (cfg.fontMode == 0) {
        OutlinedButton(onClick = { fontDialog = true }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(cfg.font.family) }
    } else {
        OptionRow(
            options = (0 until WordArtConfig.POOL_COUNT).toList(),
            selected = cfg.fontPool,
            onSelect = { p -> vm.edit { it.copy(fontPool = p) } },
            label = {
                str(when (it) {
                    0 -> R.string.wa_pool_mix
                    1 -> R.string.wa_pool_script
                    2 -> R.string.wa_pool_bold
                    else -> R.string.wa_pool_system
                })
            },
        )
        Text(
            stringResource(R.string.wa_pool_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    Head(stringResource(R.string.wa_text_style))
    ToggleRow(stringResource(R.string.wa_bold), st.bold, { v -> updSt { it.copy(bold = v) } })
    ToggleRow(stringResource(R.string.wa_italic), st.italic, { v -> updSt { it.copy(italic = v) } })
    SliderRow(
        stringResource(R.string.wa_letter_spacing), st.letterSpacing, { v -> updSt(false) { it.copy(letterSpacing = v) } },
        -0.05f..0.5f, valueText = formatValue(st.letterSpacing), defaultValue = 0f, onChangeFinished = vm::commit,
    )
    ToggleRow(stringResource(R.string.wa_shadow), st.shadow, { v -> updSt { it.copy(shadow = v) } })
    if (st.shadow) {
        ColorRow(stringResource(R.string.wa_color), st.shadowColor) { c -> updSt { it.copy(shadowColor = c) } }
        SliderRow(stringResource(R.string.wa_blur), st.shadowBlur, { v -> updSt(false) { it.copy(shadowBlur = v) } }, 0f..30f, valueText = formatValue(st.shadowBlur), onChangeFinished = vm::commit)
        SliderRow(stringResource(R.string.wa_offset_x), st.shadowDx, { v -> updSt(false) { it.copy(shadowDx = v) } }, -20f..20f, valueText = formatValue(st.shadowDx), onChangeFinished = vm::commit)
        SliderRow(stringResource(R.string.wa_offset_y), st.shadowDy, { v -> updSt(false) { it.copy(shadowDy = v) } }, -20f..20f, valueText = formatValue(st.shadowDy), onChangeFinished = vm::commit)
    }
    ToggleRow(stringResource(R.string.wa_outline), st.outline, { v -> updSt { it.copy(outline = v) } })
    if (st.outline) {
        ColorRow(stringResource(R.string.wa_color), st.outlineColor) { c -> updSt { it.copy(outlineColor = c) } }
        SliderRow(stringResource(R.string.wa_width), st.outlineWidth, { v -> updSt(false) { it.copy(outlineWidth = v) } }, 1f..20f, valueText = formatValue(st.outlineWidth), onChangeFinished = vm::commit)
    }
    ToggleRow(stringResource(R.string.wa_glow), st.glow, { v -> updSt { it.copy(glow = v) } })
    if (st.glow) {
        ColorRow(stringResource(R.string.wa_color), st.glowColor) { c -> updSt { it.copy(glowColor = c) } }
        SliderRow(stringResource(R.string.wa_radius), st.glowRadius, { v -> updSt(false) { it.copy(glowRadius = v) } }, 5f..80f, valueText = formatValue(st.glowRadius), onChangeFinished = vm::commit)
    }
    ToggleRow(stringResource(R.string.wa_text_gradient), st.gradient, { v -> updSt { it.copy(gradient = v) } })
    if (st.gradient) {
        ColorRow(stringResource(R.string.wa_color_end), st.gradColor2) { c -> updSt { it.copy(gradColor2 = c) } }
        SliderRow(stringResource(R.string.wa_angle), st.gradAngle, { v -> updSt(false) { it.copy(gradAngle = v) } }, 0f..360f, valueText = formatValue(st.gradAngle) + "°", onChangeFinished = vm::commit)
    }
    Spacer(Modifier.height(16.dp))

    if (fontDialog) {
        FontPickerDialog(
            current = cfg.font,
            onDismiss = { fontDialog = false },
            onPick = { f -> fontDialog = false; vm.edit { it.copy(font = f) } },
        )
    }
}

// ======================================================================================
// Pestana Fondo
// ======================================================================================

private fun bgKindRes(k: BgKind) = when (k) {
    BgKind.NONE -> R.string.wa_bg_none
    BgKind.SOLID -> R.string.wa_bg_solid
    BgKind.GRADIENT -> R.string.wa_bg_gradient
    BgKind.IMAGE -> R.string.wa_bg_image
    BgKind.PATTERN -> R.string.wa_bg_pattern
}

@Composable
internal fun BackgroundTab(vm: WordArtViewModel, cfg: WordArtConfig, msg: (Int) -> Unit) {
    val str = rememberStr()
    var target by rememberSaveable { mutableIntStateOf(2) }
    val spec = when (target) { 2 -> cfg.bgCanvas; 0 -> cfg.bgWords; else -> cfg.bgOther }
    fun upd(commit: Boolean = true, f: (BackgroundSpec) -> BackgroundSpec) = vm.edit(commit) { c ->
        when (target) {
            2 -> c.copy(bgCanvas = f(c.bgCanvas))
            0 -> c.copy(bgWords = f(c.bgWords))
            else -> c.copy(bgOther = f(c.bgOther))
        }
    }
    val pickImage = rememberImagePicker { uri -> vm.importBackground(uri, target) { msg(R.string.wa_import_failed) } }

    Head(stringResource(R.string.wa_canvas_format))
    val ratios = listOf(1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f)
    val names = listOf("1:1", "4:3", "3:4", "16:9", "9:16")
    OptionRow(
        options = ratios,
        selected = ratios.minByOrNull { kotlin.math.abs(it - cfg.aspect) },
        onSelect = { r -> vm.edit { it.copy(aspect = r) } },
        label = { names[ratios.indexOf(it)] },
    )

    Head(stringResource(R.string.wa_bg_apply_to))
    OptionRow(
        options = listOf(2, 0, 1),
        selected = target,
        onSelect = { target = it },
        label = {
            val inside = (it == 0) != cfg.inverted
            str(
                when {
                    it == 2 -> R.string.wa_zone_canvas
                    it == 0 && !cfg.inverted -> R.string.wa_zone_words_inside
                    it == 0 -> R.string.wa_zone_words_outside
                    inside -> R.string.wa_zone_free_inside
                    else -> R.string.wa_zone_free_outside
                },
            )
        },
    )
    OptionRow(
        options = BgKind.values().toList(),
        selected = spec.kind,
        onSelect = { k -> upd { it.copy(kind = k) } },
        label = { str(bgKindRes(it)) },
    )
    when (spec.kind) {
        BgKind.NONE -> Text(
            stringResource(R.string.wa_bg_none_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        BgKind.SOLID -> ColorRow(stringResource(R.string.wa_color), spec.color) { c -> upd { it.copy(color = c) } }
        BgKind.GRADIENT -> {
            ColorList(spec.gradColors, 2, 5) { l -> upd { it.copy(gradColors = l) } }
            ToggleRow(stringResource(R.string.wa_radial), spec.radial, { v -> upd { it.copy(radial = v) } })
            if (!spec.radial) {
                SliderRow(stringResource(R.string.wa_angle), spec.angle, { v -> upd(false) { it.copy(angle = v) } }, 0f..360f, valueText = formatValue(spec.angle) + "°", onChangeFinished = vm::commit)
            }
        }
        BgKind.IMAGE -> {
            OutlinedButton(onClick = pickImage, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(if (spec.imagePath == null) R.string.wa_pick_image else R.string.wa_change_image))
            }
            OptionRow(
                options = ImgFit.values().toList(),
                selected = spec.fit,
                onSelect = { f -> upd { it.copy(fit = f) } },
                label = {
                    str(when (it) {
                        ImgFit.CROP -> R.string.wa_fit_crop
                        ImgFit.FIT -> R.string.wa_fit_fit
                        ImgFit.STRETCH -> R.string.wa_fit_stretch
                    })
                },
            )
            SliderRow(stringResource(R.string.wa_blur), spec.imgBlur, { v -> upd(false) { it.copy(imgBlur = v) } }, 0f..10f, valueText = formatValue(spec.imgBlur), defaultValue = 0f, onChangeFinished = vm::commit)
        }
        BgKind.PATTERN -> {
            OptionRow(
                options = PatternKind.values().toList(),
                selected = spec.pattern,
                onSelect = { p -> upd { it.copy(pattern = p) } },
                label = {
                    str(when (it) {
                        PatternKind.STRIPES -> R.string.wa_pat_stripes
                        PatternKind.DOTS -> R.string.wa_pat_dots
                        PatternKind.GRID -> R.string.wa_pat_grid
                        PatternKind.CHECKER -> R.string.wa_pat_checker
                    })
                },
            )
            ColorRow(stringResource(R.string.wa_color), spec.color) { c -> upd { it.copy(color = c) } }
            ColorRow(stringResource(R.string.wa_color_back), spec.color2) { c -> upd { it.copy(color2 = c) } }
            SliderRow(stringResource(R.string.wa_size), spec.patternSize, { v -> upd(false) { it.copy(patternSize = v) } }, 2f..30f, valueText = formatValue(spec.patternSize), onChangeFinished = vm::commit)
        }
    }
    if (spec.kind != BgKind.NONE) {
        SliderRow(stringResource(R.string.wa_opacity), spec.opacity, { v -> upd(false) { it.copy(opacity = v) } }, 0f..1f, valueText = formatValue(spec.opacity), defaultValue = 1f, onChangeFinished = vm::commit)
    }
    Spacer(Modifier.height(16.dp))
}

// ======================================================================================
// Pestana Efectos
// ======================================================================================

@Composable
internal fun EffectsTab(vm: WordArtViewModel, cfg: WordArtConfig) {
    val str = rememberStr()
    val fx = cfg.fx
    fun upd(commit: Boolean = true, f: (ShapeFx) -> ShapeFx) = vm.edit(commit) { it.copy(fx = f(it.fx)) }
    @Composable
    fun S(label: String, v: Float, r: ClosedFloatingPointRange<Float>, def: Float? = null, set: (ShapeFx, Float) -> ShapeFx) =
        SliderRow(label, v, { x -> upd(false) { set(it, x) } }, r, valueText = formatValue(v), defaultValue = def, onChangeFinished = vm::commit)
    @Composable
    fun T(label: String, v: Boolean, set: (ShapeFx, Boolean) -> ShapeFx) = ToggleRow(label, v, { x -> upd { set(it, x) } })

    Head(stringResource(R.string.wa_fx_transform))
    S(stringResource(R.string.wa_fx_scale), fx.scale, 0.2f..1.4f, 0.92f) { f, v -> f.copy(scale = v) }
    S(stringResource(R.string.wa_fx_rotation), fx.rotation, -180f..180f, 0f) { f, v -> f.copy(rotation = v) }
    T(stringResource(R.string.wa_fx_flip_h), fx.flipH) { f, v -> f.copy(flipH = v) }
    T(stringResource(R.string.wa_fx_flip_v), fx.flipV) { f, v -> f.copy(flipV = v) }
    S(stringResource(R.string.wa_fx_pos_x), fx.posX, -0.5f..0.5f, 0f) { f, v -> f.copy(posX = v) }
    S(stringResource(R.string.wa_fx_pos_y), fx.posY, -0.5f..0.5f, 0f) { f, v -> f.copy(posY = v) }
    S(stringResource(R.string.wa_fx_corner), fx.corner, 0f..50f, 0f) { f, v -> f.copy(corner = v) }
    S(stringResource(R.string.wa_fx_smooth), fx.smooth, 0f..10f, 0f) { f, v -> f.copy(smooth = v) }
    S(stringResource(R.string.wa_fx_erode), fx.erode, 0f..10f, 0f) { f, v -> f.copy(erode = v) }
    S(stringResource(R.string.wa_fx_dilate), fx.dilate, 0f..10f, 0f) { f, v -> f.copy(dilate = v) }

    Head(stringResource(R.string.wa_fx_border))
    T(stringResource(R.string.wa_fx_border_on), fx.borderOn) { f, v -> f.copy(borderOn = v) }
    if (fx.borderOn) {
        S(stringResource(R.string.wa_width), fx.borderWidth, 0.2f..6f, 1.2f) { f, v -> f.copy(borderWidth = v) }
        ColorRow(stringResource(R.string.wa_color), fx.borderColor) { c -> upd { it.copy(borderColor = c) } }
        T(stringResource(R.string.wa_fx_border_gradient), fx.borderGradient) { f, v -> f.copy(borderGradient = v) }
        if (fx.borderGradient) ColorRow(stringResource(R.string.wa_color_end), fx.borderColor2) { c -> upd { it.copy(borderColor2 = c) } }
        OptionRow(
            options = BorderStyle.values().toList(),
            selected = fx.borderStyle,
            onSelect = { s -> upd { it.copy(borderStyle = s) } },
            label = {
                str(when (it) {
                    BorderStyle.SOLID -> R.string.wa_bs_solid
                    BorderStyle.DOTTED -> R.string.wa_bs_dotted
                    BorderStyle.DASHED -> R.string.wa_bs_dashed
                })
            },
        )
        S(stringResource(R.string.wa_opacity), fx.borderOpacity, 0f..1f, 1f) { f, v -> f.copy(borderOpacity = v) }
        T(stringResource(R.string.wa_fx_border_double), fx.doubleBorder) { f, v -> f.copy(doubleBorder = v) }
    }

    Head(stringResource(R.string.wa_fx_shadow_glow))
    T(stringResource(R.string.wa_shadow), fx.shadowOn) { f, v -> f.copy(shadowOn = v) }
    if (fx.shadowOn) {
        ColorRow(stringResource(R.string.wa_color), fx.shadowColor) { c -> upd { it.copy(shadowColor = c) } }
        S(stringResource(R.string.wa_blur), fx.shadowBlur, 0f..10f, 2f) { f, v -> f.copy(shadowBlur = v) }
        S(stringResource(R.string.wa_offset_x), fx.shadowDx, -10f..10f, 1.5f) { f, v -> f.copy(shadowDx = v) }
        S(stringResource(R.string.wa_offset_y), fx.shadowDy, -10f..10f, 1.5f) { f, v -> f.copy(shadowDy = v) }
    }
    T(stringResource(R.string.wa_glow), fx.glowOn) { f, v -> f.copy(glowOn = v) }
    if (fx.glowOn) {
        ColorRow(stringResource(R.string.wa_color), fx.glowColor) { c -> upd { it.copy(glowColor = c) } }
        S(stringResource(R.string.wa_radius), fx.glowRadius, 0.5f..15f, 3f) { f, v -> f.copy(glowRadius = v) }
    }

    Head(stringResource(R.string.wa_fx_relief))
    T(stringResource(R.string.wa_fx_bevel), fx.bevelOn) { f, v -> f.copy(bevelOn = v) }
    if (fx.bevelOn) {
        S(stringResource(R.string.wa_size), fx.bevelSize, 0.2f..4f, 0.8f) { f, v -> f.copy(bevelSize = v) }
        S(stringResource(R.string.wa_fx_strength), fx.bevelStrength, 0f..1f, 0.6f) { f, v -> f.copy(bevelStrength = v) }
    }
    S(stringResource(R.string.wa_blur), fx.blur, 0f..6f, 0f) { f, v -> f.copy(blur = v) }
    S(stringResource(R.string.wa_opacity), fx.opacity, 0f..1f, 1f) { f, v -> f.copy(opacity = v) }
    Spacer(Modifier.height(16.dp))
}

// ======================================================================================
// Pestana Presets
// ======================================================================================

@Composable
private fun PresetCard(vm: WordArtViewModel, key: String, cfg: WordArtConfig, name: String, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    val bm by produceState<Bitmap?>(null, key) { value = vm.thumb(key, cfg) }
    Column(Modifier.width(96.dp)) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
            Checker(Modifier.fillMaxSize())
            val b = bm
            if (b == null) {
                CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center), strokeWidth = 2.dp)
            } else {
                Image(b.asImageBitmap(), name, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_delete), Modifier.size(16.dp))
                }
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun BlockRow(chips: List<Triple<String, () -> Unit, (() -> Unit)?>>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips.size) { i ->
            val (label, apply, del) = chips[i]
            AssistChip(
                onClick = apply,
                label = { Text(label) },
                trailingIcon = del?.let { d ->
                    @Composable {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_delete), Modifier.size(16.dp).clickable(onClick = d))
                    }
                },
            )
        }
    }
}

@Composable
internal fun PresetsTab(vm: WordArtViewModel, cfg: WordArtConfig, msg: (Int) -> Unit) {
    val context = LocalContext.current
    val bundle by vm.presets.collectAsStateWithLifecycle()
    val es = vm.es
    var naming by remember { mutableIntStateOf(-1) }
    var removing by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(vm.exportPresets()) } }.isSuccess
            msg(if (ok) R.string.wa_presets_exported else R.string.wa_import_failed)
        }
    }
    val importer = rememberDocumentPicker(arrayOf("application/json", "text/*")) { uri ->
        val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        msg(if (text != null && vm.importPresets(text)) R.string.wa_presets_imported else R.string.wa_import_failed)
    }

    Head(stringResource(R.string.wa_presets_full))
    val builtin = remember(es) { BuiltinPresets.full(es) }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(builtin.size) { i ->
            val p = builtin[i]
            PresetCard(vm, "b$i$es", p.config, stringResource(p.nameRes), { vm.applyFull(p.config) }, null)
        }
        items(bundle.full.size) { i ->
            val p = bundle.full[i]
            PresetCard(vm, p.id, p.config, p.name, { vm.applyFull(p.config) }) { removing = 0 to p.id }
        }
    }
    OutlinedButton(onClick = { naming = 0 }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.wa_save_current))
    }

    Head(stringResource(R.string.wa_presets_text))
    BlockRow(
        BuiltinPresets.texts.map { b -> Triple(stringResource(b.nameRes), { vm.applyText(b.block) }, null as (() -> Unit)?) } +
            bundle.text.map { b -> Triple(b.name, { vm.applyText(b.block) }, { removing = 1 to b.id } as (() -> Unit)?) },
    )
    TextButton(onClick = { naming = 1 }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(R.string.wa_save_current)) }

    Head(stringResource(R.string.wa_presets_bg))
    BlockRow(
        BuiltinPresets.bgs.map { b -> Triple(stringResource(b.nameRes), { vm.applyBg(b.words, b.other) }, null as (() -> Unit)?) } +
            bundle.bg.map { b -> Triple(b.name, { vm.applyBg(b.words, b.other) }, { removing = 2 to b.id } as (() -> Unit)?) },
    )
    TextButton(onClick = { naming = 2 }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(R.string.wa_save_current)) }

    Head(stringResource(R.string.wa_presets_shape))
    BlockRow(
        BuiltinPresets.fxs.map { b -> Triple(stringResource(b.nameRes), { vm.applyFx(b.fx) }, null as (() -> Unit)?) } +
            bundle.shape.map { b -> Triple(b.name, { vm.applyFx(b.fx) }, { removing = 3 to b.id } as (() -> Unit)?) },
    )
    TextButton(onClick = { naming = 3 }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(R.string.wa_save_current)) }

    Head(stringResource(R.string.wa_presets_share))
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { exporter.launch("pixa_nube_presets.json") }) { Text(stringResource(R.string.action_export)) }
        OutlinedButton(onClick = importer) { Text(stringResource(R.string.action_import)) }
    }
    Spacer(Modifier.height(16.dp))

    if (naming >= 0) {
        TextPromptDialog(
            title = stringResource(R.string.wa_save_current),
            initial = "",
            onConfirm = { n ->
                val name = n.ifBlank { "Preset" }
                when (naming) {
                    0 -> vm.saveFull(name)
                    1 -> vm.saveText(name)
                    2 -> vm.saveBg(name)
                    else -> vm.saveShape(name)
                }
                naming = -1
            },
            onDismiss = { naming = -1 },
        )
    }
    removing?.let { (kind, id) ->
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            body = stringResource(R.string.wa_delete_preset_confirm),
            onConfirm = { vm.deletePreset(kind, id); removing = null },
            onDismiss = { removing = null },
        )
    }
}
