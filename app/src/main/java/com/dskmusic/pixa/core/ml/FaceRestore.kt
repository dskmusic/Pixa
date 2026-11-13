package com.dskmusic.pixa.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.ml.FaceRestorer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Mejora de caras: ML Kit encuentra cada cara y sus puntos (ojos y comisuras de la boca),
 * se alinea a la plantilla de 512 px que espera GFPGAN, el modelo la restaura y el
 * resultado se pega de vuelta con un borde difuminado. Solo se toca la zona de la cara.
 */
object FaceRestore {

    class Result(val bitmap: Bitmap?, val faces: Int)

    // Plantilla FFHQ de 512 px: ojo izquierdo, ojo derecho, comisura izquierda y derecha
    // (izquierda y derecha tal como se ven en la imagen).
    private val TEMPLATE = floatArrayOf(
        192.98f, 239.95f,
        318.90f, 240.20f,
        201.26f, 371.40f,
        313.09f, 371.15f,
    )

    private const val DETECT_SIDE = 1600

    /** Puntos [ojoI, ojoD, bocaI, bocaD] (8 valores) de cada cara, en pixeles de [bitmap]. */
    private suspend fun detect(bitmap: Bitmap): List<FloatArray> {
        val long = max(bitmap.width, bitmap.height)
        val scale = if (long > DETECT_SIDE) DETECT_SIDE.toFloat() / long else 1f
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
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.04f)
                .build(),
        )
        val faces = try {
            suspendCancellableCoroutine<List<com.google.mlkit.vision.face.Face>> { c ->
                detector.process(InputImage.fromBitmap(input, 0))
                    .addOnSuccessListener { if (c.isActive) c.resume(it) }
                    .addOnFailureListener { if (c.isActive) c.resume(emptyList()) }
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            detector.close()
            if (input !== bitmap) input.recycle()
        }
        val inv = 1f / scale
        return faces.mapNotNull { face ->
            val le = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
            val re = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
            val ml = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
            val mr = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
            if (le == null || re == null || ml == null || mr == null) return@mapNotNull null
            // Se ordena por x: ML Kit nombra izquierda/derecha desde el punto de vista de
            // la cara y aqui hace falta como se ve en la imagen.
            val eyes = listOf(le, re).sortedBy { it.x }
            val mouth = listOf(ml, mr).sortedBy { it.x }
            floatArrayOf(
                eyes[0].x * inv, eyes[0].y * inv, eyes[1].x * inv, eyes[1].y * inv,
                mouth[0].x * inv, mouth[0].y * inv, mouth[1].x * inv, mouth[1].y * inv,
            )
        }
    }

    /** Semejanza (giro + escala + traslacion) por minimos cuadrados de [pts] sobre la plantilla. */
    private fun alignment(pts: FloatArray): Matrix {
        var smx = 0f; var smy = 0f; var dmx = 0f; var dmy = 0f
        for (i in 0 until 4) {
            smx += pts[i * 2] / 4f; smy += pts[i * 2 + 1] / 4f
            dmx += TEMPLATE[i * 2] / 4f; dmy += TEMPLATE[i * 2 + 1] / 4f
        }
        var num1 = 0f; var num2 = 0f; var den = 0f
        for (i in 0 until 4) {
            val sx = pts[i * 2] - smx
            val sy = pts[i * 2 + 1] - smy
            val dx = TEMPLATE[i * 2] - dmx
            val dy = TEMPLATE[i * 2 + 1] - dmy
            num1 += sx * dx + sy * dy
            num2 += sx * dy - sy * dx
            den += sx * sx + sy * sy
        }
        val c = num1 / den
        val s = num2 / den
        val tx = dmx - (c * smx - s * smy)
        val ty = dmy - (s * smx + c * smy)
        return Matrix().apply { setValues(floatArrayOf(c, -s, tx, s, c, ty, 0f, 0f, 1f)) }
    }

    /** Mascara 512x512 con borde difuminado: opaca en el centro de la cara, transparente fuera. */
    private fun featherMask(): Bitmap {
        val size = FaceRestorer.SIZE
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            maskFilter = BlurMaskFilter(34f, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(mask).drawRoundRect(RectF(64f, 56f, 448f, 456f), 150f, 150f, paint)
        return mask
    }

    /**
     * Restaura todas las caras de [src]. Devuelve la imagen nueva y el numero de caras
     * procesadas; con 0 caras el bitmap es null. [onProgress] avanza cara a cara.
     */
    suspend fun restoreAll(context: Context, src: Bitmap, onProgress: (Float) -> Unit): Result? {
        onProgress(0.02f)
        val faces = detect(src)
        if (faces.isEmpty()) return Result(null, 0)
        val out = BitmapUtils.mutableCopy(src)
        val canvas = Canvas(out)
        val mask = featherMask()
        val dstIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        var restoredCount = 0
        for ((index, pts) in faces.withIndex()) {
            currentCoroutineContext().ensureActive()
            val m = alignment(pts)
            val inverse = Matrix()
            if (!m.invert(inverse)) continue
            val crop = Bitmap.createBitmap(FaceRestorer.SIZE, FaceRestorer.SIZE, Bitmap.Config.ARGB_8888)
            Canvas(crop).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
            val restored = FaceRestorer.restore(context, crop) ?: return null
            crop.recycle()
            Canvas(restored).drawBitmap(mask, 0f, 0f, dstIn)
            canvas.drawBitmap(restored, inverse, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            restored.recycle()
            restoredCount++
            onProgress(0.05f + 0.95f * (index + 1) / faces.size)
        }
        mask.recycle()
        return Result(out, restoredCount)
    }
}
