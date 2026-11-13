package com.dskmusic.pixa.core.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min

/** Los doce formatos de publicacion del requisito 19. */
enum class SocialFormat {
    TEXT, PHOTO, CAROUSEL, THREAD, VIDEO, STORY, REEL, GROUP, PROFILE, SAVED, CHAT, STREAM,
}

/**
 * Las once plataformas del requisito 19.
 *
 * Cada una aporta tres cosas: el nombre que se escribe en la maqueta, el color con el que
 * se reconoce y los formatos que tienen sentido en ella (en X no hay carrete, en Pinterest
 * no hay directo). El dibujo es el mismo para todas: la plataforma solo cambia el rotulo,
 * el color y la lista de formatos, no hay doce motores distintos.
 *
 * El nombre es texto, no un logotipo: no se incluye ninguna imagen de marca en el
 * proyecto. Son maquetas de aspecto para uso personal, nunca para suplantar a nadie.
 */
enum class SocialPlatform(
    val brand: String,
    val accent: Int,
    val formats: List<SocialFormat>,
) {
    X(
        "X",
        0xFF0F1419.toInt(),
        listOf(
            SocialFormat.TEXT, SocialFormat.PHOTO, SocialFormat.THREAD,
            SocialFormat.VIDEO, SocialFormat.PROFILE, SocialFormat.SAVED,
        ),
    ),
    TWITTER(
        "Twitter",
        0xFF1D9BF0.toInt(),
        listOf(
            SocialFormat.TEXT, SocialFormat.PHOTO, SocialFormat.THREAD,
            SocialFormat.VIDEO, SocialFormat.PROFILE, SocialFormat.SAVED,
        ),
    ),
    INSTAGRAM(
        "Instagram",
        0xFFE1306C.toInt(),
        listOf(
            SocialFormat.PHOTO, SocialFormat.CAROUSEL, SocialFormat.STORY,
            SocialFormat.REEL, SocialFormat.PROFILE, SocialFormat.SAVED,
        ),
    ),
    FACEBOOK(
        "Facebook",
        0xFF1877F2.toInt(),
        listOf(
            SocialFormat.TEXT, SocialFormat.PHOTO, SocialFormat.CAROUSEL,
            SocialFormat.VIDEO, SocialFormat.STORY, SocialFormat.GROUP,
            SocialFormat.PROFILE,
        ),
    ),
    YOUTUBE(
        "YouTube",
        0xFFFF0000.toInt(),
        listOf(
            SocialFormat.VIDEO, SocialFormat.REEL, SocialFormat.STREAM,
            SocialFormat.PROFILE,
        ),
    ),
    TIKTOK(
        "TikTok",
        0xFFFE2C55.toInt(),
        listOf(
            SocialFormat.REEL, SocialFormat.STORY, SocialFormat.STREAM,
            SocialFormat.PROFILE,
        ),
    ),
    WHATSAPP(
        "WhatsApp",
        0xFF25D366.toInt(),
        listOf(SocialFormat.CHAT, SocialFormat.GROUP, SocialFormat.STORY),
    ),
    THREADS(
        "Threads",
        0xFF101010.toInt(),
        listOf(
            SocialFormat.TEXT, SocialFormat.PHOTO, SocialFormat.THREAD,
            SocialFormat.CAROUSEL, SocialFormat.PROFILE,
        ),
    ),
    REDDIT(
        "Reddit",
        0xFFFF4500.toInt(),
        listOf(
            SocialFormat.TEXT, SocialFormat.PHOTO, SocialFormat.THREAD,
            SocialFormat.VIDEO, SocialFormat.GROUP, SocialFormat.SAVED,
        ),
    ),
    LINKEDIN(
        "LinkedIn",
        0xFF0A66C2.toInt(),
        listOf(
            SocialFormat.TEXT, SocialFormat.PHOTO, SocialFormat.CAROUSEL,
            SocialFormat.VIDEO, SocialFormat.PROFILE,
        ),
    ),
    PINTEREST(
        "Pinterest",
        0xFFE60023.toInt(),
        listOf(
            SocialFormat.PHOTO, SocialFormat.CAROUSEL, SocialFormat.SAVED,
            SocialFormat.PROFILE,
        ),
    ),
    TWITCH(
        "Twitch",
        0xFF9146FF.toInt(),
        listOf(
            SocialFormat.STREAM, SocialFormat.VIDEO, SocialFormat.CHAT,
            SocialFormat.PROFILE,
        ),
    ),
    ;

    /** Formato por omision: el primero de la lista, el mas tipico de la plataforma. */
    val defaultFormat: SocialFormat get() = formats.first()
}

/** Un comentario, una respuesta del hilo o un mensaje del chat, segun el formato. */
@kotlinx.serialization.Serializable
data class SocialComment(
    val author: String = "",
    val text: String = "",
    val likes: Int = 0,
    /** En el formato chat marca los mensajes propios, que van a la derecha. */
    val mine: Boolean = false,
)

/** Textos fijos de las interfaces (botones, rotulos). Los pone la pantalla, ya traducidos. */
data class SocialLabels(
    val likes: String = "likes",
    val viewAll: String = "View all %s comments",
    val views: String = "views",
    val like: String = "Like",
    val comment: String = "Comment",
    val share: String = "Share",
    val repost: String = "Repost",
    val send: String = "Send",
    val follow: String = "Follow",
    val following: String = "Following",
    val forYou: String = "For You",
    val subscribe: String = "Subscribe",
    val save: String = "Save",
    val join: String = "Join",
    val comments: String = "comments",
    val replies: String = "replies",
    val shares: String = "shares",
    val sound: String = "Original sound",
    val followers: String = "Followers",
    val posts: String = "Posts",
    val message: String = "Message",
)

/** Todo el contenido editable de una publicacion. */
data class SocialPost(
    val platform: SocialPlatform = SocialPlatform.X,
    val format: SocialFormat = SocialFormat.TEXT,
    val dark: Boolean = false,
    val displayName: String = "",
    val handle: String = "",
    val verified: Boolean = false,
    val body: String = "",
    val datetime: String = "",
    val location: String = "",
    val link: String = "",
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val saves: Int = 0,
    val views: Int = 0,
    val replies: List<SocialComment> = emptyList(),
    /** Color de marca; arranca en el de la plataforma pero se puede cambiar. */
    val accent: Int = SocialPlatform.X.accent,
    /** Etiqueta del directo; la pone la pantalla para que este traducida. */
    val liveLabel: String = "LIVE",
    val labels: SocialLabels = SocialLabels(),
)

/**
 * Dibuja las maquetas de publicacion social del requisito 19.
 *
 * Doce formatos (texto, foto, carrusel, hilo, video, story, reel, grupo, perfil,
 * guardado, chat y directo) por once plataformas ([SocialPlatform]). La plataforma pone
 * el rotulo y el color; el formato pone la maqueta. No hay ningun logotipo en el
 * proyecto: la marca se escribe como texto. El aviso de la pantalla recuerda que son
 * imitaciones de aspecto y no sirven para suplantar a nadie.
 *
 * Todo se dibuja sobre un lienzo de [WIDTH] px de ancho y de alto generoso; al final se
 * recorta a la altura que haya ocupado el contenido. Medir primero y dibujar despues
 * exigiria duplicar cada bloque, y recortar un bitmap es inmediato.
 */
object SocialCard {

    const val WIDTH = 1080
    private const val MAX_HEIGHT = 3200
    private const val STORY_HEIGHT = 1920

    fun render(
        post: SocialPost,
        avatar: Bitmap?,
        images: List<Bitmap>,
        typeface: Typeface?,
        brandLogo: Bitmap? = null,
    ): Bitmap {
        val tall = post.format == SocialFormat.STORY || post.format == SocialFormat.REEL
        val height = if (tall) STORY_HEIGHT else MAX_HEIGHT
        val sheet = BitmapUtils.create(WIDTH, height)
        val painter = CardPainter(Canvas(sheet), post, typeface, brandLogo)
        painter.background()

        // Cada red tiene su propia maqueta fiel; si no hay para esa combinacion se usa la generica.
        val skinned = painter.platformLayout(images, avatar)
        val used = skinned ?: when (post.format) {
            SocialFormat.TEXT -> painter.simple(images, avatar, media = false)
            SocialFormat.PHOTO -> painter.simple(images, avatar, media = true)
            SocialFormat.CAROUSEL -> painter.carousel(images, avatar)
            SocialFormat.THREAD -> painter.thread(avatar)
            SocialFormat.VIDEO -> painter.video(images, avatar)
            SocialFormat.STORY -> painter.story(images, avatar, reel = false)
            SocialFormat.REEL -> painter.story(images, avatar, reel = true)
            SocialFormat.GROUP -> painter.group(images, avatar)
            SocialFormat.PROFILE -> painter.profile(images, avatar)
            SocialFormat.SAVED -> painter.saved(images, avatar)
            SocialFormat.CHAT -> painter.chat(avatar)
            SocialFormat.STREAM -> painter.stream(images, avatar)
        }
        if (skinned == null) painter.brandMark()
        if (tall) return sheet
        // Se recorta a lo ocupado y se libera la hoja larga. Se usa el recorte de
        // BitmapUtils para que el resultado sea mutable, como espera el editor.
        val finalHeight = used.toInt().coerceIn(240, height)
        val cropped = BitmapUtils.crop(sheet, Rect(0, 0, WIDTH, finalHeight))
        sheet.recycle()
        return cropped
    }
}

// Margenes del lienzo, a nivel de fichero porque los usa el pincel, no la fachada.
internal const val PAD = 40f
internal const val AVATAR = 108f

/**
 * Pincel con estado: guarda los colores del tema claro u oscuro y va bajando por el
 * lienzo. Cada bloque recibe la y de arriba y devuelve la y de abajo, que es lo que
 * permite montar los doce formatos combinando los mismos bloques.
 */
internal class CardPainter(
    internal val canvas: Canvas,
    internal val post: SocialPost,
    typeface: Typeface?,
    internal val brandLogo: Bitmap?,
) {

    internal val base: Typeface = typeface ?: Typeface.SANS_SERIF
    internal val bold: Typeface = Typeface.create(base, Typeface.BOLD)

    internal val bg = if (post.dark) 0xFF15181C.toInt() else 0xFFFFFFFF.toInt()
    internal val ink = if (post.dark) 0xFFE8EDF2.toInt() else 0xFF111418.toInt()
    internal val muted = if (post.dark) 0xFF8A98A5.toInt() else 0xFF65727E.toInt()
    internal val line = if (post.dark) 0xFF262B31.toInt() else 0xFFE3E8EC.toInt()
    internal val chip = if (post.dark) 0xFF1E2328.toInt() else 0xFFF2F4F6.toInt()

    internal val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    internal val numbers: NumberFormat = NumberFormat.getIntegerInstance()

    internal val width = SocialCard.WIDTH.toFloat()
    internal val content = width - PAD * 2

    fun background() {
        canvas.drawColor(bg)
    }

    // ----------------------------------------------------------------------------------
    // Formatos
    // ----------------------------------------------------------------------------------

    fun simple(images: List<Bitmap>, avatar: Bitmap?, media: Boolean): Float {
        var y = header(PAD, avatar)
        y = body(y)
        if (media) {
            images.firstOrNull()?.let { y = cover(y, it, 1f, inset = true) }
        } else if (post.link.isNotBlank()) {
            y = linkCard(y, images.firstOrNull())
        }
        y = meta(y)
        return stats(y) + PAD
    }

    fun carousel(images: List<Bitmap>, avatar: Bitmap?): Float {
        var y = header(PAD, avatar)
        y = body(y)
        val first = images.firstOrNull()
        if (first != null) {
            val top = y
            y = cover(y, first, 1f, inset = true)
            // Contador y puntos: lo que distingue un carrusel de una foto suelta.
            counter(top + 24f, images.size)
            y = dots(y, images.size)
        }
        y = meta(y)
        return stats(y) + PAD
    }

    fun thread(avatar: Bitmap?): Float {
        var y = header(PAD, avatar)
        y = body(y)
        y = meta(y)
        y = stats(y)
        post.replies.forEach { reply ->
            y = divider(y)
            y = replyBlock(y, reply, avatar)
        }
        return y + PAD
    }

    fun video(images: List<Bitmap>, avatar: Bitmap?): Float {
        var y = header(PAD, avatar)
        y = body(y)
        val thumb = images.firstOrNull()
        if (thumb != null) {
            val top = y
            y = cover(y, thumb, 9f / 16f, inset = true)
            playButton((top + y) / 2f)
        }
        y = meta(y)
        return stats(y) + PAD
    }

    fun story(images: List<Bitmap>, avatar: Bitmap?, reel: Boolean): Float {
        val height = 1920f
        images.firstOrNull()?.let { shot ->
            coverRect(RectF(0f, 0f, width, height), shot, 0f)
        }
        scrim(RectF(0f, 0f, width, 420f), top = true)
        scrim(RectF(0f, height - 620f, width, height), top = false)
        // En las historias el texto va sobre la foto, asi que siempre se escribe en blanco.
        val white = Color.WHITE
        if (!reel) progressBars(36f)
        avatarCircle(PAD, 92f, avatar, ringed = true)
        text(post.displayName, PAD + AVATAR + 24f, 118f, 44f, white, bold = true)
        text(post.datetime, PAD + AVATAR + 24f, 174f, 34f, 0xCCFFFFFF.toInt())
        if (post.verified) {
            badge(PAD + AVATAR + 24f + measure(post.displayName, 44f, true) + 18f, 118f, 44f, white)
        }

        val bodyTop = if (reel) height - 560f else height / 2f - 120f
        val layout = wrap(post.body, 46f, white, bold = !reel, maxWidth = content - if (reel) 120f else 0f)
        canvas.save()
        canvas.translate(PAD, bodyTop)
        layout.draw(canvas)
        canvas.restore()
        var y = bodyTop + layout.height + 28f
        if (post.location.isNotBlank()) {
            pill(PAD, y, post.location, white)
            y += 92f
        }
        if (reel) {
            // El reel lleva las cifras en vertical a la derecha, como los videos cortos.
            verticalStats(height - 760f)
        } else if (post.views > 0) {
            text(numbers.format(post.views), PAD, height - 60f, 36f, 0xCCFFFFFF.toInt())
        }
        return height
    }

    fun group(images: List<Bitmap>, avatar: Bitmap?): Float {
        // Cabecera del grupo: el nombre va en el campo de ubicacion, que es el que sobra.
        val groupName = post.location.ifBlank { post.displayName }
        fill.color = chip
        canvas.drawRect(0f, 0f, width, 150f, fill)
        avatarCircle(PAD, 24f, images.getOrNull(1) ?: avatar, ringed = false, size = 100f)
        text(groupName, PAD + 124f, 74f, 42f, ink, bold = true)
        text(post.handle, PAD + 124f, 124f, 34f, muted)
        var y = header(174f, avatar, withHandle = false)
        y = body(y)
        images.firstOrNull()?.let { y = cover(y, it, 4f / 5f, inset = true) }
        y = meta(y, skipLocation = true)
        y = stats(y)
        post.replies.forEach { comment ->
            y = commentRow(y, comment)
        }
        return y + PAD
    }

    fun profile(images: List<Bitmap>, avatar: Bitmap?): Float {
        val bannerHeight = 300f
        val banner = images.firstOrNull()
        if (banner != null) {
            coverRect(RectF(0f, 0f, width, bannerHeight), banner, 0f)
        } else {
            fill.color = post.accent
            canvas.drawRect(0f, 0f, width, bannerHeight, fill)
        }
        avatarCircle(PAD, bannerHeight - 90f, avatar, ringed = true, size = 200f)
        var y = bannerHeight + 130f
        text(post.displayName, PAD, y, 54f, ink, bold = true)
        if (post.verified) {
            badge(PAD + measure(post.displayName, 54f, true) + 20f, y, 54f, post.accent)
        }
        y += 56f
        y = wrapAt(post.body, y, 40f, ink)
        if (post.location.isNotBlank()) {
            text(post.location, PAD, y + 36f, 36f, muted)
            y += 60f
        }
        if (post.link.isNotBlank()) {
            text(post.link, PAD, y + 36f, 36f, post.accent)
            y += 60f
        }
        y += 24f
        y = divider(y)
        // En un perfil las cifras son seguidores y publicaciones, no me gusta y compartir.
        val pairs = listOf(post.likes, post.comments, post.views).filter { it > 0 }
        if (pairs.isNotEmpty()) {
            var x = PAD
            pairs.forEach { value ->
                text(numbers.format(value), x, y + 54f, 42f, ink, bold = true)
                x += 260f
            }
            y += 90f
        }
        return y + PAD
    }

    fun saved(images: List<Bitmap>, avatar: Bitmap?): Float {
        fill.color = chip
        canvas.drawRect(0f, 0f, width, 110f, fill)
        bookmarkIcon(PAD + 18f, 36f, 40f, post.accent, filled = true)
        text(post.datetime, PAD + 90f, 72f, 36f, muted)
        var y = header(140f, avatar)
        y = body(y)
        images.firstOrNull()?.let { y = cover(y, it, 1f, inset = true) }
        if (post.link.isNotBlank()) y = linkCard(y, null)
        y = meta(y)
        return stats(y) + PAD
    }

    fun chat(avatar: Bitmap?): Float {
        fill.color = chip
        canvas.drawRect(0f, 0f, width, 160f, fill)
        avatarCircle(PAD, 26f, avatar, ringed = false, size = 108f)
        text(post.displayName, PAD + 132f, 80f, 44f, ink, bold = true)
        text(post.handle, PAD + 132f, 130f, 34f, muted)
        if (post.verified) {
            badge(PAD + 132f + measure(post.displayName, 44f, true) + 18f, 80f, 44f, post.accent)
        }
        var y = 200f
        val messages = buildList {
            if (post.body.isNotBlank()) add(SocialComment(post.displayName, post.body))
            addAll(post.replies)
        }
        messages.forEach { message ->
            y = bubble(y, message)
        }
        if (post.datetime.isNotBlank()) {
            val paint = textPaint(32f, false, muted)
            canvas.drawText(
                post.datetime,
                (width - paint.measureText(post.datetime)) / 2f,
                y + 48f,
                paint,
            )
            y += 70f
        }
        return y + PAD
    }

    fun stream(images: List<Bitmap>, avatar: Bitmap?): Float {
        val shot = images.firstOrNull()
        var y = 0f
        if (shot != null) {
            y = cover(0f, shot, 9f / 16f, inset = false)
            liveBadge(PAD, PAD)
            if (post.views > 0) {
                pill(width - 300f, PAD, numbers.format(post.views), Color.WHITE, dark = true)
            }
        } else {
            fill.color = chip
            canvas.drawRect(0f, 0f, width, 608f, fill)
            liveBadge(PAD, PAD)
            y = 608f
        }
        y += 28f
        y = wrapAt(post.body, y, 46f, ink, bold = true)
        y = header(y + 16f, avatar)
        y = meta(y)
        return stats(y) + PAD
    }

    // ----------------------------------------------------------------------------------
    // Bloques
    // ----------------------------------------------------------------------------------

    internal fun header(top: Float, avatar: Bitmap?, withHandle: Boolean = true): Float {
        avatarCircle(PAD, top, avatar, ringed = false)
        val textX = PAD + AVATAR + 24f
        text(post.displayName, textX, top + 46f, 44f, ink, bold = true)
        if (post.verified) {
            badge(textX + measure(post.displayName, 44f, true) + 18f, top + 46f, 44f, post.accent)
        }
        val second = listOfNotNull(
            post.handle.takeIf { withHandle && it.isNotBlank() },
            post.datetime.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ")
        if (second.isNotBlank()) text(second, textX, top + 102f, 36f, muted)
        return top + AVATAR + 32f
    }

    internal fun body(top: Float): Float {
        if (post.body.isBlank()) return top
        return wrapAt(post.body, top, 42f, ink) + 12f
    }

    internal fun meta(top: Float, skipLocation: Boolean = false): Float {
        val parts = listOfNotNull(
            post.location.takeIf { !skipLocation && it.isNotBlank() },
            post.link.takeIf { it.isNotBlank() && post.format != SocialFormat.PROFILE },
        )
        if (parts.isEmpty()) return top
        var y = top
        parts.forEach { part ->
            val isLink = part == post.link
            text(part, PAD, y + 40f, 36f, if (isLink) post.accent else muted)
            y += 54f
        }
        return y + 8f
    }

    /** Fila de cifras. Cada icono se dibuja por codigo, no hay recursos de imagen. */
    internal fun stats(top: Float): Float {
        val entries = buildList {
            if (post.likes > 0) add(StatKind.LIKE to post.likes)
            if (post.comments > 0) add(StatKind.COMMENT to post.comments)
            if (post.shares > 0) add(StatKind.SHARE to post.shares)
            if (post.saves > 0) add(StatKind.SAVE to post.saves)
            if (post.views > 0) add(StatKind.VIEW to post.views)
        }
        if (entries.isEmpty()) return top
        val y = divider(top) + 24f
        var x = PAD
        val paint = textPaint(36f, false, muted)
        entries.forEach { (kind, value) ->
            statIcon(kind, x, y, 44f, muted)
            val label = numbers.format(value)
            canvas.drawText(label, x + 58f, y + 34f, paint)
            x += 58f + paint.measureText(label) + 56f
        }
        return y + 78f
    }

    internal fun divider(top: Float): Float {
        stroke.color = line
        stroke.strokeWidth = 2f
        canvas.drawLine(PAD, top + 12f, width - PAD, top + 12f, stroke)
        return top + 14f
    }

    internal fun replyBlock(top: Float, reply: SocialComment, avatar: Bitmap?): Float {
        avatarCircle(PAD, top + 16f, avatar, ringed = false, size = 80f)
        val textX = PAD + 104f
        text(reply.author.ifBlank { post.displayName }, textX, top + 58f, 40f, ink, bold = true)
        var y = wrapAt(reply.text, top + 74f, 40f, ink, x = textX, maxWidth = content - 104f)
        if (reply.likes > 0) {
            statIcon(StatKind.LIKE, textX, y + 8f, 36f, muted)
            text(numbers.format(reply.likes), textX + 48f, y + 38f, 32f, muted)
            y += 60f
        }
        return y + 12f
    }

    internal fun commentRow(top: Float, comment: SocialComment): Float {
        val initial = comment.author.trim().take(1).uppercase().ifBlank { "·" }
        fill.color = chip
        canvas.drawCircle(PAD + 36f, top + 48f, 36f, fill)
        val initialPaint = textPaint(36f, true, muted)
        canvas.drawText(
            initial,
            PAD + 36f - initialPaint.measureText(initial) / 2f,
            top + 60f,
            initialPaint,
        )
        val textX = PAD + 92f
        fill.color = chip
        val bubbleBottom = measureWrap(comment.text, 36f, content - 92f - PAD).toFloat()
        canvas.drawRoundRect(
            RectF(textX, top + 8f, width - PAD, top + 76f + bubbleBottom),
            28f,
            28f,
            fill,
        )
        text(comment.author, textX + 24f, top + 52f, 34f, ink, bold = true)
        val y = wrapAt(
            comment.text,
            top + 62f,
            36f,
            ink,
            x = textX + 24f,
            maxWidth = content - 92f - PAD - 48f,
        )
        return y + 24f
    }

    internal fun bubble(top: Float, message: SocialComment): Float {
        val maxWidth = content * 0.78f
        val height = measureWrap(message.text, 40f, maxWidth).toFloat()
        val textWidth = min(maxWidth, measure(message.text, 40f, false) + 8f)
        val boxWidth = textWidth + 56f
        val left = if (message.mine) width - PAD - boxWidth else PAD
        fill.color = if (message.mine) post.accent else chip
        canvas.drawRoundRect(
            RectF(left, top, left + boxWidth, top + height + 44f),
            36f,
            36f,
            fill,
        )
        val color = if (message.mine) Color.WHITE else ink
        wrapAt(message.text, top + 10f, 40f, color, x = left + 28f, maxWidth = maxWidth)
        return top + height + 60f
    }

    internal fun linkCard(top: Float, image: Bitmap?): Float {
        val box = RectF(PAD, top + 8f, width - PAD, top + 8f)
        var bottom = box.top
        if (image != null) {
            bottom = coverRect(
                RectF(PAD, box.top, width - PAD, box.top + content * 0.52f),
                image,
                24f,
                topOnly = true,
            )
        }
        val textTop = bottom
        fill.color = chip
        canvas.drawRoundRect(
            RectF(PAD, textTop, width - PAD, textTop + 150f),
            if (image == null) 24f else 0f,
            if (image == null) 24f else 0f,
            fill,
        )
        val domain = post.link.removePrefix("https://").removePrefix("http://").substringBefore('/')
        text(domain, PAD + 28f, textTop + 56f, 34f, muted)
        text(
            post.displayName.ifBlank { domain },
            PAD + 28f,
            textTop + 112f,
            40f,
            ink,
            bold = true,
        )
        stroke.color = line
        stroke.strokeWidth = 2f
        canvas.drawRoundRect(
            RectF(PAD, box.top, width - PAD, textTop + 150f),
            24f,
            24f,
            stroke,
        )
        return textTop + 174f
    }

    // ----------------------------------------------------------------------------------
    // Piezas sueltas
    // ----------------------------------------------------------------------------------

    internal fun avatarCircle(
        x: Float,
        y: Float,
        avatar: Bitmap?,
        ringed: Boolean,
        size: Float = AVATAR,
    ) {
        val rect = RectF(x, y, x + size, y + size)
        if (avatar != null) {
            coverRect(rect, avatar, size / 2f)
        } else {
            fill.color = chip
            canvas.drawOval(rect, fill)
            // Silueta generica cuando no hay foto: circulo y hombros.
            fill.color = muted
            canvas.drawCircle(rect.centerX(), rect.top + size * 0.38f, size * 0.17f, fill)
            canvas.drawRoundRect(
                RectF(
                    rect.centerX() - size * 0.27f,
                    rect.top + size * 0.58f,
                    rect.centerX() + size * 0.27f,
                    rect.bottom - size * 0.04f,
                ),
                size * 0.27f,
                size * 0.27f,
                fill,
            )
        }
        if (ringed) {
            stroke.color = Color.WHITE
            stroke.strokeWidth = 6f
            canvas.drawOval(rect, stroke)
        }
    }

    /**
     * Requisito del usuario: la insignia de verificado quedaba descolocada (cada sitio
     * donde se usaba le pasaba un desplazamiento vertical a ojo). Ahora se centra sola en
     * el alto real del texto de al lado, midiendo con [Paint.getTextBounds] en vez de un
     * numero fijo que solo cuadraba por casualidad con un tamano de letra concreto.
     */
    internal fun badge(x: Float, baseline: Float, textSize: Float, color: Int) {
        val size = 46f
        val bounds = Rect()
        textPaint(textSize, true, color).getTextBounds("A", 0, 1, bounds)
        val textCenterY = baseline + (bounds.top + bounds.bottom) / 2f
        val y = textCenterY - size / 2f
        fill.color = color
        val star = PathShapes.polygon(size, size, 10)
        star.offset(x, y)
        canvas.drawPath(star, fill)
        stroke.color = Color.WHITE
        stroke.strokeWidth = 5f
        stroke.strokeCap = Paint.Cap.ROUND
        val check = Path().apply {
            moveTo(x + size * 0.28f, y + size * 0.52f)
            lineTo(x + size * 0.45f, y + size * 0.68f)
            lineTo(x + size * 0.74f, y + size * 0.34f)
        }
        canvas.drawPath(check, stroke)
        // El trazo es compartido: se deja el extremo como estaba para las lineas finas.
        stroke.strokeCap = Paint.Cap.BUTT
    }

    internal fun playButton(centerY: Float) {
        fill.color = 0x99000000.toInt()
        canvas.drawCircle(width / 2f, centerY, 86f, fill)
        fill.color = Color.WHITE
        val play = Path().apply {
            moveTo(width / 2f - 26f, centerY - 42f)
            lineTo(width / 2f + 46f, centerY)
            lineTo(width / 2f - 26f, centerY + 42f)
            close()
        }
        canvas.drawPath(play, fill)
    }

    internal fun counter(top: Float, total: Int) {
        if (total < 2) return
        val label = "1/$total"
        val paint = textPaint(34f, true, Color.WHITE)
        val textWidth = paint.measureText(label)
        fill.color = 0x99000000.toInt()
        canvas.drawRoundRect(
            RectF(width - PAD - textWidth - 56f, top, width - PAD - 20f, top + 62f),
            31f,
            31f,
            fill,
        )
        canvas.drawText(label, width - PAD - textWidth - 38f, top + 42f, paint)
    }

    internal fun dots(top: Float, total: Int): Float {
        if (total < 2) return top
        val count = min(total, 8)
        val gap = 34f
        var x = width / 2f - (count - 1) * gap / 2f
        repeat(count) { index ->
            fill.color = if (index == 0) post.accent else line
            canvas.drawCircle(x, top + 34f, if (index == 0) 11f else 9f, fill)
            x += gap
        }
        return top + 70f
    }

    internal fun progressBars(top: Float) {
        val segments = 4
        val gap = 10f
        val segmentWidth = (width - PAD * 2 - gap * (segments - 1)) / segments
        var x = PAD
        repeat(segments) { index ->
            fill.color = if (index == 0) Color.WHITE else 0x66FFFFFF
            canvas.drawRoundRect(RectF(x, top, x + segmentWidth, top + 8f), 4f, 4f, fill)
            x += segmentWidth + gap
        }
    }

    internal fun verticalStats(top: Float) {
        val entries = buildList {
            if (post.likes > 0) add(StatKind.LIKE to post.likes)
            if (post.comments > 0) add(StatKind.COMMENT to post.comments)
            if (post.shares > 0) add(StatKind.SHARE to post.shares)
            if (post.saves > 0) add(StatKind.SAVE to post.saves)
        }
        var y = top
        val paint = textPaint(32f, false, Color.WHITE)
        entries.forEach { (kind, value) ->
            statIcon(kind, width - 110f, y, 58f, Color.WHITE)
            val label = numbers.format(value)
            canvas.drawText(label, width - 110f + (58f - paint.measureText(label)) / 2f, y + 96f, paint)
            y += 150f
        }
    }

    internal fun liveBadge(x: Float, y: Float) {
        val paint = textPaint(34f, true, Color.WHITE)
        val textWidth = paint.measureText(post.liveLabel)
        fill.color = 0xFFE01B24.toInt()
        canvas.drawRoundRect(RectF(x, y, x + textWidth + 86f, y + 62f), 14f, 14f, fill)
        fill.color = Color.WHITE
        canvas.drawCircle(x + 32f, y + 31f, 11f, fill)
        canvas.drawText(post.liveLabel, x + 56f, y + 43f, paint)
    }

    /**
     * Rotulo de la plataforma, arriba a la derecha. Va el ultimo de todo para que ninguna
     * foto a sangre lo tape, y lleva su propia chapa porque debajo puede haber cualquier
     * cosa. El texto se pone blanco o negro segun el brillo del color de marca, que es lo
     * unico que garantiza que se lea tanto con el negro de X como con el amarillo que el
     * usuario elija a mano.
     */
    /**
     * Requisito del usuario: el logotipo real de la red, no una chapa de color con el
     * nombre escrito. [brandLogo] llega ya rasterizado en blanco desde la UI (el render de
     * aqui no depende de Context/Resources). Si no llega (fallo al cargarlo), se cae a la
     * chapa de texto de antes para no dejar el hueco vacio.
     */
    fun brandMark() {
        val logo = brandLogo
        if (logo == null) {
            brandMarkFallback()
            return
        }
        val size = 84f
        val cx = width - PAD - size / 2f
        val cy = PAD + size / 2f
        fill.color = post.accent
        canvas.drawCircle(cx, cy, size / 2f, fill)
        // Un borde suave despega la chapa del fondo cuando ambos son casi del mismo color
        // (el negro de X o de Threads sobre el tema oscuro).
        stroke.color = 0x33FFFFFF
        stroke.strokeWidth = 2f
        canvas.drawCircle(cx, cy, size / 2f, stroke)
        val logoSize = size * 0.5f
        val dst = RectF(cx - logoSize / 2f, cy - logoSize / 2f, cx + logoSize / 2f, cy + logoSize / 2f)
        canvas.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    internal fun brandMarkFallback() {
        val label = post.platform.brand
        val paint = textPaint(36f, true, Color.WHITE)
        val textWidth = paint.measureText(label)
        val boxWidth = textWidth + 52f
        val left = width - PAD - boxWidth
        val box = RectF(left, PAD, left + boxWidth, PAD + 62f)
        fill.color = post.accent
        canvas.drawRoundRect(box, 31f, 31f, fill)
        stroke.color = 0x33FFFFFF
        stroke.strokeWidth = 2f
        canvas.drawRoundRect(box, 31f, 31f, stroke)
        paint.color = if (luminance(post.accent) > 0.6f) 0xFF101010.toInt() else Color.WHITE
        canvas.drawText(label, left + 26f, PAD + 43f, paint)
    }

    /** Brillo percibido 0..1, para decidir si encima va texto blanco o negro. */
    internal fun luminance(color: Int): Float =
        (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) /
            255f

    internal fun pill(x: Float, y: Float, label: String, color: Int, dark: Boolean = true) {
        val paint = textPaint(34f, false, color)
        val textWidth = paint.measureText(label)
        fill.color = if (dark) 0x66000000 else chip
        canvas.drawRoundRect(RectF(x, y, x + textWidth + 56f, y + 62f), 31f, 31f, fill)
        canvas.drawText(label, x + 28f, y + 43f, paint)
    }

    internal fun scrim(rect: RectF, top: Boolean) {
        val from = if (top) 0xB3000000.toInt() else Color.TRANSPARENT
        val to = if (top) Color.TRANSPARENT else 0xCC000000.toInt()
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, from, to, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, fill)
        fill.shader = null
    }

    internal enum class StatKind { LIKE, COMMENT, SHARE, SAVE, VIEW }

    internal fun statIcon(kind: StatKind, x: Float, y: Float, size: Float, color: Int) {
        when (kind) {
            StatKind.LIKE -> {
                fill.color = color
                val heart = PathShapes.heart(size, size)
                heart.offset(x, y)
                canvas.drawPath(heart, fill)
            }
            StatKind.COMMENT -> {
                fill.color = color
                val bubbleShape = PathShapes.cloudPath(PathShapes.CloudShape.BUBBLE, size, size)
                bubbleShape.offset(x, y)
                canvas.drawPath(bubbleShape, fill)
            }
            StatKind.SHARE -> {
                fill.color = color
                val arrow = PathShapes.arrow(size, size * 0.8f)
                arrow.offset(x, y + size * 0.1f)
                canvas.drawPath(arrow, fill)
            }
            StatKind.SAVE -> bookmarkIcon(x + size * 0.2f, y, size, color, filled = true)
            StatKind.VIEW -> {
                stroke.color = color
                stroke.strokeWidth = size * 0.1f
                canvas.drawOval(
                    RectF(x, y + size * 0.22f, x + size, y + size * 0.78f),
                    stroke,
                )
                fill.color = color
                canvas.drawCircle(x + size / 2f, y + size / 2f, size * 0.16f, fill)
            }
        }
    }

    internal fun bookmarkIcon(x: Float, y: Float, size: Float, color: Int, filled: Boolean) {
        val path = Path().apply {
            moveTo(x + size * 0.2f, y + size * 0.06f)
            lineTo(x + size * 0.8f, y + size * 0.06f)
            lineTo(x + size * 0.8f, y + size * 0.94f)
            lineTo(x + size * 0.5f, y + size * 0.66f)
            lineTo(x + size * 0.2f, y + size * 0.94f)
            close()
        }
        if (filled) {
            fill.color = color
            canvas.drawPath(path, fill)
        } else {
            stroke.color = color
            stroke.strokeWidth = size * 0.1f
            canvas.drawPath(path, stroke)
        }
    }

    // ----------------------------------------------------------------------------------
    // Texto e imagenes
    // ----------------------------------------------------------------------------------

    internal fun textPaint(size: Float, isBold: Boolean, color: Int): TextPaint = TextPaint(
        Paint.ANTI_ALIAS_FLAG,
    ).apply {
        typeface = if (isBold) bold else base
        textSize = size
        this.color = color
    }

    internal fun text(
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) {
        if (value.isBlank()) return
        canvas.drawText(value, x, baseline, textPaint(size, bold, color))
    }

    internal fun measure(value: String, size: Float, isBold: Boolean): Float =
        textPaint(size, isBold, ink).measureText(value)

    internal fun wrap(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        maxWidth: Float = content,
    ): StaticLayout {
        val paint = textPaint(size, bold, color)
        val safeWidth = max(1, maxWidth.toInt())
        return StaticLayout.Builder
            .obtain(value, 0, value.length, paint, safeWidth)
            .setLineSpacing(0f, 1.18f)
            .setIncludePad(false)
            .build()
    }

    /** Escribe un texto con salto de linea y devuelve la y de debajo. */
    internal fun wrapAt(
        value: String,
        top: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        x: Float = PAD,
        maxWidth: Float = content,
    ): Float {
        if (value.isBlank()) return top
        val layout = wrap(value, size, color, bold, maxWidth)
        canvas.save()
        canvas.translate(x, top + 8f)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height + 16f
    }

    internal fun measureWrap(value: String, size: Float, maxWidth: Float): Int =
        if (value.isBlank()) 0 else wrap(value, size, ink, false, maxWidth).height

    /** Imagen recortada al centro dentro de un rectangulo, con esquinas redondeadas. */
    internal fun coverRect(
        rect: RectF,
        bitmap: Bitmap,
        radius: Float,
        topOnly: Boolean = false,
    ): Float {
        val scale = max(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            // Centrado: el sobrante se reparte a los dos lados.
            postTranslate(
                rect.left + (rect.width() - bitmap.width * scale) / 2f,
                rect.top + (rect.height() - bitmap.height * scale) / 2f,
            )
        }
        fill.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }
        if (radius > 0f) {
            canvas.drawRoundRect(rect, radius, radius, fill)
            if (topOnly) {
                // Esquinas de abajo cuadradas: debajo va la ficha de texto del enlace.
                canvas.drawRect(rect.left, rect.bottom - radius, rect.right, rect.bottom, fill)
            }
        } else {
            canvas.drawRect(rect, fill)
        }
        fill.shader = null
        return rect.bottom
    }

    /** Imagen a lo ancho del lienzo con la proporcion pedida; devuelve la y de debajo. */
    internal fun cover(top: Float, bitmap: Bitmap, aspect: Float, inset: Boolean): Float {
        val left = if (inset) PAD else 0f
        val right = width - left
        val height = (right - left) * aspect
        val rect = RectF(left, top + 8f, right, top + 8f + height)
        coverRect(rect, bitmap, if (inset) 28f else 0f)
        return rect.bottom + 16f
    }
}
