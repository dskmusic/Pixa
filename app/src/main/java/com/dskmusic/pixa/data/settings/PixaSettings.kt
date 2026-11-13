package com.dskmusic.pixa.data.settings

import com.dskmusic.pixa.ui.theme.ThemeMode

/** Idioma de la app (requisito 1). AUTO sigue al sistema. */
enum class AppLanguage(val tag: String?) {
    AUTO(null),
    SPANISH("es"),
    ENGLISH("en"),
}

/** Lado preferente para barra de herramientas y boton de capas (requisito 1). */
enum class Handedness { RIGHT, LEFT }

/**
 * Estado completo de ajustes. Es un unico objeto inmutable que viaja por un Flow,
 * de modo que cualquier cambio se aplica al instante en toda la UI.
 */
data class PixaSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: Int = 0xFFFFFFFF.toInt(),
    val language: AppLanguage = AppLanguage.AUTO,
    val handedness: Handedness = Handedness.RIGHT,
    val haptics: Boolean = true,
    /** Preguntar antes de descartar los cambios sin aplicar de un panel. */
    val confirmDiscard: Boolean = true,
    /** Pedir confirmacion antes de eliminar una LUT personal (si no, se borra directamente). */
    val confirmLutDelete: Boolean = true,
    /** Con acento blanco, el logo cicla colores vivos (si no, no se aprecia la animacion). */
    val whiteLogoCycle: Boolean = true,
    /** La cortina antes/despues empieza activa al entrar en ajustes, filtros y efectos. */
    val curtainByDefault: Boolean = false,
    /** Aumento de la lupa (veces). */
    val magnifierZoom: Float = 2.5f,
    /** La lupa se aparta del dedo al acercarse; si no, queda fija arriba a la derecha. */
    val magnifierAvoid: Boolean = true,
    /** Esquina de reposo (o fija) de la lupa: 0 sup. izq., 1 sup. der., 2 inf. izq., 3 inf. der. */
    val magnifierCorner: Int = 3,
    /** Forma de la lupa: 0 circulo, 1 cuadrado, 2 rectangulo. */
    val magnifierShape: Int = 0,
    /** Tamano de la lupa respecto al normal (0,5 a 1,6). */
    val magnifierSize: Float = 1f,
    /** Margen (en radios de lupa) alrededor de ella antes de apartarla. */
    val magnifierMargin: Float = 0.25f,
    val magnifier: Boolean = false,
    /** Tamano de la mira de la lupa en seleccion (0,5 a 2 veces). */
    val lensCrossSize: Float = 1f,
    /** Grosor de la mira de la lupa en seleccion (0,5 a 3 veces). */
    val lensCrossWidth: Float = 1f,
    /** Grosor de la linea punteada de la seleccion (0,5 a 3 veces). */
    val selectionLineWidth: Float = 1f,
    val gridByDefault: Boolean = false,
    val snapping: Boolean = true,
    val maxResolution: Int = 4096,
    val autosave: Boolean = true,
    val historyLimit: Int = 40,
    /** Reajustar el lienzo al abrir/cerrar un panel inferior (requisito del usuario). */
    val refitOnPanel: Boolean = true,
    /** Vista al abrir / cerrar paneles: 0 encajar el lienzo, 1 no tocar el zoom, 2 (defecto) encajar el objeto elegido. */
    val panelViewMode: Int = 2,
    /** Bloquear la app en vertical (móviles). El default real depende del dispositivo. */
    val lockOrientation: Boolean = true,
    val tutorialDone: Boolean = false,
    val hintProjectVsImageShown: Boolean = false,
    /** Paleta global de colores guardados, disponible en todas las herramientas. */
    val palette: List<Int> = emptyList(),
    /** Colores usados recientemente, se alimenta solo. */
    val recentColors: List<Int> = emptyList(),
    val lastProjectId: String? = null,
) {
    companion object {
        val RESOLUTION_OPTIONS = listOf(2048, 4096, 8192)
        val HISTORY_OPTIONS = listOf(20, 40, 80, 150)
        const val MAX_PALETTE = 48
        const val MAX_RECENT = 20
    }
}
