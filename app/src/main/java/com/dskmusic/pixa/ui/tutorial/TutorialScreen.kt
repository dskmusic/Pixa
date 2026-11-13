package com.dskmusic.pixa.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.R
import com.dskmusic.pixa.pixa
import kotlinx.coroutines.launch

private data class TutorialPage(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

private val Pages = listOf(
    TutorialPage(Icons.Filled.Brush, R.string.tutorial_p1_title, R.string.tutorial_p1_body),
    TutorialPage(Icons.Filled.Save, R.string.tutorial_p2_title, R.string.tutorial_p2_body),
    TutorialPage(Icons.Filled.TouchApp, R.string.tutorial_p3_title, R.string.tutorial_p3_body),
    TutorialPage(Icons.Filled.AutoAwesomeMosaic, R.string.tutorial_p4_title, R.string.tutorial_p4_body),
    TutorialPage(Icons.Filled.Layers, R.string.tutorial_p5_title, R.string.tutorial_p5_body),
    TutorialPage(Icons.Filled.Gesture, R.string.tutorial_p6_title, R.string.tutorial_p6_body),
    TutorialPage(Icons.Filled.TextFields, R.string.tutorial_p7_title, R.string.tutorial_p7_body),
    TutorialPage(Icons.Filled.Category, R.string.tutorial_p8_title, R.string.tutorial_p8_body),
    TutorialPage(Icons.Filled.ChatBubble, R.string.tutorial_p9_title, R.string.tutorial_p9_body),
    TutorialPage(Icons.Filled.PhotoFilter, R.string.tutorial_p10_title, R.string.tutorial_p10_body),
    TutorialPage(Icons.Filled.ContentCut, R.string.tutorial_p11_title, R.string.tutorial_p11_body),
    TutorialPage(Icons.Filled.Psychology, R.string.tutorial_p12_title, R.string.tutorial_p12_body),
    TutorialPage(Icons.Filled.AutoFixHigh, R.string.tutorial_p13_title, R.string.tutorial_p13_body),
    TutorialPage(Icons.Filled.Collections, R.string.tutorial_p14_title, R.string.tutorial_p14_body),
    TutorialPage(Icons.Filled.Tune, R.string.tutorial_p15_title, R.string.tutorial_p15_body),
    TutorialPage(Icons.Filled.PanTool, R.string.tutorial_p16_title, R.string.tutorial_p16_body),
)

/**
 * Tutorial de bienvenida (requisito 1).
 *
 * Se puede saltar en cualquier pagina y al terminar queda marcado como visto, de modo que
 * solo vuelve a aparecer si se pide desde Ajustes.
 */
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val repo = LocalContext.current.pixa.settings
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { Pages.size })

    val finish: () -> Unit = {
        scope.launch { repo.setTutorialDone(true) }
        onDone()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = finish) { Text(stringResource(R.string.action_skip)) }
        }

        Text(
            stringResource(R.string.tutorial_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { index ->
            val page = Pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(page.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(page.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Pages.indices.forEach { index ->
                val active = index == pager.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 9.dp else 6.dp)
                        .background(
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }

        val last = pager.currentPage == Pages.lastIndex
        Button(
            onClick = {
                if (last) {
                    finish()
                } else {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            // fillMaxWidth + Center mantiene la etiqueta centrada tambien cuando el
            // texto traducido ocupa dos lineas.
            Text(
                text = stringResource(
                    if (last) R.string.tutorial_start else R.string.action_next,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}
