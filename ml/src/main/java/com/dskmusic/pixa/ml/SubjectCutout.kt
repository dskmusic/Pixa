package com.dskmusic.pixa.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Recorte automatico del sujeto con ML Kit Subject Segmentation (requisito 8).
 *
 * Vive en el modulo :ml para que el resto de la app no vea ni una clase de ML Kit: aqui
 * solo salen tipos propios. El modelo no viene en el APK, lo entrega Google Play Services
 * como modulo opcional, asi que puede faltar (dispositivo sin Play Services, sin red, o
 * espacio insuficiente). Todo eso se resuelve dentro: la app recibe [Availability] o un
 * resultado nulo y ofrece las alternativas manuales.
 */
object SubjectCutout {

    /** Estado del modelo en el dispositivo. */
    enum class Availability {
        /** Listo para usar. */
        READY,

        /** Se puede usar, pero hay que descargar el modelo antes. */
        NEEDS_DOWNLOAD,

        /** El dispositivo no lo soporta (sin Play Services, por ejemplo). */
        UNSUPPORTED,
    }

    /** Progreso de la descarga del modelo. */
    sealed interface Download {
        data class Running(val percent: Int) : Download
        data object Done : Download
        data class Failed(val reason: String?) : Download
    }

    /**
     * Mascara de confianza del sujeto: un valor 0..1 por pixel, en orden de filas.
     * El llamante la convierte en mascara alfa con el umbral que prefiera.
     */
    class Mask(val confidences: FloatArray, val width: Int, val height: Int)

    private fun segmenter() = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build(),
    )

    /** Comprueba si el modelo esta disponible, sin descargar nada. */
    suspend fun availability(context: Context): Availability {
        val client = segmenter()
        return try {
            val response = ModuleInstall.getClient(context)
                .areModulesAvailable(client)
                .await()
            if (response.areModulesAvailable()) Availability.READY else Availability.NEEDS_DOWNLOAD
        } catch (e: Exception) {
            // Sin Play Services la consulta falla en vez de responder "no disponible".
            Availability.UNSUPPORTED
        } finally {
            client.close()
        }
    }

    /**
     * Descarga el modelo informando del progreso.
     *
     * El porcentaje sale de los bytes que reporta Play Services; cuando no los da (los
     * primeros avisos llegan sin cifras) se mantiene el ultimo valor conocido para que la
     * barra no salte hacia atras.
     */
    suspend fun download(context: Context, onProgress: (Download) -> Unit): Boolean =
        suspendCancellableCoroutine { continuation ->
            val client = segmenter()
            var percent = 0
            var finished = false

            fun finish(ok: Boolean, reason: String? = null) {
                if (finished) return
                finished = true
                client.close()
                onProgress(if (ok) Download.Done else Download.Failed(reason))
                if (continuation.isActive) continuation.resume(ok)
            }

            val listener = object : InstallStatusListener {
                override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                    update.progressInfo?.let { info ->
                        val total = info.totalBytesToDownload
                        if (total > 0) {
                            percent = (info.bytesDownloaded * 100 / total)
                                .toInt().coerceIn(0, 100)
                        }
                    }
                    when (update.installState) {
                        ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED ->
                            finish(true)
                        ModuleInstallStatusUpdate.InstallState.STATE_FAILED ->
                            finish(false, "error ${update.errorCode}")
                        ModuleInstallStatusUpdate.InstallState.STATE_CANCELED ->
                            finish(false, null)
                        else -> onProgress(Download.Running(percent))
                    }
                }
            }

            val request = ModuleInstallRequest.newBuilder()
                .addApi(client)
                .setListener(listener)
                .build()

            val installClient = ModuleInstall.getClient(context)
            installClient.installModules(request)
                .addOnSuccessListener { response ->
                    // Si ya estaba instalado no llega ningun aviso al listener.
                    if (!response.areModulesAlreadyInstalled()) return@addOnSuccessListener
                    finish(true)
                }
                .addOnFailureListener { error -> finish(false, error.message) }

            continuation.invokeOnCancellation {
                runCatching { installClient.unregisterListener(listener) }
                if (!finished) {
                    finished = true
                    client.close()
                }
            }
        }

    /**
     * Calcula la mascara del sujeto. Devuelve null si el modelo no esta disponible o si no
     * encuentra ningun sujeto; en ese caso la app ofrece el recorte manual.
     */
    suspend fun segment(bitmap: Bitmap): Mask? {
        val client = segmenter()
        return try {
            val result = client.process(InputImage.fromBitmap(bitmap, 0)).await()
            val buffer = result.foregroundConfidenceMask ?: return null
            val values = FloatArray(bitmap.width * bitmap.height)
            buffer.rewind()
            val count = minOf(values.size, buffer.remaining())
            buffer.get(values, 0, count)
            Mask(values, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            null
        } finally {
            client.close()
        }
    }

    /**
     * Espera un Task de Play Services sin arrastrar kotlinx-coroutines-play-services solo
     * por esta funcion.
     */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("tarea cancelada"))
            }
        }
    }
}
