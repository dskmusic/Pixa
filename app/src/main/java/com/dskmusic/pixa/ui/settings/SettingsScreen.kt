package com.dskmusic.pixa.ui.settings

import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.editor.panels.AiModelsDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.dskmusic.pixa.ui.common.PixaToastHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.export.Exporter
import com.dskmusic.pixa.data.settings.AppLanguage
import com.dskmusic.pixa.data.settings.Handedness
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.ColorPickerDialog
import com.dskmusic.pixa.ui.common.ColorSwatch
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.common.RainbowSwatch
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.rememberDocumentPicker
import com.dskmusic.pixa.ui.theme.AccentPresets
import com.dskmusic.pixa.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** Siete circulos de acento tienen que caber en la pantalla mas estrecha. */
private const val ACCENT_SWATCH_DP = 34

/**
 * Ajustes de la app (requisito 1).
 *
 * Cada control escribe directamente en DataStore y la UI se repinta desde el Flow: no hay
 * un "guardar" que haya que recordar pulsar, y el cambio de tema o idioma se ve al momento.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenAbout: () -> Unit,
    pendingBackupUri: Uri? = null,
    onPendingBackupHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = context.pixa
    val repo = container.settings
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = PixaSettings())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var pickingAccent by remember { mutableStateOf(false) }
    var pickingPaletteColor by remember { mutableStateOf(false) }

    val exportedOk = stringResource(R.string.settings_backup_ok)
    val restoredOk = stringResource(R.string.settings_restore_ok)
    val restoreFailed = stringResource(R.string.settings_restore_failed)

    suspend fun doImportBackup(uri: Uri) {
        val result = runCatching { container.userData.importBackup(uri) }
        val map = result.getOrNull()
        if (map == null) {
            snackbar.showSnackbar(restoreFailed)
        } else {
            repo.importMap(map)
            snackbar.showSnackbar(restoredOk)
        }
    }

    val importBackup = rememberDocumentPicker(
        mimeTypes = arrayOf("application/zip", "application/octet-stream", "*/*"),
    ) { uri -> scope.launch { doImportBackup(uri) } }

    // Requisito del usuario: un .pixabak abierto "con" la app desde el gestor de archivos
    // restaura directamente, sin tener que venir a Ajustes y pulsar "Restaurar".
    LaunchedEffect(pendingBackupUri) {
        val uri = pendingBackupUri ?: return@LaunchedEffect
        doImportBackup(uri)
        onPendingBackupHandled()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        },
        snackbarHost = { PixaToastHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ------------------------------------------------------------------ aspecto
            SectionTitle(stringResource(R.string.settings_appearance))

            LabeledOptions(
                label = stringResource(R.string.settings_theme),
                options = ThemeMode.entries.toList(),
                selected = settings.themeMode,
                label2 = { mode ->
                    stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                            ThemeMode.OLED -> R.string.theme_oled
                        },
                    )
                },
                onSelect = { mode -> scope.launch { repo.setThemeMode(mode) } },
            )

            Text(
                stringResource(R.string.settings_accent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
            )
            // Seis acentos y el circulo multicolor, todo en una fila: el septimo
            // circulo abre el selector HSV para cualquier otro color.
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentPresets.colors.forEach { preset ->
                    val argb = preset.toArgb()
                    ColorSwatch(
                        color = preset,
                        selected = argb == settings.accentColor,
                        size = ACCENT_SWATCH_DP,
                        onClick = { scope.launch { repo.setAccentColor(argb) } },
                    )
                }
                RainbowSwatch(
                    selected = AccentPresets.colors.none { it.toArgb() == settings.accentColor },
                    size = ACCENT_SWATCH_DP,
                    onClick = { pickingAccent = true },
                )
            }

            LabeledOptions(
                label = stringResource(R.string.settings_language),
                options = AppLanguage.entries.toList(),
                selected = settings.language,
                label2 = { language ->
                    stringResource(
                        when (language) {
                            AppLanguage.AUTO -> R.string.language_auto
                            AppLanguage.SPANISH -> R.string.language_es
                            AppLanguage.ENGLISH -> R.string.language_en
                        },
                    )
                },
                onSelect = { language -> scope.launch { repo.setLanguage(language) } },
            )

            // ------------------------------------------------------------------- editor
            SectionTitle(stringResource(R.string.settings_editor))

            LabeledOptions(
                label = stringResource(R.string.settings_handedness),
                sublabel = stringResource(R.string.settings_handedness_sub),
                options = Handedness.entries.toList(),
                selected = settings.handedness,
                label2 = { value ->
                    stringResource(
                        when (value) {
                            Handedness.RIGHT -> R.string.handedness_right
                            Handedness.LEFT -> R.string.handedness_left
                        },
                    )
                },
                onSelect = { value -> scope.launch { repo.setHandedness(value) } },
            )

            ToggleRow(
                label = stringResource(R.string.settings_haptics),
                checked = settings.haptics,
                onCheckedChange = { value -> scope.launch { repo.setHaptics(value) } },
            )
            ToggleRow(
                label = stringResource(R.string.settings_confirm_discard),
                subtitle = stringResource(R.string.settings_confirm_discard_hint),
                checked = settings.confirmDiscard,
                onCheckedChange = { value -> scope.launch { repo.setConfirmDiscard(value) } },
            )
            ToggleRow(
                label = stringResource(R.string.settings_confirm_lut_delete),
                subtitle = stringResource(R.string.settings_confirm_lut_delete_hint),
                checked = settings.confirmLutDelete,
                onCheckedChange = { value -> scope.launch { repo.setConfirmLutDelete(value) } },
            )
            ToggleRow(
                label = stringResource(R.string.settings_white_logo_cycle),
                subtitle = stringResource(R.string.settings_white_logo_cycle_hint),
                checked = settings.whiteLogoCycle,
                onCheckedChange = { value -> scope.launch { repo.setWhiteLogoCycle(value) } },
            )
            ToggleRow(
                label = stringResource(R.string.settings_curtain_default),
                subtitle = stringResource(R.string.settings_curtain_default_hint),
                checked = settings.curtainByDefault,
                onCheckedChange = { value -> scope.launch { repo.setCurtainByDefault(value) } },
            )
            var showAiModels by remember { mutableStateOf(false) }
            ActionRow(stringResource(R.string.settings_ai_models)) { showAiModels = true }
            if (showAiModels) AiModelsDialog { showAiModels = false }
            ToggleRow(
                label = stringResource(R.string.settings_magnifier),
                checked = settings.magnifier,
                onCheckedChange = { value -> scope.launch { repo.setMagnifier(value) } },
            )
            SliderRow(
                label = stringResource(R.string.settings_magnifier_zoom),
                value = settings.magnifierZoom,
                onValueChange = { value -> scope.launch { repo.setMagnifierZoom(value) } },
                valueRange = 1.5f..5f,
                valueText = "×" + String.format("%.1f", settings.magnifierZoom),
                defaultValue = 2.5f,
            )
            ToggleRow(
                label = stringResource(R.string.settings_magnifier_avoid),
                subtitle = stringResource(R.string.settings_magnifier_avoid_hint),
                checked = settings.magnifierAvoid,
                onCheckedChange = { value -> scope.launch { repo.setMagnifierAvoid(value) } },
            )
            // Esquina de reposo con "apartar" activado, o esquina fija si esta desactivado.
            LabeledOptions(
                label = stringResource(R.string.settings_magnifier_corner),
                options = listOf(0, 1, 2, 3),
                selected = settings.magnifierCorner,
                label2 = { c ->
                    stringResource(
                        when (c) {
                            0 -> R.string.corner_top_left
                            1 -> R.string.corner_top_right
                            2 -> R.string.corner_bottom_left
                            else -> R.string.corner_bottom_right
                        },
                    )
                },
                onSelect = { c -> scope.launch { repo.setMagnifierCorner(c) } },
            )
            LabeledOptions(
                label = stringResource(R.string.settings_magnifier_shape),
                options = listOf(0, 1, 2),
                selected = settings.magnifierShape,
                label2 = { c ->
                    stringResource(
                        when (c) {
                            0 -> R.string.mag_shape_circle
                            1 -> R.string.mag_shape_square
                            else -> R.string.mag_shape_rect
                        },
                    )
                },
                onSelect = { c -> scope.launch { repo.setMagnifierShape(c) } },
            )
            SliderRow(
                label = stringResource(R.string.settings_magnifier_size),
                value = settings.magnifierSize,
                onValueChange = { value -> scope.launch { repo.setMagnifierSize(value) } },
                valueRange = 0.5f..1.6f,
                valueText = "${(settings.magnifierSize * 100).toInt()} %",
                defaultValue = 1f,
            )
            SliderRow(
                label = stringResource(R.string.settings_magnifier_margin),
                value = settings.magnifierMargin,
                onValueChange = { value -> scope.launch { repo.setMagnifierMargin(value) } },
                valueRange = 0f..1.5f,
                valueText = "${(settings.magnifierMargin * 100).toInt()} %",
                defaultValue = 0.25f,
                enabled = settings.magnifierAvoid,
            )
            SliderRow(
                label = stringResource(R.string.settings_lens_cross_size),
                value = settings.lensCrossSize,
                onValueChange = { value -> scope.launch { repo.setLensCrossSize(value) } },
                valueRange = 0.5f..2f,
                valueText = "${(settings.lensCrossSize * 100).toInt()} %",
                defaultValue = 1f,
            )
            SliderRow(
                label = stringResource(R.string.settings_lens_cross_width),
                value = settings.lensCrossWidth,
                onValueChange = { value -> scope.launch { repo.setLensCrossWidth(value) } },
                valueRange = 0.5f..3f,
                valueText = "${(settings.lensCrossWidth * 100).toInt()} %",
                defaultValue = 1f,
            )
            SliderRow(
                label = stringResource(R.string.settings_selection_line_width),
                value = settings.selectionLineWidth,
                onValueChange = { value -> scope.launch { repo.setSelectionLineWidth(value) } },
                valueRange = 0.5f..3f,
                valueText = "${(settings.selectionLineWidth * 100).toInt()} %",
                defaultValue = 1f,
            )
            ToggleRow(
                label = stringResource(R.string.settings_grid_default),
                checked = settings.gridByDefault,
                onCheckedChange = { value -> scope.launch { repo.setGridByDefault(value) } },
            )
            ToggleRow(
                label = stringResource(R.string.settings_snap),
                checked = settings.snapping,
                onCheckedChange = { value -> scope.launch { repo.setSnapping(value) } },
            )
            LabeledOptions(
                label = stringResource(R.string.settings_panel_view),
                // La primera opcion (y la de por defecto): encajar el objeto elegido.
                options = listOf(2, 0, 1),
                selected = settings.panelViewMode,
                label2 = { mode ->
                    when (mode) {
                        1 -> context.getString(R.string.panel_view_keep)
                        2 -> context.getString(R.string.panel_view_selected)
                        else -> context.getString(R.string.panel_view_fit)
                    }
                },
                onSelect = { value -> scope.launch { repo.setPanelViewMode(value) } },
            )
            ToggleRow(
                label = stringResource(R.string.settings_lock_orientation),
                checked = settings.lockOrientation,
                onCheckedChange = { value -> scope.launch { repo.setLockOrientation(value) } },
            )
            ToggleRow(
                label = stringResource(R.string.settings_autosave),
                subtitle = stringResource(R.string.settings_autosave_sub),
                checked = settings.autosave,
                onCheckedChange = { value -> scope.launch { repo.setAutosave(value) } },
            )

            LabeledOptions(
                label = stringResource(R.string.settings_max_resolution),
                options = PixaSettings.RESOLUTION_OPTIONS,
                selected = settings.maxResolution,
                label2 = { value -> "$value px" },
                onSelect = { value -> scope.launch { repo.setMaxResolution(value) } },
            )
            LabeledOptions(
                label = stringResource(R.string.history_limit),
                options = PixaSettings.HISTORY_OPTIONS,
                selected = settings.historyLimit,
                label2 = { value -> value.toString() },
                onSelect = { value -> scope.launch { repo.setHistoryLimit(value) } },
            )

            // --------------------------------------------------------------------- datos
            SectionTitle(stringResource(R.string.settings_data))

            Text(
                stringResource(R.string.settings_palette),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
            )
            if (settings.palette.isEmpty()) {
                Text(
                    stringResource(R.string.palette_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                // Pulsar un color de la paleta lo quita: es la unica accion que tiene
                // sentido aqui, y asi no hace falta un modo de edicion aparte.
                SwatchGrid(
                    colors = settings.palette,
                    onClick = { argb -> scope.launch { repo.removeFromPalette(argb) } },
                )
                Text(
                    stringResource(R.string.palette_tap_to_remove),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            TextButton(
                onClick = { pickingPaletteColor = true },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.settings_palette_add))
            }

            if (settings.recentColors.isNotEmpty()) {
                Text(
                    stringResource(R.string.settings_palette_recent),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
                )
                SwatchGrid(
                    colors = settings.recentColors,
                    onClick = { argb -> scope.launch { repo.addToPalette(argb) } },
                )
            }

            SectionTitle(
                text = stringResource(R.string.settings_backup),
                help = stringResource(R.string.help_settings_backup),
            )
            ActionRow(
                label = stringResource(R.string.settings_backup_export),
                onClick = {
                    scope.launch {
                        val map = repo.exportMap(settings)
                        val file = runCatching { container.userData.exportBackup(map) }.getOrNull()
                        if (file == null) {
                            snackbar.showSnackbar(restoreFailed)
                        } else {
                            val uri = Exporter.fileUri(context, file)
                            context.startActivity(
                                Intent.createChooser(
                                    Exporter.shareIntent(uri, "application/zip"),
                                    null,
                                ),
                            )
                            snackbar.showSnackbar(exportedOk)
                        }
                    }
                },
            )
            ActionRow(
                label = stringResource(R.string.settings_backup_import),
                onClick = importBackup,
            )

            ActionRow(
                label = stringResource(R.string.settings_tutorial_again),
                onClick = onOpenTutorial,
            )
            ActionRow(
                label = stringResource(R.string.settings_reset_hints),
                onClick = { scope.launch { repo.resetHints() } },
            )
            ActionRow(
                label = stringResource(R.string.about_title),
                onClick = onOpenAbout,
            )

            Spacer(Modifier.height(28.dp))
        }
    }

    if (pickingAccent) {
        ColorPickerDialog(
            initial = settings.accentColor,
            palette = settings.palette,
            recents = settings.recentColors,
            withAlpha = false,
            onDismiss = { pickingAccent = false },
            onConfirm = { picked -> scope.launch { repo.setAccentColor(picked) } },
        )
    }
    if (pickingPaletteColor) {
        ColorPickerDialog(
            initial = settings.accentColor,
            palette = settings.palette,
            recents = settings.recentColors,
            onDismiss = { pickingPaletteColor = false },
            onConfirm = { picked -> scope.launch { repo.addToPalette(picked) } },
        )
    }
}

/** Fila de etiqueta + opciones en linea, el patron que repiten casi todos los ajustes. */
@Composable
private fun <T> LabeledOptions(
    label: String,
    options: List<T>,
    selected: T,
    label2: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    sublabel: String? = null,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (sublabel != null) {
            Text(
                sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val texts = options.associateWith { label2(it) }
        OptionRow(
            options = options,
            selected = selected,
            onSelect = onSelect,
            label = { option -> texts[option].orEmpty() },
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(label)
        }
    }
}

/**
 * Rejilla de muestras de color.
 *
 * Se parte la lista en filas con chunked en vez de usar una rejilla perezosa porque todo
 * esto vive dentro de una columna con scroll vertical y anidar dos scrolls no se permite.
 */
@Composable
private fun SwatchGrid(
    colors: List<Int>,
    onClick: (Int) -> Unit,
    selected: Int? = null,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { argb ->
                    ColorSwatch(
                        color = Color(argb),
                        selected = argb == selected,
                        onClick = { onClick(argb) },
                    )
                }
            }
        }
    }
}
