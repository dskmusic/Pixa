package com.dskmusic.pixa.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.dskmusic.pixa.core.project.DraftStore
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** Foto instantanea de un generador: su estado en JSON y los ids de los bitmaps que usa. */
data class DraftSnapshot(val json: String, val imageIds: List<String>)

/** Mango del borrador: [discard] lo borra y detiene el autoguardado (al salir o terminar). */
class DraftHandle internal constructor(
    private val store: DraftStore,
    private val kind: String,
) {
    @Volatile
    internal var active = true

    fun discard() {
        active = false
        store.clear(kind)
    }
}

/**
 * Autoguardado y recuperacion de un generador.
 *
 * - Si Inicio pidio restaurar este generador, [restore] recibe el JSON guardado y una
 *   funcion para devolver cada imagen al almacen del editor con su id original.
 * - Despues, cada cambio de [snapshot] se guarda (con una pausa para no escribir a cada
 *   paso de un deslizador). Con `null` no hay nada que proteger y se borra el borrador.
 */
@Composable
fun rememberDraft(
    kind: String,
    vm: EditorViewModel,
    snapshot: () -> DraftSnapshot?,
    restore: suspend (json: String, loadImage: suspend (String) -> Boolean) -> Unit,
): DraftHandle {
    val store = LocalContext.current.pixa.drafts
    val handle = remember { DraftHandle(store, kind) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (store.takeRestore(kind)) {
            runCatching {
                val json = withContext(Dispatchers.IO) { store.readState(kind) }
                if (json != null) {
                    restore(json) { id ->
                        val bmp = withContext(Dispatchers.IO) { store.readImage(kind, id) }
                        if (bmp != null) vm.putBitmap(id, bmp)
                        bmp != null
                    }
                }
            }
        }
        ready = true
    }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow(snapshot).collectLatest { snap ->
            delay(1200)
            if (!handle.active) return@collectLatest
            withContext(Dispatchers.IO) {
                if (snap == null) {
                    store.clear(kind)
                } else {
                    store.save(kind, snap.json, snap.imageIds.distinct()) { vm.getBitmap(it) }
                    // Si se descarto mientras se escribia, no dejar el borrador a medias.
                    if (!handle.active) store.clear(kind)
                }
            }
        }
    }
    return handle
}
