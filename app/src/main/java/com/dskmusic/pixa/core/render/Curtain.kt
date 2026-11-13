package com.dskmusic.pixa.core.render

import android.graphics.Path
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import kotlin.math.max

/**
 * Cortina de comparacion antes/despues: a la izquierda de [split] (0..1 del ancho) se ve
 * el documento original y a la derecha la vista previa, con una linea divisoria.
 *
 * Se compone sobre el propio bitmap del lienzo, asi que zoom, desplazamiento y giro de
 * la vista funcionan sin tocar nada mas. Libera [before] y [after]; devuelve uno nuevo.
 * Si los tamanos no coinciden devuelve [after] tal cual.
 */
object Curtain {

    fun compose(before: Bitmap, after: Bitmap, split: Float): Bitmap {
        if (before.width != after.width || before.height != after.height) {
            before.recycle()
            return after
        }
        val out = BitmapUtils.mutableCopy(after)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val x = (split.coerceIn(0f, 1f) * w)
        val canvas = Canvas(out)

        canvas.save()
        canvas.clipRect(0f, 0f, x, h)
        // Se borra antes de pintar el original: si no, las zonas transparentes del
        // original dejarian ver el resultado debajo y la comparacion mentiria.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(before, 0f, 0f, null)
        canvas.restore()

        val stroke = max(2f, w / 450f)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = stroke
            setShadowLayer(stroke * 1.5f, 0f, 0f, 0xAA000000.toInt())
        }
        canvas.drawLine(x, 0f, x, h, line)
        // Tirador grande: se agarra con el dedo, asi que mejor generoso.
        val knob = max(20f, w / 26f)
        canvas.drawCircle(x, h / 2f, knob, line)
        line.clearShadowLayer()
        line.color = 0xFF222222.toInt()
        // Dos flechas a los lados indican que se arrastra en horizontal.
        val a = knob * 0.5f
        val tri = Path()
        tri.moveTo(x - knob * 0.2f, h / 2f - a * 0.7f)
        tri.lineTo(x - knob * 0.2f - a * 0.7f, h / 2f)
        tri.lineTo(x - knob * 0.2f, h / 2f + a * 0.7f)
        tri.close()
        tri.moveTo(x + knob * 0.2f, h / 2f - a * 0.7f)
        tri.lineTo(x + knob * 0.2f + a * 0.7f, h / 2f)
        tri.lineTo(x + knob * 0.2f, h / 2f + a * 0.7f)
        tri.close()
        canvas.drawPath(tri, line)

        before.recycle()
        after.recycle()
        return out
    }
}
