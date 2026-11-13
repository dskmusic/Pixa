package com.dskmusic.pixa.ui.editor.panels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.text.GoogleFontsApi
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.TextFieldRow

/**
 * Explorador del catalogo completo de Google Fonts (requisito del usuario), mas alla de las
 * 21 familias fijas de FontRepository.CATALOG. Solo lista nombres: el tipo de letra lo sigue
 * sirviendo Downloadable Fonts, que acepta cualquier familia por nombre.
 */
@Composable
fun GoogleFontsBrowserDialog(
    onDismiss: () -> Unit,
    onPick: (FontRef) -> Unit,
) {
    val context = LocalContext.current
    val fonts = remember(context) { context.pixa.fonts }

    var query by remember { mutableStateOf("") }
    var families by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = runCatching { GoogleFontsApi.listFamilies() }.getOrNull()
        loading = false
        if (result == null) error = true else families = result.map { it.family }
    }

    val filtered = remember(families, query) {
        if (query.isBlank()) families else families.filter { it.contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                    }
                    Text(
                        stringResource(R.string.font_browse_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextFieldRow(
                    label = stringResource(R.string.font_browse_search_hint),
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        !GoogleFontsApi.hasKey -> CenteredHint(
                            stringResource(R.string.font_browse_no_key),
                            Modifier.align(Alignment.Center),
                        )
                        error && families.isEmpty() -> CenteredHint(
                            stringResource(R.string.font_browse_error),
                            Modifier.align(Alignment.Center),
                        )
                        loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        filtered.isEmpty() -> CenteredHint(
                            stringResource(R.string.emoji_not_found),
                            Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it }) { family ->
                                FontRowItem(
                                    label = family,
                                    family = fonts.composeFamily(family),
                                    selected = false,
                                    onClick = {
                                        onPick(FontRef(family = family, source = FontRef.Source.GOOGLE))
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(24.dp),
    )
}
