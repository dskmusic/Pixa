package com.dskmusic.pixa.core.model

import android.graphics.BlendMode
import android.graphics.Matrix
import kotlinx.serialization.Serializable

// ======================================================================================
// Modos de fusion
// ======================================================================================

/**
 * Modos de fusion de capa (requisito 7). Se mapean 1:1 a android.graphics.BlendMode,
 * disponible desde API 29, que es nuestro minSdk: no hace falta fallback a PorterDuff.
 */
enum class PixaBlend(val androidMode: BlendMode) {
    NORMAL(BlendMode.SRC_OVER),
    MULTIPLY(BlendMode.MULTIPLY),
    SCREEN(BlendMode.SCREEN),
    OVERLAY(BlendMode.OVERLAY),
    SOFT_LIGHT(BlendMode.SOFT_LIGHT),
    HARD_LIGHT(BlendMode.HARD_LIGHT),
    LIGHTEN(BlendMode.LIGHTEN),
    DARKEN(BlendMode.DARKEN),
    DIFFERENCE(BlendMode.DIFFERENCE),
    EXCLUSION(BlendMode.EXCLUSION),
    COLOR_BURN(BlendMode.COLOR_BURN),
    COLOR_DODGE(BlendMode.COLOR_DODGE),
    HUE(BlendMode.HUE),
    SATURATION(BlendMode.SATURATION),
    COLOR(BlendMode.COLOR),
    LUMINOSITY(BlendMode.LUMINOSITY),
}

// ======================================================================================
// Transformacion de capa
// ======================================================================================

/**
 * Transformacion afin de una capa, mas una distorsion opcional de 4 esquinas.
 *
 * El pivote siempre es el centro del contenido, de forma que escalar y rotar con dos
 * dedos se comporta como el usuario espera. [perspective], si existe, son 8 valores
 * (x,y de las 4 esquinas destino, en coordenadas de documento) y se aplica con
 * Matrix.setPolyToPoly, sustituyendo a la parte afin.
 */
@Serializable
data class Xf(
    val tx: Float = 0f,
    val ty: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val skewX: Float = 0f,
    val skewY: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val perspective: List<Float>? = null,
) {
    /** Construye la matriz para un contenido de tamano [w] x [h]. */
    fun toMatrix(w: Int, h: Int): Matrix {
        val m = Matrix()
        val p = perspective
        if (p != null && p.size == 8) {
            val src = floatArrayOf(
                0f, 0f,
                w.toFloat(), 0f,
                w.toFloat(), h.toFloat(),
                0f, h.toFloat(),
            )
            m.setPolyToPoly(src, 0, p.toFloatArray(), 0, 4)
            return m
        }
        val cx = w / 2f
        val cy = h / 2f
        // Orden: centrar -> voltear -> sesgar -> escalar -> rotar -> trasladar.
        m.postTranslate(-cx, -cy)
        if (flipH || flipV) {
            m.postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
        }
        if (skewX != 0f || skewY != 0f) {
            m.postSkew(skewX, skewY)
        }
        m.postScale(scaleX, scaleY)
        m.postRotate(rotation)
        m.postTranslate(cx + tx, cy + ty)
        return m
    }

    fun isIdentity(): Boolean = perspective == null && tx == 0f && ty == 0f &&
        scaleX == 1f && scaleY == 1f && rotation == 0f && skewX == 0f && skewY == 0f &&
        !flipH && !flipV
}

// ======================================================================================
// Rellenos y degradados
// ======================================================================================

@Serializable
data class GradientStop(val position: Float, val color: Int)

enum class GradientKind { LINEAR, RADIAL, SWEEP, REFLECTED, REPEATING, RADIAL_REPEAT }

@Serializable
sealed interface FillSpec {
    @Serializable
    data class Solid(val color: Int) : FillSpec

    @Serializable
    data class Gradient(
        val kind: GradientKind = GradientKind.LINEAR,
        val stops: List<GradientStop> = listOf(
            GradientStop(0f, 0xFF000000.toInt()),
            GradientStop(1f, 0xFFFFFFFF.toInt()),
        ),
        /** Angulo en grados para lineal, 0 = izquierda a derecha. */
        val angle: Float = 0f,
    ) : FillSpec

    /** Relleno con una imagen (texto o forma rellenos con foto, requisitos 10 y 11). */
    @Serializable
    data class Image(
        val bitmapId: String,
        /** Transformacion de la foto dentro del hueco, manipulable con dos dedos. */
        val inner: Xf = Xf(),
    ) : FillSpec
}

// ======================================================================================
// Texto
// ======================================================================================

enum class TextAlign { LEFT, CENTER, RIGHT }

/** Referencia a una fuente: Google Fonts descargable, importada por el usuario, o sistema. */
@Serializable
data class FontRef(
    val family: String = "Roboto",
    val source: Source = Source.GOOGLE,
    /** Ruta del fichero para fuentes importadas. */
    val filePath: String? = null,
) {
    enum class Source { SYSTEM, GOOGLE, IMPORTED }
}

@Serializable
data class TextSpec(
    val text: String = "",
    val font: FontRef = FontRef(),
    val sizePx: Float = 72f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val align: TextAlign = TextAlign.CENTER,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val allCaps: Boolean = false,
    val lineHeightMul: Float = 1.15f,
    val letterSpacingEm: Float = 0f,
    /** Ancho de la caja en px de documento; el texto se ajusta dentro. */
    val boxWidth: Float = 600f,
    val outlineWidth: Float = 0f,
    val outlineColor: Int = 0xFF000000.toInt(),
    val shadowRadius: Float = 0f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0f,
    val shadowColor: Int = 0x80000000.toInt(),
    val boxColor: Int = 0,
    val boxPadding: Float = 0f,
    val boxCornerRadius: Float = 0f,
    /** Relleno alternativo al color plano: degradado o imagen dentro de las letras. */
    val fill: FillSpec? = null,
    /** Curvatura en arco: 0 = recto, positivo = hacia arriba (requisito 10). */
    val arc: Float = 0f,
    /** Puntos del trayecto dibujado a mano para "texto en trayecto" (requisito 10). */
    val path: List<Float>? = null,
    /** Contornos adicionales, de dentro a fuera, apilados sobre [outlineWidth]. */
    val extraOutlines: List<TextOutline> = emptyList(),
    /** Forma predefinida del trayecto (circulo, onda). NONE usa el arco o el trayecto dibujado. */
    val shape: TextShape = TextShape.NONE,
    /** 0..1: cuanto del circulo se recorre, o amplitud de la onda. */
    val shapeAmount: Float = 0.6f,
)

/** Un contorno adicional del texto: grosor hacia fuera (px) y color. */
@Serializable
data class TextOutline(
    val width: Float = 6f,
    val color: Int = 0xFFFFFFFF.toInt(),
)

/** Formas de trayecto predefinidas para el texto. */
enum class TextShape { NONE, CIRCLE, WAVE }

// ======================================================================================
// Formas vectoriales
// ======================================================================================

enum class ShapeKind { RECT, ELLIPSE, LINE, ARROW, POLYGON, STAR, HEART }

/** Que extremo de la flecha lleva punta (requisito del usuario). */
enum class ArrowHeads { END, START, BOTH }

/** Estilo del trazo del borde, para todas las formas (requisito del usuario). */
enum class StrokeStyle { SOLID, DASHED, DOTTED }

@Serializable
data class ShapeSpec(
    val kind: ShapeKind = ShapeKind.RECT,
    val width: Float = 400f,
    val height: Float = 300f,
    val cornerRadius: Float = 0f,
    /** Numero de lados (poligono) o puntas (estrella). */
    val sides: Int = 5,
    /** Relacion radio interior/exterior de la estrella. */
    val innerRatio: Float = 0.5f,
    val fill: FillSpec? = FillSpec.Solid(0xFF3E7BFA.toInt()),
    val strokeColor: Int = 0xFFFFFFFF.toInt(),
    val strokeWidth: Float = 0f,
    val strokeStyle: StrokeStyle = StrokeStyle.SOLID,
    val arrowHeads: ArrowHeads = ArrowHeads.END,
)

// ======================================================================================
// Codigo QR
// ======================================================================================

enum class QrPayloadKind { URL, TEXT, WIFI, VCARD, EMAIL, PHONE }

enum class QrErrorLevel { L, M, Q, H }

@Serializable
data class QrSpec(
    val kind: QrPayloadKind = QrPayloadKind.URL,
    /** Campos del contenido. Las claves dependen de [kind]. */
    val fields: Map<String, String> = mapOf("url" to "https://www.dskmusic.com"),
    val sizePx: Int = 600,
    val fgColor: Int = 0xFF000000.toInt(),
    val bgColor: Int = 0xFFFFFFFF.toInt(),
    val errorLevel: QrErrorLevel = QrErrorLevel.M,
    val marginModules: Int = 2,
    val roundedModules: Boolean = false,
    val logoBitmapId: String? = null,
)

// ======================================================================================
// Estilos de capa
// ======================================================================================

@Serializable
data class LayerStyle(
    val shadowEnabled: Boolean = false,
    val shadowColor: Int = 0x99000000.toInt(),
    val shadowRadius: Float = 12f,
    val shadowDx: Float = 6f,
    val shadowDy: Float = 6f,

    val strokeEnabled: Boolean = false,
    val strokeColor: Int = 0xFFFFFFFF.toInt(),
    val strokeWidth: Float = 8f,

    val glowEnabled: Boolean = false,
    val glowColor: Int = 0xFFFFFFFF.toInt(),
    val glowRadius: Float = 18f,
) {
    val isEmpty: Boolean get() = !shadowEnabled && !strokeEnabled && !glowEnabled
}
