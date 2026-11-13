package com.dskmusic.pixa.core.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.model.FillSpec
import com.dskmusic.pixa.core.model.GradientKind
import com.dskmusic.pixa.core.model.Xf
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Traduce un [FillSpec] a un Paint listo para pintar dentro de un rectangulo de
 * [width] x [height] con origen en (0,0). Lo usan el texto (requisito 10), las formas
 * (requisito 11) y las capas de relleno (requisito 7).
 */
object FillPainter {

    /** Paint configurado con el relleno. Devuelve null si el relleno no se puede resolver. */
    fun paintFor(
        fill: FillSpec?,
        width: Float,
        height: Float,
        store: BitmapStore?,
    ): Paint? {
        if (fill == null) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (fill) {
            is FillSpec.Solid -> paint.color = fill.color
            is FillSpec.Gradient -> paint.shader = gradientShader(fill, width, height)
            is FillSpec.Image -> {
                val bmp = store?.get(fill.bitmapId) ?: return null
                paint.shader = imageShader(bmp, width, height, fill.inner)
                paint.isFilterBitmap = true
            }
        }
        return paint
    }

    fun gradientShader(spec: FillSpec.Gradient, width: Float, height: Float): Shader {
        val stops = spec.stops.sortedBy { it.position }
        val colors = IntArray(stops.size) { stops[it].color }
        val positions = FloatArray(stops.size) { stops[it].position.coerceIn(0f, 1f) }
        // Un degradado necesita al menos dos paradas; si hay una se duplica.
        if (colors.size < 2) {
            val c = if (colors.isEmpty()) 0 else colors[0]
            return LinearGradient(0f, 0f, width, 0f, c, c, Shader.TileMode.CLAMP)
        }

        val cx = width / 2f
        val cy = height / 2f
        return when (spec.kind) {
            GradientKind.LINEAR -> {
                // El eje del degradado pasa por el centro con el angulo pedido y se
                // extiende hasta cubrir la diagonal, asi nunca queda una banda plana.
                val rad = Math.toRadians(spec.angle.toDouble())
                val half = max(width, height) / 2f
                val dx = cos(rad).toFloat() * half
                val dy = sin(rad).toFloat() * half
                LinearGradient(
                    cx - dx, cy - dy, cx + dx, cy + dy,
                    colors, positions, Shader.TileMode.CLAMP,
                )
            }
            GradientKind.REFLECTED, GradientKind.REPEATING -> {
                // Eje corto (un cuarto del lado mayor) que se refleja o se repite.
                val rad = Math.toRadians(spec.angle.toDouble())
                val half = max(width, height) / 4f
                val dx = cos(rad).toFloat() * half
                val dy = sin(rad).toFloat() * half
                LinearGradient(
                    cx - dx, cy - dy, cx + dx, cy + dy, colors, positions,
                    if (spec.kind == GradientKind.REFLECTED) Shader.TileMode.MIRROR else Shader.TileMode.REPEAT,
                )
            }
            GradientKind.RADIAL_REPEAT -> RadialGradient(
                cx, cy, max(1f, max(width, height) / 4f),
                colors, positions, Shader.TileMode.REPEAT,
            )
            GradientKind.RADIAL -> RadialGradient(
                cx, cy, max(1f, max(width, height) / 2f),
                colors, positions, Shader.TileMode.CLAMP,
            )
            GradientKind.SWEEP -> SweepGradient(cx, cy, colors, positions).apply {
                if (spec.angle != 0f) {
                    setLocalMatrix(Matrix().apply { postRotate(spec.angle, cx, cy) })
                }
            }
        }
    }

    /**
     * Shader de imagen que cubre el rectangulo por defecto y admite encima la
     * transformacion interna que el usuario hace con dos dedos (requisitos 10 y 11).
     */
    fun imageShader(bmp: Bitmap, width: Float, height: Float, inner: Xf): BitmapShader {
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val cover = max(width / bmp.width, height / bmp.height)
        val m = Matrix()
        m.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        m.postScale(
            cover * inner.scaleX * (if (inner.flipH) -1f else 1f),
            cover * inner.scaleY * (if (inner.flipV) -1f else 1f),
        )
        if (inner.rotation != 0f) m.postRotate(inner.rotation)
        m.postTranslate(width / 2f + inner.tx, height / 2f + inner.ty)
        shader.setLocalMatrix(m)
        return shader
    }
}
