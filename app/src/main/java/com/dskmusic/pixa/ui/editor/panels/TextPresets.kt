package com.dskmusic.pixa.ui.editor.panels

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.GradientKind
import com.dskmusic.pixa.core.model.GradientStop
import com.dskmusic.pixa.core.model.TextAlign
import com.dskmusic.pixa.core.model.TextOutline
import com.dskmusic.pixa.core.model.TextSpec
import com.dskmusic.pixa.core.render.TextRenderer
import com.dskmusic.pixa.core.render.TextTexture
import com.dskmusic.pixa.core.render.TextTextures
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

/**
 * Un estilo de texto completo. Parte siempre de [clean] (todo el estilo a cero salvo el
 * texto, el tamano, el ancho de caja, la alineacion y la fuente) y solo despues aplica
 * [build]: asi un preset nunca se mezcla con el anterior.
 *
 * [font] es una familia de Google Fonts que el preset impone (se descarga al aplicarlo; sin
 * conexion el sistema usa la suya). [texture] rellena las letras con una textura generada.
 */
private class TextPreset(
    val nameRes: Int,
    val group: Int,
    val font: String? = null,
    val texture: TextTexture? = null,
    /** "Sin estilo": quita todo menos el color que ya tenia el texto. */
    val keepColor: Boolean = false,
    val build: TextSpec.() -> TextSpec,
)

private fun TextSpec.clean(): TextSpec = copy(
    color = WHITE,
    bold = false,
    italic = false,
    underline = false,
    strikethrough = false,
    allCaps = false,
    lineHeightMul = 1.15f,
    letterSpacingEm = 0f,
    outlineWidth = 0f,
    outlineColor = BLACK,
    shadowRadius = 0f,
    shadowDx = 0f,
    shadowDy = 0f,
    shadowColor = 0x80000000.toInt(),
    boxColor = 0,
    boxPadding = 0f,
    boxCornerRadius = 0f,
    fill = null,
    extraOutlines = emptyList(),
)

private fun grad(angle: Float, vararg colors: Int, kind: GradientKind = GradientKind.LINEAR): FillSpec.Gradient =
    FillSpec.Gradient(
        kind = kind,
        stops = colors.mapIndexed { i, c ->
            GradientStop(if (colors.size == 1) 0f else i / (colors.size - 1f), c)
        },
        angle = angle,
    )

private const val G_BASE = 6
private const val G_IMPACT = 0
private const val G_NEON = 1
private const val G_MATERIAL = 2
private const val G_GRADIENT = 3
private const val G_LABEL = 4
private const val G_FUN = 5

/** Grupos en el orden en que se muestran: "Base" (sin estilo) primero. */
private val GROUPS = listOf(
    G_BASE to R.string.tpg_base,
    G_IMPACT to R.string.tpg_impact,
    G_NEON to R.string.tpg_neon,
    G_MATERIAL to R.string.tpg_material,
    G_GRADIENT to R.string.tpg_gradient,
    G_LABEL to R.string.tpg_label,
    G_FUN to R.string.tpg_fun,
)

private val PRESETS: List<TextPreset> = listOf(
    // ---- Base: quitar cualquier estilo ----
    TextPreset(R.string.tp_none, G_BASE, keepColor = true) { this },

    // ---- Impacto y memes ----
    TextPreset(R.string.tp_meme, G_IMPACT, font = "Anton") {
        copy(allCaps = true, outlineWidth = sizePx * 0.07f, outlineColor = BLACK)
    },
    TextPreset(R.string.tp_comic, G_IMPACT, font = "Bangers") {
        copy(
            color = 0xFFFFEB3B.toInt(), outlineWidth = sizePx * 0.06f,
            shadowColor = BLACK, shadowDx = sizePx * 0.07f, shadowDy = sizePx * 0.07f,
        )
    },
    TextPreset(R.string.tp_headline, G_IMPACT, font = "Bebas Neue") {
        copy(
            allCaps = true, letterSpacingEm = 0.04f, shadowRadius = sizePx * 0.12f,
            shadowDy = sizePx * 0.05f, shadowColor = 0x99000000.toInt(),
        )
    },
    TextPreset(R.string.tp_sticker, G_IMPACT) {
        copy(
            color = 0xFF111111.toInt(), bold = true,
            outlineWidth = sizePx * 0.10f, outlineColor = WHITE,
            extraOutlines = listOf(TextOutline(sizePx * 0.035f, BLACK)),
        )
    },
    TextPreset(R.string.tp_heavy, G_IMPACT) {
        copy(
            bold = true, allCaps = true, letterSpacingEm = -0.02f, lineHeightMul = 0.95f,
            shadowRadius = sizePx * 0.1f, shadowDy = sizePx * 0.05f, shadowColor = 0x99000000.toInt(),
        )
    },
    TextPreset(R.string.tp_double_outline, G_IMPACT) {
        copy(
            color = 0xFFFFD600.toInt(), bold = true, allCaps = true,
            outlineWidth = sizePx * 0.05f, outlineColor = 0xFFE53935.toInt(),
            extraOutlines = listOf(TextOutline(sizePx * 0.05f, BLACK)),
        )
    },

    // ---- Neon ----
    TextPreset(R.string.tp_neon_cyan, G_NEON) {
        copy(color = 0xFFE6FFFF.toInt(), shadowRadius = sizePx * 0.3f, shadowColor = 0xFF00E5FF.toInt())
    },
    TextPreset(R.string.tp_neon_pink, G_NEON) {
        copy(color = 0xFFFFE6F3.toInt(), shadowRadius = sizePx * 0.3f, shadowColor = 0xFFFF2D95.toInt())
    },
    TextPreset(R.string.tp_neon_green, G_NEON) {
        copy(color = 0xFFEFFFE6.toInt(), shadowRadius = sizePx * 0.3f, shadowColor = 0xFF39FF14.toInt())
    },
    TextPreset(R.string.tp_neon_double, G_NEON) {
        copy(
            color = WHITE, outlineWidth = sizePx * 0.025f, outlineColor = 0xFF00E5FF.toInt(),
            shadowRadius = sizePx * 0.32f, shadowColor = 0xFFFF2D95.toInt(),
        )
    },
    TextPreset(R.string.tp_arcade, G_NEON, font = "Press Start 2P") {
        copy(
            color = 0xFFFFE600.toInt(), outlineWidth = sizePx * 0.05f, outlineColor = 0xFFB00020.toInt(),
            shadowColor = 0xFF7B2CBF.toInt(), shadowDx = sizePx * 0.07f, shadowDy = sizePx * 0.07f,
        )
    },
    TextPreset(R.string.tp_vapor, G_NEON, font = "Monoton") {
        copy(
            fill = grad(90f, 0xFFFF71CE.toInt(), 0xFF01CDFE.toInt(), 0xFFB967FF.toInt()),
            shadowRadius = sizePx * 0.18f, shadowColor = 0x99B967FF.toInt(),
        )
    },

    // ---- Materiales (texturas generadas) ----
    TextPreset(R.string.tex_gold, G_MATERIAL, texture = TextTexture.GOLD) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF5A3A00.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_silver, G_MATERIAL, texture = TextTexture.SILVER) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF2F353B.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_fire, G_MATERIAL, texture = TextTexture.FIRE) {
        copy(bold = true, shadowRadius = sizePx * 0.22f, shadowColor = 0xCCFF6D00.toInt())
    },
    TextPreset(R.string.tex_ice, G_MATERIAL, texture = TextTexture.ICE) {
        copy(bold = true, outlineWidth = sizePx * 0.025f, outlineColor = 0xFF1B5E7A.toInt(),
            shadowRadius = sizePx * 0.18f, shadowColor = 0xCC4FC3F7.toInt())
    },
    TextPreset(R.string.tex_marble, G_MATERIAL, texture = TextTexture.MARBLE) {
        copy(bold = true, outlineWidth = sizePx * 0.02f, outlineColor = 0xFF37474F.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x80000000.toInt())
    },
    TextPreset(R.string.tex_wood, G_MATERIAL, texture = TextTexture.WOOD) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF2E1A0B.toInt(),
            shadowRadius = sizePx * 0.06f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },

    // ---- Degradados ----
    TextPreset(R.string.tp_sunset, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFFFFB347.toInt(), 0xFFFF5E62.toInt(), 0xFF8E2DE2.toInt()))
    },
    TextPreset(R.string.tp_ocean, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFF00E5FF.toInt(), 0xFF0072FF.toInt()))
    },
    TextPreset(R.string.tp_aurora, G_GRADIENT) {
        copy(bold = true, fill = grad(0f, 0xFF00F5A0.toInt(), 0xFF00D9F5.toInt(), 0xFF7B2FF7.toInt()))
    },
    TextPreset(R.string.tp_instagram, G_GRADIENT) {
        copy(
            bold = true,
            fill = grad(45f, 0xFFFEDA75.toInt(), 0xFFFA7E1E.toInt(), 0xFFD62976.toInt(), 0xFF962FBF.toInt(), 0xFF4F5BD5.toInt()),
        )
    },
    TextPreset(R.string.tp_rainbow, G_GRADIENT) {
        copy(
            bold = true,
            fill = grad(
                0f, 0xFFFF1744.toInt(), 0xFFFF9100.toInt(), 0xFFFFEA00.toInt(), 0xFF00E676.toInt(),
                0xFF00B0FF.toInt(), 0xFF651FFF.toInt(), 0xFFD500F9.toInt(),
            ),
            outlineWidth = sizePx * 0.015f, outlineColor = 0x66000000,
        )
    },
    TextPreset(R.string.tp_flame_gradient, G_GRADIENT) {
        copy(
            bold = true, allCaps = true,
            fill = grad(90f, 0xFFFFF200.toInt(), 0xFFFF8C00.toInt(), 0xFFE53935.toInt()),
            shadowRadius = sizePx * 0.14f, shadowColor = 0xAAE53935.toInt(),
        )
    },

    // ---- Etiquetas y cajas ----
    TextPreset(R.string.tp_subtitle, G_LABEL) {
        copy(boxColor = 0xB3000000.toInt(), boxPadding = sizePx * 0.25f, boxCornerRadius = sizePx * 0.15f)
    },
    TextPreset(R.string.tp_highlighter, G_LABEL) {
        copy(
            color = 0xFF111111.toInt(), bold = true, boxColor = 0xE6FFEB3B.toInt(),
            boxPadding = sizePx * 0.18f, boxCornerRadius = sizePx * 0.1f,
        )
    },
    TextPreset(R.string.tp_pill, G_LABEL) {
        copy(
            bold = true, allCaps = true, letterSpacingEm = 0.05f, boxColor = BLACK,
            boxPadding = sizePx * 0.3f, boxCornerRadius = sizePx * 2f,
        )
    },
    TextPreset(R.string.tp_banner, G_LABEL) {
        copy(
            bold = true, allCaps = true, letterSpacingEm = 0.06f, boxColor = 0xFFE53935.toInt(),
            boxPadding = sizePx * 0.3f,
        )
    },
    TextPreset(R.string.tp_glass, G_LABEL) {
        copy(
            bold = true, boxColor = 0x40FFFFFF, boxPadding = sizePx * 0.3f, boxCornerRadius = sizePx * 0.3f,
            shadowRadius = sizePx * 0.1f, shadowColor = 0x66000000,
        )
    },
    TextPreset(R.string.tp_postit, G_LABEL) {
        copy(
            color = 0xFF3E2723.toInt(), boxColor = 0xFFFFF176.toInt(),
            boxPadding = sizePx * 0.3f, boxCornerRadius = sizePx * 0.05f,
        )
    },

    // ---- Divertidos y de autor ----
    TextPreset(R.string.tp_hollow, G_FUN) {
        copy(bold = true, color = 0x00FFFFFF, outlineWidth = sizePx * 0.04f, outlineColor = WHITE)
    },
    TextPreset(R.string.tp_glitch, G_FUN) {
        copy(
            bold = true, color = WHITE, shadowColor = 0xFF00FFFF.toInt(),
            shadowDx = -sizePx * 0.05f, outlineWidth = sizePx * 0.012f, outlineColor = 0xFFFF0055.toInt(),
        )
    },
    TextPreset(R.string.tp_elegant, G_FUN) {
        copy(allCaps = true, letterSpacingEm = 0.3f, shadowRadius = sizePx * 0.08f, shadowColor = 0x80000000.toInt())
    },
    TextPreset(R.string.tp_long_shadow, G_FUN) {
        copy(bold = true, shadowColor = 0xCC000000.toInt(), shadowDx = sizePx * 0.12f, shadowDy = sizePx * 0.12f)
    },
    TextPreset(R.string.tp_script, G_FUN, font = "Pacifico") {
        copy(shadowRadius = sizePx * 0.1f, shadowDy = sizePx * 0.05f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tp_terminal, G_FUN, font = "VT323") {
        copy(color = 0xFF39FF14.toInt(), boxColor = 0xFF0A0A0A.toInt(), boxPadding = sizePx * 0.3f, boxCornerRadius = sizePx * 0.1f)
    },

    // ---- Mas estilos ----
    TextPreset(R.string.tp_street, G_IMPACT, font = "Permanent Marker") {
        copy(outlineWidth = sizePx * 0.06f, outlineColor = BLACK, shadowColor = 0xAA000000.toInt(), shadowDx = sizePx * 0.05f, shadowDy = sizePx * 0.05f)
    },
    TextPreset(R.string.tp_poster, G_IMPACT, font = "Bungee") {
        copy(color = 0xFFFFD600.toInt(), allCaps = true, shadowColor = 0xFFD50000.toInt(), shadowDx = sizePx * 0.06f, shadowDy = sizePx * 0.06f, outlineWidth = sizePx * 0.03f, outlineColor = BLACK)
    },
    TextPreset(R.string.tp_horror, G_IMPACT, font = "Creepster") {
        copy(color = 0xFFB71C1C.toInt(), shadowRadius = sizePx * 0.2f, shadowColor = 0xFF000000.toInt(), outlineWidth = sizePx * 0.02f, outlineColor = BLACK)
    },
    TextPreset(R.string.tp_stencil, G_IMPACT, font = "Black Ops One") {
        copy(color = 0xFFC5E1A5.toInt(), allCaps = true, letterSpacingEm = 0.05f, shadowRadius = sizePx * 0.06f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tp_neon_yellow, G_NEON) {
        copy(color = 0xFFFFFDE6.toInt(), shadowRadius = sizePx * 0.3f, shadowColor = 0xFFFFEA00.toInt())
    },
    TextPreset(R.string.tp_neon_purple, G_NEON) {
        copy(color = 0xFFF3E6FF.toInt(), shadowRadius = sizePx * 0.3f, shadowColor = 0xFFB026FF.toInt())
    },
    TextPreset(R.string.tp_cyber, G_NEON, font = "Orbitron") {
        copy(color = 0xFF00E5FF.toInt(), bold = true, allCaps = true, letterSpacingEm = 0.08f, shadowColor = 0xFFFF2D95.toInt(), shadowDx = sizePx * 0.05f, shadowDy = sizePx * 0.03f)
    },
    TextPreset(R.string.tp_diner, G_NEON, font = "Lobster") {
        copy(color = 0xFFFFF8E1.toInt(), outlineWidth = sizePx * 0.05f, outlineColor = 0xFFD32F2F.toInt(), shadowColor = 0xFF1A237E.toInt(), shadowDx = sizePx * 0.06f, shadowDy = sizePx * 0.06f)
    },
    TextPreset(R.string.tp_candy, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFFFF8AD8.toInt(), 0xFFB388FF.toInt()), outlineWidth = sizePx * 0.02f, outlineColor = WHITE)
    },
    TextPreset(R.string.tp_mint, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFFB2FF59.toInt(), 0xFF00BFA5.toInt()))
    },
    TextPreset(R.string.tp_gold_gradient, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFFFFF3B0.toInt(), 0xFFFFC107.toInt(), 0xFF8D5B00.toInt()), outlineWidth = sizePx * 0.025f, outlineColor = 0xFF4E2F00.toInt())
    },
    TextPreset(R.string.tp_midnight, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFF7F7FD5.toInt(), 0xFF86A8E7.toInt(), 0xFF91EAE4.toInt()), shadowRadius = sizePx * 0.12f, shadowColor = 0x99283593.toInt())
    },
    TextPreset(R.string.tp_lava, G_GRADIENT) {
        copy(bold = true, allCaps = true, fill = grad(90f, 0xFFFFEB3B.toInt(), 0xFFFF5722.toInt(), 0xFF7F0000.toInt()), shadowRadius = sizePx * 0.16f, shadowColor = 0xCCFF3D00.toInt())
    },
    TextPreset(R.string.tp_label_blue, G_LABEL) {
        copy(bold = true, boxColor = 0xFF1565C0.toInt(), boxPadding = sizePx * 0.28f, boxCornerRadius = sizePx * 0.15f)
    },
    TextPreset(R.string.tp_label_green, G_LABEL) {
        copy(bold = true, boxColor = 0xFF2E7D32.toInt(), boxPadding = sizePx * 0.28f, boxCornerRadius = sizePx * 0.15f)
    },
    TextPreset(R.string.tp_tag_white, G_LABEL) {
        copy(color = 0xFF111111.toInt(), bold = true, boxColor = WHITE, boxPadding = sizePx * 0.28f, boxCornerRadius = sizePx * 0.1f, shadowRadius = sizePx * 0.1f, shadowDy = sizePx * 0.04f, shadowColor = 0x66000000)
    },
    TextPreset(R.string.tp_pop, G_FUN) {
        copy(bold = true, color = WHITE, outlineWidth = sizePx * 0.06f, outlineColor = 0xFFFF1F8E.toInt(), extraOutlines = listOf(TextOutline(sizePx * 0.05f, BLACK)))
    },
    TextPreset(R.string.tp_handwritten, G_FUN, font = "Caveat") {
        copy(shadowRadius = sizePx * 0.05f, shadowDy = sizePx * 0.03f, shadowColor = 0x66000000)
    },
    TextPreset(R.string.tp_typewriter, G_FUN, font = "Special Elite") {
        copy(color = 0xFF212121.toInt(), boxColor = 0xFFF5F0E1.toInt(), boxPadding = sizePx * 0.3f, boxCornerRadius = sizePx * 0.03f)
    },
    TextPreset(R.string.tp_gothic, G_FUN, font = "UnifrakturCook") {
        copy(allCaps = false, shadowRadius = sizePx * 0.1f, shadowDy = sizePx * 0.05f, shadowColor = 0xCC000000.toInt())
    },
    TextPreset(R.string.tp_kids, G_FUN, font = "Chewy") {
        copy(color = 0xFFFFEB3B.toInt(), outlineWidth = sizePx * 0.06f, outlineColor = 0xFFE91E63.toInt(), shadowColor = 0xFF1565C0.toInt(), shadowDx = sizePx * 0.05f, shadowDy = sizePx * 0.05f)
    },

    // ---- Mas estilos (tanda 2) ----
    TextPreset(R.string.tp_graffiti, G_IMPACT, font = "Rock Salt") {
        copy(color = WHITE, outlineWidth = sizePx * 0.05f, outlineColor = 0xFFFF1F8E.toInt(), shadowColor = BLACK, shadowDx = sizePx * 0.05f, shadowDy = sizePx * 0.05f)
    },
    TextPreset(R.string.tp_western, G_IMPACT, font = "Rye") {
        copy(color = 0xFFF5DEB3.toInt(), allCaps = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF4E2A0A.toInt(), shadowColor = 0xFF4E2A0A.toInt(), shadowDx = sizePx * 0.05f, shadowDy = sizePx * 0.05f)
    },
    TextPreset(R.string.tp_boxing, G_IMPACT, font = "Anton") {
        copy(color = 0xFFD50000.toInt(), allCaps = true, outlineWidth = sizePx * 0.05f, outlineColor = WHITE, shadowRadius = sizePx * 0.1f, shadowDy = sizePx * 0.05f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tp_noir, G_IMPACT, font = "Abril Fatface") {
        copy(color = WHITE, shadowRadius = sizePx * 0.18f, shadowColor = BLACK, outlineWidth = sizePx * 0.01f, outlineColor = 0xFF9E9E9E.toInt())
    },
    TextPreset(R.string.tp_neon_red, G_NEON) {
        copy(color = 0xFFFFE6E6.toInt(), shadowRadius = sizePx * 0.3f, shadowColor = 0xFFFF1744.toInt())
    },
    TextPreset(R.string.tp_neon_blue, G_NEON) {
        copy(color = 0xFFE6EEFF.toInt(), shadowRadius = sizePx * 0.3f, shadowColor = 0xFF2979FF.toInt())
    },
    TextPreset(R.string.tp_synthwave, G_NEON, font = "Audiowide") {
        copy(fill = grad(90f, 0xFFFF2D95.toInt(), 0xFFFFD600.toInt()), shadowRadius = sizePx * 0.25f, shadowColor = 0xCCB026FF.toInt())
    },
    TextPreset(R.string.tp_laser, G_NEON, font = "Orbitron") {
        copy(color = WHITE, bold = true, allCaps = true, shadowRadius = sizePx * 0.28f, shadowColor = 0xFFFF1744.toInt(), outlineWidth = sizePx * 0.015f, outlineColor = 0xFFFF1744.toInt())
    },
    TextPreset(R.string.tp_peach, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFFFFE0B2.toInt(), 0xFFFF8A65.toInt()))
    },
    TextPreset(R.string.tp_deep_ocean, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFF4DD0E1.toInt(), 0xFF01579B.toInt(), 0xFF0D1B4C.toInt()), outlineWidth = sizePx * 0.015f, outlineColor = 0x66FFFFFF)
    },
    TextPreset(R.string.tp_forest, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFFC5E1A5.toInt(), 0xFF43A047.toInt(), 0xFF1B5E20.toInt()))
    },
    TextPreset(R.string.tp_fire_ice, G_GRADIENT) {
        copy(bold = true, fill = grad(0f, 0xFFFF3D00.toInt(), 0xFFFFFFFF.toInt(), 0xFF00B0FF.toInt()))
    },
    TextPreset(R.string.tp_pastel, G_GRADIENT) {
        copy(bold = true, fill = grad(0f, 0xFFFFC1E3.toInt(), 0xFFC1D8FF.toInt(), 0xFFC8FFD4.toInt()), shadowRadius = sizePx * 0.06f, shadowColor = 0x55000000)
    },
    TextPreset(R.string.tp_holo, G_GRADIENT) {
        copy(bold = true, fill = grad(45f, 0xFFFF9AF0.toInt(), 0xFF9AD0FF.toInt(), 0xFF9AFFD0.toInt(), 0xFFFFF39A.toInt(), 0xFFFF9AF0.toInt()), outlineWidth = sizePx * 0.012f, outlineColor = 0x88FFFFFF.toInt())
    },
    TextPreset(R.string.tp_label_pink, G_LABEL) {
        copy(bold = true, boxColor = 0xFFEC407A.toInt(), boxPadding = sizePx * 0.28f, boxCornerRadius = sizePx * 0.2f)
    },
    TextPreset(R.string.tp_label_orange, G_LABEL) {
        copy(bold = true, boxColor = 0xFFFB8C00.toInt(), boxPadding = sizePx * 0.28f, boxCornerRadius = sizePx * 0.15f)
    },
    TextPreset(R.string.tp_label_gray, G_LABEL) {
        copy(boxColor = 0xCC424242.toInt(), boxPadding = sizePx * 0.28f, boxCornerRadius = sizePx * 0.12f)
    },
    TextPreset(R.string.tp_ribbon, G_LABEL) {
        copy(bold = true, allCaps = true, letterSpacingEm = 0.08f, boxColor = 0xFF6A1B9A.toInt(), boxPadding = sizePx * 0.32f, boxCornerRadius = sizePx * 0.04f)
    },
    TextPreset(R.string.tp_tape, G_LABEL) {
        copy(color = 0xFF3E2723.toInt(), boxColor = 0xB3FFE082.toInt(), boxPadding = sizePx * 0.26f, boxCornerRadius = sizePx * 0.02f)
    },
    TextPreset(R.string.tp_comic_book, G_FUN, font = "Bangers") {
        copy(color = WHITE, outlineWidth = sizePx * 0.05f, outlineColor = 0xFF1565C0.toInt(), extraOutlines = listOf(TextOutline(sizePx * 0.04f, BLACK)), letterSpacingEm = 0.04f)
    },
    TextPreset(R.string.tp_chalk, G_FUN, font = "Gloria Hallelujah") {
        copy(color = 0xFFF5F5F5.toInt(), shadowRadius = sizePx * 0.05f, shadowColor = 0x88FFFFFF.toInt(), boxColor = 0xFF2E3B32.toInt(), boxPadding = sizePx * 0.3f, boxCornerRadius = sizePx * 0.05f)
    },
    TextPreset(R.string.tp_wedding, G_FUN, font = "Great Vibes") {
        copy(color = 0xFFFFE9A8.toInt(), shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tp_thin, G_FUN, font = "Josefin Sans") {
        copy(allCaps = true, letterSpacingEm = 0.35f, shadowRadius = sizePx * 0.05f, shadowColor = 0x66000000)
    },
    TextPreset(R.string.tp_bold_serif, G_FUN, font = "Playfair Display") {
        copy(bold = true, shadowRadius = sizePx * 0.1f, shadowDy = sizePx * 0.05f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tp_marker_blue, G_FUN, font = "Permanent Marker") {
        copy(color = 0xFF1976D2.toInt(), outlineWidth = sizePx * 0.025f, outlineColor = WHITE)
    },

    // ---- Mas texturas y estilos (tanda 3) ----
    TextPreset(R.string.tex_copper, G_MATERIAL, texture = TextTexture.COPPER) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF4A210E.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_bronze, G_MATERIAL, texture = TextTexture.BRONZE) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF3B2508.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_rose_gold, G_MATERIAL, texture = TextTexture.ROSE_GOLD) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF6B3037.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_chrome, G_MATERIAL, texture = TextTexture.CHROME) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF1B1F26.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_emerald, G_MATERIAL, texture = TextTexture.EMERALD) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF063B27.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_lava, G_MATERIAL, texture = TextTexture.LAVA) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF1A0503.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_carbon, G_MATERIAL, texture = TextTexture.CARBON) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF000000.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_galaxy, G_MATERIAL, texture = TextTexture.GALAXY) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF0B0B2B.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_concrete, G_MATERIAL, texture = TextTexture.CONCRETE) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF3A3A3A.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_camo, G_MATERIAL, texture = TextTexture.CAMO) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF1C2010.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_water, G_MATERIAL, texture = TextTexture.WATER) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF063A5A.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_grass, G_MATERIAL, texture = TextTexture.GRASS) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF123A0E.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_holo, G_MATERIAL, texture = TextTexture.HOLO) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF5E4B8A.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tex_leather, G_MATERIAL, texture = TextTexture.LEATHER) {
        copy(bold = true, outlineWidth = sizePx * 0.03f, outlineColor = 0xFF1A0E06.toInt(),
            shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x99000000.toInt())
    },
    TextPreset(R.string.tp_sunrise, G_GRADIENT) {
        copy(bold = true, fill = grad(90f, 0xFFFFF176.toInt(), 0xFFFFA726.toInt(), 0xFFF06292.toInt()), outlineWidth = sizePx * 0.025f, outlineColor = WHITE)
    },
    TextPreset(R.string.tp_ink, G_FUN) {
        copy(color = BLACK, bold = true, outlineWidth = sizePx * 0.03f, outlineColor = WHITE, shadowColor = 0x99000000.toInt(), shadowDx = sizePx * 0.06f, shadowDy = sizePx * 0.06f)
    },
    TextPreset(R.string.tp_cotton, G_FUN) {
        copy(bold = true, color = 0xFFFFC1E3.toInt(), outlineWidth = sizePx * 0.05f, outlineColor = 0xFF8EC5FF.toInt(), shadowRadius = sizePx * 0.08f, shadowDy = sizePx * 0.04f, shadowColor = 0x66000000)
    },
)

/**
 * Estilos de texto listos (requisito del usuario): un toque aplica contorno, sombra,
 * degradado, textura o caja ya combinados. Cada miniatura es un render real del propio
 * estilo ("Aa"), no un dibujo aparte, asi que lo que se ve es lo que se obtiene.
 *
 * Los renders de las miniaturas se hacen una sola vez, fuera del hilo de la interfaz.
 */
@Composable
internal fun TextPresetsSection(
    vm: EditorViewModel,
    change: ((TextSpec) -> TextSpec) -> Unit,
) {
    val context = LocalContext.current
    val fonts = remember(context) { context.pixa.fonts }
    val scope = rememberCoroutineScope()
    val previews = remember { mutableStateMapOf<TextPreset, Bitmap>() }
    // Almacen propio para las texturas de las miniaturas: el del editor se vacia de lo que
    // el documento no referencia y reciclaria estos bitmaps.
    val previewStore = remember { BitmapStore() }
    DisposableEffect(Unit) { onDispose { previewStore.clear() } }

    LaunchedEffect(Unit) {
        val base = TextSpec(text = "Aa", sizePx = 64f, boxWidth = 220f, align = TextAlign.CENTER)
        val textures = HashMap<TextTexture, String>()
        suspend fun renderChip(preset: TextPreset) {
            val bmp = withContext(Dispatchers.Default) {
                runCatching {
                    var spec = preset.build(base.clean().let {
                        if (preset.font != null) it.copy(font = FontRef(preset.font, FontRef.Source.GOOGLE)) else it
                    })
                    preset.texture?.let { tex ->
                        val id = textures.getOrPut(tex) { previewStore.put(TextTextures.make(tex, 160)) }
                        spec = spec.copy(fill = FillSpec.Image(bitmapId = id))
                    }
                    TextRenderer.render(spec, fonts.cached(spec.font), previewStore)
                }.getOrNull()
            }
            if (bmp != null) previews[preset] = bmp
        }
        // Primero todas con lo que haya (asi no se espera a la red); despues, las que usan
        // una fuente de Google se repintan cuando esta llega. Sin conexion falla rapido.
        PRESETS.forEach { renderChip(it) }
        PRESETS.mapNotNull { it.font }.distinct().forEach { family ->
            if (fonts.load(FontRef(family = family, source = FontRef.Source.GOOGLE)) != null) {
                PRESETS.filter { it.font == family }.forEach { renderChip(it) }
            }
        }
    }

    fun apply(preset: TextPreset) {
        scope.launch {
            val fill = preset.texture?.let { tex ->
                val bmp = withContext(Dispatchers.Default) { TextTextures.make(tex) }
                FillSpec.Image(bitmapId = vm.putBitmap(bmp))
            }
            change { current ->
                var next = current.clean()
                if (preset.keepColor) next = next.copy(color = current.color)
                if (preset.font != null) {
                    next = next.copy(font = FontRef(preset.font, FontRef.Source.GOOGLE))
                }
                next = preset.build(next)
                if (fill != null) next = next.copy(fill = fill)
                next
            }
        }
    }

    var open by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Cabecera destacada: es una funcion principal y arranca plegada, asi que el
        // chevron va en un circulo de color y toda la fila tiene un fondo suave.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                .clickable { open = !open }
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.text_presets_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (!open) {
                Text(
                    stringResource(R.string.text_styles_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (open) GROUPS.forEach { (group, nameRes) ->
            Text(
                stringResource(nameRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PRESETS.filter { it.group == group }.forEach { preset ->
                    PresetChip(
                        label = stringResource(preset.nameRes),
                        preview = previews[preset],
                        onClick = { apply(preset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, preview: Bitmap?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                // Gris medio: se leen bien tanto los estilos claros como los oscuros.
                .background(androidx.compose.ui.graphics.Color(0xFF5C6168))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null && !preview.isRecycled) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
    }
}
