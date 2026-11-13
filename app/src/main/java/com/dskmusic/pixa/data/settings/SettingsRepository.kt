package com.dskmusic.pixa.data.settings

import androidx.datastore.preferences.core.floatPreferencesKey
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dskmusic.pixa.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pixa_settings")

/**
 * Unico punto de lectura/escritura de ajustes. Persiste con DataStore (requisito 1).
 *
 * Las listas de colores se guardan como texto separado por comas: son cortas y asi el
 * fichero de ajustes sigue siendo legible y trivial de exportar en la copia de seguridad.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = intPreferencesKey("accent_color")
        val LANGUAGE = stringPreferencesKey("language")
        val HANDEDNESS = stringPreferencesKey("handedness")
        val HAPTICS = booleanPreferencesKey("haptics")
        val CONFIRM_DISCARD = booleanPreferencesKey("confirm_discard")
        val CONFIRM_LUT_DELETE = booleanPreferencesKey("confirm_lut_delete")
        val WHITE_LOGO_CYCLE = booleanPreferencesKey("white_logo_cycle")
        val CURTAIN_DEFAULT = booleanPreferencesKey("curtain_default")
        val MAGNIFIER_ZOOM = floatPreferencesKey("magnifier_zoom")
        val MAGNIFIER_AVOID = booleanPreferencesKey("magnifier_avoid")
        val MAGNIFIER_CORNER = intPreferencesKey("magnifier_corner")
        val MAGNIFIER_SHAPE = intPreferencesKey("magnifier_shape")
        val MAGNIFIER_SIZE = floatPreferencesKey("magnifier_size")
        val MAGNIFIER_MARGIN = floatPreferencesKey("magnifier_margin")
        val MAGNIFIER = booleanPreferencesKey("magnifier")
        val LENS_CROSS_SIZE = floatPreferencesKey("lens_cross_size")
        val LENS_CROSS_WIDTH = floatPreferencesKey("lens_cross_width")
        val SELECTION_LINE_WIDTH = floatPreferencesKey("selection_line_width")
        val GRID = booleanPreferencesKey("grid_default")
        val SNAPPING = booleanPreferencesKey("snapping")
        val MAX_RES = intPreferencesKey("max_resolution")
        val AUTOSAVE = booleanPreferencesKey("autosave")
        val HISTORY_LIMIT = intPreferencesKey("history_limit")
        val REFIT_ON_PANEL = booleanPreferencesKey("refit_on_panel")
        val PANEL_VIEW_MODE = intPreferencesKey("panel_view_mode")
        val LOCK_ORIENTATION = booleanPreferencesKey("lock_orientation")
        val TUTORIAL_DONE = booleanPreferencesKey("tutorial_done")
        val HINT_PROJ_IMG = booleanPreferencesKey("hint_project_vs_image")
        val PALETTE = stringPreferencesKey("palette")
        val RECENT = stringPreferencesKey("recent_colors")
        val LAST_PROJECT = stringPreferencesKey("last_project")
    }

    val settings: Flow<PixaSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): PixaSettings {
        val d = PixaSettings()
        return PixaSettings(
            themeMode = this[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            accentColor = this[Keys.ACCENT] ?: d.accentColor,
            language = this[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: d.language,
            handedness = this[Keys.HANDEDNESS]?.let { runCatching { Handedness.valueOf(it) }.getOrNull() } ?: d.handedness,
            haptics = this[Keys.HAPTICS] ?: d.haptics,
            confirmDiscard = this[Keys.CONFIRM_DISCARD] ?: d.confirmDiscard,
            confirmLutDelete = this[Keys.CONFIRM_LUT_DELETE] ?: d.confirmLutDelete,
            whiteLogoCycle = this[Keys.WHITE_LOGO_CYCLE] ?: d.whiteLogoCycle,
            curtainByDefault = this[Keys.CURTAIN_DEFAULT] ?: d.curtainByDefault,
            magnifierZoom = this[Keys.MAGNIFIER_ZOOM] ?: d.magnifierZoom,
            magnifierAvoid = this[Keys.MAGNIFIER_AVOID] ?: d.magnifierAvoid,
            magnifierCorner = this[Keys.MAGNIFIER_CORNER] ?: d.magnifierCorner,
            magnifierShape = this[Keys.MAGNIFIER_SHAPE] ?: d.magnifierShape,
            magnifierSize = this[Keys.MAGNIFIER_SIZE] ?: d.magnifierSize,
            magnifierMargin = this[Keys.MAGNIFIER_MARGIN] ?: d.magnifierMargin,
            magnifier = this[Keys.MAGNIFIER] ?: d.magnifier,
            lensCrossSize = this[Keys.LENS_CROSS_SIZE] ?: d.lensCrossSize,
            lensCrossWidth = this[Keys.LENS_CROSS_WIDTH] ?: d.lensCrossWidth,
            selectionLineWidth = this[Keys.SELECTION_LINE_WIDTH] ?: d.selectionLineWidth,
            gridByDefault = this[Keys.GRID] ?: d.gridByDefault,
            snapping = this[Keys.SNAPPING] ?: d.snapping,
            maxResolution = this[Keys.MAX_RES] ?: d.maxResolution,
            autosave = this[Keys.AUTOSAVE] ?: d.autosave,
            historyLimit = this[Keys.HISTORY_LIMIT] ?: d.historyLimit,
            refitOnPanel = this[Keys.REFIT_ON_PANEL] ?: d.refitOnPanel,
            // Si nunca se eligio, se respeta el ajuste antiguo (reajustar si/no).
            panelViewMode = this[Keys.PANEL_VIEW_MODE]
                ?: if (this[Keys.REFIT_ON_PANEL] == false) 1 else d.panelViewMode,
            // Sin valor guardado, el default depende del dispositivo: moviles bloqueados
            // en vertical, tablets libres.
            lockOrientation = this[Keys.LOCK_ORIENTATION] ?: !isTablet(),
            tutorialDone = this[Keys.TUTORIAL_DONE] ?: d.tutorialDone,
            hintProjectVsImageShown = this[Keys.HINT_PROJ_IMG] ?: d.hintProjectVsImageShown,
            palette = decodeColors(this[Keys.PALETTE]),
            recentColors = decodeColors(this[Keys.RECENT]),
            lastProjectId = this[Keys.LAST_PROJECT],
        )
    }

    private fun decodeColors(raw: String?): List<Int> =
        raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

    private fun encodeColors(list: List<Int>): String = list.joinToString(",")

    private suspend fun edit(block: MutablePreferences.() -> Unit) {
        context.dataStore.edit { it.block() }
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { this[Keys.THEME] = mode.name }
    suspend fun setAccentColor(color: Int) = edit { this[Keys.ACCENT] = color }
    suspend fun setLanguage(language: AppLanguage) = edit { this[Keys.LANGUAGE] = language.name }
    suspend fun setHandedness(value: Handedness) = edit { this[Keys.HANDEDNESS] = value.name }
    suspend fun setHaptics(value: Boolean) = edit { this[Keys.HAPTICS] = value }
    suspend fun setConfirmDiscard(value: Boolean) = edit { this[Keys.CONFIRM_DISCARD] = value }
    suspend fun setConfirmLutDelete(value: Boolean) = edit { this[Keys.CONFIRM_LUT_DELETE] = value }
    suspend fun setWhiteLogoCycle(value: Boolean) = edit { this[Keys.WHITE_LOGO_CYCLE] = value }
    suspend fun setCurtainByDefault(value: Boolean) = edit { this[Keys.CURTAIN_DEFAULT] = value }
    suspend fun setMagnifierZoom(value: Float) = edit { this[Keys.MAGNIFIER_ZOOM] = value }
    suspend fun setMagnifierAvoid(value: Boolean) = edit { this[Keys.MAGNIFIER_AVOID] = value }
    suspend fun setMagnifierCorner(value: Int) = edit { this[Keys.MAGNIFIER_CORNER] = value.coerceIn(0, 3) }
    suspend fun setMagnifierShape(value: Int) = edit { this[Keys.MAGNIFIER_SHAPE] = value.coerceIn(0, 2) }
    suspend fun setMagnifierSize(value: Float) = edit { this[Keys.MAGNIFIER_SIZE] = value }
    suspend fun setMagnifierMargin(value: Float) = edit { this[Keys.MAGNIFIER_MARGIN] = value }
    suspend fun setMagnifier(value: Boolean) = edit { this[Keys.MAGNIFIER] = value }
    suspend fun setLensCrossSize(value: Float) = edit { this[Keys.LENS_CROSS_SIZE] = value.coerceIn(0.5f, 2f) }
    suspend fun setLensCrossWidth(value: Float) = edit { this[Keys.LENS_CROSS_WIDTH] = value.coerceIn(0.5f, 3f) }
    suspend fun setSelectionLineWidth(value: Float) = edit { this[Keys.SELECTION_LINE_WIDTH] = value.coerceIn(0.5f, 3f) }
    suspend fun setGridByDefault(value: Boolean) = edit { this[Keys.GRID] = value }
    suspend fun setSnapping(value: Boolean) = edit { this[Keys.SNAPPING] = value }
    suspend fun setMaxResolution(value: Int) = edit { this[Keys.MAX_RES] = value }
    suspend fun setAutosave(value: Boolean) = edit { this[Keys.AUTOSAVE] = value }
    suspend fun setHistoryLimit(value: Int) = edit { this[Keys.HISTORY_LIMIT] = value }
    suspend fun setRefitOnPanel(value: Boolean) = edit { this[Keys.REFIT_ON_PANEL] = value }
    suspend fun setPanelViewMode(value: Int) = edit { this[Keys.PANEL_VIEW_MODE] = value.coerceIn(0, 2) }
    suspend fun setLockOrientation(value: Boolean) = edit { this[Keys.LOCK_ORIENTATION] = value }
    suspend fun setTutorialDone(value: Boolean) = edit { this[Keys.TUTORIAL_DONE] = value }
    suspend fun setHintProjectVsImageShown(value: Boolean) = edit { this[Keys.HINT_PROJ_IMG] = value }
    suspend fun setLastProjectId(id: String?) = edit {
        if (id == null) remove(Keys.LAST_PROJECT) else this[Keys.LAST_PROJECT] = id
    }

    /** Anade un color a la paleta global, sin duplicados y con tope de tamano. */
    suspend fun addToPalette(color: Int) = edit {
        val current = decodeColors(this[Keys.PALETTE])
        if (color in current) return@edit
        this[Keys.PALETTE] = encodeColors((current + color).takeLast(PixaSettings.MAX_PALETTE))
    }

    suspend fun removeFromPalette(color: Int) = edit {
        this[Keys.PALETTE] = encodeColors(decodeColors(this[Keys.PALETTE]) - color)
    }

    suspend fun setPalette(colors: List<Int>) = edit {
        this[Keys.PALETTE] = encodeColors(colors.distinct().takeLast(PixaSettings.MAX_PALETTE))
    }

    /** Registra un color como reciente (el mas nuevo primero). */
    suspend fun pushRecentColor(color: Int) = edit {
        val current = decodeColors(this[Keys.RECENT])
        val next = (listOf(color) + current.filter { it != color }).take(PixaSettings.MAX_RECENT)
        this[Keys.RECENT] = encodeColors(next)
    }

    suspend fun resetHints() = edit {
        this[Keys.HINT_PROJ_IMG] = false
    }

    /** Disponible solo en dispositivos grandes (tablets): ancho minimo de 600 dp. */
    private fun isTablet(): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= 600

    /** Vuelca los ajustes a un mapa plano para la copia de seguridad (requisito 1). */
    suspend fun exportMap(current: PixaSettings): Map<String, String> = mapOf(
        "theme_mode" to current.themeMode.name,
        "accent_color" to current.accentColor.toString(),
        "language" to current.language.name,
        "handedness" to current.handedness.name,
        "haptics" to current.haptics.toString(),
        "confirm_discard" to current.confirmDiscard.toString(),
        "confirm_lut_delete" to current.confirmLutDelete.toString(),
        "white_logo_cycle" to current.whiteLogoCycle.toString(),
        "curtain_default" to current.curtainByDefault.toString(),
        "magnifier_zoom" to current.magnifierZoom.toString(),
        "magnifier_avoid" to current.magnifierAvoid.toString(),
        "magnifier_corner" to current.magnifierCorner.toString(),
        "magnifier_shape" to current.magnifierShape.toString(),
        "magnifier_size" to current.magnifierSize.toString(),
        "magnifier_margin" to current.magnifierMargin.toString(),
        "magnifier" to current.magnifier.toString(),
        "lens_cross_size" to current.lensCrossSize.toString(),
        "lens_cross_width" to current.lensCrossWidth.toString(),
        "selection_line_width" to current.selectionLineWidth.toString(),
        "grid_default" to current.gridByDefault.toString(),
        "snapping" to current.snapping.toString(),
        "max_resolution" to current.maxResolution.toString(),
        "autosave" to current.autosave.toString(),
        "history_limit" to current.historyLimit.toString(),
        "refit_on_panel" to current.refitOnPanel.toString(),
        "panel_view_mode" to current.panelViewMode.toString(),
        "lock_orientation" to current.lockOrientation.toString(),
        "palette" to encodeColors(current.palette),
        "recent_colors" to encodeColors(current.recentColors),
    )

    /** Restaura desde el mapa generado por [exportMap]. Ignora claves desconocidas. */
    suspend fun importMap(map: Map<String, String>) = edit {
        map["theme_mode"]?.let { v -> runCatching { ThemeMode.valueOf(v) }.getOrNull()?.let { this[Keys.THEME] = it.name } }
        map["accent_color"]?.toIntOrNull()?.let { this[Keys.ACCENT] = it }
        map["language"]?.let { v -> runCatching { AppLanguage.valueOf(v) }.getOrNull()?.let { this[Keys.LANGUAGE] = it.name } }
        map["handedness"]?.let { v -> runCatching { Handedness.valueOf(v) }.getOrNull()?.let { this[Keys.HANDEDNESS] = it.name } }
        map["haptics"]?.toBooleanStrictOrNull()?.let { this[Keys.HAPTICS] = it }
        map["confirm_discard"]?.toBooleanStrictOrNull()?.let { this[Keys.CONFIRM_DISCARD] = it }
        map["confirm_lut_delete"]?.toBooleanStrictOrNull()?.let { this[Keys.CONFIRM_LUT_DELETE] = it }
        map["white_logo_cycle"]?.toBooleanStrictOrNull()?.let { this[Keys.WHITE_LOGO_CYCLE] = it }
        map["curtain_default"]?.toBooleanStrictOrNull()?.let { this[Keys.CURTAIN_DEFAULT] = it }
        map["magnifier_zoom"]?.toFloatOrNull()?.let { this[Keys.MAGNIFIER_ZOOM] = it }
        map["magnifier_avoid"]?.toBooleanStrictOrNull()?.let { this[Keys.MAGNIFIER_AVOID] = it }
        map["magnifier_corner"]?.toIntOrNull()?.let { this[Keys.MAGNIFIER_CORNER] = it.coerceIn(0, 3) }
        map["magnifier_shape"]?.toIntOrNull()?.let { this[Keys.MAGNIFIER_SHAPE] = it.coerceIn(0, 2) }
        map["magnifier_size"]?.toFloatOrNull()?.let { this[Keys.MAGNIFIER_SIZE] = it }
        map["magnifier_margin"]?.toFloatOrNull()?.let { this[Keys.MAGNIFIER_MARGIN] = it }
        map["magnifier"]?.toBooleanStrictOrNull()?.let { this[Keys.MAGNIFIER] = it }
        map["lens_cross_size"]?.toFloatOrNull()?.let { this[Keys.LENS_CROSS_SIZE] = it.coerceIn(0.5f, 2f) }
        map["lens_cross_width"]?.toFloatOrNull()?.let { this[Keys.LENS_CROSS_WIDTH] = it.coerceIn(0.5f, 3f) }
        map["selection_line_width"]?.toFloatOrNull()?.let { this[Keys.SELECTION_LINE_WIDTH] = it.coerceIn(0.5f, 3f) }
        map["grid_default"]?.toBooleanStrictOrNull()?.let { this[Keys.GRID] = it }
        map["snapping"]?.toBooleanStrictOrNull()?.let { this[Keys.SNAPPING] = it }
        map["max_resolution"]?.toIntOrNull()?.let { this[Keys.MAX_RES] = it }
        map["autosave"]?.toBooleanStrictOrNull()?.let { this[Keys.AUTOSAVE] = it }
        map["history_limit"]?.toIntOrNull()?.let { this[Keys.HISTORY_LIMIT] = it }
        map["refit_on_panel"]?.toBooleanStrictOrNull()?.let { this[Keys.REFIT_ON_PANEL] = it }
        map["panel_view_mode"]?.toIntOrNull()?.let { this[Keys.PANEL_VIEW_MODE] = it.coerceIn(0, 2) }
        map["lock_orientation"]?.toBooleanStrictOrNull()?.let { this[Keys.LOCK_ORIENTATION] = it }
        map["palette"]?.let { this[Keys.PALETTE] = it }
        map["recent_colors"]?.let { this[Keys.RECENT] = it }
    }
}
