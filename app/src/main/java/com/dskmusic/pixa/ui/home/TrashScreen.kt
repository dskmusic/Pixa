package com.dskmusic.pixa.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.project.ProjectEntry
import com.dskmusic.pixa.ui.common.ConfirmDialog
import com.dskmusic.pixa.ui.common.EmptyState
import com.dskmusic.pixa.ui.common.PixaTopBar
import java.io.File

/**
 * Papelera de proyectos (requisito 5).
 *
 * Los proyectos no se borran al mandarlos aqui: solo quedan marcados, y un worker diario
 * los elimina pasados 30 dias. Esta pantalla muestra los dias que quedan de cada uno para
 * que el borrado definitivo nunca sea una sorpresa.
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val trash by vm.trash.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ProjectEntry?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(
                title = stringResource(R.string.home_trash),
                onBack = onBack,
                actions = {
                    if (trash.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text(stringResource(R.string.action_empty_trash))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (trash.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.trash_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Text(
                    stringResource(R.string.trash_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            items(trash, key = { it.id }) { entry ->
                TrashRow(
                    entry = entry,
                    thumb = vm.projectThumb(entry.id),
                    daysLeft = vm.daysLeft(entry),
                    onRestore = { vm.restore(entry.id) },
                    onDelete = { confirmDelete = entry },
                )
            }
            item { Spacer(Modifier.padding(vertical = 12.dp)) }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.trash_confirm_empty),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { vm.emptyTrash() },
            onDismiss = { confirmEmpty = false },
        )
    }
    confirmDelete?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.action_confirm_delete),
            body = entry.name,
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { vm.deleteForever(entry.id) },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun TrashRow(
    entry: ProjectEntry,
    thumb: File,
    daysLeft: Int,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                stringResource(R.string.trash_days_left, daysLeft),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRestore) {
            Icon(Icons.Filled.Restore, stringResource(R.string.action_restore))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteForever, stringResource(R.string.action_delete))
        }
    }
}
