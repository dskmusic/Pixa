package com.dskmusic.pixa.core.text

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.model.FontRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Catalogo de fuentes y descarga bajo demanda (requisito 10).
 *
 * Las fuentes de Google llegan por Downloadable Fonts, no empaquetadas: el proyecto no
 * contiene ningun .ttf, se piden al proveedor de Google Play Services la primera vez y
 * quedan en su cache del sistema. El usuario puede anadir las suyas importando un archivo.
 */
class FontRepository(private val context: Context) {

    /** Una familia del catalogo. [query] es el nombre que entiende el proveedor. */
    data class Family(val query: String, val label: String, val condensed: Boolean = false)

    private val cache = ConcurrentHashMap<String, Typeface>()
    private val failed = ConcurrentHashMap<String, Boolean>()
    private val inFlight = ConcurrentHashMap<String, Boolean>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _loaded = MutableStateFlow(0)

    /**
     * Contador que sube cada vez que una fuente entra en cache.
     *
     * Quien dibuje texto con [cached] lo observa para repintar: la descarga tarda y, sin
     * este aviso, el lienzo se quedaria con la fuente de reserva hasta el siguiente
     * cambio del documento.
     */
    val loaded: StateFlow<Int> = _loaded.asStateFlow()

    private val thread by lazy { HandlerThread("pixa-fonts").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }

    // Los certificados del proveedor de fuentes de Google los declara la propia app en
    // res/values/font_certs.xml: ui-text-google-fonts no trae ningun recurso, y
    // FontRequest no admite peticiones sin certificados.
    private fun fontRequest(query: String) = FontRequest(
        PROVIDER_AUTHORITY,
        PROVIDER_PACKAGE,
        query,
        R.array.com_google_android_gms_fonts_certs,
    )

    // ----------------------------------------------------------------------------------
    // Typeface para el renderizador de Canvas
    // ----------------------------------------------------------------------------------

    /**
     * Typeface ya disponible, sin esperar. Devuelve null si todavia no esta descargada:
     * el renderizador dibuja entonces con la fuente del sistema y se repinta cuando
     * [load] la deja en cache, de modo que la descarga nunca bloquea el lienzo.
     */
    fun cached(ref: FontRef): Typeface? {
        return when (ref.source) {
            FontRef.Source.SYSTEM -> systemTypeface(ref.family)
            FontRef.Source.GOOGLE -> cache[ref.family] ?: run {
                request(ref.family)
                null
            }
            FontRef.Source.IMPORTED -> {
                val path = ref.filePath ?: return null
                cache[path]?.let { return it }
                val loaded = runCatching { Typeface.createFromFile(File(path)) }.getOrNull()
                loaded?.also { cache[path] = it }
            }
        }
    }

    /**
     * Pide la fuente en segundo plano para que [cached] se arregle sola.
     *
     * Hace falta porque una capa de texto puede llegar ya con su familia puesta (al
     * reabrir un proyecto, por ejemplo) sin que nadie haya llamado a [load].
     */
    private fun request(family: String) {
        if (failed[family] == true || cache.containsKey(family)) return
        if (inFlight.putIfAbsent(family, true) != null) return
        scope.launch {
            load(FontRef(family = family, source = FontRef.Source.GOOGLE))
            inFlight.remove(family)
        }
    }

    /** Descarga (si hace falta) y devuelve la fuente. */
    suspend fun load(ref: FontRef): Typeface? {
        if (ref.source != FontRef.Source.GOOGLE) return cached(ref)
        cache[ref.family]?.let { return it }
        if (failed[ref.family] == true) return null
        val typeface = requestGoogleFont(ref.family)
        if (typeface == null) {
            // Si el proveedor no esta disponible, se marca para no reintentar en cada
            // repintado; la fuente del sistema hace de reserva.
            failed[ref.family] = true
        } else {
            cache[ref.family] = typeface
            // update y no "value += 1": varias descargas pueden terminar a la vez.
            _loaded.update { it + 1 }
        }
        return typeface
    }

    private suspend fun requestGoogleFont(family: String): Typeface? =
        suspendCancellableCoroutine { continuation ->
            val callback = object : FontsContractCompat.FontRequestCallback() {
                override fun onTypefaceRetrieved(typeface: Typeface) {
                    if (continuation.isActive) continuation.resume(typeface)
                }

                override fun onTypefaceRequestFailed(reason: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            FontsContractCompat.requestFont(context, fontRequest(family), callback, handler)
        }

    private fun systemTypeface(family: String): Typeface =
        cache.getOrPut("system:$family") {
            when (family.lowercase()) {
                "serif" -> Typeface.SERIF
                "monospace", "mono" -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
        }

    /** Precarga el catalogo para que la lista de fuentes se vea con su propia letra. */
    suspend fun preloadCatalog(limit: Int = CATALOG.size) {
        CATALOG.take(limit).forEach { family ->
            load(FontRef(family = family.query, source = FontRef.Source.GOOGLE))
        }
    }

    fun release() {
        scope.cancel()
        if (thread.isAlive) thread.quitSafely()
    }

    // ----------------------------------------------------------------------------------
    // FontFamily para Compose
    // ----------------------------------------------------------------------------------

    /** Familia para textos de Compose (la usa la vista previa de la lista de fuentes). */
    fun composeFamily(query: String): FontFamily = FontFamily(
        Font(
            googleFont = GoogleFont(query),
            fontProvider = COMPOSE_PROVIDER,
            weight = FontWeight.Normal,
        ),
        Font(
            googleFont = GoogleFont(query),
            fontProvider = COMPOSE_PROVIDER,
            weight = FontWeight.Bold,
        ),
    )

    companion object {
        private const val PROVIDER_AUTHORITY = "com.google.android.gms.fonts"
        private const val PROVIDER_PACKAGE = "com.google.android.gms"

        val COMPOSE_PROVIDER = GoogleFont.Provider(
            providerAuthority = PROVIDER_AUTHORITY,
            providerPackage = PROVIDER_PACKAGE,
            certificates = R.array.com_google_android_gms_fonts_certs,
        )

        /**
         * Catalogo del requisito 10: 21 familias variadas, con Anton como alternativa
         * libre a Impact para los memes (condensada, muy negra, todo mayusculas).
         */
        val CATALOG = listOf(
            Family("Roboto", "Roboto"),
            Family("Roboto Condensed", "Roboto Condensed", condensed = true),
            Family("Anton", "Anton", condensed = true),
            Family("Bebas Neue", "Bebas Neue", condensed = true),
            Family("Oswald", "Oswald", condensed = true),
            Family("Archivo Black", "Archivo Black"),
            Family("Montserrat", "Montserrat"),
            Family("Poppins", "Poppins"),
            Family("Inter", "Inter"),
            Family("Lato", "Lato"),
            Family("Open Sans", "Open Sans"),
            Family("Raleway", "Raleway"),
            Family("Nunito", "Nunito"),
            Family("Playfair Display", "Playfair Display"),
            Family("Merriweather", "Merriweather"),
            Family("Lobster", "Lobster"),
            Family("Pacifico", "Pacifico"),
            Family("Dancing Script", "Dancing Script"),
            Family("Caveat", "Caveat"),
            Family("Permanent Marker", "Permanent Marker"),
            Family("Bangers", "Bangers"),
        )

        /** Fuente de los memes: condensada y pesada, el papel que hacia Impact. */
        const val MEME_FAMILY = "Anton"
    }
}
