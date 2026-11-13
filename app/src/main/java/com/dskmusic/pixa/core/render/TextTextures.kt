package com.dskmusic.pixa.core.render

import android.graphics.Bitmap
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Texturas generadas por codigo para rellenar las letras, sin archivos de imagen. */
enum class TextTexture {
    GOLD, SILVER, WOOD, MARBLE, FIRE, ICE,
    COPPER, BRONZE, ROSE_GOLD, CHROME, EMERALD, LAVA, CARBON, GALAXY, CONCRETE, CAMO,
    WATER, GRASS, HOLO, LEATHER,
}

object TextTextures {

    private class Stop(val pos: Float, val rgb: Int)

    private val GOLD = listOf(
        Stop(0f, 0x8A5A12), Stop(0.22f, 0xF7D979), Stop(0.38f, 0xB8861B),
        Stop(0.5f, 0xFFF1B0), Stop(0.68f, 0x9C6A15), Stop(1f, 0xD9A93A),
    )
    private val SILVER = listOf(
        Stop(0f, 0x6E7681), Stop(0.22f, 0xE8EDF2), Stop(0.38f, 0x9AA3AD),
        Stop(0.5f, 0xFFFFFF), Stop(0.68f, 0x7A838D), Stop(1f, 0xC8D0D8),
    )
    // De abajo (0) a arriba (1): brasa clara, naranja, rojo y humo oscuro.
    private val FIRE = listOf(
        Stop(0f, 0xFFF3A0), Stop(0.3f, 0xFF9A1F), Stop(0.62f, 0xE02D0B), Stop(1f, 0x3A0A05),
    )
    private val ICE = listOf(
        Stop(0f, 0xE8FBFF), Stop(0.35f, 0xA9E5F5), Stop(0.7f, 0x6CC3E6), Stop(1f, 0xDDF6FF),
    )

    private val COPPER = listOf(
        Stop(0f, 0x7A3A1C), Stop(0.22f, 0xF2A883), Stop(0.4f, 0xB35A32),
        Stop(0.52f, 0xFFD2B5), Stop(0.7f, 0x8D4526), Stop(1f, 0xD98A5F),
    )
    private val BRONZE = listOf(
        Stop(0f, 0x5B3A14), Stop(0.25f, 0xCD9B4F), Stop(0.45f, 0x8C6122),
        Stop(0.55f, 0xE3B96B), Stop(0.75f, 0x6B4617), Stop(1f, 0xB88337),
    )
    private val ROSE = listOf(
        Stop(0f, 0x9A5560), Stop(0.22f, 0xFAD3D3), Stop(0.4f, 0xC98388),
        Stop(0.52f, 0xFFEDEA), Stop(0.7f, 0xB06A72), Stop(1f, 0xE9AFAF),
    )
    private val CHROME = listOf(
        Stop(0f, 0x1B1F26), Stop(0.28f, 0xF4F8FF), Stop(0.46f, 0x5E6B7A),
        Stop(0.52f, 0xFFFFFF), Stop(0.72f, 0x2B323C), Stop(1f, 0xBFCBD8),
    )
    private val EMERALD = listOf(
        Stop(0f, 0x0B3D2A), Stop(0.35f, 0x1FAE6E), Stop(0.65f, 0x7DF2B0), Stop(1f, 0x0F6B45),
    )
    private val HOLO = listOf(
        Stop(0f, 0xFF9AF0), Stop(0.25f, 0x9AD0FF), Stop(0.5f, 0x9AFFD0),
        Stop(0.75f, 0xFFF39A), Stop(1f, 0xFF9AF0),
    )

    fun make(kind: TextTexture, size: Int = 512): Bitmap {
        val px = IntArray(size * size)
        for (y in 0 until size) {
            val v = y / (size - 1f)
            for (x in 0 until size) {
                val u = x / (size - 1f)
                px[y * size + x] = 0xFF000000.toInt() or (shade(kind, u, v) and 0xFFFFFF)
            }
        }
        val bmp = BitmapUtils.create(size, size)
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }

    private fun shade(kind: TextTexture, u: Float, v: Float): Int = when (kind) {
        TextTexture.GOLD -> gradient(GOLD, v + (fbm(u * 6f, v * 6f) - 0.5f) * 0.06f, 1f + (fbm(u * 40f, v * 40f) - 0.5f) * 0.14f)
        TextTexture.SILVER -> gradient(SILVER, v + (fbm(u * 5f, v * 5f) - 0.5f) * 0.05f, 1f + (fbm(u * 80f, v * 3f) - 0.5f) * 0.12f)
        TextTexture.FIRE -> {
            // El eje esta invertido (la llama sube) y el ruido deforma el degradado.
            val warp = (fbm(u * 5f, v * 4f) - 0.5f) * 0.45f
            gradient(FIRE, 1f - v + warp, 1f)
        }
        TextTexture.ICE -> {
            val base = gradient(ICE, v + (fbm(u * 4f, v * 4f) - 0.5f) * 0.2f, 1f)
            // Grietas: lineas finas donde el ruido cruza 0,5.
            val crack = (1f - abs(fbm(u * 7f + 3f, v * 7f) - 0.5f) * 14f).coerceIn(0f, 1f).pow(3f)
            mixRgb(base, 0xFFFFFF, crack * 0.8f)
        }
        TextTexture.WOOD -> {
            val n = fbm(u * 3f, v * 0.7f)
            // Anillos: el seno de una coordenada deformada por ruido da vetas irregulares.
            val ring = 0.5f + 0.5f * sin(((v * 16f) + n * 5f) * PI.toFloat())
            val fine = (fbm(u * 60f, v * 4f) - 0.5f) * 0.2f
            mixRgb(0x5A3A1E, 0xB07A45, (ring + fine).coerceIn(0f, 1f))
        }
        TextTexture.COPPER -> gradient(COPPER, v + (fbm(u * 6f, v * 6f) - 0.5f) * 0.06f, 1f + (fbm(u * 40f, v * 40f) - 0.5f) * 0.14f)
        TextTexture.BRONZE -> gradient(BRONZE, v + (fbm(u * 6f, v * 6f) - 0.5f) * 0.06f, 1f + (fbm(u * 40f, v * 40f) - 0.5f) * 0.14f)
        TextTexture.ROSE_GOLD -> gradient(ROSE, v + (fbm(u * 6f, v * 6f) - 0.5f) * 0.06f, 1f + (fbm(u * 40f, v * 40f) - 0.5f) * 0.12f)
        TextTexture.CHROME -> gradient(CHROME, v + (fbm(u * 3f, v * 3f) - 0.5f) * 0.12f, 1f)
        TextTexture.EMERALD -> gradient(EMERALD, v * 0.8f + fbm(u * 5f, v * 5f) * 0.5f, 1f + (fbm(u * 60f, v * 60f) - 0.5f) * 0.1f)
        TextTexture.LAVA -> {
            val base = mixRgb(0x2B0A05, 0x5A1A0A, fbm(u * 6f, v * 6f))
            val crack = (1f - abs(fbm(u * 5f + 9f, v * 5f) - 0.5f) * 10f).coerceIn(0f, 1f).pow(3f)
            mixRgb(base, 0xFF8A1C, crack * 0.95f)
        }
        TextTexture.CARBON -> {
            val cells = 32f
            val cu = floor(u * cells)
            val cv = floor(v * cells)
            val fu = u * cells - cu
            val fv = v * cells - cv
            val weave = if (((cu + cv).toInt() and 1) == 0) fu else fv
            mixRgb(0x15181C, 0x3A4048, weave * 0.8f)
        }
        TextTexture.GALAXY -> {
            val base = mixRgb(0x0B0B2B, 0x4B1D8F, fbm(u * 3f, v * 3f))
            val nebula = mixRgb(base, 0xFF4DA6, ((fbm(u * 5f + 2f, v * 5f) - 0.45f) * 1.4f).coerceIn(0f, 1f))
            if (hash((u * 220f).toInt(), (v * 220f).toInt()) > 0.993f) 0xFFFFFF else nebula
        }
        TextTexture.CONCRETE -> mixRgb(
            0x6E6E6E, 0xB5B5B5,
            (fbm(u * 12f, v * 12f) * 0.8f + (fbm(u * 80f, v * 80f) - 0.5f) * 0.3f).coerceIn(0f, 1f),
        )
        TextTexture.CAMO -> {
            val n = fbm(u * 4f, v * 4f)
            when {
                n < 0.4f -> 0x4B5320
                n < 0.55f -> 0x6B7A3A
                n < 0.68f -> 0x2E3318
                else -> 0x8B7E55
            }
        }
        TextTexture.WATER -> mixRgb(
            0x0B5C8A, 0x6FD3F5,
            0.5f + 0.5f * sin((v * 14f + fbm(u * 4f, v * 4f) * 4f) * PI.toFloat()),
        )
        TextTexture.GRASS -> mixRgb(0x1E5A18, 0x8FD13B, fbm(u * 70f, v * 6f).coerceIn(0f, 1f))
        TextTexture.HOLO -> gradient(HOLO, (u + v) / 2f + (fbm(u * 4f, v * 4f) - 0.5f) * 0.4f, 1f)
        TextTexture.LEATHER -> {
            val base = mixRgb(0x4A2A16, 0x7A4B2A, fbm(u * 10f, v * 10f))
            mixRgb(base, 0x2A170B, ((fbm(u * 60f, v * 60f) - 0.45f) * 2f).coerceIn(0f, 1f) * 0.6f)
        }
        TextTexture.MARBLE -> {
            val n = fbm(u * 3f, v * 3f)
            val vein = (1f - abs(sin((u + v) * 6f + n * 10f))).pow(8f)
            mixRgb(0xF2F2F2, 0x4A4F57, (vein * 0.85f + (fbm(u * 30f, v * 30f) - 0.5f) * 0.08f).coerceIn(0f, 1f))
        }
    }

    /** Degradado por paradas; [pos] fuera de 0..1 se recorta. [light] multiplica el brillo. */
    private fun gradient(stops: List<Stop>, pos: Float, light: Float): Int {
        val t = pos.coerceIn(0f, 1f)
        var i = 0
        while (i < stops.size - 2 && t > stops[i + 1].pos) i++
        val a = stops[i]
        val b = stops[i + 1]
        val f = ((t - a.pos) / (b.pos - a.pos)).coerceIn(0f, 1f)
        val c = mixRgb(a.rgb, b.rgb, f)
        return scale(c, light)
    }

    private fun mixRgb(a: Int, b: Int, t: Float): Int {
        val r = ((a shr 16 and 0xFF) + ((b shr 16 and 0xFF) - (a shr 16 and 0xFF)) * t).roundToInt()
        val g = ((a shr 8 and 0xFF) + ((b shr 8 and 0xFF) - (a shr 8 and 0xFF)) * t).roundToInt()
        val bl = ((a and 0xFF) + ((b and 0xFF) - (a and 0xFF)) * t).roundToInt()
        return (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or bl.coerceIn(0, 255)
    }

    private fun scale(c: Int, k: Float): Int {
        val r = ((c shr 16 and 0xFF) * k).roundToInt().coerceIn(0, 255)
        val g = ((c shr 8 and 0xFF) * k).roundToInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * k).roundToInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    // ----------------------------------------------------------------------------------
    // Ruido de valor con interpolacion suave (fbm de 4 octavas), determinista
    // ----------------------------------------------------------------------------------

    private fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    private fun noise(x: Float, y: Float): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val sx = xf * xf * (3f - 2f * xf)
        val sy = yf * yf * (3f - 2f * yf)
        val a = hash(xi, yi)
        val b = hash(xi + 1, yi)
        val c = hash(xi, yi + 1)
        val d = hash(xi + 1, yi + 1)
        val top = a + (b - a) * sx
        val bot = c + (d - c) * sx
        return top + (bot - top) * sy
    }

    private fun fbm(x: Float, y: Float): Float {
        var sum = 0f
        var amp = 0.5f
        var fx = x
        var fy = y
        repeat(4) {
            sum += amp * noise(fx, fy)
            fx *= 2f
            fy *= 2f
            amp *= 0.5f
        }
        return sum / 0.9375f
    }
}
