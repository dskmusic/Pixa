package com.dskmusic.pixa.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Segundo metodo de recorte de fondo (requisito del usuario): MediaPipe Image Segmenter
 * con el modelo "selfie multiclass". El modelo (16 MB) ya no viaja en el APK: se descarga
 * bajo demanda (o en silencio al arrancar, ver [prefetch]) y se queda en el almacenamiento
 * propio de la app, asi que solo hace falta red la primera vez.
 *
 * El modelo distingue 6 clases por pixel (fondo, pelo, piel, cara, ropa, accesorios); solo
 * nos interesa la mascara de confianza de "fondo" (indice 0), invertida a confianza de
 * sujeto para encajar en el mismo [SubjectCutout.Mask] que ya consume la UI.
 */
object MediaPipeCutout {

    private const val MODEL_FILE = "selfie_multiclass_256x256.tflite"
    private const val MODEL_URL =
        "https://storage.googleapis.com/mediapipe-models/image_segmenter/" +
            "selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite"

    @Volatile
    private var segmenter: ImageSegmenter? = null
    private val buildLock = Mutex()

    /** Deja el modelo descargado sin construir nada todavia (requisito del usuario: en
     * silencio al abrir la app, para que el primer uso real no necesite red). */
    suspend fun prefetch(context: Context) {
        ModelDownloader.ensure(context, MODEL_URL, MODEL_FILE)
    }

    private suspend fun segmenterOrNull(context: Context): ImageSegmenter? {
        segmenter?.let { return it }
        return buildLock.withLock {
            segmenter?.let { return@withLock it }
            val file = ModelDownloader.ensure(context, MODEL_URL, MODEL_FILE)
                ?: return@withLock null
            runCatching {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetBuffer(ByteBuffer.wrap(file.readBytes()))
                    .build()
                val options = ImageSegmenter.ImageSegmenterOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputCategoryMask(false)
                    .setOutputConfidenceMasks(true)
                    .build()
                ImageSegmenter.createFromOptions(context.applicationContext, options)
            }.getOrNull()?.also { segmenter = it }
        }
    }

    /** Mascara del sujeto, o null si el modelo no se pudo descargar/cargar todavia. */
    suspend fun segment(context: Context, bitmap: Bitmap): SubjectCutout.Mask? =
        withContext(Dispatchers.Default) {
            val client = segmenterOrNull(context) ?: return@withContext null
            runCatching {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = client.segment(mpImage)
                val backgroundMask = result.confidenceMasks().get().getOrNull(0)
                    ?: return@runCatching null
                val buffer = ByteBufferExtractor.extract(backgroundMask).asFloatBuffer()
                val size = backgroundMask.width * backgroundMask.height
                val values = FloatArray(size)
                buffer.rewind()
                buffer.get(values, 0, minOf(size, buffer.remaining()))
                // Confianza de fondo -> confianza de sujeto, que es lo que espera Cutout.
                for (i in values.indices) values[i] = 1f - values[i]
                SubjectCutout.Mask(values, backgroundMask.width, backgroundMask.height)
            }.getOrNull()
        }
}
