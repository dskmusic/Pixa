package com.dskmusic.pixa.core.text

import com.dskmusic.pixa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GoogleFontFamily(val family: String = "", val category: String = "")

@Serializable
private data class GoogleFontsResponse(val items: List<GoogleFontFamily> = emptyList())

/**
 * Catalogo completo de familias de Google Fonts (requisito del usuario: explorar las
 * +1500 familias, no solo las 21 fijas de [FontRepository.CATALOG]).
 *
 * Solo trae la lista de nombres: el tipo de letra en si lo sigue sirviendo Downloadable
 * Fonts via Play Services ([FontRepository]), que acepta cualquier nombre de familia, asi
 * que esta API no descarga ningun .ttf, solo dice que familias existen.
 */
object GoogleFontsApi {

    private const val BASE = "https://www.googleapis.com/webfonts/v1/webfonts"
    private val json = Json { ignoreUnknownKeys = true }

    val hasKey: Boolean get() = BuildConfig.GOOGLE_FONTS_API_KEY.isNotBlank()

    class GoogleFontsException(message: String) : Exception(message)

    // ponytail: cache en memoria de processo, no en disco; una sola peticion por sesion.
    private var cache: List<GoogleFontFamily>? = null

    suspend fun listFamilies(): List<GoogleFontFamily> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }
        if (!hasKey) throw GoogleFontsException("no_key")
        val url = URL("$BASE?key=${BuildConfig.GOOGLE_FONTS_API_KEY}&sort=popularity")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw GoogleFontsException("http_${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val items = json.decodeFromString<GoogleFontsResponse>(body).items
            cache = items
            items
        } finally {
            connection.disconnect()
        }
    }
}
