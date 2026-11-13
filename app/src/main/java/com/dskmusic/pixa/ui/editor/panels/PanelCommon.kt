package com.dskmusic.pixa.ui.editor.panels

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.model.Xf
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.ml.SuperResolution
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.SliderRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.formatPercent
import com.dskmusic.pixa.ui.common.ColorPickerDialog
import com.dskmusic.pixa.ui.common.ColorSwatch
import com.dskmusic.pixa.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================================================
// Operaciones destructivas sobre la capa activa
// ======================================================================================

/**
 * Aplica una operacion de pixeles a la capa raster activa y la registra en el historial.
 *
 * Centraliza las tres cosas que todas las operaciones destructivas comparten: comprobar
 * que hay una capa de imagen desbloqueada, trabajar fuera del hilo principal y no dejar
 * el indicador de ocupado encendido si algo falla.
 */
fun EditorViewModel.applyToRaster(@StringRes labelRes: Int, op: (Bitmap) -> Bitmap) {
    applyToRasterLayer(labelRes) { _, bitmap -> op(bitmap) }
}

/** Como [applyToRaster] pero la operacion tambien recibe la capa, para su transformacion. */
fun EditorViewModel.applyToRasterLayer(
    @StringRes labelRes: Int,
    op: (PixaLayer, Bitmap) -> Bitmap,
) {
    val layer = pixelTargetOrAsk() ?: return
    val src = bitmapOf(layer) ?: run {
        post(R.string.layer_pick_raster)
        return
    }
    busy(R.string.label_progress)
    viewModelScope.launch {
        val out = withContext(Dispatchers.Default) {
            runCatching { op(layer, src) }.getOrNull()
        }
        if (out == null) {
            idle()
            post(R.string.error_generic)
            return@launch
        }
        replaceRaster(layer.id, out, labelRes)
        // El aviso se queda hasta que el lienzo termina de recomponer todas las capas a
        // tamano completo, no solo hasta que acaba el filtro.
        awaitFullRender()
        idle()
    }
}

/**
 * Capa activa lista para editar pixeles, o null tras avisar de por que no lo esta.
 *
 * Si la capa se puede rasterizar no se queda en el aviso: pregunta si hacerlo (requisito
 * 14). Es el unico sitio donde se decide, asi que todas las herramientas destructivas se
 * comportan igual.
 */
private fun EditorViewModel.pixelTargetOrAsk(): PixaLayer? {
    val layer = selectedLayer
    if (layer == null) {
        post(R.string.layer_pick_raster)
        return null
    }
    if (layer.locked) {
        post(R.string.layer_locked_warning)
        return null
    }
    if (!layer.isRaster) {
        askRasterize(layer)
        return null
    }
    return layer
}

/** Como [applyToRaster] pero la operacion puede suspenderse (ML Kit, descargas). */
fun EditorViewModel.applyToRasterSuspending(
    @StringRes labelRes: Int,
    @StringRes busyRes: Int = R.string.label_progress,
    op: suspend (Bitmap) -> Bitmap?,
) {
    val layer = pixelTargetOrAsk() ?: return
    val src = bitmapOf(layer) ?: run {
        post(R.string.layer_pick_raster)
        return
    }
    busy(busyRes)
    viewModelScope.launch {
        val out = runCatching { op(src) }.getOrNull()
        if (out == null) {
            idle()
            post(R.string.bg_remove_failed)
            return@launch
        }
        replaceRaster(layer.id, out, labelRes)
        awaitFullRender()
        idle()
    }
}

/** Pixeles de la capa activa, o null con aviso si no es una capa de imagen. */
fun EditorViewModel.rasterOrWarn(): Pair<PixaLayer, Bitmap>? {
    val layer = pixelTargetOrAsk() ?: return null
    val bmp = bitmapOf(layer)
    if (bmp == null) {
        post(R.string.layer_pick_raster)
        return null
    }
    return layer to bmp
}

// ======================================================================================
// Previsualizacion viva de ajustes y efectos (requisito 13)
// ======================================================================================

/**
 * Previsualizacion de una operacion destructiva mientras se mueven los deslizadores.
 *
 * Trabaja sobre una copia reducida de la capa y la mete en el documento de
 * previsualizacion, que no entra en el historial. Es lo que permite mover un deslizador
 * sobre una foto de 12 megapixeles sin esperas: la operacion se calcula sobre unos
 * 700 kpx y solo al pulsar "Aplicar" se repite a resolucion completa.
 */
@Stable
class LivePreview internal constructor(
    private val vm: EditorViewModel,
    private val store: BitmapStore,
    val layer: PixaLayer,
    val source: Bitmap,
    val proxy: Bitmap,
    private val previewId: String,
) {
    /**
     * Operacion pendiente, como maximo una.
     *
     * Cancelar el Job no servia: la operacion de pixeles no se suspende en ningun punto,
     * asi que seguia hasta el final. Cada pixel de recorrido del deslizador dejaba otro
     * calculo de un megapixel en marcha y la vista previa iba siempre por detras del dedo.
     * Con un trabajador unico y una cola de un hueco se calcula solo el ultimo valor.
     */
    private val queue = Channel<PendingOp>(Channel.CONFLATED)
    private var worker: Job? = null

    /** Operacion pendiente con el tamano del resultado a resolucion completa. */
    private class PendingOp(
        val op: (Bitmap) -> Bitmap,
        val fullW: Int,
        val fullH: Int,
    )

    /** Pide la previsualizacion del valor actual; sustituye a la que estuviera en cola. */
    fun update(@StringRes labelRes: Int, op: (Bitmap) -> Bitmap) {
        updateResized(labelRes, source.width, source.height, op)
    }

    /**
     * Como [update], para operaciones que cambian el tamano del bitmap (p.ej. el
     * caleidoscopio, que lo recorta a un cuadrado). [fullW]x[fullH] son las dimensiones
     * del resultado a resolucion completa, de modo que el resultado reducido se escala y
     * centra para ocupar exactamente ese espacio en el documento.
     */
    fun updateResized(
        @StringRes labelRes: Int,
        fullW: Int,
        fullH: Int,
        op: (Bitmap) -> Bitmap,
    ) {
        // Para el aviso de "cambios sin aplicar": aplicar aqui = repetir esta misma
        // operacion a resolucion completa.
        vm.pendingApply = { apply(labelRes, op) }
        if (worker == null) {
            worker = vm.viewModelScope.launch {
                for (pending in queue) {
                    val out = withContext(Dispatchers.Default) {
                        runCatching { pending.op(proxy) }.getOrNull()
                    } ?: continue
                    store.put(previewId, out)
                    vm.previewDocument(
                        previewDocument(out.width, out.height, pending.fullW, pending.fullH),
                    )
                }
            }
        }
        queue.trySend(PendingOp(op, fullW, fullH))
    }

    /** Vuelve a ensenar el documento real, sin la previsualizacion. */
    fun cancel() {
        stopWorker()
        vm.pendingApply = null
        vm.cancelPreview()
    }

    /** Repite la operacion a resolucion completa y la guarda en el historial. */
    fun apply(@StringRes labelRes: Int, op: (Bitmap) -> Bitmap) {
        stopWorker()
        vm.pendingApply = null
        vm.cancelPreview()
        vm.applyToRaster(labelRes) { op(it) }
    }

    private fun stopWorker() {
        worker?.cancel()
        worker = null
        // Lo que quedara en la cola ya no interesa: el panel se cierra o se confirma.
        while (queue.tryReceive().isSuccess) Unit
    }

    private fun previewDocument(outW: Int, outH: Int, fullW: Int, fullH: Int): PixaDocument =
        vm.doc.updateLayer(layer.id) {
            it.copy(
                content = LayerContent.Raster(previewId),
                transform = proxyTransform(it.transform, outW, outH, fullW, fullH),
            )
        }

    /**
     * Ajusta la transformacion al bitmap de previsualizacion, cuyo tamano puede no
     * coincidir con el del contenido original (requisito: caleidoscopio).
     *
     * El modelo coloca el contenido por su centro, asi que al cambiar el tamano hay que
     * escalarlo para que ocupe el espacio del resultado final y re-centrarlo. Con
     * perspectiva no hace falta, porque la matriz mapea el rectangulo del contenido a las
     * cuatro esquinas sea cual sea.
     */
    private fun proxyTransform(xf: Xf, outW: Int, outH: Int, fullW: Int, fullH: Int): Xf {
        if (xf.perspective != null) return xf
        val kx = fullW.toFloat() / outW
        val ky = fullH.toFloat() / outH
        return xf.copy(
            scaleX = xf.scaleX * kx,
            scaleY = xf.scaleY * ky,
            tx = xf.tx + (source.width - outW) / 2f,
            ty = xf.ty + (source.height - outH) / 2f,
        )
    }
}

/**
 * Presupuesto de la copia reducida con la que trabajan los deslizadores, en kilopixeles.
 *
 * Es el compromiso entre ver bien el efecto y que el dedo no espere: a 600 kpx una pasada
 * de ajustes tarda unas decenas de milisegundos incluso en un movil modesto, y el lienzo
 * se pinta ademas escalado, asi que la diferencia no se aprecia hasta pulsar "Aplicar",
 * que repite la operacion a resolucion completa.
 */
private const val PROXY_BUDGET_KPX = 600

/**
 * Crea la previsualizacion de la capa activa, o null si no hay capa de imagen.
 *
 * Al salir del panel se cancela sola, para que el lienzo no se quede ensenando una
 * previsualizacion que ya nadie puede confirmar.
 */
@Composable
fun rememberLivePreview(vm: EditorViewModel, proxyBudget: Int = PROXY_BUDGET_KPX): LivePreview? {
    val store = LocalContext.current.pixa.bitmaps
    val layer = vm.selectedLayer
    val source = layer?.let { vm.bitmapOf(it) }
    val preview = remember(layer?.id, source) {
        if (layer == null || source == null) {
            null
        } else {
            LivePreview(
                vm = vm,
                store = store,
                layer = layer,
                source = source,
                proxy = BitmapUtils.thumbnail(source, proxyBudget),
                previewId = PixaLayer.newId(),
            )
        }
    }
    // Requisito del usuario: cerrar el panel tocando fuera ya NO aplica el ajuste
    // pendiente, solo lo descarta. Hace falta pulsar "Aplicar" a proposito.
    DisposableEffect(preview) {
        onDispose { preview?.cancel() }
    }
    return preview
}

/** Texto de ayuda breve, normalmente bajo una botonera de herramientas. */
@Composable
fun PanelHint(@StringRes textRes: Int) {
    Text(
        stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Aviso de "aqui hace falta una capa de imagen", igual en todos los paneles. */
@Composable
fun NeedsRasterNotice(vm: EditorViewModel? = null) {
    Text(
        stringResource(R.string.layer_pick_raster_long),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
    // Si la capa elegida se puede rasterizar, se ofrece ahi mismo (pide confirmacion).
    val layer = vm?.selectedLayer
    if (vm != null && layer != null && !layer.isRaster) {
        PanelActions {
            TextButton(onClick = { vm.askRasterize(layer) }) {
                Text(stringResource(R.string.layer_rasterize))
            }
        }
    }
}

// ======================================================================================
// Color
// ======================================================================================

/**
 * Fila con una muestra de color que abre el selector.
 *
 * Usa la paleta global y los recientes de los ajustes, y alimenta los recientes al
 * confirmar: es el requisito 1 (paleta global con recientes automaticos) resuelto en un
 * solo sitio para que todas las herramientas lo compartan.
 */
@Composable
fun ColorRow(
    label: String,
    color: Int,
    settings: PixaSettings,
    onPick: (Int) -> Unit,
    withAlpha: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        ColorSwatch(color = Color(color), onClick = { open = true })
    }
    if (open) {
        ColorPickerDialog(
            initial = color,
            palette = settings.palette,
            recents = settings.recentColors,
            withAlpha = withAlpha,
            onDismiss = { open = false },
            onConfirm = { picked ->
                onPick(picked)
                scope.launch { context.pixa.settings.pushRecentColor(picked) }
            },
            onAddToPalette = { added ->
                scope.launch { context.pixa.settings.addToPalette(added) }
            },
        )
    }
}

/** Dos colores en la misma fila, para duotono y degradados de dos paradas. */
@Composable
fun ColorPairRow(
    labelA: String,
    colorA: Int,
    onPickA: (Int) -> Unit,
    labelB: String,
    colorB: Int,
    onPickB: (Int) -> Unit,
    settings: PixaSettings,
) {
    Column {
        ColorRow(label = labelA, color = colorA, settings = settings, onPick = onPickA)
        ColorRow(label = labelB, color = colorB, settings = settings, onPick = onPickB)
    }
}

/** Botonera horizontal compacta, con separacion uniforme. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun PanelActions(
    align: Alignment.Horizontal = Alignment.End,
    content: @Composable () -> Unit,
) {
    // FlowRow: si no caben en una linea pasan a la siguiente en vez de salirse de la pantalla.
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, align),
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

/**
 * Boton de accion principal del panel (Aplicar/Insertar/Anadir...), requisito del usuario:
 * un mismo diseno en todos los paneles en vez de la mezcla que habia (unos con icono
 * suelto, otros con texto plano). El visto bueno de toda la vida, pero destacado: fondo
 * del color de acento y el interior contrastado.
 */
@Composable
fun PrimaryPanelButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Check,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(icon, contentDescription = text)
    }
}

// ======================================================================================
// Comparar antes / despues con cortina
// ======================================================================================

/**
 * Interruptor y deslizador de la cortina de comparacion. El estado vive en el ViewModel
 * para compartirlo con el boton rapido del panel de ajustes y con el tirador del lienzo.
 * [enabled] debe ser true solo mientras el panel tiene algo en previsualizacion.
 */
@Composable
fun CompareControls(vm: EditorViewModel, enabled: Boolean) {
    // El interruptor de la cortina vive en el menu de tres puntos (requisito del usuario).
    if (vm.curtainOn && enabled) {
        SliderRow(
            label = stringResource(R.string.compare_position),
            value = vm.curtainPos,
            onValueChange = { vm.moveCurtain(it) },
            valueText = formatPercent(vm.curtainPos),
            defaultValue = 0.5f,
        )
    }
}
