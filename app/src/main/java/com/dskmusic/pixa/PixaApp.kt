package com.dskmusic.pixa

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.project.DraftStore
import com.dskmusic.pixa.core.project.ProjectStore
import com.dskmusic.pixa.core.project.RecentsStore
import com.dskmusic.pixa.core.render.DocumentRenderer
import com.dskmusic.pixa.core.render.LayerRasterizer
import com.dskmusic.pixa.core.text.FontRepository
import com.dskmusic.pixa.data.settings.SettingsRepository
import com.dskmusic.pixa.data.userdata.UserDataStore
import com.dskmusic.pixa.ml.MediaPipeCutout
import com.dskmusic.pixa.ml.U2NetCutout
import com.dskmusic.pixa.work.TrashCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Contenedor de dependencias.
 *
 * La app no usa ninguna libreria de inyeccion: son ocho objetos de ambito de aplicacion y
 * un contenedor creado a mano se entiende de un vistazo, sin anotaciones ni generacion de
 * codigo. Las pantallas lo alcanzan con [Context.pixa].
 */
class AppContainer(context: Context) {

    val settings = SettingsRepository(context)
    val projects = ProjectStore(context)
    val drafts = DraftStore(context)
    val recents = RecentsStore(context)
    val userData = UserDataStore(context)
    val fonts = FontRepository(context)

    /**
     * Almacen de pixeles compartido por el editor y los generadores.
     *
     * Es uno solo para toda la app a proposito: solo hay un documento abierto a la vez, y
     * tener un unico almacen permite que el recolector del historial sepa con certeza que
     * bitmaps ya no referencia nadie.
     */
    val bitmaps = BitmapStore()

    val rasterizer = LayerRasterizer(bitmaps, typefaceFor = { ref -> fonts.cached(ref) })

    val renderer = DocumentRenderer(bitmaps, rasterizer)
}

class PixaApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Para el intento silencioso de descargar los modelos de IA al arrancar. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleTrashCleanup()
        prefetchModels()
    }

    /**
     * Requisito del usuario: los modelos de MediaPipe y del recorte general ya no van en
     * el APK (ahorra ~21 MB), se descargan solos. Aqui se intenta en silencio, sin avisar
     * a nadie si falla: si no hay red ahora, el propio uso de la funcion (o el siguiente
     * arranque) lo vuelve a intentar sin que el usuario tenga que hacer nada especial.
     */
    private fun prefetchModels() {
        appScope.launch {
            runCatching { MediaPipeCutout.prefetch(this@PixaApp) }
            runCatching { U2NetCutout.prefetch(this@PixaApp) }
        }
    }

    /**
     * Limpieza de la papelera (requisito 5): una vez al dia basta, y WorkManager la
     * reprograma solo tras un reinicio.
     */
    private fun scheduleTrashCleanup() {
        val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TrashCleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun onLowMemory() {
        super.onLowMemory()
        container.rasterizer.clearCache()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) container.rasterizer.clearCache()
    }
}

/** Acceso al contenedor desde cualquier Context. */
val Context.pixa: AppContainer
    get() = (applicationContext as PixaApp).container
