package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.Paint
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.EffectKind
import com.dskmusic.pixa.core.model.EffectSpec
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Efectos de un solo paso (requisito 13).
 *
 * Los que se pueden expresar como matriz de color o tabla de consulta van por esa via.
 * Para el semitono, que es el unico realmente caro por pixel, se intenta primero AGSL y
 * se cae a la implementacion en CPU si el dispositivo no lo soporta.
 *
 * Todas las funciones devuelven un bitmap nuevo.
 */
object Effects {

    fun apply(src: Bitmap, spec: EffectSpec): Bitmap = when (spec.kind) {
        EffectKind.NONE -> BitmapUtils.mutableCopy(src)
        EffectKind.BW -> blackAndWhite(src, spec.amount)
        EffectKind.SEPIA -> sepia(src, spec.amount)
        EffectKind.INVERT -> invert(src)
        EffectKind.THRESHOLD -> threshold(src, spec.amount)
        EffectKind.POSTERIZE -> posterize(src, spec.levels)
        EffectKind.DUOTONE -> duotone(src, spec.colorA, spec.colorB)
        EffectKind.GLITCH -> glitch(src, spec.amount, spec.levels, spec.seed)
        EffectKind.BROKEN_TV -> brokenTv(src, spec.amount, spec.levels, spec.seed)
        EffectKind.RGB_SPLIT -> rgbSplit(src, spec.amount, spec.angle)
        EffectKind.HALFTONE -> halftone(src, spec.amount)
        EffectKind.VINTAGE -> vintage(src, spec.amount)
        EffectKind.MOTION_BLUR -> Blur.motionBlur(
            src,
            (spec.amount.coerceIn(0f, 1f) * 60f * sideScale(src)).roundToInt().coerceAtLeast(1),
            spec.angle,
        )
        EffectKind.RADIAL_BLUR -> Blur.radialBlur(src, spec.amount)
        // Radio relativo al lado mayor (hasta ~8 %): la vista previa reducida y el resultado
        // a resolucion completa se ven igual de desenfocados.
        EffectKind.GAUSSIAN_BLUR -> Blur.blur(
            src,
            (spec.amount.coerceIn(0f, 1f) * 0.08f * maxOf(src.width, src.height)).coerceAtLeast(0.5f),
        )
        EffectKind.PIXELATE -> pixelate(src, spec.amount)
        EffectKind.SOLARIZE -> solarize(src, spec.amount)
        EffectKind.SCANLINES -> scanlines(src, spec.amount)
        EffectKind.MIRROR -> mirror(src, spec.angle, spec.amount)
        // Efectos de la ampliacion: viven aparte para no inflar este archivo.
        else -> MoreEffects.apply(src, spec)
    }

    // ----------------------------------------------------------------------------------
    // Efectos de matriz de color
    // ----------------------------------------------------------------------------------

    /** Blanco y negro con pesos de luminancia reales; [amount] mezcla con el original. */
    fun blackAndWhite(src: Bitmap, amount: Float = 1f): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        val m = ColorMatrix()
        m.setSaturation(1f - k)
        return Adjust.applyColorMatrix(src, m)
    }

    fun sepia(src: Bitmap, amount: Float = 1f): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        // Se desatura segun la intensidad y luego se vira a tierra: asi un amount
        // intermedio da un sepia parcial sin necesidad de mezclar dos bitmaps.
        val m = ColorMatrix().apply {
            setSaturation(1f - k)
            postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 36f * k,
                0f, 1f, 0f, 0f, 16f * k,
                0f, 0f, 1f, 0f, -22f * k,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        return Adjust.applyColorMatrix(src, m)
    }

    fun invert(src: Bitmap): Bitmap = Adjust.applyColorMatrix(
        src,
        ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )),
    )

    // ----------------------------------------------------------------------------------
    // Efectos de tabla de consulta
    // ----------------------------------------------------------------------------------

    /** Umbral binario sobre la luminancia. [amount] 0..1 mueve el punto de corte. */
    fun threshold(src: Bitmap, amount: Float): Bitmap {
        val cut = (amount.coerceIn(0f, 1f) * 255f).roundToInt()
        return mapByLuma(src) { lum ->
            val v = if (lum >= cut) 255 else 0
            Color.rgb(v, v, v)
        }
    }

    /** Reduce a [levels] niveles por canal. */
    fun posterize(src: Bitmap, levels: Int): Bitmap {
        val n = levels.coerceIn(2, 32)
        val step = 255f / (n - 1)
        val lut = IntArray(256) { i ->
            ((i / step).roundToInt() * step).roundToInt().coerceIn(0, 255)
        }
        return ToneOps.applyChannelLuts(src, lut, lut, lut)
    }

    /** Dos tonos interpolados por luminancia (requisito 13). */
    fun duotone(src: Bitmap, colorA: Int, colorB: Int): Bitmap {
        val ar = Color.red(colorA); val ag = Color.green(colorA); val ab = Color.blue(colorA)
        val br = Color.red(colorB); val bg = Color.green(colorB); val bb = Color.blue(colorB)
        // Tabla de 256 colores: la pasada por pixel se queda en una indexacion.
        val table = IntArray(256) { i ->
            val t = i / 255f
            Color.rgb(
                (ar + (br - ar) * t).roundToInt().coerceIn(0, 255),
                (ag + (bg - ag) * t).roundToInt().coerceIn(0, 255),
                (ab + (bb - ab) * t).roundToInt().coerceIn(0, 255),
            )
        }
        return mapByLuma(src) { lum -> table[lum] }
    }

    /**
     * Vintage: curva en S suave, virado calido, bajada de contraste en negros,
     * vineta y grano. [amount] controla toda la mezcla.
     */
    fun vintage(src: Bitmap, amount: Float): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        // Negros levantados y blancos recortados, como una emulsion vieja.
        val lift = (18f * k).roundToInt()
        val ceil = 255 - (12f * k).roundToInt()
        val lr = IntArray(256) { i -> (lift + i * (ceil - lift) / 255f).roundToInt().coerceIn(0, 255) }
        val lg = IntArray(256) { i -> (lift + i * (ceil - lift) / 255f).roundToInt().coerceIn(0, 255) }
        val lb = IntArray(256) { i ->
            (lift + 8f * k + i * (ceil - lift) / 255f).roundToInt().coerceIn(0, 255)
        }
        val toned = ToneOps.applyChannelLuts(src, lr, lg, lb)

        val warm = ColorMatrix().apply {
            setSaturation(1f - 0.25f * k)
            postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 14f * k,
                0f, 1f, 0f, 0f, 4f * k,
                0f, 0f, 1f, 0f, -10f * k,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        val out = Adjust.applyColorMatrix(toned, warm)
        toned.recycle()
        Adjust.drawVignette(out, -0.45f * k)
        Adjust.drawGrain(out, 0.35f * k)
        return out
    }

    // ----------------------------------------------------------------------------------
    // Efectos de desplazamiento
    // ----------------------------------------------------------------------------------

    /**
     * Glitch: bandas horizontales desplazadas al azar mas una separacion de canales en
     * algunas de ellas. La semilla es fija para que el resultado sea estable mientras se
     * mueve el slider de intensidad.
     */
    fun glitch(src: Bitmap, amount: Float, bandCount: Int = 12, seed: Long = 11L): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        if (k <= 0f) return BitmapUtils.mutableCopy(src)

        val w = src.width
        val h = src.height
        val inPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)
        val outPx = inPx.copyOf()

        val rnd = java.util.Random(seed)
        val bands = bandCount.coerceIn(1, 60)
        val maxShift = (w * 0.12f * k)

        repeat(bands) {
            val bandTop = rnd.nextInt(h)
            val bandHeight = 2 + rnd.nextInt(max(2, (h * 0.06f).toInt()))
            val shift = ((rnd.nextFloat() - 0.5f) * 2f * maxShift).roundToInt()
            // En una banda de cada tres se separan los canales, que es lo que da el color.
            val splitChannels = rnd.nextInt(3) == 0
            val chShift = if (splitChannels) (maxShift * 0.35f).roundToInt().coerceAtLeast(1) else 0

            for (y in bandTop until min(h, bandTop + bandHeight)) {
                val row = y * w
                for (x in 0 until w) {
                    val sx = (x - shift).coerceIn(0, w - 1)
                    var p = inPx[row + sx]
                    if (chShift != 0) {
                        val rx = (sx + chShift).coerceIn(0, w - 1)
                        val bx = (sx - chShift).coerceIn(0, w - 1)
                        p = (p and -0x1000000) or
                            (inPx[row + rx] and 0x00FF0000) or
                            (p and 0x0000FF00) or
                            (inPx[row + bx] and 0x000000FF)
                    }
                    outPx[row + x] = p
                }
            }
        }

        val out = BitmapUtils.create(w, h)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    /** Separacion cromatica: rojo y azul desplazados en direcciones opuestas. */
    fun rgbSplit(src: Bitmap, amount: Float, angleDeg: Float): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        if (k <= 0f) return BitmapUtils.mutableCopy(src)

        val w = src.width
        val h = src.height
        val inPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)
        val outPx = IntArray(w * h)

        val rad = Math.toRadians(angleDeg.toDouble())
        val dist = max(w, h) * 0.035f * k
        val dx = (cos(rad) * dist).roundToInt()
        val dy = (sin(rad) * dist).roundToInt()

        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                val base = inPx[y * w + x]
                val rp = inPx[(y - dy).coerceIn(0, h - 1) * w + (x - dx).coerceIn(0, w - 1)]
                val bp = inPx[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
                // El alfa se toma del maximo para que el borde de una capa recortada no se rompa.
                val a = max(base ushr 24 and 0xFF, max(rp ushr 24 and 0xFF, bp ushr 24 and 0xFF))
                outPx[y * w + x] = (a shl 24) or
                    (rp and 0x00FF0000) or
                    (base and 0x0000FF00) or
                    (bp and 0x000000FF)
            }
            }
        }

        val out = BitmapUtils.create(w, h)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    // ----------------------------------------------------------------------------------
    // Semitono: AGSL con fallback en CPU
    // ----------------------------------------------------------------------------------

    private const val HALFTONE_AGSL = """
        uniform shader image;
        uniform float2 size;
        uniform float cell;

        half4 main(float2 coord) {
            // Centro de la celda a la que pertenece el pixel.
            float2 cellIndex = floor(coord / cell);
            float2 center = (cellIndex + 0.5) * cell;
            half4 src = image.eval(clamp(center, float2(0.0), size - 1.0));
            float lum = dot(float3(src.r, src.g, src.b), float3(0.2126, 0.7152, 0.0722));
            // Punto mas grande donde la imagen es oscura.
            float radius = (1.0 - lum) * cell * 0.72;
            float d = distance(coord, center);
            // Borde de un pixel para que el punto no quede aliasado.
            float inside = 1.0 - smoothstep(radius - 1.0, radius + 1.0, d);
            half4 here = image.eval(coord);
            return half4(0.0, 0.0, 0.0, half(inside) * here.a);
        }
    """

    /**
     * Semitono de puntos negros sobre el fondo. Es el efecto mas caro por pixel, asi que
     * se intenta en AGSL (API 33+) y solo si no se puede se recorre en CPU.
     */
    fun halftone(src: Bitmap, amount: Float): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        // Celdas grandes = trama mas marcada.
        val cell = ((3f + k * 14f) * sideScale(src)).coerceAtLeast(2f)

        val dots = Agsl.run(src, HALFTONE_AGSL, mapOf("cell" to floatArrayOf(cell)))
            ?: halftoneCpu(src, cell)

        val out = BitmapUtils.create(src.width, src.height)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(dots, 0f, 0f, null)
        // DST_IN recorta el resultado al alfa original, para que una capa con
        // transparencia no acabe con un rectangulo blanco alrededor.
        canvas.drawBitmap(src, 0f, 0f, Paint().apply {
            blendMode = android.graphics.BlendMode.DST_IN
        })
        dots.recycle()
        return out
    }

    private fun halftoneCpu(src: Bitmap, cell: Float): Bitmap {
        val w = src.width
        val h = src.height
        val inPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)
        val outPx = IntArray(w * h)
        val c = max(2f, cell)

        var cy = 0f
        while (cy < h) {
            var cx = 0f
            while (cx < w) {
                val centerX = cx + c / 2f
                val centerY = cy + c / 2f
                val sx = centerX.toInt().coerceIn(0, w - 1)
                val sy = centerY.toInt().coerceIn(0, h - 1)
                val p = inPx[sy * w + sx]
                val lum = (0.2126f * (p ushr 16 and 0xFF) +
                    0.7152f * (p ushr 8 and 0xFF) +
                    0.0722f * (p and 0xFF)) / 255f
                val radius = (1f - lum) * c * 0.72f
                val r2 = radius * radius

                val y0 = cy.toInt().coerceIn(0, h - 1)
                val y1 = (cy + c).toInt().coerceIn(0, h)
                val x0 = cx.toInt().coerceIn(0, w - 1)
                val x1 = (cx + c).toInt().coerceIn(0, w)
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val dx = x + 0.5f - centerX
                        val dy = y + 0.5f - centerY
                        if (dx * dx + dy * dy <= r2) {
                            val a = inPx[y * w + x] ushr 24 and 0xFF
                            outPx[y * w + x] = a shl 24
                        }
                    }
                }
                cx += c
            }
            cy += c
        }

        val out = BitmapUtils.create(w, h)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    // ----------------------------------------------------------------------------------
    // Requisito del usuario: unos pocos efectos mas
    // ----------------------------------------------------------------------------------

    /** Mosaico de bloques; reutiliza el mismo pixelado que el pincel censurador. */
    fun pixelate(src: Bitmap, amount: Float): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        // Bug encontrado (requisito del usuario): la formula anterior escalaba con el
        // lado del bitmap y se saturaba al tamano maximo de bloque para casi cualquier
        // valor del deslizador por encima de 0.3, asi que se notaba poco o nada el
        // cambio. En bloques fijos (no relativos al tamano) se ve bien en todo el rango,
        // de un pixelado sutil a uno muy marcado.
        // Bloque relativo al lado mayor (1080 px de referencia): la vista previa reducida y el
        // resultado a resolucion completa tienen que verse igual de pixelados.
        val block = ((4 + k * 56f) * sideScale(src)).roundToInt().coerceAtLeast(2)
        return Retouch.pixelate(src, block)
    }

    /** Factor que lleva un tamano en pixeles pensado para 1080 px de lado mayor al de [src]. */
    internal fun sideScale(src: Bitmap): Float = max(src.width, src.height) / 1080f

    /** Invierte los tonos por encima del punto de corte (revelado solarizado clasico). */
    fun solarize(src: Bitmap, amount: Float): Bitmap {
        val cut = (255 - amount.coerceIn(0f, 1f) * 200f).roundToInt().coerceIn(0, 255)
        val lut = IntArray(256) { i -> if (i > cut) 255 - i else i }
        return ToneOps.applyChannelLuts(src, lut, lut, lut)
    }

    /** Lineas horizontales oscuras, como un tubo de rayos catodicos viejo. */
    fun scanlines(src: Bitmap, amount: Float): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        val out = BitmapUtils.mutableCopy(src)
        val canvas = Canvas(out)
        val step = max(2, (out.height / 180f).roundToInt())
        val paint = Paint().apply { color = Color.BLACK; alpha = (k * 160).roundToInt() }
        var y = 0
        while (y < out.height) {
            canvas.drawRect(0f, y.toFloat(), out.width.toFloat(), y + step / 2f, paint)
            y += step
        }
        return out
    }

    /** Espeja una mitad sobre la otra; [angleDeg] elige el eje, igual que el reparto RGB. */
    fun mirror(src: Bitmap, angleDeg: Float, amount: Float = 1f): Bitmap {
        val w = src.width
        val h = src.height
        val out = BitmapUtils.create(w, h)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val vertical = ((angleDeg % 180f) + 180f) % 180f < 90f
        canvas.drawBitmap(src, 0f, 0f, paint)
        canvas.save()
        // [amount] mezcla la mitad espejada con la original; a 0 no cambia nada.
        val mirrorPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (amount.coerceIn(0f, 1f) * 255f).roundToInt()
        }
        if (vertical) {
            canvas.clipRect(0f, 0f, w / 2f, h.toFloat())
            canvas.scale(-1f, 1f, w / 2f, 0f)
            canvas.drawBitmap(src, 0f, 0f, mirrorPaint)
        } else {
            canvas.clipRect(0f, 0f, w.toFloat(), h / 2f)
            canvas.scale(1f, -1f, 0f, h / 2f)
            canvas.drawBitmap(src, 0f, 0f, mirrorPaint)
        }
        canvas.restore()
        return out
    }

    /**
     * Television mal sintonizada (requisito del usuario): sangrado de color, bandas de
     * interferencia horizontal, grano de estatica y un par de lineas de barrido
     * brillantes. [bandCount] (reutiliza [EffectSpec.levels]) y [seed] dan control fino y
     * un boton de "Aleatorio" sin anadir mas campos al modelo.
     */
    fun brokenTv(src: Bitmap, amount: Float, bandCount: Int, seed: Long): Bitmap {
        val k = amount.coerceIn(0f, 1f)
        if (k <= 0f) return BitmapUtils.mutableCopy(src)

        val w = src.width
        val h = src.height
        val inPx = IntArray(w * h)
        src.getPixels(inPx, 0, w, 0, 0, w, h)
        val outPx = IntArray(w * h)
        val rnd = java.util.Random(seed)

        // Sangrado de color: rojo y azul desplazados un poco en direcciones opuestas.
        val bleed = (w * 0.012f * k).roundToInt()
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val p = inPx[row + x]
                val rx = (x + bleed).coerceIn(0, w - 1)
                val bx = (x - bleed).coerceIn(0, w - 1)
                outPx[row + x] = (p and -0x1000000) or
                    (inPx[row + rx] and 0x00FF0000) or
                    (p and 0x0000FF00) or
                    (inPx[row + bx] and 0x000000FF)
            }
        }

        // Bandas de interferencia: filas desplazadas, como un mal sintonizado.
        val bands = bandCount.coerceIn(1, 40)
        val maxShift = w * 0.22f * k
        repeat(bands) {
            val bandTop = rnd.nextInt(h)
            val bandHeight = 1 + rnd.nextInt(max(2, (h * 0.03f).toInt()))
            val shift = ((rnd.nextFloat() - 0.5f) * 2f * maxShift).roundToInt()
            for (y in bandTop until min(h, bandTop + bandHeight)) {
                val row = y * w
                val copy = outPx.copyOfRange(row, row + w)
                for (x in 0 until w) {
                    outPx[row + x] = copy[(x - shift).coerceIn(0, w - 1)]
                }
            }
        }

        // Grano de estatica: un ruido de brillo aleatorio por pixel.
        val noise = (55 * k).roundToInt()
        if (noise > 0) {
            for (i in outPx.indices) {
                val p = outPx[i]
                val n = rnd.nextInt(noise * 2 + 1) - noise
                val r = ((p ushr 16 and 0xFF) + n).coerceIn(0, 255)
                val g = ((p ushr 8 and 0xFF) + n).coerceIn(0, 255)
                val b = ((p and 0xFF) + n).coerceIn(0, 255)
                outPx[i] = (p and -0x1000000) or (r shl 16) or (g shl 8) or b
            }
        }

        // Un par de lineas de barrido brillantes, no siempre las dos.
        repeat(2) {
            if (rnd.nextFloat() < k) {
                val row = rnd.nextInt(h) * w
                for (x in 0 until w) {
                    val p = outPx[row + x]
                    val r = ((p ushr 16 and 0xFF) + 90).coerceAtMost(255)
                    val g = ((p ushr 8 and 0xFF) + 90).coerceAtMost(255)
                    val b = ((p and 0xFF) + 90).coerceAtMost(255)
                    outPx[row + x] = (p and -0x1000000) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        val out = BitmapUtils.create(w, h)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    // ----------------------------------------------------------------------------------
    // Utilidad
    // ----------------------------------------------------------------------------------

    /** Recorre la imagen traduciendo cada pixel segun su luminancia, respetando el alfa. */
    private inline fun mapByLuma(src: Bitmap, crossinline map: (Int) -> Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val p = px[i]
            val a = p ushr 24 and 0xFF
            if (a == 0) {
                px[i] = 0
                continue
            }
            val lum = (0.2126f * (p ushr 16 and 0xFF) +
                0.7152f * (p ushr 8 and 0xFF) +
                0.0722f * (p and 0xFF)).roundToInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (map(lum) and 0xFFFFFF)
            }
        }
        val out = BitmapUtils.create(w, h)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
