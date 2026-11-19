package com.dskmusic.pixa.ui.editor

import androidx.compose.material.icons.filled.PanTool
import com.dskmusic.pixa.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.selection.MarchingAnts
import com.dskmusic.pixa.core.selection.Selection
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Milisegundos por debajo de los cuales un contacto cuenta como toque y no arrastre. */
private const val TAP_MS = 260L
private const val DOUBLE_TAP_MS = 320L

/** Instante y posicion del ultimo toque, para el doble toque. */
private class LastTap {
    var at = 0L
    var pos = Offset.Zero
}

/** Grados que hay que girar con dos dedos antes de mover el elemento. */
private const val ROTATE_SLOP_DEG = 5f

/** Variacion de escala que hay que superar antes de transformar el elemento. */
private const val OBJECT_SCALE_SLOP = 0.02f

/**
 * Lienzo del editor: pinta el documento y reparte los gestos (requisito 6).
 *
 * Reglas de los dedos, iguales en todo el editor para que nunca haya duda:
 *
 *  - Un dedo arrastrando = la herramienta activa.
 *  - Dos o mas dedos = mover, ampliar y girar. Con [objectGesture] el gesto completo va
 *    al elemento seleccionado y la vista no se mueve; sin el, mueven y amplian la vista,
 *    que nunca gira. En cuanto entra un segundo dedo se cancela el trazo que hubiera
 *    empezado, de modo que el pinza-zoom nunca pinta.
 *  - Toque con dos dedos = deshacer; con tres = rehacer (requisito 16).
 *  - Toque doble = alternar entre encajar y 100 %.
 *
 * El trazo de herramienta no arranca al apoyar el dedo sino al superar un umbral de
 * movimiento: asi un toque con dos dedos no deja una marca de pincel antes de que llegue
 * el segundo dedo.
 */
@Composable
fun CanvasHost(
    controller: CanvasController,
    doc: PixaDocument,
    composite: Bitmap?,
    selection: Selection?,
    ui: EditorUi,
    modifier: Modifier = Modifier,
    handleQuad: FloatArray? = null,
    onTap: (Offset) -> Unit = {},
    onDoubleTap: (Offset) -> Boolean = { false },
    onLongPress: (Offset) -> Unit = {},
    onLongPressRelease: () -> Unit = {},
    /** Esquina superior derecha del marco como boton rojo de borrar (herramienta Mover). */
    deleteCorner: Boolean = false,
    onStrokeStart: (Offset) -> Unit = {},
    onStrokeMove: (Offset) -> Unit = {},
    onStrokeEnd: (Offset) -> Unit = {},
    onStrokeCancel: () -> Unit = {},
    onUndoGesture: () -> Unit = {},
    onRedoGesture: () -> Unit = {},
    objectGesture: Boolean = false,
    onObjectGesture: (scale: Float, pan: Offset, degrees: Float) -> Unit = { _, _, _ -> },
    onObjectGestureEnd: () -> Unit = {},
    overlay: DrawScope.(CanvasController) -> Unit = {},
) {
    val density = LocalDensity.current
    val slop = remember(density) { with(density) { 5.dp.toPx() } }
    val handleRadius = remember(density) { with(density) { 9.dp.toPx() } }

    val checker = remember { checkerShader() }
    val paints = remember { CanvasPaints() }
    // Ultimo toque (para detectar el doble toque). Vive fuera del pointerInput: ese bloque se
    // reinicia al cambiar la seleccion (objectGesture), y un toque que selecciona perdia el
    // recuerdo del anterior, de modo que el doble toque nunca llegaba a detectarse.
    val lastTap = remember { LastTap() }

    val contour = remember(selection) { selection?.let { MarchingAnts.contour(it) } }
    val ants by rememberInfiniteTransition(label = "ants").animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "antsPhase",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(ui.tool, objectGesture, ui.handPan) {

                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val downAt = first.uptimeMillis
                        val downPos = first.position

                        var maxPointers = 1
                        var travelled = 0f
                        var strokeActive = false
                        var viewMode = false
                        var longPressSent = false
                        var lastPos = downPos

                        // Con el cuentagotas la lupa aparece ya al apoyar el dedo, sin
                        // esperar al umbral de arrastre: si no, el primer color no se ve.
                        if (ui.tool == CanvasTool.EYEDROPPER || (ui.tool.isAiTool && !ui.panMode)) ui.touchPoint = downPos

                        var prevCentroid = downPos
                        var prevSpan = 0f
                        var prevAngle = 0f
                        var rotateTotal = 0f
                        var rotateOffset = 0f
                        var rotating = false
                        var objectScale = 1f
                        var objectPan = Offset.Zero
                        var objectActive = false
                        // Bug encontrado (requisito del usuario): al soltar un dedo del
                        // pellizco, el centroide de los dos dedos y la posicion del que
                        // queda no coinciden (el centroide esta en medio de los dos). Sin
                        // esto, el primer frame con un solo dedo aplicaba esa diferencia
                        // como desplazamiento y la vista saltaba un poco al soltar.
                        var justLeftMultiTouch = false

                        // Sin nada que mover con un dedo (herramienta de mover sin una capa
                        // elegida y sin bloquear), el arrastre mueve la vista en vez de no
                        // hacer nada: es justo lo que ya decide [objectGesture] para el gesto
                        // de dos dedos, asi que se reutiliza el mismo criterio.
                        val viewPans = ui.handPan || (ui.tool == CanvasTool.MOVE && !objectGesture) ||
                            (ui.panMode && ui.tool.isAiTool)
                        var panAnchor = downPos

                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.filter { it.pressed }
                            if (active.isEmpty()) break
                            maxPointers = max(maxPointers, active.size)

                            val centroid = centroidOf(active)
                            travelled = max(travelled, (centroid - downPos).getDistance())
                            lastPos = centroid

                            if (active.size >= 2) {
                                justLeftMultiTouch = true
                                if (!viewMode) {
                                    // Llego un segundo dedo: lo empezado con uno se anula.
                                    viewMode = true
                                    // La lupa se va con el segundo dedo: ya no hay un
                                    // punto que mirar y se quedaria congelada en pantalla.
                                    ui.touchPoint = null
                                    if (strokeActive) {
                                        strokeActive = false
                                        onStrokeCancel()
                                    }
                                    prevCentroid = centroid
                                    prevSpan = spanOf(active, centroid)
                                    prevAngle = angleOf(active)
                                    continue
                                }
                                val span = spanOf(active, centroid)
                                val angle = angleOf(active)
                                val zoomFactor = if (prevSpan > 1f && span > 1f) span / prevSpan else 1f
                                val panDelta = centroid - prevCentroid
                                // El giro nunca es de la vista. Hasta pasar el umbral no
                                // se gira nada: un toque de dos dedos para deshacer
                                // siempre mueve unas decimas de grado.
                                rotateTotal += deltaAngle(prevAngle, angle)
                                if (!rotating && abs(rotateTotal) >= ROTATE_SLOP_DEG) {
                                    rotating = true
                                    // Se descuenta el umbral: el elemento empieza a girar
                                    // desde donde esta, sin un salto inicial de 5 grados.
                                    rotateOffset = rotateTotal
                                }
                                if (objectGesture) {
                                    // Acumulados desde el inicio del gesto y en unidades
                                    // de documento: quien los recibe transforma siempre
                                    // la capa original, sin encadenar redondeos.
                                    objectScale *= zoomFactor
                                    objectPan += panDelta / controller.zoom
                                    if (!objectActive && (
                                            rotating ||
                                                abs(objectScale - 1f) > OBJECT_SCALE_SLOP ||
                                                panDistance(objectPan, controller.zoom) > slop
                                            )
                                    ) {
                                        objectActive = true
                                    }
                                    if (objectActive) {
                                        onObjectGesture(
                                            objectScale,
                                            objectPan,
                                            if (rotating) rotateTotal - rotateOffset else 0f,
                                        )
                                    }
                                } else {
                                    controller.applyGesture(
                                        centroid = centroid,
                                        panDelta = panDelta,
                                        zoomFactor = zoomFactor,
                                    )
                                }
                                prevCentroid = centroid
                                prevSpan = span
                                prevAngle = angle
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            val moved = event.changes.any { it.positionChanged() }
                            if (viewMode) {
                                // Bug encontrado (requisito del usuario): al soltar un dedo
                                // tras pellizcar con dos, el que quedaba se quedaba inerte
                                // hasta soltar del todo y volver a tocar. Picsart y el
                                // resto de apps del estilo siguen moviendo la vista con el
                                // dedo que queda, sin cortar el gesto. Solo cuando el
                                // pellizco movia la vista, no un objeto: si estaba
                                // transformando la capa seleccionada, soltar un dedo se
                                // queda inerte como antes, para no mezclar los dos gestos.
                                if (justLeftMultiTouch) {
                                    // Primer frame con un solo dedo tras el pellizco: solo
                                    // se recoge como nueva base, sin aplicar el salto del
                                    // centroide de dos dedos a la posicion de uno.
                                    justLeftMultiTouch = false
                                    prevCentroid = centroid
                                } else if (!objectGesture && moved) {
                                    controller.nudge(centroid - prevCentroid)
                                    prevCentroid = centroid
                                    event.changes.forEach { it.consume() }
                                }
                                continue
                            }

                            if (viewPans) {
                                if (moved) {
                                    controller.nudge(centroid - panAnchor)
                                    panAnchor = centroid
                                    event.changes.forEach { it.consume() }
                                } else if (!longPressSent && !ui.handPan &&
                                    event.changes.first().uptimeMillis - downAt > 420L
                                ) {
                                    longPressSent = true
                                    onLongPress(controller.viewToDoc(downPos))
                                }
                                continue
                            }

                            if (!strokeActive && travelled > slop) {
                                strokeActive = true
                                ui.touchPoint = centroid
                                onStrokeStart(controller.viewToDoc(downPos))
                            }
                            if (strokeActive && moved) {
                                ui.touchPoint = centroid
                                onStrokeMove(controller.viewToDoc(centroid))
                                event.changes.forEach { it.consume() }
                            } else if (!strokeActive &&
                                !longPressSent &&
                                event.changes.first().uptimeMillis - downAt > 420L
                            ) {
                                // Mantener pulsado sin mover: antes y despues, o menu.
                                longPressSent = true
                                onLongPress(controller.viewToDoc(downPos))
                            }
                        }

                        ui.touchPoint = null
                        ui.lensCorner = -1
                        val duration = (lastEventTimeFallback(downAt) - downAt).coerceAtLeast(0L)

                        when {
                            strokeActive -> onStrokeEnd(controller.viewToDoc(lastPos))

                            objectActive -> onObjectGestureEnd()

                            viewMode && travelled < slop * 3f && duration < TAP_MS * 2 -> {
                                if (maxPointers >= 3) onRedoGesture() else onUndoGesture()
                            }

                            !viewMode && travelled < slop && !longPressSent && !ui.handPan -> {
                                val now = downAt + duration
                                val isDouble = now - lastTap.at < DOUBLE_TAP_MS &&
                                    (downPos - lastTap.pos).getDistance() < slop * 6f
                                if (isDouble) {
                                    lastTap.at = 0L
                                    if (!onDoubleTap(controller.viewToDoc(downPos))) {
                                        controller.toggleFit()
                                    }
                                } else {
                                    lastTap.at = now
                                    lastTap.pos = downPos
                                    onTap(controller.viewToDoc(downPos))
                                }
                            }
                        }

                        // El "mantener pulsado" tiene final propio: quien lo use para
                        // comparar antes y despues necesita saber cuando se suelta.
                        if (longPressSent) onLongPressRelease()
                    }
                },
        ) {
            controller.updateViewSize(Size(size.width, size.height))
            val matrix = controller.matrix()

            drawIntoCanvas { canvasWrapper ->
                val canvas = canvasWrapper.nativeCanvas
                drawDocument(canvas, doc, composite, matrix, checker, paints)
                if (ui.showGrid) drawGrid(canvas, doc, matrix, paints)
                if (ui.showGuides) drawCenterGuides(canvas, doc, matrix, paints)
                ui.guides.forEach { guide -> drawSmartGuide(canvas, doc, guide, matrix, paints) }
                contour?.let { path ->
                    val saved = canvas.save()
                    canvas.concat(matrix)
                    MarchingAnts.draw(canvas, path, controller.zoom, ants, ui.selectionLineWidth)
                    canvas.restoreToCount(saved)
                }
                if (handleQuad != null) drawHandles(canvas, handleQuad, matrix, handleRadius, paints, deleteCorner)
                if (ui.showRulers) drawRulers(canvas, controller, paints)
                // Con el cuentagotas la lupa sale siempre: es la unica forma de ver
                // el color que hay bajo el dedo sin que el dedo lo tape.
                val eyedropper = ui.tool == CanvasTool.EYEDROPPER
                if (ui.magnifier || eyedropper || (ui.tool.isAiTool && !ui.panMode)) {
                    ui.touchPoint?.let { point ->
                        drawMagnifier(
                            canvas = canvas,
                            touch = point,
                            composite = composite,
                            doc = doc,
                            controller = controller,
                            paints = paints,
                            ui = ui,
                            fabReserve = with(density) { 84.dp.toPx() },
                            swatch = eyedropper,
                            contour = contour,
                            antsPhase = ants,
                        )
                    }
                }
            }
            overlay(controller)
        }

        // Indicador de zoom con sus botones: - y + para acercar o alejar desde el centro y un
        // icono para volver al encaje inicial (con los margenes negros de siempre). Solo
        // molesta cuando no aporta, asi que se oculta en "encajar".
        if (abs(controller.zoom - controller.fitScale) > 0.01f || ui.handPan) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val centre = Offset(controller.viewSize.width / 2f, controller.viewSize.height / 2f)
                ZoomButton(Icons.Filled.Remove, stringResource(R.string.zoom_out)) {
                    controller.applyGesture(centre, Offset.Zero, 1f / 1.25f)
                }
                ZoomButton(Icons.Filled.Add, stringResource(R.string.zoom_in)) {
                    controller.applyGesture(centre, Offset.Zero, 1.25f)
                }
                // Mano: con ella activada, un dedo solo desplaza el lienzo; no mueve objetos ni
                // usa la herramienta activa. Se desactiva volviendo a pulsarla.
                ZoomButton(
                    Icons.Filled.PanTool,
                    stringResource(R.string.zoom_hand),
                    active = ui.handPan,
                ) { ui.handPan = !ui.handPan }
                ZoomButton(Icons.Filled.CenterFocusStrong, stringResource(R.string.zoom_reset)) {
                    controller.fit()
                }
                Text(
                    text = "${controller.zoomPercent} %",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Boton redondo pequeno del indicador de zoom. */
@Composable
private fun ZoomButton(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Hora del ultimo evento procesado.
 *
 * Compose no expone el instante del "up" dentro de awaitEachGesture una vez salimos del
 * bucle, asi que se usa el reloj del sistema: la referencia de awaitFirstDown proviene del
 * mismo reloj (SystemClock.uptimeMillis) y la diferencia es valida.
 */
private fun lastEventTimeFallback(downAt: Long): Long {
    val now = android.os.SystemClock.uptimeMillis()
    return if (now >= downAt) now else downAt
}

// ======================================================================================
// Matematicas de los gestos
// ======================================================================================

private fun centroidOf(changes: List<PointerInputChange>): Offset {
    var x = 0f
    var y = 0f
    changes.forEach {
        x += it.position.x
        y += it.position.y
    }
    return Offset(x / changes.size, y / changes.size)
}

/** Recorrido del arrastre en pixeles de pantalla, para compararlo con el umbral. */
private fun panDistance(pan: Offset, zoom: Float): Float = pan.getDistance() * zoom

/** Distancia media de los dedos al centroide: base del factor de zoom. */
private fun spanOf(changes: List<PointerInputChange>, centroid: Offset): Float {
    if (changes.size < 2) return 0f
    var sum = 0f
    changes.forEach { sum += (it.position - centroid).getDistance() }
    return sum / changes.size
}

/** Angulo en grados del vector entre los dos primeros dedos. */
private fun angleOf(changes: List<PointerInputChange>): Float {
    if (changes.size < 2) return 0f
    val a = changes[0].position
    val b = changes[1].position
    return Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
}

/** Diferencia de angulos normalizada a -180..180, para que no salte al cruzar 180. */
private fun deltaAngle(from: Float, to: Float): Float {
    var d = to - from
    while (d > 180f) d -= 360f
    while (d < -180f) d += 360f
    return d
}

// ======================================================================================
// Pintado
// ======================================================================================

/** Paints reutilizados: crear objetos Paint en cada fotograma se nota en el scroll. */
private class CanvasPaints {
    val bitmap = Paint(Paint.FILTER_BITMAP_FLAG)
    val checker = Paint()
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x66000000
    }
    val grid = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33000000
    }
    val guide = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xAA00B0FF.toInt()
    }
    val smart = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFFF4081.toInt()
    }
    val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF3E7BFA.toInt()
    }
    val deleteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
    val deleteCross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    val ruler = Paint().apply { color = 0xCC222222.toInt() }
    val rulerTick = Paint().apply {
        color = 0xFFEEEEEE.toInt()
        strokeWidth = 1.5f
    }
    val rulerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEEEEE.toInt()
        textSize = 20f
    }
    val swatch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }
    val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFFFFFF.toInt()
    }
    val lensCross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x99FF0000.toInt()
    }
    val path = Path()
}

private fun checkerShader(): BitmapShader {
    val tile = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(tile)
    canvas.drawColor(0xFFFFFFFF.toInt())
    val dark = Paint().apply { color = 0xFFD8D8D8.toInt() }
    canvas.drawRect(0f, 0f, 12f, 12f, dark)
    canvas.drawRect(12f, 12f, 24f, 24f, dark)
    return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

private fun docPath(doc: PixaDocument, matrix: Matrix, into: Path): Path {
    into.reset()
    into.addRect(0f, 0f, doc.width.toFloat(), doc.height.toFloat(), Path.Direction.CW)
    into.transform(matrix)
    return into
}

private fun drawDocument(
    canvas: AndroidCanvas,
    doc: PixaDocument,
    composite: Bitmap?,
    matrix: Matrix,
    checker: BitmapShader,
    paints: CanvasPaints,
) {
    val outline = docPath(doc, matrix, paints.path)

    if (doc.background.transparent) {
        // El tablero de ajedrez va en espacio de pantalla: si se escalara con el zoom
        // parecerian cuadros del dibujo y no una indicacion de transparencia.
        val saved = canvas.save()
        canvas.clipPath(outline)
        paints.checker.shader = checker
        canvas.drawPaint(paints.checker)
        canvas.restoreToCount(saved)
    }

    if (composite != null && !composite.isRecycled) {
        // El compuesto puede venir a resolucion reducida durante los gestos.
        val m = Matrix(matrix)
        m.preScale(
            doc.width.toFloat() / composite.width,
            doc.height.toFloat() / composite.height,
        )
        canvas.drawBitmap(composite, m, paints.bitmap)
    }
    canvas.drawPath(outline, paints.border)
}

private fun drawGrid(
    canvas: AndroidCanvas,
    doc: PixaDocument,
    matrix: Matrix,
    paints: CanvasPaints,
) {
    val step = Snapping.gridStep(doc.width.toFloat(), doc.height.toFloat())
    val saved = canvas.save()
    canvas.clipPath(docPath(doc, matrix, paints.path))
    canvas.concat(matrix)
    var x = step
    while (x < doc.width) {
        canvas.drawLine(x, 0f, x, doc.height.toFloat(), paints.grid)
        x += step
    }
    var y = step
    while (y < doc.height) {
        canvas.drawLine(0f, y, doc.width.toFloat(), y, paints.grid)
        y += step
    }
    canvas.restoreToCount(saved)
}

private fun drawCenterGuides(
    canvas: AndroidCanvas,
    doc: PixaDocument,
    matrix: Matrix,
    paints: CanvasPaints,
) {
    val saved = canvas.save()
    canvas.concat(matrix)
    val cx = doc.width / 2f
    val cy = doc.height / 2f
    canvas.drawLine(cx, 0f, cx, doc.height.toFloat(), paints.guide)
    canvas.drawLine(0f, cy, doc.width.toFloat(), cy, paints.guide)
    // Tercios: es la referencia de composicion que mas se usa al recortar.
    canvas.drawLine(doc.width / 3f, 0f, doc.width / 3f, doc.height.toFloat(), paints.grid)
    canvas.drawLine(doc.width * 2f / 3f, 0f, doc.width * 2f / 3f, doc.height.toFloat(), paints.grid)
    canvas.drawLine(0f, doc.height / 3f, doc.width.toFloat(), doc.height / 3f, paints.grid)
    canvas.drawLine(0f, doc.height * 2f / 3f, doc.width.toFloat(), doc.height * 2f / 3f, paints.grid)
    canvas.restoreToCount(saved)
}

private fun drawSmartGuide(
    canvas: AndroidCanvas,
    doc: PixaDocument,
    guide: SmartGuide,
    matrix: Matrix,
    paints: CanvasPaints,
) {
    val saved = canvas.save()
    canvas.concat(matrix)
    if (guide.vertical) {
        canvas.drawLine(guide.position, 0f, guide.position, doc.height.toFloat(), paints.smart)
    } else {
        canvas.drawLine(0f, guide.position, doc.width.toFloat(), guide.position, paints.smart)
    }
    canvas.restoreToCount(saved)
}

/** Marco y tiradores de la capa seleccionada. [quad] son 8 floats en coordenadas de documento. */
private fun drawHandles(
    canvas: AndroidCanvas,
    quad: FloatArray,
    matrix: Matrix,
    radius: Float,
    paints: CanvasPaints,
    deleteCorner: Boolean,
) {
    if (quad.size < 8) return
    val pts = quad.copyOf()
    matrix.mapPoints(pts)
    paints.path.reset()
    paints.path.moveTo(pts[0], pts[1])
    paints.path.lineTo(pts[2], pts[3])
    paints.path.lineTo(pts[4], pts[5])
    paints.path.lineTo(pts[6], pts[7])
    paints.path.close()
    canvas.drawPath(paints.path, paints.handleStroke)
    for (i in 0 until 4) {
        if (deleteCorner && i == 1) continue
        canvas.drawCircle(pts[i * 2], pts[i * 2 + 1], radius, paints.handleFill)
        canvas.drawCircle(pts[i * 2], pts[i * 2 + 1], radius, paints.handleStroke)
    }
    if (deleteCorner) {
        // Boton de borrar: circulo rojo con una X blanca en la esquina superior derecha.
        val x = pts[2]
        val y = pts[3]
        val r = radius * 1.35f
        canvas.drawCircle(x, y, r, paints.deleteFill)
        val a = r * 0.42f
        canvas.drawLine(x - a, y - a, x + a, y + a, paints.deleteCross)
        canvas.drawLine(x - a, y + a, x + a, y - a, paints.deleteCross)
    }
    // Tiradores de los lados: un poco mas pequenos que los de las esquinas.
    if (pts.size >= 16) {
        for (i in 4 until 8) {
            canvas.drawCircle(pts[i * 2], pts[i * 2 + 1], radius * 0.75f, paints.handleFill)
            canvas.drawCircle(pts[i * 2], pts[i * 2 + 1], radius * 0.75f, paints.handleStroke)
        }
    }
}

/** Reglas en pixeles de documento, con una marca etiquetada cada 100 px. */
private fun drawRulers(
    canvas: AndroidCanvas,
    controller: CanvasController,
    paints: CanvasPaints,
) {
    val thickness = 22f
    val width = controller.viewSize.width
    val height = controller.viewSize.height
    canvas.drawRect(0f, 0f, width, thickness, paints.ruler)
    canvas.drawRect(0f, 0f, thickness, height, paints.ruler)

    val step = rulerStep(controller.zoom)
    var doc = 0f
    while (doc <= controller.docWidth) {
        val p = controller.docToView(Offset(doc, 0f))
        if (p.x in thickness..width) {
            canvas.drawLine(p.x, 0f, p.x, thickness, paints.rulerTick)
            canvas.drawText(doc.toInt().toString(), p.x + 3f, thickness - 6f, paints.rulerText)
        }
        doc += step
    }
    doc = 0f
    while (doc <= controller.docHeight) {
        val p = controller.docToView(Offset(0f, doc))
        if (p.y in thickness..height) {
            canvas.drawLine(0f, p.y, thickness, p.y, paints.rulerTick)
        }
        doc += step
    }
}

private fun rulerStep(zoom: Float): Float = when {
    zoom >= 2f -> 50f
    zoom >= 0.5f -> 100f
    zoom >= 0.2f -> 250f
    else -> 500f
}

/**
 * Lupa (requisito 6): circular, cuadrada o rectangular segun los ajustes, con tamano y aumento
 * configurables. Descansa en la esquina elegida y, si esta activado, salta a otra cuando el
 * dedo se acerca. Nunca se coloca bajo el menu superior de las herramientas de IA, y en las
 * esquinas del lado de los botones flotantes (capas e insertar) sube lo justo para no taparlos.
 */
private fun drawMagnifier(
    canvas: AndroidCanvas,
    touch: Offset,
    composite: Bitmap?,
    doc: PixaDocument,
    controller: CanvasController,
    paints: CanvasPaints,
    ui: EditorUi,
    fabReserve: Float,
    swatch: Boolean = false,
    contour: Path? = null,
    antsPhase: Float = 0f,
) {
    if (composite == null || composite.isRecycled) return
    val view = controller.viewSize
    if (view.width <= 0f || view.height <= 0f) return

    // Con las herramientas de IA la lupa es algo mayor y se ve mas alrededor del punto.
    val aiTool = ui.tool.isAiTool
    val base = min(view.width, view.height) * (if (aiTool) 0.2f else 0.16f) * ui.magnifierSize.coerceIn(0.5f, 1.6f)
    val shape = ui.magnifierShape.coerceIn(0, 2)
    // Medias dimensiones: circulo, cuadrado (algo menor, ocupa mas) o rectangulo apaisado.
    val hw = when (shape) { 1 -> base * 0.92f; 2 -> base * 1.3f; else -> base }
    val hh = when (shape) { 1 -> base * 0.92f; 2 -> base * 0.8f; else -> base }
    val marginX = hw + 16f
    val marginY = hh + 16f

    fun cornerX(c: Int) = if (c == 1 || c == 3) view.width - marginX else marginX
    fun cornerY(c: Int): Float {
        if (c < 2) return marginY
        // Las esquinas de abajo del lado de los botones flotantes suben lo justo para no taparlos.
        val fabSide = (c == 3) == ui.fabOnRight
        return view.height - marginY - (if (fabSide) fabReserve else 0f)
    }
    // Esquinas donde la lupa no se monta con el menu superior: con la barra del borrador magico
    // solo valen las de abajo; con el boton de mover vista (arriba a la derecha), todas menos esa.
    val toolbarAtTop = ui.tool == CanvasTool.AI_MARK
    val chipAtTopRight = ui.tool.isAiTool && !toolbarAtTop
    fun allowed(c: Int): Boolean = when {
        toolbarAtTop -> c >= 2
        chipAtTopRight -> c != 1
        else -> true
    }
    // La esquina elegida en los ajustes; si cae bajo un menu, pasa a la de abajo del mismo lado.
    fun fit(c: Int): Int = if (allowed(c)) c else if (c == 1 && !toolbarAtTop) 3 else (c + 2).coerceAtMost(3)
    val rest = fit(ui.magnifierCorner.coerceIn(0, 3))
    var corner = if (ui.magnifierAvoid && ui.lensCorner in 0..3 && allowed(ui.lensCorner)) ui.lensCorner else rest
    if (ui.magnifierAvoid) {
        val trigger = max(hw, hh) * (1f + ui.magnifierMargin)
        if (hypot(touch.x - cornerX(corner), touch.y - cornerY(corner)) < trigger) {
            corner = (0..3).filter { allowed(it) }
                .maxByOrNull { hypot(touch.x - cornerX(it), touch.y - cornerY(it)) } ?: corner
            ui.lensCorner = corner
        }
    }
    val cx = cornerX(corner)
    val cy = cornerY(corner)

    // Contorno de la lupa, ensanchado [inflate] px (para el anillo del cuentagotas).
    fun shapePath(inflate: Float): Path {
        val path = paints.path
        path.reset()
        if (shape == 0) {
            path.addCircle(cx, cy, hw + inflate, Path.Direction.CW)
        } else {
            path.addRoundRect(
                android.graphics.RectF(cx - hw - inflate, cy - hh - inflate, cx + hw + inflate, cy + hh + inflate),
                24f, 24f, Path.Direction.CW,
            )
        }
        return path
    }

    val zoom = ui.magnifierZoom.coerceIn(1.2f, 6f)
    val saved = canvas.save()
    canvas.clipPath(shapePath(0f))
    canvas.drawColor(0xFF202020.toInt())

    // Documento -> vista, ampliado y recentrado en el punto que toca el dedo.
    val m = Matrix(controller.matrix())
    m.preScale(doc.width.toFloat() / composite.width, doc.height.toFloat() / composite.height)
    m.postTranslate(-touch.x, -touch.y)
    m.postScale(zoom, zoom)
    m.postTranslate(cx, cy)
    canvas.drawBitmap(composite, m, paints.bitmap)
    // Las marcas del borrador magico tambien se ven dentro de la lupa, para colocarlas con precision.
    if (ui.tool == CanvasTool.AI_MARK) {
        val docToLens = Matrix(controller.matrix())
        docToLens.postTranslate(-touch.x, -touch.y)
        docToLens.postScale(zoom, zoom)
        docToLens.postTranslate(cx, cy)
        val marksSaved = canvas.save()
        canvas.concat(docToLens)
        drawMarksNative(canvas, ui)
        canvas.restoreToCount(marksSaved)
    }
    // El trazo en curso del borrador magico (rojo, como en el lienzo) tambien se ve en la lupa.
    if (ui.tool == CanvasTool.AI_ERASE && ui.livePoints.size > 1) {
        val docToLens = Matrix(controller.matrix())
        docToLens.postTranslate(-touch.x, -touch.y)
        docToLens.postScale(zoom, zoom)
        docToLens.postTranslate(cx, cy)
        val stroke = Path()
        ui.livePoints.forEachIndexed { i, p -> if (i == 0) stroke.moveTo(p.x, p.y) else stroke.lineTo(p.x, p.y) }
        val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = ui.brush.size
            color = 0x8CFF2D55.toInt() // = 0.55 de opacidad, igual que en el lienzo
        }
        val strokeSaved = canvas.save()
        canvas.concat(docToLens)
        canvas.drawPath(stroke, strokePaint)
        canvas.restoreToCount(strokeSaved)
    }
    // Lazo, magnetico, poligono e IA: la linea que se esta dibujando tambien se ve en la lupa.
    val liveLine: List<Offset> = when (ui.tool) {
        CanvasTool.SELECT_LASSO, CanvasTool.SELECT_MAGNETIC, CanvasTool.AI_SELECT -> ui.livePoints
        CanvasTool.SELECT_POLY -> ui.dragCurrent?.let { ui.polyPoints + it } ?: ui.polyPoints
        else -> emptyList()
    }
    if (liveLine.size > 1) {
        val docToLens = Matrix(controller.matrix())
        docToLens.postTranslate(-touch.x, -touch.y)
        docToLens.postScale(zoom, zoom)
        docToLens.postTranslate(cx, cy)
        val line = Path()
        liveLine.forEachIndexed { i, p -> if (i == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y) }
        // Escala total doc -> lupa, para que el grosor sea el mismo en pantalla que fuera.
        val s = controller.zoom * zoom
        val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val lineSaved = canvas.save()
        canvas.concat(docToLens)
        linePaint.strokeWidth = 6f / s
        linePaint.color = 0x99000000.toInt() // halo oscuro
        canvas.drawPath(line, linePaint)
        linePaint.strokeWidth = 3f / s
        linePaint.color = 0xFFFFFFFF.toInt()
        canvas.drawPath(line, linePaint)
        canvas.restoreToCount(lineSaved)
    }
    // La seleccion activa tambien se ve dentro de la lupa, para saber con precision que
    // se anade o se resta.
    if (contour != null) {
        val docToLens = Matrix(controller.matrix())
        docToLens.postTranslate(-touch.x, -touch.y)
        docToLens.postScale(zoom, zoom)
        docToLens.postTranslate(cx, cy)
        val contourSaved = canvas.save()
        canvas.concat(docToLens)
        MarchingAnts.draw(canvas, contour, controller.zoom * zoom, antsPhase, ui.selectionLineWidth)
        canvas.restoreToCount(contourSaved)
    }
    canvas.restoreToCount(saved)

    // Circulo con el tamano real del pincel, para ver exactamente que cubrira.
    if ((ui.tool == CanvasTool.AI_MARK && ui.markShape == MarkShape.BRUSH) || ui.tool == CanvasTool.AI_ERASE) {
        val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xFFFFFFFF.toInt()
        }
        val ringRadius = (ui.brush.size / 2f * controller.zoom * zoom).coerceIn(3f, min(hw, hh) - 4f)
        canvas.drawCircle(cx, cy, ringRadius, ring)
    }

    // Anillo con el color exacto que se tomaria al levantar el dedo.
    if (swatch) {
        val p = controller.viewToDoc(touch)
        if (p.x >= 0f && p.y >= 0f && p.x < doc.width && p.y < doc.height) {
            val sx = (p.x * composite.width / doc.width).toInt().coerceIn(0, composite.width - 1)
            val sy = (p.y * composite.height / doc.height).toInt().coerceIn(0, composite.height - 1)
            paints.swatch.color = composite.getPixel(sx, sy)
            canvas.drawPath(shapePath(7f), paints.swatch)
        }
    }

    canvas.drawPath(shapePath(0f), paints.lens)
    // En seleccion la mira ocupa media lupa (con borde oscuro para verse sobre cualquier
    // imagen), para apuntar con precision; en el resto se queda pequena.
    val selecting = ui.tool.name.startsWith("SELECT_")
    val arm = if (selecting) min(hw, hh) * 0.85f * ui.lensCrossSize.coerceIn(0.5f, 2f) else 10f
    if (selecting) {
        fun cross(width: Float, argb: Int) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = width
                color = argb
            }
            canvas.drawLine(cx - arm, cy, cx + arm, cy, p)
            canvas.drawLine(cx, cy - arm, cx, cy + arm, p)
        }
        val thick = ui.lensCrossWidth.coerceIn(0.5f, 3f)
        cross(9f * thick, 0xB0000000.toInt()) // borde oscuro
        cross(5f * thick, 0xFFFFFFFF.toInt()) // mira blanca gruesa
    } else {
        canvas.drawLine(cx - arm, cy, cx + arm, cy, paints.lensCross)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, paints.lensCross)
    }
}

// ======================================================================================
// Utilidades para los overlays de las herramientas
// ======================================================================================

/** Convierte una lista de puntos de documento en un Path en coordenadas de vista. */
fun pathFromDocPoints(points: List<Offset>, controller: CanvasController): Path {
    val path = Path()
    points.forEachIndexed { index, point ->
        val p = controller.docToView(point)
        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    return path
}

/** Rectangulo normalizado entre dos puntos, util para los arrastres de seleccion y recorte. */
fun rectBetween(a: Offset, b: Offset): android.graphics.RectF = android.graphics.RectF(
    min(a.x, b.x),
    min(a.y, b.y),
    max(a.x, b.x),
    max(a.y, b.y),
)

/** Distancia entre dos puntos, en las unidades en que vengan. */
fun distance(a: Offset, b: Offset): Float = hypot(b.x - a.x, b.y - a.y)

/** Marcas del borrador magico (y la que esta en curso) dibujadas en coordenadas de documento. */
private fun drawMarksNative(canvas: AndroidCanvas, ui: EditorUi) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FF2D55.toInt() }
    fun one(shape: MarkShape, pts: List<Offset>, radius: Float) {
        if (pts.isEmpty()) return
        when (shape) {
            MarkShape.BRUSH -> {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = (radius * 2f).coerceAtLeast(1f)
                paint.strokeCap = android.graphics.Paint.Cap.ROUND
                paint.strokeJoin = android.graphics.Paint.Join.ROUND
                val path = Path()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                if (pts.size == 1) path.lineTo(pts[0].x + 0.5f, pts[0].y + 0.5f)
                canvas.drawPath(path, paint)
            }
            MarkShape.RECT, MarkShape.ELLIPSE -> if (pts.size >= 2) {
                paint.style = android.graphics.Paint.Style.FILL
                val rect = android.graphics.RectF(
                    min(pts.first().x, pts.last().x), min(pts.first().y, pts.last().y),
                    max(pts.first().x, pts.last().x), max(pts.first().y, pts.last().y),
                )
                if (shape == MarkShape.RECT) canvas.drawRect(rect, paint) else canvas.drawOval(rect, paint)
            }
            MarkShape.LASSO -> if (pts.size >= 3) {
                paint.style = android.graphics.Paint.Style.FILL
                val path = Path()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }
    for (m in ui.marks) one(m.shape, m.points, m.radius)
    val live = ui.livePoints
    if (live.isNotEmpty()) {
        val pts = if (ui.markShape == MarkShape.RECT || ui.markShape == MarkShape.ELLIPSE) {
            listOf(ui.dragStart ?: live.first(), live.last())
        } else {
            live
        }
        one(ui.markShape, pts, ui.brush.size / 2f)
    }
}
