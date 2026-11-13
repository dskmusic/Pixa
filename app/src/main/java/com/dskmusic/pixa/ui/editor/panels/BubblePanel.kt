package com.dskmusic.pixa.ui.editor.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.collage.Bubble
import com.dskmusic.pixa.core.collage.BubbleDraw
import com.dskmusic.pixa.core.collage.BubbleStyle
import com.dskmusic.pixa.core.ml.FaceFinder
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.render.LayerRasterizer
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Tipografias de Google Fonts para los bocadillos (las mismas del creador de comics). */
private val BUBBLE_FONTS = listOf(
    "Comic Neue", "Bangers", "Patrick Hand", "Permanent Marker", "Luckiest Guy", "Caveat", "Roboto",
)

/** Lado del lienzo de referencia con el que se dibuja un bocadillo-capa. */
private const val REF = 1080

private val MAX_FACES = 6

/**
 * Edicion de un bocadillo (capa [LayerContent.SpeechBubble]): texto, tipo, tipografia,
 * colores, contorno y direccion de la cola. Moverlo, girarlo o cambiar su tamano se hace
 * con los dedos como en cualquier capa.
 */
@Composable
fun BubblePanel(vm: EditorViewModel, doc: PixaDocument, settings: PixaSettings) {
    val layer = doc.selected
    val content = layer?.content as? LayerContent.SpeechBubble
    if (layer == null || content == null) {
        Text(
            stringResource(R.string.bubble_pick),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    val b = content.spec
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fonts = remember(context) { context.pixa.fonts }
    val rasterizer = remember(context) { context.pixa.rasterizer }

    /** Aplica el cambio y mantiene el bocadillo centrado donde estaba si cambia de tamano. */
    fun change(block: (Bubble) -> Bubble) {
        val before = rasterizer.intrinsicSize(layer, doc.width, doc.height)
        val next = block(b)
        val moved = layer.copy(content = LayerContent.SpeechBubble(next))
        val after = rasterizer.intrinsicSize(moved, doc.width, doc.height)
        vm.updateLayer(R.string.bubble_title, layer.id) {
            it.copy(
                content = LayerContent.SpeechBubble(next),
                transform = it.transform.copy(
                    tx = it.transform.tx + (before.width - after.width) / 2f * it.transform.scaleX,
                    ty = it.transform.ty + (before.height - after.height) / 2f * it.transform.scaleY,
                ),
            )
        }
    }

    val styleNames = mapOf(
        BubbleStyle.SPEECH to stringResource(R.string.comic_style_speech),
        BubbleStyle.THOUGHT to stringResource(R.string.comic_style_thought),
        BubbleStyle.SHOUT to stringResource(R.string.comic_style_shout),
        BubbleStyle.WHISPER to stringResource(R.string.comic_style_whisper),
        BubbleStyle.CAPTION to stringResource(R.string.comic_style_caption),
        BubbleStyle.BOX to stringResource(R.string.comic_style_box),
    )

    Column {
        TextFieldRow(
            label = stringResource(R.string.bubble_text),
            value = b.text,
            onValueChange = { t -> change { it.copy(text = t) } },
            singleLine = false,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        OptionRow(
            options = BubbleStyle.entries,
            selected = b.style,
            onSelect = { s -> change { it.copy(style = s) } },
            label = { styleNames[it].orEmpty() },
        )
        OptionRow(
            options = BUBBLE_FONTS,
            selected = b.font,
            onSelect = { family ->
                scope.launch {
                    fonts.load(FontRef(family, FontRef.Source.GOOGLE))
                    change { it.copy(font = family) }
                }
            },
            label = { it },
        )
        SliderRow(
            label = stringResource(R.string.comic_text_size),
            value = b.textSize,
            onValueChange = { v -> change { it.copy(textSize = v) } },
            valueRange = 16f..120f,
            valueText = b.textSize.toInt().toString(),
            defaultValue = 44f,
        )
        SliderRow(
            label = stringResource(R.string.comic_outline),
            value = b.strokeWidth,
            onValueChange = { v -> change { it.copy(strokeWidth = v) } },
            valueRange = 0f..16f,
            valueText = b.strokeWidth.toInt().toString(),
            defaultValue = 5f,
        )
        SliderRow(
            label = stringResource(R.string.bubble_tail_x),
            value = b.tailX,
            onValueChange = { v -> change { it.copy(tailX = v) } },
            valueRange = -0.5f..0.5f,
            valueText = "${(b.tailX * 100).toInt()}",
            defaultValue = -0.05f,
        )
        SliderRow(
            label = stringResource(R.string.bubble_tail_y),
            value = b.tailY,
            onValueChange = { v -> change { it.copy(tailY = v) } },
            valueRange = -0.5f..0.5f,
            valueText = "${(b.tailY * 100).toInt()}",
            defaultValue = 0.16f,
        )
        SliderRow(
            label = stringResource(R.string.bubble_pad_top),
            value = b.padTop,
            onValueChange = { v -> change { it.copy(padTop = v) } },
            valueRange = 0f..120f,
            valueText = b.padTop.toInt().toString(),
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.bubble_pad_bottom),
            value = b.padBottom,
            onValueChange = { v -> change { it.copy(padBottom = v) } },
            valueRange = 0f..120f,
            valueText = b.padBottom.toInt().toString(),
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.bubble_pad_left),
            value = b.padLeft,
            onValueChange = { v -> change { it.copy(padLeft = v) } },
            valueRange = 0f..120f,
            valueText = b.padLeft.toInt().toString(),
            defaultValue = 0f,
        )
        SliderRow(
            label = stringResource(R.string.bubble_pad_right),
            value = b.padRight,
            onValueChange = { v -> change { it.copy(padRight = v) } },
            valueRange = 0f..120f,
            valueText = b.padRight.toInt().toString(),
            defaultValue = 0f,
        )
        ColorRow(
            label = stringResource(R.string.comic_text_color),
            color = b.textColor,
            settings = settings,
            onPick = { c -> change { it.copy(textColor = c) } },
        )
        ColorRow(
            label = stringResource(R.string.comic_fill_color),
            color = b.fillColor,
            settings = settings,
            onPick = { c -> change { it.copy(fillColor = c) } },
        )
        ColorRow(
            label = stringResource(R.string.bubble_stroke_color),
            color = b.strokeColor,
            settings = settings,
            onPick = { c -> change { it.copy(strokeColor = c) } },
        )
        ToggleRow(
            label = stringResource(R.string.comic_caps),
            checked = b.caps,
            onCheckedChange = { on -> change { it.copy(caps = on) } },
        )
    }
}

/**
 * Anade bocadillos al documento: uno por cada cara que detecte (hasta [MAX_FACES]), encima
 * de ella y con la cola apuntandole; si no encuentra ninguna, uno solo arriba al centro.
 * Cada bocadillo es una capa editable ([BubblePanel]). Devuelve cuantas caras se encontraron.
 */
suspend fun insertBubbles(
    vm: EditorViewModel,
    rasterizer: LayerRasterizer,
    fonts: com.dskmusic.pixa.core.text.FontRepository,
    sampleText: String,
    layerName: String,
    detectFaces: Boolean = true,
): Int {
    val doc = vm.doc
    val font = BUBBLE_FONTS.first()
    fonts.load(FontRef(font, FontRef.Source.GOOGLE))
    val tf: (String) -> android.graphics.Typeface? = { fonts.cached(FontRef(it, FontRef.Source.GOOGLE)) }

    val faces = (if (!detectFaces) emptyList<FaceFinder.Face>() else runCatching {
        FaceFinder.detect(vm.compositeNow(), marginPercent = 0f)
    }.getOrDefault(emptyList())).sortedByDescending { it.area.width() * it.area.height() }.take(MAX_FACES)

    val unit = min(doc.width, doc.height) / REF.toFloat()
    // Cada objetivo: centro y radio de la cara en px del documento (o null = sin cara).
    val targets: List<Triple<Float, Float, Float>?> = if (faces.isEmpty()) {
        listOf(null)
    } else {
        faces.map { f ->
            Triple(f.area.exactCenterX(), f.area.exactCenterY(), max(f.area.width(), f.area.height()) / 2f)
        }
    }

    for (t in targets) {
        // cx / cy en 0,5: el cuerpo se dibuja en el centro del lienzo de referencia.
        val base = Bubble(text = sampleText, font = font, width = 0.3f, cx = 0.5f, cy = 0.5f)
        val box0 = BubbleDraw.bounds(base, REF, REF, tf)
        val halfH = box0.height() / 2f * unit
        // Donde cae el centro del bocadillo en el documento.
        var cx: Float
        var cy: Float
        var tailX = -0.05f
        var tailY = 0.16f
        if (t == null) {
            // Sin cara: arriba, y cada bocadillo nuevo un poco mas abajo para no tapar al anterior.
            val existing = doc.layers.count { it.content is LayerContent.SpeechBubble }
            cx = doc.width * (if (existing % 2 == 0) 0.4f else 0.6f)
            cy = doc.height * (0.2f + 0.1f * (existing % 5))
        } else {
            val (fx, fy, r) = t
            val above = fy - r - 2 * halfH > 0.02f * doc.height
            cx = fx.coerceIn(0.2f * doc.width, 0.8f * doc.width)
            cy = if (above) fy - r - halfH * 1.1f else fy + r + halfH * 1.1f
            val tipY = if (above) fy - r * 0.55f else fy + r * 0.55f
            // La cola, en fracciones del lienzo de referencia desde el centro del bocadillo.
            tailX = ((fx - cx) / unit / REF).coerceIn(-0.5f, 0.5f)
            tailY = ((tipY - cy) / unit / REF).coerceIn(-0.5f, 0.5f)
        }
        cx = cx.coerceIn(0f, doc.width.toFloat())
        cy = cy.coerceIn(0f, doc.height.toFloat())
        val spec = base.copy(tailX = tailX, tailY = tailY)
        val box = BubbleDraw.bounds(spec, REF, REF, tf)
        val w = box.width().toInt().coerceAtLeast(1)
        val h = box.height().toInt().coerceAtLeast(1)
        // Centro del cuerpo en el bitmap: (REF/2 - box.left, REF/2 - box.top); la escala
        // gira en torno al centro del bitmap.
        val bodyX = REF / 2f - box.left
        val bodyY = REF / 2f - box.top
        val layer = PixaLayer(
            name = layerName,
            content = LayerContent.SpeechBubble(spec),
            transform = Xf(
                tx = cx - w / 2f - (bodyX - w / 2f) * unit,
                ty = cy - h / 2f - (bodyY - h / 2f) * unit,
                scaleX = unit,
                scaleY = unit,
            ),
        )
        vm.addLayer(R.string.bubble_title, layer)
    }
    return faces.size
}


// ---- Punto de la cola (se arrastra sobre el lienzo) ----

/** Posicion en el documento de un punto (rx, ry) del lienzo de referencia del bocadillo. */
private fun refToDoc(layer: PixaLayer, rasterizer: LayerRasterizer, rx: Float, ry: Float): androidx.compose.ui.geometry.Offset? {
    val spec = (layer.content as? LayerContent.SpeechBubble)?.spec ?: return null
    val bmp = rasterizer.rasterize(layer) ?: return null
    val box = BubbleDraw.bounds(spec, REF, REF, rasterizer::bubbleFont)
    val p = floatArrayOf(rx - box.left, ry - box.top)
    layer.transform.toMatrix(bmp.width, bmp.height).mapPoints(p)
    return androidx.compose.ui.geometry.Offset(p[0], p[1])
}

/** Donde esta la punta de la cola de un bocadillo, en coordenadas del documento. */
fun bubbleTipDoc(layer: PixaLayer, rasterizer: LayerRasterizer): androidx.compose.ui.geometry.Offset? {
    val spec = (layer.content as? LayerContent.SpeechBubble)?.spec ?: return null
    val tip = BubbleDraw.tip(spec, REF, REF, rasterizer::bubbleFont)
    return refToDoc(layer, rasterizer, tip.first, tip.second)
}

/**
 * El bocadillo con la punta de la cola en [docPoint]. El cuerpo se queda quieto en el
 * documento aunque la caja del bocadillo cambie de tamano al mover la cola.
 */
fun bubbleWithTail(
    layer: PixaLayer,
    rasterizer: LayerRasterizer,
    docPoint: androidx.compose.ui.geometry.Offset,
): PixaLayer? {
    val spec = (layer.content as? LayerContent.SpeechBubble)?.spec ?: return null
    val bmp = rasterizer.rasterize(layer) ?: return null
    val box = BubbleDraw.bounds(spec, REF, REF, rasterizer::bubbleFont)
    val inverse = android.graphics.Matrix()
    if (!layer.transform.toMatrix(bmp.width, bmp.height).invert(inverse)) return null
    val p = floatArrayOf(docPoint.x, docPoint.y)
    inverse.mapPoints(p)
    val rx = p[0] + box.left
    val ry = p[1] + box.top
    val next = spec.copy(
        tailX = ((rx - spec.cx * REF) / REF).coerceIn(-1f, 1f),
        tailY = ((ry - spec.cy * REF) / REF).coerceIn(-1f, 1f),
    )
    val moved = layer.copy(content = LayerContent.SpeechBubble(next))
    val before = refToDoc(layer, rasterizer, spec.cx * REF, spec.cy * REF)
    val after = refToDoc(moved, rasterizer, next.cx * REF, next.cy * REF)
    if (before == null || after == null) return moved
    return moved.copy(
        transform = moved.transform.copy(
            tx = moved.transform.tx + (before.x - after.x),
            ty = moved.transform.ty + (before.y - after.y),
        ),
    )
}

/**
 * Cuadrilatero del bocadillo con los tiradores de los lados (16 valores), pero con cada
 * tirador de lado desplazado a lo largo de su lado si cayera a menos de [minDist] de la
 * punta de la cola: asi nunca se montan y cada uno se puede tocar sin ambiguedad.
 * [minDist] va en unidades de documento.
 */
fun bubbleHandleQuad(
    layer: PixaLayer,
    rasterizer: LayerRasterizer,
    quad: FloatArray,
    minDist: Float,
): FloatArray {
    val base = com.dskmusic.pixa.ui.editor.LayerGeometry.withEdgeMidpoints(quad)
    val tip = bubbleTipDoc(layer, rasterizer) ?: return base
    val out = base.copyOf()
    for (i in 0 until 4) {
        val ax = quad[(i * 2) % 8]
        val ay = quad[(i * 2 + 1) % 8]
        val bx = quad[((i + 1) * 2) % 8]
        val by = quad[((i + 1) * 2 + 1) % 8]
        val mx = (ax + bx) / 2f
        val my = (ay + by) / 2f
        val len = kotlin.math.hypot(bx - ax, by - ay)
        if (len < 1f) continue
        val dx = (bx - ax) / len
        val dy = (by - ay) / len
        // Posicion de la punta a lo largo del lado (t0) y su distancia perpendicular.
        val t0 = (tip.x - mx) * dx + (tip.y - my) * dy
        val perp = kotlin.math.abs((tip.x - mx) * -dy + (tip.y - my) * dx)
        if (perp >= minDist) continue
        val need = kotlin.math.sqrt(minDist * minDist - perp * perp)
        if (kotlin.math.abs(t0) >= need) continue
        // El tirador se aparta de la punta hasta quedar a [minDist], sin salirse del lado.
        val sign = if (t0 > 0f) -1f else 1f
        val limit = (len / 2f - 8f).coerceAtLeast(0f)
        val t = (t0 + sign * need).coerceIn(-limit, limit)
        out[8 + i * 2] = mx + dx * t
        out[9 + i * 2] = my + dy * t
    }
    return out
}
