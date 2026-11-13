package com.dskmusic.pixa.data.userdata

import android.content.Context
import android.net.Uri
import com.dskmusic.pixa.core.model.AdjustPreset
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.WatermarkSpec
import com.dskmusic.pixa.core.project.PixaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Fuente importada por el usuario (requisito 10). No se empaqueta ninguna en la app. */
@Serializable
data class ImportedFont(
    val family: String,
    val fileName: String,
)

@Serializable
data class UserData(
    val presets: List<AdjustPreset> = emptyList(),
    val watermarks: List<WatermarkSpec> = emptyList(),
    val fonts: List<ImportedFont> = emptyList(),
)

/**
 * Datos creados por el usuario: presets de ajustes, marcas de agua y fuentes importadas
 * (requisitos 10, 13 y 15), mas la copia de seguridad del requisito 1.
 *
 * Todo vive en el almacenamiento privado de la app. La copia de seguridad es un zip con
 * el JSON y los archivos binarios: un unico archivo que el usuario puede guardar donde
 * quiera y restaurar en otro telefono.
 */
class UserDataStore(private val context: Context) {

    private val root: File get() = File(context.filesDir, "userdata").apply { mkdirs() }
    private val dataFile: File get() = File(root, "data.json")
    private val logosDir: File get() = File(root, "logos").apply { mkdirs() }
    private val fontsDir: File get() = File(root, "fonts").apply { mkdirs() }

    private val _data = MutableStateFlow(UserData())
    val data: StateFlow<UserData> = _data.asStateFlow()

    fun logoFile(name: String): File = File(logosDir, name)

    /** Carpeta de logos, que Overlays necesita para resolver el logo de la marca. */
    fun logoDir(): File = logosDir
    fun fontFile(name: String): File = File(fontsDir, name)

    suspend fun refresh(): UserData = withContext(Dispatchers.IO) {
        val loaded = read()
        _data.value = loaded
        loaded
    }

    private fun read(): UserData {
        if (!dataFile.exists()) return UserData()
        return runCatching {
            PixaFile.json.decodeFromString(UserData.serializer(), dataFile.readText())
        }.getOrElse { UserData() }
    }

    private fun write(data: UserData) {
        runCatching {
            dataFile.writeText(PixaFile.json.encodeToString(UserData.serializer(), data))
        }
        _data.value = data
    }

    private suspend fun edit(block: (UserData) -> UserData) = withContext(Dispatchers.IO) {
        write(block(read()))
    }

    // ----------------------------------------------------------------------------------
    // Presets de ajustes
    // ----------------------------------------------------------------------------------

    suspend fun savePreset(preset: AdjustPreset) = edit { data ->
        data.copy(presets = data.presets.filterNot { it.id == preset.id } + preset)
    }

    suspend fun deletePreset(id: String) = edit { data ->
        data.copy(presets = data.presets.filterNot { it.id == id })
    }

    suspend fun renamePreset(id: String, name: String) = edit { data ->
        data.copy(presets = data.presets.map { if (it.id == id) it.copy(name = name) else it })
    }

    // ----------------------------------------------------------------------------------
    // Marcas de agua
    // ----------------------------------------------------------------------------------

    suspend fun saveWatermark(spec: WatermarkSpec) = edit { data ->
        data.copy(watermarks = data.watermarks.filterNot { it.id == spec.id } + spec)
    }

    suspend fun deleteWatermark(id: String) = withContext(Dispatchers.IO) {
        val data = read()
        data.watermarks.firstOrNull { it.id == id }?.logoFile?.let { logo ->
            // Solo se borra el logo si no lo comparte otro preset.
            if (data.watermarks.none { it.id != id && it.logoFile == logo }) {
                logoFile(logo).delete()
            }
        }
        write(data.copy(watermarks = data.watermarks.filterNot { it.id == id }))
    }

    /** Copia el logo elegido a los datos de la app y devuelve su nombre de archivo. */
    suspend fun importLogo(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = "logo_${UUID.randomUUID()}.png"
        val target = logoFile(name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("No se pudo leer el logo")
        name
    }

    // ----------------------------------------------------------------------------------
    // Fuentes importadas
    // ----------------------------------------------------------------------------------

    /**
     * Copia un TTF u OTF elegido por el usuario. La app no trae ninguna fuente dentro,
     * las de Google se descargan y estas las aporta el usuario.
     */
    suspend fun importFont(uri: Uri, displayName: String): FontRef = withContext(Dispatchers.IO) {
        val safe = displayName.replace(Regex("[^A-Za-z0-9 _.-]"), "_").ifBlank { "fuente" }
        val fileName = "${UUID.randomUUID()}_$safe"
        val target = fontFile(fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("No se pudo leer la fuente")

        val family = safe.substringBeforeLast('.')
        val data = read()
        write(data.copy(fonts = data.fonts.filterNot { it.fileName == fileName } +
            ImportedFont(family, fileName)))
        FontRef(family = family, source = FontRef.Source.IMPORTED, filePath = target.absolutePath)
    }

    /**
     * Importa todas las fuentes TTF/OTF que haya dentro de un zip (requisito del usuario:
     * compartir paquetes de fuentes con otros usuarios de un tiron, sin importarlas de
     * una en una).
     */
    suspend fun importFontsFromZip(uri: Uri): List<FontRef> = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo leer el zip")
        val refs = mutableListOf<FontRef>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && entryName.substringAfterLast('.', "")
                        .lowercase() in setOf("ttf", "otf")
                ) {
                    val safe = entryName.replace(Regex("[^A-Za-z0-9 _.-]"), "_")
                    val fileName = "${UUID.randomUUID()}_$safe"
                    fontFile(fileName).outputStream().use { out -> zip.copyTo(out) }
                    val family = safe.substringBeforeLast('.')
                    edit { data ->
                        data.copy(
                            fonts = data.fonts.filterNot { it.fileName == fileName } +
                                ImportedFont(family, fileName),
                        )
                    }
                    refs += FontRef(
                        family = family,
                        source = FontRef.Source.IMPORTED,
                        filePath = fontFile(fileName).absolutePath,
                    )
                }
                zip.closeEntry()
            }
        }
        refs
    }

    suspend fun deleteFont(fileName: String) = withContext(Dispatchers.IO) {
        fontFile(fileName).delete()
        val data = read()
        write(data.copy(fonts = data.fonts.filterNot { it.fileName == fileName }))
    }

    fun fontRefFor(font: ImportedFont): FontRef = FontRef(
        family = font.family,
        source = FontRef.Source.IMPORTED,
        filePath = fontFile(font.fileName).absolutePath,
    )

    // ----------------------------------------------------------------------------------
    // Copia de seguridad (requisito 1)
    // ----------------------------------------------------------------------------------

    /** Escribe el zip de copia de seguridad en la cache y devuelve el archivo. */
    suspend fun exportBackup(settings: Map<String, String>): File = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "shared").apply { mkdirs() }
            .resolve("pixa_backup_${System.currentTimeMillis()}.pixabak")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_SETTINGS))
            val settingsJson = PixaFile.json.encodeToString(STRING_MAP, settings)
            zip.write(settingsJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_DATA))
            zip.write(
                PixaFile.json.encodeToString(UserData.serializer(), read())
                    .toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()

            listOf(logosDir to DIR_LOGOS, fontsDir to DIR_FONTS)
                .forEach { (dir, prefix) ->
                    dir.listFiles()?.forEach { file ->
                        zip.putNextEntry(ZipEntry("$prefix${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
        }
        out
    }

    /**
     * Restaura una copia de seguridad y devuelve el mapa de ajustes para que lo aplique
     * SettingsRepository. Los archivos binarios se reescriben tal cual.
     */
    suspend fun importBackup(uri: Uri): Map<String, String> = withContext(Dispatchers.IO) {
        val settings = mutableMapOf<String, String>()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir la copia de seguridad")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == ENTRY_SETTINGS -> {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        runCatching {
                            PixaFile.json.decodeFromString(STRING_MAP, text)
                        }.getOrNull()?.let { settings.putAll(it) }
                    }
                    name == ENTRY_DATA -> {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        runCatching {
                            PixaFile.json.decodeFromString(UserData.serializer(), text)
                        }.getOrNull()?.let { write(it) }
                    }
                    else -> {
                        // Nombre saneado: un zip manipulado podria intentar salir del
                        // directorio con ../ y escribir en cualquier sitio.
                        val target = when {
                            name.startsWith(DIR_LOGOS) -> logoFile(name.removePrefix(DIR_LOGOS))
                            name.startsWith(DIR_FONTS) -> fontFile(name.removePrefix(DIR_FONTS))
                            else -> null
                        }
                        if (target != null && !target.name.contains("..") &&
                            !target.name.contains('/')
                        ) {
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        refresh()
        settings
    }

    companion object {
        /** Serializador del mapa de ajustes de la copia de seguridad. */
        private val STRING_MAP = MapSerializer(String.serializer(), String.serializer())

        const val BACKUP_EXTENSION = "pixabak"
        private const val ENTRY_SETTINGS = "settings.json"
        private const val ENTRY_DATA = "userdata.json"
        private const val DIR_LOGOS = "logos/"
        private const val DIR_FONTS = "fonts/"
    }
}
