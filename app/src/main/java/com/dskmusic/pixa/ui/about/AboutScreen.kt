package com.dskmusic.pixa.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.BuildConfig
import com.dskmusic.pixa.R
import com.dskmusic.pixa.ui.common.PanelCard
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.common.SectionTitle

private const val DEV_URL = "https://www.dskmusic.com/dsk_dev_redirect.php"
private const val DEV_EMAIL = "dskmusicapps@gmail.com"

/**
 * Creditos y licencias (requisito 1).
 *
 * Las licencias se listan en codigo y sin traducir: son nombres propios de librerias y de
 * licencias, y traducirlos solo complicaria mantener la lista al dia.
 */
private val Licenses = listOf(
    "AndroidX / Jetpack Compose, Material 3 — Apache License 2.0",
    "AndroidX Lifecycle, Navigation, DataStore, WorkManager, Glance — Apache License 2.0",
    "Google ML Kit (Subject Segmentation, Face Detection) — Apache License 2.0",
    "ZXing Core — Apache License 2.0",
    "Coil — Apache License 2.0",
    "Kotlin, kotlinx.serialization, kotlinx.coroutines — Apache License 2.0",
    "Google Fonts (Anton, Roboto, Montserrat, Lora, Oswald...) — SIL Open Font License 1.1",
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(title = stringResource(R.string.about_title), onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            // El icono es a color, asi que se pinta como imagen y no como Icon (que lo
            // tenirla de un solo tono).
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { context.openUrl(DEV_URL) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.about_made_by),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            LinkRow(
                icon = Icons.Filled.Language,
                label = stringResource(R.string.about_website),
                onClick = { context.openUrl(DEV_URL) },
            )
            LinkRow(
                icon = Icons.Filled.Email,
                label = stringResource(R.string.about_email),
                onClick = {
                    context.startIntent(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEV_EMAIL")),
                    )
                },
            )

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.about_no_trackers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            SectionTitle(stringResource(R.string.about_licenses))
            PanelCard {
                Text(
                    stringResource(R.string.about_licenses_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Licenses.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun android.content.Context.openUrl(url: String) {
    startIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

/**
 * Lanza el intent y no hace nada si el telefono no tiene como atenderlo: un dispositivo
 * sin navegador o sin cliente de correo no debe tumbar la pantalla de informacion.
 */
private fun android.content.Context.startIntent(intent: Intent) {
    runCatching { startActivity(intent) }
}
