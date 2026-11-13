package com.dskmusic.pixa.ui.common

import androidx.compose.material3.OutlinedButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.ui.theme.MinTouchTarget
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// ======================================================================================
// Barras y contenedores
// ======================================================================================

// TopAppBar y sus colores siguen marcados como experimentales en Material 3 1.3.x; la
// firma es estable desde hace varias versiones y esta es la unica barra de la app.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixaTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(MinTouchTarget)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.cd_back),
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * Boton "Aplicar" con un pulso verde semitransparente que crece y se desvanece en bucle
 * mientras hay un cambio pendiente ([enabled] == true). Es un recordatorio suave de que
 * falta confirmar, sin resultar estresante: el anillo es ténue y el ciclo es lento.
 */
@Composable
fun PulseApplyButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "applyPulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "applyPulseProgress",
    )
    // El anillo crece (escala) y pierde opacidad a lo largo del ciclo.
    val haloScale = 0.6f + progress * 0.6f
    val haloAlpha = if (enabled) (1f - progress) * 0.35f else 0f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (enabled) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(MinTouchTarget)
                    .padding(4.dp),
            ) {
                val radius = size.minDimension / 2f * haloScale
                drawCircle(
                    color = Color(0xFF4CAF50).copy(alpha = haloAlpha),
                    radius = radius,
                )
            }
        }
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(MinTouchTarget).padding(4.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_apply))
        }
    }
}

@Composable
fun SectionTitle(
    text: String,
    help: String? = null,
    onClose: (() -> Unit)? = null,
    onApply: (() -> Unit)? = null,
    applyEnabled: Boolean = true,
    onMinimize: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Requisito del usuario: pulsar la cabecera minimiza el panel igual que el
            // boton, y deslizarla hacia abajo tambien lo minimiza. Los botones de dentro
            // siguen con su propia accion, que gana al estar mas adentro.
            .then(if (onMinimize != null) Modifier.clickable(onClick = onMinimize) else Modifier)
            .panelSwipeToToggle(
                onSwipeDown = onMinimize ?: {},
                onSwipeUp = {},
            )
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        // Requisito del usuario: el "Aplicar" homogeneizado arriba, antes de ayuda y
        // cerrar, para ahorrar la fila que ocupaba abajo. Con pulso verde si hay cambios
        // pendientes, para que no quede escondido.
        extra?.invoke()
        if (onApply != null) {
            PulseApplyButton(onClick = onApply, enabled = applyEnabled)
        }
        // Requisito del usuario: minimizar el panel a una barra fina sin cerrarlo, para
        // seguir usando la herramienta activa con mas sitio de lienzo.
        if (onMinimize != null) {
            IconButton(onClick = onMinimize, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.UnfoldLess,
                    contentDescription = stringResource(R.string.action_minimize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (help != null) HelpButton(help)
        // Requisito del usuario: cerrar el panel desde su propia cabecera, junto a ayuda.
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Barra fina cuando el panel esta minimizado (requisito del usuario): nombre a la
 * izquierda, maximizar y cerrar a la derecha. El lienzo recupera el sitio que ocupaba el
 * panel entero, pero la herramienta activa (p.ej. el pincel) sigue funcionando igual,
 * porque minimizar solo afecta a lo que se pinta aqui, no a la herramienta elegida.
 */
@Composable
fun MinimizedPanelBar(
    text: String,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    onApply: (() -> Unit)? = null,
    applyEnabled: Boolean = true,
    extra: (@Composable () -> Unit)? = null,
) {
    // Requisito del usuario: altura natural de la fila, no la que tuviera el panel
    // entero (fillMaxHeight reclamaba hasta los 300dp del panel maximizado y dejaba un
    // hueco vacio debajo de la barra).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Requisito del usuario: maximizar tocando la barra entera, no solo el
            // icono. Los botones de dentro (aplicar/maximizar/cerrar) siguen con su
            // propia accion, que gana sobre esta al estar mas adentro.
            .clickable(onClick = onMaximize)
            .panelSwipeToToggle(
                onSwipeDown = {},
                onSwipeUp = onMaximize,
            )
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        // Requisito del usuario: poder aplicar sin tener que maximizar el panel solo
        // para eso, si hay un cambio pendiente (con pulso verde para que no quede oculto).
        extra?.invoke()
        if (onApply != null) {
            PulseApplyButton(onClick = onApply, enabled = applyEnabled)
        }
        IconButton(onClick = onMaximize, modifier = Modifier.size(MinTouchTarget)) {
            Icon(
                Icons.Filled.UnfoldMore,
                contentDescription = stringResource(R.string.action_maximize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Desliza verticalmente una barra de panel para minimizarla (hacia abajo) o maximizarla
 * (hacia arriba) (requisito del usuario).
 *
 * El umbral es el "slop" de toque: cualquier deslizamiento que ya supera el gesto de
 * arrastre dispara la accion segun su direccion neta al soltar el dedo. Asi un toque simple
 * no minimiza por error (eso lo hace el clickable de la barra) y el gesto intencionado si.
 */
fun Modifier.panelSwipeToToggle(
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
): Modifier = pointerInput(Unit) {
    val threshold = viewConfiguration.touchSlop.toFloat()
    var totalDy = 0f
    detectVerticalDragGestures(
        onVerticalDrag = { change, dragAmount ->
            change.consume()
            totalDy += dragAmount
        },
        onDragEnd = {
            when {
                totalDy > threshold -> onSwipeDown()
                totalDy < -threshold -> onSwipeUp()
            }
            totalDy = 0f
        },
        onDragCancel = { totalDy = 0f },
    )
}

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) { content() }
    }
}

/**
 * Ayuda contextual (requisito 1): cada panel ofrece un "?" que explica para que sirve.
 * Se resuelve con un dialogo y no con un tooltip porque los textos son de varias lineas y
 * asi tambien los lee un lector de pantalla.
 */
@Composable
fun HelpButton(text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(MinTouchTarget)) {
        Icon(
            Icons.Filled.HelpOutline,
            contentDescription = stringResource(R.string.cd_help_tool),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        InfoDialog(
            title = stringResource(R.string.help_title),
            body = text,
            onDismiss = { open = false },
        )
    }
}

// ======================================================================================
// Controles de valor
// ======================================================================================

/**
 * Fila de slider con etiqueta, valor y boton de restablecer.
 *
 * El valor numerico se muestra siempre: muchos ajustes se usan mirando la foto y sin la
 * cifra no hay forma de repetir un resultado en otra imagen.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueText: String = formatValue(value),
    defaultValue: Float? = null,
    onChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    // Compacto (requisito del usuario): etiqueta pequena sobre el slider; valor y reset a su
    // derecha, centrados con el slider (no con la etiqueta).
    Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                onValueChangeFinished = onChangeFinished,
                modifier = Modifier.weight(1f).height(30.dp).semanticsLabel(label),
            )
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            if (defaultValue != null) {
                IconButton(
                    onClick = {
                        onValueChange(defaultValue)
                        onChangeFinished?.invoke()
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_reset),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Slider para valores enteros (tamanos, radios, numero de lados). */
@Composable
fun IntSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    suffix: String = "",
    defaultValue: Int? = null,
    onChangeFinished: (() -> Unit)? = null,
) {
    SliderRow(
        label = label,
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceIn(0, 200),
        valueText = "$value$suffix",
        defaultValue = defaultValue?.toFloat(),
        onChangeFinished = onChangeFinished,
    )
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Margen entre el texto (que puede ser largo, con subtitulo) y el interruptor,
        // para que no se peguen.
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Campo numerico con etiqueta, para los paneles de medidas y tamanos. */
@Composable
fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..8192,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(6)
            text = digits
            digits.toIntOrNull()?.let { onValueChange(it.coerceIn(range.first, range.last)) }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.defaultMinSize(minWidth = 110.dp),
    )
}

/** Campo de texto reutilizable (nombres, enlaces, cuerpos de post). */
@Composable
fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// ======================================================================================
// Selectores
// ======================================================================================

/**
 * Lista de opciones en fila desplazable. La usan todos los paneles para elegir modo,
 * formato o estilo, de modo que el gesto es siempre el mismo en toda la app.
 */
@Composable
fun <T> OptionRow(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    /** Al cambiar este valor la fila vuelve a su posicion inicial (p.ej. al cambiar de grupo). */
    resetKey: Any? = null,
) {
    val rowScroll = rememberScrollState()
    LaunchedEffect(resetKey) { rowScroll.scrollTo(0) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rowScroll)
            
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                modifier = Modifier.clickable { onSelect(option) },
            ) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 30.dp)
                        .padding(horizontal = 11.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** Boton de herramienta: icono grande, etiqueta debajo y estado seleccionado. */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    width: Dp = 76.dp,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Muestra de color pulsable, con marco para que se vea sobre fondos del mismo tono. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean = false,
    size: Int = 36,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semanticsLabel(stringResource(R.string.cd_color_swatch)),
    )
}

/**
 * Circulo multicolor: el atajo al selector de color propio.
 *
 * Es el septimo circulo de la fila de acentos, asi que se ve de un golpe que ahi hay mas
 * colores de los seis que caben.
 */
@Composable
fun RainbowSwatch(
    selected: Boolean = false,
    size: Int = 36,
    onClick: () -> Unit,
) {
    val wheel = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759),
                Color(0xFF00C7D4), Color(0xFF007AFF), Color(0xFFAF52DE),
                Color(0xFFFF3B30),
            ),
        )
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(wheel)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semanticsLabel(stringResource(R.string.settings_accent_custom)),
    )
}

// ======================================================================================
// Estado
// ======================================================================================

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Capa de "trabajando" con progreso opcional.
 *
 * Bloquea los toques a proposito: las operaciones que la muestran modifican el documento y
 * dejar la UI viva invitaria a lanzar dos a la vez. Sin velo oscuro ni animacion de
 * entrada grande: eso se veia como un cuadro transparente que se superponia a toda la
 * interfaz; ahora solo el bloqueo de toques es invisible y el indicador es una tarjeta
 * pequena que aparece con un simple fundido.
 */
@Composable
fun BusyOverlay(
    visible: Boolean,
    text: String? = null,
    progress: Float? = null,
    onCancel: (() -> Unit)? = null,
    detail: String? = null,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            // Bloquea los toques pero no se ve: nada de velo oscuro cubriendo la pantalla.
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = true, onClick = {}),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.padding(bottom = 96.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .width(200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (progress == null) {
                        CircularProgressIndicator()
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text ?: stringResource(R.string.label_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (progress != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${(progress.coerceIn(0f, 1f) * 100).roundToInt()} %",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (detail != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (onCancel != null) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onCancel) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Avisos cortos de la app, en vez del [androidx.compose.material3.SnackbarHost] por
 * defecto: ese aparecia pegado abajo, justo sobre la barra de herramientas del editor, y
 * tapaba lo que se estaba haciendo. Este se centra en la pantalla, con fondo fuerte y
 * texto claro, y desaparece solo a los pocos segundos.
 *
 * Material 3 programa ese cierre automatico dentro de su propio `SnackbarHost`, no en
 * [SnackbarHostState]; al no reutilizar ese composable, el temporizador se repite aqui.
 */
@Composable
fun PixaToastHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val data = hostState.currentSnackbarData
    LaunchedEffect(data) {
        if (data != null) {
            delay(3200)
            data.dismiss()
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = data != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            data?.let { PixaToast(it) }
        }
    }
}

@Composable
private fun PixaToast(data: SnackbarData) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 8.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                data.visuals.message,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            data.visuals.actionLabel?.let { label ->
                Spacer(Modifier.width(16.dp))
                Text(
                    label,
                    color = MaterialTheme.colorScheme.inversePrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { data.performAction() },
                )
            }
        }
    }
}

// ======================================================================================
// Utilidades
// ======================================================================================

/** Etiqueta para lector de pantalla sin exponer la estructura interna del control. */
fun Modifier.semanticsLabel(label: String): Modifier =
    this.clearAndSetSemantics { contentDescription = label }

fun formatValue(value: Float): String =
    if (value == value.roundToInt().toFloat()) {
        value.roundToInt().toString()
    } else {
        String.format("%.2f", value)
    }

/** Porcentaje legible, con signo cuando el rango va de -1 a 1. */
fun formatPercent(value: Float, signed: Boolean = false): String {
    val pct = (value * 100).roundToInt()
    return if (signed && pct > 0) "+$pct %" else "$pct %"
}
