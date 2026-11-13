package com.dskmusic.pixa.core.project

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.bitmapIds
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Formato propio .pixa (requisito 5).
 *
 * Es un zip con el documento en JSON y un PNG por cada bitmap al que apunta. Guardar los
 * pixeles como PNG y no dentro del JSON es lo que permite abrir el mismo archivo en otro
 * telefono sin perder calidad ni transparencia, y mantiene el JSON legible.
 *
 *   document.json          modelo completo del proyecto
 *   bitmaps/<id>.png       un archivo por bitmap referenciado
 *   thumb.png              miniatura para la pantalla de inicio
 */
object PixaFile {

    const val EXTENSION = "pixa"
    const val MIME = "application/octet-stream"

    private const val ENTRY_DOC = "document.json"
    private const val ENTRY_BITMAPS = "bitmaps/"
    private const val ENTRY_THUMB = "thumb.png"
    private const val THUMB_MAX = 512

    /** Version del formato, por si en el futuro cambia la estructura. */
    const val FORMAT_VERSION = 1

    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    class FormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Loaded(val document: PixaDocument, val thumbnail: Bitmap?)

    // ----------------------------------------------------------------------------------
    // Guardar
    // ----------------------------------------------------------------------------------

    fun save(
        file: File,
        document: PixaDocument,
        store: BitmapStore,
        thumbnail: Bitmap? = null,
    ) {
        file.parentFile?.mkdirs()
        // Se escribe a un temporal y se renombra: si la app muere a mitad, el .pixa
        // anterior sigue intacto en vez de quedarse un zip truncado.
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_DOC))
                val header = json.encodeToString(PixaDocument.serializer(), document)
                zip.write(header.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                for (id in document.bitmapIds()) {
                    val bmp = store.get(id) ?: continue
                    zip.putNextEntry(ZipEntry("$ENTRY_BITMAPS$id.png"))
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, zip)
                    zip.closeEntry()
                }

                thumbnail?.let {
                    zip.putNextEntry(ZipEntry(ENTRY_THUMB))
                    val thumb = BitmapUtils.thumbnail(it, THUMB_MAX)
                    thumb.compress(Bitmap.CompressFormat.PNG, 90, zip)
                    if (thumb !== it) thumb.recycle()
                    zip.closeEntry()
                }
            }
            if (file.exists() && !file.delete()) {
                throw FormatException("No se pudo reemplazar el proyecto anterior")
            }
            if (!temp.renameTo(file)) throw FormatException("No se pudo renombrar el proyecto")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    // ----------------------------------------------------------------------------------
    // Abrir
    // ----------------------------------------------------------------------------------

    fun load(file: File, store: BitmapStore): Loaded =
        BufferedInputStream(file.inputStream()).use { read(it, store) }

    /** Importar desde otro telefono: llega por uri compartida, no por ruta. */
    fun load(context: Context, uri: Uri, store: BitmapStore): Loaded {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw FormatException("No se pudo abrir el archivo")
        return stream.use { read(BufferedInputStream(it), store) }
    }

    private fun read(input: InputStream, store: BitmapStore): Loaded {
        var document: PixaDocument? = null
        var thumbnail: Bitmap? = null
        val bitmaps = mutableMapOf<String, Bitmap>()

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == ENTRY_DOC -> {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        document = try {
                            json.decodeFromString(PixaDocument.serializer(), text)
                        } catch (e: Exception) {
                            throw FormatException("El proyecto no se pudo leer", e)
                        }
                    }
                    name == ENTRY_THUMB -> {
                        thumbnail = BitmapFactory.decodeStream(zip)
                    }
                    name.startsWith(ENTRY_BITMAPS) && name.endsWith(".png") -> {
                        val id = name.removePrefix(ENTRY_BITMAPS).removeSuffix(".png")
                        // Nombre de entrada saneado: un zip ajeno podria traer rutas con
                        // ../ para escribir fuera de su sitio.
                        if (id.isNotEmpty() && !id.contains('/') && !id.contains("..")) {
                            BitmapFactory.decodeStream(zip)?.let { bitmaps[id] = it }
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        val doc = document ?: throw FormatException("El archivo no es un proyecto de Pixa")
        val needed = doc.bitmapIds()
        val missing = needed.filter { it !in bitmaps }
        if (missing.isNotEmpty()) {
            bitmaps.values.forEach { it.recycle() }
            throw FormatException("Al proyecto le faltan ${missing.size} imagenes")
        }
        bitmaps.forEach { (id, bmp) -> store.put(id, bmp) }
        return Loaded(doc, thumbnail)
    }

    /** Lee solo la miniatura y el tamano, para listar proyectos sin cargarlos enteros. */
    fun peek(file: File): PixaPeek? {
        var thumb: Bitmap? = null
        var width = 0
        var height = 0
        runCatching {
            ZipInputStream(BufferedInputStream(file.inputStream())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        ENTRY_THUMB -> thumb = BitmapFactory.decodeStream(zip)
                        ENTRY_DOC -> {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            val doc = json.decodeFromString(PixaDocument.serializer(), text)
                            width = doc.width
                            height = doc.height
                        }
                    }
                    zip.closeEntry()
                }
            }
        }.onFailure { return null }
        return PixaPeek(thumb, width, height)
    }

    data class PixaPeek(val thumbnail: Bitmap?, val width: Int, val height: Int)
}
