package com.dskmusic.pixa.ml

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Descarga bajo demanda de los modelos que antes iban empaquetados en el APK (requisito
 * del usuario: ahorrar los ~21 MB que ocupaban MediaPipe y el recorte general en el APK).
 *
 * Un unico archivo pedido con [ensure] sirve para los tres casos que hacen falta: el
 * intento silencioso al arrancar la app, el reintento en un arranque posterior si aquel
 * fallo (sin red, por ejemplo), y el intento en el momento en que la funcion se usa de
 * verdad si todavia no hay archivo local. Los tres pasan por el mismo codigo: solo
 * descarga si el archivo no esta ya, y no hace falta distinguir casos.
 */
object ModelDownloader {

    private val locks = mutableMapOf<String, Mutex>()

    private fun lockFor(fileName: String): Mutex = synchronized(locks) {
        locks.getOrPut(fileName) { Mutex() }
    }

    private fun dir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    /** Devuelve el archivo local si ya existe, sin descargar nada. */
    fun existing(context: Context, fileName: String): File? =
        File(dir(context), fileName).takeIf { it.exists() && it.length() > 0L }

    /**
     * Devuelve el archivo local, descargandolo primero si hace falta. Null si la
     * descarga falla (sin red, servidor caido...); el llamante decide como avisarlo o
     * simplemente lo vuelve a intentar la proxima vez que se llame.
     */
    suspend fun ensure(
        context: Context,
        url: String,
        fileName: String,
        onProgress: ((Float) -> Unit)? = null,
    ): File? =
        withContext(Dispatchers.IO) {
            val target = File(dir(context), fileName)
            // Un mutex por archivo: si el intento silencioso del arranque y un uso
            // directo coinciden, se descarga una sola vez, no dos en paralelo.
            lockFor(fileName).withLock {
                if (target.exists() && target.length() > 0L) return@withLock target
                val tmp = File(target.parentFile, "$fileName.part")
                // Las direcciones salen de assets/ai_models.json (ver AiUrls): se prueban en
                // orden y se pasa a la siguiente si falla la red, el servidor o el peso.
                val expected = AiUrls.expectedBytes(context, fileName)
                for (candidate in AiUrls.urlsFor(context, fileName, url)) {
                    if (tryDownload(candidate, tmp, expected, onProgress) && tmp.renameTo(target)) {
                        return@withLock target
                    }
                    tmp.delete()
                    // Si la causa fue una cancelacion, no se sigue probando otras direcciones.
                    currentCoroutineContext().ensureActive()
                }
                null
            }
        }

    /** Una descarga completa de [url] a [tmp]. false si falla o el peso no es el esperado. */
    private suspend fun tryDownload(
        url: String,
        tmp: File,
        expected: Long?,
        onProgress: ((Float) -> Unit)?,
    ): Boolean = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("http_${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    // Copia por bloques en vez de copyTo: asi se puede cancelar entre
                    // bloques y informar del progreso real.
                    val buffer = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress?.invoke((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        // Un espejo mal subido o una descarga cortada no deben quedar como modelo valido.
        check(expected == null || tmp.length() == expected) { "bad_size" }
    }.isSuccess
}
