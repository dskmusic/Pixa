package com.dskmusic.pixa.core.bitmap

import android.graphics.Bitmap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Almacen de pixeles del documento.
 *
 * El modelo (PixaDocument) solo guarda ids de bitmap, nunca los pixeles. Eso permite
 * copiar el documento entero para cada paso del historial sin duplicar memoria: los
 * bitmaps no modificados se comparten por referencia.
 *
 * Regla de oro: un bitmap registrado aqui se trata como inmutable. Toda edicion de
 * pixeles crea un bitmap nuevo con un id nuevo (copy-on-write). La unica excepcion es
 * el bitmap de trabajo de un trazo de pincel, que se muta mientras el dedo esta en
 * pantalla y se registra como nuevo al levantarlo.
 *
 * Por eso no se recicla al borrar una capa (el historial puede seguir apuntando al
 * bitmap): la memoria se libera en [gc] cuando el historial se recorta, y en [clear]
 * al cerrar el editor.
 */
class BitmapStore {

    private val map = ConcurrentHashMap<String, Bitmap>()

    fun put(bitmap: Bitmap): String {
        val id = UUID.randomUUID().toString()
        map[id] = bitmap
        return id
    }

    fun put(id: String, bitmap: Bitmap) {
        map[id] = bitmap
    }

    fun get(id: String?): Bitmap? {
        if (id == null) return null
        val b = map[id] ?: return null
        return if (b.isRecycled) null else b
    }

    fun contains(id: String): Boolean = map.containsKey(id)

    fun ids(): Set<String> = map.keys.toSet()

    fun size(): Int = map.size

    /** Bytes aproximados retenidos, para decidir cuando recortar el historial. */
    fun approximateBytes(): Long =
        map.values.sumOf { if (it.isRecycled) 0L else it.allocationByteCount.toLong() }

    /**
     * Libera los bitmaps que ya no referencia nadie. [liveIds] debe incluir los ids del
     * documento actual y de todos los pasos de historial que siguen vivos.
     */
    fun gc(liveIds: Set<String>): Int {
        var freed = 0
        for (id in map.keys.toList()) {
            if (id in liveIds) continue
            map.remove(id)?.let {
                if (!it.isRecycled) {
                    it.recycle()
                    freed++
                }
            }
        }
        return freed
    }

    fun clear() {
        for (b in map.values) {
            if (!b.isRecycled) b.recycle()
        }
        map.clear()
    }
}
