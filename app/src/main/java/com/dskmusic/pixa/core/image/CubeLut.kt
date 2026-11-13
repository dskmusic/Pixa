package com.dskmusic.pixa.core.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.StringTokenizer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * LUT 3D en formato Adobe/Resolve `.cube`, el estandar de "looks" profesionales.
 *
 * [data] guarda size^3 entradas RGB con el rojo variando mas rapido, como en el archivo.
 * La aplicacion interpola trilinealmente. Las LUT 1D no se admiten: [parse] devuelve null.
 */
class CubeLut private constructor(
    val size: Int,
    private val data: FloatArray,
    private val domainMin: FloatArray,
    private val domainMax: FloatArray,
) {

    /** Aplica la LUT con intensidad [strength] (0 = original, 1 = LUT completa). */
    fun apply(src: Bitmap, strength: Float = 1f): Bitmap {
        val k = strength.coerceIn(0f, 1f)
        if (k <= 0f) return BitmapUtils.mutableCopy(src)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val n = size
        val last = (n - 1).toFloat()
        val scaleR = last / max(1e-6f, domainMax[0] - domainMin[0])
        val scaleG = last / max(1e-6f, domainMax[1] - domainMin[1])
        val scaleB = last / max(1e-6f, domainMax[2] - domainMin[2])

        Parallel.rows(px.size) { i0, i1 ->
            val out = FloatArray(3)
            for (i in i0 until i1) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            val r = (p ushr 16 and 0xFF)
            val g = (p ushr 8 and 0xFF)
            val b = (p and 0xFF)
            sample(
                ((r / 255f - domainMin[0]) * scaleR).coerceIn(0f, last),
                ((g / 255f - domainMin[1]) * scaleG).coerceIn(0f, last),
                ((b / 255f - domainMin[2]) * scaleB).coerceIn(0f, last),
                out,
            )
            val nr = (r + (out[0].coerceIn(0f, 1f) * 255f - r) * k).roundToInt().coerceIn(0, 255)
            val ng = (g + (out[1].coerceIn(0f, 1f) * 255f - g) * k).roundToInt().coerceIn(0, 255)
            val nb = (b + (out[2].coerceIn(0f, 1f) * 255f - b) * k).roundToInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        val result = BitmapUtils.create(w, h)
        result.setPixels(px, 0, w, 0, 0, w, h)
        return result
    }

    /** Interpolacion trilineal en coordenadas de rejilla; el resultado va a [out]. */
    private fun sample(r: Float, g: Float, b: Float, out: FloatArray) {
        val n = size
        val r0 = r.toInt()
        val g0 = g.toInt()
        val b0 = b.toInt()
        val r1 = if (r0 + 1 < n) r0 + 1 else r0
        val g1 = if (g0 + 1 < n) g0 + 1 else g0
        val b1 = if (b0 + 1 < n) b0 + 1 else b0
        val fr = r - r0
        val fg = g - g0
        val fb = b - b0
        val i000 = ((b0 * n + g0) * n + r0) * 3
        val i100 = ((b0 * n + g0) * n + r1) * 3
        val i010 = ((b0 * n + g1) * n + r0) * 3
        val i110 = ((b0 * n + g1) * n + r1) * 3
        val i001 = ((b1 * n + g0) * n + r0) * 3
        val i101 = ((b1 * n + g0) * n + r1) * 3
        val i011 = ((b1 * n + g1) * n + r0) * 3
        val i111 = ((b1 * n + g1) * n + r1) * 3
        for (c in 0 until 3) {
            val c00 = data[i000 + c] + (data[i100 + c] - data[i000 + c]) * fr
            val c10 = data[i010 + c] + (data[i110 + c] - data[i010 + c]) * fr
            val c01 = data[i001 + c] + (data[i101 + c] - data[i001 + c]) * fr
            val c11 = data[i011 + c] + (data[i111 + c] - data[i011 + c]) * fr
            val c0 = c00 + (c10 - c00) * fg
            val c1 = c01 + (c11 - c01) * fg
            out[c] = c0 + (c1 - c0) * fb
        }
    }

    companion object {
        private const val MAX_SIZE = 129

        /** Lee un `.cube` 3D. Null si esta mal formado, es 1D o no tiene todas las entradas. */
        fun parse(text: String): CubeLut? {
            var size = 0
            val dmin = floatArrayOf(0f, 0f, 0f)
            val dmax = floatArrayOf(1f, 1f, 1f)
            var data: FloatArray? = null
            var filled = 0

            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line[0] == '#') continue
                val c = line[0]
                if (c.isLetter()) {
                    val t = StringTokenizer(line)
                    when (t.nextToken().uppercase()) {
                        "LUT_1D_SIZE" -> return null
                        "LUT_3D_SIZE" -> {
                            size = t.nextToken().toIntOrNull() ?: return null
                            if (size < 2 || size > MAX_SIZE) return null
                            data = FloatArray(size * size * size * 3)
                        }
                        "DOMAIN_MIN" -> for (j in 0 until 3) dmin[j] = t.nextToken().toFloatOrNull() ?: return null
                        "DOMAIN_MAX" -> for (j in 0 until 3) dmax[j] = t.nextToken().toFloatOrNull() ?: return null
                        // TITLE y cualquier otra cabecera se ignoran.
                        else -> Unit
                    }
                    continue
                }
                val target = data ?: return null
                if (filled + 3 > target.size) return null
                val t = StringTokenizer(line)
                if (t.countTokens() < 3) return null
                for (j in 0 until 3) {
                    target[filled + j] = t.nextToken().toFloatOrNull() ?: return null
                }
                filled += 3
            }
            val done = data ?: return null
            if (filled != done.size) return null
            return CubeLut(size, done, dmin, dmax)
        }
    }
}

/**
 * Biblioteca de LUT importadas, guardadas como copias en el almacenamiento propio de la
 * app. Se copia el archivo en lugar de recordar la Uri: el permiso del selector de
 * archivos caduca y la LUT dejaria de funcionar.
 */
object LutStore {

    private const val MAX_BYTES = 40_000_000

    private fun dir(context: Context): File = File(context.filesDir, "luts").apply { mkdirs() }

    // Una LUT de 65^3 pesa unos 3 MB en memoria: se guardan pocas y la clave lleva la fecha.
    private val cache = LinkedHashMap<String, CubeLut>()

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension.equals("cube", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    fun displayName(file: File): String = file.nameWithoutExtension

    fun delete(file: File) {
        synchronized(cache) { cache.remove(file.path + file.lastModified()) }
        file.delete()
    }

    fun load(file: File): CubeLut? {
        val key = file.path + file.lastModified()
        synchronized(cache) { cache[key]?.let { return it } }
        val lut = runCatching { CubeLut.parse(file.readText()) }.getOrNull() ?: return null
        synchronized(cache) {
            if (cache.size >= 4) cache.remove(cache.keys.first())
            cache[key] = lut
        }
        return lut
    }

    /** Importa el `.cube` de [uri]. Devuelve el archivo guardado, o null si no es una LUT 3D valida. */
    suspend fun import(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                    if (buffer.size() > MAX_BYTES) return@runCatching null
                }
                buffer.toByteArray()
            } ?: return@runCatching null
            val text = String(bytes, Charsets.UTF_8)
            // Se valida antes de guardar: un archivo roto no debe quedar en la lista.
            CubeLut.parse(text) ?: return@runCatching null

            val shown = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            val base = (shown ?: "lut").substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "_")
                .trim().ifEmpty { "lut" }.take(40)
            var target = File(dir(context), "$base.cube")
            var n = 2
            while (target.exists()) target = File(dir(context), "$base $n.cube").also { n++ }
            target.writeText(text)
            target
        }.getOrNull()
    }

    /** Miniatura de [file] aplicada a [base], para la lista de LUT del panel. */
    fun thumb(file: File, base: Bitmap): Bitmap? = load(file)?.apply(base, 1f)
}
