package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.AdjustPreset
import com.dskmusic.pixa.core.model.Adjustments
import com.dskmusic.pixa.core.model.BuiltInFilter
import com.dskmusic.pixa.core.model.EffectKind
import com.dskmusic.pixa.core.model.EffectSpec
import com.dskmusic.pixa.core.model.FilterCategory
import kotlin.math.roundToInt

/**
 * Catalogo de filtros predefinidos y tuberia de aplicacion (requisito 13).
 *
 * Cada filtro es solo una combinacion de ajustes y, como maximo, un efecto. Al ser datos
 * y no codigo, la intensidad ajustable sale gratis: se escala la combinacion con
 * [Adjustments.scaled] y el efecto con su propio amount.
 */
object Filters {

    /** Filtros basicos, el primero sin cambios para poder volver al original. */
    private val basic: List<BuiltInFilter> = listOf(
        BuiltInFilter("original", R.string.filter_none),
        BuiltInFilter(
            "vivid", R.string.filter_vivid,
            Adjustments(contrast = 0.18f, saturation = 0.22f, vibrance = 0.25f, sharpen = 0.2f),
        ),
        BuiltInFilter(
            "soft", R.string.filter_soft,
            Adjustments(contrast = -0.12f, brightness = 0.06f, saturation = -0.08f, highlights = 0.15f),
        ),
        BuiltInFilter(
            "bright", R.string.filter_bright,
            Adjustments(exposure = 0.18f, brightness = 0.08f, shadows = 0.25f, vibrance = 0.12f),
        ),
        BuiltInFilter(
            "bw", R.string.filter_bw,
            Adjustments(contrast = 0.1f),
            EffectSpec(EffectKind.BW, amount = 1f),
        ),
        BuiltInFilter(
            "bw_hard", R.string.filter_bw_hard,
            Adjustments(contrast = 0.35f, shadows = -0.2f, sharpen = 0.25f),
            EffectSpec(EffectKind.BW, amount = 1f),
        ),
        BuiltInFilter(
            "noir", R.string.filter_noir,
            Adjustments(contrast = 0.28f, shadows = -0.3f, vignette = -0.45f, grain = 0.2f),
            EffectSpec(EffectKind.BW, amount = 1f),
        ),
        BuiltInFilter(
            "sepia", R.string.filter_sepia,
            Adjustments(contrast = 0.08f),
            EffectSpec(EffectKind.SEPIA, amount = 1f),
        ),
        BuiltInFilter(
            "vintage", R.string.filter_vintage,
            effect = EffectSpec(EffectKind.VINTAGE, amount = 0.8f),
        ),
        BuiltInFilter(
            "retro", R.string.filter_retro,
            Adjustments(saturation = -0.15f, grain = 0.35f, vignette = -0.3f, temperature = 0.12f),
            EffectSpec(EffectKind.VINTAGE, amount = 0.45f),
        ),
        BuiltInFilter(
            "warm", R.string.filter_warm,
            Adjustments(temperature = 0.3f, vibrance = 0.1f, highlights = -0.08f),
        ),
        BuiltInFilter(
            "cool", R.string.filter_cool,
            Adjustments(temperature = -0.3f, tint = -0.08f, vibrance = 0.08f),
        ),
        BuiltInFilter(
            "fade", R.string.filter_fade,
            Adjustments(contrast = -0.2f, saturation = -0.25f, shadows = 0.3f, brightness = 0.05f),
        ),
        BuiltInFilter(
            "matte", R.string.filter_matte,
            Adjustments(contrast = -0.1f, shadows = 0.22f, highlights = -0.18f, saturation = -0.12f),
        ),
        BuiltInFilter(
            "drama", R.string.filter_drama,
            Adjustments(
                contrast = 0.3f, shadows = -0.25f, highlights = -0.2f,
                sharpen = 0.35f, vignette = -0.25f,
            ),
        ),
        BuiltInFilter(
            "sunset", R.string.filter_sunset,
            Adjustments(temperature = 0.35f, tint = 0.1f, saturation = 0.18f, highlights = -0.12f),
        ),
        BuiltInFilter(
            "forest", R.string.filter_forest,
            Adjustments(temperature = -0.15f, saturation = 0.2f, shadows = 0.1f, contrast = 0.12f),
        ),
        BuiltInFilter(
            "cyber", R.string.filter_cyber,
            Adjustments(contrast = 0.2f),
            EffectSpec(
                EffectKind.DUOTONE,
                colorA = 0xFF0B1033.toInt(),
                colorB = 0xFF35F2E8.toInt(),
            ),
        ),
        BuiltInFilter(
            "invert", R.string.filter_invert,
            effect = EffectSpec(EffectKind.INVERT, amount = 1f),
        ),
        BuiltInFilter(
            "threshold", R.string.filter_threshold,
            Adjustments(contrast = 0.2f),
            EffectSpec(EffectKind.THRESHOLD, amount = 1f),
        ),
        BuiltInFilter(
            "posterize", R.string.filter_posterize,
            Adjustments(saturation = 0.1f, contrast = 0.12f),
            EffectSpec(EffectKind.POSTERIZE, amount = 1f, levels = 5),
        ),
        BuiltInFilter(
            "solarize", R.string.filter_solarize,
            Adjustments(contrast = 0.15f),
            EffectSpec(EffectKind.SOLARIZE, amount = 1f),
        ),
        BuiltInFilter(
            "glitch", R.string.filter_glitch,
            Adjustments(contrast = 0.15f, saturation = 0.15f),
            EffectSpec(EffectKind.GLITCH, amount = 1f),
        ),
        BuiltInFilter(
            "broken_tv", R.string.filter_broken_tv,
            Adjustments(contrast = 0.1f, grain = 0.15f),
            EffectSpec(EffectKind.BROKEN_TV, amount = 1f),
        ),
        BuiltInFilter(
            "pixelate", R.string.filter_pixelate,
            Adjustments(contrast = 0.08f, saturation = 0.1f),
            EffectSpec(EffectKind.PIXELATE, amount = 0.5f),
        ),
        BuiltInFilter(
            "rgb_split", R.string.filter_rgb_split,
            Adjustments(contrast = 0.12f, saturation = 0.1f),
            EffectSpec(EffectKind.RGB_SPLIT, amount = 1f),
        ),
        BuiltInFilter(
            "halftone", R.string.filter_halftone,
            Adjustments(contrast = 0.15f),
            EffectSpec(EffectKind.HALFTONE, amount = 1f),
        ),
        BuiltInFilter(
            "scanlines", R.string.filter_scanlines,
            Adjustments(contrast = 0.1f),
            EffectSpec(EffectKind.SCANLINES, amount = 1f),
        ),
        BuiltInFilter(
            "cyanotype", R.string.filter_cyanotype,
            Adjustments(contrast = 0.15f),
            EffectSpec(
                EffectKind.DUOTONE,
                colorA = 0xFF0A1E33.toInt(),
                colorB = 0xFF7FB2D9.toInt(),
            ),
        ),
        BuiltInFilter(
            "neon_purple", R.string.filter_neon_purple,
            Adjustments(contrast = 0.2f, saturation = 0.2f, vibrance = 0.15f),
            EffectSpec(
                EffectKind.DUOTONE,
                colorA = 0xFF1A0B2E.toInt(),
                colorB = 0xFFE14BF0.toInt(),
            ),
        ),
        BuiltInFilter(
            "old_gold", R.string.filter_old_gold,
            Adjustments(temperature = 0.2f, saturation = 0.12f, contrast = 0.18f, vignette = -0.2f),
            EffectSpec(EffectKind.SEPIA, amount = 1f),
        ),
    )

    /** Basicos mas los packs de fabrica ([FactoryPresets]). */
    val all: List<BuiltInFilter> = basic + FactoryPresets.all

    fun byId(id: String): BuiltInFilter? = all.firstOrNull { it.id == id }

    fun byCategory(category: FilterCategory): List<BuiltInFilter> =
        all.filter { it.category == category }

    /**
     * Mezcla [filtered] sobre [original] con opacidad [k] (0..1) y libera [filtered]. Es el
     * deslizador de intensidad de los looks que no son datos escalables: LUTs y presets del
     * usuario con curvas.
     */
    fun mix(original: Bitmap, filtered: Bitmap, k: Float): Bitmap {
        val t = k.coerceIn(0f, 1f)
        if (t >= 1f) return filtered
        val out = BitmapUtils.mutableCopy(original)
        if (t > 0f) {
            val paint = Paint().apply { alpha = (t * 255f).roundToInt() }
            Canvas(out).drawBitmap(filtered, 0f, 0f, paint)
        }
        filtered.recycle()
        return out
    }

    /**
     * Aplica un filtro con intensidad [strength] (0..1). Se escalan los ajustes y, si el
     * filtro lleva efecto, tambien su amount, de modo que al 0 el resultado es el original.
     */
    fun apply(src: Bitmap, filter: BuiltInFilter, strength: Float = 1f): Bitmap {
        val k = strength.coerceIn(0f, 1f)
        if (k <= 0f || filter.id == "original") return BitmapUtils.mutableCopy(src)

        var out = Adjust.apply(src, filter.adjustments.scaled(k))
        if (filter.effect.kind != EffectKind.NONE) {
            val effected = Effects.apply(out, filter.effect.copy(amount = filter.effect.amount * k))
            out.recycle()
            out = effected
        }
        return out
    }

    /**
     * Aplica un preset del usuario completo: ajustes, efecto, curvas y niveles, en el
     * mismo orden en que los ve en el editor.
     */
    fun applyPreset(src: Bitmap, preset: AdjustPreset): Bitmap {
        var out = Adjust.apply(src, preset.adjustments)

        if (!preset.curves.isIdentity) {
            val next = ToneOps.applyCurves(out, preset.curves)
            out.recycle()
            out = next
        }
        if (!preset.levels.isIdentity) {
            val next = ToneOps.applyLevels(out, preset.levels)
            out.recycle()
            out = next
        }
        if (preset.effect.kind != EffectKind.NONE) {
            val next = Effects.apply(out, preset.effect)
            out.recycle()
            out = next
        }
        return out
    }
}
