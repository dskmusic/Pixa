package com.dskmusic.pixa.ui.editor

import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dskmusic.pixa.R
import kotlin.math.roundToInt

/**
 * Aviso al salir de un panel con cambios sin aplicar: aplicar y salir, salir sin aplicar
 * o seguir editando. Se puede desactivar en Ajustes.
 */
@Composable
fun DiscardChangesDialog(
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    Dialog(onDismissRequest = onKeepEditing) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.discard_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.discard_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.discard_apply))
                }
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.discard_leave))
                }
                TextButton(onClick = onKeepEditing, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.discard_keep))
                }
            }
        }
    }
}

/**
 * Franja invisible sobre la linea de la cortina antes/despues, para arrastrarla con el
 * dedo desde cualquier punto de la linea (incluido el tirador). Se coloca con las mismas
 * conversiones que el lienzo, asi que sigue a la linea al hacer zoom o desplazar la vista.
 */
@Composable
fun CurtainHandle(
    controller: CanvasController,
    docWidth: Int,
    pos: Float,
    onPos: (Float) -> Unit,
) {
    val stripWidth = 72.dp
    val stripPx = with(LocalDensity.current) { stripWidth.toPx() }
    val currentPos by rememberUpdatedState(pos)
    val currentOnPos by rememberUpdatedState(onPos)
    val x = controller.docToView(Offset(pos * docWidth, 0f)).x
    Box(
        modifier = Modifier
            .offset { IntOffset((x - stripPx / 2f).roundToInt(), 0) }
            .width(stripWidth)
            .fillMaxHeight()
            .pointerInput(docWidth) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val viewX = controller.docToView(Offset(currentPos * docWidth, 0f)).x + dragAmount
                    currentOnPos(controller.viewToDoc(Offset(viewX, 0f)).x / docWidth)
                }
            },
    )
}

/**
 * Barra del borrador magico, arriba a la izquierda del lienzo, para no depender del panel:
 * deshacer, rehacer, borrar marcas, lapiz (pintar), selector de grosor y formas (circulo),
 * mano (mover la vista) y aceptar (aplica el borrado).
 */
@Composable
fun MarkToolbar(
    ui: EditorUi,
    controller: CanvasController,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasMarks = ui.marks.isNotEmpty()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Los seis botones van en la barra; aceptar es un circulo mayor que sale por su extremo.
        Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
            tonalElevation = 4.dp,
        ) {
            Row(modifier = Modifier.padding(start = 2.dp, end = 14.dp)) {
                ToolbarButton(
                    Icons.AutoMirrored.Filled.Undo, stringResource(R.string.cd_undo), enabled = hasMarks,
                ) { ui.markPopup = false; ui.undoMark() }
                ToolbarButton(
                    Icons.AutoMirrored.Filled.Redo, stringResource(R.string.cd_redo), enabled = ui.markRedo.isNotEmpty(),
                ) { ui.markPopup = false; ui.redoMark() }
                ToolbarButton(
                    Icons.Filled.DeleteSweep, stringResource(R.string.ai_mark_clear), enabled = hasMarks,
                ) { ui.markPopup = false; ui.clearMarks() }
                // Lapiz: indica que se pinta; vuelve a marcar con pincel si se estaba moviendo la vista.
                ToolbarButton(
                    Icons.Filled.Edit, stringResource(R.string.ai_mark_pencil),
                    selected = !ui.panMode && ui.markShape == MarkShape.BRUSH,
                ) {
                    ui.markPopup = false
                    ui.panMode = false
                    ui.markShape = MarkShape.BRUSH
                }
                // Circulo: abre el selector de grosor y formas.
                ToolbarButton(
                    Icons.Filled.RadioButtonUnchecked, stringResource(R.string.ai_mark_options),
                    selected = ui.markPopup,
                ) { ui.markPopup = !ui.markPopup }
                ToolbarButton(
                    Icons.Filled.PanTool, stringResource(R.string.ai_pan_mode), selected = ui.panMode,
                ) { ui.markPopup = false; ui.panMode = !ui.panMode }
            }
        }
        ApplyCircle(enabled = hasMarks, description = stringResource(R.string.action_apply)) {
            ui.markPopup = false
            onApply()
        }
        }
        if (ui.markPopup) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).width(176.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(
                            MarkShape.BRUSH to Icons.Filled.Brush,
                            MarkShape.RECT to Icons.Filled.CropSquare,
                            MarkShape.ELLIPSE to Icons.Filled.RadioButtonUnchecked,
                            MarkShape.LASSO to Icons.Filled.Gesture,
                        ).forEach { (shape, icon) ->
                            ToolbarButton(
                                icon, stringResource(R.string.ai_mark_options), selected = ui.markShape == shape,
                            ) {
                                ui.markShape = shape
                                ui.panMode = false
                            }
                        }
                    }
                    if (ui.markShape == MarkShape.BRUSH) {
                        // Vista previa: circulo con el tamano que tendra el pincel en pantalla.
                        val previewColor = Color(0xFFFF2D55)
                        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                val radius = (ui.brush.size / 2f * controller.zoom).coerceIn(2f, size.minDimension / 2f - 2f)
                                drawCircle(previewColor.copy(alpha = 0.55f), radius)
                            }
                        }
                        Text("${ui.brush.size.toInt()} px", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = ui.brush.size,
                            onValueChange = { ui.brush = ui.brush.copy(size = it.roundToInt().toFloat()) },
                            valueRange = 4f..160f,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Boton de la barra del borrador magico: 40 dp, con fondo cuando esta activo. El de aceptar
 * ([accent]) es mas grande y va en un circulo relleno con el color de acento, para destacar.
 */
@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    if (accent) {
        val container = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        }
        val content = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(50.dp)
                .background(container, androidx.compose.foundation.shape.CircleShape),
        ) {
            Icon(icon, description, tint = content, modifier = Modifier.size(28.dp))
        }
        return
    }
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                androidx.compose.foundation.shape.CircleShape,
            ),
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Aceptar del borrador magico: circulo relleno con el color de acento, mayor que los demas botones. */
@Composable
private fun ApplyCircle(enabled: Boolean, description: String, onClick: () -> Unit) {
    val container = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            // Se solapa un poco con el extremo de la barra para que parezca sobre ella.
            .offset(x = (-12).dp)
            .size(54.dp)
            .background(container, androidx.compose.foundation.shape.CircleShape),
    ) {
        Icon(Icons.Filled.Check, description, tint = content, modifier = Modifier.size(30.dp))
    }
}
