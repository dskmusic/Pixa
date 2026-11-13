package com.dskmusic.pixa.ml

import android.content.Context
import org.json.JSONObject

/**
 * Direcciones de descarga de los modelos, leidas de `assets/ai_models.json` (en este
 * modulo: `ml/src/main/assets/ai_models.json`). Ese archivo es el unico sitio que hay que
 * tocar para cambiar un enlace o anadir un servidor de respaldo:
 *
 *  - `files.<archivo>.urls`: direcciones alternativas, en orden de preferencia.
 *  - `files.<archivo>.bytes`: peso exacto, para rechazar descargas cortadas o espejos mal
 *    subidos. Si falta o es 0 no se comprueba.
 *  - `mirror_base`: si no esta vacio, se anade `mirror_base/<archivo>` como una direccion
 *    mas para TODOS los modelos. Con `mirror_first` true se prueba antes que las demas;
 *    con false solo se usa si fallan las otras (respaldo).
 *
 * Si el archivo falta o esta roto, se usan las direcciones que trae el codigo.
 */
object AiUrls {

    private class Config(
        val mirrorBase: String,
        val mirrorFirst: Boolean,
        val urls: Map<String, List<String>>,
        val bytes: Map<String, Long>,
    )

    @Volatile
    private var cached: Config? = null

    private fun config(context: Context): Config {
        cached?.let { return it }
        val parsed = runCatching {
            val text = context.applicationContext.assets.open("ai_models.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val urls = mutableMapOf<String, List<String>>()
            val bytes = mutableMapOf<String, Long>()
            val files = root.optJSONObject("files")
            files?.keys()?.forEach { name ->
                val entry = files.getJSONObject(name)
                val list = entry.optJSONArray("urls")
                if (list != null) {
                    urls[name] = (0 until list.length()).map { list.getString(it) }.filter { it.isNotBlank() }
                }
                val b = entry.optLong("bytes", 0L)
                if (b > 0L) bytes[name] = b
            }
            Config(
                mirrorBase = root.optString("mirror_base", "").trim(),
                mirrorFirst = root.optBoolean("mirror_first", false),
                urls = urls,
                bytes = bytes,
            )
        }.getOrElse { Config("", false, emptyMap(), emptyMap()) }
        cached = parsed
        return parsed
    }

    /** Direcciones a probar para [fileName], en orden. [default] es la que trae el codigo. */
    fun urlsFor(context: Context, fileName: String, default: String): List<String> {
        val c = config(context)
        val own = c.urls[fileName].orEmpty().ifEmpty { listOf(default) }
        if (c.mirrorBase.isEmpty()) return own
        val mirror = c.mirrorBase.trimEnd('/') + "/" + fileName
        return if (c.mirrorFirst) listOf(mirror) + own else own + mirror
    }

    fun expectedBytes(context: Context, fileName: String): Long? = config(context).bytes[fileName]
}
