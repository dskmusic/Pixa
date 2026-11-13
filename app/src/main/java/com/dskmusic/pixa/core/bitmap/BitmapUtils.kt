package com.dskmusic.pixa.core.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Utilidades de carga y manipulacion de bitmaps con cuidado de memoria (requisito 6):
 * se consulta primero el tamano real, se calcula inSampleSize y solo despues se decodifica.
 */
object BitmapUtils {

    /** Configuracion unica para todo el motor: alfa real y 8 bits por canal. */
    val CONFIG: Bitmap.Config = Bitmap.Config.ARGB_8888

    class LoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Decodifica una imagen limitando su lado mayor a [maxDim], aplicando la rotacion
     * EXIF. Devuelve un bitmap mutable listo para usarse como capa.
     */
    fun decodeUri(context: Context, uri: Uri, maxDim: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw LoadException("No se ha podido leer el tamano de la imagen")
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            inPreferredConfig = CONFIG
            inMutable = true
        }
        val decoded = openStream(context, uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw LoadException("No se ha podido decodificar la imagen")

        val rotated = applyExifOrientation(context, uri, decoded)
        return clampToMax(rotated, maxDim)
    }

    fun decodeFile(file: File, maxDim: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) throw LoadException("Archivo de imagen no valido")
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            inPreferredConfig = CONFIG
            inMutable = true
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw LoadException("No se ha podido decodificar el archivo")
        return clampToMax(bmp, maxDim)
    }

    fun decodeBytes(bytes: ByteArray, maxDim: Int = Int.MAX_VALUE): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            inPreferredConfig = CONFIG
            inMutable = true
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw LoadException("Datos de imagen no validos")
    }

    private fun openStream(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw LoadException("No se ha podido abrir $uri")

    /** Potencia de 2 mas grande que mantiene la imagen por debajo de [maxDim]. */
    fun computeSampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (maxDim <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (max(w, h) > maxDim) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            openStream(context, uri).use { ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            ) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    /** Reduce el bitmap si su lado mayor supera [maxDim]. */
    fun clampToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val k = maxDim.toFloat() / longest
        val w = max(1, (bitmap.width * k).roundToInt())
        val h = max(1, (bitmap.height * k).roundToInt())
        val out = scale(bitmap, w, h)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    /** Reescala con filtrado bilineal. */
    fun scale(src: Bitmap, width: Int, height: Int): Bitmap {
        if (src.width == width && src.height == height) return src
        val out = create(width, height)
        Canvas(out).drawBitmap(
            src,
            Rect(0, 0, src.width, src.height),
            Rect(0, 0, width, height),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }

    /** Reescala sin suavizado: lo usa el efecto de pixelado. */
    fun scaleNearest(src: Bitmap, width: Int, height: Int): Bitmap {
        val out = create(width, height)
        Canvas(out).drawBitmap(
            src,
            Rect(0, 0, src.width, src.height),
            Rect(0, 0, width, height),
            // Bug encontrado (requisito del usuario): un Paint() recien creado filtra el
            // bitmap por defecto desde Android 10 (API 29, nuestro minSdk), asi que esto
            // salia siempre suavizado en vez de con los pixeles cuadrados de verdad.
            Paint().apply { isFilterBitmap = false },
        )
        return out
    }

    /** Crea un bitmap transparente, convirtiendo OOM en un error manejable. */
    fun create(width: Int, height: Int): Bitmap {
        val w = max(1, width)
        val h = max(1, height)
        return try {
            Bitmap.createBitmap(w, h, CONFIG)
        } catch (e: OutOfMemoryError) {
            throw LoadException("Sin memoria para un bitmap de ${w}x$h", e)
        }
    }

    /** Copia mutable; usar antes de pintar sobre un bitmap del store. */
    fun mutableCopy(src: Bitmap): Bitmap {
        val out = create(src.width, src.height)
        Canvas(out).drawBitmap(src, 0f, 0f, null)
        return out
    }

    /** Miniatura con el lado mayor igual a [maxDim], para listas y previsualizaciones. */
    fun thumbnail(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return mutableCopy(src)
        val k = maxDim.toFloat() / longest
        return scale(src, max(1, (src.width * k).roundToInt()), max(1, (src.height * k).roundToInt()))
    }

    /** Recorta con limites saneados. */
    fun crop(src: Bitmap, rect: Rect): Bitmap {
        val r = Rect(rect)
        if (!r.intersect(0, 0, src.width, src.height) || r.isEmpty) {
            throw LoadException("Recorte fuera de la imagen")
        }
        val out = create(r.width(), r.height())
        Canvas(out).drawBitmap(src, -r.left.toFloat(), -r.top.toFloat(), null)
        return out
    }

    /**
     * Escala de previsualizacion: durante los gestos se compone a resolucion reducida
     * para mantener la fluidez y al soltar se hace el render completo (requisito 6).
     */
    fun previewScale(width: Int, height: Int, budgetPx: Int = 1_600_000): Float {
        val px = width.toLong() * height.toLong()
        if (px <= budgetPx) return 1f
        return min(1f, kotlin.math.sqrt(budgetPx.toDouble() / px.toDouble()).toFloat())
    }
}
