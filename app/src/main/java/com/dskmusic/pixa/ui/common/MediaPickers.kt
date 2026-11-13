package com.dskmusic.pixa.ui.common

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dskmusic.pixa.core.export.Exporter
import java.io.File

/**
 * Entradas de imagen de la app (requisito 5).
 *
 * Todo pasa por Photo Picker y por la app de camara del sistema, asi que no se declara
 * ningun permiso de almacenamiento ni de camara (requisito 25).
 */

/** Selector de una imagen. */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPicked) }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}

/** Selector de varias imagenes, con tope (lotes, unir fotos). */
@Composable
fun rememberMultiImagePicker(max: Int, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val limit = max.coerceIn(2, 100)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(limit),
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris) }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}

/**
 * Captura con la camara del sistema.
 *
 * Se delega en ACTION_IMAGE_CAPTURE y se le pasa un destino propio mediante FileProvider:
 * asi la app no necesita el permiso CAMERA y la foto queda en la cache, no en la galeria,
 * hasta que el usuario decida exportarla.
 */
@Composable
fun rememberCameraCapture(onResult: (Uri?) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok -> onResult(if (ok) pending else null) }
    return {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = Exporter.fileUri(context, file)
        pending = uri
        launcher.launch(uri)
    }
}

/** Selector de documento por tipo MIME: proyectos .pixa, copias de seguridad, fuentes. */
@Composable
fun rememberDocumentPicker(
    mimeTypes: Array<String>,
    onPicked: (Uri) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPicked) }
    return { launcher.launch(mimeTypes) }
}

/** Primera imagen del portapapeles, o null si no hay ninguna (requisito 2). */
fun clipboardImage(context: Context): Uri? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clip = manager.primaryClip ?: return null
    for (i in 0 until clip.itemCount) {
        val uri = clip.getItemAt(i).uri ?: continue
        val type = context.contentResolver.getType(uri) ?: continue
        if (type.startsWith("image/")) return uri
    }
    return null
}
