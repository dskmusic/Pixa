package com.dskmusic.pixa.ui.editor.panels

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.dskmusic.pixa.R
import com.dskmusic.pixa.ml.AiModel
import com.dskmusic.pixa.ml.AiStats
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Una operacion de IA lista para lanzar.
 *
 * @param id clave con la que se guarda la velocidad medida en este dispositivo.
 * @param model modelo que necesita (se descarga antes si falta), o null si no usa ninguno.
 * @param units trabajo estimado en unidades de [id] (megapixeles, caras...), para calcular
 *   cuanto va a tardar a partir de lo medido en ejecuciones anteriores.
 * @param exec el trabajo. Recibe el ViewModel como receptor y una funcion de progreso 0..1.
 *   Devuelve true si ha modificado el documento, false si ha fallado y null si no habia
 *   nada que hacer (no se espera a ningun repintado).
 * @param onDone se ejecuta en el hilo principal al terminar sin error.
 */
class AiAction(
    val id: String,
    @StringRes val titleRes: Int,
    val model: AiModel?,
    val units: Float,
    /** Otros modelos que necesita ademas de [model]; se descargan juntos antes de empezar. */
    val extraModels: List<AiModel> = emptyList(),
    val onDone: (() -> Unit)? = null,
    val exec: suspend EditorViewModel.(Context, (Float) -> Unit) -> Boolean?,
)

/** "40 s", "3 min", "2 min 10 s". */
fun formatDuration(context: Context, seconds: Long): String = when {
    seconds < 60 -> context.getString(R.string.ai_dur_s, seconds.toInt())
    seconds % 60 == 0L -> context.getString(R.string.ai_dur_min, (seconds / 60).toInt())
    else -> context.getString(R.string.ai_dur_min_s, (seconds / 60).toInt(), (seconds % 60).toInt())
}

/**
 * Lanza [action]: descarga el modelo si falta, ejecuta el trabajo y mantiene el aviso de
 * ocupado con progreso y tiempo restante. Todo el proceso se puede cancelar desde el aviso;
 * al cancelar se corta la descarga (borrando lo parcial) o el procesado entre pasos.
 */
fun EditorViewModel.runAi(context: Context, action: AiAction) {
    val app = context.applicationContext
    val missing = (listOfNotNull(action.model) + action.extraModels).filter { !it.isReady(app) }
    val needsDownload = missing.isNotEmpty()
    busy(if (needsDownload) R.string.ai_downloading else R.string.ai_busy, 0f)

    val job = viewModelScope.launch {
        try {
            if (needsDownload) {
                val t0 = SystemClock.elapsedRealtime()
                val totalBytes = missing.sumOf { it.missingBytes(app) }.coerceAtLeast(1L)
                var doneBytes = 0L
                for (m in missing) {
                    val bytes = m.missingBytes(app)
                    val ok = m.download(app) { p ->
                        val overall = ((doneBytes + p * bytes) / totalBytes).coerceIn(0f, 1f)
                        progress(overall)
                        showEta(app, overall, t0)
                    }
                    if (!ok) {
                        idle()
                        post(R.string.ai_failed_download)
                        return@launch
                    }
                    doneBytes += bytes
                }
            }
            busy(R.string.ai_busy, 0f)
            busyDetail(null)
            val t0 = SystemClock.elapsedRealtime()
            val committed = action.exec(this@runAi, app) { p ->
                progress(p)
                showEta(app, p, t0)
            }
            val seconds = (SystemClock.elapsedRealtime() - t0) / 1000f
            when (committed) {
                true -> {
                    if (action.units > 0f && seconds > 1f) {
                        AiStats.recordOp(app, action.id, seconds / max(action.units, 0.1f))
                    }
                    awaitFullRender()
                    idle()
                    action.onDone?.invoke()
                }
                false -> {
                    idle()
                    post(R.string.ai_failed)
                }
                null -> {
                    idle()
                    action.onDone?.invoke()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            idle()
            post(R.string.ai_failed)
        }
    }
    makeBusyCancellable(job)
}

/** Tiempo restante a partir del avance y lo que se lleva de reloj; no se muestra hasta tener datos. */
private fun EditorViewModel.showEta(app: Context, progress: Float, startedAt: Long) {
    val elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000f
    if (progress < 0.03f || elapsed < 3f) return
    val left = (elapsed * (1f - progress) / progress).toLong().coerceAtLeast(1L)
    busyDetail(app.getString(R.string.ai_eta, formatDuration(app, left)))
}
