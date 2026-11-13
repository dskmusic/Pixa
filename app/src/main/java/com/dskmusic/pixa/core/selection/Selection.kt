package com.dskmusic.pixa.core.selection

import com.dskmusic.pixa.core.image.Parallel
import android.graphics.Bitmap
import android.graphics.Rect
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.image.Blur
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Seleccion del lienzo (requisito 8).
 *
 * La seleccion se guarda como una mascara de cobertura de un byte por pixel, no como una
 * figura geometrica. Eso es lo que permite que rectangulo, elipse, lazo, varita magica y
 * seleccion por color se combinen entre si con los modos anadir, restar e intersecar sin
 * ningun caso especial, y que calar los bordes sea simplemente desenfocar la mascara.
 *
 * Se usa un ByteArray en vez de un Bitmap ALPHA_8 porque el acceso a pixel es directo y
 * no depende del relleno de fila que el sistema elija. El bitmap se construye solo cuando
 * hay que pintar ([toAlphaBitmap]).
 */
class Selection private constructor(
    val width: Int,
    val height: Int,
    /** Cobertura 0..255 por pixel, tamano width * height. */
    val mask: ByteArray,
) {

    /** Rectangulo que contiene todo lo seleccionado. Vacio si no hay seleccion. */
    val bounds: Rect by lazy { computeBounds() }

    val isEmpty: Boolean get() = bounds.isEmpty

    fun coverageAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        return mask[y * width + x].toInt() and 0xFF
    }

    fun contains(x: Int, y: Int): Boolean = coverageAt(x, y) >= 128

    private fun computeBounds(): Rect {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((mask[row + x].toInt() and 0xFF) == 0) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return if (maxX < 0) Rect() else Rect(minX, minY, maxX + 1, maxY + 1)
    }

    // ----------------------------------------------------------------------------------
    // Combinacion
    // ----------------------------------------------------------------------------------

    enum class Mode { NEW, ADD, SUBTRACT, INTERSECT }

    /** Combina esta seleccion con [other] segun [mode]. */
    fun combine(other: Selection, mode: Mode): Selection {
        require(other.width == width && other.height == height) {
            "Las selecciones deben tener el mismo tamano"
        }
        if (mode == Mode.NEW) return other
        val out = ByteArray(mask.size)
        Parallel.rows(mask.size) { i0, i1 ->
            for (i in i0 until i1) {
            val a = mask[i].toInt() and 0xFF
            val b = other.mask[i].toInt() and 0xFF
            out[i] = when (mode) {
                Mode.ADD -> max(a, b)
                Mode.SUBTRACT -> max(0, a - b)
                Mode.INTERSECT -> min(a, b)
                Mode.NEW -> b
            }.toByte()
            }
        }
        return Selection(width, height, out)
    }

    fun inverted(): Selection {
        val out = ByteArray(mask.size)
        for (i in mask.indices) out[i] = (255 - (mask[i].toInt() and 0xFF)).toByte()
        return Selection(width, height, out)
    }

    // ----------------------------------------------------------------------------------
    // Calar, expandir y contraer
    // ----------------------------------------------------------------------------------

    /** Suaviza el borde con un radio en pixeles. */
    fun feathered(radius: Float): Selection {
        if (radius <= 0f) return this
        val bmp = toAlphaArgb()
        Blur.blurInPlace(bmp, radius.toInt().coerceAtLeast(1))
        val out = fromAlphaArgb(bmp)
        bmp.recycle()
        return out
    }

    /**
     * Expande ([pixels] > 0) o contrae ([pixels] < 0) el borde.
     *
     * Se desenfoca la mascara y se vuelve a umbralizar: desplazar el umbral equivale a
     * mover el contorno, lo que evita escribir una dilatacion morfologica completa y da
     * un resultado indistinguible a los radios que se usan en la practica.
     */
    fun grown(pixels: Int): Selection {
        if (pixels == 0) return this
        val radius = kotlin.math.abs(pixels)
        val bmp = toAlphaArgb()
        Blur.blurInPlace(bmp, radius)
        val px = IntArray(width * height)
        bmp.getPixels(px, 0, width, 0, 0, width, height)
        bmp.recycle()
        // Umbral bajo = el desenfoque cuenta como dentro (expandir).
        val threshold = if (pixels > 0) 40 else 215
        val out = ByteArray(mask.size)
        Parallel.rows(px.size) { i0, i1 ->
            for (i in i0 until i1) {
            val a = px[i] ushr 24 and 0xFF
            out[i] = if (a >= threshold) 255.toByte() else 0
            }
        }
        return Selection(width, height, out)
    }

    // ----------------------------------------------------------------------------------
    // Conversion a bitmap
    // ----------------------------------------------------------------------------------

    /**
     * Mascara como bitmap ALPHA_8, lista para recortar con DST_IN.
     * Si el sistema rellena las filas, se cae a ARGB_8888, que siempre encaja.
     */
    fun toAlphaBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        return if (bmp.rowBytes == width) {
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(mask))
            bmp
        } else {
            bmp.recycle()
            toAlphaArgb()
        }
    }

    /** Mascara como ARGB_8888 con el color en negro y la cobertura en el alfa. */
    fun toAlphaArgb(): Bitmap {
        val px = IntArray(width * height)
        for (i in px.indices) px[i] = (mask[i].toInt() and 0xFF) shl 24
        val bmp = BitmapUtils.create(width, height)
        bmp.setPixels(px, 0, width, 0, 0, width, height)
        return bmp
    }

    companion object {

        fun empty(width: Int, height: Int): Selection =
            Selection(width, height, ByteArray(width * height))

        fun all(width: Int, height: Int): Selection =
            Selection(width, height, ByteArray(width * height) { 255.toByte() })

        /** Construye una seleccion a partir de una mascara ya calculada. */
        fun of(width: Int, height: Int, mask: ByteArray): Selection {
            require(mask.size == width * height) { "Mascara de tamano incorrecto" }
            return Selection(width, height, mask)
        }

        /** Lee la cobertura del canal alfa de un bitmap ARGB. */
        fun fromAlphaArgb(bmp: Bitmap): Selection {
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val mask = ByteArray(w * h)
            for (i in px.indices) mask[i] = (px[i] ushr 24 and 0xFF).toByte()
            return Selection(w, h, mask)
        }
    }
}
