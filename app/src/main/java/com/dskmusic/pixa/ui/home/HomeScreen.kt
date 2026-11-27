package com.dskmusic.pixa.ui.home

import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.foundation.layout.ColumnScope
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.dskmusic.pixa.ui.common.PixaToastHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.project.ProjectEntry
import com.dskmusic.pixa.core.project.RecentItem
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ui.common.ConfirmDialog
import com.dskmusic.pixa.ui.common.TextPromptDialog
import com.dskmusic.pixa.ui.common.clipboardImage
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.panels.StockImageDialog
import com.dskmusic.pixa.ui.nav.Routes
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.core.project.DraftStore
import kotlinx.coroutines.launch
import java.io.File

/** Una accion de la pantalla de inicio: icono, etiqueta y que hacer al pulsarla. */
private data class HomeAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val sublabel: String? = null,
)

@Composable
fun HomeScreen(
    editor: EditorViewModel,
    settings: PixaSettings,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onNewCanvas: () -> Unit,
    onPickImage: () -> Unit,
    onCamera: () -> Unit,
    onPickBackgroundRemoval: () -> Unit,
    onPickQuickAdjust: () -> Unit = {},
    onPickAiTools: () -> Unit = {},
    onPickFilters: () -> Unit = {},
    onPickEffects: () -> Unit = {},
    onOpenEditor: () -> Unit,
    onCreate: (String) -> Unit,
    onOpenTrash: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    var stockDialogOpen by remember { mutableStateOf(false) }
    val projects by vm.projects.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val clipboardEmpty = stringResource(R.string.home_clipboard_empty)

    // La lista se refresca al volver de cualquier pantalla, no solo al crearse.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Recuperacion tras un cierre inesperado (requisito 5).
    var offerRecovery by remember { mutableStateOf(editor.hasAutosave() && !editor.hasDocument) }
    // Borradores de los generadores (collage / comic, memes, unir fotos, posts): al
    // recuperarlos se vuelve al propio generador, no al editor.
    val draftStore = LocalContext.current.pixa.drafts
    var pendingDrafts by remember { mutableStateOf(draftStore.pending()) }

    // Seleccion multiple de recientes: se entra manteniendo pulsado y se sale al vaciarla.
    var pickedRecents by remember { mutableStateOf(emptySet<String>()) }
    var confirmRemoveRecents by remember { mutableStateOf(false) }
    var confirmClearRecents by remember { mutableStateOf(false) }
    var showAllRecents by remember { mutableStateOf(false) }
    var showAllProjects by remember { mutableStateOf(false) }

    // Seleccion multiple de proyectos guardados: igual que la de recientes.
    var pickedProjects by remember { mutableStateOf(emptySet<String>()) }
    var confirmRemoveProjects by remember { mutableStateOf(false) }

    // La lista se relee al volver de otra pantalla: la seleccion no puede quedarse
    // apuntando a elementos que ya no existen.
    val recentIds = recents.map { it.id }
    LaunchedEffect(recentIds) {
        pickedRecents = pickedRecents.intersect(recentIds.toSet())
    }
    val projectIds = projects.map { it.id }
    LaunchedEffect(projectIds) {
        pickedProjects = pickedProjects.intersect(projectIds.toSet())
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { PixaToastHost(snackbar) },
        topBar = {
            // Superficie propia y con sombra: se nota donde acaba el encabezado y
            // empiezan los botones de accion, en vez de flotar los dos sobre el mismo
            // fondo plano.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Requisito del usuario: icono de la app a la izquierda del logotipo,
                    // pegado al nombre "Pixa".
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    AnimatedLogo(
                        cycleColors = settings.whiteLogoCycle && settings.accentColor.toLong().and(0xFFFFFFL) == 0xFFFFFFL,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, stringResource(R.string.cd_about))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.cd_settings))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (offerRecovery) {
                RecoveryCard(
                    onRestore = {
                        offerRecovery = false
                        editor.restoreAutosave()
                        onOpenEditor()
                    },
                    onDiscard = {
                        offerRecovery = false
                        editor.discardAutosave()
                    },
                )
            }

            pendingDrafts.forEach { kind ->
                val (labelRes, route) = when (kind) {
                    DraftStore.COLLAGE -> R.string.collage_title to Routes.COLLAGE
                    DraftStore.MEME -> R.string.meme_title to Routes.MEME
                    DraftStore.STITCH -> R.string.stitch_title to Routes.STITCH
                    else -> R.string.social_title to Routes.SOCIAL
                }
                RecoveryCard(
                    title = stringResource(R.string.draft_title),
                    body = stringResource(R.string.draft_body, stringResource(labelRes)),
                    onRestore = {
                        pendingDrafts = pendingDrafts - kind
                        draftStore.requestRestore(kind)
                        onCreate(route)
                    },
                    onDiscard = {
                        pendingDrafts = pendingDrafts - kind
                        draftStore.clear(kind)
                    },
                )
            }

            Spacer(Modifier.height(10.dp))

            // Requisito del usuario: las cuatro acciones principales en 2x2, compactas y
            // con una sola linea de texto (sin descripcion).
            // Orden 2x2 -> abrir imagen / abrir camara (fila superior), nueva imagen /
            // pegar imagen (fila inferior).
            val primaryActions = listOf(
                HomeAction(
                    Icons.Filled.PhotoLibrary,
                    stringResource(R.string.home_open_image),
                    onPickImage,
                ),
                HomeAction(
                    Icons.Filled.PhotoCamera,
                    stringResource(R.string.home_camera),
                    onCamera,
                ),
                HomeAction(
                    Icons.Filled.Brush,
                    stringResource(R.string.home_new_canvas),
                    onNewCanvas,
                ),
                HomeAction(
                    Icons.Filled.ContentPaste,
                    stringResource(R.string.home_paste),
                    onClick = {
                        val uri = clipboardImage(context)
                        if (uri == null) {
                            scope.launch { snackbar.showSnackbar(clipboardEmpty) }
                        } else {
                            editor.startFromUri(uri)
                            onOpenEditor()
                        }
                    },
                ),
            )
            PrimaryGrid(actions = primaryActions, startIndex = 0)

            Spacer(Modifier.height(12.dp))

            // Requisito del usuario: "Herramientas" entre las cuatro principales y las
            // seis de abajo, alineado a la izquierda y en negrita.
            Text(
                text = stringResource(R.string.home_tools),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Las seis acciones restantes en 2x3, con el texto en una sola linea.
            val secondaryActions = listOf(
                HomeAction(
                    Icons.Filled.Interests,
                    stringResource(R.string.ic_create_icon),
                    onClick = { onCreate(Routes.ICON_MAKER) },
                ),
                HomeAction(
                    Icons.Filled.BrokenImage,
                    stringResource(R.string.bg_remove_title),
                    onPickBackgroundRemoval,
                ),
                HomeAction(
                    Icons.Filled.Psychology,
                    stringResource(R.string.home_ai_tools),
                    onPickAiTools,
                ),
                HomeAction(
                    Icons.Filled.Dashboard,
                    stringResource(R.string.create_collage),
                    onClick = { onCreate(Routes.COLLAGE) },
                ),
                HomeAction(
                    Icons.Filled.EmojiEmotions,
                    stringResource(R.string.create_meme),
                    onClick = { onCreate(Routes.MEME) },
                ),
                HomeAction(
                    Icons.Filled.ViewCarousel,
                    stringResource(R.string.create_social),
                    onClick = { onCreate(Routes.SOCIAL) },
                ),
                HomeAction(
                    Icons.Filled.Collections,
                    stringResource(R.string.stock_images_title),
                    onClick = { stockDialogOpen = true },
                ),
                HomeAction(
                    Icons.Filled.ViewDay,
                    stringResource(R.string.create_stitch),
                    onClick = { onCreate(Routes.STITCH) },
                ),
                HomeAction(
                    Icons.Filled.GridView,
                    stringResource(R.string.create_batch),
                    onClick = { onCreate(Routes.BATCH) },
                ),
                // Nueva fila bajo "Imagenes stock".
                HomeAction(
                    Icons.Filled.Cloud,
                    stringResource(R.string.home_word_art),
                    onClick = { onCreate(Routes.WORD_ART) },
                ),
                // Accesos directos provisionales que rellenan la fila hasta que haya
                // herramientas propias: abren la foto elegida directamente en un panel.
                HomeAction(
                    Icons.Filled.AutoAwesome,
                    stringResource(R.string.fx_title),
                    onPickEffects,
                ),
                HomeAction(
                    Icons.Filled.PhotoFilter,
                    stringResource(R.string.filters_title),
                    onPickFilters,
                ),
            )
            ActionGrid(actions = secondaryActions, columns = 3, startIndex = 4)

            Spacer(Modifier.height(20.dp))

            // Requisito del usuario: la seccion de recientes siempre es visible; si no
            // hay contenido se muestra una tarjeta de estado vacio.
            // Dos columnas de media anchura: Recientes a la izquierda y Proyectos a la derecha.
            // Cada lista crece hacia abajo (4 elementos) y lleva debajo el "+" y la papelera.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // ---------------- Recientes ----------------
                Column(modifier = Modifier.weight(1f)) {
                    ColumnHeader(
                        title = stringResource(R.string.home_recents),
                        pickedCount = pickedRecents.size,
                        onDeletePicked = { confirmRemoveRecents = true },
                        onClearPicked = { pickedRecents = emptySet() },
                    )
                    GrayPanel {
                    if (recents.isEmpty()) {
                        CompactEmpty(Icons.Outlined.History, stringResource(R.string.home_no_recents))
                    } else {
                        recents.take(4).forEach { item ->
                            RecentRow(
                                file = vm.recentThumb(item.id),
                                title = item.label,
                                picked = item.id in pickedRecents,
                                onClick = {
                                    if (pickedRecents.isNotEmpty()) {
                                        pickedRecents = pickedRecents.toggled(item.id)
                                    } else if (openRecent(editor, vm, item)) {
                                        onOpenEditor()
                                    }
                                },
                                onLongClick = { pickedRecents = pickedRecents.toggled(item.id) },
                            )
                        }
                    }
                    if (pickedRecents.isEmpty()) {
                        ColumnFooter(
                            moreEnabled = recents.isNotEmpty(),
                            moreDescription = stringResource(R.string.home_recents_see_all),
                            onMore = { showAllRecents = true },
                            trashEnabled = recents.isNotEmpty(),
                            trashDescription = stringResource(R.string.home_recents_clear),
                            onTrash = { confirmClearRecents = true },
                        )
                    }
                    }
                }

                // ---------------- Proyectos ----------------
                Column(modifier = Modifier.weight(1f)) {
                    ColumnHeader(
                        title = stringResource(R.string.home_projects),
                        pickedCount = pickedProjects.size,
                        onDeletePicked = { confirmRemoveProjects = true },
                        onClearPicked = { pickedProjects = emptySet() },
                    )
                    GrayPanel {
                    if (projects.isEmpty()) {
                        CompactEmpty(Icons.Outlined.FolderOpen, stringResource(R.string.home_no_projects))
                    } else {
                        projects.take(4).forEach { entry ->
                            ProjectCompactRow(
                                entry = entry,
                                thumb = vm.projectThumb(entry.id),
                                picked = entry.id in pickedProjects,
                                onOpen = {
                                    editor.openProject(entry.id)
                                    onOpenEditor()
                                },
                                onClick = {
                                    if (pickedProjects.isNotEmpty()) {
                                        pickedProjects = pickedProjects.toggled(entry.id)
                                    } else {
                                        editor.openProject(entry.id)
                                        onOpenEditor()
                                    }
                                },
                                onLongClick = { pickedProjects = pickedProjects.toggled(entry.id) },
                                onRename = { vm.rename(entry.id, it) },
                                onDuplicate = { vm.duplicate(entry.id) },
                                onDelete = { vm.moveToTrash(entry.id) },
                            )
                        }
                    }
                    if (pickedProjects.isEmpty()) {
                        // La papelera de proyectos abre la pantalla con los proyectos borrados.
                        ColumnFooter(
                            moreEnabled = projects.isNotEmpty(),
                            moreDescription = stringResource(R.string.home_recents_see_all),
                            onMore = { showAllProjects = true },
                            trashEnabled = true,
                            trashDescription = stringResource(R.string.home_trash),
                            onTrash = onOpenTrash,
                        )
                    }
                    }
                }
            }

            if (confirmRemoveRecents) {
                ConfirmDialog(
                    title = stringResource(R.string.home_recents_remove),
                    body = stringResource(R.string.home_recents_remove_confirm, pickedRecents.size),
                    onConfirm = {
                        pickedRecents.forEach(vm::removeRecent)
                        pickedRecents = emptySet()
                    },
                    onDismiss = { confirmRemoveRecents = false },
                )
            }
            if (confirmClearRecents) {
                ConfirmDialog(
                    title = stringResource(R.string.home_recents_clear),
                    body = stringResource(R.string.home_recents_clear_confirm),
                    onConfirm = {
                        vm.clearRecents()
                        pickedRecents = emptySet()
                    },
                    onDismiss = { confirmClearRecents = false },
                )
            }
            if (confirmRemoveProjects) {
                ConfirmDialog(
                    title = stringResource(R.string.action_delete),
                    body = stringResource(R.string.home_projects_remove_confirm, pickedProjects.size),
                    onConfirm = {
                        pickedProjects.forEach(vm::moveToTrash)
                        pickedProjects = emptySet()
                    },
                    onDismiss = { confirmRemoveProjects = false },
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showAllRecents) {
        AllRecentsDialog(
            recents = recents,
            vm = vm,
            onOpen = { item ->
                showAllRecents = false
                if (openRecent(editor, vm, item)) onOpenEditor()
            },
            onDismiss = { showAllRecents = false },
        )
    }

    if (showAllProjects) {
        AllProjectsDialog(
            projects = projects,
            vm = vm,
            onOpen = { entry ->
                showAllProjects = false
                editor.openProject(entry.id)
                onOpenEditor()
            },
            onDismiss = { showAllProjects = false },
        )
    }

    if (stockDialogOpen) {
        // Requisito del usuario: buscador de Pixabay accesible desde Home, sin documento
        // abierto todavia; la foto elegida abre un documento nuevo en el editor.
        StockImageDialog(
            vm = editor,
            doc = null,
            settings = settings,
            onDismiss = { stockDialogOpen = false },
            onPickForNewDocument = { bitmap ->
                stockDialogOpen = false
                editor.startFromBitmap(bitmap)
                onOpenEditor()
            },
        )
    }
}

/**
 * Nombre de la app con un efecto de color en bucle (requisito del usuario): discreto, no
 * un arcoiris completo, ciclando entre tres colores del propio tema.
 */
@Composable
private fun AnimatedLogo(cycleColors: Boolean, modifier: Modifier = Modifier) {
    // Acento blanco: ni el blanco ni los grises del tema se distinguen, asi que cicla tres tonos vivos.
    val primary = if (cycleColors) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.primary
    val tertiary = if (cycleColors) Color(0xFF4DD0E1) else MaterialTheme.colorScheme.tertiary
    val secondary = if (cycleColors) Color(0xFFFFD54F) else MaterialTheme.colorScheme.secondary
    val transition = rememberInfiniteTransition(label = "logo")
    val color by transition.animateColor(
        initialValue = primary,
        targetValue = primary,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 7000
                primary at 0
                tertiary at 2300
                secondary at 4600
                primary at 7000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "logoColor",
    )
    // Requisito del usuario: "Pixa" mas grande y en negrita, "by DSK" mas pequeno y
    // separado unos px a la derecha.
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        Text(
            stringResource(R.string.app_name_short),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.app_name_suffix),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}

/**
 * Ventana con todos los recientes (requisito del usuario): la Home solo ensena tres, aqui
 * esta el listado completo con la misma seleccion multiple y borrado con confirmacion.
 */
@Composable
private fun AllRecentsDialog(
    recents: List<RecentItem>,
    vm: HomeViewModel,
    onOpen: (RecentItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var confirmRemove by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                    }
                    Text(
                        stringResource(R.string.home_recents_all_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (picked.isNotEmpty()) {
                        IconButton(onClick = { confirmRemove = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.home_recents_remove))
                        }
                        IconButton(onClick = { picked = emptySet() }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_cancel))
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(72.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    gridItems(recents, key = { it.id }) { item ->
                        ThumbTile(
                            file = vm.recentThumb(item.id),
                            title = item.label,
                            picked = item.id in picked,
                            onClick = {
                                if (picked.isNotEmpty()) {
                                    picked = picked.toggled(item.id)
                                } else {
                                    onOpen(item)
                                }
                            },
                            onLongClick = { picked = picked.toggled(item.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.home_recents_remove),
            body = stringResource(R.string.home_recents_remove_confirm, picked.size),
            onConfirm = {
                picked.forEach(vm::removeRecent)
                picked = emptySet()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

/**
 * Ventana con todos los proyectos guardados (requisito del usuario): la Home solo
 * ensena tres, aqui esta el listado completo, con el mismo menu de cada fila
 * (abrir, renombrar, duplicar, borrar) y seleccion multiple para borrar con
 * confirmacion.
 */
@Composable
private fun AllProjectsDialog(
    projects: List<ProjectEntry>,
    vm: HomeViewModel,
    onOpen: (ProjectEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var confirmRemove by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                    }
                    Text(
                        if (picked.isEmpty()) {
                            stringResource(R.string.home_projects_all_title)
                        } else {
                            stringResource(R.string.home_recents_picked, picked.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (picked.isNotEmpty()) {
                        IconButton(onClick = { confirmRemove = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                        }
                        IconButton(onClick = { picked = emptySet() }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_cancel))
                        }
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(projects, key = { it.id }) { entry ->
                        ProjectRow(
                            entry = entry,
                            thumb = vm.projectThumb(entry.id),
                            picked = entry.id in picked,
                            onOpen = { onOpen(entry) },
                            onClick = {
                                if (picked.isNotEmpty()) {
                                    picked = picked.toggled(entry.id)
                                } else {
                                    onOpen(entry)
                                }
                            },
                            onLongClick = { picked = picked.toggled(entry.id) },
                            onRename = { vm.rename(entry.id, it) },
                            onDuplicate = { vm.duplicate(entry.id) },
                            onDelete = { vm.moveToTrash(entry.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            body = stringResource(R.string.home_projects_remove_confirm, picked.size),
            onConfirm = {
                picked.forEach(vm::moveToTrash)
                picked = emptySet()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

// ======================================================================================
// Piezas
// ======================================================================================

@Composable
private fun RecoveryCard(
    onRestore: () -> Unit,
    onDiscard: () -> Unit,
    title: String = stringResource(R.string.settings_autosave),
    body: String = stringResource(R.string.settings_autosave_sub),
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_discard)) }
                TextButton(onClick = onRestore) { Text(stringResource(R.string.action_restore)) }
            }
        }
    }
}

/** Color del icono y de su insignia, por indice: tres tonos del tema que se alternan
 * para que la rejilla no se vea de un solo color plano (requisito de diseno del usuario). */
/**
 * Titulo de seccion de Home, sin el relleno asimetrico de [SectionTitle] (pensado para
 * las cabeceras de panel del editor): aqui el relleno vive en la fila que lo contiene, el
 * mismo para "Recientes" y "Proyectos guardados", asi quedan igual de separados del
 * separador de arriba y de su contenido de abajo (requisito del usuario).
 */
@Composable
private fun HomeSectionTitle(text: String, modifier: Modifier = Modifier) {
    // Requisito del usuario: mismo tamanio y estilo que el encabezado "Herramientas".
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun accentFor(index: Int): Pair<Color, Color> = when (index % 3) {
    0 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    1 -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun StartAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    badge: Color = MaterialTheme.colorScheme.primaryContainer,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Insignia de color detras del icono: sin ella, la rejilla de acciones era
            // una fila de tarjetas identicas y costaba distinguir una accion de otra.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            // fillMaxWidth + Center + una sola linea con ellipsis: el texto se muestra
            // en una unica linea con espacios, sin partirse en dos.
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Rejilla de acciones de ancho fijo por celda. No se usa LazyVerticalGrid porque esta
 * dentro de una columna con scroll propio y anidar dos contenedores desplazables da
 * problemas. [startIndex] es el indice global desde el que se asignan los colores.
 */
@Composable
private fun ActionGrid(
    actions: List<HomeAction>,
    columns: Int,
    startIndex: Int,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        actions.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, action ->
                    val index = startIndex + rowIndex * columns + colIndex
                    StartAction(
                        icon = action.icon,
                        label = action.label,
                        modifier = Modifier.weight(1f),
                        tint = accentFor(index).first,
                        badge = accentFor(index).second,
                        onClick = action.onClick,
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Rejilla 2x2 de las acciones principales, con un diseno distinto al del resto: icono a
 * la izquierda, enunciado arriba y una descripcion pequena debajo.
 */
@Composable
private fun PrimaryGrid(actions: List<HomeAction>, startIndex: Int) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        actions.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, action ->
                    val index = startIndex + rowIndex * 2 + colIndex
                    PrimaryActionCard(
                        action = action,
                        modifier = Modifier.weight(1f),
                        tint = accentFor(index).first,
                        badge = accentFor(index).second,
                    )
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Tarjeta de accion principal: icono a la izquierda, titulo y descripcion a la derecha. */
@Composable
private fun PrimaryActionCard(
    action: HomeAction,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    badge: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = action.onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                action.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Estado vacio: icono centrado, titulo y una descripcion secundaria, sin caja de fondo.
 * Se adapta al tema (claro u oscuro) a traves de los colores de MaterialTheme y usa el
 * color de acento para el icono.
 */
@Composable
private fun EmptyCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    // Requisito del usuario: sin caja de fondo; el contenido va directamente sobre el
    // fondo de la pantalla, centrado y pegado hacia arriba (poco relleno superior).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Anade o quita un elemento de la seleccion. */
/**
 * Abre un reciente por el camino mas fiable disponible: proyecto completo si se guardo
 * (conserva todas las capas), copia local si no (bug encontrado: la uri del selector de
 * fotos deja de tener permiso en cuanto la app se reinicia), y la uri externa solo como
 * ultimo recurso para recientes antiguos de antes de este cambio. Devuelve si pudo abrir
 * algo, para no navegar al editor cuando no hay nada que mostrar.
 */
private fun openRecent(editor: EditorViewModel, vm: HomeViewModel, item: RecentItem): Boolean {
    val projectId = item.projectId
    return when {
        projectId != null -> {
            editor.openProject(projectId)
            true
        }
        item.hasLocalCopy -> {
            editor.startFromRecentFile(
                vm.recentSourceFile(item.id),
                item.label,
                item.sourceUri?.let(Uri::parse),
            )
            true
        }
        item.sourceUri != null -> {
            editor.startFromUri(Uri.parse(item.sourceUri))
            true
        }
        else -> false
    }
}

private fun Set<String>.toggled(id: String): Set<String> =
    if (contains(id)) this - id else this + id

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbTile(
    file: File,
    title: String,
    picked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Requisito del usuario: al 60% del tamano anterior, para ganar espacio vertical.
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.cd_project_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            // Borde y marca a la vez: la seleccion se tiene que notar tambien sin
            // depender del color.
            if (picked) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp),
                        ),
                )
                Surface(
                    modifier = Modifier.padding(4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.home_recents_picked_cd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(1.dp)
                            .size(12.dp),
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
    entry: ProjectEntry,
    thumb: File,
    picked: Boolean,
    onOpen: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = thumb,
            contentDescription = stringResource(R.string.cd_project_thumb),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.width} x ${entry.height}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (picked) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.home_recents_picked_cd),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(14.dp),
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_open)) },
                    onClick = { menuOpen = false; onOpen() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = { menuOpen = false; renaming = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_duplicate)) },
                    onClick = { menuOpen = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { menuOpen = false; confirmDelete = true },
                )
            }
        }
    }

    if (renaming) {
        TextPromptDialog(
            title = stringResource(R.string.action_rename),
            initial = entry.name,
            onConfirm = onRename,
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.action_confirm_delete),
            body = stringResource(R.string.trash_info),
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

// ======================================================================================
// Columnas de Recientes y Proyectos
// ======================================================================================

/** Titulo de columna; con elementos elegidos muestra cuantos y los botones de borrar y cancelar. */
@Composable
private fun ColumnHeader(
    title: String,
    pickedCount: Int,
    onDeletePicked: () -> Unit,
    onClearPicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            HomeSectionTitle(
                if (pickedCount == 0) title else stringResource(R.string.home_recents_picked, pickedCount),
            )
        }
        if (pickedCount > 0) {
            IconButton(onClick = onDeletePicked, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
            }
            IconButton(onClick = onClearPicked, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_cancel))
            }
        }
    }
}

/** Pie de columna: "+" (ver todos) y papelera, debajo de la lista. */
@Composable
private fun ColumnFooter(
    moreEnabled: Boolean,
    moreDescription: String,
    onMore: () -> Unit,
    trashEnabled: Boolean,
    trashDescription: String,
    onTrash: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMore, enabled = moreEnabled) {
            Icon(Icons.Filled.Add, moreDescription)
        }
        IconButton(onClick = onTrash, enabled = trashEnabled) {
            Icon(Icons.Filled.Delete, trashDescription)
        }
    }
}

/** Estado vacio compacto para una columna estrecha. */
@Composable
private fun CompactEmpty(icon: ImageVector, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Reciente en una fila compacta: miniatura cuadrada y nombre. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    file: File,
    title: String,
    picked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.cd_project_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (picked) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                )
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.home_recents_picked_cd),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .size(14.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Proyecto en una fila compacta, con el mismo menu (abrir, renombrar, duplicar, borrar) que la fila grande. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCompactRow(
    entry: ProjectEntry,
    thumb: File,
    picked: Boolean,
    onOpen: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = thumb,
                contentDescription = stringResource(R.string.cd_project_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (picked) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                )
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.home_recents_picked_cd),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .size(14.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.width} x ${entry.height}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_open)) },
                    onClick = { menuOpen = false; onOpen() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = { menuOpen = false; renaming = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_duplicate)) },
                    onClick = { menuOpen = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { menuOpen = false; confirmDelete = true },
                )
            }
        }
    }

    if (renaming) {
        TextPromptDialog(
            title = stringResource(R.string.action_rename),
            initial = entry.name,
            onConfirm = onRename,
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.action_confirm_delete),
            body = stringResource(R.string.trash_info),
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Fondo gris redondeado, igual que las tarjetas de Herramientas, que envuelve una lista y sus iconos. */
@Composable
private fun GrayPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            content = content,
        )
    }
}
