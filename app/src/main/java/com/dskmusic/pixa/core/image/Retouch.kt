package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlin.math.roundToInt

/**
 * Retoque y privacidad (requisito 14): tampon de clonar, pinceles de desenfocar y
 * enfocar, ojos rojos, y pixelar o desenfocar zonas (tambien caras).
 *
 * Todos los pinceles siguen el mismo patron: se genera la version modificada de la imagen
 * completa y se vuelca solo por donde pasa el trazo. Es mas sencillo que procesar pixel a
 * pixel bajo el pincel y, al reutilizar el mismo codigo de efectos, el resultado es
 * identico al del panel de ajustes.
 */
object Retouch {

    enum class CensorStyle { BAR, PIXELATE, BLUR }

    /** Pixela toda la imagen con bloques de [blockSize] pixeles. */
    fun pixelate(src: Bitmap, blockSize: Int): Bitmap {
        val block = blockSize.coerceAtLeast(2)
        val w = (src.width / block).coerceAtLeast(1)
        val h = (src.height / block).coerceAtLeast(1)
        val small = BitmapUtils.scale(src, w, h)
        val out = BitmapUtils.scaleNearest(small, src.width, src.height)
        if (small !== src) small.recycle()
        return out
    }

    /** Pixela solo un rectangulo, dejando el resto intacto. */
    fun pixelateRegion(src: Bitmap, region: Rect, blockSize: Int): Bitmap {
        val rect = clamp(region, src.width, src.height) ?: return BitmapUtils.mutableCopy(src)
        val out = BitmapUtils.mutableCopy(src)
        val patch = BitmapUtils.crop(src, rect)
        val pixelated = pixelate(patch, blockSize)
        Canvas(out).drawBitmap(pixelated, rect.left.toFloat(), rect.top.toFloat(), null)
        patch.recycle()
        pixelated.recycle()
        return out
    }

    /** Desenfoca solo un rectangulo. */
    fun blurRegion(src: Bitmap, region: Rect, radius: Float): Bitmap {
        val rect = clamp(region, src.width, src.height) ?: return BitmapUtils.mutableCopy(src)
        val out = BitmapUtils.mutableCopy(src)
        val patch = BitmapUtils.crop(src, rect)
        val blurred = Blur.blur(patch, radius)
        Canvas(out).drawBitmap(blurred, rect.left.toFloat(), rect.top.toFloat(), null)
        patch.recycle()
        blurred.recycle()
        return out
    }

    /**
     * Genera el parche que tapa una zona (requisito 14).
     *
     * Devuelve un bitmap del tamano del rectangulo para que la UI lo monte como capa
     * independiente: asi la censura se puede mover, ajustar o quitar despues, que es lo que
     * pide el requisito en vez de quemarla en la foto.
     */
    fun censorPatch(
        src: Bitmap,
        region: Rect,
        style: CensorStyle,
        barColor: Int = Color.BLACK,
        strength: Float = 0.6f,
    ): Bitmap? {
        val rect = clamp(region, src.width, src.height) ?: return null
        return when (style) {
            CensorStyle.BAR -> {
                val out = BitmapUtils.create(rect.width(), rect.height())
                Canvas(out).drawColor(barColor)
                out
            }
            CensorStyle.PIXELATE -> {
                val patch = BitmapUtils.crop(src, rect)
                // El tamano de bloque va con el tamano de la cara: una barra de 12 px en
                // una cara pequena no taparia nada y en una grande se veria el rostro.
                val block = (minOf(rect.width(), rect.height()) *
                    (0.06f + 0.14f * strength.coerceIn(0f, 1f))).roundToInt().coerceAtLeast(3)
                val out = pixelate(patch, block)
                patch.recycle()
                out
            }
            CensorStyle.BLUR -> {
                val patch = BitmapUtils.crop(src, rect)
                val radius = minOf(rect.width(), rect.height()) *
                    (0.05f + 0.15f * strength.coerceIn(0f, 1f))
                val out = Blur.blur(patch, radius.coerceAtLeast(2f))
                patch.recycle()
                out
            }
        }
    }

    private fun clamp(region: Rect, width: Int, height: Int): Rect? {
        val rect = Rect(region)
        if (!rect.intersect(0, 0, width, height)) return null
        if (rect.width() < 2 || rect.height() < 2) return null
        return rect
    }

    // ----------------------------------------------------------------------------------
    // Pinceles
    // ----------------------------------------------------------------------------------

    /**
     * Tampon de clonar: copia pixeles desde un punto de origen desplazado.
     *
     * [offsetX] y [offsetY] son la diferencia entre el punto de origen marcado y donde se
     * empezo a pintar, asi que el origen se arrastra con el pincel igual que en Photoshop.
     */
    fun cloneStroke(
        target: Bitmap,
        source: Bitmap,
        stroke: Path,
        offsetX: Float,
        offsetY: Float,
        radius: Float,
        hardness: Float = 0.7f,
        opacity: Float = 1f,
    ) {
        val patch = BitmapUtils.create(target.width, target.height)
        Canvas(patch).drawBitmap(source, offsetX, offsetY, Paint(Paint.FILTER_BITMAP_FLAG))
        stampThroughStroke(target, patch, stroke, radius, hardness, opacity)
        patch.recycle()
    }

    /** Pincel de pixelar, para tapar datos a mano (requisito 14). */
    fun pixelateStroke(
        target: Bitmap,
        stroke: Path,
        radius: Float,
        blockSize: Int,
        hardness: Float = 0.6f,
    ) {
        val blocks = pixelate(target, blockSize)
        stampThroughStroke(target, blocks, stroke, radius, hardness, 1f)
        blocks.recycle()
    }

    /** Pincel de desenfocar. */
    fun blurStroke(
        target: Bitmap,
        stroke: Path,
        radius: Float,
        amount: Float = 0.6f,
        hardness: Float = 0.6f,
    ) {
        val blurred = Blur.blur(target, (radius * amount.coerceIn(0.05f, 1f)).coerceAtLeast(1.5f))
        stampThroughStroke(target, blurred, stroke, radius, hardness, 1f)
        blurred.recycle()
    }

    /** Pincel de enfocar. */
    fun sharpenStroke(
        target: Bitmap,
        stroke: Path,
        radius: Float,
        amount: Float = 0.6f,
        hardness: Float = 0.6f,
    ) {
        val sharp = Adjust.sharpen(target, amount.coerceIn(0f, 1f))
        stampThroughStroke(target, sharp, stroke, radius, hardness, 1f)
        sharp.recycle()
    }

    /**
     * Vuelca [patch] sobre [target] solo por donde pasa el trazo.
     *
     * El trazo se dibuja en una mascara aparte y recorta el parche con DST_IN; eso da el
     * borde suave del pincel sin tener que pintar el parche a mano.
     */
    private fun stampThroughStroke(
        target: Bitmap,
        patch: Bitmap,
        stroke: Path,
        radius: Float,
        hardness: Float,
        opacity: Float,
    ) {
        val mask = BitmapUtils.create(target.width, target.height)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
            val soft = (1f - hardness.coerceIn(0f, 1f)) * radius
            if (soft > 0.5f) maskFilter = BlurMaskFilter(soft, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(mask).drawPath(stroke, maskPaint)

        val clipped = BitmapUtils.mutableCopy(patch)
        Canvas(clipped).drawBitmap(
            mask, 0f, 0f,
            Paint().apply { blendMode = android.graphics.BlendMode.DST_IN },
        )
        Canvas(target).drawBitmap(
            clipped, 0f, 0f,
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (opacity.coerceIn(0f, 1f) * 255).roundToInt()
            },
        )
        clipped.recycle()
        mask.recycle()
    }

    // ----------------------------------------------------------------------------------
    // Ojos rojos
    // ----------------------------------------------------------------------------------

    /**
     * Corrige ojos rojos dentro de un circulo.
     *
     * Solo toca los pixeles donde el rojo domina claramente sobre verde y azul, y los
     * sustituye por su propio brillo: asi el iris conserva textura en vez de quedar como
     * una mancha gris.
     */
    fun fixRedEye(src: Bitmap, area: RectF, strength: Float = 1f): Bitmap {
        val rect = clamp(
            Rect(
                area.left.roundToInt(), area.top.roundToInt(),
                area.right.roundToInt(), area.bottom.roundToInt(),
            ),
            src.width, src.height,
        ) ?: return BitmapUtils.mutableCopy(src)

        val out = BitmapUtils.mutableCopy(src)
        val w = rect.width()
        val h = rect.height()
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, rect.left, rect.top, w, h)
        val cx = w / 2f
        val cy = h / 2f
        val rx = w / 2f
        val ry = h / 2f
        val k = strength.coerceIn(0f, 1f)

        Parallel.rows(h) { y0, y1 ->
            for (y in y0 until y1) {
            for (x in 0 until w) {
                val nx = (x - cx) / rx
                val ny = (y - cy) / ry
                if (nx * nx + ny * ny > 1f) continue
                val c = px[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val maxGb = maxOf(g, b)
                if (r <= maxGb * 1.4f || r < 60) continue
                val target = ((g + b) / 2f).roundToInt().coerceIn(0, 255)
                val newR = (r + (target - r) * k).roundToInt().coerceIn(0, 255)
                px[y * w + x] = (c and 0xFF00FFFF.toInt()) or (newR shl 16)
            }
            }
        }
        out.setPixels(px, 0, w, rect.left, rect.top, w, h)
        return out
    }
}
