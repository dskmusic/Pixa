package com.dskmusic.pixa.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.dskmusic.pixa.MainActivity
import com.dskmusic.pixa.R
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.nav.PixaActions
import kotlinx.coroutines.flow.first

/**
 * Widget de pantalla de inicio (requisito 2).
 *
 * Tres accesos: lienzo nuevo, ultima edicion y camara. Cada uno lanza [MainActivity] con
 * una accion distinta de [PixaActions]; la actividad la traduce a un destino con
 * LaunchRequest, igual que los atajos del launcher, asi que no hay codigo de navegacion
 * duplicado aqui.
 */
class PixaWidget : GlanceAppWidget() {

    /** Un solo tamano: el contenido es una fila de tres celdas que se reparte el ancho. */
    override val sizeMode: SizeMode = SizeMode.Single

    /**
     * Sin estado propio: lo que muestra el widget sale de los ajustes y del almacen de
     * proyectos, que ya son persistentes. Declararlo a null evita crear un DataStore mas.
     */
    override val stateDefinition: GlanceStateDefinition<*>? = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.pixa
        val lastId = container.settings.settings.first().lastProjectId
        // Se relee el indice en lugar de mirar el StateFlow: el proceso puede haber
        // arrancado por el propio widget y la lista en memoria estaria vacia.
        val lastName = lastId?.let { projectId ->
            container.projects.refresh()
                .firstOrNull { it.id == projectId && it.deletedAt == null }
                ?.name
        }
        provideContent { WidgetBody(lastName) }
    }

    companion object {
        /**
         * Repinta el widget. Se llama al guardar o abrir un proyecto para que el nombre
         * de "ultima edicion" no se quede viejo.
         */
        suspend fun refresh(context: Context) {
            runCatching { PixaWidget().updateAll(context) }
        }
    }
}

/** Receptor declarado en el manifest. */
class PixaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PixaWidget()
}

@Composable
private fun WidgetBody(lastName: String?) {
    val context = LocalContext.current
    GlanceTheme {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                // ponytail: cornerRadius solo redondea en API 31+; por debajo Glance lo
                // ignora y el widget sale cuadrado. Si hiciera falta en API 29-30, poner
                // un drawable shape como fondo en lugar del color del tema.
                .cornerRadius(16.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tile(
                iconRes = R.drawable.ic_shortcut_new,
                label = context.getString(R.string.widget_new_canvas),
                iconDescription = null,
                action = PixaActions.NEW_CANVAS,
            )
            Tile(
                iconRes = R.drawable.ic_widget_last,
                label = lastName ?: context.getString(R.string.widget_no_last),
                // Aqui si se describe el icono: el texto de debajo es el nombre del
                // proyecto, que no dice por si solo de que accion se trata. En las otras
                // dos celdas el texto ya es la accion y describir el icono la repetiria.
                iconDescription = context.getString(R.string.widget_last_edit),
                action = PixaActions.LAST_PROJECT.takeIf { lastName != null },
            )
            Tile(
                iconRes = R.drawable.ic_shortcut_camera,
                label = context.getString(R.string.widget_camera),
                iconDescription = null,
                action = PixaActions.CAMERA,
            )
        }
    }
}

/**
 * Una celda del widget: icono, texto y toque.
 *
 * Es extension de [RowScope] porque [GlanceModifier.defaultWeight] solo existe dentro de
 * la fila, y asi las tres celdas se reparten el ancho por igual.
 */
@Composable
private fun RowScope.Tile(
    iconRes: Int,
    label: String,
    iconDescription: String?,
    action: String?,
) {
    val context = LocalContext.current
    // Sin accion (no hay proyecto reciente) el toque abre la app en el inicio.
    val intent = if (action == null) {
        Intent(context, MainActivity::class.java)
    } else {
        Intent(action).setClass(context, MainActivity::class.java)
    }
    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .clickable(actionStartActivity(intent)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = iconDescription,
            modifier = GlanceModifier.size(26.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}
