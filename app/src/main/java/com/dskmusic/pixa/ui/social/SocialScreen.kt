package com.dskmusic.pixa.ui.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.dskmusic.pixa.ui.common.PixaToastHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dskmusic.pixa.R
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.CanvasBackground
import com.dskmusic.pixa.core.model.FontRef
import com.dskmusic.pixa.core.model.LayerContent
import com.dskmusic.pixa.core.model.PixaDocument
import com.dskmusic.pixa.core.model.PixaLayer
import com.dskmusic.pixa.core.render.SocialCard
import com.dskmusic.pixa.core.render.SocialComment
import com.dskmusic.pixa.core.render.SocialFormat
import com.dskmusic.pixa.core.render.SocialLabels
import com.dskmusic.pixa.core.render.SocialPlatform
import com.dskmusic.pixa.core.render.SocialPost
import com.dskmusic.pixa.data.settings.PixaSettings
import com.dskmusic.pixa.pixa
import com.dskmusic.pixa.ui.common.BusyOverlay
import com.dskmusic.pixa.ui.common.EditorMessages
import com.dskmusic.pixa.ui.common.GeneratorActions
import com.dskmusic.pixa.ui.common.GeneratorPreview
import com.dskmusic.pixa.ui.common.NumberField
import com.dskmusic.pixa.ui.common.OptionRow
import com.dskmusic.pixa.ui.common.PixaTopBar
import com.dskmusic.pixa.ui.common.rememberLeaveGuard
import com.dskmusic.pixa.core.project.DraftStore
import com.dskmusic.pixa.ui.common.DraftSnapshot
import com.dskmusic.pixa.ui.common.rememberDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.dskmusic.pixa.ui.common.SectionTitle
import com.dskmusic.pixa.ui.common.TextFieldRow
import com.dskmusic.pixa.ui.common.ToggleRow
import com.dskmusic.pixa.ui.common.rememberImagePicker
import com.dskmusic.pixa.ui.common.rememberMultiImagePicker
import com.dskmusic.pixa.ui.editor.EditorViewModel
import com.dskmusic.pixa.ui.editor.panels.ColorRow
import com.dskmusic.pixa.ui.editor.panels.FontPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Los doce formatos con su etiqueta, en el orden en que se ofrecen. */
private val FORMATS = listOf(
    SocialFormat.TEXT to R.string.social_fmt_text,
    SocialFormat.PHOTO to R.string.social_fmt_photo,
    SocialFormat.CAROUSEL to R.string.social_fmt_carousel,
    SocialFormat.THREAD to R.string.social_fmt_thread,
    SocialFormat.VIDEO to R.string.social_fmt_video,
    SocialFormat.STORY to R.string.social_fmt_story,
    SocialFormat.REEL to R.string.social_fmt_reel,
    SocialFormat.GROUP to R.string.social_fmt_group,
    SocialFormat.PROFILE to R.string.social_fmt_profile,
    SocialFormat.SAVED to R.string.social_fmt_saved,
    SocialFormat.CHAT to R.string.social_fmt_chat,
    SocialFormat.STREAM to R.string.social_fmt_stream,
)

/** Las plataformas en el orden del requisito 19. */
private val PLATFORMS = listOf(
    SocialPlatform.X,
    SocialPlatform.TWITTER,
    SocialPlatform.INSTAGRAM,
    SocialPlatform.FACEBOOK,
    SocialPlatform.YOUTUBE,
    SocialPlatform.TIKTOK,
    SocialPlatform.WHATSAPP,
    SocialPlatform.THREADS,
    SocialPlatform.REDDIT,
    SocialPlatform.LINKEDIN,
    SocialPlatform.PINTEREST,
    SocialPlatform.TWITCH,
)

/**
 * Logotipo oficial de cada red (requisito del usuario): nada de texto con el nombre en
 * color, el icono de verdad en un circulo con el color de marca. SVG de Simple Icons
 * (CC0), convertidos a vector drawable.
 */
private fun brandIcon(platform: SocialPlatform): Int = when (platform) {
    SocialPlatform.X -> R.drawable.ic_brand_x
    SocialPlatform.TWITTER -> R.drawable.ic_brand_twitter
    SocialPlatform.INSTAGRAM -> R.drawable.ic_brand_instagram
    SocialPlatform.FACEBOOK -> R.drawable.ic_brand_facebook
    SocialPlatform.YOUTUBE -> R.drawable.ic_brand_youtube
    SocialPlatform.TIKTOK -> R.drawable.ic_brand_tiktok
    SocialPlatform.WHATSAPP -> R.drawable.ic_brand_whatsapp
    SocialPlatform.THREADS -> R.drawable.ic_brand_threads
    SocialPlatform.REDDIT -> R.drawable.ic_brand_reddit
    SocialPlatform.LINKEDIN -> R.drawable.ic_brand_linkedin
    SocialPlatform.PINTEREST -> R.drawable.ic_brand_pinterest
    SocialPlatform.TWITCH -> R.drawable.ic_brand_twitch
}

/**
 * Rasteriza el logotipo (en blanco) para que el renderizador de la imagen exportada lo
 * dibuje de verdad, en vez de una chapa de color con el nombre (requisito del usuario: que
 * el post generado parezca una captura real).
 */
private fun brandLogoBitmap(context: Context, platform: SocialPlatform, sizePx: Int): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, brandIcon(platform))?.mutate() ?: return null
    drawable.setTint(android.graphics.Color.WHITE)
    drawable.setBounds(0, 0, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    drawable.draw(Canvas(bitmap))
    return bitmap
}

@Composable
private fun PlatformSelector(
    platforms: List<SocialPlatform>,
    selected: SocialPlatform,
    onSelect: (SocialPlatform) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        platforms.forEach { platform ->
            val isSelected = platform == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { onSelect(platform) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(platform.accent), CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Icon(
                        painter = painterResource(brandIcon(platform)),
                        contentDescription = platform.brand,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    platform.brand,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Formatos que usan la lista de respuestas, comentarios o mensajes. */
private val WITH_REPLIES = setOf(SocialFormat.THREAD, SocialFormat.CHAT, SocialFormat.GROUP)

private const val MAX_REPLIES = 8
private const val MAX_IMAGES = 8
private const val AVATAR_DECODE = 400
private const val IMAGE_DECODE = 1200
private const val COUNTER_MAX = 999_999

/**
 * Maquetas de publicacion social (requisito 19).
 *
 * La pantalla solo recoge el contenido y se lo pasa a [SocialCard], que es quien dibuja.
 * Elegir plataforma cambia el color de marca y la lista de formatos; el aviso de arriba
 * recuerda que son imitaciones de aspecto y para que no deben usarse.
 */
private val DRAFT_JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/** Estado del post para recuperarlo tras un cierre inesperado. */
@Serializable
private class SocialDraft(
    val platform: SocialPlatform,
    val format: SocialFormat,
    val dark: Boolean,
    val displayName: String,
    val handle: String,
    val verified: Boolean,
    val body: String,
    val datetime: String,
    val location: String,
    val link: String,
    val likes: Int,
    val comments: Int,
    val shares: Int,
    val saves: Int,
    val views: Int,
    val accent: Int,
    val avatarUri: String?,
    val imageUris: List<String>,
    val replies: List<SocialComment>,
    val fontRef: FontRef,
)

@Composable
fun SocialScreen(
    editor: EditorViewModel,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val vm = editor
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val fonts = remember(context) { context.pixa.fonts }
    val settings by context.pixa.settings.settings.collectAsStateWithLifecycle(PixaSettings())

    var platform by remember { mutableStateOf(SocialPlatform.X) }
    var format by remember { mutableStateOf(SocialPlatform.X.defaultFormat) }
    var dark by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf("") }
    var datetime by remember {
        mutableStateOf(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date()),
        )
    }
    var location by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }

    var likes by remember { mutableStateOf(0) }
    var comments by remember { mutableStateOf(0) }
    var shares by remember { mutableStateOf(0) }
    var saves by remember { mutableStateOf(0) }
    var views by remember { mutableStateOf(0) }
    // El color arranca en el de la marca, pero se puede cambiar a mano: por eso es
    // estado propio y no se lee de la plataforma al dibujar.
    var accent by remember { mutableStateOf(SocialPlatform.X.accent) }

    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val imageUris = remember { mutableStateListOf<Uri>() }
    val replies = remember { mutableStateListOf<SocialComment>() }

    var fontRef by remember { mutableStateOf(FontRef(family = "Inter")) }
    var pickingFont by remember { mutableStateOf(false) }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }

    val pickAvatar = rememberImagePicker { uri -> avatarUri = uri }
    val pickImages = rememberMultiImagePicker(MAX_IMAGES) { picked ->
        imageUris.clear()
        imageUris.addAll(picked.take(MAX_IMAGES))
    }

    val post = SocialPost(
        platform = platform,
        format = format,
        dark = dark,
        displayName = displayName,
        handle = handle,
        verified = verified,
        body = body,
        datetime = datetime,
        location = location,
        link = link,
        likes = likes,
        comments = comments,
        shares = shares,
        saves = saves,
        views = views,
        replies = if (format in WITH_REPLIES) replies.toList() else emptyList(),
        accent = accent,
        liveLabel = stringResource(R.string.social_live),
        labels = SocialLabels(
            likes = stringResource(R.string.social_ui_likes),
            viewAll = stringResource(R.string.social_ui_view_all),
            views = stringResource(R.string.social_ui_views),
            like = stringResource(R.string.social_ui_like),
            comment = stringResource(R.string.social_ui_comment),
            share = stringResource(R.string.social_ui_share),
            repost = stringResource(R.string.social_ui_repost),
            send = stringResource(R.string.social_ui_send),
            follow = stringResource(R.string.social_ui_follow),
            following = stringResource(R.string.social_ui_following),
            forYou = stringResource(R.string.social_ui_for_you),
            subscribe = stringResource(R.string.social_ui_subscribe),
            save = stringResource(R.string.social_ui_save),
            join = stringResource(R.string.social_ui_join),
            comments = stringResource(R.string.social_ui_comments),
            replies = stringResource(R.string.social_ui_replies),
            shares = stringResource(R.string.social_ui_shares),
            sound = stringResource(R.string.social_ui_sound),
            followers = stringResource(R.string.social_ui_followers),
            posts = stringResource(R.string.social_ui_posts),
            message = stringResource(R.string.social_ui_message),
        ),
    )
    val imageKeys = imageUris.toList()

    // Un data class como clave: el render solo se repite cuando cambia algo del contenido.
    LaunchedEffect(post, avatarUri, imageKeys, fontRef) {
        if (post.displayName.isBlank() && post.body.isBlank()) {
            preview = null
            rendering = false
            return@LaunchedEffect
        }
        delay(220)
        rendering = true
        val typeface = fonts.load(fontRef)
        val result = withContext(Dispatchers.Default) {
            // ponytail: se redecodifican las fotos en cada render. Con el antirrebote y el
            // tope de ocho imagenes no se nota; si llegara a notarse, cachear por uri.
            val avatar = avatarUri?.let {
                runCatching { BitmapUtils.decodeUri(context, it, AVATAR_DECODE) }.getOrNull()
            }
            val images = imageKeys.mapNotNull {
                runCatching { BitmapUtils.decodeUri(context, it, IMAGE_DECODE) }.getOrNull()
            }
            val brandLogo = brandLogoBitmap(context, post.platform, 168)
            val card = runCatching {
                SocialCard.render(post, avatar, images, typeface, brandLogo)
            }.getOrNull()
            brandLogo?.recycle()
            avatar?.recycle()
            images.forEach { it.recycle() }
            card
        }
        rendering = false
        // El anterior no se recicla: puede estar ya instalado como capa en el editor.
        if (result != null) preview = result
    }

    val build: () -> PixaDocument? = build@{
        val bitmap = preview ?: return@build null
        val layer = PixaLayer(
            name = context.getString(R.string.social_title),
            content = LayerContent.Raster(vm.putBitmap(bitmap)),
        )
        PixaDocument(
            width = bitmap.width,
            height = bitmap.height,
            background = CanvasBackground(transparent = false, color = 0xFFFFFFFF.toInt()),
            layers = listOf(layer),
            selectedLayerId = layer.id,
        )
    }

    EditorMessages(vm, snackbar)
    val dirty = displayName.isNotBlank() || body.isNotBlank() || avatarUri != null ||
        imageUris.isNotEmpty() || replies.isNotEmpty()
    // Las imagenes se guardan solo como direccion (Uri): si el sistema ya no deja leerlas,
    // se pierden, pero el texto y los ajustes se recuperan siempre.
    val draft = rememberDraft(
        kind = DraftStore.SOCIAL,
        vm = vm,
        snapshot = {
            val has = displayName.isNotBlank() || body.isNotBlank() || avatarUri != null ||
                imageUris.isNotEmpty() || replies.isNotEmpty()
            if (!has) {
                null
            } else {
                val d = SocialDraft(
                    platform = platform, format = format, dark = dark, displayName = displayName,
                    handle = handle, verified = verified, body = body, datetime = datetime,
                    location = location, link = link, likes = likes, comments = comments,
                    shares = shares, saves = saves, views = views, accent = accent,
                    avatarUri = avatarUri?.toString(), imageUris = imageUris.map { it.toString() },
                    replies = replies.toList(), fontRef = fontRef,
                )
                DraftSnapshot(DRAFT_JSON.encodeToString(d), emptyList())
            }
        },
        restore = { json, _ ->
            val d = DRAFT_JSON.decodeFromString<SocialDraft>(json)
            platform = d.platform; format = d.format; dark = d.dark
            displayName = d.displayName; handle = d.handle; verified = d.verified
            body = d.body; datetime = d.datetime; location = d.location; link = d.link
            likes = d.likes; comments = d.comments; shares = d.shares
            saves = d.saves; views = d.views; accent = d.accent
            avatarUri = d.avatarUri?.let { Uri.parse(it) }
            imageUris.clear(); imageUris.addAll(d.imageUris.map { Uri.parse(it) })
            replies.clear(); replies.addAll(d.replies)
            fontRef = d.fontRef
        },
    )
    val guardedBack = rememberLeaveGuard(
        dirty = dirty,
        onLeave = { draft.discard(); onBack() },
        vm = vm,
        defaultName = stringResource(R.string.social_title),
        build = build,
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PixaTopBar(
                title = stringResource(R.string.social_title),
                onBack = guardedBack,
                actions = {
                    Text(
                        stringResource(R.string.font_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    TextButton(onClick = { pickingFont = true }) {
                        Text(fontRef.family, maxLines = 1)
                    }
                },
            )
        },
        snackbarHost = { PixaToastHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                GeneratorPreview(
                    bitmap = preview,
                    emptyText = stringResource(R.string.social_empty),
                    contentDescription = stringResource(R.string.label_preview),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.social_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )

                    SectionTitle(stringResource(R.string.social_platform))
                    PlatformSelector(
                        platforms = PLATFORMS,
                        selected = platform,
                        onSelect = { picked ->
                            platform = picked
                            accent = picked.accent
                            // El formato elegido puede no existir en la plataforma nueva
                            // (un chat de WhatsApp no es un carrusel): se respeta si cabe
                            // y si no se cae al tipico de la plataforma.
                            if (format !in picked.formats) format = picked.defaultFormat
                        },
                    )

                    SectionTitle(stringResource(R.string.social_format))
                    val formatLabels = FORMATS.associate { (value, res) ->
                        value to context.getString(res)
                    }
                    val available = FORMATS.map { it.first }.filter { it in platform.formats }
                    OptionRow(
                        options = available,
                        selected = format,
                        onSelect = { format = it },
                        label = { formatLabels[it].orEmpty() },
                    )
                    ToggleRow(
                        label = stringResource(R.string.social_dark_mode),
                        checked = dark,
                        onCheckedChange = { dark = it },
                    )
                    ColorRow(
                        label = stringResource(R.string.social_accent),
                        color = accent,
                        settings = settings,
                        onPick = { accent = it },
                        withAlpha = false,
                    )

                    SectionTitle(stringResource(R.string.social_content))
                    TextFieldRow(
                        label = stringResource(R.string.social_display_name),
                        value = displayName,
                        onValueChange = { displayName = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    TextFieldRow(
                        label = stringResource(R.string.social_handle),
                        value = handle,
                        onValueChange = { handle = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    ToggleRow(
                        label = stringResource(R.string.social_verified),
                        checked = verified,
                        onCheckedChange = { verified = it },
                    )
                    TextFieldRow(
                        label = stringResource(R.string.social_body),
                        value = body,
                        onValueChange = { body = it },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    TextFieldRow(
                        label = stringResource(R.string.social_datetime),
                        value = datetime,
                        onValueChange = { datetime = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    TextFieldRow(
                        label = stringResource(R.string.social_location),
                        value = location,
                        onValueChange = { location = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    TextFieldRow(
                        label = stringResource(R.string.social_link),
                        value = link,
                        onValueChange = { link = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    SectionTitle(stringResource(R.string.social_images))
                    PickRow(
                        label = stringResource(R.string.social_avatar),
                        detail = null,
                        chosen = avatarUri != null,
                        onPick = pickAvatar,
                        onClear = { avatarUri = null },
                    )
                    PickRow(
                        label = stringResource(R.string.social_pick_images),
                        detail = if (imageUris.isEmpty()) {
                            null
                        } else {
                            stringResource(R.string.batch_images_selected, imageUris.size)
                        },
                        chosen = imageUris.isNotEmpty(),
                        onPick = pickImages,
                        onClear = { imageUris.clear() },
                    )

                    SectionTitle(stringResource(R.string.social_stats))
                    CounterRow(
                        firstLabel = stringResource(R.string.social_likes),
                        firstValue = likes,
                        onFirst = { likes = it },
                        secondLabel = stringResource(R.string.social_comments),
                        secondValue = comments,
                        onSecond = { comments = it },
                    )
                    CounterRow(
                        firstLabel = stringResource(R.string.social_shares),
                        firstValue = shares,
                        onFirst = { shares = it },
                        secondLabel = stringResource(R.string.social_saves),
                        secondValue = saves,
                        onSecond = { saves = it },
                    )
                    CounterRow(
                        firstLabel = stringResource(R.string.social_views),
                        firstValue = views,
                        onFirst = { views = it },
                        secondLabel = null,
                        secondValue = 0,
                        onSecond = {},
                    )

                    if (format in WITH_REPLIES) {
                        SectionTitle(stringResource(R.string.social_replies))
                        replies.forEachIndexed { index, reply ->
                            ReplyEditor(
                                reply = reply,
                                chat = format == SocialFormat.CHAT,
                                thread = format == SocialFormat.THREAD,
                                onChange = { replies[index] = it },
                                onRemove = { replies.removeAt(index) },
                            )
                        }
                        if (replies.size < MAX_REPLIES) {
                            TextButton(
                                onClick = { replies.add(SocialComment()) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Text(stringResource(R.string.social_comment_add))
                            }
                        }
                    }
                }

                GeneratorActions(
                    vm = vm,
                    defaultName = stringResource(R.string.social_title),
                    onOpenEditor = { draft.discard(); onOpenEditor() },
                    enabled = preview != null && !rendering,
                    build = build,
                )
            }

            BusyOverlay(visible = rendering)
        }
    }

    if (pickingFont) {
        FontPickerDialog(
            current = fontRef,
            onDismiss = { pickingFont = false },
            onPick = {
                fontRef = it
                pickingFont = false
            },
        )
    }
}

/** Fila de imagen: elegir o reemplazar, y quitar si ya hay una. */
@Composable
private fun PickRow(
    label: String,
    detail: String?,
    chosen: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onPick) {
            Text(
                stringResource(if (chosen) R.string.action_replace else R.string.action_add),
            )
        }
        if (chosen) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_remove))
            }
        }
    }
}

/** Dos contadores por fila; el segundo es opcional para que la ultima quede suelta. */
@Composable
private fun CounterRow(
    firstLabel: String,
    firstValue: Int,
    onFirst: (Int) -> Unit,
    secondLabel: String?,
    secondValue: Int,
    onSecond: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NumberField(
            label = firstLabel,
            value = firstValue,
            onValueChange = onFirst,
            modifier = Modifier.weight(1f),
            range = 0..COUNTER_MAX,
        )
        if (secondLabel != null) {
            NumberField(
                label = secondLabel,
                value = secondValue,
                onValueChange = onSecond,
                modifier = Modifier.weight(1f),
                range = 0..COUNTER_MAX,
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }
    }
}

/** Editor de una respuesta, comentario o mensaje de chat. */
@Composable
private fun ReplyEditor(
    reply: SocialComment,
    chat: Boolean,
    thread: Boolean,
    onChange: (SocialComment) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextFieldRow(
                label = stringResource(R.string.social_comment_author),
                value = reply.author,
                onValueChange = { onChange(reply.copy(author = it)) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete_item))
            }
        }
        TextFieldRow(
            label = stringResource(R.string.social_comment_text),
            value = reply.text,
            onValueChange = { onChange(reply.copy(text = it)) },
            singleLine = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (chat) {
            ToggleRow(
                label = stringResource(R.string.social_mine),
                checked = reply.mine,
                onCheckedChange = { onChange(reply.copy(mine = it)) },
            )
        }
        if (thread) {
            NumberField(
                label = stringResource(R.string.social_likes),
                value = reply.likes,
                onValueChange = { onChange(reply.copy(likes = it)) },
                modifier = Modifier.padding(horizontal = 16.dp),
                range = 0..COUNTER_MAX,
            )
        }
    }
}
