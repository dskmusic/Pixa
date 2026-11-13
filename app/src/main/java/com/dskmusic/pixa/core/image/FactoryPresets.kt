package com.dskmusic.pixa.core.image

import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.model.Adjustments
import com.dskmusic.pixa.core.model.BuiltInFilter
import com.dskmusic.pixa.core.model.EffectKind
import com.dskmusic.pixa.core.model.EffectSpec
import com.dskmusic.pixa.core.model.FilterCategory

/**
 * Presets de fabrica agrupados por tema. Como los filtros basicos son solo datos: una
 * combinacion de [Adjustments] y, como mucho, un efecto. La intensidad la da el mismo
 * deslizador de [Filters.apply], que escala ambas cosas.
 *
 * Los tonos divididos (SPLIT_TONE) llevan el color de las sombras en `colorA` y el de las
 * luces en `colorB`.
 */
object FactoryPresets {

    private fun p(
        id: String,
        label: Int,
        cat: FilterCategory,
        adj: Adjustments = Adjustments(),
        fx: EffectSpec = EffectSpec(),
    ) = BuiltInFilter(id, label, adj, fx, cat)

    private fun split(shadow: Long, light: Long, amount: Float) =
        EffectSpec(EffectKind.SPLIT_TONE, amount = amount, colorA = shadow.toInt(), colorB = light.toInt())

    private fun fx(kind: EffectKind, amount: Float, angle: Float = 0f) =
        EffectSpec(kind, amount = amount, angle = angle)

    val all: List<BuiltInFilter> = listOf(
        // ---- Retrato ---------------------------------------------------------------
        p("p_soft_skin", R.string.fp_soft_skin, FilterCategory.PORTRAIT,
            Adjustments(texture = -0.45f, clarity = -0.15f, brightness = 0.03f, shadows = 0.1f,
                highlights = -0.05f, denoise = 0.25f, vibrance = 0.05f)),
        p("p_warm", R.string.fp_warm_portrait, FilterCategory.PORTRAIT,
            Adjustments(temperature = 0.22f, tint = 0.04f, vibrance = 0.12f, shadows = 0.1f,
                highlights = -0.08f, texture = -0.2f)),
        p("p_glow", R.string.fp_glow, FilterCategory.PORTRAIT,
            Adjustments(brightness = 0.04f, contrast = -0.05f, texture = -0.25f),
            fx(EffectKind.BLOOM, 0.45f)),
        p("p_clean", R.string.fp_clean_studio, FilterCategory.PORTRAIT,
            Adjustments(exposure = 0.08f, contrast = 0.08f, shadows = 0.18f, highlights = -0.1f,
                temperature = 0.03f, denoise = 0.2f, sharpen = 0.15f, sharpenEdges = 0.8f)),
        p("p_bw", R.string.fp_portrait_bw, FilterCategory.PORTRAIT,
            Adjustments(contrast = 0.22f, clarity = 0.25f, vignette = -0.25f, grain = 0.1f),
            fx(EffectKind.BW, 1f)),

        // ---- Paisaje ---------------------------------------------------------------
        p("l_golden", R.string.fp_golden_hour, FilterCategory.LANDSCAPE,
            Adjustments(temperature = 0.34f, tint = 0.06f, contrast = 0.12f, saturation = 0.18f,
                highlights = -0.15f, clarity = 0.2f, vignette = -0.15f)),
        p("l_forest", R.string.fp_deep_forest, FilterCategory.LANDSCAPE,
            Adjustments(temperature = -0.1f, saturation = 0.25f, contrast = 0.15f, clarity = 0.3f,
                shadows = 0.12f, dehaze = 0.2f)),
        p("l_vivid", R.string.fp_vivid_nature, FilterCategory.LANDSCAPE,
            Adjustments(saturation = 0.2f, vibrance = 0.3f, contrast = 0.14f, clarity = 0.22f,
                dehaze = 0.15f, sharpen = 0.2f, sharpenEdges = 0.6f)),
        p("l_alpine", R.string.fp_alpine, FilterCategory.LANDSCAPE,
            Adjustments(temperature = -0.2f, clarity = 0.35f, dehaze = 0.35f, contrast = 0.12f,
                saturation = 0.1f, sharpen = 0.3f, sharpenEdges = 0.7f)),
        p("l_desert", R.string.fp_desert, FilterCategory.LANDSCAPE,
            Adjustments(temperature = 0.28f, saturation = -0.05f, contrast = 0.2f, clarity = 0.25f,
                highlights = -0.1f, shadows = 0.1f)),

        // ---- Comida ----------------------------------------------------------------
        p("f_fresh", R.string.fp_fresh, FilterCategory.FOOD,
            Adjustments(saturation = 0.28f, vibrance = 0.25f, brightness = 0.04f, contrast = 0.12f,
                temperature = 0.1f, clarity = 0.15f)),
        p("f_cozy", R.string.fp_cozy, FilterCategory.FOOD,
            Adjustments(temperature = 0.3f, saturation = 0.15f, contrast = 0.1f, shadows = 0.12f,
                vignette = -0.2f, clarity = 0.1f)),
        p("f_rustic", R.string.fp_rustic, FilterCategory.FOOD,
            Adjustments(temperature = 0.18f, contrast = 0.22f, saturation = -0.05f, texture = 0.3f,
                clarity = 0.25f, vignette = -0.25f, grain = 0.12f)),

        // ---- Cine ------------------------------------------------------------------
        p("c_teal_orange", R.string.fp_teal_orange, FilterCategory.CINEMA,
            Adjustments(contrast = 0.15f, saturation = 0.1f, vibrance = 0.1f),
            split(0xFF0F6F7F, 0xFFFF9A3C, 0.85f)),
        p("c_blockbuster", R.string.fp_blockbuster, FilterCategory.CINEMA,
            Adjustments(contrast = 0.25f, clarity = 0.3f, vignette = -0.2f, saturation = -0.05f,
                shadows = -0.1f),
            split(0xFF0F6F7F, 0xFFFF9A3C, 0.55f)),
        p("c_neo_noir", R.string.fp_neo_noir, FilterCategory.CINEMA,
            Adjustments(contrast = 0.38f, shadows = -0.28f, highlights = 0.1f, vignette = -0.45f,
                grain = 0.22f, clarity = 0.2f),
            fx(EffectKind.BW, 1f)),
        p("c_kodak", R.string.fp_kodak, FilterCategory.CINEMA,
            Adjustments(temperature = 0.22f, tint = 0.05f, saturation = 0.1f, contrast = 0.12f,
                shadows = 0.1f, grain = 0.22f, vignette = -0.15f),
            split(0xFF3A2A10, 0xFFFFD28A, 0.4f)),
        p("c_fuji", R.string.fp_fuji, FilterCategory.CINEMA,
            Adjustments(saturation = 0.3f, vibrance = 0.15f, contrast = 0.18f, tint = -0.05f,
                temperature = -0.04f, clarity = 0.1f, grain = 0.12f),
            split(0xFF0B3B3F, 0xFFFFE9B0, 0.3f)),
        p("c_scifi", R.string.fp_scifi_green, FilterCategory.CINEMA,
            Adjustments(tint = -0.5f, temperature = -0.15f, contrast = 0.2f, saturation = -0.1f)),
        p("c_bleach", R.string.fp_bleach_bypass, FilterCategory.CINEMA,
            Adjustments(saturation = -0.45f, contrast = 0.35f, clarity = 0.3f, highlights = -0.05f,
                grain = 0.15f)),

        // ---- Pelicula analogica ----------------------------------------------------
        p("a_film400", R.string.fp_film_400, FilterCategory.ANALOG,
            Adjustments(contrast = 0.08f, saturation = -0.08f, shadows = 0.1f, temperature = 0.06f),
            fx(EffectKind.FILM_GRAIN, 0.55f)),
        p("a_leaks", R.string.fp_light_leaks, FilterCategory.ANALOG,
            Adjustments(contrast = -0.05f, saturation = 0.05f, grain = 0.1f),
            fx(EffectKind.LIGHT_LEAK, 0.8f, angle = 20f)),
        p("a_burned", R.string.fp_burned_edges, FilterCategory.ANALOG,
            Adjustments(temperature = 0.15f, grain = 0.15f),
            fx(EffectKind.BURNED_EDGES, 0.7f)),
        p("a_expired", R.string.fp_expired_film, FilterCategory.ANALOG,
            Adjustments(contrast = -0.12f, saturation = -0.1f, grain = 0.35f, temperature = 0.1f,
                tint = 0.08f),
            split(0xFF2B5A3C, 0xFFF5C77A, 0.7f)),
        p("a_cross", R.string.fp_cross_process, FilterCategory.ANALOG,
            Adjustments(contrast = 0.25f, saturation = 0.25f),
            split(0xFF1CA7B8, 0xFFF7E04A, 0.9f)),
        p("a_disposable", R.string.fp_disposable, FilterCategory.ANALOG,
            Adjustments(contrast = 0.22f, saturation = 0.12f, brightness = 0.05f, vignette = -0.4f,
                grain = 0.3f, temperature = 0.08f, sharpen = 0.2f)),
        p("a_instant", R.string.fp_instant, FilterCategory.ANALOG,
            Adjustments(contrast = -0.08f, shadows = 0.22f, highlights = -0.12f, saturation = -0.12f,
                temperature = 0.12f, tint = 0.06f, vignette = -0.18f, grain = 0.18f)),

        // ---- Retro -----------------------------------------------------------------
        p("r_70s", R.string.fp_70s, FilterCategory.RETRO,
            Adjustments(temperature = 0.3f, tint = 0.08f, saturation = -0.08f, contrast = -0.08f,
                shadows = 0.22f, grain = 0.22f, vignette = -0.2f),
            split(0xFF5A2A10, 0xFFFFB347, 0.6f)),
        p("r_80s", R.string.fp_80s, FilterCategory.RETRO,
            Adjustments(contrast = 0.12f, saturation = 0.15f, grain = 0.1f),
            split(0xFF2B1B6B, 0xFFFF6FB1, 0.7f)),
        p("r_vhs", R.string.fp_vhs, FilterCategory.RETRO,
            Adjustments(saturation = -0.1f, contrast = -0.05f, grain = 0.3f, blur = 0.04f,
                temperature = 0.05f),
            fx(EffectKind.RGB_SPLIT, 0.35f)),
        p("r_polaroid", R.string.fp_polaroid_look, FilterCategory.RETRO,
            Adjustments(contrast = -0.18f, saturation = -0.2f, shadows = 0.3f, temperature = 0.15f,
                tint = 0.05f, brightness = 0.06f, vignette = -0.15f),
            fx(EffectKind.VINTAGE, 0.3f)),
        p("r_dag", R.string.fp_daguerreotype, FilterCategory.RETRO,
            Adjustments(contrast = 0.15f, vignette = -0.35f, grain = 0.2f),
            fx(EffectKind.SEPIA, 0.85f)),

        // ---- Cyberpunk, neon y pastel ---------------------------------------------
        p("n_cyberpunk", R.string.fp_cyberpunk, FilterCategory.NEON,
            Adjustments(contrast = 0.25f, saturation = 0.3f, vibrance = 0.2f, clarity = 0.25f),
            split(0xFF3A0CA3, 0xFFFF2E93, 0.85f)),
        p("n_neon_night", R.string.fp_neon_nights, FilterCategory.NEON,
            Adjustments(contrast = 0.3f, saturation = 0.35f),
            split(0xFF00C2FF, 0xFFFF2BD6, 0.9f)),
        p("n_synthwave", R.string.fp_synthwave, FilterCategory.NEON,
            Adjustments(contrast = 0.2f, saturation = 0.25f, vignette = -0.25f),
            split(0xFF2A0A5E, 0xFFFF5EA8, 0.85f)),
        p("n_vapor", R.string.fp_vaporwave, FilterCategory.NEON,
            Adjustments(contrast = 0.1f, saturation = 0.15f, temperature = -0.05f, tint = 0.15f,
                grain = 0.1f),
            split(0xFF5B2A86, 0xFF7FFFEA, 0.8f)),
        p("n_pastel", R.string.fp_pastel, FilterCategory.NEON,
            Adjustments(brightness = 0.08f, contrast = -0.22f, saturation = -0.1f, shadows = 0.35f,
                highlights = -0.1f, temperature = -0.04f, tint = 0.04f, vibrance = 0.15f),
            split(0xFFB8E0F0, 0xFFFFD6E8, 0.35f)),
        p("n_candy", R.string.fp_candy, FilterCategory.NEON,
            Adjustments(saturation = 0.35f, vibrance = 0.25f, brightness = 0.08f, contrast = -0.1f,
                tint = 0.12f, temperature = 0.05f),
            split(0xFFB89BFF, 0xFFFFB6D0, 0.35f)),

        // ---- Cielo (heuristica de color y posicion, ver MoreEffects.skyMask) -------
        p("s_golden", R.string.fp_sky_golden, FilterCategory.SKY, fx = fx(EffectKind.SKY_GOLDEN, 0.85f)),
        p("s_night", R.string.fp_sky_night, FilterCategory.SKY, fx = fx(EffectKind.SKY_NIGHT, 0.9f)),
        p("s_dramatic", R.string.fp_sky_dramatic, FilterCategory.SKY,
            Adjustments(clarity = 0.2f), fx(EffectKind.SKY_DRAMATIC, 0.85f)),
        p("s_blue", R.string.fp_sky_blue, FilterCategory.SKY, fx = fx(EffectKind.SKY_BLUE, 0.8f)),

        // ---- Tanda 3 ----------------------------------------------------------------
        p("p_golden_skin", R.string.fp_golden_skin, FilterCategory.PORTRAIT,
            Adjustments(temperature = 0.18f, tint = 0.03f, brightness = 0.05f, saturation = 0.06f,
                texture = -0.3f, highlights = -0.05f),
            split(0xFF8A4B2D, 0xFFFFE0B0, 0.25f)),
        p("p_cool_studio", R.string.fp_cool_studio, FilterCategory.PORTRAIT,
            Adjustments(temperature = -0.12f, contrast = 0.1f, shadows = 0.12f, clarity = 0.12f,
                denoise = 0.15f)),
        p("l_misty", R.string.fp_misty_morning, FilterCategory.LANDSCAPE,
            Adjustments(dehaze = -0.25f, brightness = 0.06f, contrast = -0.12f, temperature = -0.06f,
                saturation = -0.1f, shadows = 0.2f)),
        p("l_dune", R.string.fp_dune, FilterCategory.LANDSCAPE,
            Adjustments(temperature = 0.3f, saturation = 0.15f, contrast = 0.16f, clarity = 0.25f,
                highlights = -0.1f)),
        p("f_fresh", R.string.fp_fresh_food, FilterCategory.FOOD,
            Adjustments(vibrance = 0.3f, saturation = 0.15f, brightness = 0.06f, contrast = 0.08f,
                clarity = 0.15f, temperature = 0.04f)),
        p("c_noir_blue", R.string.fp_noir_blue, FilterCategory.CINEMA,
            Adjustments(contrast = 0.25f, saturation = -0.4f, vignette = -0.3f, grain = 0.08f),
            split(0xFF0B1A3A, 0xFFB7C7E8, 0.5f)),
        p("a_warm_film", R.string.fp_warm_film, FilterCategory.ANALOG,
            Adjustments(temperature = 0.18f, saturation = -0.08f, contrast = 0.06f, grain = 0.18f,
                vignette = -0.18f, highlights = -0.1f),
            split(0xFF3B2A1E, 0xFFFFE6B5, 0.3f)),
    )
}
