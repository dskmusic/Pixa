package com.dskmusic.pixa.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dskmusic.pixa.core.project.ProjectEntry
import com.dskmusic.pixa.core.project.RecentItem
import com.dskmusic.pixa.pixa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Estado de la pantalla de inicio (requisito 2).
 *
 * Proyectos y papelera salen de la misma lista: la papelera no mueve archivos, solo marca
 * la fecha de borrado, asi que aqui basta separarla por [ProjectEntry.deletedAt].
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.pixa
    private val store = container.projects
    private val recentsStore = container.recents

    private val _projects = MutableStateFlow<List<ProjectEntry>>(emptyList())
    val projects: StateFlow<List<ProjectEntry>> = _projects.asStateFlow()

    private val _trash = MutableStateFlow<List<ProjectEntry>>(emptyList())
    val trash: StateFlow<List<ProjectEntry>> = _trash.asStateFlow()

    private val _recents = MutableStateFlow<List<RecentItem>>(emptyList())
    val recents: StateFlow<List<RecentItem>> = _recents.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val all = runCatching { store.refresh() }.getOrDefault(emptyList())
            _projects.value = all.filter { it.deletedAt == null }.sortedByDescending { it.updatedAt }
            _trash.value = all.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
            _recents.value = runCatching { recentsStore.refresh() }.getOrDefault(emptyList())
        }
    }

    fun projectThumb(id: String): File = store.thumbFile(id)

    fun recentThumb(id: String): File = recentsStore.thumbFile(id)
    fun recentSourceFile(id: String): File = recentsStore.sourceFile(id)

    fun daysLeft(entry: ProjectEntry): Int = store.daysLeft(entry)

    fun rename(id: String, name: String) = launchAndRefresh { store.rename(id, name) }

    fun duplicate(id: String) = launchAndRefresh { store.duplicate(id) }

    fun moveToTrash(id: String) = launchAndRefresh { store.moveToTrash(id) }

    fun restore(id: String) = launchAndRefresh { store.restore(id) }

    fun deleteForever(id: String) = launchAndRefresh { store.deleteForever(id) }

    fun emptyTrash() = launchAndRefresh { store.emptyTrash() }

    fun removeRecent(id: String) = launchAndRefresh { recentsStore.remove(id) }

    fun clearRecents() = launchAndRefresh { recentsStore.clear() }

    /** Ejecuta una operacion del almacen y vuelve a leer las listas. */
    private fun launchAndRefresh(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
            refresh()
        }
    }
}
