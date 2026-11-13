package com.dskmusic.pixa.core.model

import kotlinx.serialization.Serializable

/**
 * Parametros de ajuste de imagen (requisito 13).
 *
 * Son destructivos: se usan como previsualizacion en vivo y al pulsar "Aplicar" se
 * hornean en el bitmap de la capa (recuperable con deshacer). Por eso no se guardan
 * dentro de la capa, solo en el estado temporal del editor y en los presets.
 *
 * Todos los valores van de -1 a 1 salvo los marcados como 0..1.
 */
@Serializable
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val exposure: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    /** 0..1 */
    val sharpen: Float = 0f,
    /** 0..1, se traduce a radio de desenfoque gaussiano. */
    val blur: Float = 0f,
    /** -1..1: negativo oscurece las esquinas, positivo las aclara. */
    val vignette: Float = 0f,
    /** 0..1 */
    val grain: Float = 0f,
    /** -1..1: contraste local de radio grande (claridad). Negativo suaviza. */
    val clarity: Float = 0f,
    /** -1..1: contraste local fino (textura). Negativo suaviza la piel. */
    val texture: Float = 0f,
    /** -1..1: quita (positivo) o anade (negativo) neblina. */
    val dehaze: Float = 0f,
    /** 0..1: reduccion de ruido que respeta los bordes. */
    val denoise: Float = 0f,
    /** 0..1: cuanto de la nitidez se limita a los bordes (0 = toda la imagen). */
    val sharpenEdges: Float = 0f,
    /** -1..1: distorsion de lente. Positivo corrige barril (lineas curvas hacia fuera), negativo corrige cojin. */
    val lens: Float = 0f,
) {
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && exposure == 0f && saturation == 0f &&
            vibrance == 0f && temperature == 0f && tint == 0f && shadows == 0f &&
            highlights == 0f && sharpen == 0f && blur == 0f && vignette == 0f && grain == 0f &&
            clarity == 0f && texture == 0f && dehaze == 0f && denoise == 0f && lens == 0f

    /** Escala la intensidad global, para el slider de intensidad de los filtros. */
    fun scaled(k: Float): Adjustments = Adjustments(
        brightness = brightness * k, contrast = contrast * k, exposure = exposure * k,
        saturation = saturation * k, vibrance = vibrance * k,
        temperature = temperature * k, tint = tint * k, shadows = shadows * k,
        highlights = highlights * k, sharpen = sharpen * k, blur = blur * k,
        vignette = vignette * k, grain = grain * k,
        clarity = clarity * k, texture = texture * k, dehaze = dehaze * k,
        denoise = denoise * k, sharpenEdges = sharpenEdges, lens = lens * k,
    )
}

/** Curva tonal: puntos de control ordenados por x, en 0..1 (requisito 13). */
@Serializable
data class CurvePoints(val points: List<Float> = listOf(0f, 0f, 1f, 1f)) {
    val isIdentity: Boolean get() = points.size == 4 &&
        points[0] == 0f && points[1] == 0f && points[2] == 1f && points[3] == 1f
}

@Serializable
data class CurvesSpec(
    val rgb: CurvePoints = CurvePoints(),
    val red: CurvePoints = CurvePoints(),
    val green: CurvePoints = CurvePoints(),
    val blue: CurvePoints = CurvePoints(),
) {
    val isIdentity: Boolean get() = rgb.isIdentity && red.isIdentity && green.isIdentity && blue.isIdentity
}

/** Niveles por canal. */
@Serializable
data class LevelsSpec(
    val black: Float = 0f,
    val white: Float = 1f,
    val gamma: Float = 1f,
) {
    val isIdentity: Boolean get() = black == 0f && white == 1f && gamma == 1f
}

/** Rangos de color para el ajuste HSL por color (requisito 13). */
enum class HslBand(val centerHue: Float) {
    RED(0f), ORANGE(30f), YELLOW(60f), GREEN(120f),
    CYAN(180f), BLUE(240f), PURPLE(280f), MAGENTA(320f),
}

@Serializable
data class HslAdjust(
    val hueShift: Float = 0f,
    val satScale: Float = 0f,
    val lumScale: Float = 0f,
) {
    val isIdentity: Boolean get() = hueShift == 0f && satScale == 0f && lumScale == 0f
}

/** Efectos de un solo paso (requisito 13). */
enum class EffectKind {
    // Requisito del usuario: glitch y television estropeada al principio, pixelado al
    // final. El orden de declaracion es el orden en que se ven en el panel; se puede
    // reordenar sin romper los proyectos guardados porque se serializa por nombre, no
    // por posicion.
    NONE, GLITCH, BROKEN_TV, PIXELATE,
    BW, SEPIA, INVERT, THRESHOLD, POSTERIZE,
    DUOTONE, RGB_SPLIT, HALFTONE, VINTAGE, MOTION_BLUR, RADIAL_BLUR, GAUSSIAN_BLUR,
    SOLARIZE, SCANLINES, MIRROR,
    // Ampliacion (ver MoreEffects): lente y luz, arte, clima, color y cielo.
    FILM_GRAIN, VIGNETTE, CHROMATIC, BLOOM, TILT_SHIFT, BOKEH, LIGHT_LEAK, BURNED_EDGES,
    SKETCH, COMIC, OIL, WATERCOLOR, MOSAIC, SCATTER,
    SPEED_LINES, RAIN, SNOW, SPARKLES,
    DOUBLE_EXPOSURE, ANAGLYPH, HEATMAP, SENSOR_NOISE,
    SKY_GOLDEN, SKY_NIGHT, SKY_DRAMATIC, SKY_BLUE,
    SPLIT_TONE,
    // Tanda 3.
    EMBOSS, DREAMY, LOMO, CROSS_PROCESS, SWIRL,
    // Tanda 4.
    OLD_PHOTO,
}

@Serializable
data class EffectSpec(
    val kind: EffectKind = EffectKind.NONE,
    /** 0..1, significado segun el efecto. */
    val amount: Float = 0.5f,
    val angle: Float = 0f,
    val colorA: Int = 0xFF1B2B4B.toInt(),
    val colorB: Int = 0xFFF5A623.toInt(),
    val levels: Int = 5,
    /** Semilla del patron aleatorio (glitch, television estropeada). */
    val seed: Long = 0L,
    /** 0..1: rayones, polvo y manchas (foto antigua). */
    val damage: Float = 0.5f,
    /** 0..1: de plata (frio) a amarillento (calido) (foto antigua). */
    val tone: Float = 0.5f,
)

/**
 * Preset guardado por el usuario: una combinacion con nombre de ajustes y efecto,
 * reutilizable en otras fotos y desde el editor por lotes (requisito 13).
 */
@Serializable
data class AdjustPreset(
    val id: String,
    val name: String,
    val adjustments: Adjustments = Adjustments(),
    val effect: EffectSpec = EffectSpec(),
    val curves: CurvesSpec = CurvesSpec(),
    val levels: LevelsSpec = LevelsSpec(),
)

/**
 * Filtros predefinidos con miniatura e intensidad ajustable (requisito 13).
 * 18 entradas incluyendo "Original".
 */
data class BuiltInFilter(
    val id: String,
    val labelRes: Int,
    val adjustments: Adjustments = Adjustments(),
    val effect: EffectSpec = EffectSpec(),
    val category: FilterCategory = FilterCategory.BASIC,
)

/** Packs de filtros de fabrica; MY_PRESETS y LUT los rellena el panel con datos del usuario. */
enum class FilterCategory { BASIC, PORTRAIT, LANDSCAPE, FOOD, CINEMA, ANALOG, RETRO, NEON, SKY, LUT, MY_PRESETS }
