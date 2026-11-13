package com.dskmusic.pixa.core.project

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.PixaDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Ficha de un proyecto guardado. */
@Serializable
data class ProjectEntry(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val width: Int,
    val height: Int,
    /** Instante en que se mando a la papelera, o null si esta activo. */
    val deletedAt: Long? = null,
    /** Proyecto del que salio esta version, para agrupar "v2", "v3"... */
    val originId: String? = null,
)

@Serializable
private data class ProjectIndex(val entries: List<ProjectEntry> = emptyList())

/**
 * Almacen de proyectos (requisitos 2, 3 y 5).
 *
 * Los proyectos viven en el almacenamiento privado de la app, no en la galeria: eso es lo
 * que mantiene separados "guardar proyecto" y "exportar imagen", que es el punto del
 * requisito 3. La papelera no mueve archivos, solo marca una fecha en el indice, asi que
 * restaurar es inmediato y no puede fallar a medias.
 */
class ProjectStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "projects").apply { mkdirs() }
    private val indexFile: File get() = File(dir, "index.json")
    private val autosaveFile: File get() = File(context.filesDir, "autosave/current.pixa")

    private val _projects = MutableStateFlow<List<ProjectEntry>>(emptyList())
    val projects: StateFlow<List<ProjectEntry>> = _projects.asStateFlow()

    fun projectFile(id: String): File = File(dir, "$id.$EXT")

    /** Miniatura suelta junto al proyecto: listar la pantalla de inicio no abre los zips. */
    fun thumbFile(id: String): File = File(dir, "$id.png")

    // ----------------------------------------------------------------------------------
    // Indice
    // ----------------------------------------------------------------------------------

    suspend fun refresh(): List<ProjectEntry> = withContext(Dispatchers.IO) {
        val index = readIndex()
        // Se descartan las fichas cuyo archivo ya no existe (borrado externo, limpieza de
        // datos a medias) para que la pantalla de inicio nunca muestre un proyecto fantasma.
        val valid = index.entries.filter { projectFile(it.id).exists() }
        if (valid.size != index.entries.size) writeIndex(ProjectIndex(valid))
        _projects.value = valid
        valid
    }

    private fun readIndex(): ProjectIndex {
        if (!indexFile.exists()) return ProjectIndex()
        return runCatching {
            PixaFile.json.decodeFromString(ProjectIndex.serializer(), indexFile.readText())
        }.getOrElse { ProjectIndex() }
    }

    private fun writeIndex(index: ProjectIndex) {
        runCatching {
            indexFile.writeText(PixaFile.json.encodeToString(ProjectIndex.serializer(), index))
        }
    }

    private fun updateIndex(block: (List<ProjectEntry>) -> List<ProjectEntry>) {
        val next = block(readIndex().entries)
        writeIndex(ProjectIndex(next))
        _projects.value = next
    }

    val active: List<ProjectEntry> get() = _projects.value.filter { it.deletedAt == null }
    val trashed: List<ProjectEntry> get() = _projects.value.filter { it.deletedAt != null }

    fun entry(id: String): ProjectEntry? = _projects.value.firstOrNull { it.id == id }

    // ----------------------------------------------------------------------------------
    // Guardar y abrir
    // ----------------------------------------------------------------------------------

    /** Guarda (o sobrescribe) un proyecto y devuelve su ficha. */
    suspend fun save(
        id: String?,
        name: String,
        document: PixaDocument,
        store: BitmapStore,
        preview: Bitmap?,
        originId: String? = null,
    ): ProjectEntry = withContext(Dispatchers.IO) {
        val projectId = id ?: UUID.randomUUID().toString()
        PixaFile.save(projectFile(projectId), document, store, preview)
        preview?.let { bmp ->
            runCatching {
                val thumb = BitmapUtils.thumbnail(bmp, 512)
                thumbFile(projectId).outputStream().use {
                    thumb.compress(Bitmap.CompressFormat.PNG, 90, it)
                }
                if (thumb !== bmp) thumb.recycle()
            }
        }
        val entry = ProjectEntry(
            id = projectId,
            name = name.trim().ifEmpty { "Pixa" },
            updatedAt = System.currentTimeMillis(),
            width = document.width,
            height = document.height,
            originId = originId ?: entry(projectId)?.originId,
        )
        updateIndex { list -> list.filterNot { it.id == projectId } + entry }
        entry
    }

    suspend fun open(id: String, store: BitmapStore): PixaFile.Loaded = withContext(Dispatchers.IO) {
        PixaFile.load(projectFile(id), store)
    }

    /** Importar un .pixa recibido de otro telefono: se copia al almacen como proyecto nuevo. */
    suspend fun import(uri: Uri, store: BitmapStore, name: String): ProjectEntry =
        withContext(Dispatchers.IO) {
            val loaded = PixaFile.load(context, uri, store)
            save(null, name, loaded.document, store, loaded.thumbnail)
        }

    /** Copia el .pixa a la cache para compartirlo con otra app. */
    suspend fun exportFile(id: String): File = withContext(Dispatchers.IO) {
        val source = projectFile(id)
        if (!source.exists()) throw PixaFile.FormatException("El proyecto ya no existe")
        val safe = (entry(id)?.name ?: "pixa").replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim()
        val target = File(context.cacheDir, "shared").apply { mkdirs() }
            .resolve("${safe.ifEmpty { "pixa" }}.$EXT")
        source.copyTo(target, overwrite = true)
        target
    }

    // ----------------------------------------------------------------------------------
    // Renombrar, duplicar, versionar
    // ----------------------------------------------------------------------------------

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        updateIndex { list ->
            list.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
        }
    }

    suspend fun duplicate(id: String): ProjectEntry? = withContext(Dispatchers.IO) {
        val source = entry(id) ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        if (!projectFile(id).exists()) return@withContext null
        projectFile(id).copyTo(projectFile(newId), overwrite = true)
        if (thumbFile(id).exists()) thumbFile(id).copyTo(thumbFile(newId), overwrite = true)
        val copy = source.copy(
            id = newId,
            name = "${source.name} (2)",
            updatedAt = System.currentTimeMillis(),
            deletedAt = null,
        )
        updateIndex { list -> list + copy }
        copy
    }

    /**
     * "Guardar como version" (requisito 2): deja intacto el proyecto actual y crea otro
     * llamado "nombre v2", "nombre v3"... contando las versiones que ya existen.
     */
    suspend fun saveAsVersion(
        id: String,
        document: PixaDocument,
        store: BitmapStore,
        preview: Bitmap?,
    ): ProjectEntry? = withContext(Dispatchers.IO) {
        val source = entry(id) ?: return@withContext null
        val origin = source.originId ?: source.id
        val family = _projects.value.count { it.id == origin || it.originId == origin }
        val baseName = source.name.replace(Regex("\\s+v\\d+$"), "")
        save(
            id = null,
            name = "$baseName v${family + 1}",
            document = document,
            store = store,
            preview = preview,
            originId = origin,
        )
    }

    // ----------------------------------------------------------------------------------
    // Papelera (requisito 5: 30 dias)
    // ----------------------------------------------------------------------------------

    suspend fun moveToTrash(id: String) = withContext(Dispatchers.IO) {
        updateIndex { list ->
            list.map { if (it.id == id) it.copy(deletedAt = System.currentTimeMillis()) else it }
        }
    }

    suspend fun restore(id: String) = withContext(Dispatchers.IO) {
        updateIndex { list -> list.map { if (it.id == id) it.copy(deletedAt = null) else it } }
    }

    suspend fun deleteForever(id: String) = withContext(Dispatchers.IO) {
        projectFile(id).delete()
        thumbFile(id).delete()
        updateIndex { list -> list.filterNot { it.id == id } }
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val toDelete = readIndex().entries.filter { it.deletedAt != null }
        toDelete.forEach {
            projectFile(it.id).delete()
            thumbFile(it.id).delete()
        }
        updateIndex { list -> list.filter { it.deletedAt == null } }
    }

    /** Lo llama el trabajo periodico de WorkManager. Devuelve cuantos ha borrado. */
    suspend fun purgeExpiredTrash(retentionDays: Int = TRASH_DAYS): Int =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            val expired = readIndex().entries.filter { it.deletedAt != null && it.deletedAt < cutoff }
            expired.forEach {
                projectFile(it.id).delete()
                thumbFile(it.id).delete()
            }
            if (expired.isNotEmpty()) {
                val ids = expired.map { it.id }.toSet()
                updateIndex { list -> list.filterNot { it.id in ids } }
            }
            expired.size
        }

    /** Dias que le quedan en la papelera, para mostrarlo en la lista. */
    fun daysLeft(entry: ProjectEntry, retentionDays: Int = TRASH_DAYS): Int {
        val deleted = entry.deletedAt ?: return retentionDays
        val elapsed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - deleted).toInt()
        return (retentionDays - elapsed).coerceAtLeast(0)
    }

    // ----------------------------------------------------------------------------------
    // Autoguardado y recuperacion tras cierre inesperado (requisito 5)
    // ----------------------------------------------------------------------------------

    suspend fun autosave(document: PixaDocument, store: BitmapStore) = withContext(Dispatchers.IO) {
        runCatching { PixaFile.save(autosaveFile, document, store, null) }
        Unit
    }

    fun hasAutosave(): Boolean = autosaveFile.exists()

    suspend fun loadAutosave(store: BitmapStore): PixaDocument? = withContext(Dispatchers.IO) {
        if (!autosaveFile.exists()) return@withContext null
        runCatching { PixaFile.load(autosaveFile, store).document }.getOrNull()
    }

    /** Se llama al cerrar el editor con el trabajo ya guardado o descartado a proposito. */
    fun clearAutosave() {
        runCatching { autosaveFile.delete() }
    }

    companion object {
        const val EXT = PixaFile.EXTENSION
        const val TRASH_DAYS = 30
    }
}
