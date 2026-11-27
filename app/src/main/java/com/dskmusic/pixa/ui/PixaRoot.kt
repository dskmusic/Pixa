package com.dskmusic.pixa.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dskmusic.pixa.data.settings.AppLanguage
import com.dskmusic.pixa.core.export.Exporter
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.about.AboutScreen
import com.dskmusic.pixa.ui.batch.BatchScreen
import com.dskmusic.pixa.ui.common.rememberCameraCapture
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.editor.EditorPanel
import com.dskmusic.pixa.ui.editor.EditorScreen
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.home.HomeScreen
import com.dskmusic.pixa.ui.icon.IconMakerScreen
import com.dskmusic.pixa.ui.wordart.WordArtScreen
import com.dskmusic.pixa.ui.home.NewCanvasScreen
import com.dskmusic.pixa.ui.home.TrashScreen
import com.dskmusic.pixa.ui.meme.MemeScreen
import com.dskmusic.pixa.ui.nav.LaunchRequest
import com.dskmusic.pixa.ui.collage.CollageScreen
import com.dskmusic.pixa.ui.nav.Routes
import com.dskmusic.pixa.ui.settings.SettingsScreen
import com.dskmusic.pixa.ui.social.SocialScreen
import com.dskmusic.pixa.ui.stitch.StitchScreen
import com.dskmusic.pixa.ui.tutorial.TutorialScreen
import com.dskmusic.pixa.ui.theme.PixaTheme

/**
 * Raiz de la app: tema, idioma y grafo de navegacion.
 *
 * El EditorViewModel se crea aqui, fuera del NavHost, de modo que su ciclo de vida es el
 * de la actividad: los generadores (posts sociales, memes, unir fotos...) pueden dejar un
 * documento preparado y navegar al editor sin serializar nada, y volver atras no pierde
 * el trabajo (requisito 1: sin perdida de estado).
 */
@Composable
fun PixaRoot(
    request: LaunchRequest,
    onRequestHandled: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.pixa
    val settings by container.settings.settings.collectAsState(initial = null)
    val current = settings

    // Mientras no llega el primer valor de DataStore no se pinta nada: evita el parpadeo
    // de tema claro antes de aplicar el elegido.
    if (current == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    LaunchedEffect(current.language) {
        applyLanguage(current.language)
    }

    // Requisito del usuario: el permiso de "acceso a todos los archivos" se comprueba y pide
    // al iniciar (no al guardar), porque es necesario ya al ABRIR una imagen para leer su
    // nombre y ruta reales. Se pide una sola vez por arranque, y al volver (ON_RESUME)
    // se verifica si ya se concedio.
    var promptedThisLaunch by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!promptedThisLaunch &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !Exporter.canAccessAllFiles()
                ) {
                    promptedThisLaunch = true
                    context.findActivity()?.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PixaTheme(
        accent = Color(current.accentColor),
        themeMode = current.themeMode,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            // Los insets se aplican y se consumen aqui, una sola vez: ninguna pantalla
            // queda debajo de la barra de estado, de la de navegacion ni de un notch.
            // safeDrawing incluye ademas el teclado, asi que los formularios suben solos.
            // Los Scaffold de dentro llevan contentWindowInsets = WindowInsets(0) para no
            // sumar el mismo margen dos veces.
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                PixaNavHost(
                    settings = current,
                    request = request,
                    onRequestHandled = onRequestHandled,
                )
            }
        }
    }
}

private fun applyLanguage(language: AppLanguage) {
    val tag = language.tag
    val desired = if (tag == null) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(tag)
    }
    if (AppCompatDelegate.getApplicationLocales() != desired) {
        AppCompatDelegate.setApplicationLocales(desired)
    }
}

@Composable
private fun PixaNavHost(
    settings: PixaSettings,
    request: LaunchRequest,
    onRequestHandled: () -> Unit,
) {
    val nav = rememberNavController()
    val editor: EditorViewModel = viewModel()

    // Fotos que un generador debe recibir ya cargadas (compartir varias imagenes con la
    // app). Se guardan aqui porque una lista de Uri no cabe bien en una ruta de
    // navegacion.
    var pendingPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }

    fun openEditorWithUri(uri: Uri, external: Boolean, panel: EditorPanel? = null) {
        editor.launchedExternally = external
        editor.startFromUri(uri, openPanel = panel)
        nav.navigateSingle(Routes.EDITOR)
    }
    val pickForEditor = rememberImagePicker { openEditorWithUri(it, false) }
    val cameraForEditor = rememberCameraCapture { uri -> uri?.let { openEditorWithUri(it, false) } }
    // Requisito del usuario: acceso directo de Home a "quitar fondo", que entra al editor
    // con ese panel ya desplegado en vez de dejar al usuario buscarlo.
    val pickForBackgroundRemoval = rememberImagePicker { uri ->
        editor.launchedExternally = false
        editor.startFromUri(uri, openPanel = EditorPanel.BACKGROUND)
        nav.navigateSingle(Routes.EDITOR)
    }

    // Accesos directos de Inicio a la seccion IA: restaurar foto y el panel completo.
    // Ajustes rapidos: abre la foto directamente en el panel Ajustes (brillo, contraste...).
    val pickForQuickAdjust = rememberImagePicker { uri ->
        editor.launchedExternally = false
        editor.aiLaunch = null
        editor.startFromUri(uri, openPanel = EditorPanel.ADJUST)
        nav.navigateSingle(Routes.EDITOR)
    }
    val pickForEffects = rememberImagePicker { uri ->
        editor.launchedExternally = false
        editor.aiLaunch = null
        editor.startFromUri(uri, openPanel = EditorPanel.EFFECTS)
        nav.navigateSingle(Routes.EDITOR)
    }
    val pickForFilters = rememberImagePicker { uri ->
        editor.launchedExternally = false
        editor.aiLaunch = null
        editor.startFromUri(uri, openPanel = EditorPanel.FILTERS)
        nav.navigateSingle(Routes.EDITOR)
    }
    val pickForAiTools = rememberImagePicker { uri ->
        editor.launchedExternally = false
        editor.aiLaunch = null
        editor.startFromUri(uri, openPanel = EditorPanel.AI)
        nav.navigateSingle(Routes.EDITOR)
    }

    // Primer arranque: el tutorial se muestra una vez y se puede saltar (requisito 1).
    LaunchedEffect(settings.tutorialDone) {
        if (!settings.tutorialDone) nav.navigateSingle(Routes.TUTORIAL)
    }

    LaunchedEffect(request) {
        when (request) {
            LaunchRequest.None -> return@LaunchedEffect
            LaunchRequest.NewCanvas -> nav.navigateSingle(Routes.NEW_CANVAS)
            LaunchRequest.Camera -> cameraForEditor()
            LaunchRequest.LastProject -> {
                val id = settings.lastProjectId
                if (id != null) {
                    editor.openProject(id)
                    nav.navigateSingle(Routes.EDITOR)
                }
            }
            // Imagen llegada de otra app: abre directo en Recortar si el ajuste esta activo.
            is LaunchRequest.EditImage -> openEditorWithUri(
                request.uri, request.externalEdit,
                if (settings.cropOnExternalOpen) EditorPanel.CROP else null,
            )
            is LaunchRequest.StitchWith -> {
                pendingPhotos = request.uris
                nav.navigateSingle(Routes.STITCH)
            }
            is LaunchRequest.OpenProject -> {
                editor.importProject(request.uri, "Pixa")
                nav.navigateSingle(Routes.EDITOR)
            }
            is LaunchRequest.ImportBackup -> {
                pendingBackupUri = request.uri
                nav.navigateSingle(Routes.SETTINGS)
            }
        }
        onRequestHandled()
    }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(220),
            ) + fadeIn(tween(220))
        },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(220),
            ) + fadeOut(tween(220))
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                editor = editor,
                settings = settings,
                onOpenSettings = { nav.navigateSingle(Routes.SETTINGS) },
                onOpenAbout = { nav.navigateSingle(Routes.ABOUT) },
                onNewCanvas = { nav.navigateSingle(Routes.NEW_CANVAS) },
                onPickImage = pickForEditor,
                onCamera = cameraForEditor,
                onPickBackgroundRemoval = pickForBackgroundRemoval,
                onPickQuickAdjust = pickForQuickAdjust,
                onPickAiTools = pickForAiTools,
                onPickFilters = pickForFilters,
                onPickEffects = pickForEffects,
                onOpenEditor = { nav.navigateSingle(Routes.EDITOR) },
                onOpenTrash = { nav.navigateSingle(Routes.TRASH) },
                onCreate = { route ->
                    pendingPhotos = emptyList()
                    nav.navigateSingle(route)
                },
            )
        }

        composable(Routes.NEW_CANVAS) {
            NewCanvasScreen(
                onBack = { nav.popBackStack() },
                onCreate = { width, height, background ->
                    editor.startNew(width, height, background)
                    nav.navigateReplacing(Routes.EDITOR)
                },
            )
        }

        composable(Routes.EDITOR) {
            val activity = LocalContext.current.findActivity()
            EditorScreen(
                vm = editor,
                settings = settings,
                // Si la app se lanzo desde otra app como editor (p.ej. "Editar con" desde la
                // Galeria), al salir se cierra Pixa para devolver el control a la app origen;
                // si no, se vuelve a la pantalla de inicio como siempre.
                onExit = {
                    if (editor.launchedExternally) {
                        activity?.finish()
                    } else {
                        nav.popToHome()
                    }
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenTutorial = { nav.navigateSingle(Routes.TUTORIAL) },
                onOpenAbout = { nav.navigateSingle(Routes.ABOUT) },
                pendingBackupUri = pendingBackupUri,
                onPendingBackupHandled = { pendingBackupUri = null },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.TRASH) {
            TrashScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.TUTORIAL) {
            TutorialScreen(onDone = { nav.popBackStack() })
        }

        composable(Routes.SOCIAL) {
            SocialScreen(
                editor = editor,
                onBack = { nav.popBackStack() },
                onOpenEditor = { nav.navigateReplacing(Routes.EDITOR) },
            )
        }

        composable(Routes.ICON_MAKER) {
            IconMakerScreen(
                editor = editor,
                onBack = { nav.popBackStack() },
                onOpenEditor = { nav.navigateReplacing(Routes.EDITOR) },
            )
        }

        composable(Routes.WORD_ART) {
            WordArtScreen(
                editor = editor,
                onBack = { nav.popBackStack() },
                onOpenEditor = { nav.navigateReplacing(Routes.EDITOR) },
            )
        }

        composable(Routes.MEME) {
            MemeScreen(
                editor = editor,
                onBack = { nav.popBackStack() },
                onOpenEditor = { nav.navigateReplacing(Routes.EDITOR) },
            )
        }

        composable(Routes.BATCH) {
            BatchScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.COLLAGE) {
            CollageScreen(
                editor = editor,
                onBack = { nav.popBackStack() },
                onOpenEditor = { nav.navigateReplacing(Routes.EDITOR) },
            )
        }

        composable(Routes.STITCH) {
            StitchScreen(
                editor = editor,
                initialPhotos = pendingPhotos,
                onPhotosConsumed = { pendingPhotos = emptyList() },
                onBack = { nav.popBackStack() },
                onOpenEditor = { nav.navigateReplacing(Routes.EDITOR) },
            )
        }
    }
}

/** Navega evitando duplicar el destino si ya esta arriba de la pila. */
private fun NavHostController.navigateSingle(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}

/** Sube hasta la Activity que envuelve un Context (compose), o null si no la hay. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Navega sustituyendo el destino actual: el generador no debe quedar detras del editor. */
private fun NavHostController.navigateReplacing(route: String) {
    val from = currentDestination?.route
    navigate(route) {
        launchSingleTop = true
        if (from != null) popUpTo(from) { inclusive = true }
    }
}

private fun NavHostController.popToHome() {
    popBackStack(Routes.HOME, inclusive = false)
}
