package com.dskmusic.pixa.core.stock

import com.dskmusic.pixa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class KlipyFile(val url: String = "", val width: Int = 0, val height: Int = 0)

@Serializable
data class KlipyFormats(
    val gif: KlipyFile? = null,
    val webp: KlipyFile? = null,
    val png: KlipyFile? = null,
)

@Serializable
data class KlipySizes(
    val hd: KlipyFormats? = null,
    val md: KlipyFormats? = null,
    val sm: KlipyFormats? = null,
    val xs: KlipyFormats? = null,
)

/** Un sticker de Klipy. Solo se usan sus versiones estaticas (PNG). */
@Serializable
data class KlipySticker(
    val id: Long = 0L,
    val title: String = "",
    val file: KlipySizes? = null,
) {
    /** Miniatura para la rejilla. */
    val previewUrl: String
        get() = file?.sm?.png?.url ?: file?.md?.png?.url ?: file?.xs?.png?.url
            ?: file?.md?.webp?.url ?: file?.sm?.gif?.url.orEmpty()

    /** Imagen a insertar: la mayor version estatica disponible, con alfa. */
    val fullUrl: String
        get() = file?.hd?.png?.url ?: file?.md?.png?.url ?: file?.hd?.webp?.url
            ?: file?.md?.webp?.url ?: file?.hd?.gif?.url ?: previewUrl
}

@Serializable
private class KlipyPage(val has_next: Boolean = false, val data: List<KlipySticker> = emptyList())

@Serializable
private class KlipyResponse(val data: KlipyPage? = null)

/**
 * Cliente minimo de Klipy (stickers). Solo la categoria "stickers": se insertan como
 * imagen estatica (PNG con transparencia), asi que son una capa normal del editor.
 *
 * ponytail: mismo patron que PixabayApi (HttpURLConnection + kotlinx.serialization).
 */
object KlipyApi {

    private const val BASE = "https://api.klipy.com/api/v1"
    private const val CUSTOMER = "pixa-by-dsk"
    private val json = Json { ignoreUnknownKeys = true }

    val hasKey: Boolean get() = BuildConfig.KLIPY_API_KEY.isNotBlank()

    class KlipyException(message: String) : Exception(message)

    /** Resultado de una pagina: los stickers y si hay mas. */
    class Page(val items: List<KlipySticker>, val hasNext: Boolean)

    /** Con [query] en blanco devuelve los stickers del momento. */
    suspend fun stickers(query: String, page: Int, perPage: Int = 24): Page =
        withContext(Dispatchers.IO) {
            if (!hasKey) throw KlipyException("no_key")
            val key = BuildConfig.KLIPY_API_KEY
            val common = "page=$page&per_page=$perPage&customer_id=$CUSTOMER"
            val url = if (query.isBlank()) {
                URL("$BASE/$key/stickers/trending?$common")
            } else {
                URL("$BASE/$key/stickers/search?q=${URLEncoder.encode(query, "UTF-8")}&$common")
            }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw KlipyException("http_${connection.responseCode}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val data = json.decodeFromString<KlipyResponse>(body).data
                Page(data?.data.orEmpty(), data?.has_next ?: false)
            } finally {
                connection.disconnect()
            }
        }
}
