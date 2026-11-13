package com.dskmusic.pixa.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Paleta de acentos predefinidos (requisito 1). El usuario puede elegir uno de estos
 * o cualquier color propio con el selector HSV.
 */
object AccentPresets {
    val colors: List<Color> = listOf(
        Color(0xFFFFFFFF), // blanco (por defecto)
        Color(0xFF3E7BFA), // azul
        Color(0xFF00A3A3), // turquesa
        Color(0xFF2E9E5B), // verde
        Color(0xFFF5A623), // ambar
        Color(0xFF8E5BF0), // violeta
    )

    val default: Color = colors.first()
}

// --------------------------------------------------------------------------------------
// Conversion HSV <-> Color. Se usa tanto para el tema como para el selector de color.
// --------------------------------------------------------------------------------------

/** Devuelve [hue 0..360, sat 0..1, value 0..1]. */
fun Color.toHsv(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    val h = when {
        d < 1e-6f -> 0f
        mx == r -> (60f * (((g - b) / d) % 6f) + 360f) % 360f
        mx == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    val s = if (mx <= 0f) 0f else d / mx
    return floatArrayOf(h, s, mx)
}

fun hsvToColor(hue: Float, sat: Float, value: Float, alpha: Float = 1f): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = sat.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when ((h / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, alpha.coerceIn(0f, 1f))
}

/** Mezcla lineal entre dos colores. */
fun blend(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}

/** Devuelve blanco o negro segun lo que contraste mejor sobre [bg]. */
fun onColorFor(bg: Color): Color {
    // Luminancia relativa aproximada (coeficientes Rec. 709).
    val l = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (l > 0.55f) Color(0xFF101010) else Color.White
}

private fun Color.withValue(v: Float): Color {
    val hsv = toHsv()
    return hsvToColor(hsv[0], hsv[1], v, alpha)
}

private fun Color.withSat(s: Float): Color {
    val hsv = toHsv()
    return hsvToColor(hsv[0], s, hsv[2], alpha)
}

/** Luminancia relativa aproximada (coeficientes Rec. 709). */
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Mete el color en una franja de luminancia utilizable por el tema.
 *
 * Sin esto un acento blanco deja `primary` casi blanco: en el tema claro los iconos y los
 * textos que usan `primary` desaparecen sobre la superficie blanca. Se corrige el valor
 * HSV, que para un tono fijo mueve la luminancia de forma casi lineal, asi que el tono y
 * la saturacion que eligio el usuario se conservan.
 *
 * ponytail: si la franja no se alcanza solo bajando o subiendo el valor (un azul puro
 * saturado nunca llega a luminancia alta), se deja el color como esta: ya contrastaba.
 */
private fun Color.clampLuminance(min: Float, max: Float): Color {
    val l = luminance()
    if (l >= min && l <= max) return this
    val target = if (l > max) max else min
    val hsv = toHsv()
    val value = if (l <= 1e-4f) target else hsv[2] * (target / l)
    return hsvToColor(hsv[0], hsv[1], value.coerceIn(0.04f, 1f), alpha)
}

private fun Color.rotateHue(deg: Float): Color {
    val hsv = toHsv()
    return hsvToColor(hsv[0] + deg, hsv[1], hsv[2], alpha)
}

/**
 * Construye un ColorScheme de Material 3 a partir de un unico color de acento.
 *
 * Compose Material 3 no expone un generador publico de paletas tonales, asi que en lugar
 * de arrastrar material-color-utilities se derivan los roles con ajustes de
 * saturacion/valor sobre el acento. Es suficiente para una UI de una sola marca y
 * mantiene el acento exacto que el usuario ha elegido en `primary`.
 */
/**
 * Franjas de luminancia de `primary`. En el tema claro el acento no puede acercarse al
 * blanco de la superficie ni al negro del texto; en el oscuro no puede quedarse en una
 * sombra que no se vea sobre el fondo.
 */
private const val LIGHT_PRIMARY_MIN_LUM = 0.10f
private const val LIGHT_PRIMARY_MAX_LUM = 0.42f
private const val DARK_PRIMARY_MIN_LUM = 0.45f

fun buildPixaScheme(accent: Color, dark: Boolean, oled: Boolean): ColorScheme {
    val secondary = accent.rotateHue(-28f).withSat(accent.toHsv()[1] * 0.75f)
    val tertiary = accent.rotateHue(42f).withSat(accent.toHsv()[1] * 0.8f)

    return if (dark) {
        val bg = if (oled) Color(0xFF000000) else Color(0xFF111316)
        val surface = if (oled) Color(0xFF000000) else Color(0xFF15181C)
        val surfaceVariant = if (oled) Color(0xFF121212) else Color(0xFF22262B)
        val primary = accent
            .withValue(min(1f, accent.toHsv()[2] * 1.15f + 0.1f))
            .clampLuminance(DARK_PRIMARY_MIN_LUM, 1f)
        darkColorScheme(
            primary = primary,
            onPrimary = onColorFor(primary),
            primaryContainer = blend(primary, bg, 0.62f),
            onPrimaryContainer = blend(primary, Color.White, 0.5f),
            inversePrimary = blend(primary, Color.Black, 0.4f),
            secondary = secondary.withValue(0.78f),
            onSecondary = onColorFor(secondary.withValue(0.78f)),
            secondaryContainer = blend(secondary, bg, 0.7f),
            onSecondaryContainer = blend(secondary, Color.White, 0.55f),
            tertiary = tertiary.withValue(0.8f),
            onTertiary = onColorFor(tertiary.withValue(0.8f)),
            tertiaryContainer = blend(tertiary, bg, 0.7f),
            onTertiaryContainer = blend(tertiary, Color.White, 0.55f),
            background = bg,
            onBackground = Color(0xFFE6E8EB),
            surface = surface,
            onSurface = Color(0xFFE6E8EB),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(0xFFB7BDC6),
            surfaceTint = primary,
            surfaceContainerLowest = if (oled) Color(0xFF000000) else Color(0xFF0D0F12),
            surfaceContainerLow = if (oled) Color(0xFF070707) else Color(0xFF13161A),
            surfaceContainer = if (oled) Color(0xFF0D0D0D) else Color(0xFF191D21),
            surfaceContainerHigh = if (oled) Color(0xFF151515) else Color(0xFF21262B),
            surfaceContainerHighest = if (oled) Color(0xFF1C1C1C) else Color(0xFF2A2F35),
            outline = Color(0xFF8A9199),
            outlineVariant = if (oled) Color(0xFF2A2A2A) else Color(0xFF3A4046),
            error = Color(0xFFFF6E63),
            onError = Color(0xFF3A0905),
            errorContainer = Color(0xFF5E1810),
            onErrorContainer = Color(0xFFFFD9D4),
            scrim = Color.Black,
        )
    } else {
        val primary = accent
            .withValue(min(1f, max(0.45f, accent.toHsv()[2] * 0.92f)))
            .clampLuminance(LIGHT_PRIMARY_MIN_LUM, LIGHT_PRIMARY_MAX_LUM)
        lightColorScheme(
            primary = primary,
            onPrimary = onColorFor(primary),
            primaryContainer = blend(primary, Color.White, 0.86f),
            onPrimaryContainer = blend(primary, Color.Black, 0.55f),
            inversePrimary = blend(primary, Color.White, 0.45f),
            secondary = secondary.withValue(0.6f),
            onSecondary = Color.White,
            secondaryContainer = blend(secondary, Color.White, 0.85f),
            onSecondaryContainer = blend(secondary, Color.Black, 0.55f),
            tertiary = tertiary.withValue(0.6f),
            onTertiary = Color.White,
            tertiaryContainer = blend(tertiary, Color.White, 0.85f),
            onTertiaryContainer = blend(tertiary, Color.Black, 0.55f),
            background = Color(0xFFFBFBFD),
            onBackground = Color(0xFF1A1C1E),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1C1E),
            surfaceVariant = Color(0xFFEDEFF3),
            onSurfaceVariant = Color(0xFF474B52),
            surfaceTint = primary,
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F8FA),
            surfaceContainer = Color(0xFFF1F3F6),
            surfaceContainerHigh = Color(0xFFEBEDF1),
            surfaceContainerHighest = Color(0xFFE4E7EC),
            outline = Color(0xFF757982),
            outlineVariant = Color(0xFFC8CCD3),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            scrim = Color.Black,
        )
    }
}
