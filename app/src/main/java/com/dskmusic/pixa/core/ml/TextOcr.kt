package com.dskmusic.pixa.core.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reconocimiento de texto (alfabeto latino) con ML Kit. El modelo lo gestiona Google Play
 * Services, asi que no pesa en la app; la primera vez puede necesitar conexion.
 */
object TextOcr {

    /** Texto reconocido (vacio si no hay), o null si el reconocedor no esta disponible. */
    suspend fun recognize(bitmap: Bitmap): String? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine<String?> { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        } catch (e: Exception) {
            null
        } finally {
            recognizer.close()
        }
    }
}
