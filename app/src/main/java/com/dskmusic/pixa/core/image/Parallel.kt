package com.dskmusic.pixa.core.image

import java.util.stream.IntStream
import kotlin.math.min

/**
 * Reparte un bucle de filas (o de indices) entre los nucleos del procesador.
 *
 * Cada franja [y0, y1) se procesa en un hilo; el cuerpo debe escribir solo en su franja y
 * leer de datos que no cambian (los efectos por pixel cumplen esto). Con imagenes pequenas
 * o un solo nucleo se ejecuta directamente, sin coste de reparto.
 */
internal object Parallel {

    private val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

    fun rows(n: Int, body: (y0: Int, y1: Int) -> Unit) {
        if (cores <= 1 || n < 64) {
            body(0, n)
            return
        }
        // Mas franjas que hilos: si una es mas lenta, las demas la compensan.
        val bands = cores * 3
        val step = (n + bands - 1) / bands
        IntStream.range(0, bands).parallel().forEach { b ->
            val from = b * step
            val to = min(n, from + step)
            if (from < to) body(from, to)
        }
    }
}
