package com.dskmusic.pixa.ml

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Operaciones de IA sobre ONNX Runtime. Todas siguen las mismas reglas:
 *  - Suponen que el modelo ya esta descargado (la interfaz lo descarga antes con
 *    AiModel.download); si falta o falla la inferencia devuelven null.
 *  - Son cancelables: comprueban la corrutina entre teselas y lanzan CancellationException.
 *  - Informan del progreso 0..1 desde un hilo de fondo.
 * Los formatos de entrada y salida de cada modelo se comprobaron con inferencia real.
 */

private fun OnnxTensor.floats(): FloatArray {
    val fb = floatBuffer
    return FloatArray(fb.remaining()).also { fb.get(it) }
}

private fun pack(r: Float, g: Float, b: Float, alpha: Int = 0xFF): Int {
    val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
    val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
    val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (alpha shl 24) or (ri shl 16) or (gi shl 8) or bi
}

// ======================================================================================
// Quitar ruido: SCUNet (ruido real, color)
// ======================================================================================

object Denoiser {
    private const val FILE = "scunet_color_real_psnr.onnx"

    // El modelo exige entradas multiplo de 64 (200x333 falla, 256x256 funciona): se trabaja
    // siempre con teselas de 256 = 224 utiles + 16 de margen a cada lado, que se descarta.
    private const val CORE = 224
    private const val MARGIN = 16
    private const val SIZE = 256

    suspend fun denoise(context: Context, src: Bitmap, onProgress: (Float) -> Unit = {}): Bitmap? =
        withContext(Dispatchers.Default) {
            val ort = AiRuntime.session(context.applicationContext, FILE) ?: return@withContext null
            try {
                val w = src.width
                val h = src.height
                val pixels = IntArray(w * h)
                src.getPixels(pixels, 0, w, 0, 0, w, h)
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val env = OrtEnvironment.getEnvironment()
                val inputName = ort.inputNames.iterator().next()
                val tilesX = (w + CORE - 1) / CORE
                val tilesY = (h + CORE - 1) / CORE
                val total = tilesX * tilesY
                var done = 0
                val plane = SIZE * SIZE
                val data = FloatArray(3 * plane)
                onProgress(0f)
                for (ty in 0 until tilesY) {
                    for (tx in 0 until tilesX) {
                        currentCoroutineContext().ensureActive()
                        val x0 = tx * CORE
                        val y0 = ty * CORE
                        val cw = min(CORE, w - x0)
                        val ch = min(CORE, h - y0)
                        for (yy in 0 until SIZE) {
                            val sy = (y0 - MARGIN + yy).coerceIn(0, h - 1)
                            for (xx in 0 until SIZE) {
                                val p = pixels[sy * w + (x0 - MARGIN + xx).coerceIn(0, w - 1)]
                                val i = yy * SIZE + xx
                                data[i] = ((p shr 16) and 0xFF) / 255f
                                data[plane + i] = ((p shr 8) and 0xFF) / 255f
                                data[2 * plane + i] = (p and 0xFF) / 255f
                            }
                        }
                        val shape = longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { tensor ->
                            ort.run(mapOf(inputName to tensor)).use { result ->
                                val o = (result[0] as OnnxTensor).floats()
                                val tile = IntArray(cw * ch)
                                for (yy in 0 until ch) {
                                    for (xx in 0 until cw) {
                                        val i = (MARGIN + yy) * SIZE + MARGIN + xx
                                        val alpha = pixels[(y0 + yy) * w + x0 + xx] ushr 24
                                        tile[yy * cw + xx] = pack(o[i], o[plane + i], o[2 * plane + i], alpha)
                                    }
                                }
                                out.setPixels(tile, 0, cw, x0, y0, cw, ch)
                            }
                        }
                        done++
                        onProgress(done / total.toFloat())
                    }
                }
                out
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }
}

// ======================================================================================
// Colorear: DDColor tiny (512x512 fijo, solo predice crominancia ab)
// ======================================================================================

object Colorizer {
    private const val FILE = "ddcolor_tiny_fp16.onnx"
    private const val SIZE = 512

    // sRGB <-> CIE Lab (D65) con la convencion de OpenCV en coma flotante: L 0..100 y
    // a, b aproximadamente -127..127. Es la que uso el modelo al entrenarse.
    private val toLinear = FloatArray(256) { i ->
        val c = i / 255f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
    private const val ENC_N = 4096
    private val fromLinear = FloatArray(ENC_N + 1) { i ->
        val v = i / ENC_N.toFloat()
        if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
    }

    private fun encode(v: Float): Float = fromLinear[(v.coerceIn(0f, 1f) * ENC_N).roundToInt()]
    private fun f(t: Float): Float = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
    private fun finv(t: Float): Float {
        val c = t * t * t
        return if (c > 0.008856f) c else (t - 16f / 116f) / 7.787f
    }

    private fun lightness(p: Int): Float {
        val r = toLinear[p shr 16 and 0xFF]
        val g = toLinear[p shr 8 and 0xFF]
        val b = toLinear[p and 0xFF]
        val y = 0.212671f * r + 0.715160f * g + 0.072169f * b
        return 116f * f(y) - 16f
    }

    suspend fun colorize(context: Context, src: Bitmap, onProgress: (Float) -> Unit = {}): Bitmap? =
        withContext(Dispatchers.Default) {
            val ort = AiRuntime.session(context.applicationContext, FILE) ?: return@withContext null
            try {
                val w = src.width
                val h = src.height
                val pixels = IntArray(w * h)
                src.getPixels(pixels, 0, w, 0, 0, w, h)
                onProgress(0.05f)

                // Entrada: la imagen a 512x512 convertida a gris a partir de su luminosidad L.
                val small = Bitmap.createScaledBitmap(src, SIZE, SIZE, true)
                val sp = IntArray(SIZE * SIZE)
                small.getPixels(sp, 0, SIZE, 0, 0, SIZE, SIZE)
                if (small !== src) small.recycle()
                val plane = SIZE * SIZE
                val data = FloatArray(3 * plane)
                for (i in sp.indices) {
                    val l = lightness(sp[i])
                    val gray = encode(finv((l + 16f) / 116f))
                    data[i] = gray
                    data[plane + i] = gray
                    data[2 * plane + i] = gray
                }
                currentCoroutineContext().ensureActive()
                val env = OrtEnvironment.getEnvironment()
                val ab: FloatArray = OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()),
                ).use { tensor ->
                    ort.run(mapOf(ort.inputNames.iterator().next() to tensor)).use { result ->
                        (result[0] as OnnxTensor).floats()
                    }
                }
                onProgress(0.7f)

                // Salida: ab a baja resolucion + L original a resolucion completa.
                val out = IntArray(w * h)
                val sx = if (w > 1) (SIZE - 1f) / (w - 1) else 0f
                val sy = if (h > 1) (SIZE - 1f) / (h - 1) else 0f
                for (y in 0 until h) {
                    if (y % 64 == 0) currentCoroutineContext().ensureActive()
                    val fy = y * sy
                    val y0 = fy.toInt()
                    val y1 = min(y0 + 1, SIZE - 1)
                    val ty = fy - y0
                    for (x in 0 until w) {
                        val p = pixels[y * w + x]
                        val a = p ushr 24
                        if (a == 0) continue
                        val fx = x * sx
                        val x0 = fx.toInt()
                        val x1 = min(x0 + 1, SIZE - 1)
                        val tx = fx - x0
                        fun bil(base: Int): Float {
                            val top = ab[base + y0 * SIZE + x0] * (1 - tx) + ab[base + y0 * SIZE + x1] * tx
                            val bot = ab[base + y1 * SIZE + x0] * (1 - tx) + ab[base + y1 * SIZE + x1] * tx
                            return top * (1 - ty) + bot * ty
                        }
                        val l = lightness(p)
                        val fyv = (l + 16f) / 116f
                        val fxv = fyv + bil(0) / 500f
                        val fzv = fyv - bil(plane) / 200f
                        val xx = 0.950456f * finv(fxv)
                        val yy = finv(fyv)
                        val zz = 1.088754f * finv(fzv)
                        val r = 3.240479f * xx - 1.537150f * yy - 0.498535f * zz
                        val g = -0.969256f * xx + 1.875992f * yy + 0.041556f * zz
                        val b = 0.055648f * xx - 0.204043f * yy + 1.057311f * zz
                        out[y * w + x] = pack(encode(r), encode(g), encode(b), a)
                    }
                }
                onProgress(0.95f)
                val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                result.setPixels(out, 0, w, 0, 0, w, h)
                onProgress(1f)
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }
}

// ======================================================================================
// Profundidad: Depth Anything V2 Small
// ======================================================================================

/** Mapa de profundidad relativa normalizado: 1 = lo mas cercano, 0 = lo mas lejano. */
class DepthMap(val width: Int, val height: Int, val values: FloatArray) {
    /** Valor bilineal en coordenadas (x, y) de una imagen de [imgW] x [imgH]. */
    fun sample(x: Float, y: Float, imgW: Int, imgH: Int): Float {
        val fx = (x / imgW * width - 0.5f).coerceIn(0f, width - 1f)
        val fy = (y / imgH * height - 0.5f).coerceIn(0f, height - 1f)
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = values[y0 * width + x0] * (1 - tx) + values[y0 * width + x1] * tx
        val bot = values[y1 * width + x0] * (1 - tx) + values[y1 * width + x1] * tx
        return top * (1 - ty) + bot * ty
    }
}

object DepthEstimator {
    private const val FILE = "depth_anything_v2_small_q.onnx"
    private const val LONG_SIDE = 518
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    suspend fun estimate(context: Context, src: Bitmap, onProgress: (Float) -> Unit = {}): DepthMap? =
        withContext(Dispatchers.Default) {
            val ort = AiRuntime.session(context.applicationContext, FILE) ?: return@withContext null
            try {
                // El modelo pide lados multiplos de 14.
                val k = LONG_SIDE.toFloat() / max(src.width, src.height)
                val tw = max(14, (src.width * k / 14f).roundToInt() * 14)
                val th = max(14, (src.height * k / 14f).roundToInt() * 14)
                val small = Bitmap.createScaledBitmap(src, tw, th, true)
                val px = IntArray(tw * th)
                small.getPixels(px, 0, tw, 0, 0, tw, th)
                if (small !== src) small.recycle()
                val plane = tw * th
                val data = FloatArray(3 * plane)
                for (i in px.indices) {
                    val p = px[i]
                    data[i] = (((p shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]
                    data[plane + i] = (((p shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]
                    data[2 * plane + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
                }
                onProgress(0.1f)
                currentCoroutineContext().ensureActive()
                val env = OrtEnvironment.getEnvironment()
                val raw = OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(data), longArrayOf(1, 3, th.toLong(), tw.toLong()),
                ).use { tensor ->
                    ort.run(mapOf("pixel_values" to tensor)).use { (it[0] as OnnxTensor).floats() }
                }
                onProgress(0.95f)
                // Normaliza con percentiles 2-98 %: un par de pixeles raros no deben aplastar
                // el resto del rango.
                val sorted = raw.copyOf().also { it.sort() }
                val lo = sorted[(sorted.size * 0.02f).toInt()]
                val hi = sorted[((sorted.size - 1) * 0.98f).toInt()]
                val range = max(1e-6f, hi - lo)
                val norm = FloatArray(raw.size) { ((raw[it] - lo) / range).coerceIn(0f, 1f) }
                onProgress(1f)
                DepthMap(tw, th, norm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }
}

// ======================================================================================
// Segmentacion por toque: MobileSAM (codificador una vez, decodificador en cada toque)
// ======================================================================================

/** Imagen ya codificada por el modelo; los toques se resuelven contra ella al instante. */
class SamSession(
    val embedding: FloatArray,
    /** Tamano de la imagen reducida con la que se codifico (lado mayor 1024). */
    val workW: Int,
    val workH: Int,
    /** Tamano de la imagen original, en el que se expresan los toques. */
    val origW: Int,
    val origH: Int,
)

/** Mascara 0..255 de [width] x [height]. */
class SamMask(
    val width: Int,
    val height: Int,
    val coverage: ByteArray,
    /** Confianza del modelo (IoU predicho, ~0..1): baja cuando en la zona no hay un objeto claro. */
    val score: Float = 1f,
)

object Segmenter {
    private const val ENCODER = "mobile_sam_image_encoder.onnx"
    private const val DECODER = "sam_mask_decoder_single.onnx"
    private const val LONG_SIDE = 1024

    suspend fun encode(context: Context, src: Bitmap, onProgress: (Float) -> Unit = {}): SamSession? =
        withContext(Dispatchers.Default) {
            val enc = AiRuntime.session(context.applicationContext, ENCODER) ?: return@withContext null
            AiRuntime.session(context.applicationContext, DECODER) ?: return@withContext null
            try {
                val k = LONG_SIDE.toFloat() / max(src.width, src.height)
                val ww = max(1, (src.width * k).roundToInt())
                val wh = max(1, (src.height * k).roundToInt())
                val small = Bitmap.createScaledBitmap(src, ww, wh, true)
                val px = IntArray(ww * wh)
                small.getPixels(px, 0, ww, 0, 0, ww, wh)
                if (small !== src) small.recycle()
                // El codificador exportado ya normaliza y rellena a 1024: espera HWC en
                // 0..255, RGB.
                val data = FloatArray(ww * wh * 3)
                for (i in px.indices) {
                    val p = px[i]
                    data[i * 3] = ((p shr 16) and 0xFF).toFloat()
                    data[i * 3 + 1] = ((p shr 8) and 0xFF).toFloat()
                    data[i * 3 + 2] = (p and 0xFF).toFloat()
                }
                onProgress(0.1f)
                currentCoroutineContext().ensureActive()
                val env = OrtEnvironment.getEnvironment()
                val emb = OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(data), longArrayOf(wh.toLong(), ww.toLong(), 3),
                ).use { tensor ->
                    enc.run(mapOf("input_image" to tensor)).use { (it[0] as OnnxTensor).floats() }
                }
                onProgress(1f)
                SamSession(emb, ww, wh, src.width, src.height)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }

    /**
     * Mascara del objeto indicado por [points] (x, y en pixeles de la imagen original) con
     * sus [labels]: 1 = es parte del objeto, 0 = no lo es.
     */
    suspend fun decode(
        context: Context,
        session: SamSession,
        points: List<FloatArray>,
        labels: List<Int>,
        /** Zona (x0, y0, x1, y1 en pixeles de la imagen original): el modelo busca el objeto dentro. */
        box: FloatArray? = null,
    ): SamMask? = withContext(Dispatchers.Default) {
        val dec = AiRuntime.session(context.applicationContext, DECODER) ?: return@withContext null
        try {
            // Con caja, sus dos esquinas van como puntos con etiquetas 2 y 3 y no hace falta
            // relleno; sin caja, el decodificador pide un punto de relleno con etiqueta -1.
            val head = if (box != null) 2 else 0
            val n = head + points.size + (if (box == null) 1 else 0)
            val coords = FloatArray(n * 2)
            val lab = FloatArray(n)
            if (box != null) {
                coords[0] = box[0] * session.workW / session.origW
                coords[1] = box[1] * session.workH / session.origH
                coords[2] = box[2] * session.workW / session.origW
                coords[3] = box[3] * session.workH / session.origH
                lab[0] = 2f
                lab[1] = 3f
            }
            for (i in points.indices) {
                coords[(head + i) * 2] = points[i][0] * session.workW / session.origW
                coords[(head + i) * 2 + 1] = points[i][1] * session.workH / session.origH
                lab[head + i] = labels[i].toFloat()
            }
            if (box == null) lab[n - 1] = -1f
            val env = OrtEnvironment.getEnvironment()
            val tensors = mutableListOf<OnnxTensor>()
            try {
                fun t(data: FloatArray, vararg shape: Long): OnnxTensor =
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).also { tensors += it }
                val inputs = mapOf(
                    "image_embeddings" to t(session.embedding, 1, 256, 64, 64),
                    "point_coords" to t(coords, 1, n.toLong(), 2),
                    "point_labels" to t(lab, 1, n.toLong()),
                    "mask_input" to t(FloatArray(256 * 256), 1, 1, 256, 256),
                    "has_mask_input" to t(floatArrayOf(0f), 1),
                    "orig_im_size" to t(floatArrayOf(session.workH.toFloat(), session.workW.toFloat()), 2),
                )
                dec.run(inputs).use { result ->
                    val logits = (result[0] as OnnxTensor).floats()
                    // De logit a cobertura: borde suave entre -4 y 4, 128 justo en 0.
                    val cov = ByteArray(session.workW * session.workH) { i ->
                        ((logits[i] / 8f + 0.5f).coerceIn(0f, 1f) * 255f).toInt().toByte()
                    }
                    val iou = (result[1] as OnnxTensor).floats().firstOrNull() ?: 1f
                    SamMask(session.workW, session.workH, cov, iou)
                }
            } finally {
                tensors.forEach { it.close() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }
}

// ======================================================================================
// Eliminar objetos: MI-GAN
// ======================================================================================

object Inpainter {
    private const val FILE = "migan_pipeline.onnx"

    // Lado mayor de trabajo del recorte. Por encima se reduce, se rellena y se vuelve a
    // ampliar solo la zona rellenada: el resto de la imagen no se toca.
    private const val WORK = 640

    /**
     * Rellena la zona marcada en [hole] (cobertura 0..255 del tamano de [src]) con lo que
     * el modelo cree que habia debajo. Devuelve una imagen nueva del mismo tamano.
     */
    suspend fun inpaint(
        context: Context,
        src: Bitmap,
        hole: ByteArray,
        onProgress: (Float) -> Unit = {},
    ): Bitmap? = withContext(Dispatchers.Default) {
        val ort = AiRuntime.session(context.applicationContext, FILE) ?: return@withContext null
        try {
            val w = src.width
            val h = src.height
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) for (x in 0 until w) {
                if ((hole[y * w + x].toInt() and 0xFF) >= 128) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < 0) return@withContext null
            onProgress(0.05f)

            // Recorte con margen de contexto: el modelo rellena mejor viendo alrededor.
            val margin = max(96, (0.4f * max(maxX - minX, maxY - minY)).roundToInt())
            val cx0 = max(0, minX - margin)
            val cy0 = max(0, minY - margin)
            val cx1 = min(w, maxX + 1 + margin)
            val cy1 = min(h, maxY + 1 + margin)
            val cw = cx1 - cx0
            val ch = cy1 - cy0

            // Zona a rellenar algo dilatada, para tapar el halo del borde del objeto.
            val grow = max(2, (max(cw, ch) * 0.006f).roundToInt())
            val holeCrop = ByteArray(cw * ch)
            for (y in 0 until ch) for (x in 0 until cw) {
                holeCrop[y * cw + x] = hole[(cy0 + y) * w + cx0 + x]
            }
            val dil = dilate(holeCrop, cw, ch, grow)

            val scale = min(1f, WORK.toFloat() / max(cw, ch))
            val ww = max(8, (cw * scale / 8f).roundToInt() * 8)
            val wh = max(8, (ch * scale / 8f).roundToInt() * 8)
            val crop = Bitmap.createBitmap(src, cx0, cy0, cw, ch)
            val workBmp = Bitmap.createScaledBitmap(crop, ww, wh, true)
            val maskBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888).also { m ->
                m.setPixels(IntArray(cw * ch) { i -> 0xFF000000.toInt() or ((dil[i].toInt() and 0xFF) * 0x010101) }, 0, cw, 0, 0, cw, ch)
            }
            val workMask = Bitmap.createScaledBitmap(maskBmp, ww, wh, true)

            val ip = IntArray(ww * wh)
            workBmp.getPixels(ip, 0, ww, 0, 0, ww, wh)
            val mp = IntArray(ww * wh)
            workMask.getPixels(mp, 0, ww, 0, 0, ww, wh)
            // createScaledBitmap/createBitmap devuelven el mismo objeto si no hay cambio de
            // tamano: se libera solo lo que de verdad es una copia, y nunca [src].
            if (workBmp !== crop) workBmp.recycle()
            if (workMask !== maskBmp) workMask.recycle()
            maskBmp.recycle()
            val plane = ww * wh
            val img = ByteArray(3 * plane)
            val msk = ByteArray(plane)
            for (i in 0 until plane) {
                val p = ip[i]
                img[i] = (p shr 16).toByte()
                img[plane + i] = (p shr 8).toByte()
                img[2 * plane + i] = p.toByte()
                // Convencion del modelo: 255 = pixel conocido, 0 = hueco.
                msk[i] = if ((mp[i] and 0xFF) >= 64) 0 else 255.toByte()
            }
            onProgress(0.2f)
            currentCoroutineContext().ensureActive()

            val env = OrtEnvironment.getEnvironment()
            val outBytes = OnnxTensor.createTensor(
                env, ByteBuffer.wrap(img), longArrayOf(1, 3, wh.toLong(), ww.toLong()), OnnxJavaType.UINT8,
            ).use { imgT ->
                OnnxTensor.createTensor(
                    env, ByteBuffer.wrap(msk), longArrayOf(1, 1, wh.toLong(), ww.toLong()), OnnxJavaType.UINT8,
                ).use { mskT ->
                    ort.run(mapOf("image" to imgT, "mask" to mskT)).use { result ->
                        val bb = (result[0] as OnnxTensor).byteBuffer
                        ByteArray(bb.remaining()).also { bb.get(it) }
                    }
                }
            }
            onProgress(0.85f)

            val filled = IntArray(plane) { i ->
                (0xFF shl 24) or ((outBytes[i].toInt() and 0xFF) shl 16) or
                    ((outBytes[plane + i].toInt() and 0xFF) shl 8) or (outBytes[2 * plane + i].toInt() and 0xFF)
            }
            var filledBmp = Bitmap.createBitmap(ww, wh, Bitmap.Config.ARGB_8888)
            filledBmp.setPixels(filled, 0, ww, 0, 0, ww, wh)
            if (ww != cw || wh != ch) {
                val big = Bitmap.createScaledBitmap(filledBmp, cw, ch, true)
                filledBmp.recycle()
                filledBmp = big
            }
            val fp = IntArray(cw * ch)
            filledBmp.getPixels(fp, 0, cw, 0, 0, cw, ch)
            filledBmp.recycle()

            // Mezcla con borde suave: dentro de la zona dilatada manda el resultado del
            // modelo, y en el borde se funde con la imagen original.
            val alpha = softenAlpha(dil, cw, ch, max(1, grow / 2))
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val full = IntArray(w * h)
            src.getPixels(full, 0, w, 0, 0, w, h)
            for (y in 0 until ch) for (x in 0 until cw) {
                val a = alpha[y * cw + x].toInt() and 0xFF
                if (a == 0) continue
                val o = full[(cy0 + y) * w + cx0 + x]
                val f = fp[y * cw + x]
                val t = a / 255f
                val r = ((o shr 16 and 0xFF) + ((f shr 16 and 0xFF) - (o shr 16 and 0xFF)) * t).roundToInt()
                val g = ((o shr 8 and 0xFF) + ((f shr 8 and 0xFF) - (o shr 8 and 0xFF)) * t).roundToInt()
                val b = ((o and 0xFF) + ((f and 0xFF) - (o and 0xFF)) * t).roundToInt()
                full[(cy0 + y) * w + cx0 + x] = (o and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(full, 0, w, 0, 0, w, h)
            if (crop !== src) crop.recycle()
            onProgress(1f)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    /** Dilatacion binaria por maximo en ventana cuadrada, separable. */
    private fun dilate(m: ByteArray, w: Int, h: Int, r: Int): ByteArray {
        val tmp = ByteArray(m.size)
        for (y in 0 until h) for (x in 0 until w) {
            var best = 0
            for (k in max(0, x - r)..min(w - 1, x + r)) best = max(best, m[y * w + k].toInt() and 0xFF)
            tmp[y * w + x] = best.toByte()
        }
        val out = ByteArray(m.size)
        for (y in 0 until h) for (x in 0 until w) {
            var best = 0
            for (k in max(0, y - r)..min(h - 1, y + r)) best = max(best, tmp[k * w + x].toInt() and 0xFF)
            out[y * w + x] = best.toByte()
        }
        return out
    }

    /** Desenfoque de caja de la mascara (dos pasadas) para que el borde no tenga escalon. */
    private fun softenAlpha(m: ByteArray, w: Int, h: Int, r: Int): ByteArray {
        var a = m
        repeat(2) {
            val tmp = ByteArray(a.size)
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0; var n = 0
                for (k in max(0, x - r)..min(w - 1, x + r)) { s += a[y * w + k].toInt() and 0xFF; n++ }
                tmp[y * w + x] = (s / n).toByte()
            }
            val out = ByteArray(a.size)
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0; var n = 0
                for (k in max(0, y - r)..min(h - 1, y + r)) { s += tmp[k * w + x].toInt() and 0xFF; n++ }
                out[y * w + x] = (s / n).toByte()
            }
            a = out
        }
        return a
    }
}

// ======================================================================================
// Mejorar caras: GFPGAN 1.4 (entrada y salida 512x512 RGB en -1..1, cara ya alineada)
// ======================================================================================

object FaceRestorer {
    private const val FILE = "gfpgan_v1_4.onnx"
    const val SIZE = 512

    /** Restaura una cara alineada de [SIZE] x [SIZE]. La alineacion la hace el llamante. */
    suspend fun restore(context: Context, face: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        val ort = AiRuntime.session(context.applicationContext, FILE) ?: return@withContext null
        try {
            val px = IntArray(SIZE * SIZE)
            face.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            val plane = SIZE * SIZE
            val data = FloatArray(3 * plane)
            for (i in px.indices) {
                val p = px[i]
                data[i] = ((p shr 16) and 0xFF) / 127.5f - 1f
                data[plane + i] = ((p shr 8) and 0xFF) / 127.5f - 1f
                data[2 * plane + i] = (p and 0xFF) / 127.5f - 1f
            }
            currentCoroutineContext().ensureActive()
            val env = OrtEnvironment.getEnvironment()
            val o = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()),
            ).use { tensor ->
                ort.run(mapOf(ort.inputNames.iterator().next() to tensor)).use { (it[0] as OnnxTensor).floats() }
            }
            val out = IntArray(plane) { i ->
                pack((o[i] + 1f) / 2f, (o[plane + i] + 1f) / 2f, (o[2 * plane + i] + 1f) / 2f)
            }
            Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also {
                it.setPixels(out, 0, SIZE, 0, 0, SIZE, SIZE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }
}
