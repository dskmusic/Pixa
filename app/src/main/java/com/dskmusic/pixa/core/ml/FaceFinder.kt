package com.dskmusic.pixa.core.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.Face as MlFace
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Deteccion de caras para la censura (requisito 14).
 *
 * El modelo de caras va empaquetado con la libreria, asi que no hace falta descargar nada
 * ni comprobar disponibilidad: si falla, se devuelve lista vacia y el usuario marca las
 * zonas a mano.
 */
object FaceFinder {

    /** Una cara encontrada. [area] esta en pixeles de la imagen original. */
    data class Face(val index: Int, val area: Rect)

    /**
     * Busca caras en [bitmap].
     *
     * Se trabaja sobre una copia reducida cuando la imagen es grande: el detector no gana
     * precision con mas de ~1200 px de lado y si ahorra bastante memoria y tiempo. Las
     * coordenadas se devuelven ya reescaladas a la imagen original.
     */
    suspend fun detect(bitmap: Bitmap, marginPercent: Float = 8f): List<Face> {
        val longSide = maxOf(bitmap.width, bitmap.height)
        val scale = if (longSide > MAX_SIDE) MAX_SIDE.toFloat() / longSide else 1f
        val input = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }

        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.05f)
                .build(),
        )
        val faces: List<MlFace> = try {
            suspendCancellableCoroutine<List<MlFace>> { continuation ->
                detector.process(InputImage.fromBitmap(input, 0))
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            detector.close()
            if (input !== bitmap) input.recycle()
        }

        val inverse = if (scale < 1f) 1f / scale else 1f
        val margin = (marginPercent / 100f).coerceIn(0f, 0.5f)
        return faces.mapIndexedNotNull { index, face ->
            val box = face.boundingBox
            val padX = (box.width() * margin).toInt()
            val padY = (box.height() * margin).toInt()
            val area = Rect(
                ((box.left - padX) * inverse).toInt(),
                ((box.top - padY) * inverse).toInt(),
                ((box.right + padX) * inverse).toInt(),
                ((box.bottom + padY) * inverse).toInt(),
            )
            if (!area.intersect(0, 0, bitmap.width, bitmap.height)) return@mapIndexedNotNull null
            if (area.width() < 4 || area.height() < 4) return@mapIndexedNotNull null
            Face(index, area)
        }
    }

    private const val MAX_SIDE = 1200
}
