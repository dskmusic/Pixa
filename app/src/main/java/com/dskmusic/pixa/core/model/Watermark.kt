package com.dskmusic.pixa.core.model

import kotlinx.serialization.Serializable

/** Las nueve posiciones de la rejilla, mas libre (requisito 15). */
@Serializable
enum class Anchor9 {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
    FREE,
    ;

    /** Posicion relativa 0..1 dentro del lienzo. FREE usa el desplazamiento del spec. */
    val fractionX: Float
        get() = when (this) {
            TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> 0f
            TOP_CENTER, CENTER, BOTTOM_CENTER -> 0.5f
            TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> 1f
            FREE -> 0.5f
        }

    val fractionY: Float
        get() = when (this) {
            TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0f
            MIDDLE_LEFT, CENTER, MIDDLE_RIGHT -> 0.5f
            BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> 1f
            FREE -> 0.5f
        }
}

@Serializable
enum class WatermarkKind { TEXT, LOGO }

/**
 * Marca de agua guardada como preset (requisito 15).
 *
 * El tamano se guarda en porcentaje del lado mayor de la imagen, no en pixeles: asi el
 * mismo preset se ve igual en una foto de 1000 px y en una de 4000 px, que es justo lo que
 * se espera de una marca reutilizable.
 */
@Serializable
data class WatermarkSpec(
    val id: String,
    val name: String,
    val kind: WatermarkKind = WatermarkKind.TEXT,
    val text: String = "",
    val font: FontRef = FontRef(),
    val color: Int = 0xFFFFFFFF.toInt(),
    /** Nombre del archivo del logo dentro de los datos de usuario. */
    val logoFile: String? = null,
    /** Tamano en porcentaje del lado mayor de la imagen (1..60). */
    val sizePercent: Float = 12f,
    val opacity: Float = 0.6f,
    val anchor: Anchor9 = Anchor9.BOTTOM_RIGHT,
    /** Desplazamiento relativo 0..1, tambien usado como posicion absoluta con FREE. */
    val offsetX: Float = 0.04f,
    val offsetY: Float = 0.04f,
    val rotation: Float = 0f,
    /** Repetir en mosaico por toda la imagen. */
    val tiled: Boolean = false,
    /** Separacion del mosaico en porcentaje del lado mayor. */
    val tileSpacing: Float = 25f,
    val shadow: Boolean = true,
)

/** Estilos de marco decorativo (requisito 15). */
@Serializable
enum class FrameKind {
    NONE, SOLID, ROUNDED, DOUBLE, DASHED, INSET, POLAROID, FILM, VIGNETTE_FRAME, CORNERS,
    // Requisito del usuario: unos pocos tipos de marco mas.
    DOTTED, GRADIENT, NEON, SHADOW,
    // Tanda 3.
    STAMP, CHECKERED, RAINBOW, ORNATE, INNER_GLOW, DOUBLE_ROUNDED, BRACKETS,
}

/**
 * Marco o borde decorativo. El grosor va en porcentaje del lado menor para que el marco
 * se vea proporcionado en cualquier formato.
 */
@Serializable
data class FrameSpec(
    val kind: FrameKind = FrameKind.NONE,
    val widthPercent: Float = 4f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val secondaryColor: Int = 0xFF222222.toInt(),
    val cornerRadiusPercent: Float = 6f,
)
