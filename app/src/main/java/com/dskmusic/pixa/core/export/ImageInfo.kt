package com.dskmusic.pixa.core.export

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface

/**
 * Datos de una imagen para el panel "Informacion de imagen" (requisito 5).
 *
 * Se lee con decodificacion solo de cabeceras, asi que abrir la informacion de una foto de
 * 50 megapixeles no carga ningun pixel en memoria.
 */
data class ImageInfo(
    val name: String?,
    val mime: String?,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateTime: String?,
    val camera: String?,
    val lens: String?,
    val hasGps: Boolean,
) {
    val megapixels: Float get() = width * height / 1_000_000f
}

object ImageInfoReader {

    fun read(context: Context, uri: Uri): ImageInfo {
        var name: String? = null
        var size = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }

        var dateTime: String? = null
        var camera: String? = null
        var lens: String? = null
        var hasGps = false
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                camera = listOfNotNull(make, model).joinToString(" ").ifBlank { null }
                val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)
                val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                lens = listOfNotNull(
                    focal?.let { "$it mm" },
                    aperture?.let { "f/$it" },
                ).joinToString(" · ").ifBlank { null }
                hasGps = exif.latLong != null
            }
        }

        return ImageInfo(
            name = name,
            mime = bounds.outMimeType ?: context.contentResolver.getType(uri),
            width = bounds.outWidth.coerceAtLeast(0),
            height = bounds.outHeight.coerceAtLeast(0),
            sizeBytes = size,
            dateTime = dateTime,
            camera = camera,
            lens = lens,
            hasGps = hasGps,
        )
    }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "—"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024f)
        else -> String.format("%.1f MB", bytes / (1024f * 1024f))
    }
}
