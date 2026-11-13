package com.dskmusic.pixa.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Contenido de una capa (requisito 7).
 *
 * Los pixeles nunca viajan dentro del modelo: las capas raster guardan el id de un bitmap
 * que vive en el BitmapStore. Asi el documento sigue siendo un objeto inmutable, ligero,
 * serializable y barato de copiar para el historial de deshacer.
 */
@Serializable
sealed interface LayerContent {

    /** Imagen o dibujo a mano rasterizado. */
    @Serializable
    data class Raster(
        val bitmapId: String,
        /** Marca las capas creadas con herramientas de dibujo, para la UI. */
        val isDrawing: Boolean = false,
    ) : LayerContent

    @Serializable
    data class Text(val spec: TextSpec) : LayerContent

    @Serializable
    data class Shape(val spec: ShapeSpec) : LayerContent

    /** Relleno que cubre todo el lienzo: color plano o degradado. */
    @Serializable
    data class Fill(val spec: FillSpec) : LayerContent

    @Serializable
    data class Qr(val spec: QrSpec) : LayerContent

    /** Emoji como capa, escalable sin perdida porque se dibuja como texto. */
    @Serializable
    data class Emoji(val emoji: String, val sizePx: Float = 256f) : LayerContent

    /**
     * Bocadillo de comic editable. Se dibuja sobre un lienzo de referencia de 1080 px y la
     * capa lo escala; sus coordenadas cx / cy no se usan, la posicion es la de la capa.
     */
    @Serializable
    data class SpeechBubble(val spec: com.dskmusic.pixa.core.collage.Bubble) : LayerContent
}

/** Carpeta de capas (requisito 7). Las capas apuntan a ella por id. */
@Serializable
data class LayerGroup(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val collapsed: Boolean = false,
)

/** Mascara de capa: un bitmap en escala de grises del tamano del contenido. */
@Serializable
data class MaskRef(
    val bitmapId: String,
    val enabled: Boolean = true,
    val inverted: Boolean = false,
)

@Serializable
data class PixaLayer(
    val id: String = newId(),
    val name: String,
    val content: LayerContent,
    val transform: Xf = Xf(),
    val visible: Boolean = true,
    val locked: Boolean = false,
    /** Bloqueo solo de mover/redimensionar; los pixeles siguen editables (capa base al abrir imagen). */
    val transformLocked: Boolean = false,
    val opacity: Float = 1f,
    val blend: PixaBlend = PixaBlend.NORMAL,
    val mask: MaskRef? = null,
    val style: LayerStyle? = null,
    /** Mascara de recorte: recorta esta capa con el alfa de la de debajo. */
    val clipToBelow: Boolean = false,
    val groupId: String? = null,
) {
    val isRaster: Boolean get() = content is LayerContent.Raster

    /** El texto sigue siendo editable mientras no se rasterice. */
    val isEditableText: Boolean get() = content is LayerContent.Text

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/** Fondo del lienzo: transparente o un color plano. */
@Serializable
data class CanvasBackground(
    val transparent: Boolean = false,
    val color: Int = 0xFFFFFFFF.toInt(),
)

/**
 * Documento completo. [layers] esta ordenada de abajo (indice 0) a arriba, igual que
 * el orden de pintado.
 */
@Serializable
data class PixaDocument(
    val width: Int,
    val height: Int,
    val background: CanvasBackground = CanvasBackground(),
    val layers: List<PixaLayer> = emptyList(),
    val groups: List<LayerGroup> = emptyList(),
    val selectedLayerId: String? = null,
) {
    val selected: PixaLayer? get() = layers.firstOrNull { it.id == selectedLayerId }

    fun indexOf(layerId: String): Int = layers.indexOfFirst { it.id == layerId }

    fun layer(layerId: String?): PixaLayer? =
        if (layerId == null) null else layers.firstOrNull { it.id == layerId }

    /** Visibilidad efectiva: la capa y su carpeta deben estar visibles. */
    fun isEffectivelyVisible(layer: PixaLayer): Boolean {
        if (!layer.visible) return false
        val g = layer.groupId?.let { id -> groups.firstOrNull { it.id == id } }
        return g?.visible ?: true
    }

    fun replaceLayer(layer: PixaLayer): PixaDocument =
        copy(layers = layers.map { if (it.id == layer.id) layer else it })

    fun updateLayer(layerId: String, block: (PixaLayer) -> PixaLayer): PixaDocument =
        copy(layers = layers.map { if (it.id == layerId) block(it) else it })

    /** Inserta encima de la capa seleccionada y la deja seleccionada. */
    fun addLayerOnTop(layer: PixaLayer): PixaDocument {
        val at = selectedLayerId?.let { indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: layers.size
        val next = layers.toMutableList().apply { add(at, layer) }
        return copy(layers = next, selectedLayerId = layer.id)
    }

    fun removeLayer(layerId: String): PixaDocument {
        val idx = indexOf(layerId)
        if (idx < 0) return this
        val next = layers.filterNot { it.id == layerId }
        val newSelection = next.getOrNull(idx.coerceAtMost(next.lastIndex))?.id
        return copy(layers = next, selectedLayerId = newSelection)
    }

    /** Mueve una capa a otra posicion de la pila (reordenar arrastrando). */
    fun moveLayer(from: Int, to: Int): PixaDocument {
        if (from !in layers.indices) return this
        val target = to.coerceIn(0, layers.lastIndex)
        if (from == target) return this
        val next = layers.toMutableList()
        next.add(target, next.removeAt(from))
        return copy(layers = next)
    }

    companion object {
        fun blank(width: Int, height: Int, background: CanvasBackground): PixaDocument =
            PixaDocument(width = width, height = height, background = background)
    }
}
