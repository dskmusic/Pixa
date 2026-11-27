package com.dskmusic.pixa.core.wordart

import android.content.Context
import androidx.annotation.StringRes
import com.dskmusic.pixa.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

@Serializable data class NamedConfig(val id: String, val name: String, val config: WordArtConfig)
@Serializable data class NamedText(val id: String, val name: String, val block: TextBlock)
@Serializable data class NamedBg(val id: String, val name: String, val words: BackgroundSpec, val other: BackgroundSpec)
@Serializable data class NamedFx(val id: String, val name: String, val fx: ShapeFx)

@Serializable
data class PresetBundle(
    val full: List<NamedConfig> = emptyList(),
    val text: List<NamedText> = emptyList(),
    val bg: List<NamedBg> = emptyList(),
    val shape: List<NamedFx> = emptyList(),
)

/** Presets propios, en un unico JSON local. Tambien sirve para exportar e importar. */
class PresetStore(context: Context) {
    private val root = File(context.filesDir, "wordart").apply { mkdirs() }
    private val file = File(root, "presets.json")
    private val thumbs = File(root, "thumbs").apply { mkdirs() }

    fun thumbFile(id: String) = File(thumbs, "$id.png")

    fun load(): PresetBundle =
        runCatching { WordArtConfig.json.decodeFromString<PresetBundle>(file.readText()) }.getOrDefault(PresetBundle())

    fun save(b: PresetBundle) {
        file.writeText(WordArtConfig.json.encodeToString(b))
    }

    fun deleteThumb(id: String) {
        thumbFile(id).delete()
    }

    fun export(b: PresetBundle): String = WordArtConfig.json.encodeToString(b)

    /** Fusiona un JSON importado dando ids nuevos, para no pisar presets existentes. */
    fun merge(current: PresetBundle, text: String): PresetBundle? {
        val inc = runCatching { WordArtConfig.json.decodeFromString<PresetBundle>(text) }.getOrNull() ?: return null
        fun id() = UUID.randomUUID().toString().take(8)
        return PresetBundle(
            full = current.full + inc.full.map { it.copy(id = id()) },
            text = current.text + inc.text.map { it.copy(id = id()) },
            bg = current.bg + inc.bg.map { it.copy(id = id()) },
            shape = current.shape + inc.shape.map { it.copy(id = id()) },
        )
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)
}

/** Presets que trae la app. Las palabras salen en el idioma del usuario. */
object BuiltinPresets {
    class Full(@StringRes val nameRes: Int, val config: WordArtConfig)
    class TextB(@StringRes val nameRes: Int, val block: TextBlock)
    class BgB(@StringRes val nameRes: Int, val words: BackgroundSpec, val other: BackgroundSpec)
    class FxB(@StringRes val nameRes: Int, val fx: ShapeFx)

    private fun words(id: String, es: Boolean) = WordLists.presets.first { it.id == id }.text(es)
    private fun pal(name: String) = WordArtConfig.PALETTES.first { it.first == name }.second
    private fun solid(c: Long) = BackgroundSpec(BgKind.SOLID, color = c.toInt())
    private fun grad(a: Long, b: Long, angle: Float = 90f) =
        BackgroundSpec(BgKind.GRADIENT, gradColors = listOf(a.toInt(), b.toInt()), angle = angle)

    fun full(es: Boolean): List<Full> {
        val base = WordArtConfig()
        return listOf(
            Full(R.string.wa_p_love, base.copy(
                shapeId = "geo:heart", wordsText = words("love", es), mainWord = if (es) "amor" else "love",
                palette = pal("love"), bgWords = solid(0xFFFFF0F3), seed = 7,
            )),
            Full(R.string.wa_p_star, base.copy(
                shapeId = "geo:star5", wordsText = words("motivation", es), colorMode = ColorMode.BY_WEIGHT,
                palette = pal("gold"), bgWords = grad(0xFF0D1B3E, 0xFF283593),
                fx = ShapeFx(glowOn = true, glowColor = 0xFFFFD54F.toInt(), glowRadius = 2.5f), seed = 11,
            )),
            Full(R.string.wa_p_cloud, base.copy(
                shapeId = "geo:cloud", wordsText = words("tech", es), colorMode = ColorMode.GRADIENT,
                color1 = 0xFF0D47A1.toInt(), color2 = 0xFF00ACC1.toInt(),
                bgWords = grad(0xFFE3F2FD, 0xFFBBDEFB), rotMode = RotMode.HORIZONTAL, seed = 3,
            )),
            Full(R.string.wa_p_drop, base.copy(
                shapeId = "geo:drop", wordsText = words("nature", es), palette = pal("forest"),
                bgWords = solid(0xFFF1F8E9), seed = 5,
            )),
            Full(R.string.wa_p_moon, base.copy(
                shapeId = "geo:moon", wordsText = words("friendship", es), palette = pal("pastel"),
                bgWords = solid(0xFF0B1026), rotMode = RotMode.ANGLES, rotAngles = "0,-30,30", seed = 9,
            )),
            Full(R.string.wa_p_birthday, base.copy(
                shapeId = "geo:circle", wordsText = words("birthday", es), colorMode = ColorMode.RANDOM,
                bgWords = BackgroundSpec(BgKind.PATTERN, color = 0xFFFFF3E0.toInt(), color2 = 0xFFFFFFFF.toInt(), pattern = PatternKind.DOTS, patternSize = 5f),
                fx = ShapeFx(borderOn = true, borderStyle = BorderStyle.DOTTED, borderColor = 0xFFFF9800.toInt(), borderWidth = 1.5f), seed = 13,
            )),
            Full(R.string.wa_p_inverted, base.copy(
                shapeId = "geo:heart", inverted = true, wordsText = words("love", es), palette = pal("love"),
                bgWords = solid(0xFF1B1B2F), bgOther = solid(0xFFFFCDD2), minSize = 2f, maxSize = 7f, seed = 21,
            )),
            Full(R.string.wa_p_music, base.copy(
                shapeId = "ph:music-notes", wordsText = words("music", es), palette = pal("neon"),
                bgWords = solid(0xFF0B0B14), textStyle = TextStyleSpec(bold = true), seed = 17,
            )),
        )
    }

    val texts = listOf(
        TextB(R.string.wa_t_plain, WordArtConfig().toTextBlock()),
        TextB(R.string.wa_t_shadow, WordArtConfig(textStyle = TextStyleSpec(bold = true, shadow = true)).toTextBlock()),
        TextB(R.string.wa_t_outline, WordArtConfig(
            textStyle = TextStyleSpec(bold = true, outline = true, outlineColor = 0xFFFFFFFF.toInt(), outlineWidth = 8f),
            colorMode = ColorMode.PALETTE,
        ).toTextBlock()),
        TextB(R.string.wa_t_neon, WordArtConfig(
            textStyle = TextStyleSpec(bold = true, glow = true, glowColor = 0xFFFFFFFF.toInt(), glowRadius = 30f),
            palette = pal("neon"),
        ).toTextBlock()),
    )

    val bgs = listOf(
        BgB(R.string.wa_b_none, BackgroundSpec(), BackgroundSpec()),
        BgB(R.string.wa_b_paper, solid(0xFFFDF6E3), BackgroundSpec()),
        BgB(R.string.wa_b_night, grad(0xFF0D1B3E, 0xFF1A237E), BackgroundSpec()),
        BgB(R.string.wa_b_dots, BackgroundSpec(BgKind.PATTERN, color = 0xFFE0E0E0.toInt(), color2 = 0xFFFFFFFF.toInt(), pattern = PatternKind.DOTS), BackgroundSpec()),
    )

    val fxs = listOf(
        FxB(R.string.wa_f_plain, ShapeFx()),
        FxB(R.string.wa_f_border, ShapeFx(borderOn = true, borderWidth = 1.6f)),
        FxB(R.string.wa_f_relief, ShapeFx(shadowOn = true, bevelOn = true)),
        FxB(R.string.wa_f_glow, ShapeFx(glowOn = true, glowRadius = 3.5f)),
    )
}
