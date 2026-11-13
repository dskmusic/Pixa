package com.dskmusic.pixa.ui.collage

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.collage.Bubble
import com.dskmusic.pixa.core.collage.BubbleDraw
import com.dskmusic.pixa.core.collage.BubbleStyle
import com.dskmusic.pixa.core.collage.CollageBgKind
import com.dskmusic.pixa.core.collage.CollageBlur
import com.dskmusic.pixa.core.collage.CollageDraw
import com.dskmusic.pixa.core.collage.CollageGeometry
import com.dskmusic.pixa.core.collage.CollageLayout
import com.dskmusic.pixa.core.collage.CollageLayouts
import com.dskmusic.pixa.core.collage.CollageNames
import com.dskmusic.pixa.core.collage.CollagePhoto
import com.dskmusic.pixa.core.collage.CollageSpec
import com.dskmusic.pixa.core.collage.SlotShape
import com.dskmusic.pixa.core.image.Blur
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.EditorMessages
import com.dskmusic.pixa.ui.common.EmptyState
import com.dskmusic.pixa.ui.common.FullscreenImageDialog
import com.dskmusic.pixa.ui.common.GRADIENT_PRESETS
import com.dskmusic.pixa.ui.common.GeneratorActions
import com.dskmusic.pixa.ui.common.GradientSwatch
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.core.ml.FaceFinder
import com.dskmusic.pixa.core.project.DraftStore
import com.dskmusic.pixa.ui.common.DraftSnapshot
import com.dskmusic.pixa.ui.common.panelSwipeToToggle
import com.dskmusic.pixa.ui.common.rememberDraft
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import androidx.compose.material.icons.filled.Face
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.editor.EditorLabels
import com.dskmusic.pixa.core.image.Effects
import com.dskmusic.pixa.core.model.EffectKind
import com.dskmusic.pixa.core.model.EffectSpec
import androidx.compose.material3.FilterChip
import kotlinx.coroutines.delay
import com.dskmusic.pixa.ui.common.PixaToastHost
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.formatValue
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.common.rememberLeaveGuard
import com.dskmusic.pixa.ui.common.rememberMultiImagePicker
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.panels.ColorRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

private const val MAX_PHOTOS = 9
private const val DECODE_SIDE = 1600

/** Lado mayor de las copias de las fotos que se usan en la vista previa. */
private const val PREVIEW_PHOTO_SIDE = 900
private const val WHITE = 0xFFFFFFFF.toInt()

/** Proporciones del lienzo: etiqueta y ancho / alto. */
private val RATIOS = listOf(
    "1:1" to 1f, "4:5" to 0.8f, "3:4" to 0.75f, "2:3" to 2f / 3f, "9:16" to 9f / 16f,
    "16:9" to 16f / 9f, "3:2" to 1.5f, "4:3" to 4f / 3f, "A4" to 0.7071f,
)

private val SIZES = listOf(1080, 2160, 3240)

private const val MAX_FACE_BUBBLES = 12

private val DRAFT_JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/** Estado completo del collage / comic para recuperarlo tras un cierre inesperado. */
@Serializable
private class CollageDraft(
    val photos: List<CollagePhoto>,
    val count: Int,
    val layoutIndex: Int,
    val ratioIndex: Int,
    val sizeIndex: Int,
    val gap: Float,
    val margin: Float,
    val radius: Float,
    val border: Float,
    val borderColor: Int,
    val shadow: Boolean,
    val bgKind: CollageBgKind,
    val bgColor: Int,
    val gradientIndex: Int,
    val gStart: Int,
    val gEnd: Int,
    val gAngle: Float,
    val gPosStart: Float,
    val gPosEnd: Float,
    val comic: Boolean,
    val comicStyled: Boolean,
    val fxKind: EffectKind,
    val fxAmount: Float,
    val fxLevels: Int,
    val bubbles: List<Bubble>,
)


/** Efectos que imitan el cómic y el arte pop, en el orden en que salen en el panel. */
private val COMIC_EFFECTS = listOf(
    EffectKind.NONE, EffectKind.COMIC, EffectKind.HALFTONE, EffectKind.POSTERIZE,
    EffectKind.SOLARIZE, EffectKind.SKETCH, EffectKind.THRESHOLD, EffectKind.DUOTONE,
    EffectKind.OIL, EffectKind.BW,
)

/** Tipografias de Google Fonts para los bocadillos; la primera es la de comic por defecto. */
private val COMIC_FONTS = listOf(
    "Comic Neue", "Bangers", "Patrick Hand", "Permanent Marker", "Luckiest Guy", "Caveat", "Roboto",
)

private val GRADIENTS = GRADIENT_PRESETS

/**
 * Collage (mejora pedida por el usuario): 45 disenos de 1 a 9 fotos, proporciones de
 * lienzo, separacion, margen, esquinas, borde, sombra y fondo (color, degradado o la propia
 * foto desenfocada).
 *
 * Todo se monta como capas normales del editor (marco redondeado + foto recortada al marco),
 * asi que despues de "Abrir en el editor" cada foto se puede seguir moviendo dentro de su
 * hueco. En la vista previa se toca un hueco para elegirlo, se arrastra para encuadrar y se
 * pellizca para acercar.
 */
@Composable
fun CollageScreen(
    editor: EditorViewModel,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val vm = editor
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by context.pixa.settings.settings.collectAsStateWithLifecycle(PixaSettings())

    val photos = remember { mutableStateListOf<CollagePhoto>() }
    var count by remember { mutableStateOf(4) }
    var layoutIndex by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(-1) }
    var ratioIndex by remember { mutableStateOf(0) }
    var sizeIndex by remember { mutableStateOf(1) }
    var gap by remember { mutableStateOf(14f) }
    var margin by remember { mutableStateOf(28f) }
    var radius by remember { mutableStateOf(24f) }
    var border by remember { mutableStateOf(0f) }
    var borderColor by remember { mutableStateOf(WHITE) }
    var shadow by remember { mutableStateOf(false) }
    var bgKind by remember { mutableStateOf(CollageBgKind.SOLID) }
    var bgColor by remember { mutableStateOf(WHITE) }
    var gradientIndex by remember { mutableStateOf(0) }
    // Degradado personalizado: es el ultimo de los 14 huecos (indice GRADIENTS.size).
    var gStart by remember { mutableStateOf(0xFFFF9A9E.toInt()) }
    var gEnd by remember { mutableStateOf(0xFF3F5EFB.toInt()) }
    var gAngle by remember { mutableStateOf(90f) }
    var gPosStart by remember { mutableStateOf(0f) }
    var gPosEnd by remember { mutableStateOf(1f) }
    val customGradient = gradientIndex >= GRADIENTS.size
    var blur by remember { mutableStateOf<CollageBlur?>(null) }
    // Modo comic: los mismos huecos, mas bocadillos encima.
    var comic by remember { mutableStateOf(false) }
    var comicStyled by remember { mutableStateOf(false) }
    // Efecto aplicado a todas las fotos (cómic, trama, solarizado...). Cada CollagePhoto
    // guarda la original en srcId y la version con efecto en id.
    var fx by remember { mutableStateOf(EffectSpec(kind = EffectKind.NONE, amount = 0.6f, levels = 4)) }
    val bubbles = remember { mutableStateListOf<Bubble>() }
    var bubbleSel by remember { mutableStateOf(-1) }
    val fonts = remember(context) { context.pixa.fonts }
    // Sube cada vez que una fuente termina de descargarse: repinta la vista previa.
    val fontsLoaded by fonts.loaded.collectAsStateWithLifecycle()
    val bubbleFont: (String) -> android.graphics.Typeface? = { fonts.cached(FontRef(it, FontRef.Source.GOOGLE)) }
    // La vista previa vive en un State que solo lee el propio composable de la vista previa:
    // asi cada fotograma de un gesto no recompone toda la pantalla (sliders incluidos).
    val previewState = remember { mutableStateOf<Bitmap?>(null) }
    val hasPreview by remember { derivedStateOf { previewState.value != null } }
    // Edicion en vivo de un hueco mientras el dedo esta puesto. No es un State de
    // composicion: solo lo lee el flujo de la vista previa. Se vuelca a `photos` al soltar.
    val live = remember { LiveEdit() }
    var liveTick by remember { mutableStateOf(0) }
    // Copias reducidas de las fotos para la vista previa y dos bitmaps que se alternan.
    val previewCache = remember { java.util.concurrent.ConcurrentHashMap<String, Bitmap>() }
    val pool = remember { arrayOfNulls<Bitmap>(2) }
    var fullscreen by remember { mutableStateOf(false) }

    val layouts = remember(count) { CollageLayouts.forCount(count) }

    // El diseno se resuelve aqui dentro y no fuera: el flujo de la vista previa se crea una
    // sola vez y debe leer siempre el estado actual, no el de la primera composicion.
    fun bubblesNow(): List<Bubble> {
        if (!comic) return emptyList()
        val list = bubbles.toMutableList()
        val lb = live.bubble
        if (lb != null && live.bubbleIndex in list.indices) list[live.bubbleIndex] = lb
        return list
    }

    fun currentSpec() = CollageSpec(
        layout = CollageLayouts.forCount(count).let { it[layoutIndex.coerceIn(0, it.lastIndex)] },
        ratio = RATIOS[ratioIndex].second,
        longSide = SIZES[sizeIndex],
        gap = gap,
        margin = margin,
        radius = radius,
        border = border,
        borderColor = borderColor,
        shadow = shadow,
        bgKind = bgKind,
        bgColor = bgColor,
        // Se decide aqui y no con un val de la composicion: el flujo de la vista previa
        // vive desde la primera composicion y vería un valor viejo.
        bgGradient = if (gradientIndex >= GRADIENTS.size) listOf(gStart, gEnd) else GRADIENTS[gradientIndex],
        bgAngle = if (gradientIndex >= GRADIENTS.size) gAngle else 90f,
        bgStart = if (gradientIndex >= GRADIENTS.size) gPosStart else 0f,
        bgEnd = if (gradientIndex >= GRADIENTS.size) gPosEnd else 1f,
        blur = blur,
        bubbles = bubblesNow(),
        fonts = bubbleFont,
    )

    val names = CollageNames(
        photo = { context.getString(R.string.collage_layer_photo, it) },
        frame = { context.getString(R.string.collage_layer_frame, it) },
        border = { context.getString(R.string.collage_layer_border, it) },
        background = stringResource(R.string.collage_layer_bg),
        bubble = { context.getString(R.string.comic_bubble_n, it) },
    )

    val spec = currentSpec()
    val (canvasW, canvasH) = CollageGeometry.canvasSize(spec)
    val slotRects = CollageGeometry.slotRects(spec)

    // ---- Seleccion de fotos -----------------------------------------------------------

    /** La foto con el efecto [spec] aplicado sobre su original (sin efecto: la original). */
    suspend fun withFx(p: CollagePhoto, spec: EffectSpec): CollagePhoto {
        if (p.isEmpty) return p
        if (spec.kind == EffectKind.NONE) return p.copy(id = p.srcId)
        val src = vm.getBitmap(p.srcId) ?: return p
        val out = withContext(Dispatchers.Default) {
            runCatching { Effects.apply(src, spec) }.getOrNull()
        } ?: return p
        return p.copy(id = vm.putBitmap(out))
    }

    suspend fun load(uri: android.net.Uri): CollagePhoto? {
        val bmp = withContext(Dispatchers.IO) {
            runCatching { BitmapUtils.decodeUri(context, uri, DECODE_SIDE) }.getOrNull()
        } ?: return null
        return withFx(CollagePhoto(id = vm.putBitmap(bmp), width = bmp.width, height = bmp.height), fx)
    }

    // Al cambiar el efecto o su intensidad se rehacen las fotos (con una pausa para no
    // procesar cada paso del deslizador).
    /**
     * Detecta las caras de cada foto y pone un bocadillo de prueba por cara: encima de ella
     * (o debajo si no cabe) y con la cola apuntando a la cara. Despues se editan a mano.
     */
    fun addFaceBubbles() {
        scope.launch {
            var added = 0
            val rects = slotRects
            for ((i, p) in photos.toList().withIndex()) {
                if (p.isEmpty || i !in rects.indices || bubbles.size >= MAX_FACE_BUBBLES) continue
                val bmp = vm.getBitmap(p.id) ?: continue
                val faces = FaceFinder.detect(bmp, marginPercent = 0f)
                    .sortedByDescending { it.area.width() * it.area.height() }.take(3)
                val slot = rects[i]
                val m = CollageGeometry.photoTransform(slot, p).toMatrix(p.width, p.height)
                val scale = CollageGeometry.place(slot, p).scale
                for (face in faces) {
                    val c = floatArrayOf(face.area.exactCenterX(), face.area.exactCenterY())
                    m.mapPoints(c)
                    if (!slot.contains(c[0], c[1])) continue
                    val r = maxOf(face.area.width(), face.area.height()) / 2f * scale
                    val bw = 0.28f
                    val bx = (c[0] / canvasW).coerceIn(bw / 2f + 0.02f, 1f - bw / 2f - 0.02f)
                    val above = c[1] - r - 0.08f * canvasH > 0.04f * canvasH
                    val by = (if (above) c[1] - r - 0.07f * canvasH else c[1] + r + 0.07f * canvasH) / canvasH
                    val ty = (if (above) c[1] - r * 0.5f else c[1] + r * 0.5f)
                    bubbles.add(
                        Bubble(
                            text = "Lorem ipsum", // texto de prueba: se edita despues
                            cx = bx,
                            cy = by.coerceIn(0.05f, 0.95f),
                            width = bw,
                            tailX = c[0] / canvasW - bx,
                            tailY = ty / canvasH - by.coerceIn(0.05f, 0.95f),
                        ),
                    )
                    added++
                    if (bubbles.size >= MAX_FACE_BUBBLES) break
                }
            }
            if (added > 0) bubbleSel = bubbles.lastIndex else snackbar.showSnackbar(context.getString(R.string.comic_no_faces))
        }
    }

    LaunchedEffect(fx) {
        delay(300)
        val snapshot = photos.toList()
        val done = snapshot.map { withFx(it, fx) }
        done.forEachIndexed { i, np ->
            if (i in photos.indices && photos[i].srcId == snapshot[i].srcId && photos[i].id != np.id) {
                photos[i] = photos[i].copy(id = np.id)
            }
        }
    }

    val addPhotos = rememberMultiImagePicker(MAX_PHOTOS) { uris ->
        scope.launch {
            val wasEmpty = photos.none { !it.isEmpty }
            uris.forEach { uri ->
                val fresh = load(uri) ?: return@forEach
                val hole = photos.indexOfFirst { it.isEmpty }
                if (hole >= 0) photos[hole] = fresh else if (photos.size < MAX_PHOTOS) photos.add(fresh)
            }
            if (photos.any { !it.isEmpty } && (wasEmpty || photos.size > count)) {
                count = photos.size.coerceIn(1, MAX_PHOTOS)
                layoutIndex = 0
            }
        }
    }
    val replacePhoto = rememberImagePicker { uri ->
        scope.launch {
            val fresh = load(uri) ?: return@launch
            val i = selected
            when {
                // Hueco tocado: la foto va justo ahi, aunque queden huecos libres antes.
                i in photos.indices -> photos[i] = fresh
                i in 0 until MAX_PHOTOS -> {
                    while (photos.size < i) photos.add(CollagePhoto.EMPTY)
                    photos.add(fresh)
                }
                else -> {
                    val hole = photos.indexOfFirst { it.isEmpty }
                    if (hole >= 0) photos[hole] = fresh else if (photos.size < MAX_PHOTOS) photos.add(fresh)
                }
            }
        }
    }

    // ---- Fondo desenfocado: se genera una vez por foto / proporcion -------------------

    val firstId = photos.firstOrNull { !it.isEmpty }?.id
    LaunchedEffect(bgKind, firstId, ratioIndex) {
        if (bgKind != CollageBgKind.BLUR || firstId == null) {
            blur = null
            return@LaunchedEffect
        }
        val src = vm.getBitmap(firstId) ?: return@LaunchedEffect
        val ratio = RATIOS[ratioIndex].second
        val made = withContext(Dispatchers.Default) {
            val long = 384
            val w = if (ratio >= 1f) long else max(16, (long * ratio).toInt())
            val h = if (ratio >= 1f) max(16, (long / ratio).toInt()) else long
            val out = BitmapUtils.create(w, h)
            val s = max(w / src.width.toFloat(), h / src.height.toFloat())
            val m = Matrix().apply {
                setScale(s, s)
                postTranslate((w - src.width * s) / 2f, (h - src.height * s) / 2f)
            }
            AndroidCanvas(out).drawBitmap(src, m, null)
            Blur.blur(out, 14f)
        }
        blur = CollageBlur(vm.putBitmap(made), made.width, made.height)
    }

    // ---- Vista previa ----------------------------------------------------------------

    val build: () -> PixaDocument? = {
        if (photos.none { !it.isEmpty }) null else CollageGeometry.build(currentSpec(), photos.toList(), names, vm::putBitmap)
    }

    // La vista previa se dibuja directa sobre un bitmap (CollageDraw), sin montar el
    // documento de capas: asi arrastrar y pellizcar un hueco es fluido. El documento solo se
    // monta al exportar o abrir en el editor.
    fun previewBitmap(id: String): Bitmap? {
        previewCache[id]?.let { if (!it.isRecycled) return it }
        val src = vm.getBitmap(id) ?: return null
        val small = if (max(src.width, src.height) > PREVIEW_PHOTO_SIDE) {
            BitmapUtils.thumbnail(src, PREVIEW_PHOTO_SIDE)
        } else {
            src
        }
        previewCache[id] = small
        return small
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            // Se leen todos los estados que cambian el resultado: cualquiera repinta.
            val list = photos.toList().toMutableList()
            val lp = live.photo
            if (lp != null && live.index in list.indices) list[live.index] = lp
            val tick = liveTick + fontsLoaded
            Triple(currentSpec(), list, (lp != null || live.bubble != null) to tick)
        }.collectLatest { (s, list, living) ->
            // Sin fotos tambien se pinta: fondo y huecos vacios desde el principio.
            // Se pinta en el bitmap que no se esta mostrando y despues se intercambian.
            val shown = previewState.value
            val idx = if (pool[0] != null && pool[0] === shown) 1 else 0
            val bmp = withContext(Dispatchers.Default) {
                runCatching {
                    CollageDraw.render(
                        s, list, { id -> previewBitmap(id) },
                        // Mientras el dedo esta puesto se baja la resolucion; al soltar vuelve.
                        if (living.first) 450_000 else 1_000_000,
                        reuse = pool[idx],
                    )
                }.getOrNull()
            }
            if (bmp != null) {
                pool[idx] = bmp
                previewState.value = bmp
            }
        }
    }

    val modeComic = stringResource(R.string.collage_mode_comic)
    val modeCollage = stringResource(R.string.collage_mode_collage)
    val styleNames = mapOf(
        BubbleStyle.SPEECH to stringResource(R.string.comic_style_speech),
        BubbleStyle.THOUGHT to stringResource(R.string.comic_style_thought),
        BubbleStyle.SHOUT to stringResource(R.string.comic_style_shout),
        BubbleStyle.WHISPER to stringResource(R.string.comic_style_whisper),
        BubbleStyle.CAPTION to stringResource(R.string.comic_style_caption),
        BubbleStyle.BOX to stringResource(R.string.comic_style_box),
    )
    val defaultBubbleText = stringResource(R.string.comic_default_text)
    fun addBubble() {
        val n = bubbles.size
        bubbles.add(
            Bubble(
                text = defaultBubbleText,
                cx = if (n % 2 == 0) 0.35f else 0.65f,
                cy = 0.18f + 0.12f * (n % 5),
            ),
        )
        bubbleSel = bubbles.lastIndex
        selected = -1
    }

    EditorMessages(vm, snackbar)
    // Borrador: si la app se cierra, Inicio ofrece volver aqui con todo como estaba.
    val draft = rememberDraft(
        kind = DraftStore.COLLAGE,
        vm = vm,
        snapshot = {
            if (photos.none { !it.isEmpty } && bubbles.isEmpty()) {
                null
            } else {
                val d = CollageDraft(
                    photos = photos.toList(), count = count, layoutIndex = layoutIndex,
                    ratioIndex = ratioIndex, sizeIndex = sizeIndex, gap = gap, margin = margin,
                    radius = radius, border = border, borderColor = borderColor, shadow = shadow,
                    bgKind = bgKind, bgColor = bgColor, gradientIndex = gradientIndex,
                    gStart = gStart, gEnd = gEnd, gAngle = gAngle, gPosStart = gPosStart,
                    gPosEnd = gPosEnd, comic = comic, comicStyled = comicStyled,
                    fxKind = fx.kind, fxAmount = fx.amount, fxLevels = fx.levels,
                    bubbles = bubbles.toList(),
                )
                DraftSnapshot(
                    DRAFT_JSON.encodeToString(d),
                    d.photos.flatMap { listOf(it.id, it.srcId) }.filter { it.isNotEmpty() },
                )
            }
        },
        restore = { json, loadImage ->
            val d = DRAFT_JSON.decodeFromString<CollageDraft>(json)
            val ok = HashSet<String>()
            d.photos.flatMap { listOf(it.id, it.srcId) }.filter { it.isNotEmpty() }.distinct()
                .forEach { if (loadImage(it)) ok += it }
            count = d.count; layoutIndex = d.layoutIndex; ratioIndex = d.ratioIndex
            sizeIndex = d.sizeIndex; gap = d.gap; margin = d.margin; radius = d.radius
            border = d.border; borderColor = d.borderColor; shadow = d.shadow
            bgKind = d.bgKind; bgColor = d.bgColor; gradientIndex = d.gradientIndex
            gStart = d.gStart; gEnd = d.gEnd; gAngle = d.gAngle
            gPosStart = d.gPosStart; gPosEnd = d.gPosEnd
            comic = d.comic; comicStyled = d.comicStyled
            photos.clear()
            photos.addAll(d.photos.map { if (it.isEmpty || (it.id in ok && it.srcId in ok)) it else CollagePhoto.EMPTY })
            bubbles.clear(); bubbles.addAll(d.bubbles)
            fx = EffectSpec(kind = d.fxKind, amount = d.fxAmount, levels = d.fxLevels)
        },
    )
    val guardedBack = rememberLeaveGuard(
        dirty = photos.any { !it.isEmpty },
        onLeave = { draft.discard(); onBack() },
        vm = vm,
        defaultName = stringResource(R.string.collage_title),
        build = build,
    )
    // Panel inferior minimizado: deja la vista previa a todo el alto para encuadrar mejor.
    var panelMin by remember { mutableStateOf(false) }

    // ---- Edicion del hueco elegido ---------------------------------------------------

    fun editSelected(block: (CollagePhoto) -> CollagePhoto) {
        val i = selected
        if (i in photos.indices) photos[i] = block(photos[i])
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(
                title = stringResource(R.string.collage_title),
                onBack = guardedBack,
                actions = {
                    IconButton(onClick = { fullscreen = true }, enabled = hasPreview) {
                        Icon(Icons.Filled.Fullscreen, stringResource(R.string.collage_fullscreen))
                    }
                    TextButton(onClick = addPhotos, enabled = photos.size < MAX_PHOTOS) {
                        Icon(Icons.Filled.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.collage_add_photos),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { PixaToastHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CollagePreview(
                bitmapState = previewState,
                canvasW = canvasW,
                canvasH = canvasH,
                rects = slotRects,
                filled = slotRects.indices.map { photos.getOrNull(it)?.isEmpty == false },
                selected = selected,
                bubbleBoxes = if (comic) bubbles.map { BubbleDraw.bounds(it, canvasW, canvasH, bubbleFont) } else emptyList(),
                bubbleTips = if (comic) {
                    bubbles.map { BubbleDraw.tip(it, canvasW, canvasH, bubbleFont).let { t -> Offset(t.first, t.second) } }
                } else {
                    emptyList()
                },
                bubbleSel = bubbleSel,
                onSelectBubble = { i ->
                    bubbleSel = i
                    if (i >= 0) selected = -1
                },
                onBubbleStart = { tail ->
                    val i = bubbleSel
                    if (i in bubbles.indices) {
                        live.bubbleIndex = i
                        live.bubble = bubbles[i]
                        live.tail = tail
                    }
                },
                onBubbleLive = { dx, dy, z, rot ->
                    live.bubble?.let { b ->
                        live.bubble = if (live.tail) {
                            // La cola vive en el marco del bocadillo: se deshace su giro.
                            val a = -b.rotation * PI.toFloat() / 180f
                            val ux = dx * cos(a) - dy * sin(a)
                            val uy = dx * sin(a) + dy * cos(a)
                            b.copy(tailX = b.tailX + ux / canvasW, tailY = b.tailY + uy / canvasH)
                        } else {
                            b.copy(
                                cx = (b.cx + dx / canvasW).coerceIn(0f, 1f),
                                cy = (b.cy + dy / canvasH).coerceIn(0f, 1f),
                                width = (b.width * z).coerceIn(0.12f, 0.95f),
                                rotation = normDeg(b.rotation + rot),
                            )
                        }
                        liveTick++
                    }
                },
                onBubbleEnd = {
                    val i = live.bubbleIndex
                    live.bubble?.let { if (i in bubbles.indices) bubbles[i] = it }
                    live.bubble = null
                    live.bubbleIndex = -1
                    liveTick++
                },
                onSelect = { i ->
                    selected = i
                    if (i >= 0) bubbleSel = -1
                    // Un hueco vacio se rellena al tocarlo: es lo que se espera de un "+".
                    if (i >= 0 && photos.getOrNull(i)?.isEmpty != false) replacePhoto()
                },
                onLiveStart = {
                    val i = selected
                    if (i in photos.indices) {
                        live.index = i
                        live.photo = photos[i]
                    }
                },
                onLive = { dx, dy, z ->
                    val i = live.index
                    val p = live.photo
                    val slot = slotRects.getOrNull(i)
                    if (p != null && slot != null) {
                        var q = if (z != 1f) p.copy(zoom = (p.zoom * z).coerceIn(1f, 4f)) else p
                        val pl = CollageGeometry.place(slot, q)
                        q = q.copy(
                            panX = if (pl.overflowX > 0.5f) (q.panX + dx / pl.overflowX).coerceIn(-1f, 1f) else 0f,
                            panY = if (pl.overflowY > 0.5f) (q.panY + dy / pl.overflowY).coerceIn(-1f, 1f) else 0f,
                        )
                        live.photo = q
                        liveTick++
                    }
                },
                onLiveEnd = {
                    val i = live.index
                    val p = live.photo
                    if (p != null && i in photos.indices) photos[i] = p
                    live.photo = null
                    live.index = -1
                    liveTick++
                },
                onSwap = { a, b ->
                    if (a in photos.indices && b in photos.indices && a != b) {
                        val t = photos[a]
                        photos[a] = photos[b]
                        photos[b] = t
                        selected = b
                    }
                },
                emptyText = stringResource(R.string.collage_empty),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            HorizontalDivider()
            Column(
                modifier = Modifier.fillMaxWidth()
                    .then(if (panelMin) Modifier else Modifier.weight(1f))
                    .verticalScroll(rememberScrollState(), enabled = !panelMin),
            ) {
                Row(
                    modifier = Modifier.panelSwipeToToggle(
                        onSwipeDown = { panelMin = true },
                        onSwipeUp = { panelMin = false },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Box(Modifier.weight(1f)) {
                OptionRow(
                    options = listOf(false, true),
                    selected = comic,
                    onSelect = { on ->
                        comic = on
                        if (on && !comicStyled) {
                            // Primera vez: aspecto de vineta (borde negro, esquinas rectas).
                            comicStyled = true
                            // Un cómic se piensa en hoja: A4 por defecto (se puede cambiar).
                            ratioIndex = RATIOS.indexOfFirst { it.first == "A4" }.coerceAtLeast(0)
                            gap = 16f; margin = 24f; radius = 0f
                            border = 6f; borderColor = 0xFF000000.toInt()
                            bgKind = CollageBgKind.SOLID; bgColor = WHITE
                        }
                        if (!on) bubbleSel = -1
                    },
                    label = { if (it) modeComic else modeCollage },
                )
                }
                // Papelera junto a minimizar: borra el bocadillo elegido (modo comic).
                if (comic) {
                    IconButton(
                        onClick = {
                            if (bubbleSel in bubbles.indices) {
                                bubbles.removeAt(bubbleSel)
                                bubbleSel = -1
                            }
                        },
                        enabled = bubbleSel in bubbles.indices,
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { panelMin = !panelMin }) {
                    Icon(
                        if (panelMin) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                        contentDescription = stringResource(
                            if (panelMin) R.string.action_maximize else R.string.action_minimize,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
                if (!panelMin) {
                Text(
                    stringResource(if (comic) R.string.comic_hint else R.string.collage_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                // Hueco elegido.
                val sel = photos.getOrNull(selected)?.takeIf { !it.isEmpty }
                if (sel != null) {
                    SectionTitle(stringResource(R.string.collage_selected, selected + 1))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = replacePhoto) {
                            Icon(Icons.Filled.PhotoLibrary, stringResource(R.string.action_replace))
                        }
                        IconButton(onClick = { editSelected { it.copy(quarterTurns = (it.quarterTurns + 1) % 4, panX = 0f, panY = 0f) } }) {
                            Icon(Icons.Filled.RotateRight, stringResource(R.string.collage_rotate))
                        }
                        IconButton(onClick = { editSelected { it.copy(flipH = !it.flipH) } }) {
                            Icon(Icons.Filled.Flip, stringResource(R.string.collage_flip))
                        }
                        IconButton(
                            onClick = {
                                val i = selected
                                if (i > 0) {
                                    val t = photos[i]; photos[i] = photos[i - 1]; photos[i - 1] = t
                                    selected = i - 1
                                }
                            },
                            enabled = selected > 0,
                        ) { Icon(Icons.Filled.SwapHoriz, stringResource(R.string.collage_move_earlier)) }
                        IconButton(
                            onClick = {
                                val i = selected
                                if (i < photos.lastIndex) {
                                    val t = photos[i]; photos[i] = photos[i + 1]; photos[i + 1] = t
                                    selected = i + 1
                                }
                            },
                            enabled = selected < photos.lastIndex,
                        ) { Icon(Icons.Filled.SwapHoriz, stringResource(R.string.collage_move_later)) }
                        IconButton(onClick = { editSelected { it.copy(zoom = 1f, panX = 0f, panY = 0f) } }) {
                            Icon(Icons.Filled.CenterFocusStrong, stringResource(R.string.collage_reset_framing))
                        }
                        IconButton(onClick = {
                            val i = selected
                            if (i in photos.indices) {
                                photos[i] = CollagePhoto.EMPTY
                                selected = -1
                            }
                        }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_remove)) }
                    }
                    SliderRow(
                        label = stringResource(R.string.collage_zoom),
                        value = sel.zoom,
                        onValueChange = { z -> editSelected { it.copy(zoom = z) } },
                        valueRange = 1f..4f,
                        valueText = "${formatValue(sel.zoom)}x",
                        defaultValue = 1f,
                    )
                }

                // Efecto de las fotos (cómic, trama de puntos, solarizado...).
                SectionTitle(stringResource(R.string.collage_photo_effect))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    COMIC_EFFECTS.forEach { kind ->
                        FilterChip(
                            selected = fx.kind == kind,
                            onClick = { fx = fx.copy(kind = kind) },
                            label = { Text(stringResource(EditorLabels.effect(kind))) },
                        )
                    }
                }
                if (fx.kind != EffectKind.NONE && fx.kind != EffectKind.POSTERIZE &&
                    fx.kind != EffectKind.INVERT && fx.kind != EffectKind.DUOTONE
                ) {
                    SliderRow(
                        label = stringResource(R.string.label_amount),
                        value = fx.amount,
                        onValueChange = { fx = fx.copy(amount = it) },
                        valueText = formatPercent(fx.amount),
                        defaultValue = 0.6f,
                    )
                }

                // Diseno.
                SectionTitle(stringResource(R.string.collage_layout))
                OptionRow(
                    options = CollageLayouts.counts,
                    selected = count,
                    onSelect = {
                        count = it
                        layoutIndex = 0
                        selected = -1
                    },
                    label = { "$it" },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    layouts.forEachIndexed { i, l ->
                        LayoutThumb(l, selected = i == layoutIndex.coerceIn(0, layouts.lastIndex)) {
                            layoutIndex = i
                        }
                    }
                }

                // Bocadillos (modo comic).
                if (comic) {
                    // Anadir y borrar el bocadillo elegido, a mano en la propia cabecera.
                    SectionTitle(
                        stringResource(R.string.comic_bubbles),
                        extra = {
                            IconButton(onClick = { addBubble() }) {
                                Icon(Icons.Filled.Add, stringResource(R.string.comic_add_bubble))
                            }
                            IconButton(
                                onClick = {
                                    if (bubbleSel in bubbles.indices) {
                                        bubbles.removeAt(bubbleSel)
                                        bubbleSel = -1
                                    }
                                },
                                enabled = bubbleSel in bubbles.indices,
                            ) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = {
                            val n = bubbles.size
                            bubbles.add(
                                Bubble(
                                    text = defaultBubbleText,
                                    cx = if (n % 2 == 0) 0.35f else 0.65f,
                                    cy = 0.18f + 0.12f * (n % 5),
                                ),
                            )
                            bubbleSel = bubbles.lastIndex
                            selected = -1
                        }) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.comic_add_bubble),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        TextButton(
                            onClick = { addFaceBubbles() },
                            enabled = photos.any { !it.isEmpty },
                        ) {
                            Icon(Icons.Filled.Face, null, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.comic_detect_faces),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    if (bubbles.isNotEmpty()) {
                        OptionRow(
                            options = bubbles.indices.toList(),
                            selected = bubbleSel.takeIf { it >= 0 },
                            onSelect = { bubbleSel = it; selected = -1 },
                            label = { (it + 1).toString() },
                        )
                    }
                    val bb = bubbles.getOrNull(bubbleSel)
                    if (bb != null) {
                        val bi = bubbleSel
                        fun edit(block: (Bubble) -> Bubble) {
                            if (bi in bubbles.indices) bubbles[bi] = block(bubbles[bi])
                        }
                        TextFieldRow(
                            label = stringResource(R.string.label_text),
                            value = bb.text,
                            onValueChange = { v -> edit { it.copy(text = v) } },
                            singleLine = false,
                        )
                        OptionRow(
                            options = BubbleStyle.entries,
                            selected = bb.style,
                            onSelect = { st -> edit { it.copy(style = st) } },
                            label = { styleNames[it].orEmpty() },
                        )
                        OptionRow(
                            options = COMIC_FONTS,
                            selected = bb.font,
                            onSelect = { f -> edit { it.copy(font = f) } },
                            label = { it },
                        )
                        SliderRow(
                            label = stringResource(R.string.comic_rotation),
                            value = bb.rotation,
                            onValueChange = { v -> edit { it.copy(rotation = v) } },
                            valueRange = -180f..180f,
                            valueText = bb.rotation.roundToInt().toString() + "°",
                            defaultValue = 0f,
                        )
                        SliderRow(
                            label = stringResource(R.string.label_size),
                            value = bb.width,
                            onValueChange = { v -> edit { it.copy(width = v) } },
                            valueRange = 0.12f..0.95f,
                            valueText = (bb.width * 100).roundToInt().toString() + "%",
                            defaultValue = 0.4f,
                        )
                        SliderRow(
                            label = stringResource(R.string.comic_text_size),
                            value = bb.textSize,
                            onValueChange = { v -> edit { it.copy(textSize = v) } },
                            valueRange = 16f..140f,
                            valueText = formatValue(bb.textSize),
                            defaultValue = 44f,
                        )
                        SliderRow(
                            label = stringResource(R.string.bubble_pad_top),
                            value = bb.padTop,
                            onValueChange = { v -> edit { it.copy(padTop = v) } },
                            valueRange = 0f..120f,
                            valueText = formatValue(bb.padTop),
                            defaultValue = 0f,
                        )
                        SliderRow(
                            label = stringResource(R.string.bubble_pad_bottom),
                            value = bb.padBottom,
                            onValueChange = { v -> edit { it.copy(padBottom = v) } },
                            valueRange = 0f..120f,
                            valueText = formatValue(bb.padBottom),
                            defaultValue = 0f,
                        )
                        SliderRow(
                            label = stringResource(R.string.bubble_pad_left),
                            value = bb.padLeft,
                            onValueChange = { v -> edit { it.copy(padLeft = v) } },
                            valueRange = 0f..120f,
                            valueText = formatValue(bb.padLeft),
                            defaultValue = 0f,
                        )
                        SliderRow(
                            label = stringResource(R.string.bubble_pad_right),
                            value = bb.padRight,
                            onValueChange = { v -> edit { it.copy(padRight = v) } },
                            valueRange = 0f..120f,
                            valueText = formatValue(bb.padRight),
                            defaultValue = 0f,
                        )
                        SliderRow(
                            label = stringResource(R.string.comic_outline),
                            value = bb.strokeWidth,
                            onValueChange = { v -> edit { it.copy(strokeWidth = v) } },
                            valueRange = 0f..20f,
                            valueText = formatValue(bb.strokeWidth),
                            defaultValue = 5f,
                        )
                        ColorRow(
                            label = stringResource(R.string.comic_text_color),
                            color = bb.textColor, settings = settings, withAlpha = false,
                            onPick = { c -> edit { it.copy(textColor = c) } },
                        )
                        ColorRow(
                            label = stringResource(R.string.comic_fill_color),
                            color = bb.fillColor, settings = settings, withAlpha = false,
                            onPick = { c -> edit { it.copy(fillColor = c) } },
                        )
                        ColorRow(
                            label = stringResource(R.string.comic_outline),
                            color = bb.strokeColor, settings = settings, withAlpha = false,
                            onPick = { c -> edit { it.copy(strokeColor = c) } },
                        )
                        ToggleRow(
                            label = stringResource(R.string.text_bold),
                            checked = bb.bold,
                            onCheckedChange = { v -> edit { it.copy(bold = v) } },
                        )
                        ToggleRow(
                            label = stringResource(R.string.comic_caps),
                            checked = bb.caps,
                            onCheckedChange = { v -> edit { it.copy(caps = v) } },
                        )
                        TextButton(
                            onClick = { bubbles.removeAt(bi); bubbleSel = -1 },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.action_remove),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }

                // Lienzo.
                SectionTitle(stringResource(R.string.collage_canvas))
                OptionRow(
                    options = RATIOS.indices.toList(),
                    selected = ratioIndex,
                    onSelect = { ratioIndex = it },
                    label = { RATIOS[it].first },
                )
                OptionRow(
                    options = SIZES.indices.toList(),
                    selected = sizeIndex,
                    onSelect = { sizeIndex = it },
                    label = { "${SIZES[it]} px" },
                )

                // Huecos.
                SectionTitle(stringResource(R.string.collage_slots))
                SliderRow(
                    label = stringResource(R.string.collage_gap),
                    value = gap, onValueChange = { gap = it },
                    valueRange = 0f..80f, valueText = formatValue(gap), defaultValue = 14f,
                )
                SliderRow(
                    label = stringResource(R.string.collage_margin),
                    value = margin, onValueChange = { margin = it },
                    valueRange = 0f..160f, valueText = formatValue(margin), defaultValue = 28f,
                )
                SliderRow(
                    label = stringResource(R.string.label_radius),
                    value = radius, onValueChange = { radius = it },
                    valueRange = 0f..300f, valueText = formatValue(radius), defaultValue = 24f,
                )
                SliderRow(
                    label = stringResource(R.string.collage_border),
                    value = border, onValueChange = { border = it },
                    valueRange = 0f..40f, valueText = formatValue(border), defaultValue = 0f,
                )
                if (border > 0f) {
                    ColorRow(
                        label = stringResource(R.string.label_color),
                        color = borderColor,
                        settings = settings,
                        onPick = { borderColor = it },
                        withAlpha = false,
                    )
                }
                ToggleRow(
                    label = stringResource(R.string.collage_shadow),
                    checked = shadow,
                    onCheckedChange = { shadow = it },
                )

                // Fondo.
                SectionTitle(stringResource(R.string.collage_bg))
                val bgNames = mapOf(
                    CollageBgKind.SOLID to stringResource(R.string.collage_bg_solid),
                    CollageBgKind.GRADIENT to stringResource(R.string.collage_bg_gradient),
                    CollageBgKind.BLUR to stringResource(R.string.collage_bg_blur),
                )
                OptionRow(
                    options = CollageBgKind.entries,
                    selected = bgKind,
                    onSelect = { bgKind = it },
                    label = { bgNames[it].orEmpty() },
                )
                when (bgKind) {
                    CollageBgKind.SOLID -> ColorRow(
                        label = stringResource(R.string.label_color),
                        color = bgColor,
                        settings = settings,
                        onPick = { bgColor = it },
                        withAlpha = false,
                    )
                    CollageBgKind.GRADIENT -> Column {
                        // 14 huecos en dos filas de 7: 13 presets y, al final, el personalizado.
                        val swatches = GRADIENTS.map { it } + listOf(listOf(gStart, gEnd))
                        swatches.chunked(7).forEachIndexed { row, chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                chunk.forEachIndexed { k, colors ->
                                    val i = row * 7 + k
                                    GradientSwatch(colors, selected = i == gradientIndex, onClick = { gradientIndex = i }) {
                                        if (i == GRADIENTS.size) {
                                            Icon(Icons.Filled.Tune, stringResource(R.string.collage_bg_custom), tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                        if (customGradient) {
                            ColorRow(
                                label = stringResource(R.string.collage_grad_start),
                                color = gStart, settings = settings, withAlpha = false,
                                onPick = { gStart = it },
                            )
                            ColorRow(
                                label = stringResource(R.string.collage_grad_end),
                                color = gEnd, settings = settings, withAlpha = false,
                                onPick = { gEnd = it },
                            )
                            TextButton(
                                onClick = { val t = gStart; gStart = gEnd; gEnd = t },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(18.dp))
                                Text(
                                    stringResource(R.string.collage_grad_swap),
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                            SliderRow(
                                label = stringResource(R.string.collage_grad_start_pos),
                                value = gPosStart,
                                onValueChange = { gPosStart = it.coerceAtMost(gPosEnd) },
                                valueRange = 0f..1f,
                                valueText = (gPosStart * 100).roundToInt().toString() + "%",
                                defaultValue = 0f,
                            )
                            SliderRow(
                                label = stringResource(R.string.collage_grad_end_pos),
                                value = gPosEnd,
                                onValueChange = { gPosEnd = it.coerceAtLeast(gPosStart) },
                                valueRange = 0f..1f,
                                valueText = (gPosEnd * 100).roundToInt().toString() + "%",
                                defaultValue = 1f,
                            )
                            SliderRow(
                                label = stringResource(R.string.collage_grad_angle),
                                value = gAngle,
                                onValueChange = { gAngle = it },
                                valueRange = 0f..360f,
                                valueText = gAngle.roundToInt().toString() + "°",
                                defaultValue = 90f,
                            )
                        }
                    }
                    CollageBgKind.BLUR -> Unit
                }
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                GeneratorActions(
                    vm = vm,
                    defaultName = stringResource(R.string.collage_title),
                    onOpenEditor = { draft.discard(); onOpenEditor() },
                    enabled = photos.any { !it.isEmpty },
                    build = build,
                )
                }
            }
        }
    }

    if (fullscreen) {
        previewState.value?.let { FullscreenImageDialog(it, null) { fullscreen = false } }
            ?: run { fullscreen = false }
    }
}

/**
 * Vista previa con hueco elegible. Toca = elegir, arrastra = encuadrar, pellizca = acercar.
 * Los huecos vacios llevan un "+" y el elegido un contorno.
 */
@Composable
private fun CollagePreview(
    bitmapState: State<Bitmap?>,
    canvasW: Int,
    canvasH: Int,
    rects: List<RectF>,
    filled: List<Boolean>,
    selected: Int,
    bubbleBoxes: List<RectF>,
    bubbleTips: List<Offset>,
    bubbleSel: Int,
    onSelectBubble: (Int) -> Unit,
    onBubbleStart: (Boolean) -> Unit,
    onBubbleLive: (Float, Float, Float, Float) -> Unit,
    onBubbleEnd: () -> Unit,
    onSelect: (Int) -> Unit,
    onLiveStart: () -> Unit,
    onLive: (Float, Float, Float) -> Unit,
    onLiveEnd: () -> Unit,
    onSwap: (Int, Int) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = bitmapState.value
    val liveStart by rememberUpdatedState(onLiveStart)
    val liveMove by rememberUpdatedState(onLive)
    val liveEnd by rememberUpdatedState(onLiveEnd)
    val bubbleStart by rememberUpdatedState(onBubbleStart)
    val bubbleMove by rememberUpdatedState(onBubbleLive)
    val bubbleEnd by rememberUpdatedState(onBubbleEnd)
    var box by remember { mutableStateOf(IntSize.Zero) }
    // Intercambio: se mantiene pulsado un hueco con foto y se arrastra sobre otro.
    var dragFrom by remember { mutableStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val pad = with(LocalDensity.current) { 8.dp.toPx() }
    val availW = max(1f, box.width - pad * 2)
    val availH = max(1f, box.height - pad * 2)
    val scale = min(availW / canvasW, availH / canvasH)
    val left = (box.width - canvasW * scale) / 2f
    val top = (box.height - canvasH * scale) / 2f
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .onSizeChanged { box = it }
            .pointerInput(rects, scale, left, top, bubbleBoxes) {
                detectTapGestures { off ->
                    val x = (off.x - left) / scale
                    val y = (off.y - top) / scale
                    // Un bocadillo tapa lo que tiene debajo.
                    val hit = bubbleBoxes.indexOfLast { it.contains(x, y) }
                    onSelectBubble(hit)
                    onSelect(if (hit >= 0) -1 else rects.indexOfLast { it.contains(x, y) })
                }
            }
            .pointerInput(rects, scale, left, top, selected, bubbleSel, bubbleBoxes, bubbleTips) {
                // Arrastrar y pellizcar un hueco. Solo se activa pasado el umbral de
                // movimiento (para no robar los toques) y entrega los cambios en vivo, sin
                // tocar la lista de fotos hasta soltar.
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var active = false
                    var totalPan = Offset.Zero
                    var totalZoom = 1f
                    var totalRot = 0f
                    // 0 = nada, 1 = mover el bocadillo, 2 = mover su cola.
                    var bmode = 0
                    if (bubbleSel in bubbleBoxes.indices) {
                        val x = (down.position.x - left) / scale
                        val y = (down.position.y - top) / scale
                        val tip = bubbleTips.getOrNull(bubbleSel)
                        bmode = when {
                            tip != null && hypot(x - tip.x, y - tip.y) * scale < 36.dp.toPx() -> 2
                            bubbleBoxes[bubbleSel].contains(x, y) -> 1
                            else -> 0
                        }
                    }
                    do {
                        val event = awaitPointerEvent()
                        if (bmode != 0) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val rot = event.calculateRotation()
                            if (!active) {
                                totalPan += pan
                                totalZoom *= zoom
                                totalRot += rot
                                if (totalPan.getDistance() > slop || abs(totalZoom - 1f) > 0.03f || abs(totalRot) > 3f) {
                                    active = true
                                    bubbleStart(bmode == 2)
                                    bubbleMove(totalPan.x / scale, totalPan.y / scale, totalZoom, totalRot)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } else {
                                bubbleMove(pan.x / scale, pan.y / scale, zoom, rot)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (selected >= 0 && dragFrom < 0) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (!active) {
                                totalPan += pan
                                totalZoom *= zoom
                                if (totalPan.getDistance() > slop || abs(totalZoom - 1f) > 0.03f) {
                                    active = true
                                    liveStart()
                                    // Lo acumulado hasta superar el umbral tambien cuenta.
                                    liveMove(totalPan.x / scale, totalPan.y / scale, totalZoom)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } else {
                                liveMove(pan.x / scale, pan.y / scale, zoom)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (active) { if (bmode != 0) bubbleEnd() else liveEnd() }
                }
            }
            .pointerInput(rects, scale, left, top, filled) {
                fun isFilled(i: Int) = filled.getOrElse(i) { false }
                fun slotAt(p: Offset): Int =
                    rects.indexOfLast { it.contains((p.x - left) / scale, (p.y - top) / scale) }
                detectDragGesturesAfterLongPress(
                    onDragStart = { off ->
                        val i = slotAt(off)
                        if (isFilled(i)) {
                            dragFrom = i
                            dragPos = off
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (dragFrom >= 0) dragPos += amount
                    },
                    onDragEnd = {
                        val to = slotAt(dragPos)
                        if (dragFrom >= 0 && isFilled(to) && to != dragFrom) onSwap(dragFrom, to)
                        dragFrom = -1
                    },
                    onDragCancel = { dragFrom = -1 },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            EmptyState(emptyText)
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            rects.forEachIndexed { i, r ->
                val tl = Offset(left + r.left * scale, top + r.top * scale)
                val sz = Size(r.width() * scale, r.height() * scale)
                if (!filled.getOrElse(i) { false }) {
                    // "+" en el centro de cada hueco sin foto.
                    val c = Offset(tl.x + sz.width / 2f, tl.y + sz.height / 2f)
                    val arm = min(sz.width, sz.height) * 0.14f
                    val w = 3.dp.toPx()
                    drawLine(Color.White, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), w)
                    drawLine(Color.White, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), w)
                }
                if (i == selected) {
                    drawRect(primary, tl, sz, style = Stroke(width = 3.dp.toPx()))
                }
                if (dragFrom >= 0 && i == dragFrom) {
                    // El hueco de origen se atenua mientras se arrastra.
                    drawRect(Color.Black.copy(alpha = 0.35f), tl, sz)
                }
            }
            bubbleBoxes.getOrNull(bubbleSel)?.let { r ->
                drawRect(
                    primary,
                    Offset(left + r.left * scale, top + r.top * scale),
                    Size(r.width() * scale, r.height() * scale),
                    style = Stroke(width = 2.dp.toPx()),
                )
                bubbleTips.getOrNull(bubbleSel)?.let { t ->
                    val c = Offset(left + t.x * scale, top + t.y * scale)
                    drawCircle(Color.White, 11.dp.toPx(), c)
                    drawCircle(primary, 8.dp.toPx(), c)
                }
            }
            if (dragFrom >= 0) {
                val to = rects.indexOfLast {
                    it.contains((dragPos.x - left) / scale, (dragPos.y - top) / scale)
                }
                if (filled.getOrElse(to) { false } && to != dragFrom) {
                    val r = rects[to]
                    val tl = Offset(left + r.left * scale, top + r.top * scale)
                    val sz = Size(r.width() * scale, r.height() * scale)
                    drawRect(primary.copy(alpha = 0.28f), tl, sz)
                    drawRect(primary, tl, sz, style = Stroke(width = 4.dp.toPx()))
                }
                // Ficha que sigue al dedo.
                drawCircle(primary.copy(alpha = 0.85f), 26.dp.toPx(), dragPos)
                drawCircle(Color.White, 10.dp.toPx(), dragPos)
            }
        }
    }
}

/** Miniatura esquematica de un diseno. */
@Composable
private fun LayoutThumb(layout: CollageLayout, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                2.dp,
                if (selected) primary else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val color = if (selected) primary else neutral
            layout.rects.forEachIndexed { i, r ->
                var l = r.left * size.width + 1.5f
                var t = r.top * size.height + 1.5f
                var rt = r.right * size.width - 1.5f
                var b = r.bottom * size.height - 1.5f
                val shape = layout.shape(i)
                if (shape == SlotShape.RECT) {
                    drawRect(color, Offset(l, t), Size(rt - l, b - t))
                } else {
                    // Las formas son cuadradas: el mayor cuadrado centrado de la celda.
                    val side = min(rt - l, b - t)
                    val cx = (l + rt) / 2f
                    val cy = (t + b) / 2f
                    l = cx - side / 2f; t = cy - side / 2f; rt = l + side; b = t + side
                    drawPath(CollageGeometry.slotPath(shape, RectF(l, t, rt, b), 0f).asComposePath(), color)
                }
            }
        }
    }
}

/** Foto del hueco que se esta moviendo con el dedo (aun sin volcar a la lista). */
private fun normDeg(d: Float): Float {
    var r = d % 360f
    if (r > 180f) r -= 360f
    if (r < -180f) r += 360f
    return r
}

private class LiveEdit(
    var index: Int = -1,
    var photo: CollagePhoto? = null,
    var bubbleIndex: Int = -1,
    var bubble: Bubble? = null,
    /** true = se arrastra la punta de la cola y no el cuerpo. */
    var tail: Boolean = false,
)
