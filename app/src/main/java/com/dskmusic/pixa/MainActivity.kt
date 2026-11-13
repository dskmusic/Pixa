package com.dskmusic.pixa

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ui.PixaRoot
import com.dskmusic.pixa.ui.nav.LaunchRequest
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Unica actividad de la app.
 *
 * Hereda de AppCompatActivity porque el idioma por app (requisito 1) se aplica con
 * AppCompatDelegate.setApplicationLocales, que necesita este contexto para funcionar
 * tambien por debajo de API 33.
 *
 * configChanges en el manifest evita recrear la actividad al girar: el documento abierto,
 * que puede pesar bastante en bitmaps, se queda donde esta.
 */
class MainActivity : AppCompatActivity() {

    private val launchRequest = MutableStateFlow<LaunchRequest>(LaunchRequest.None)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Solo la primera creacion consume el intent: al girar no se vuelve a abrir la
        // imagen compartida.
        if (savedInstanceState == null) {
            launchRequest.value = LaunchRequest.from(intent)
        }
        setContent {
            val request by launchRequest.collectAsState()

            // Requisito del usuario: bloquear en vertical u permitir rotar, segun el ajuste.
            // El valor por defecto ya viene resuelto por pantalla (movil > vertical, tablet > libre).
            val settings by pixa.settings.settings.collectAsState(initial = PixaSettings())
            LaunchedEffect(settings.lockOrientation) {
                requestedOrientation = if (settings.lockOrientation) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            PixaRoot(
                request = request,
                onRequestHandled = { launchRequest.value = LaunchRequest.None },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest.value = LaunchRequest.from(intent)
    }
}
