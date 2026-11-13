package com.dskmusic.pixa.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dskmusic.pixa.core.project.ProjectStore

/**
 * Borra definitivamente los proyectos que llevan mas de 30 dias en la papelera
 * (requisito 5).
 */
class TrashCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = ProjectStore(applicationContext)
        return try {
            store.purgeExpiredTrash()
            Result.success()
        } catch (e: Exception) {
            // Si falla, se reintenta en la siguiente ejecucion diaria.
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "pixa_trash_cleanup"
    }
}
