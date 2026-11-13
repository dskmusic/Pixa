package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.EffectKind
import com.dskmusic.pixa.core.model.EffectSpec
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Efectos creativos ampliados: lente y luz, arte, clima, color y cielo.
 *
 * Sigue las reglas de [Effects]: cada funcion devuelve un bitmap nuevo y nunca toca el
 * original. Todos los tamanos (radios, grano, lineas) salen del lado mayor de la imagen y
 * no de pixeles fijos, porque la vista previa se calcula sobre una copia reducida y
 * "Aplicar" repite la operacion a resolucion completa: tienen que verse igual.
 *
 * En [EffectSpec], `amount` es la intensidad (0..1), `angle` orienta los efectos con
 * direccion, `seed` fija el azar para que regenerar de otro resultado.
 */
object MoreEffects {

    fun apply(src: Bitmap, spec: EffectSpec): Bitmap {
        val k = spec.amount.coerceIn(0f, 1f)
        val seed = spec.seed
        return when (spec.kind) {
            EffectKind.FILM_GRAIN -> filmGrain(src, k, seed)
            EffectKind.VIGNETTE -> vignette(src, k)
            EffectKind.CHROMATIC -> chromatic(src, k)
            EffectKind.BLOOM -> bloom(src, k)
            EffectKind.TILT_SHIFT -> tiltShift(src, k, spec.angle)
            EffectKind.BOKEH -> bokeh(src, k)
            EffectKind.LIGHT_LEAK -> lightLeak(src, k, spec.angle)
            EffectKind.BURNED_EDGES -> burnedEdges(src, k)
            EffectKind.SKETCH -> sketch(src, k)
            EffectKind.COMIC -> comic(src, k)
            EffectKind.OIL -> oil(src, k)
            EffectKind.WATERCOLOR -> watercolor(src, k, seed)
            EffectKind.MOSAIC -> mosaic(src, k, seed)
            EffectKind.SCATTER -> scatter(src, k, seed)
            EffectKind.SPEED_LINES -> speedLines(src, k, seed)
            EffectKind.RAIN -> rain(src, k, spec.angle, seed)
            EffectKind.SNOW -> snow(src, k, seed)
            EffectKind.SPARKLES -> sparkles(src, k, seed)
            EffectKind.DOUBLE_EXPOSURE -> doubleExposure(src, k, spec.colorA, spec.colorB)
            EffectKind.ANAGLYPH -> anaglyph(src, k)
            EffectKind.HEATMAP -> heatmap(src, k)
            EffectKind.SENSOR_NOISE -> sensorNoise(src, k, seed)
            EffectKind.SKY_GOLDEN -> sky(src, k, SkyLook.GOLDEN, seed)
            EffectKind.SKY_NIGHT -> sky(src, k, SkyLook.NIGHT, seed)
            EffectKind.SKY_DRAMATIC -> sky(src, k, SkyLook.DRAMATIC, seed)
            EffectKind.SKY_BLUE -> sky(src, k, SkyLook.BLUE, seed)
            EffectKind.SPLIT_TONE -> splitTone(src, k, spec.colorA, spec.colorB)
            EffectKind.EMBOSS -> emboss(src, k)
            EffectKind.DREAMY -> dreamy(src, k)
            EffectKind.LOMO -> lomo(src, k)
            EffectKind.CROSS_PROCESS -> crossProcess(src, k)
            EffectKind.SWIRL -> swirl(src, k)
            EffectKind.OLD_PHOTO -> oldPhoto(src, k, spec.damage, spec.tone, seed)
            else -> BitmapUtils.mutableCopy(src)
        }
    }

    // ----------------------------------------------------------------------------------
    // Tanda 3: relieve, ensueno, lomo, proceso cruzado y remolino
    // ----------------------------------------------------------------------------------

    /**
     * Foto antigua: desvaido y viraje de color segun [k] y [tone]; grano y vineta; y, segun
     * [damage], polvo, rayones y manchas dibujados con la semilla. Todos los
     * tamanos salen del lado mayor para que vista previa y resultado final coincidan.
     */
    private fun oldPhoto(src: Bitmap, k: Float, damage: Float, tone: Float, seed: Long): Bitmap {
        val d = damage.coerceIn(0f, 1f)
        val t = tone.coerceIn(0f, 1f) * 2f
        val c = 1f - 0.3f * k
        val lift = 127.5f * (1f - c) + 20f * k
        val m = ColorMatrix().apply {
            setSaturation(1f - 0.85f * k)
            postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, lift + 34f * k * t,
                0f, c, 0f, 0f, lift + 14f * k * t,
                0f, 0f, c, 0f, lift - 30f * k * t,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        val out = Adjust.applyColorMatrix(src, m)
        Adjust.drawGrain(out, 0.45f * k, seed)
        Adjust.drawVignette(out, -0.5f * k)
        if (d <= 0f) return out

        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val unit = max(w, h) / 1000f
        val rnd = Random(seed xor 0x01D9407L)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Manchas de humedad: halos pardos que oscurecen.
        paint.blendMode = BlendMode.MULTIPLY
        repeat((d * 5f).roundToInt()) {
            val r = min(w, h) * (0.06f + 0.18f * rnd.nextFloat())
            val cx = rnd.nextFloat() * w
            val cy = rnd.nextFloat() * h
            val a = (70 + 90 * d).roundToInt().coerceAtMost(200)
            paint.shader = RadialGradient(
                cx, cy, r, Color.argb(a, 120, 84, 40), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, r, paint)
        }
        paint.shader = null
        paint.blendMode = null

        // Polvo: motas claras y oscuras.
        repeat((d * 320f).roundToInt()) {
            val r = unit * (0.5f + 2.2f * rnd.nextFloat() * rnd.nextFloat())
            paint.color = if (rnd.nextFloat() < 0.65f) {
                Color.argb(170, 245, 240, 225)
            } else {
                Color.argb(150, 30, 22, 14)
            }
            canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, r, paint)
        }

        // Rayones: arañazos finos e irregulares. Cada uno es un paseo aleatorio con rumbo
        // casi vertical pero que serpentea, con tramos cortados y de opacidad variable.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        repeat((d * 14f).roundToInt()) {
            var x = rnd.nextFloat() * w
            var y = rnd.nextFloat() * h
            // Rumbo base: sobre todo vertical, pero algunos van en cualquier direccion.
            var heading = if (rnd.nextFloat() < 0.7f) {
                (PI / 2).toFloat() + (rnd.nextFloat() - 0.5f) * 0.5f
            } else {
                rnd.nextFloat() * (2 * PI).toFloat()
            }
            val steps = 8 + rnd.nextInt(30)
            val step = h * (0.006f + 0.012f * rnd.nextFloat())
            val baseAlpha = 50 + 120 * rnd.nextFloat()
            val width = unit * (0.4f + 0.8f * rnd.nextFloat())
            val light = rnd.nextFloat() < 0.8f
            for (i in 0 until steps) {
                heading += (rnd.nextFloat() - 0.5f) * 0.35f
                val nx = x + cos(heading) * step
                val ny = y + sin(heading) * step
                // Tramos que se pierden: el rayon se corta y reaparece.
                if (rnd.nextFloat() > 0.18f) {
                    val a = (baseAlpha * (0.5f + 0.5f * rnd.nextFloat())).roundToInt()
                    paint.strokeWidth = width * (0.6f + 0.8f * rnd.nextFloat())
                    paint.color = if (light) Color.argb(a, 250, 246, 235) else Color.argb(a, 40, 30, 20)
                    canvas.drawLine(x, y, nx, ny, paint)
                }
                x = nx
                y = ny
            }
        }

        return out
    }

    /** Relieve: diferencia de luminancia en diagonal sobre gris medio, mezclada con el original. */
    private fun emboss(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val out = IntArray(px.size)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            val ym = max(0, y - 1)
            val yp = min(h - 1, y + 1)
            for (x in 0 until w) {
                val xm = max(0, x - 1)
                val xp = min(w - 1, x + 1)
                val e = (lum(px[yp * w + xp]) - lum(px[ym * w + xm])) * 1.2f + 128f
                out[y * w + x] = mixTo(px[y * w + x], e, e, e, k.coerceIn(0f, 1f))
            }
            }
        }
        return write(out, w, h)
    }

    /** Ensueno: la imagen desenfocada se mezcla en modo pantalla, como un resplandor suave. */
    private fun dreamy(src: Bitmap, k: Float): Bitmap {
        val radius = max(1f, max(src.width, src.height) * 0.012f * (0.5f + k))
        val blurred = Blur.blur(src, radius)
        val a = read(src)
        val b = read(blurred)
        blurred.recycle()
        val out = IntArray(a.size)
        for (i in a.indices) {
            val p = a[i]
            val q = b[i]
            fun ch(shift: Int): Int {
                val x = (p ushr shift and 0xFF) / 255f
                val y = (q ushr shift and 0xFF) / 255f
                val screen = 1f - (1f - x) * (1f - y)
                return c8((x + (screen - x) * k) * 255f * (1f + 0.05f * k))
            }
            out[i] = (p and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        return write(out, src.width, src.height)
    }

    /** Lomo: contraste alto, rojos calidos, azules apagados y esquinas oscuras. */
    private fun lomo(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val out = IntArray(px.size)
        fun s(v: Float) = (0.5f + (v - 0.5f) * (1f + 0.55f * k)).coerceIn(0f, 1f)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val p = px[i]
            val r = (p ushr 16 and 0xFF) / 255f
            val g = (p ushr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val rr = s(r) * (1f + 0.08f * k)
            val gg = s(g) * (1f + 0.02f * k)
            val bb = s(b) * (1f - 0.1f * k) + 0.03f * k
            out[i] = (p and 0xFF000000.toInt()) or (c8(rr * 255f) shl 16) or
                (c8(gg * 255f) shl 8) or c8(bb * 255f)
            }
        }
        val bmp = write(out, w, h)
        Adjust.drawVignette(bmp, -k * 0.9f)
        return bmp
    }

    /** Proceso cruzado: curvas distintas por canal (luces calidas, sombras azuladas). */
    private fun crossProcess(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val out = IntArray(px.size)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val p = px[i]
            val r = (p ushr 16 and 0xFF) / 255f
            val g = (p ushr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val rr = r.pow(1f - 0.35f * k)
            val gg = (0.5f + (g - 0.5f) * (1f + 0.25f * k)).coerceIn(0f, 1f)
            val bb = (b + ((0.12f + 0.72f * b) - b) * k).coerceIn(0f, 1f)
            out[i] = (p and 0xFF000000.toInt()) or (c8(rr * 255f) shl 16) or
                (c8(gg * 255f) shl 8) or c8(bb * 255f)
            }
        }
        return write(out, w, h)
    }

    /** Remolino: los pixeles giran alrededor del centro, mas cuanto mas cerca de el. */
    private fun swirl(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val out = IntArray(px.size)
        val cx = (w - 1) / 2f
        val cy = (h - 1) / 2f
        val radius = min(w, h) * 0.5f
        val angle = k * 4.5f
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                val dx = x - cx
                val dy = y - cy
                val d = hypot(dx, dy)
                if (d >= radius) {
                    out[y * w + x] = px[y * w + x]
                } else {
                    val t = 1f - d / radius
                    val a = angle * t * t
                    val ca = cos(a)
                    val sa = sin(a)
                    out[y * w + x] = sampleBilinear(px, w, h, cx + dx * ca - dy * sa, cy + dx * sa + dy * ca)
                }
            }
            }
        }
        return write(out, w, h)
    }

    private fun sampleBilinear(px: IntArray, w: Int, h: Int, fx: Float, fy: Float): Int {
        val xf = fx.coerceIn(0f, w - 1f)
        val yf = fy.coerceIn(0f, h - 1f)
        val x0 = xf.toInt()
        val y0 = yf.toInt()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val tx = xf - x0
        val ty = yf - y0
        val p00 = px[y0 * w + x0]
        val p10 = px[y0 * w + x1]
        val p01 = px[y1 * w + x0]
        val p11 = px[y1 * w + x1]
        var res = 0
        for (s in intArrayOf(24, 16, 8, 0)) {
            val top = (p00 ushr s and 0xFF) * (1f - tx) + (p10 ushr s and 0xFF) * tx
            val bot = (p01 ushr s and 0xFF) * (1f - tx) + (p11 ushr s and 0xFF) * tx
            res = res or (c8(top * (1f - ty) + bot * ty) shl s)
        }
        return res
    }

    // ----------------------------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------------------------

    private fun read(src: Bitmap): IntArray {
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        return px
    }

    private fun write(px: IntArray, w: Int, h: Int): Bitmap {
        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun lum(p: Int): Float =
        0.2126f * (p ushr 16 and 0xFF) + 0.7152f * (p ushr 8 and 0xFF) + 0.0722f * (p and 0xFF)

    private fun c8(v: Float): Int = v.roundToInt().coerceIn(0, 255)

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Pixel con el color [r],[g],[b] mezclado en proporcion [t] con el original [p]. */
    private fun mixTo(p: Int, r: Float, g: Float, b: Float, t: Float): Int {
        val pr = p ushr 16 and 0xFF
        val pg = p ushr 8 and 0xFF
        val pb = p and 0xFF
        return (p and 0xFF000000.toInt()) or
            (c8(pr + (r - pr) * t) shl 16) or
            (c8(pg + (g - pg) * t) shl 8) or
            c8(pb + (b - pb) * t)
    }

    /** Hash entero barato y estable: mismo (x, y, salt) da siempre el mismo numero. */
    private fun hash(x: Int, y: Int, salt: Int): Int {
        var h = x * 374761393 + y * 668265263 + salt * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    /** -1..1 */
    private fun hashSigned(x: Int, y: Int, salt: Int): Float =
        ((hash(x, y, salt) and 0xFFFF) / 32767.5f) - 1f

    /** 0..1 */
    private fun hash01(x: Int, y: Int, salt: Int): Float =
        (hash(x, y, salt) and 0xFFFFFF) / 16777216f

    /** Tamano de celda de ruido: 1 px a ~1000 px de lado, mas grande en fotos grandes. */
    private fun cellSize(w: Int, h: Int): Int = max(1, (max(w, h) / 1000f).roundToInt())

    private fun seedInt(seed: Long): Int = (seed xor (seed ushr 32)).toInt()

    /** Solo las zonas claras de [px], el resto a negro, para bloom y bokeh. */
    private fun brightPass(px: IntArray, threshold: Float): IntArray {
        val out = IntArray(px.size)
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            val l = lum(p)
            if (l <= threshold) {
                out[i] = 0xFF000000.toInt()
                continue
            }
            val t = (l - threshold) / (255f - threshold)
            out[i] = (a shl 24) or (c8((p ushr 16 and 0xFF) * t) shl 16) or
                (c8((p ushr 8 and 0xFF) * t) shl 8) or c8((p and 0xFF) * t)
        }
        return out
    }

    private fun screenInt(a: Int, b: Int): Int = 255 - (255 - a) * (255 - b) / 255

    // ----------------------------------------------------------------------------------
    // Lente y luz
    // ----------------------------------------------------------------------------------

    /** Grano de pelicula: ruido en celdas, mas visible en los medios tonos que en negros y blancos. */
    private fun filmGrain(src: Bitmap, k: Float, seed: Long): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val cell = cellSize(w, h)
        val gw = (w + cell - 1) / cell
        val gh = (h + cell - 1) / cell
        val rnd = Random(seed xor 0x5EEDL)
        // Suma de tres uniformes: campana aproximada, mas natural que ruido uniforme.
        val noise = FloatArray(gw * gh) { rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f }
        val strength = k * 75f
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            val row = (y / cell) * gw
            for (x in 0 until w) {
                val p = px[y * w + x]
                val a = p ushr 24
                if (a == 0) continue
                val l = lum(p) / 255f
                val mid = 0.3f + 0.7f * 4f * l * (1f - l)
                val d = noise[row + x / cell] * strength * mid
                px[y * w + x] = (a shl 24) or
                    (c8((p ushr 16 and 0xFF) + d) shl 16) or
                    (c8((p ushr 8 and 0xFF) + d) shl 8) or
                    c8((p and 0xFF) + d)
            }
            }
        }
        return write(px, w, h)
    }

    private fun vignette(src: Bitmap, k: Float): Bitmap {
        val out = BitmapUtils.mutableCopy(src)
        Adjust.drawVignette(out, -k)
        return out
    }

    /** Aberracion cromatica: rojo hacia fuera, azul hacia dentro, creciendo hacia las esquinas. */
    private fun chromatic(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val out = IntArray(px.size)
        val shift = k * 0.022f
        val cx = (w - 1) / 2f
        val cy = (h - 1) / 2f
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx
                val rx = (x + dx * shift).roundToInt().coerceIn(0, w - 1)
                val ry = (y + dy * shift).roundToInt().coerceIn(0, h - 1)
                val bx = (x - dx * shift).roundToInt().coerceIn(0, w - 1)
                val by = (y - dy * shift).roundToInt().coerceIn(0, h - 1)
                val p = px[y * w + x]
                out[y * w + x] = (p and 0xFF000000.toInt()) or
                    (px[ry * w + rx] and 0x00FF0000) or
                    (p and 0x0000FF00) or
                    (px[by * w + bx] and 0x000000FF)
            }
            }
        }
        return write(out, w, h)
    }

    /** Resplandor: las luces se desenfocan y se suman en modo pantalla. */
    private fun bloom(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val glow = write(brightPass(px, 150f), w, h)
        val blurred = Blur.blur(glow, max(2f, max(w, h) * (0.012f + 0.03f * k)))
        glow.recycle()
        val g = read(blurred)
        blurred.recycle()
        val gain = 0.7f + 1.3f * k
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            val q = g[i]
            px[i] = (a shl 24) or
                (screenInt(p ushr 16 and 0xFF, c8((q ushr 16 and 0xFF) * gain)) shl 16) or
                (screenInt(p ushr 8 and 0xFF, c8((q ushr 8 and 0xFF) * gain)) shl 8) or
                screenInt(p and 0xFF, c8((q and 0xFF) * gain))
            }
        }
        return write(px, w, h)
    }

    /** Mezcla [src] con una version desenfocada segun una mascara 0..1 por pixel. */
    private inline fun maskedBlur(
        src: Bitmap,
        radius: Float,
        extra: (IntArray, Int, Int) -> Unit = { _, _, _ -> },
        crossinline mask: (Int, Int) -> Float,
    ): Bitmap {
        val w = src.width
        val h = src.height
        val blurred = Blur.blur(src, radius)
        val b = read(blurred)
        blurred.recycle()
        extra(b, w, h)
        val px = read(src)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                val i = y * w + x
                val t = mask(x, y)
                if (t <= 0f) continue
                val p = px[i]
                val q = b[i]
                px[i] = mixTo(
                    p,
                    (q ushr 16 and 0xFF).toFloat(),
                    (q ushr 8 and 0xFF).toFloat(),
                    (q and 0xFF).toFloat(),
                    t,
                )
            }
            }
        }
        return write(px, w, h)
    }

    /** Efecto miniatura: una franja nitida y el resto desenfocado, con color realzado. */
    private fun tiltShift(src: Bitmap, k: Float, angle: Float): Bitmap {
        val w = src.width
        val h = src.height
        val a = Math.toRadians(angle.toDouble())
        val nx = sin(a).toFloat()
        val ny = cos(a).toFloat()
        val half = max(1f, 0.5f * (abs(nx) * w + abs(ny) * h))
        val cx = w / 2f
        val cy = h / 2f
        val blurred = maskedBlur(src, max(2f, max(w, h) * (0.004f + 0.03f * k))) { x, y ->
            val d = abs((x - cx) * nx + (y - cy) * ny) / half
            smooth(0.15f, 0.85f, d)
        }
        // El realce de saturacion y contraste es lo que hace que parezca una maqueta.
        val m = ColorMatrix().apply {
            setSaturation(1f + 0.35f * k)
            postConcat(ColorMatrix(floatArrayOf(
                1.08f, 0f, 0f, 0f, -8f,
                0f, 1.08f, 0f, 0f, -8f,
                0f, 0f, 1.08f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        val out = Adjust.applyColorMatrix(blurred, m)
        blurred.recycle()
        return out
    }

    /** Lente: foco en el centro, bordes con desenfoque y luces convertidas en discos brillantes. */
    private fun bokeh(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val cx = w / 2f
        val cy = h / 2f
        val reach = 0.5f * hypot(w.toFloat(), h.toFloat()) * 0.85f
        val radius = max(2f, max(w, h) * (0.006f + 0.03f * k))
        return maskedBlur(
            src,
            radius,
            extra = { b, bw, bh ->
                // Las luces desenfocadas se suman al fondo borroso, no a la zona nitida.
                val glow = write(brightPass(b, 205f), bw, bh)
                val soft = Blur.blur(glow, radius * 0.7f)
                glow.recycle()
                val g = read(soft)
                soft.recycle()
                for (i in b.indices) {
                    val p = b[i]
                    val a = p ushr 24
                    if (a == 0) continue
                    val q = g[i]
                    b[i] = (a shl 24) or
                        (screenInt(p ushr 16 and 0xFF, c8((q ushr 16 and 0xFF) * 1.4f)) shl 16) or
                        (screenInt(p ushr 8 and 0xFF, c8((q ushr 8 and 0xFF) * 1.4f)) shl 8) or
                        screenInt(p and 0xFF, c8((q and 0xFF) * 1.4f))
                }
            },
        ) { x, y ->
            smooth(0.3f, 0.95f, hypot(x - cx, y - cy) / reach)
        }
    }

    /** Fuga de luz calida entrando por el borde indicado por [angle]. */
    private fun lightLeak(src: Bitmap, k: Float, angle: Float): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val out = BitmapUtils.mutableCopy(src)
        val canvas = Canvas(out)
        val a = Math.toRadians(angle.toDouble())
        val reach = max(w, h) * 0.8f
        fun blob(cx: Float, cy: Float, radius: Float, c1: Int, c2: Int, alpha: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(
                        Color.argb((alpha * 255).roundToInt(), Color.red(c1), Color.green(c1), Color.blue(c1)),
                        Color.argb((alpha * 190).roundToInt(), Color.red(c2), Color.green(c2), Color.blue(c2)),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                blendMode = BlendMode.SCREEN
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        val cx = (w / 2f + cos(a) * w * 0.55f).toFloat()
        val cy = (h / 2f + sin(a) * h * 0.55f).toFloat()
        blob(cx, cy, reach, 0xFFFF9A2A.toInt(), 0xFFFF2D55.toInt(), 0.95f * k)
        // Un segundo foco mas pequeno y frio en el lado contrario da profundidad.
        blob(w - cx, h - cy, reach * 0.55f, 0xFFFF5FA8.toInt(), 0xFF7A3CFF.toInt(), 0.45f * k)
        return out
    }

    /** Bordes quemados de pelicula vieja: marron oscuro que se come el marco. */
    private fun burnedEdges(src: Bitmap, k: Float): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val out = BitmapUtils.mutableCopy(src)
        val canvas = Canvas(out)
        val band = min(w, h) * (0.07f + 0.22f * k)
        val burn = Color.argb((255 * (0.55f + 0.45f * k)).roundToInt(), 48, 18, 4)
        fun edge(x0: Float, y0: Float, x1: Float, y1: Float, l: Float, t: Float, r: Float, b: Float) {
            val paint = Paint().apply {
                shader = LinearGradient(x0, y0, x1, y1, burn, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                blendMode = BlendMode.MULTIPLY
            }
            canvas.drawRect(l, t, r, b, paint)
        }
        edge(0f, 0f, band, 0f, 0f, 0f, band, h)
        edge(w, 0f, w - band, 0f, w - band, 0f, w, h)
        edge(0f, 0f, 0f, band, 0f, 0f, w, band)
        edge(0f, h, 0f, h - band, 0f, h - band, w, h)
        Adjust.drawVignette(out, -0.45f * k)
        return out
    }

    // ----------------------------------------------------------------------------------
    // Arte
    // ----------------------------------------------------------------------------------

    /** Lapiz: dodge del gris contra su inverso desenfocado. */
    private fun sketch(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val inv = IntArray(px.size)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val v = 255 - c8(lum(px[i]))
            inv[i] = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
            }
        }
        val invBmp = write(inv, w, h)
        val blurred = Blur.blur(invBmp, max(2f, max(w, h) * (0.004f + 0.004f * k)))
        invBmp.recycle()
        val b = read(blurred)
        blurred.recycle()
        val mix = min(1f, 0.4f + k * 1.2f)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            val g = lum(p)
            val blend = (b[i] and 0xFF).toFloat()
            var v = if (blend >= 254f) 255f else min(255f, g * 255f / (255f - blend))
            // Gamma: los trazos oscuros se marcan mas, como un lapiz de grafito.
            v = 255f * (v / 255f).pow(1.35f)
            px[i] = mixTo(p, v, v, v, mix)
            }
        }
        return write(px, w, h)
    }

    /** Comic: colores planos, saturados, con contorno negro por Sobel. */
    private fun comic(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        // Radio entero y relativo al lado. El gradiente de un borde desenfocado es inversamente
        // proporcional a ese radio, asi que el umbral se escala con el para que las lineas salgan
        // igual de densas en la vista previa y a resolucion completa.
        val blurRadius = max(1, (max(w, h) * 0.0025f).roundToInt())
        val smoothBmp = Blur.blur(src, blurRadius.toFloat())
        val sp = read(smoothBmp)
        smoothBmp.recycle()
        val lumas = IntArray(sp.size) { lum(sp[it]).roundToInt() }
        val levels = 6
        val step = 255f / (levels - 1)
        val thr = (95f - 60f * k) * (2.5f / blurRadius)
        val out = IntArray(sp.size)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            val ym = max(0, y - 1)
            val yp = min(h - 1, y + 1)
            for (x in 0 until w) {
                val xm = max(0, x - 1)
                val xp = min(w - 1, x + 1)
                val gx = -lumas[ym * w + xm] - 2 * lumas[y * w + xm] - lumas[yp * w + xm] +
                    lumas[ym * w + xp] + 2 * lumas[y * w + xp] + lumas[yp * w + xp]
                val gy = -lumas[ym * w + xm] - 2 * lumas[ym * w + x] - lumas[ym * w + xp] +
                    lumas[yp * w + xm] + 2 * lumas[yp * w + x] + lumas[yp * w + xp]
                val line = smooth(thr, thr * 1.8f, sqrt((gx * gx + gy * gy).toFloat()))
                val p = sp[y * w + x]
                val a = p ushr 24
                if (a == 0) continue
                val l = lum(p)
                var r = p ushr 16 and 0xFF
                var g = p ushr 8 and 0xFF
                var b = p and 0xFF
                r = ((l + (r - l) * 1.35f) / step).roundToInt().let { (it * step).roundToInt() }.coerceIn(0, 255)
                g = ((l + (g - l) * 1.35f) / step).roundToInt().let { (it * step).roundToInt() }.coerceIn(0, 255)
                b = ((l + (b - l) * 1.35f) / step).roundToInt().let { (it * step).roundToInt() }.coerceIn(0, 255)
                val keep = 1f - line * 0.93f
                out[y * w + x] = (a shl 24) or (c8(r * keep) shl 16) or (c8(g * keep) shl 8) or c8(b * keep)
            }
            }
        }
        return write(out, w, h)
    }

    /** Imagen de sumas acumuladas para medias y varianzas de ventana en O(1). */
    private class Integral(px: IntArray, val w: Int, val h: Int) {
        private val w1 = w + 1
        val r = IntArray(w1 * (h + 1))
        val g = IntArray(w1 * (h + 1))
        val b = IntArray(w1 * (h + 1))
        val l = IntArray(w1 * (h + 1))
        val l2 = LongArray(w1 * (h + 1))

        init {
            for (y in 0 until h) {
                var sr = 0
                var sg = 0
                var sb = 0
                var sl = 0
                var sl2 = 0L
                for (x in 0 until w) {
                    val p = px[y * w + x]
                    val pr = p ushr 16 and 0xFF
                    val pg = p ushr 8 and 0xFF
                    val pb = p and 0xFF
                    val pl = (0.2126f * pr + 0.7152f * pg + 0.0722f * pb).toInt()
                    sr += pr; sg += pg; sb += pb; sl += pl; sl2 += (pl * pl).toLong()
                    val i = (y + 1) * w1 + x + 1
                    r[i] = r[i - w1] + sr
                    g[i] = g[i - w1] + sg
                    b[i] = b[i - w1] + sb
                    l[i] = l[i - w1] + sl
                    l2[i] = l2[i - w1] + sl2
                }
            }
        }

        fun sum(a: IntArray, x0: Int, y0: Int, x1: Int, y1: Int): Int =
            a[(y1 + 1) * w1 + x1 + 1] - a[y0 * w1 + x1 + 1] - a[(y1 + 1) * w1 + x0] + a[y0 * w1 + x0]

        fun sum(a: LongArray, x0: Int, y0: Int, x1: Int, y1: Int): Long =
            a[(y1 + 1) * w1 + x1 + 1] - a[y0 * w1 + x1 + 1] - a[(y1 + 1) * w1 + x0] + a[y0 * w1 + x0]
    }

    /**
     * Filtro de Kuwahara: cada pixel toma el color medio del cuadrante vecino menos
     * variable. Alisa las texturas y conserva los bordes, que es el aspecto de una pintura.
     * Se trabaja a como mucho 1,5 Mpx (y se reescala): a esa escala el trazo ya es borroso
     * por definicion y el coste en una foto de 12 Mpx seria prohibitivo.
     */
    private fun kuwahara(src: Bitmap, radiusFraction: Float): Bitmap {
        val fullW = src.width
        val fullH = src.height
        val scale = min(1f, sqrt(1_500_000f / (fullW.toFloat() * fullH)))
        val work = if (scale < 1f) {
            BitmapUtils.scale(src, max(1, (fullW * scale).roundToInt()), max(1, (fullH * scale).roundToInt()))
        } else {
            src
        }
        val w = work.width
        val h = work.height
        val px = read(work)
        val radius = max(2, (max(w, h) * radiusFraction).roundToInt())
        val integ = Integral(px, w, h)
        val out = IntArray(px.size)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                val a = px[y * w + x] ushr 24
                if (a == 0) continue
                var bestVar = Float.MAX_VALUE
                var br = 0f
                var bg = 0f
                var bb = 0f
                for (q in 0 until 4) {
                    val x0 = if (q and 1 == 0) max(0, x - radius) else x
                    val x1 = if (q and 1 == 0) x else min(w - 1, x + radius)
                    val y0 = if (q and 2 == 0) max(0, y - radius) else y
                    val y1 = if (q and 2 == 0) y else min(h - 1, y + radius)
                    val n = ((x1 - x0 + 1) * (y1 - y0 + 1)).toFloat()
                    val ml = integ.sum(integ.l, x0, y0, x1, y1) / n
                    val v = integ.sum(integ.l2, x0, y0, x1, y1) / n - ml * ml
                    if (v < bestVar) {
                        bestVar = v
                        br = integ.sum(integ.r, x0, y0, x1, y1) / n
                        bg = integ.sum(integ.g, x0, y0, x1, y1) / n
                        bb = integ.sum(integ.b, x0, y0, x1, y1) / n
                    }
                }
                out[y * w + x] = (a shl 24) or (c8(br) shl 16) or (c8(bg) shl 8) or c8(bb)
            }
            }
        }
        val painted = write(out, w, h)
        if (work !== src) work.recycle()
        if (scale >= 1f) return painted
        val big = BitmapUtils.scale(painted, fullW, fullH)
        if (big !== painted) painted.recycle()
        return big
    }

    private fun oil(src: Bitmap, k: Float): Bitmap = kuwahara(src, 0.003f + 0.007f * k)

    /** Acuarela: pintura suave, colores aclarados y semi-posterizados, grano de papel. */
    private fun watercolor(src: Bitmap, k: Float, seed: Long): Bitmap {
        val painted = kuwahara(src, 0.0025f + 0.004f * k)
        val w = painted.width
        val h = painted.height
        val px = read(painted)
        painted.recycle()
        val cell = cellSize(w, h) * 2
        val salt = seedInt(seed)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                val p = px[y * w + x]
                val a = p ushr 24
                if (a == 0) continue
                val paper = hashSigned(x / cell, y / cell, salt) * 7f * (0.4f + k)
                var r = (p ushr 16 and 0xFF).toFloat()
                var g = (p ushr 8 and 0xFF).toFloat()
                var b = (p and 0xFF).toFloat()
                // Posterizado suave a pasos de 32: las manchas de pigmento tienen saltos.
                r = r * 0.55f + (r / 32f).roundToInt() * 32f * 0.45f
                g = g * 0.55f + (g / 32f).roundToInt() * 32f * 0.45f
                b = b * 0.55f + (b / 32f).roundToInt() * 32f * 0.45f
                val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = l + (r - l) * 0.88f
                g = l + (g - l) * 0.88f
                b = l + (b - l) * 0.88f
                // Aclarado: la acuarela deja ver el papel blanco.
                px[y * w + x] = (a shl 24) or
                    (c8(255f * (r / 255f).pow(0.85f) + paper) shl 16) or
                    (c8(255f * (g / 255f).pow(0.85f) + paper) shl 8) or
                    c8(255f * (b / 255f).pow(0.85f) + paper)
            }
            }
        }
        return write(px, w, h)
    }

    /** Vidriera: celdas de Voronoi con el color del centro y juntas oscuras. */
    private fun mosaic(src: Bitmap, k: Float, seed: Long): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val cs = max(6f, max(w, h) * (0.012f + 0.05f * k))
        val gw = (w / cs).toInt() + 3
        val gh = (h / cs).toInt() + 3
        val salt = seedInt(seed)
        val ptX = FloatArray(gw * gh)
        val ptY = FloatArray(gw * gh)
        val color = IntArray(gw * gh)
        for (gj in 0 until gh) {
            for (gi in 0 until gw) {
                val i = gj * gw + gi
                // Indices desplazados en uno para que la rejilla cubra tambien el borde.
                ptX[i] = (gi - 1 + 0.15f + 0.7f * hash01(gi, gj, salt)) * cs
                ptY[i] = (gj - 1 + 0.15f + 0.7f * hash01(gi, gj, salt + 77)) * cs
                val sx = ptX[i].roundToInt().coerceIn(0, w - 1)
                val sy = ptY[i].roundToInt().coerceIn(0, h - 1)
                color[i] = px[sy * w + sx]
            }
        }
        val joint = max(1f, cs * 0.07f)
        val out = IntArray(px.size)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            val gj0 = (y / cs).toInt() + 1
            for (x in 0 until w) {
                val p = px[y * w + x]
                val a = p ushr 24
                if (a == 0) continue
                val gi0 = (x / cs).toInt() + 1
                var d1 = Float.MAX_VALUE
                var d2 = Float.MAX_VALUE
                var best = 0
                for (gj in max(0, gj0 - 1)..min(gh - 1, gj0 + 1)) {
                    for (gi in max(0, gi0 - 1)..min(gw - 1, gi0 + 1)) {
                        val i = gj * gw + gi
                        val dx = x - ptX[i]
                        val dy = y - ptY[i]
                        val d = dx * dx + dy * dy
                        if (d < d1) {
                            d2 = d1; d1 = d; best = i
                        } else if (d < d2) {
                            d2 = d
                        }
                    }
                }
                val c = color[best]
                val edge = smooth(0f, joint, sqrt(d2) - sqrt(d1))
                val shade = (0.3f + 0.7f * edge) * (0.94f + 0.12f * hash01(best, 3, salt))
                out[y * w + x] = (a shl 24) or
                    (c8((c ushr 16 and 0xFF) * shade) shl 16) or
                    (c8((c ushr 8 and 0xFF) * shade) shl 8) or
                    c8((c and 0xFF) * shade)
            }
            }
        }
        return write(out, w, h)
    }

    /** Dispersion: cada pixel se toma de un vecino al azar dentro de un radio. */
    private fun scatter(src: Bitmap, k: Float, seed: Long): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val out = IntArray(px.size)
        val radius = max(1, (k * max(w, h) * 0.012f).roundToInt())
        val span = 2 * radius + 1
        val salt = seedInt(seed)
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            var s = hash(y, 1, salt) or 1
            for (x in 0 until w) {
                s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
                val dx = ((s ushr 3) and 0xFFFF) % span - radius
                val dy = ((s ushr 19) and 0x1FFF) % span - radius
                out[y * w + x] = px[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
            }
            }
        }
        return write(out, w, h)
    }

    // ----------------------------------------------------------------------------------
    // Clima y destellos
    // ----------------------------------------------------------------------------------

    /** Lineas de velocidad de manga: rayos blancos desde fuera de un centro limpio. */
    private fun speedLines(src: Bitmap, k: Float, seed: Long): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val base = if (k > 0.05f) Blur.radialBlur(src, k * 0.45f) else BitmapUtils.mutableCopy(src)
        val canvas = Canvas(base)
        val rnd = Random(seed xor 0x11EEL)
        val cx = w / 2f
        val cy = h / 2f
        val outer = hypot(w, h) / 2f * 1.08f
        val inner = min(w, h) * (0.2f + 0.22f * (1f - k))
        val count = (50 + 170 * k).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val path = Path()
        for (i in 0 until count) {
            val ang = rnd.nextFloat() * 2f * PI.toFloat()
            val half = (0.0018f + rnd.nextFloat() * 0.011f) * (0.6f + k)
            val start = inner * (1f + rnd.nextFloat() * 0.9f)
            paint.color = Color.argb(110 + rnd.nextInt(120), 255, 255, 255)
            path.reset()
            path.moveTo(cx + cos(ang) * start, cy + sin(ang) * start)
            path.lineTo(cx + cos(ang - half) * outer, cy + sin(ang - half) * outer)
            path.lineTo(cx + cos(ang + half) * outer, cy + sin(ang + half) * outer)
            path.close()
            canvas.drawPath(path, paint)
        }
        return base
    }

    /** Lluvia: imagen oscurecida y fria con trazos diagonales; [angle] inclina la caida. */
    private fun rain(src: Bitmap, k: Float, angle: Float, seed: Long): Bitmap {
        val w = src.width
        val h = src.height
        val mood = ColorMatrix().apply {
            setSaturation(1f - 0.22f * k)
            postConcat(ColorMatrix(floatArrayOf(
                0.94f, 0f, 0f, 0f, -6f * k,
                0f, 0.97f, 0f, 0f, -4f * k,
                0f, 0f, 1.03f, 0f, 2f * k,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        val out = Adjust.applyColorMatrix(src, mood)
        val canvas = Canvas(out)
        val rnd = Random(seed xor 0x7A11L)
        val side = max(w, h).toFloat()
        val tilt = Math.toRadians(angle.toDouble() + 12.0)
        val dx = sin(tilt).toFloat()
        val dy = cos(tilt).toFloat()
        // Densidad por proporcion de la imagen, no por megapixel: largo y grosor de cada trazo
        // ya escalan con el lado, asi que la cobertura es la misma en la vista previa y al aplicar.
        val count = ((120 + 750 * k) * (w.toFloat() * h) / (side * side)).toInt().coerceAtLeast(40)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = max(1f, side / 1100f)
        }
        for (i in 0 until count) {
            val x = rnd.nextFloat() * (w + side * 0.2f) - side * 0.1f
            val y = rnd.nextFloat() * h
            val len = side * (0.025f + 0.045f * rnd.nextFloat())
            paint.color = Color.argb(45 + rnd.nextInt(95), 225, 235, 255)
            canvas.drawLine(x, y, x + dx * len, y + dy * len, paint)
        }
        return out
    }

    /** Nieve: copos nitidos y desenfocados (los grandes simulan los que pasan cerca de la lente). */
    private fun snow(src: Bitmap, k: Float, seed: Long): Bitmap {
        val w = src.width
        val h = src.height
        val mood = ColorMatrix().apply {
            setSaturation(0.9f)
            postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 3f * k,
                0f, 1f, 0f, 0f, 5f * k,
                0f, 0f, 1.04f, 0f, 9f * k,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        val out = Adjust.applyColorMatrix(src, mood)
        val canvas = Canvas(out)
        val rnd = Random(seed xor 0x5A0BL)
        val unit = max(w, h) / 1000f
        val count = ((90 + 700 * k) * (w.toFloat() * h) / (max(w, h).toFloat() * max(w, h))).toInt().coerceAtLeast(30)
        val sharp = Paint(Paint.ANTI_ALIAS_FLAG)
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(max(1f, 1.6f * unit), BlurMaskFilter.Blur.NORMAL) }
        val far = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(max(1f, 4.5f * unit), BlurMaskFilter.Blur.NORMAL) }
        for (i in 0 until count) {
            val t = rnd.nextFloat()
            val r = unit * (0.9f + t * t * 5.5f)
            val paint = if (t > 0.9f) far else if (t > 0.6f) soft else sharp
            paint.color = Color.argb(if (t > 0.9f) 150 else 150 + rnd.nextInt(105), 255, 255, 255)
            canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, r * if (t > 0.9f) 1.8f else 1f, paint)
        }
        return out
    }

    /** Destellos de cuatro puntas sobre las zonas mas brillantes. */
    private fun sparkles(src: Bitmap, k: Float, seed: Long): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val out = BitmapUtils.mutableCopy(src)
        val canvas = Canvas(out)
        val rnd = Random(seed xor 0x57A2L)
        val want = (6 + 34 * k).toInt()
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val path = Path()
        var placed = 0
        var tries = 0
        while (placed < want && tries < want * 500) {
            tries++
            val x = rnd.nextInt(w)
            val y = rnd.nextInt(h)
            // Despues de muchos intentos fallidos se admiten zonas menos brillantes: una
            // foto sin luces fuertes aun debe recibir algunos destellos.
            val needed = if (tries > want * 250) 170f else 225f
            if (lum(px[y * w + x]) < needed) continue
            placed++
            val s = max(w, h) * (0.012f + rnd.nextFloat() * 0.03f) * (0.6f + k)
            glow.shader = RadialGradient(
                x.toFloat(), y.toFloat(), s * 0.55f,
                intArrayOf(Color.argb(170, 255, 255, 255), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x.toFloat(), y.toFloat(), s * 0.55f, glow)
            val n = s * 0.11f
            path.reset()
            path.moveTo(x.toFloat(), y - s)
            path.lineTo(x + n, y - n)
            path.lineTo(x + s, y.toFloat())
            path.lineTo(x + n, y + n)
            path.lineTo(x.toFloat(), y + s)
            path.lineTo(x - n, y + n)
            path.lineTo(x - s, y.toFloat())
            path.lineTo(x - n, y - n)
            path.close()
            canvas.drawPath(path, star)
        }
        return out
    }

    // ----------------------------------------------------------------------------------
    // Color
    // ----------------------------------------------------------------------------------

    /** Doble exposicion: la propia foto, volteada y teñida en duotono, mezclada en pantalla. */
    private fun doubleExposure(src: Bitmap, k: Float, colorA: Int, colorB: Int): Bitmap {
        val w = src.width
        val h = src.height
        val flipped = BitmapUtils.create(w, h)
        val m = Matrix().apply {
            postScale(-1.12f, 1.12f, w / 2f, h / 2f)
        }
        Canvas(flipped).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        val tinted = Effects.duotone(flipped, colorA, colorB)
        flipped.recycle()
        val out = BitmapUtils.mutableCopy(src)
        val paint = Paint().apply {
            blendMode = BlendMode.SCREEN
            alpha = (255 * (0.25f + 0.75f * k)).roundToInt()
        }
        Canvas(out).drawBitmap(tinted, 0f, 0f, paint)
        tinted.recycle()
        return out
    }

    /** Anaglifo rojo/cian: el canal rojo se desplaza a un lado y los demas al otro. */
    private fun anaglyph(src: Bitmap, k: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val out = IntArray(px.size)
        val d = max(1, (k * max(w, h) * 0.012f).roundToInt())
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                val left = px[y * w + (x - d).coerceIn(0, w - 1)]
                val right = px[y * w + (x + d).coerceIn(0, w - 1)]
                out[y * w + x] = (px[y * w + x] and 0xFF000000.toInt()) or
                    (left and 0x00FF0000) or (right and 0x0000FFFF)
            }
            }
        }
        return write(out, w, h)
    }

    /** Mapa de calor termico: la luminancia recorre azul, cian, verde, amarillo y rojo. */
    private fun heatmap(src: Bitmap, k: Float): Bitmap {
        val stops = arrayOf(
            floatArrayOf(10f, 20f, 120f),
            floatArrayOf(0f, 160f, 255f),
            floatArrayOf(60f, 230f, 90f),
            floatArrayOf(255f, 230f, 40f),
            floatArrayOf(235f, 30f, 20f),
        )
        val table = Array(256) { i ->
            val t = i / 255f * (stops.size - 1)
            val a = stops[t.toInt().coerceAtMost(stops.size - 2)]
            val b = stops[t.toInt().coerceAtMost(stops.size - 2) + 1]
            val f = t - t.toInt().coerceAtMost(stops.size - 2)
            floatArrayOf(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f)
        }
        val w = src.width
        val h = src.height
        val px = read(src)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val p = px[i]
            if ((p ushr 24) == 0) continue
            val c = table[c8(lum(p))]
            px[i] = mixTo(p, c[0], c[1], c[2], k)
            }
        }
        return write(px, w, h)
    }

    /**
     * Tonos divididos: [colorA] tine las sombras y [colorB] las luces. Desplaza cada
     * canal hacia el tinte en lugar de mezclar color plano, asi se conserva el contraste.
     */
    private fun splitTone(src: Bitmap, k: Float, colorA: Int, colorB: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val f = 0.6f * k
        val ar = (Color.red(colorA) - 128) * f
        val ag = (Color.green(colorA) - 128) * f
        val ab = (Color.blue(colorA) - 128) * f
        val br = (Color.red(colorB) - 128) * f
        val bg = (Color.green(colorB) - 128) * f
        val bb = (Color.blue(colorB) - 128) * f
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            val l = lum(p) / 255f
            val sw = 1f - smooth(0.05f, 0.65f, l)
            val hw = smooth(0.35f, 0.95f, l)
            px[i] = (a shl 24) or
                (c8((p ushr 16 and 0xFF) + ar * sw + br * hw) shl 16) or
                (c8((p ushr 8 and 0xFF) + ag * sw + bg * hw) shl 8) or
                c8((p and 0xFF) + ab * sw + bb * hw)
            }
        }
        return write(px, w, h)
    }

    /** Ruido de sensor: crominancia por canal, mas fuerte en sombras, como un ISO muy alto. */
    private fun sensorNoise(src: Bitmap, k: Float, seed: Long): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val cell = max(1, (max(w, h) / 2400f).roundToInt())
        val salt = seedInt(seed)
        val strength = k * 60f
        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            val cy = y / cell
            for (x in 0 until w) {
                val p = px[y * w + x]
                val a = p ushr 24
                if (a == 0) continue
                val cx = x / cell
                val l = lum(p) / 255f
                val s = strength * (1.3f - l)
                val shared = hashSigned(cx, cy, salt) * 0.5f
                px[y * w + x] = (a shl 24) or
                    (c8((p ushr 16 and 0xFF) + (hashSigned(cx, cy, salt + 1) + shared) * s) shl 16) or
                    (c8((p ushr 8 and 0xFF) + (hashSigned(cx, cy, salt + 2) + shared) * s) shl 8) or
                    c8((p and 0xFF) + (hashSigned(cx, cy, salt + 3) + shared) * s)
            }
            }
        }
        return write(px, w, h)
    }

    // ----------------------------------------------------------------------------------
    // Cielo
    // ----------------------------------------------------------------------------------

    private enum class SkyLook { GOLDEN, NIGHT, DRAMATIC, BLUE }

    /**
     * Mascara de cielo por heuristica de color y posicion, no por IA: azul dominante o
     * nube clara y poco saturada, mas probable arriba, y solo mientras la columna siga
     * "conectada" con el borde superior (un coche azul abajo no cuenta). Falla con paredes
     * blancas en la mitad alta o con cielos muy cubiertos y oscuros.
     */
    private fun skyMask(px: IntArray, w: Int, h: Int): FloatArray {
        val raw = FloatArray(w * h)
        for (y in 0 until h) {
            val vf = (1f - y.toFloat() / h * 1.15f).coerceIn(0f, 1f).pow(0.6f)
            if (vf <= 0f) continue
            for (x in 0 until w) {
                val p = px[y * w + x]
                if ((p ushr 24) == 0) continue
                val r = p ushr 16 and 0xFF
                val g = p ushr 8 and 0xFF
                val b = p and 0xFF
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                val sat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
                val l = lum(p)
                val blue = if (b > r + 8 && b >= g - 6 && b > 90) ((b - r) / 90f).coerceIn(0f, 1f) else 0f
                val cloud = if (sat < 0.22f && l > 150f) ((l - 150f) / 70f).coerceIn(0f, 1f) * (1f - sat / 0.22f) else 0f
                raw[y * w + x] = max(blue, cloud * 0.85f) * vf
            }
        }
        val limit = max(4, h / 18)
        for (x in 0 until w) {
            var run = 0
            var active = true
            for (y in 0 until h) {
                val i = y * w + x
                if (!active) {
                    raw[i] = 0f
                    continue
                }
                if (raw[i] < 0.25f) {
                    run++
                    if (run > limit) {
                        active = false
                        raw[i] = 0f
                    }
                } else {
                    run = 0
                }
            }
        }
        // Se suaviza el borde para que el cambio de cielo a tierra no tenga escalones.
        val gray = IntArray(w * h) { val v = c8(raw[it] * 255f); 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v }
        val grayBmp = write(gray, w, h)
        val blurred = Blur.blur(grayBmp, max(2f, max(w, h) * 0.004f))
        grayBmp.recycle()
        val soft = read(blurred)
        blurred.recycle()
        for (i in raw.indices) raw[i] = (soft[i] and 0xFF) / 255f
        return raw
    }

    private fun sky(src: Bitmap, k: Float, look: SkyLook, seed: Long): Bitmap {
        val w = src.width
        val h = src.height
        val px = read(src)
        val mask = skyMask(px, w, h)
        // Fila mas baja con cielo claro: el degradado de horizonte se reparte hasta ahi.
        var horizon = 1
        for (y in h - 1 downTo 0) {
            var hit = false
            for (x in 0 until w step max(1, w / 64)) if (mask[y * w + x] > 0.5f) { hit = true; break }
            if (hit) { horizon = y; break }
        }
        val salt = seedInt(seed)
        val star = cellSize(w, h)
        val boost = 1f + 1.1f * k
        val dramaticSat = 1f + 0.55f * k
        for (y in 0 until h) {
            val t = (y.toFloat() / max(1, horizon)).coerceIn(0f, 1f)
            for (x in 0 until w) {
                val i = y * w + x
                val p = px[i]
                if ((p ushr 24) == 0) continue
                val m = mask[i]
                val r = (p ushr 16 and 0xFF).toFloat()
                val g = (p ushr 8 and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()
                val l = lum(p)
                when (look) {
                    SkyLook.GOLDEN -> {
                        // Degradado violeta -> naranja -> amarillo; la luminancia original
                        // conserva las nubes.
                        val gr = if (t < 0.55f) lerp3(60f, 80f, 150f, 255f, 130f, 80f, t / 0.55f)
                        else lerp3(255f, 130f, 80f, 255f, 205f, 130f, (t - 0.55f) / 0.45f)
                        val f = 0.5f + 0.8f * (l / 255f)
                        var o = mixTo(p, gr[0] * f, gr[1] * f, gr[2] * f, m * k * 0.92f)
                        // Todo el paisaje se calienta un poco, mas fuerte donde no es cielo.
                        o = mixTo(o, r * 1.12f + 8f, g * 1.0f, b * 0.82f, k * 0.35f * (1f - m * 0.5f))
                        px[i] = o
                    }
                    SkyLook.NIGHT -> {
                        val gr = lerp3(4f, 8f, 30f, 32f, 46f, 100f, t)
                        val f = 0.55f + 0.45f * (l / 255f)
                        var o = mixTo(p, gr[0] * f, gr[1] * f, gr[2] * f, m * k * 0.97f)
                        // Estrellas: puntos aislados en el cielo, mas raros cerca del horizonte.
                        if (m > 0.7f && k > 0.2f &&
                            hash01(x / star, y / star, salt) > 1f - 0.0016f * k * (1.2f - t)
                        ) {
                            val s = 160f + 95f * hash01(x / star, y / star, salt + 9)
                            o = mixTo(o, s, s, s, m)
                        }
                        // La tierra pasa a luz de luna: oscura, azulada y sin saturar.
                        val gl = l * 0.32f
                        px[i] = mixTo(
                            o,
                            gl * 0.8f + r * 0.1f + 4f,
                            gl * 1.0f + g * 0.12f + 10f,
                            gl * 1.45f + b * 0.2f + 30f,
                            k * (1f - m) * 0.9f,
                        )
                    }
                    SkyLook.DRAMATIC -> {
                        val tl = dramaticTone(l, boost)
                        px[i] = mixTo(
                            p,
                            tl + (dramaticTone(r, boost) - tl) * dramaticSat - 6f * k,
                            tl + (dramaticTone(g, boost) - tl) * dramaticSat,
                            tl + (dramaticTone(b, boost) - tl) * dramaticSat + 8f * k,
                            m * k,
                        ).let { o ->
                            // El suelo gana un poco de contraste para acompanar.
                            mixTo(o, 128f + (r - 128f) * 1.18f, 128f + (g - 128f) * 1.18f, 128f + (b - 128f) * 1.18f, k * 0.5f * (1f - m))
                        }
                    }
                    SkyLook.BLUE -> {
                        val sat = 1f + 0.9f * k
                        px[i] = mixTo(
                            p,
                            (l + (r - l) * sat) * (1f - 0.22f * k),
                            (l + (g - l) * sat) * (1f - 0.04f * k),
                            (l + (b - l) * sat) * (1f + 0.14f * k),
                            m,
                        )
                    }
                }
            }
        }
        return write(px, w, h)
    }

    private fun dramaticTone(c: Float, boost: Float): Float = 128f + (c * 0.8f - 110f) * boost

    private fun lerp3(r0: Float, g0: Float, b0: Float, r1: Float, g1: Float, b1: Float, t: Float): FloatArray =
        floatArrayOf(r0 + (r1 - r0) * t, g0 + (g1 - g0) * t, b0 + (b1 - b0) * t)
}
