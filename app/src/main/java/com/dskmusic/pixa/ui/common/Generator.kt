package com.dskmusic.pixa.ui.common

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.export.ExportOptions
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.ui.editor.EditorMessage
import com.dskmusic.pixa.ui.editor.EditorViewModel

/**
 * Avisos del editor en cualquier pantalla que comparta su ViewModel.
 *
 * Esta aqui y no en cada pantalla porque el editor, la edicion rapida y los ocho
 * generadores muestran exactamente los mismos mensajes, incluido el boton "Ver" que abre
 * la imagen recien guardada en la galeria.
 */
@Composable
fun EditorMessages(vm: EditorViewModel, snackbar: SnackbarHostState) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val message = state.message

    // El aviso se da por servido en cuanto se recoge del ViewModel, no cuando el usuario
    // cierra el snackbar: si se sale de la pantalla mientras esta visible, este efecto
    // muere antes de llegar a consumeMessage() y el mensaje se quedaba en el estado, listo
    // para reaparecer en la siguiente imagen. Se copia aqui y se limpia alli.
    var showing by remember { mutableStateOf<EditorMessage?>(null) }
    LaunchedEffect(message?.id) {
        val pending = message ?: return@LaunchedEffect
        showing = pending
        vm.consumeMessage()
    }

    LaunchedEffect(showing?.id) {
        val msg = showing ?: return@LaunchedEffect
        val text = if (msg.arg != null) {
            context.getString(msg.textRes, msg.arg)
        } else {
            context.getString(msg.textRes)
        }
        val result = snackbar.showSnackbar(
            message = text,
            actionLabel = if (msg.viewUri != null) context.getString(R.string.action_view) else null,
        )
        if (result == SnackbarResult.ActionPerformed && msg.viewUri != null) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(msg.viewUri, msg.viewMime ?: "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
            }
        }
        showing = null
    }
}

/**
 * Las tres salidas de todo generador (requisitos 17 a 24): guardar el proyecto editable,
 * exportar la imagen plana o seguir trabajando en el editor de capas.
 *
 * Las tres empiezan igual: el generador entrega su documento con [build] y se instala en
 * el editor. Asi "Abrir en el editor" no tiene que trasladar nada, y guardar o exportar
 * reutilizan el mismo camino que el editor, con los mismos avisos y el mismo "Ver".
 *
 * [build] devuelve null cuando todavia no hay nada que entregar (sin fotos elegidas, sin
 * texto escrito), y entonces no se hace nada.
 */
@Composable
fun GeneratorActions(
    vm: EditorViewModel,
    defaultName: String,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    build: () -> PixaDocument?,
) {
    var naming by remember { mutableStateOf(false) }

    val install: (() -> Unit) -> Unit = { then ->
        val doc = build()
        if (doc != null) {
            vm.install(
                document = doc,
                projectId = null,
                name = defaultName,
                initialLabel = R.string.history_initial,
            )
            then()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = { install { naming = true } },
            enabled = enabled,
        ) { Text(stringResource(R.string.save_project)) }
        TextButton(
            onClick = {
                install {
                    vm.exportImage(ExportOptions(baseName = defaultName))
                }
            },
            enabled = enabled,
        ) { Text(stringResource(R.string.action_export)) }
        Button(
            onClick = { install(onOpenEditor) },
            enabled = enabled,
        ) { Text(stringResource(R.string.action_open_in_editor)) }
    }

    if (naming) {
        TextPromptDialog(
            title = stringResource(R.string.save_project),
            initial = defaultName,
            onConfirm = { name -> vm.saveProject(name) },
            onDismiss = { naming = false },
        )
    }
}

/**
 * Vista previa de un generador: la imagen compuesta, encajada y centrada.
 *
 * Mientras no hay nada que ensenar (sin fotos, sin texto) se explica con [emptyText] en
 * vez de dejar un hueco gris, que es lo que pide el requisito 1 para los estados vacios.
 */
@Composable
fun GeneratorPreview(
    bitmap: Bitmap?,
    emptyText: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    // Tocar la vista previa la abre a pantalla completa para verla bien sin exportar.
    var full by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(enabled = bitmap != null) { full = true },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            EmptyState(emptyText)
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }
    }
    if (full && bitmap != null) {
        FullscreenImageDialog(bitmap, contentDescription) { full = false }
    }
}

/**
 * Imagen a pantalla completa sobre negro. Se cierra tocandola o con la X de arriba; con
 * dos dedos se amplia (hasta 6x) y con doble toque vuelve al encaje.
 */
@Composable
fun FullscreenImageDialog(bitmap: Bitmap, contentDescription: String?, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onDismiss() },
                            onDoubleTap = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Color.White,
                )
            }
        }
    }
}
