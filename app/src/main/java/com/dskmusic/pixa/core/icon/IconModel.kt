package com.dskmusic.pixa.core.icon

import com.dskmusic.pixa.core.model.FontRef

/*
 * Modelo del creador de iconos. Todo son data classes inmutables: deshacer/rehacer es
 * guardar copias del IconDoc. Las medidas estan en "unidades" de un lienzo de 100x100.
 */

const val WHITE = 0xFFFFFFFF.toInt()
const val BLACK = 0xFF000000.toInt()

/** Alineacion 3x3: 0 arriba-izq ... 4 centro ... 8 abajo-der. */
const val ALIGN_TOP_LEFT = 0
const val ALIGN_CENTER = 4
const val ALIGN_BOTTOM_RIGHT = 8

data class Grad(
    val on: Boolean = false,
    val c1: Int = 0xFFAF22F8.toInt(),
    val c2: Int = 0xFF2052E8.toInt(),
    val o1: Float = 100f,
    val o2: Float = 100f,
    val a1: Int = ALIGN_TOP_LEFT,
    val a2: Int = ALIGN_BOTTOM_RIGHT,
    val stop: Float = 1f,
)

/** Posicion, giro, volteo y opacidad comunes a icono, imagen y texto. */
data class Xf(
    val rot: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val align: Int = ALIGN_CENTER,
    val px: Float = 0f,
    val py: Float = 0f,
    val opacity: Float = 100f,
)

/** Fondo propio de un icono o un texto (la "caja" detras). */
data class Plate(
    val on: Boolean = false,
    val color: Int = 0xFF1976D2.toInt(),
    /** 0 = ajustar al contenido. */
    val w: Float = 0f,
    val h: Float = 0f,
    /** izquierda, arriba, derecha, abajo. */
    val pad: List<Float> = listOf(2f, 2f, 2f, 2f),
    val opacity: Float = 100f,
    val elevation: Float = 0f,
    val radius: Float = 0f,
    val borderColor: Int = WHITE,
    val borderWidth: Float = 0f,
)

data class Shadow(
    val on: Boolean = false,
    val color: Int = BLACK,
    val dx: Float = 2f,
    val dy: Float = 2f,
    val opacity: Float = 30f,
    val blur: Float = 0f,
)

data class LongShadow(
    val on: Boolean = false,
    val color: Int = BLACK,
    val length: Float = 75f,
    val angle: Float = 45f,
    val opacity: Float = 10f,
    val blur: Float = 10f,
)

enum class Fit { COVER, CONTAIN, FILL, FIT_WIDTH, FIT_HEIGHT }

/** Modos de mezcla del filtro de imagen (los que admite PorterDuffColorFilter). */
enum class Blend { MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, ADD, SRC_IN, SRC_ATOP }

data class IconItem(
    val on: Boolean = true,
    val icon: String = "Star",
    val color: Int = WHITE,
    val size: Float = 48f,
    val xf: Xf = Xf(),
    val grad: Grad = Grad(),
    val shadow: Shadow = Shadow(),
    val longShadow: LongShadow = LongShadow(),
    val plate: Plate = Plate(),
)

data class ImageItem(
    val on: Boolean = true,
    val uri: String? = null,
    val w: Float = 50f,
    val h: Float = 50f,
    val fit: Fit = Fit.COVER,
    val xf: Xf = Xf(),
    val filterOn: Boolean = false,
    val filterColor: Int = WHITE,
    val blend: Blend = Blend.MULTIPLY,
    val borderOn: Boolean = false,
    val radius: Float = 10f,
    val borderColor: Int = WHITE,
    val borderWidth: Float = 0f,
)

enum class Deco { NONE, UNDERLINE, STRIKE, OVERLINE }

data class TextItem(
    val on: Boolean = true,
    val text: String = "Text",
    val color: Int = WHITE,
    val size: Float = 10f,
    val font: FontRef = FontRef("Roboto"),
    val weight: Int = 400,
    val italic: Boolean = false,
    val letter: Float = 1f,
    val word: Float = 1f,
    val xf: Xf = Xf(),
    /** 0 izquierda, 1 centro, 2 derecha. */
    val lineAlign: Int = 1,
    val grad: Grad = Grad(),
    val deco: Deco = Deco.NONE,
    val decoThick: Float = 1f,
    val plate: Plate = Plate(),
)

data class BackStyle(
    val colorOn: Boolean = true,
    val color: Int = 0xFF333333.toInt(),
    val opacity: Float = 100f,
    val grad: Grad = Grad(),
    val imageOn: Boolean = false,
    val imageUri: String? = null,
    val imageRot: Float = 0f,
    val imageOpacity: Float = 100f,
    val imageFit: Fit = Fit.COVER,
    val borderOn: Boolean = true,
    val radius: Float = 16f,
    val borderColor: Int = WHITE,
    val borderWidth: Float = 3f,
)

data class IconDoc(
    val back: BackStyle = BackStyle(),
    val icons: List<IconItem> = listOf(IconItem()),
    val images: List<ImageItem> = emptyList(),
    val texts: List<TextItem> = emptyList(),
)
