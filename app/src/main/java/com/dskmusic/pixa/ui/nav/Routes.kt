package com.dskmusic.pixa.ui.nav

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Destinos de navegacion. Rutas planas: la app tiene un solo grafo. */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val TRASH = "trash"
    const val TUTORIAL = "tutorial"
    const val NEW_CANVAS = "new_canvas"
    const val EDITOR = "editor"
    const val SOCIAL = "social"
    const val MEME = "meme"
    const val BATCH = "batch"
    const val STITCH = "stitch"
    const val COLLAGE = "collage"
}

/** Acciones de los atajos del launcher y del widget (requisito 2). */
object PixaActions {
    const val NEW_CANVAS = "com.dskmusic.pixa.action.NEW_CANVAS"
    const val CAMERA = "com.dskmusic.pixa.action.CAMERA"
    const val LAST_PROJECT = "com.dskmusic.pixa.action.LAST_PROJECT"
}

/**
 * Lo que la app debe hacer al arrancar, deducido del Intent (requisito 5).
 *
 * Se separa del parseo para que la UI reaccione a un solo valor y para que una segunda
 * llegada (onNewIntent) se trate igual que la primera.
 */
sealed interface LaunchRequest {
    data object None : LaunchRequest

    /** Abrir el dialogo de lienzo nuevo. */
    data object NewCanvas : LaunchRequest

    /** Lanzar la camara y editar la foto. */
    data object Camera : LaunchRequest

    /** Abrir el ultimo proyecto editado (widget). */
    data object LastProject : LaunchRequest

    /** Imagen recibida de otra app: va al editor completo. */
    data class EditImage(val uri: Uri, val externalEdit: Boolean = false) : LaunchRequest

    /** Varias imagenes recibidas: van a unir fotos ya cargadas. */
    data class StitchWith(val uris: List<Uri>) : LaunchRequest

    /** Proyecto .pixa recibido desde un archivo. */
    data class OpenProject(val uri: Uri) : LaunchRequest

    /** Copia de seguridad .pixabak recibida desde un archivo. */
    data class ImportBackup(val uri: Uri) : LaunchRequest

    companion object {
        fun from(intent: Intent?): LaunchRequest {
            if (intent == null) return None
            return when (intent.action) {
                PixaActions.NEW_CANVAS -> NewCanvas
                PixaActions.CAMERA -> Camera
                PixaActions.LAST_PROJECT -> LastProject

                Intent.ACTION_SEND -> {
                    val uri = IntentCompat.getParcelableExtra(
                        intent, Intent.EXTRA_STREAM, Uri::class.java,
                    )
                    uri?.let { EditImage(it) } ?: None
                }

                Intent.ACTION_SEND_MULTIPLE -> {
                    val uris = IntentCompat.getParcelableArrayListExtra(
                        intent, Intent.EXTRA_STREAM, Uri::class.java,
                    )?.filterNotNull().orEmpty()
                    when {
                        uris.size > 1 -> StitchWith(uris)
                        uris.size == 1 -> EditImage(uris[0])
                        else -> None
                    }
                }

                Intent.ACTION_EDIT, Intent.ACTION_VIEW -> {
                    val uri = intent.data ?: return None
                    // Un .pixa o .pixabak llegado por "abrir con" no es una foto.
                    when {
                        isBackup(intent, uri) -> ImportBackup(uri)
                        isProject(intent, uri) -> OpenProject(uri)
                        // "Editar/Abrir con" desde otra app: al terminar se cierra Pixa y se
                        // devuelve el control a la app de origen.
                        else -> EditImage(uri, externalEdit = true)
                    }
                }

                else -> None
            }
        }

        private fun isProject(intent: Intent, uri: Uri): Boolean {
            if (intent.type == "application/x-pixa") return true
            val path = uri.lastPathSegment ?: uri.path ?: return false
            return path.endsWith(".pixa", ignoreCase = true)
        }

        private fun isBackup(intent: Intent, uri: Uri): Boolean {
            if (intent.type == "application/x-pixa-backup") return true
            val path = uri.lastPathSegment ?: uri.path ?: return false
            return path.endsWith(".pixabak", ignoreCase = true)
        }
    }
}
