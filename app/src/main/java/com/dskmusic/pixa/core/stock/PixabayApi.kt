package com.dskmusic.pixa.core.stock

import com.dskmusic.pixa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Tipo de resultado que ofrece Pixabay, igual que sus propios filtros por categoria. */
enum class PixabayType(val apiValue: String) {
    ALL("all"),
    PHOTO("photo"),
    ILLUSTRATION("illustration"),
    VECTOR("vector"),
}

@Serializable
data class PixabayHit(
    val id: Long = 0,
    val tags: String = "",
    val previewURL: String = "",
    val webformatURL: String = "",
    val webformatWidth: Int = 0,
    val webformatHeight: Int = 0,
    val largeImageURL: String = "",
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val type: String = "",
    val user: String = "",
)

@Serializable
private data class PixabayResponse(
    val total: Int = 0,
    val totalHits: Int = 0,
    val hits: List<PixabayHit> = emptyList(),
)

/**
 * Cliente minimo de la API de Pixabay (requisito del usuario: fotos, ilustraciones y
 * vectores insertables desde la app).
 *
 * ponytail: `HttpURLConnection` + kotlinx.serialization en vez de anadir Retrofit/OkHttp
 * como dependencia nueva, solo para una llamada GET con un unico endpoint.
 */
object PixabayApi {

    private const val BASE = "https://pixabay.com/api/"
    private val json = Json { ignoreUnknownKeys = true }

    val hasKey: Boolean get() = BuildConfig.PIXABAY_API_KEY.isNotBlank()

    class PixabayException(message: String) : Exception(message)

    /**
     * Busca imagenes. Con [query] en blanco, Pixabay devuelve su propio orden por
     * popularidad, que es lo mas parecido a "destacadas" que ofrece la API publica.
     */
    suspend fun search(
        query: String,
        type: PixabayType,
        page: Int,
        perPage: Int = 24,
    ): List<PixabayHit> = withContext(Dispatchers.IO) {
        if (!hasKey) throw PixabayException("no_key")
        val qParam = if (query.isBlank()) "" else "&q=${URLEncoder.encode(query, "UTF-8")}"
        val typeParam = if (type == PixabayType.ALL) "" else "&image_type=${type.apiValue}"
        val url = URL(
            "$BASE?key=${BuildConfig.PIXABAY_API_KEY}$qParam$typeParam" +
                "&page=$page&per_page=$perPage&safesearch=true",
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw PixabayException("http_${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<PixabayResponse>(body).hits
        } finally {
            connection.disconnect()
        }
    }
}
