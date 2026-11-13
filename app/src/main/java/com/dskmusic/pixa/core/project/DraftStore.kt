package com.dskmusic.pixa.core.project

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Borradores de los generadores (collage / comic, memes, unir fotos, posts sociales).
 *
 * Cada generador guarda su estado como JSON mas las fotos que usa como PNG, en una carpeta
 * propia. Si la app se cierra sin terminar, Inicio ofrece recuperarlo y vuelve al
 * generador de origen, no al editor. Un borrador por generador ([kind]).
 */
class DraftStore(context: Context) {

    private val root = File(context.filesDir, "drafts")

    /** Generador que debe restaurar su borrador al abrirse (lo pide Inicio). */
    @Volatile
    private var restoreKind: String? = null

    private fun dir(kind: String) = File(root, kind)

    fun requestRestore(kind: String) {
        restoreKind = kind
    }

    /** true una sola vez si [kind] estaba pendiente de restaurar. */
    fun takeRestore(kind: String): Boolean {
        if (restoreKind != kind) return false
        restoreKind = null
        return true
    }

    fun has(kind: String): Boolean = File(dir(kind), STATE).exists()

    /** Generadores con borrador pendiente. */
    fun pending(): List<String> = KINDS.filter { has(it) }

    fun readState(kind: String): String? =
        runCatching { File(dir(kind), STATE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun readImage(kind: String, id: String): Bitmap? =
        runCatching {
            BitmapFactory.decodeFile(File(dir(kind), "$id.png").path)
                ?.let { if (it.isMutable) it else it.copy(Bitmap.Config.ARGB_8888, true) }
        }.getOrNull()

    /**
     * Guarda el estado y las imagenes [ids]. Las que ya estan en disco no se reescriben y
     * las que ya no se usan se borran. [bitmapOf] da el bitmap de cada id.
     */
    fun save(kind: String, json: String, ids: Collection<String>, bitmapOf: (String) -> Bitmap?) {
        runCatching {
            val d = dir(kind).apply { mkdirs() }
            for (id in ids) {
                val f = File(d, "$id.png")
                if (f.exists()) continue
                val bmp = bitmapOf(id) ?: continue
                if (bmp.isRecycled) continue
                val tmp = File(d, "$id.tmp")
                tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                tmp.renameTo(f)
            }
            val keep = ids.map { "$it.png" }.toSet() + STATE
            d.listFiles()?.forEach { if (it.name !in keep) it.delete() }
            // El estado se escribe el ultimo: si falla antes, el borrador anterior sigue valido.
            val tmp = File(d, "$STATE.tmp")
            tmp.writeText(json)
            tmp.renameTo(File(d, STATE))
        }
    }

    fun clear(kind: String) {
        runCatching { dir(kind).deleteRecursively() }
    }

    companion object {
        const val COLLAGE = "collage"
        const val MEME = "meme"
        const val STITCH = "stitch"
        const val SOCIAL = "social"
        val KINDS = listOf(COLLAGE, MEME, STITCH, SOCIAL)
        private const val STATE = "state.json"
    }
}
