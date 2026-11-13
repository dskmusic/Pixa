package com.dskmusic.pixa.core.model

/**
 * Ids de bitmap a los que apunta un documento.
 *
 * Lo necesitan dos sitios: el recolector del BitmapStore, para saber que pixeles siguen
 * vivos cuando se recorta el historial, y el guardado del formato .pixa, para escribir
 * exactamente los PNG que hacen falta y ninguno mas.
 */

/** Ids referenciados por un relleno (puede llevar una imagen dentro). */
fun FillSpec?.bitmapIds(): Set<String> =
    if (this is FillSpec.Image) setOf(bitmapId) else emptySet()

/** Ids referenciados por una capa: contenido, relleno, mascara y logo del QR. */
fun PixaLayer.bitmapIds(): Set<String> {
    val ids = mutableSetOf<String>()
    when (val c = content) {
        is LayerContent.Raster -> ids += c.bitmapId
        is LayerContent.Text -> ids += c.spec.fill.bitmapIds()
        is LayerContent.Shape -> ids += c.spec.fill.bitmapIds()
        is LayerContent.Fill -> ids += c.spec.bitmapIds()
        is LayerContent.Qr -> c.spec.logoBitmapId?.let { ids += it }
        is LayerContent.Emoji -> Unit
        is LayerContent.SpeechBubble -> Unit
    }
    mask?.let { ids += it.bitmapId }
    return ids
}

fun PixaDocument.bitmapIds(): Set<String> {
    val ids = mutableSetOf<String>()
    for (layer in layers) ids += layer.bitmapIds()
    return ids
}
