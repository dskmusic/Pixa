package com.dskmusic.pixa.ui.meme

import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.collage.CollageGeometry
import com.dskmusic.pixa.core.collage.CollagePhoto
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.ShapeKind
import com.dskmusic.pixa.core.model.ShapeSpec
import com.dskmusic.pixa.core.model.TextAlign
import com.dskmusic.pixa.core.model.TextSpec
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.core.render.TextRenderer
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.formatValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()
private const val INK = 0xFF111111.toInt()

/** Aspecto de cada caja de texto de una plantilla. */
internal enum class MemeBox { IMPACT, DARK, LIGHT, SUBTITLE, SIGN, TITLE }

internal class MemeText(val rect: RectF, val style: MemeBox, val defaultText: String = "")

/**
 * Una plantilla de meme: huecos de foto y cajas de texto en coordenadas normalizadas del
 * lienzo. Son solo composiciones: ninguna lleva imagenes incluidas (las fotos las pone el
 * usuario), asi que no hay material con derechos dentro de la app.
 *
 * [aspect] es ancho / alto; null significa "el de la primera foto" (una sola foto a lienzo
 * completo con las cajas encima).
 */
internal class MemeTemplate(
    val id: String,
    val nameRes: Int,
    val aspect: Float?,
    val bg: Int,
    val photos: List<RectF>,
    val texts: List<MemeText>,
)

private fun r(l: Float, t: Float, rt: Float, b: Float) = RectF(l, t, rt, b)

/** Las cuatro vinetas de una rejilla 2x2 con un hilo de separacion. */
private val GRID4 = listOf(
    r(0.008f, 0.008f, 0.496f, 0.496f), r(0.504f, 0.008f, 0.992f, 0.496f),
    r(0.008f, 0.504f, 0.496f, 0.992f), r(0.504f, 0.504f, 0.992f, 0.992f),
)

private val STRIP3 = listOf(
    r(0.006f, 0.012f, 0.331f, 0.988f), r(0.337f, 0.012f, 0.662f, 0.988f),
    r(0.669f, 0.012f, 0.994f, 0.988f),
)

internal val MEME_TEMPLATES: List<MemeTemplate> = listOf(
    MemeTemplate(
        "three_photos", R.string.mt_three_photos, 1f, WHITE,
        photos = listOf(r(0f, 0f, 1f, 0.5f), r(0f, 0.5f, 0.5f, 1f), r(0.5f, 0.5f, 1f, 1f)),
        texts = listOf(
            MemeText(r(0.04f, 0.38f, 0.96f, 0.49f), MemeBox.IMPACT),
            MemeText(r(0.03f, 0.88f, 0.47f, 0.99f), MemeBox.DARK),
            MemeText(r(0.53f, 0.88f, 0.97f, 0.99f), MemeBox.DARK),
        ),
    ),
    MemeTemplate(
        "two_panel", R.string.mt_two_panel, 2f / 3f, WHITE,
        photos = listOf(r(0f, 0f, 1f, 0.5f), r(0f, 0.5f, 1f, 1f)),
        texts = listOf(
            MemeText(r(0.04f, 0.37f, 0.96f, 0.49f), MemeBox.IMPACT),
            MemeText(r(0.04f, 0.87f, 0.96f, 0.99f), MemeBox.IMPACT),
        ),
    ),
    MemeTemplate(
        "yes_no", R.string.mt_yes_no, 1f, WHITE,
        photos = listOf(r(0f, 0f, 0.5f, 0.5f), r(0f, 0.5f, 0.5f, 1f)),
        texts = listOf(
            MemeText(r(0.54f, 0.04f, 0.96f, 0.46f), MemeBox.DARK),
            MemeText(r(0.54f, 0.54f, 0.96f, 0.96f), MemeBox.DARK),
        ),
    ),
    MemeTemplate(
        "levels", R.string.mt_levels, 2f / 3f, WHITE,
        photos = List(4) { r(0.5f, it * 0.25f, 1f, it * 0.25f + 0.25f) },
        texts = List(4) {
            MemeText(r(0.03f, it * 0.25f + 0.02f, 0.47f, it * 0.25f + 0.23f), MemeBox.DARK)
        },
    ),
    MemeTemplate(
        "versus", R.string.mt_versus, 1f, WHITE,
        photos = listOf(r(0f, 0f, 0.5f, 0.8f), r(0.5f, 0f, 1f, 0.8f)),
        texts = listOf(
            MemeText(r(0.02f, 0.82f, 0.48f, 0.98f), MemeBox.DARK),
            MemeText(r(0.52f, 0.82f, 0.98f, 0.98f), MemeBox.DARK),
            MemeText(r(0.38f, 0.30f, 0.62f, 0.50f), MemeBox.IMPACT, defaultText = "VS"),
        ),
    ),
    MemeTemplate(
        "before_after", R.string.mt_before_after, 2f, WHITE,
        photos = listOf(r(0f, 0f, 0.5f, 1f), r(0.5f, 0f, 1f, 1f)),
        texts = listOf(
            MemeText(r(0.02f, 0.03f, 0.48f, 0.17f), MemeBox.IMPACT),
            MemeText(r(0.52f, 0.03f, 0.98f, 0.17f), MemeBox.IMPACT),
        ),
    ),
    MemeTemplate(
        "comic4", R.string.mt_comic4, 1f, BLACK,
        photos = GRID4,
        texts = GRID4.map { MemeText(r(it.left + 0.02f, it.bottom - 0.115f, it.right - 0.02f, it.bottom - 0.02f), MemeBox.SUBTITLE) },
    ),
    MemeTemplate(
        "strip3", R.string.mt_strip3, 3f, BLACK,
        photos = STRIP3,
        texts = STRIP3.map { MemeText(r(it.left + 0.012f, it.bottom - 0.2f, it.right - 0.012f, it.bottom - 0.03f), MemeBox.SUBTITLE) },
    ),
    MemeTemplate(
        "labels", R.string.mt_labels, null, WHITE,
        photos = listOf(r(0f, 0f, 1f, 1f)),
        texts = listOf(
            MemeText(r(0.02f, 0.50f, 0.30f, 0.70f), MemeBox.IMPACT),
            MemeText(r(0.36f, 0.50f, 0.64f, 0.70f), MemeBox.IMPACT),
            MemeText(r(0.70f, 0.50f, 0.98f, 0.70f), MemeBox.IMPACT),
        ),
    ),
    MemeTemplate(
        "sign", R.string.mt_sign, null, WHITE,
        photos = listOf(r(0f, 0f, 1f, 1f)),
        texts = listOf(MemeText(r(0.30f, 0.60f, 0.70f, 0.78f), MemeBox.SIGN)),
    ),
    MemeTemplate(
        "movie_subtitle", R.string.mt_subtitle, null, WHITE,
        photos = listOf(r(0f, 0f, 1f, 1f)),
        texts = listOf(MemeText(r(0.06f, 0.82f, 0.94f, 0.96f), MemeBox.SUBTITLE)),
    ),
    MemeTemplate(
        "poster", R.string.mt_poster, 0.8f, BLACK,
        photos = listOf(r(0.08f, 0.05f, 0.92f, 0.70f)),
        texts = listOf(
            MemeText(r(0.05f, 0.73f, 0.95f, 0.86f), MemeBox.TITLE),
            MemeText(r(0.08f, 0.87f, 0.92f, 0.95f), MemeBox.LIGHT),
        ),
    ),
)

/** Fuentes de Google para el texto de las plantillas. */
internal val MEME_FONTS = listOf("Anton", "Bangers", "Bebas Neue", "Oswald", "Roboto", "Playfair Display")

internal class MemeNames(val text: String, val image: String, val frame: String)

private fun specFor(
    style: MemeBox,
    text: String,
    s: Float,
    boxWidth: Float,
    font: String,
    caps: Boolean,
    outline: Boolean,
): TextSpec {
    val chosen = FontRef(font, FontRef.Source.GOOGLE)
    return when (style) {
        MemeBox.IMPACT -> TextSpec(
            text = text, font = chosen, sizePx = s, color = WHITE, allCaps = caps, boxWidth = boxWidth,
            outlineWidth = if (outline) s * 0.07f else 0f, outlineColor = BLACK,
        )
        MemeBox.DARK -> TextSpec(
            text = text, font = chosen, sizePx = s, color = INK, allCaps = caps, boxWidth = boxWidth,
            align = TextAlign.LEFT,
        )
        MemeBox.LIGHT -> TextSpec(
            text = text, font = chosen, sizePx = s, color = WHITE, allCaps = caps, boxWidth = boxWidth,
        )
        MemeBox.SUBTITLE -> TextSpec(
            text = text, font = FontRef("Roboto", FontRef.Source.GOOGLE), sizePx = s, color = WHITE,
            bold = true, boxWidth = boxWidth,
            outlineWidth = if (outline) s * 0.07f else 0f, outlineColor = BLACK,
        )
        MemeBox.SIGN -> TextSpec(
            text = text, font = chosen, sizePx = s, color = INK, bold = true, allCaps = caps,
            boxWidth = boxWidth, boxColor = WHITE, boxPadding = s * 0.25f, boxCornerRadius = s * 0.08f,
        )
        MemeBox.TITLE -> TextSpec(
            text = text, font = FontRef("Playfair Display", FontRef.Source.GOOGLE), sizePx = s,
            color = WHITE, allCaps = true, letterSpacingEm = 0.06f, boxWidth = boxWidth,
        )
    }
}

/**
 * Compone una plantilla: huecos de foto (marco + foto recortada al marco, como en el
 * collage) y una capa de texto por caja, con el tamano ajustado al ancho y alto de la caja.
 * Devuelve null mientras no haya ninguna foto.
 */
internal fun buildTemplateMeme(
    t: MemeTemplate,
    photos: List<CollagePhoto?>,
    texts: List<String>,
    font: String,
    caps: Boolean,
    outline: Boolean,
    typefaceOf: (FontRef) -> Typeface?,
    names: MemeNames,
): PixaDocument? {
    val first = photos.firstOrNull { it != null } ?: return null
    val (w, h) = if (t.aspect == null) {
        // Una foto a lienzo completo: el lienzo es la propia foto (sin recorte).
        val base = photos.firstOrNull() ?: first
        // Con un giro de 90 grados el lienzo se pone de lado, como la foto.
        if (base.quarterTurns % 2 != 0) base.height to base.width else base.width to base.height
    } else {
        val width = if (t.aspect > 1.5f) 1620 else 1080
        width to (width / t.aspect).roundToInt().coerceAtLeast(16)
    }
    val layers = ArrayList<PixaLayer>()

    t.photos.forEachIndexed { i, n ->
        val rect = RectF(n.left * w, n.top * h, n.right * w, n.bottom * h)
        val rw = rect.width().roundToInt().coerceAtLeast(1)
        val rh = rect.height().roundToInt().coerceAtLeast(1)
        val photo = photos.getOrNull(i)
        if (t.aspect != null) {
            layers += PixaLayer(
                name = names.frame,
                content = LayerContent.Shape(
                    ShapeSpec(
                        kind = ShapeKind.RECT, width = rw.toFloat(), height = rh.toFloat(),
                        fill = FillSpec.Solid(0xFFCFD3D8.toInt()), strokeWidth = 0f,
                    ),
                ),
                transform = Xf(tx = rect.left, ty = rect.top),
            )
        }
        if (photo != null) {
            val pl = CollageGeometry.place(rect, photo)
            layers += PixaLayer(
                name = names.image,
                content = LayerContent.Raster(photo.id),
                transform = Xf(
                    tx = pl.cx - photo.width / 2f,
                    ty = pl.cy - photo.height / 2f,
                    scaleX = pl.scale,
                    scaleY = pl.scale,
                    rotation = 90f * (photo.quarterTurns % 4),
                    flipH = photo.flipH,
                ),
                // Con aspecto propio los huecos son paneles: la foto no puede salirse.
                clipToBelow = t.aspect != null,
            )
        }
    }

    t.texts.forEachIndexed { i, box ->
        val text = texts.getOrNull(i).orEmpty().trim().ifBlank { box.defaultText }
        if (text.isBlank()) return@forEachIndexed
        val bw = box.rect.width() * w
        val bh = box.rect.height() * h
        // Busqueda binaria del mayor tamano que cabe en la caja (ancho y alto).
        var lo = 8f
        var hi = min(bh, 400f).coerceAtLeast(9f)
        repeat(14) {
            val mid = (lo + hi) / 2f
            val trial = specFor(box.style, text, mid, bw, font, caps, outline)
            val m = TextRenderer.measure(trial, typefaceOf(trial.font))
            // Solo se mira el alto: el texto ya se parte al ancho de la caja, y el ancho
            // medido incluye el margen del contorno, asi que nunca "cabria" (el texto
            // quedaba en 8 px).
            if (m.height <= bh) lo = mid else hi = mid
        }
        val spec = specFor(box.style, text, lo, bw, font, caps, outline)
        val size = TextRenderer.measure(spec, typefaceOf(spec.font))
        val left = box.rect.left * w
        val tx = if (spec.align == TextAlign.LEFT) left else left + (bw - size.width) / 2f
        layers += PixaLayer(
            name = names.text,
            content = LayerContent.Text(spec),
            transform = Xf(tx = tx, ty = box.rect.top * h + (bh - size.height) / 2f),
        )
    }

    return PixaDocument(
        width = w,
        height = h,
        background = CanvasBackground(transparent = false, color = t.bg),
        layers = layers,
        selectedLayerId = layers.lastOrNull()?.id,
    )
}

/** Controles de una plantilla: fotos de cada hueco con su encuadre, textos y estilo. */
@Composable
internal fun TemplateControls(
    template: MemeTemplate,
    photos: List<CollagePhoto?>,
    texts: List<String>,
    slot: Int,
    font: String,
    caps: Boolean,
    outline: Boolean,
    onSlot: (Int) -> Unit,
    onPickSlot: (Int) -> Unit,
    onPhoto: (Int, CollagePhoto?) -> Unit,
    onText: (Int, String) -> Unit,
    onFont: (String) -> Unit,
    onCaps: (Boolean) -> Unit,
    onOutline: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.meme_photos))
        if (template.photos.size > 1) {
            OptionRow(
                options = template.photos.indices.toList(),
                selected = slot,
                onSelect = onSlot,
                label = { "${it + 1}" + if (photos.getOrNull(it) == null) " +" else "" },
            )
        }
        val current = photos.getOrNull(slot)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { onPickSlot(slot) }) {
                Text(
                    stringResource(
                        if (current == null) R.string.action_add else R.string.action_replace,
                    ) + " " + stringResource(R.string.meme_photo_n, slot + 1),
                )
            }
            if (current != null) {
                IconButton(onClick = { onPhoto(slot, current.copy(quarterTurns = (current.quarterTurns + 1) % 4, panX = 0f, panY = 0f)) }) {
                    Icon(Icons.Filled.RotateRight, stringResource(R.string.collage_rotate))
                }
                IconButton(onClick = { onPhoto(slot, current.copy(flipH = !current.flipH)) }) {
                    Icon(Icons.Filled.Flip, stringResource(R.string.collage_flip))
                }
                IconButton(onClick = { onPhoto(slot, null) }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.action_remove))
                }
            }
        }
        if (current != null) {
            SliderRow(
                label = stringResource(R.string.collage_zoom),
                value = current.zoom,
                onValueChange = { onPhoto(slot, current.copy(zoom = it)) },
                valueRange = 1f..4f,
                valueText = "${formatValue(current.zoom)}x",
                defaultValue = 1f,
            )
            SliderRow(
                label = stringResource(R.string.meme_pan_x),
                value = current.panX,
                onValueChange = { onPhoto(slot, current.copy(panX = it)) },
                valueRange = -1f..1f,
                valueText = formatValue(current.panX),
                defaultValue = 0f,
            )
            SliderRow(
                label = stringResource(R.string.meme_pan_y),
                value = current.panY,
                onValueChange = { onPhoto(slot, current.copy(panY = it)) },
                valueRange = -1f..1f,
                valueText = formatValue(current.panY),
                defaultValue = 0f,
            )
        }

        SectionTitle(stringResource(R.string.label_text))
        template.texts.forEachIndexed { i, box ->
            TextFieldRow(
                label = stringResource(R.string.meme_text_n, i + 1),
                value = texts.getOrNull(i).orEmpty(),
                onValueChange = { onText(i, it) },
                placeholder = box.defaultText.ifBlank { null },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Text(
            stringResource(R.string.font_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        OptionRow(
            options = MEME_FONTS,
            selected = font,
            onSelect = onFont,
            label = { it },
        )
        ToggleRow(
            label = stringResource(R.string.text_uppercase),
            checked = caps,
            onCheckedChange = onCaps,
        )
        ToggleRow(
            label = stringResource(R.string.text_outline),
            checked = outline,
            onCheckedChange = onOutline,
        )
    }
}
