package com.dskmusic.pixa.core.history

import androidx.annotation.StringRes
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.bitmapIds

/**
 * Historial de deshacer y rehacer (requisito 16).
 *
 * Cada paso guarda un documento completo. Puede parecer caro, pero el documento no
 * contiene pixeles: solo ids que apuntan al [BitmapStore], asi que una instantanea son
 * unos pocos kilobytes y saltar a cualquier punto de la lista es inmediato.
 *
 * Por eso no se vuelcan estados a disco: lo que ocupa memoria de verdad son los bitmaps,
 * y eso se controla aqui llamando a [BitmapStore.gc] con los ids que siguen vivos cada
 * vez que se recorta el historial por el limite configurado.
 */
class HistoryStack(
    private val store: BitmapStore,
    limit: Int = 40,
) {

    data class Entry(
        @StringRes val labelRes: Int,
        val document: PixaDocument,
    )

    private val entries = ArrayDeque<Entry>()

    /** Posicion actual dentro de [entries]; -1 solo antes del primer push. */
    var cursor: Int = -1
        private set

    var limit: Int = limit.coerceIn(5, 500)
        private set

    val steps: List<Entry> get() = entries.toList()

    val canUndo: Boolean get() = cursor > 0

    val canRedo: Boolean get() = cursor in 0 until entries.size - 1

    val current: PixaDocument? get() = entries.getOrNull(cursor)?.document

    /** Instante del ultimo push, para fundir los pasos de un mismo arrastre. */
    private var lastPushAt = 0L

    /**
     * Anade un paso. Si se habia deshecho algo, los pasos que quedaban por delante se
     * descartan: es el comportamiento que espera cualquiera que haya usado un editor.
     *
     * Dos pasos seguidos con la misma etiqueta y separados por menos de [COALESCE_MS] se
     * funden en uno. Arrastrar el deslizador de opacidad produce decenas de documentos y
     * lo que el usuario espera deshacer es el arrastre entero, no un pixel de recorrido;
     * de paso el historial no se llena de cuarenta "Opacidad" iguales.
     */
    fun push(@StringRes labelRes: Int, document: PixaDocument) {
        val now = System.currentTimeMillis()
        val mergeable = entries.size > 1 &&
            cursor == entries.size - 1 &&
            entries.last().labelRes == labelRes &&
            now - lastPushAt < COALESCE_MS
        lastPushAt = now
        if (mergeable) {
            entries[entries.size - 1] = Entry(labelRes, document)
            return
        }
        while (entries.size > cursor + 1) entries.removeLast()
        entries.addLast(Entry(labelRes, document))
        cursor = entries.size - 1
        trim()
    }

    fun undo(): PixaDocument? {
        if (!canUndo) return null
        cursor--
        return entries[cursor].document
    }

    fun redo(): PixaDocument? {
        if (!canRedo) return null
        cursor++
        return entries[cursor].document
    }

    /** Salto directo a un paso desde el panel de historial. */
    fun jumpTo(index: Int): PixaDocument? {
        if (index !in entries.indices) return null
        cursor = index
        return entries[index].document
    }

    fun setLimit(newLimit: Int) {
        limit = newLimit.coerceIn(5, 500)
        trim()
    }

    /** Ids de bitmap referenciados por cualquier paso vivo. */
    fun liveBitmapIds(): Set<String> {
        val ids = mutableSetOf<String>()
        for (e in entries) ids += e.document.bitmapIds()
        return ids
    }

    fun clear() {
        entries.clear()
        cursor = -1
        lastPushAt = 0L
    }

    /**
     * Recorta por el principio hasta cumplir el limite y libera los bitmaps que ya no
     * referencia ningun paso.
     */
    private fun trim() {
        var removed = false
        while (entries.size > limit) {
            entries.removeFirst()
            cursor--
            removed = true
        }
        if (cursor < 0 && entries.isNotEmpty()) cursor = 0
        if (removed) store.gc(liveBitmapIds())
    }

    private companion object {
        /** Margen entre dos cambios para considerarlos el mismo gesto. */
        const val COALESCE_MS = 700L
    }
}
