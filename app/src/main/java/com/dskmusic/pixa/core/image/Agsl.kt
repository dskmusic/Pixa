package com.dskmusic.pixa.core.image

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import com.dskmusic.pixa.core.bitmap.BitmapUtils

/**
 * Puente opcional a AGSL (requisito 13).
 *
 * RuntimeShader existe desde API 33 y es muchisimo mas rapido que recorrer pixeles en
 * Kotlin para los efectos que son puramente por pixel. Pero no es obligatorio: si el
 * dispositivo es anterior, o si la compilacion del shader falla en algun fabricante
 * concreto, [run] devuelve null y quien llama usa su implementacion en CPU.
 *
 * ponytail: un unico punto de entrada con un solo uniform de imagen y un mapa de floats
 * cubre todos los efectos de la app; si algun dia hace falta pasar matrices o varias
 * texturas, se amplia aqui y no en cada efecto.
 */
object Agsl {

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @Volatile
    private var warned = false

    /**
     * Ejecuta [source] sobre [src] y devuelve un bitmap nuevo, o null si no se puede.
     * El shader recibe la imagen en el uniform `image` y los valores de [floats] por nombre.
     */
    fun run(src: Bitmap, source: String, floats: Map<String, FloatArray>): Bitmap? {
        if (!isSupported) return null
        return try {
            runInternal(src, source, floats)
        } catch (e: RuntimeException) {
            // Shader no compilado o no soportado: el llamante hace el fallback en CPU.
            if (!warned) {
                warned = true
                android.util.Log.w("Agsl", "AGSL no disponible, se usa la CPU", e)
            }
            null
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun runInternal(src: Bitmap, source: String, floats: Map<String, FloatArray>): Bitmap {
        val shader = RuntimeShader(source)
        shader.setInputShader(
            "image",
            BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
        )
        shader.setFloatUniform("size", src.width.toFloat(), src.height.toFloat())
        for ((name, value) in floats) {
            when (value.size) {
                1 -> shader.setFloatUniform(name, value[0])
                2 -> shader.setFloatUniform(name, value[0], value[1])
                3 -> shader.setFloatUniform(name, value[0], value[1], value[2])
                4 -> shader.setFloatUniform(name, value[0], value[1], value[2], value[3])
                else -> shader.setFloatUniform(name, value)
            }
        }
        val out = BitmapUtils.create(src.width, src.height)
        val paint = Paint().apply { this.shader = shader }
        Canvas(out).drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        return out
    }
}
