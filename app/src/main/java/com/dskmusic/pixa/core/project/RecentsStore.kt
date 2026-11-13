package com.dskmusic.pixa.core.project

import android.content.Context
import android.graphics.Bitmap
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/** Imagen abierta o exportada recientemente (requisito 2). */
@Serializable
data class RecentItem(
    val id: String,
    val label: String,
    val openedAt: Long,
    /**
     * Uri de origen, si la imagen venia de la galeria. Solo sirve de respaldo para
     * reabrir: el selector de fotos moderno (Photo Picker) solo concede permiso de
     * lectura mientras dura el proceso de la app, asi que esta uri puede dejar de
     * funcionar en cuanto la app se reinicia. Por eso [hasLocalCopy] es el camino
     * principal.
     */
    val sourceUri: String? = null,
    /** Id del proyecto asociado, si la edicion acabo guardada como proyecto. */
    val projectId: String? = null,
    /** Si hay una copia propia editable guardada ([RecentsStore.sourceFile]), que no
     * depende de ningun permiso externo y siempre se puede volver a abrir. */
    val hasLocalCopy: Boolean = false,
)

@Serializable
private data class RecentsIndex(val items: List<RecentItem> = emptyList())

/**
 * Lista de recientes de la pantalla de inicio.
 *
 * Guarda su propia miniatura en lugar de confiar en la uri de origen, porque una uri de
 * la galeria puede dejar de tener permiso o desaparecer y la lista se quedaria con huecos.
 */
class RecentsStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "recents").apply { mkdirs() }
    private val indexFile: File get() = File(dir, "index.json")

    private val _items = MutableStateFlow<List<RecentItem>>(emptyList())
    val items: StateFlow<List<RecentItem>> = _items.asStateFlow()

    fun thumbFile(id: String): File = File(dir, "$id.png")

    suspend fun refresh(): List<RecentItem> = withContext(Dispatchers.IO) {
        val list = read().items
            .filter { thumbFile(it.id).exists() }
            .sortedByDescending { it.openedAt }
        _items.value = list
        list
    }

    /** Copia local editable, para poder reabrir sin depender de ningun permiso externo. */
    fun sourceFile(id: String): File = File(dir, "$id.src.jpg")

    suspend fun add(
        label: String,
        full: Bitmap,
        sourceUri: String? = null,
        projectId: String? = null,
    ): RecentItem = withContext(Dispatchers.IO) {
        val previous = read().items
        // Bug encontrado (requisito del usuario): reabrir una imagen desde Recientes la
        // iba duplicando cada vez. Si ya hay un reciente del mismo origen, se quita antes
        // de anadir el nuevo, asi que reabrir sube el existente arriba en vez de clonarlo.
        val stale = previous.filter { old ->
            (sourceUri != null && old.sourceUri == sourceUri) ||
                (projectId != null && old.projectId == projectId)
        }
        stale.forEach { thumbFile(it.id).delete(); sourceFile(it.id).delete() }
        val remaining = previous - stale.toSet()

        val id = UUID.randomUUID().toString()
        val thumb = BitmapUtils.thumbnail(full, 512)
        runCatching {
            thumbFile(id).outputStream().use { thumb.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        if (thumb !== full) thumb.recycle()
        // Bug encontrado (requisito del usuario): "abrir desde recientes" fallaba porque
        // dependia de la uri original del selector de fotos, que deja de tener permiso de
        // lectura en cuanto la app se reinicia (limitacion del Photo Picker del sistema,
        // no algo que se pueda arreglar pidiendo el permiso de otra forma). Con una copia
        // propia, reabrir siempre funciona.
        val hasLocalCopy = runCatching {
            sourceFile(id).outputStream().use { full.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        }.getOrDefault(false)

        val item = RecentItem(id, label, System.currentTimeMillis(), sourceUri, projectId, hasLocalCopy)
        val merged = (listOf(item) + remaining).take(MAX_ITEMS)
        // Las miniaturas que se caen del limite se borran aqui: nadie mas las mira.
        remaining.filter { old -> merged.none { it.id == old.id } }
            .forEach { thumbFile(it.id).delete(); sourceFile(it.id).delete() }
        write(RecentsIndex(merged))
        _items.value = merged
        item
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        thumbFile(id).delete()
        sourceFile(id).delete()
        val next = read().items.filterNot { it.id == id }
        write(RecentsIndex(next))
        _items.value = next
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        read().items.forEach { thumbFile(it.id).delete(); sourceFile(it.id).delete() }
        write(RecentsIndex(emptyList()))
        _items.value = emptyList()
    }

    private fun read(): RecentsIndex {
        if (!indexFile.exists()) return RecentsIndex()
        return runCatching {
            PixaFile.json.decodeFromString(RecentsIndex.serializer(), indexFile.readText())
        }.getOrElse { RecentsIndex() }
    }

    private fun write(index: RecentsIndex) {
        runCatching {
            indexFile.writeText(PixaFile.json.encodeToString(RecentsIndex.serializer(), index))
        }
    }

    companion object {
        const val MAX_ITEMS = 24
    }
}
