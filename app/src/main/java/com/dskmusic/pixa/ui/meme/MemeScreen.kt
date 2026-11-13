package com.dskmusic.pixa.ui.meme

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Scaffold
import com.dskmusic.pixa.ui.common.PixaToastHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.TextSpec
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.render.TextRenderer
import com.dskmusic.pixa.core.text.FontRepository
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.EditorMessages
import com.dskmusic.pixa.ui.common.GeneratorActions
import com.dskmusic.pixa.ui.common.GeneratorPreview
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.core.collage.CollagePhoto
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Los dos estilos de meme del requisito 20. */
private enum class MemeStyle { CLASSIC, WHITE_BAR }

private val DRAFT_JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/** Estado del meme para recuperarlo tras un cierre inesperado. */
@Serializable
private class MemeDraft(
    val photoId: String?,
    val top: String,
    val bottom: String,
    val extras: List<String>,
    val style: MemeStyle,
    val autofit: Boolean,
    val templateId: String?,
    val tPhotos: List<CollagePhoto?>,
    val tTexts: List<String>,
    val slot: Int,
    val fontFamily: String,
    val caps: Boolean,
    val outlineOn: Boolean,
)

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

/** Margen del texto respecto al borde de la foto, en fraccion del alto. */
private const val MARGIN_FRACTION = 0.03f

/**
 * Generador de memes (requisito 20).
 *
 * El texto se ajusta al ancho solo: la letra condensada tipo Impact (Anton, descargada,
 * sin .ttf en el proyecto) se reduce hasta que la frase cabe en una linea. Las capas que
 * salen de aqui son capas de texto normales, asi que en el editor se siguen reeditando.
 */
@Composable
fun MemeScreen(
    editor: EditorViewModel,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val vm = editor
    val context = LocalContext.current
    val fonts = remember(context) { context.pixa.fonts }
    val renderer = remember(context) { context.pixa.renderer }
    val snackbar = remember { SnackbarHostState() }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var photoId by remember { mutableStateOf<String?>(null) }
    var top by remember { mutableStateOf("") }
    var bottom by remember { mutableStateOf("") }
    val extras = remember { mutableStateListOf<String>() }
    var style by remember { mutableStateOf(MemeStyle.CLASSIC) }
    var autofit by remember { mutableStateOf(true) }
    var typeface by remember { mutableStateOf<Typeface?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    // Modo plantilla (varios paneles). null = el modo de siempre: foto con texto arriba y abajo.
    var template by remember { mutableStateOf<MemeTemplate?>(null) }
    val tPhotos = remember { mutableStateListOf<CollagePhoto?>() }
    val tTexts = remember { mutableStateListOf<String>() }
    var slot by remember { mutableStateOf(0) }
    var pickingSlot by remember { mutableStateOf(0) }
    var fontFamily by remember { mutableStateOf(FontRepository.MEME_FAMILY) }
    var caps by remember { mutableStateOf(true) }
    var outlineOn by remember { mutableStateOf(true) }
    var tTypeface by remember { mutableStateOf<Typeface?>(null) }

    val pickPhoto = rememberMemePicker { bitmap ->
        photo = bitmap
        photoId = vm.putBitmap(bitmap)
    }

    val pickSlot = rememberMemePicker { bitmap ->
        val i = pickingSlot
        if (i in tPhotos.indices) {
            tPhotos[i] = CollagePhoto(vm.putBitmap(bitmap), bitmap.width, bitmap.height)
        }
    }

    fun selectTemplate(t: MemeTemplate?) {
        template = t
        slot = 0
        if (t == null) return
        // Se conservan fotos y textos ya puestos al cambiar de plantilla.
        val oldPhotos = tPhotos.toList()
        tPhotos.clear()
        repeat(t.photos.size) { tPhotos.add(oldPhotos.getOrNull(it)) }
        val base = photo
        val id = photoId
        // La foto del modo clasico pasa al primer hueco.
        if (tPhotos.isNotEmpty() && tPhotos[0] == null && base != null && id != null) {
            tPhotos[0] = CollagePhoto(id, base.width, base.height)
        }
        val oldTexts = tTexts.toList()
        tTexts.clear()
        repeat(t.texts.size) { tTexts.add(oldTexts.getOrNull(it).orEmpty()) }
    }

    val templateLabels = remember(context) {
        MEME_TEMPLATES.associate { it.id to context.getString(it.nameRes) }
    }
    val modeLabel = stringResource(R.string.meme_mode_photo)

    val styleNames = remember(context) {
        mapOf(
            MemeStyle.CLASSIC to context.getString(R.string.meme_classic),
            MemeStyle.WHITE_BAR to context.getString(R.string.meme_modern),
        )
    }

    // La fuente del meme se pide una vez; hasta que llega se mide con la del sistema y la
    // vista previa se repinta sola cuando entra, porque typeface forma parte del estado.
    LaunchedEffect(Unit) {
        typeface = fonts.load(FontRef(FontRepository.MEME_FAMILY, FontRef.Source.GOOGLE))
    }
    LaunchedEffect(fontFamily) {
        // Roboto (subtitulos) y Playfair (poster) tambien se miden: se piden con la elegida.
        fonts.load(FontRef("Roboto", FontRef.Source.GOOGLE))
        fonts.load(FontRef("Playfair Display", FontRef.Source.GOOGLE))
        tTypeface = fonts.load(FontRef(fontFamily, FontRef.Source.GOOGLE))
    }
    // Cada fuente que llega recoloca las cajas, que se miden con la fuente real de cada una.
    val fontsLoaded by fonts.loaded.collectAsStateWithLifecycle()

    val layerName = stringResource(R.string.layer_text)
    val imageName = stringResource(R.string.layer_image)

    val buildClassic: () -> PixaDocument? = {
        val base = photo
        val id = photoId
        if (base == null || id == null) {
            null
        } else {
            buildMeme(
                photoId = id,
                photoWidth = base.width,
                photoHeight = base.height,
                top = top,
                bottom = bottom,
                extras = extras.toList(),
                whiteBar = style == MemeStyle.WHITE_BAR,
                autofit = autofit,
                typeface = typeface,
                textLayerName = layerName,
                imageLayerName = imageName,
            )
        }
    }

    val templateNames = MemeNames(layerName, imageName, imageName)
    val build: () -> PixaDocument? = {
        val tpl = template
        if (tpl != null) {
            buildTemplateMeme(
                tpl, tPhotos.toList(), tTexts.toList(), fontFamily, caps, outlineOn,
                { ref -> fonts.cached(ref) }, templateNames,
            )
        } else {
            buildClassic()
        }
    }

    // Vista previa con un pequeno retardo: escribir no debe recomponer un render por
    // pulsacion de tecla, y el documento del meme se vuelve a montar entero cada vez.
    LaunchedEffect(renderer) {
        snapshotFlow {
            listOf(
                photoId, top, bottom, extras.toList(), style, autofit, typeface,
                template, tPhotos.toList(), tTexts.toList(), fontFamily, caps, outlineOn, tTypeface,
                fontsLoaded,
            )
        }
            .collectLatest {
                // collectLatest cancela el render anterior, asi que esta espera hace de
                // antirrebote sin depender de operadores de Flow en vista previa.
                delay(180)
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
    val dirty = photo != null || top.isNotBlank() || bottom.isNotBlank() || extras.isNotEmpty() ||
        tPhotos.any { it != null } || tTexts.any { it.isNotBlank() }
    val draft = rememberDraft(
        kind = DraftStore.MEME,
        vm = vm,
        snapshot = {
            val has = photo != null || top.isNotBlank() || bottom.isNotBlank() || extras.isNotEmpty() ||
                tPhotos.any { it != null } || tTexts.any { it.isNotBlank() }
            if (!has) {
                null
            } else {
                val d = MemeDraft(
                    photoId = photoId, top = top, bottom = bottom, extras = extras.toList(),
                    style = style, autofit = autofit, templateId = template?.id,
                    tPhotos = tPhotos.toList(), tTexts = tTexts.toList(), slot = slot,
                    fontFamily = fontFamily, caps = caps, outlineOn = outlineOn,
                )
                DraftSnapshot(
                    DRAFT_JSON.encodeToString(d),
                    listOfNotNull(d.photoId) + d.tPhotos.filterNotNull().map { it.id },
                )
            }
        },
        restore = { json, loadImage ->
            val d = DRAFT_JSON.decodeFromString<MemeDraft>(json)
            val ok = HashSet<String>()
            (listOfNotNull(d.photoId) + d.tPhotos.filterNotNull().map { it.id }).distinct()
                .forEach { if (loadImage(it)) ok += it }
            d.photoId?.takeIf { it in ok }?.let { id ->
                photo = vm.getBitmap(id)
                photoId = id
            }
            top = d.top; bottom = d.bottom
            extras.clear(); extras.addAll(d.extras)
            style = d.style; autofit = d.autofit
            template = MEME_TEMPLATES.firstOrNull { it.id == d.templateId }
            tPhotos.clear(); tPhotos.addAll(d.tPhotos.map { p -> p?.takeIf { it.id in ok } })
            tTexts.clear(); tTexts.addAll(d.tTexts)
            slot = d.slot; fontFamily = d.fontFamily; caps = d.caps; outlineOn = d.outlineOn
        },
    )
    val guardedBack = rememberLeaveGuard(
        dirty = dirty,
        onLeave = { draft.discard(); onBack() },
        vm = vm,
        defaultName = stringResource(R.string.meme_title),
        build = build,
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(
                title = stringResource(R.string.meme_title),
                onBack = guardedBack,
                actions = {
                    TextButton(
                        onClick = {
                            if (template == null) {
                                pickPhoto()
                            } else {
                                pickingSlot = tPhotos.indexOfFirst { it == null }.takeIf { it >= 0 } ?: slot
                                pickSlot()
                            }
                        },
                    ) {
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
                emptyText = stringResource(R.string.home_open_image),
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
                OptionRow(
                    options = listOf<MemeTemplate?>(null) + MEME_TEMPLATES,
                    selected = template,
                    onSelect = { selectTemplate(it) },
                    label = { t -> if (t == null) modeLabel else templateLabels[t.id].orEmpty() },
                )
                val tpl = template
                if (tpl != null) {
                    TemplateControls(
                        template = tpl,
                        photos = tPhotos,
                        texts = tTexts,
                        slot = slot,
                        font = fontFamily,
                        caps = caps,
                        outline = outlineOn,
                        onSlot = { slot = it },
                        onPickSlot = {
                            pickingSlot = it
                            pickSlot()
                        },
                        onPhoto = { i, p -> if (i in tPhotos.indices) tPhotos[i] = p },
                        onText = { i, v -> if (i in tTexts.indices) tTexts[i] = v },
                        onFont = { fontFamily = it },
                        onCaps = { caps = it },
                        onOutline = { outlineOn = it },
                    )
                } else {
                OptionRow(
                    options = MemeStyle.entries,
                    selected = style,
                    onSelect = { style = it },
                    label = { styleNames[it].orEmpty() },
                )
                TextFieldRow(
                    label = stringResource(R.string.meme_top_text),
                    value = top,
                    onValueChange = { top = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                TextFieldRow(
                    label = stringResource(R.string.meme_bottom_text),
                    value = bottom,
                    onValueChange = { bottom = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                extras.forEachIndexed { index, value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextFieldRow(
                            label = stringResource(R.string.label_text),
                            value = value,
                            onValueChange = { extras[index] = it },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { extras.removeAt(index) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_remove),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { extras.add("") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) { Text(stringResource(R.string.meme_add_box)) }
                ToggleRow(
                    label = stringResource(R.string.meme_autofit),
                    checked = autofit,
                    onCheckedChange = { autofit = it },
                )
                }
                HorizontalDivider()
                GeneratorActions(
                    vm = vm,
                    defaultName = stringResource(R.string.meme_title),
                    onOpenEditor = { draft.discard(); onOpenEditor() },
                    enabled = if (template == null) photoId != null else tPhotos.any { it != null },
                    build = build,
                )
            }
        }
    }
}

/**
 * Selector de foto que ya devuelve el bitmap decodificado y limitado en tamano.
 *
 * Esta aqui como funcion aparte porque el selector del sistema entrega una uri y decodificarla
 * es trabajo de disco: nunca en el hilo de la interfaz.
 */
@Composable
private fun rememberMemePicker(onLoaded: (Bitmap) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // ponytail: 4096 es el limite minimo garantizado del requisito 6; el meme nunca
    // necesita mas y asi esta pantalla no depende de los ajustes.
    val maxSide = 4096
    return rememberImagePicker { uri ->
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { BitmapUtils.decodeUri(context, uri, maxSide) }.getOrNull()
            }
            if (bitmap != null) onLoaded(bitmap)
        }
    }
}

// ======================================================================================
// Montaje del documento
// ======================================================================================

/**
 * Compone el meme: la foto abajo y cada frase como capa de texto encima.
 *
 * En el estilo clasico el texto va dentro de la foto, blanco con contorno negro. En el
 * estilo de barra blanca el lienzo crece por arriba y la frase de arriba se pinta en negro
 * dentro de la barra, que es como se ven los memes "modernos" de redes.
 */
private fun buildMeme(
    photoId: String,
    photoWidth: Int,
    photoHeight: Int,
    top: String,
    bottom: String,
    extras: List<String>,
    whiteBar: Boolean,
    autofit: Boolean,
    typeface: Typeface?,
    textLayerName: String,
    imageLayerName: String,
): PixaDocument {
    val baseSize = (photoHeight * 0.11f).coerceIn(18f, 400f)
    val boxWidth = photoWidth * 0.94f
    val margin = photoHeight * MARGIN_FRACTION

    // La barra solo existe si hay algo que poner en ella, y su alto sale del propio texto.
    val barText = if (whiteBar) top.trim() else ""
    val barSpec = if (barText.isEmpty()) {
        null
    } else {
        memeSpec(barText, boxWidth, baseSize * 0.8f, autofit, typeface, onWhite = true)
    }
    val barHeight = barSpec
        ?.let { TextRenderer.measure(it, typeface).height + margin * 2f }
        ?: 0f

    val docWidth = photoWidth
    val docHeight = (photoHeight + barHeight).toInt().coerceAtLeast(photoHeight)

    val layers = ArrayList<PixaLayer>()
    layers += PixaLayer(
        name = imageLayerName,
        content = LayerContent.Raster(photoId),
        transform = Xf(tx = 0f, ty = barHeight),
    )

    fun textLayer(spec: TextSpec, y: Float): PixaLayer {
        val size = TextRenderer.measure(spec, typeface)
        return PixaLayer(
            name = textLayerName,
            content = LayerContent.Text(spec),
            transform = Xf(tx = (docWidth - size.width) / 2f, ty = y),
        )
    }

    if (barSpec != null) {
        val size = TextRenderer.measure(barSpec, typeface)
        layers += textLayer(barSpec, (barHeight - size.height) / 2f)
    }

    // En el estilo clasico la frase de arriba se pinta sobre la foto, no en la barra.
    if (!whiteBar && top.isNotBlank()) {
        val spec = memeSpec(top.trim(), boxWidth, baseSize, autofit, typeface, onWhite = false)
        layers += textLayer(spec, margin)
    }

    if (bottom.isNotBlank()) {
        val spec = memeSpec(bottom.trim(), boxWidth, baseSize, autofit, typeface, onWhite = false)
        val size = TextRenderer.measure(spec, typeface)
        layers += textLayer(spec, docHeight - size.height - margin)
    }

    // Las cajas extra se reparten por el centro de la foto; desde el editor se mueven a mano.
    extras.filter { it.isNotBlank() }.forEachIndexed { index, value ->
        val spec = memeSpec(value.trim(), boxWidth, baseSize * 0.85f, autofit, typeface, onWhite = false)
        val y = barHeight + photoHeight * (0.32f + 0.14f * index)
        layers += textLayer(spec, y.coerceAtMost(docHeight - margin))
    }

    return PixaDocument(
        width = docWidth,
        height = docHeight,
        background = CanvasBackground(color = WHITE),
        layers = layers,
        selectedLayerId = layers.lastOrNull()?.id,
    )
}

/** Texto de meme: condensada, mayusculas y, si procede, contorno negro. */
private fun memeSpec(
    text: String,
    boxWidth: Float,
    maxSize: Float,
    autofit: Boolean,
    typeface: Typeface?,
    onWhite: Boolean,
): TextSpec {
    val size = if (autofit) {
        TextRenderer.fitTextSize(
            text = text.uppercase(),
            typeface = typeface,
            maxWidth = boxWidth,
            maxSize = maxSize,
        )
    } else {
        maxSize
    }
    return TextSpec(
        text = text,
        font = FontRef(FontRepository.MEME_FAMILY, FontRef.Source.GOOGLE),
        sizePx = size,
        color = if (onWhite) BLACK else WHITE,
        allCaps = true,
        boxWidth = boxWidth,
        outlineWidth = if (onWhite) 0f else size * 0.07f,
        outlineColor = BLACK,
    )
}
