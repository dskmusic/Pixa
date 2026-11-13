package com.dskmusic.pixa.core.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/*
 * Maquetas fieles por red (requisito del usuario: uso personal, que se parezcan a la app
 * de verdad y no a una plantilla generica; mismo enfoque que PostSim). Cada funcion dibuja
 * la interfaz real de la red: cabecera, iconos, barra de acciones, colores y modo oscuro.
 * Devuelven la y final (o el alto del lienzo en los formatos verticales).
 * [platformLayout] devuelve null cuando no hay maqueta propia y se usa la generica.
 */

private class Skin(val bg: Int, val ink: Int, val muted: Int, val line: Int, val chip: Int)

private fun skin(dark: Boolean, light: List<Long>, night: List<Long>): Skin {
    val c = if (dark) night else light
    return Skin(c[0].toInt(), c[1].toInt(), c[2].toInt(), c[3].toInt(), c[4].toInt())
}

// bg, ink, muted, line, chip
private val X_LIGHT = listOf(0xFFFFFFFF, 0xFF0F1419, 0xFF536471, 0xFFEFF3F4, 0xFFF7F9F9)
private val X_DARK = listOf(0xFF000000, 0xFFE7E9EA, 0xFF71767B, 0xFF2F3336, 0xFF16181C)
private val IG_LIGHT = listOf(0xFFFFFFFF, 0xFF000000, 0xFF737373, 0xFFDBDBDB, 0xFFEFEFEF)
private val IG_DARK = listOf(0xFF000000, 0xFFF5F5F5, 0xFFA8A8A8, 0xFF262626, 0xFF262626)
private val FB_LIGHT = listOf(0xFFFFFFFF, 0xFF050505, 0xFF65676B, 0xFFCED0D4, 0xFFF0F2F5)
private val FB_DARK = listOf(0xFF242526, 0xFFE4E6EB, 0xFFB0B3B8, 0xFF3E4042, 0xFF3A3B3C)
private val YT_LIGHT = listOf(0xFFFFFFFF, 0xFF0F0F0F, 0xFF606060, 0xFFE5E5E5, 0xFFF2F2F2)
private val YT_DARK = listOf(0xFF0F0F0F, 0xFFF1F1F1, 0xFFAAAAAA, 0xFF3F3F3F, 0xFF272727)
private val TH_LIGHT = listOf(0xFFFFFFFF, 0xFF000000, 0xFF999999, 0xFFE0E0E0, 0xFFF3F3F3)
private val TH_DARK = listOf(0xFF101010, 0xFFF3F5F7, 0xFF777777, 0xFF2A2A2A, 0xFF1E1E1E)
private val RD_LIGHT = listOf(0xFFFFFFFF, 0xFF1C1C1C, 0xFF576F76, 0xFFE5EBED, 0xFFEAEDEF)
private val RD_DARK = listOf(0xFF0B1416, 0xFFD7DADC, 0xFF8BA2AD, 0xFF2A3C42, 0xFF1A282D)
private val LI_LIGHT = listOf(0xFFFFFFFF, 0xFF191919, 0xFF666666, 0xFFE0DFDC, 0xFFEDF3F8)
private val LI_DARK = listOf(0xFF1B1F23, 0xFFE8E8E8, 0xFF9DA6AD, 0xFF38434F, 0xFF2D3640)
private val PI_LIGHT = listOf(0xFFFFFFFF, 0xFF111111, 0xFF767676, 0xFFE9E9E9, 0xFFEFEFEF)
private val PI_DARK = listOf(0xFF121212, 0xFFFFFFFF, 0xFFB0B0B0, 0xFF333333, 0xFF2B2B2B)
private val TW_LIGHT = listOf(0xFFFFFFFF, 0xFF0E0E10, 0xFF53535F, 0xFFE5E5E5, 0xFFF7F7F8)
private val TW_DARK = listOf(0xFF18181B, 0xFFEFEFF1, 0xFFADADB8, 0xFF2F2F35, 0xFF26262C)

private const val X_BLUE = 0xFF1D9BF0.toInt()
private const val IG_BLUE = 0xFF0095F6.toInt()
private const val FB_BLUE = 0xFF1877F2.toInt()
private const val LI_BLUE = 0xFF0A66C2.toInt()
private const val TT_RED = 0xFFFE2C55.toInt()

/** Despacha a la maqueta propia de la plataforma y el formato; null = usar la generica. */
internal fun CardPainter.platformLayout(images: List<Bitmap>, avatar: Bitmap?): Float? {
    val f = post.format
    return when (post.platform) {
        SocialPlatform.X, SocialPlatform.TWITTER ->
            if (f == SocialFormat.TEXT || f == SocialFormat.PHOTO || f == SocialFormat.VIDEO ||
                f == SocialFormat.THREAD
            ) xLayout(images, avatar)
            else if (f == SocialFormat.PROFILE) xProfileLayout(images, avatar) else null
        SocialPlatform.INSTAGRAM ->
            when (f) {
                SocialFormat.PHOTO, SocialFormat.CAROUSEL -> igLayout(images, avatar)
                SocialFormat.PROFILE -> igProfileLayout(images, avatar)
                SocialFormat.REEL -> reelsLayout(images, avatar)
                else -> null
            }
        SocialPlatform.FACEBOOK ->
            if (f == SocialFormat.TEXT || f == SocialFormat.PHOTO || f == SocialFormat.CAROUSEL ||
                f == SocialFormat.VIDEO
            ) fbLayout(images, avatar) else null
        SocialPlatform.YOUTUBE -> when (f) {
            SocialFormat.VIDEO -> ytLayout(images, avatar)
            SocialFormat.REEL -> shortsLayout(images, avatar)
            else -> null
        }
        SocialPlatform.TIKTOK ->
            if (f == SocialFormat.REEL || f == SocialFormat.STORY) tiktokLayout(images, avatar) else null
        SocialPlatform.WHATSAPP ->
            if (f == SocialFormat.CHAT || f == SocialFormat.GROUP) waLayout(avatar) else null
        SocialPlatform.THREADS ->
            if (f == SocialFormat.TEXT || f == SocialFormat.PHOTO || f == SocialFormat.THREAD ||
                f == SocialFormat.CAROUSEL
            ) threadsLayout(images, avatar) else null
        SocialPlatform.REDDIT ->
            if (f == SocialFormat.TEXT || f == SocialFormat.PHOTO || f == SocialFormat.THREAD ||
                f == SocialFormat.VIDEO
            ) redditLayout(images, avatar) else null
        SocialPlatform.LINKEDIN ->
            if (f == SocialFormat.TEXT || f == SocialFormat.PHOTO || f == SocialFormat.CAROUSEL ||
                f == SocialFormat.VIDEO
            ) linkedinLayout(images, avatar) else null
        SocialPlatform.PINTEREST ->
            if (f == SocialFormat.PHOTO) pinterestLayout(images, avatar) else null
        SocialPlatform.TWITCH -> when (f) {
            SocialFormat.CHAT -> twitchChatLayout()
            SocialFormat.STREAM -> twitchStreamLayout(images, avatar)
            else -> null
        }
    }
}

// ======================================================================================
// Utilidades
// ======================================================================================

/** 1.234 hasta 9.999; 12,3K y 1,2M despues, como las abrevian las apps. */
internal fun abbr(n: Int): String {
    fun trim(v: Double) = String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    return when {
        n >= 1_000_000 -> trim(n / 1_000_000.0) + "M"
        n >= 10_000 -> trim(n / 1_000.0) + "K"
        else -> String.format(Locale.US, "%,d", n)
    }
}

private val TOKEN = Regex("[#@][\\p{L}\\p{N}_]+|https?://\\S+")

/**
 * Texto con #etiquetas, @menciones y enlaces coloreados, como en las apps. [boldLead]
 * pone en negrita los primeros caracteres (el usuario delante del pie, en Instagram).
 */
private fun CardPainter.richLayout(
    value: String,
    size: Float,
    color: Int,
    link: Int,
    maxWidth: Float,
    bold: Boolean = false,
    boldLead: Int = 0,
    boldTokens: Boolean = false,
): StaticLayout {
    val sp = SpannableStringBuilder(value)
    TOKEN.findAll(value).forEach {
        val range = it.range
        sp.setSpan(ForegroundColorSpan(link), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (boldTokens) {
            sp.setSpan(StyleSpan(Typeface.BOLD), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    if (boldLead > 0) {
        sp.setSpan(StyleSpan(Typeface.BOLD), 0, boldLead.coerceAtMost(sp.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return StaticLayout.Builder
        .obtain(sp, 0, sp.length, textPaint(size, bold, color), max(1, maxWidth.toInt()))
        .setLineSpacing(0f, 1.18f)
        .setIncludePad(false)
        .build()
}

private fun CardPainter.drawLayout(layout: StaticLayout, x: Float, top: Float) {
    canvas.save()
    canvas.translate(x, top)
    layout.draw(canvas)
    canvas.restore()
}

/** Escribe texto rico y devuelve la y de debajo. */
private fun CardPainter.rich(
    value: String,
    top: Float,
    size: Float,
    color: Int,
    link: Int,
    x: Float = PAD,
    maxWidth: Float = content,
    bold: Boolean = false,
    boldLead: Int = 0,
    boldTokens: Boolean = false,
): Float {
    if (value.isBlank()) return top
    val layout = richLayout(value, size, color, link, maxWidth, bold, boldLead, boldTokens)
    drawLayout(layout, x, top + 8f)
    return top + layout.height + 16f
}

private fun StaticLayout.widest(): Float =
    (0 until lineCount).maxOfOrNull { getLineWidth(it) } ?: 0f

private fun CardPainter.user(): String = post.handle.removePrefix("@").ifBlank { post.displayName }

/** Avatar con inicial para quien responde (solo hay una foto de perfil cargada). */
private fun CardPainter.letterAvatar(x: Float, y: Float, size: Float, name: String) {
    val hue = (abs(name.hashCode()) % 360).toFloat()
    fill.color = Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.78f))
    canvas.drawCircle(x + size / 2f, y + size / 2f, size / 2f, fill)
    val paint = textPaint(size * 0.46f, true, Color.WHITE)
    val letter = name.trim().take(1).uppercase().ifBlank { "?" }
    canvas.drawText(letter, x + size / 2f - paint.measureText(letter) / 2f, y + size * 0.65f, paint)
}

private fun CardPainter.who(avatar: Bitmap?, x: Float, y: Float, size: Float, author: String) {
    if (author.isBlank() || author == post.displayName) {
        avatarCircle(x, y, avatar, false, size)
    } else {
        letterAvatar(x, y, size, author)
    }
}

private fun CardPainter.clockText(): String =
    Regex("\\d{1,2}:\\d{2}(\\s?[AaPp]\\.?[Mm]\\.?)?").find(post.datetime)?.value.orEmpty()

private fun CardPainter.hline(top: Float, color: Int, x0: Float = 0f, x1: Float = width): Float {
    stroke.color = color
    stroke.strokeWidth = 2f
    canvas.drawLine(x0, top, x1, top, stroke)
    return top
}

private fun CardPainter.pillText(
    x: Float,
    y: Float,
    h: Float,
    label: String,
    size: Float,
    bgColor: Int,
    fg: Int,
    bold: Boolean = true,
    padX: Float = 36f,
): Float {
    val paint = textPaint(size, bold, fg)
    val w = paint.measureText(label) + padX * 2
    fill.color = bgColor
    canvas.drawRoundRect(RectF(x, y, x + w, y + h), h / 2f, h / 2f, fill)
    canvas.drawText(label, x + padX, y + h / 2f + size * 0.34f, paint)
    return x + w
}

// ======================================================================================
// Iconos de linea (se dibujan por codigo: no hay recursos de imagen)
// ======================================================================================

internal enum class Ic {
    HEART, COMMENT, REPOST, SHARE, BOOKMARK, SEND, THUMB, CHART, DOTS, UP, DOWN, GLOBE,
    VIDEOCAM, BACK, MIC, CHECKS, NOTE, PLUS, CLOSE,
}

private fun unitPath(kind: Ic): Path = Path().apply {
    when (kind) {
        Ic.REPOST -> {
            moveTo(0.12f, 0.44f); lineTo(0.12f, 0.32f); quadTo(0.12f, 0.2f, 0.24f, 0.2f); lineTo(0.8f, 0.2f)
            moveTo(0.66f, 0.06f); lineTo(0.82f, 0.2f); lineTo(0.66f, 0.34f)
            moveTo(0.88f, 0.56f); lineTo(0.88f, 0.68f); quadTo(0.88f, 0.8f, 0.76f, 0.8f); lineTo(0.2f, 0.8f)
            moveTo(0.34f, 0.66f); lineTo(0.18f, 0.8f); lineTo(0.34f, 0.94f)
        }
        Ic.SHARE -> {
            moveTo(0.3f, 0.4f); lineTo(0.2f, 0.4f); lineTo(0.2f, 0.9f); lineTo(0.8f, 0.9f); lineTo(0.8f, 0.4f); lineTo(0.7f, 0.4f)
            moveTo(0.5f, 0.66f); lineTo(0.5f, 0.1f)
            moveTo(0.32f, 0.28f); lineTo(0.5f, 0.1f); lineTo(0.68f, 0.28f)
        }
        Ic.SEND -> {
            moveTo(0.08f, 0.46f); lineTo(0.94f, 0.1f); lineTo(0.72f, 0.9f); lineTo(0.5f, 0.6f); close()
            moveTo(0.94f, 0.1f); lineTo(0.5f, 0.6f)
        }
        Ic.THUMB -> {
            moveTo(0.1f, 0.46f); lineTo(0.26f, 0.46f); lineTo(0.26f, 0.9f); lineTo(0.1f, 0.9f); close()
            moveTo(0.26f, 0.5f); lineTo(0.44f, 0.12f); quadTo(0.64f, 0.12f, 0.6f, 0.34f); lineTo(0.56f, 0.46f)
            lineTo(0.84f, 0.46f); quadTo(0.94f, 0.48f, 0.92f, 0.58f); lineTo(0.84f, 0.82f)
            quadTo(0.8f, 0.9f, 0.72f, 0.9f); lineTo(0.26f, 0.9f)
        }
        Ic.CHART -> {
            moveTo(0.2f, 0.9f); lineTo(0.2f, 0.5f)
            moveTo(0.5f, 0.9f); lineTo(0.5f, 0.15f)
            moveTo(0.8f, 0.9f); lineTo(0.8f, 0.38f)
        }
        Ic.UP -> {
            moveTo(0.5f, 0.08f); lineTo(0.08f, 0.55f); lineTo(0.32f, 0.55f); lineTo(0.32f, 0.92f)
            lineTo(0.68f, 0.92f); lineTo(0.68f, 0.55f); lineTo(0.92f, 0.55f); close()
        }
        Ic.DOWN -> {
            moveTo(0.5f, 0.92f); lineTo(0.08f, 0.45f); lineTo(0.32f, 0.45f); lineTo(0.32f, 0.08f)
            lineTo(0.68f, 0.08f); lineTo(0.68f, 0.45f); lineTo(0.92f, 0.45f); close()
        }
        Ic.GLOBE -> {
            addCircle(0.5f, 0.5f, 0.42f, Path.Direction.CW)
            addOval(RectF(0.3f, 0.08f, 0.7f, 0.92f), Path.Direction.CW)
            moveTo(0.08f, 0.5f); lineTo(0.92f, 0.5f)
        }
        Ic.VIDEOCAM -> {
            addRoundRect(RectF(0.06f, 0.26f, 0.62f, 0.74f), 0.08f, 0.08f, Path.Direction.CW)
            moveTo(0.62f, 0.44f); lineTo(0.92f, 0.28f); lineTo(0.92f, 0.72f); lineTo(0.62f, 0.56f)
        }
        Ic.BACK -> {
            moveTo(0.62f, 0.14f); lineTo(0.26f, 0.5f); lineTo(0.62f, 0.86f)
        }
        Ic.MIC -> {
            addRoundRect(RectF(0.38f, 0.06f, 0.62f, 0.56f), 0.12f, 0.12f, Path.Direction.CW)
            moveTo(0.24f, 0.46f); quadTo(0.24f, 0.74f, 0.5f, 0.74f); quadTo(0.76f, 0.74f, 0.76f, 0.46f)
            moveTo(0.5f, 0.74f); lineTo(0.5f, 0.94f)
        }
        Ic.CHECKS -> {
            moveTo(0.04f, 0.56f); lineTo(0.24f, 0.76f); lineTo(0.6f, 0.26f)
            moveTo(0.36f, 0.66f); lineTo(0.44f, 0.76f); lineTo(0.8f, 0.26f)
        }
        Ic.NOTE -> {
            addOval(RectF(0.1f, 0.62f, 0.46f, 0.92f), Path.Direction.CW)
            moveTo(0.44f, 0.76f); lineTo(0.44f, 0.12f); lineTo(0.86f, 0.24f)
        }
        Ic.PLUS -> {
            moveTo(0.5f, 0.12f); lineTo(0.5f, 0.88f)
            moveTo(0.12f, 0.5f); lineTo(0.88f, 0.5f)
        }
        Ic.CLOSE -> {
            moveTo(0.16f, 0.16f); lineTo(0.84f, 0.84f)
            moveTo(0.84f, 0.16f); lineTo(0.16f, 0.84f)
        }
        else -> Unit
    }
}

internal fun CardPainter.icon(kind: Ic, x: Float, y: Float, size: Float, color: Int, filled: Boolean = false) {
    when (kind) {
        Ic.DOTS -> {
            fill.color = color
            repeat(3) { canvas.drawCircle(x + size * (0.2f + 0.3f * it), y + size / 2f, size * 0.075f, fill) }
            return
        }
        Ic.NOTE -> {
            // Nota musical rellena: cabeza y plica.
            fill.color = color
            canvas.drawOval(RectF(x + size * 0.1f, y + size * 0.62f, x + size * 0.46f, y + size * 0.92f), fill)
            stroke.color = color
            stroke.strokeWidth = size * 0.1f
            canvas.drawLine(x + size * 0.44f, y + size * 0.78f, x + size * 0.44f, y + size * 0.1f, stroke)
            canvas.drawLine(x + size * 0.44f, y + size * 0.1f, x + size * 0.86f, y + size * 0.24f, stroke)
            return
        }
        else -> Unit
    }
    val path: Path = when (kind) {
        Ic.HEART -> PathShapes.heart(size, size).also { it.offset(x, y) }
        Ic.COMMENT -> PathShapes.cloudPath(PathShapes.CloudShape.BUBBLE, size, size).also { it.offset(x, y) }
        Ic.BOOKMARK -> Path().apply {
            moveTo(x + size * 0.22f, y + size * 0.08f)
            lineTo(x + size * 0.78f, y + size * 0.08f)
            lineTo(x + size * 0.78f, y + size * 0.94f)
            lineTo(x + size * 0.5f, y + size * 0.68f)
            lineTo(x + size * 0.22f, y + size * 0.94f)
            close()
        }
        else -> unitPath(kind).also {
            it.transform(Matrix().apply { setScale(size, size); postTranslate(x, y) })
        }
    }
    // Las formas cerradas macizas (flechas de voto) se rellenan; el resto va de linea.
    val solid = filled || kind == Ic.UP || kind == Ic.DOWN
    if (solid) {
        fill.color = color
        canvas.drawPath(path, fill)
    } else {
        stroke.color = color
        stroke.strokeWidth = size * 0.085f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeJoin = Paint.Join.MITER
    }
}

// ======================================================================================
// X / Twitter (vista de detalle del post)
// ======================================================================================

private fun CardPainter.xLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, X_LIGHT, X_DARK)
    canvas.drawColor(s.bg)
    var y = 36f
    avatarCircle(PAD, y, avatar, false, 112f)
    val tx = PAD + 136f
    text(post.displayName, tx, y + 48f, 46f, s.ink, true)
    if (post.verified) badge(tx + measure(post.displayName, 46f, true) + 12f, y + 48f, 46f, X_BLUE)
    text(post.handle, tx, y + 100f, 40f, s.muted)
    icon(Ic.DOTS, width - PAD - 56f, y + 28f, 56f, s.muted)
    y += 112f + 22f

    y = rich(post.body, y, 54f, s.ink, X_BLUE)
    val img = images.firstOrNull()
    if (img != null && post.format != SocialFormat.TEXT && post.format != SocialFormat.THREAD) {
        val top = y
        y = cover(y, img, if (post.format == SocialFormat.VIDEO) 9f / 16f else 0.72f, inset = true)
        if (post.format == SocialFormat.VIDEO) playButton((top + y - 8f) / 2f)
    }
    if (post.link.isNotBlank()) {
        text(post.link, PAD, y + 40f, 40f, X_BLUE)
        y += 64f
    }
    text(post.datetime, PAD, y + 48f, 38f, s.muted)
    y += 76f

    hline(y, s.line, PAD, width - PAD)
    y += 22f
    val slots = listOf(
        Ic.COMMENT to post.comments, Ic.REPOST to post.shares,
        Ic.HEART to post.likes, Ic.CHART to post.views,
    )
    val slotW = (width - PAD * 2 - 180f) / slots.size
    slots.forEachIndexed { i, (kind, n) ->
        val x = PAD + slotW * i
        icon(kind, x, y, 52f, s.muted)
        if (n > 0) text(abbr(n), x + 66f, y + 40f, 38f, s.muted)
    }
    icon(Ic.BOOKMARK, width - PAD - 52f - 90f, y, 52f, s.muted)
    icon(Ic.SHARE, width - PAD - 52f, y, 52f, s.muted)
    y += 52f + 22f
    hline(y, s.line, PAD, width - PAD)
    y += 4f

    if (post.format == SocialFormat.THREAD) {
        post.replies.forEach { reply ->
            y = timelineRow(
                y, s, avatar, reply.author,
                if (reply.author.isBlank()) post.handle else "",
                reply.text, reply.likes, X_BLUE, null,
            )
            hline(y - 8f, s.line, PAD, width - PAD)
        }
    }
    return y + 28f
}

/** Fila de linea de tiempo: avatar a la izquierda; nombre, texto y corazon a la derecha. */
private fun CardPainter.timelineRow(
    top: Float,
    s: Skin,
    avatar: Bitmap?,
    author: String,
    sub: String,
    body: String,
    likes: Int,
    link: Int,
    verified: Int?,
): Float {
    val name = author.ifBlank { post.displayName }
    who(avatar, PAD, top + 20f, 88f, author)
    val tx = PAD + 112f
    text(name, tx, top + 62f, 42f, s.ink, true)
    var nx = tx + measure(name, 42f, true) + 12f
    if (verified != null) {
        badge(nx, top + 62f, 42f, verified)
        nx += 58f
    }
    if (sub.isNotBlank()) text(sub, nx, top + 62f, 38f, s.muted)
    var y = rich(body, top + 74f, 46f, s.ink, link, x = tx, maxWidth = width - tx - PAD)
    if (likes > 0) {
        icon(Ic.HEART, tx, y, 44f, s.muted)
        text(abbr(likes), tx + 58f, y + 34f, 36f, s.muted)
        y += 60f
    }
    return y + 14f
}

// ======================================================================================
// Instagram (publicacion del feed)
// ======================================================================================

private fun CardPainter.igLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, IG_LIGHT, IG_DARK)
    canvas.drawColor(s.bg)
    val name = user()
    var y = 0f
    // Cabecera: avatar con el aro degradado de las historias.
    val cy = 76f
    stroke.shader = LinearGradient(
        PAD, cy + 56f, PAD + 112f, cy - 56f,
        intArrayOf(0xFFFEDA75.toInt(), 0xFFFA7E1E.toInt(), 0xFFD62976.toInt(), 0xFF962FBF.toInt()),
        null, Shader.TileMode.CLAMP,
    )
    stroke.strokeWidth = 6f
    canvas.drawCircle(PAD + 56f, cy, 56f, stroke)
    stroke.shader = null
    avatarCircle(PAD + 10f, cy - 46f, avatar, false, 92f)
    val tx = PAD + 140f
    val hasLoc = post.location.isNotBlank()
    text(name, tx, if (hasLoc) cy - 6f else cy + 14f, 42f, s.ink, true)
    if (post.verified) {
        badge(tx + measure(name, 42f, true) + 10f, if (hasLoc) cy - 6f else cy + 14f, 42f, IG_BLUE)
    }
    if (hasLoc) text(post.location, tx, cy + 38f, 34f, s.ink)
    icon(Ic.DOTS, width - PAD - 64f, cy - 32f, 64f, s.ink)
    y = 150f

    // Foto a sangre. Sin foto, un hueco gris con la misma proporcion.
    val rect = RectF(0f, y, width, y + width)
    val img = images.firstOrNull()
    if (img != null) {
        coverRect(rect, img, 0f)
    } else {
        fill.color = s.chip
        canvas.drawRect(rect, fill)
    }
    val total = images.size
    if (post.format == SocialFormat.CAROUSEL && total > 1) {
        val label = "1/$total"
        val paint = textPaint(32f, true, Color.WHITE)
        val w = paint.measureText(label) + 44f
        fill.color = 0xB3262626.toInt()
        canvas.drawRoundRect(RectF(width - 28f - w, y + 28f, width - 28f, y + 88f), 30f, 30f, fill)
        canvas.drawText(label, width - 28f - w + 22f, y + 68f, paint)
    }
    y = rect.bottom + 30f

    // Barra de iconos: corazon, comentario, enviar a la izquierda; guardar a la derecha.
    icon(Ic.HEART, PAD, y, 68f, s.ink)
    icon(Ic.COMMENT, PAD + 104f, y, 68f, s.ink)
    icon(Ic.SEND, PAD + 208f, y, 68f, s.ink)
    icon(Ic.BOOKMARK, width - PAD - 68f, y, 68f, s.ink)
    if (post.format == SocialFormat.CAROUSEL && total > 1) {
        val count = minOf(total, 6)
        var x = width / 2f - (count - 1) * 30f / 2f
        repeat(count) { i ->
            fill.color = if (i == 0) IG_BLUE else s.line
            canvas.drawCircle(x, y + 34f, 9f, fill)
            x += 30f
        }
    }
    y += 68f + 24f

    if (post.likes > 0) {
        text("${abbr(post.likes)} ${post.labels.likes}", PAD, y + 38f, 40f, s.ink, true)
        y += 62f
    }
    val tag = if (post.dark) 0xFFE0F1FF.toInt() else 0xFF00376B.toInt()
    if (post.body.isNotBlank()) {
        y = rich("$name ${post.body}", y, 40f, s.ink, tag, boldLead = name.length)
    }
    if (post.comments > 0) {
        text(post.labels.viewAll.format(abbr(post.comments)), PAD, y + 38f, 38f, s.muted)
        y += 60f
    }
    post.replies.take(3).forEach { r ->
        y = rich("${r.author.ifBlank { name }} ${r.text}", y, 38f, s.ink, tag, boldLead = r.author.ifBlank { name }.length)
    }
    if (post.datetime.isNotBlank()) {
        text(post.datetime.uppercase(), PAD, y + 36f, 30f, s.muted)
        y += 64f
    }
    return y + 20f
}

// ======================================================================================
// Facebook
// ======================================================================================

private fun CardPainter.fbLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, FB_LIGHT, FB_DARK)
    canvas.drawColor(s.bg)
    var y = 28f
    avatarCircle(PAD, y, avatar, false, 112f)
    val tx = PAD + 136f
    text(post.displayName, tx, y + 46f, 44f, s.ink, true)
    if (post.verified) badge(tx + measure(post.displayName, 44f, true) + 10f, y + 46f, 44f, FB_BLUE)
    val sub = listOf(post.datetime, post.location).filter { it.isNotBlank() }.joinToString(" · ")
    text(sub, tx, y + 98f, 34f, s.muted)
    val subW = measure(sub, 34f, false)
    if (sub.isNotBlank()) icon(Ic.GLOBE, tx + subW + 14f, y + 68f, 36f, s.muted)
    icon(Ic.DOTS, width - PAD - 190f, y + 28f, 56f, s.muted)
    icon(Ic.CLOSE, width - PAD - 56f, y + 28f, 52f, s.muted)
    y += 112f + 28f

    // Un texto corto sin foto se ve grande, como en la app.
    val short = images.isEmpty() && post.body.length in 1..110 && !post.body.contains('\n')
    y = rich(post.body, y, if (short) 64f else 46f, s.ink, FB_BLUE)
    val img = images.firstOrNull()
    if (img != null && post.format != SocialFormat.TEXT) {
        val top = y + 8f
        val rect = RectF(0f, top, width, top + width * if (post.format == SocialFormat.VIDEO) 0.5625f else 1f)
        coverRect(rect, img, 0f)
        if (post.format == SocialFormat.VIDEO) playButton(rect.centerY())
        if (post.format == SocialFormat.CAROUSEL && images.size > 1) {
            val label = "1/${images.size}"
            val paint = textPaint(32f, true, Color.WHITE)
            val w = paint.measureText(label) + 44f
            fill.color = 0xB3000000.toInt()
            canvas.drawRoundRect(RectF(width - 28f - w, top + 28f, width - 28f, top + 88f), 30f, 30f, fill)
            canvas.drawText(label, width - 28f - w + 22f, top + 68f, paint)
        }
        y = rect.bottom + 8f
    }

    // Reacciones a la izquierda, comentarios y veces compartido a la derecha.
    y += 18f
    if (post.likes > 0) {
        fill.color = FB_BLUE
        canvas.drawCircle(PAD + 24f, y + 28f, 26f, fill)
        icon(Ic.THUMB, PAD + 8f, y + 12f, 32f, Color.WHITE, filled = false)
        fill.color = 0xFFF33E58.toInt()
        canvas.drawCircle(PAD + 62f, y + 28f, 26f, fill)
        stroke.color = s.bg
        stroke.strokeWidth = 3f
        canvas.drawCircle(PAD + 62f, y + 28f, 27f, stroke)
        icon(Ic.HEART, PAD + 47f, y + 14f, 30f, Color.WHITE, filled = true)
        text(abbr(post.likes), PAD + 104f, y + 40f, 38f, s.muted)
    }
    val right = listOfNotNull(
        post.comments.takeIf { it > 0 }?.let { "${abbr(it)} ${post.labels.comments}" },
        post.shares.takeIf { it > 0 }?.let { "${abbr(it)} ${post.labels.shares}" },
    ).joinToString("  ")
    if (right.isNotBlank()) {
        val w = measure(right, 36f, false)
        text(right, width - PAD - w, y + 40f, 36f, s.muted)
    }
    y += 70f
    hline(y, s.line, PAD, width - PAD)
    y += 14f
    val third = width / 3f
    listOf(
        Ic.THUMB to post.labels.like, Ic.COMMENT to post.labels.comment, Ic.SHARE to post.labels.share,
    ).forEachIndexed { i, (kind, label) ->
        val w = measure(label, 38f, true)
        val x = third * i + (third - (52f + 14f + w)) / 2f
        icon(kind, x, y + 14f, 52f, s.muted)
        text(label, x + 66f, y + 52f, 38f, s.muted, true)
    }
    return y + 100f
}

// ======================================================================================
// YouTube (pagina de un video)
// ======================================================================================

private fun CardPainter.ytLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, YT_LIGHT, YT_DARK)
    canvas.drawColor(s.bg)
    val rect = RectF(0f, 0f, width, width * 0.5625f)
    val img = images.firstOrNull()
    if (img != null) {
        coverRect(rect, img, 0f)
    } else {
        fill.color = 0xFF000000.toInt()
        canvas.drawRect(rect, fill)
    }
    playButton(rect.centerY())
    // Barra de progreso roja pegada al borde inferior del reproductor.
    fill.color = 0x66FFFFFF
    canvas.drawRect(0f, rect.bottom - 8f, width, rect.bottom, fill)
    fill.color = 0xFFFF0000.toInt()
    canvas.drawRect(0f, rect.bottom - 8f, width * 0.34f, rect.bottom, fill)
    var y = rect.bottom + 24f

    y = rich(post.body.ifBlank { post.displayName }, y, 52f, s.ink, s.ink, bold = true)
    val stats = listOfNotNull(
        post.views.takeIf { it > 0 }?.let { "${abbr(it)} ${post.labels.views}" },
        post.datetime.takeIf { it.isNotBlank() },
    ).joinToString("  ")
    if (stats.isNotBlank()) {
        text(stats, PAD, y + 34f, 36f, s.muted)
        y += 64f
    }
    y += 12f
    // Canal y boton de suscripcion.
    avatarCircle(PAD, y, avatar, false, 104f)
    val tx = PAD + 128f
    text(post.displayName, tx, y + 44f, 42f, s.ink, true)
    if (post.verified) {
        fill.color = s.muted
        canvas.drawCircle(tx + measure(post.displayName, 42f, true) + 30f, y + 30f, 14f, fill)
        stroke.color = s.bg
        stroke.strokeWidth = 4f
        val cx = tx + measure(post.displayName, 42f, true) + 30f
        canvas.drawLine(cx - 6f, y + 31f, cx - 1f, y + 37f, stroke)
        canvas.drawLine(cx - 1f, y + 37f, cx + 8f, y + 24f, stroke)
    }
    text(post.handle, tx, y + 92f, 34f, s.muted)
    val sub = post.labels.subscribe
    val sw = measure(sub, 38f, true) + 72f
    pillText(width - PAD - sw, y + 16f, 76f, sub, 38f, s.ink, s.bg)
    y += 104f + 30f

    // Fila de acciones: me gusta / no me gusta, compartir.
    var x = PAD
    val h = 80f
    val likeLabel = if (post.likes > 0) abbr(post.likes) else post.labels.like
    val lw = measure(likeLabel, 38f, true)
    fill.color = s.chip
    canvas.drawRoundRect(RectF(x, y, x + 52f + 20f + lw + 110f, y + h), h / 2f, h / 2f, fill)
    icon(Ic.THUMB, x + 28f, y + 14f, 52f, s.ink)
    text(likeLabel, x + 28f + 70f, y + 54f, 38f, s.ink, true)
    val dx = x + 28f + 70f + lw + 28f
    stroke.color = s.line
    stroke.strokeWidth = 2f
    canvas.drawLine(dx, y + 18f, dx, y + h - 18f, stroke)
    canvas.save()
    canvas.scale(1f, -1f, dx + 56f, y + 40f)
    icon(Ic.THUMB, dx + 30f, y + 14f, 52f, s.ink)
    canvas.restore()
    x += 52f + 20f + lw + 110f + 20f
    x = pillText(x, y, h, post.labels.share, 38f, s.chip, s.ink, padX = 30f)
    y += h + 30f

    // Caja de comentarios.
    if (post.comments > 0 || post.replies.isNotEmpty()) {
        val first = post.replies.firstOrNull()
        val boxH = if (first != null) 190f else 100f
        fill.color = s.chip
        canvas.drawRoundRect(RectF(PAD, y, width - PAD, y + boxH), 28f, 28f, fill)
        val cm = post.labels.comments.replaceFirstChar { it.uppercase() }
        text(if (post.comments > 0) "$cm  ${abbr(post.comments)}" else cm, PAD + 28f, y + 52f, 38f, s.ink, true)
        if (first != null) {
            val name = first.author.ifBlank { post.displayName }
            letterAvatar(PAD + 28f, y + 78f, 70f, name)
            text(first.text.lines().first().take(60), PAD + 116f, y + 124f, 36f, s.ink)
        }
        y += boxH + 24f
    }
    return y
}

// ======================================================================================
// TikTok (video vertical a pantalla completa)
// ======================================================================================

private fun CardPainter.tiktokLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val h = 1920f
    val white = Color.WHITE
    canvas.drawColor(0xFF000000.toInt())
    images.firstOrNull()?.let { coverRect(RectF(0f, 0f, width, h), it, 0f) }
    scrim(RectF(0f, 0f, width, 300f), top = true)
    scrim(RectF(0f, h - 800f, width, h), top = false)

    // Pestanas superiores.
    val dim = textPaint(46f, false, 0xB3FFFFFF.toInt())
    val on = textPaint(46f, true, white)
    val gap = 60f
    val a = dim.measureText(post.labels.following)
    val b = on.measureText(post.labels.forYou)
    var x = (width - (a + gap + b)) / 2f
    canvas.drawText(post.labels.following, x, 124f, dim)
    x += a + gap
    canvas.drawText(post.labels.forYou, x, 124f, on)
    fill.color = white
    canvas.drawRoundRect(RectF(x + b / 2f - 30f, 146f, x + b / 2f + 30f, 152f), 3f, 3f, fill)

    // Columna lateral de acciones.
    val cx = width - 96f
    var y = h - 1010f
    avatarCircle(cx - 64f, y, avatar, ringed = true, size = 128f)
    fill.color = TT_RED
    canvas.drawCircle(cx, y + 128f, 26f, fill)
    icon(Ic.PLUS, cx - 14f, y + 114f, 28f, white)
    y += 214f
    listOf(
        Ic.HEART to post.likes, Ic.COMMENT to post.comments,
        Ic.BOOKMARK to post.saves, Ic.SHARE to post.shares,
    ).forEach { (kind, n) ->
        icon(kind, cx - 44f, y, 88f, white, filled = kind != Ic.SHARE)
        val label = if (n > 0) abbr(n) else "0"
        val paint = textPaint(34f, true, white)
        canvas.drawText(label, cx - paint.measureText(label) / 2f, y + 88f + 40f, paint)
        y += 176f
    }
    // Disco de musica.
    fill.color = 0xFF1B1B1B.toInt()
    canvas.drawCircle(cx, y + 56f, 60f, fill)
    stroke.color = 0x66FFFFFF
    stroke.strokeWidth = 4f
    canvas.drawCircle(cx, y + 56f, 60f, stroke)
    if (avatar != null) {
        avatarCircle(cx - 30f, y + 26f, avatar, false, 60f)
    } else {
        fill.color = TT_RED
        canvas.drawCircle(cx, y + 56f, 30f, fill)
    }

    // Texto inferior izquierdo.
    val name = "@" + user()
    val maxW = width - PAD - 220f
    var by = h - 470f
    text(name, PAD, by, 50f, white, true)
    if (post.verified) badge(PAD + measure(name, 50f, true) + 12f, by, 50f, 0xFF20D5EC.toInt())
    by += 14f
    by = rich(post.body, by, 44f, white, white, maxWidth = maxW, boldTokens = true)
    icon(Ic.NOTE, PAD, by + 14f, 44f, white)
    text("${post.labels.sound} - ${user()}", PAD + 64f, by + 48f, 38f, white)
    by += 90f
    if (post.location.isNotBlank()) pillText(PAD, by, 64f, post.location, 34f, 0x66000000, white, bold = false, padX = 26f)
    return h
}

// ======================================================================================
// WhatsApp (chat y grupo)
// ======================================================================================

private val WA_NAMES = intArrayOf(
    0xFF06CF9C.toInt(), 0xFFFFA726.toInt(), 0xFF53BDEB.toInt(),
    0xFFE26AB6.toInt(), 0xFFB38BFA.toInt(), 0xFFF2705B.toInt(),
)

private fun CardPainter.waLayout(avatar: Bitmap?): Float {
    val dark = post.dark
    val wall = if (dark) 0xFF0B141A.toInt() else 0xFFEFEAE2.toInt()
    val head = if (dark) 0xFF1F2C34.toInt() else 0xFF008069.toInt()
    val inBubble = if (dark) 0xFF1F2C34.toInt() else 0xFFFFFFFF.toInt()
    val outBubble = if (dark) 0xFF005C4B.toInt() else 0xFFD9FDD3.toInt()
    val ink = if (dark) 0xFFE9EDEF.toInt() else 0xFF111B21.toInt()
    val time = if (dark) 0xFF8696A0.toInt() else 0xFF667781.toInt()
    val tick = 0xFF53BDEB.toInt()
    val group = post.format == SocialFormat.GROUP
    canvas.drawColor(wall)

    // Cabecera verde con atras, avatar, nombre y estado.
    fill.color = head
    canvas.drawRect(0f, 0f, width, 180f, fill)
    icon(Ic.BACK, 24f, 60f, 60f, Color.WHITE)
    avatarCircle(104f, 34f, avatar, false, 112f)
    val title = if (group) post.location.ifBlank { post.displayName } else post.displayName
    text(title, 244f, 92f, 46f, Color.WHITE, true)
    text(post.handle, 244f, 142f, 34f, 0xCCFFFFFF.toInt())
    icon(Ic.VIDEOCAM, width - 270f, 60f, 64f, Color.WHITE)
    icon(Ic.DOTS, width - 110f, 60f, 64f, Color.WHITE)

    var y = 180f + 36f
    val clock = clockText()
    val date = post.datetime.replace(clock, "").trim().trimEnd(',').trim()
    if (date.isNotBlank()) {
        val paint = textPaint(32f, false, time)
        val w = paint.measureText(date) + 56f
        fill.color = if (dark) 0xFF1F2C34.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(RectF((width - w) / 2f, y, (width + w) / 2f, y + 60f), 18f, 18f, fill)
        canvas.drawText(date, (width - w) / 2f + 28f, y + 41f, paint)
        y += 96f
    }

    val messages = buildList {
        if (post.body.isNotBlank()) add(SocialComment(post.displayName, post.body))
        addAll(post.replies)
    }
    messages.forEach { m ->
        val mine = m.mine
        val author = m.author.ifBlank { post.displayName }
        val showName = group && !mine
        val maxW = width * 0.76f - 56f
        val layout = richLayout(m.text, 44f, ink, 0xFF027EB5.toInt(), maxW)
        val timeW = measure(clock, 30f, false) + if (mine) 58f else 0f
        val nameW = if (showName) measure(author, 38f, true) else 0f
        val bw = max(max(layout.widest(), nameW), timeW) + 56f
        val bh = 18f + (if (showName) 46f else 0f) + layout.height + 8f + 40f + 10f
        val left = if (mine) width - 24f - bw else 24f
        fill.color = if (mine) outBubble else inBubble
        canvas.drawRoundRect(RectF(left, y, left + bw, y + bh), 28f, 28f, fill)
        var ty = y + 18f
        if (showName) {
            val c = WA_NAMES[abs(author.hashCode()) % WA_NAMES.size]
            text(author, left + 28f, ty + 34f, 38f, c, true)
            ty += 46f
        }
        drawLayout(layout, left + 28f, ty)
        val tb = y + bh - 16f
        val tw = measure(clock, 30f, false)
        val tx = left + bw - 24f - tw - if (mine) 58f else 0f
        text(clock, tx, tb, 30f, if (mine) (if (dark) 0xFF99BEB7.toInt() else 0xFF667781.toInt()) else time)
        if (mine) icon(Ic.CHECKS, left + bw - 24f - 48f, tb - 30f, 44f, tick)
        y += bh + 14f
    }

    // Barra de escribir.
    y += 28f
    fill.color = inBubble
    canvas.drawRoundRect(RectF(24f, y, width - 24f - 140f, y + 116f), 58f, 58f, fill)
    fill.color = 0xFF00A884.toInt()
    canvas.drawCircle(width - 24f - 58f, y + 58f, 58f, fill)
    icon(Ic.MIC, width - 24f - 58f - 28f, y + 58f - 30f, 56f, Color.WHITE)
    return y + 116f + 28f
}

// ======================================================================================
// Threads
// ======================================================================================

private fun CardPainter.threadsLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, TH_LIGHT, TH_DARK)
    canvas.drawColor(s.bg)
    var y = 30f
    val tx = PAD + 124f
    avatarCircle(PAD, y + 8f, avatar, false, 96f)
    val name = user()
    text(name, tx, y + 52f, 44f, s.ink, true)
    var nx = tx + measure(name, 44f, true) + 12f
    if (post.verified) {
        badge(nx, y + 52f, 44f, IG_BLUE)
        nx += 58f
    }
    text(post.datetime, nx + 6f, y + 52f, 38f, s.muted)
    icon(Ic.DOTS, width - PAD - 56f, y + 24f, 56f, s.muted)
    val lineTop = y + 118f
    var by = rich(post.body, y + 64f, 46f, s.ink, 0xFF1D9BF0.toInt(), x = tx, maxWidth = width - tx - PAD)
    val img = images.firstOrNull()
    if (img != null && post.format != SocialFormat.TEXT && post.format != SocialFormat.THREAD) {
        val rect = RectF(tx, by + 4f, width - PAD, by + 4f + (width - tx - PAD) * 1.2f)
        coverRect(rect, img, 36f)
        if (post.format == SocialFormat.CAROUSEL && images.size > 1) {
            val label = "1/${images.size}"
            val paint = textPaint(30f, true, Color.WHITE)
            val w = paint.measureText(label) + 40f
            fill.color = 0xB3000000.toInt()
            canvas.drawRoundRect(RectF(rect.right - 20f - w, rect.top + 20f, rect.right - 20f, rect.top + 76f), 28f, 28f, fill)
            canvas.drawText(label, rect.right - 20f - w + 20f, rect.top + 58f, paint)
        }
        by = rect.bottom + 14f
    }
    val iconColor = s.ink
    listOf(Ic.HEART, Ic.COMMENT, Ic.REPOST, Ic.SEND).forEachIndexed { i, kind ->
        icon(kind, tx + i * 100f, by + 6f, 56f, iconColor)
    }
    by += 76f
    val counts = listOfNotNull(
        post.comments.takeIf { it > 0 }?.let { "${abbr(it)} ${post.labels.replies}" },
        post.likes.takeIf { it > 0 }?.let { "${abbr(it)} ${post.labels.likes}" },
    ).joinToString("  ·  ")
    if (counts.isNotBlank()) {
        text(counts, tx, by + 36f, 38f, s.muted)
        by += 64f
    }
    // Linea del hilo que baja desde el avatar hasta la siguiente respuesta.
    val replies = if (post.format == SocialFormat.THREAD) post.replies else emptyList()
    if (replies.isNotEmpty()) {
        stroke.color = s.line
        stroke.strokeWidth = 5f
        canvas.drawLine(PAD + 48f, lineTop, PAD + 48f, by + 10f, stroke)
    }
    y = by + 24f
    replies.forEachIndexed { i, r ->
        val top = y
        val author = r.author.ifBlank { post.displayName }
        who(avatar, PAD + 8f, top, 80f, r.author)
        text(author, tx, top + 46f, 42f, s.ink, true)
        var ry = rich(r.text, top + 56f, 46f, s.ink, 0xFF1D9BF0.toInt(), x = tx, maxWidth = width - tx - PAD)
        if (r.likes > 0) {
            icon(Ic.HEART, tx, ry, 44f, s.muted)
            text(abbr(r.likes), tx + 58f, ry + 34f, 36f, s.muted)
            ry += 60f
        }
        if (i < replies.lastIndex) {
            stroke.color = s.line
            stroke.strokeWidth = 5f
            canvas.drawLine(PAD + 48f, top + 100f, PAD + 48f, ry + 10f, stroke)
        }
        y = ry + 24f
    }
    return y + 8f
}

// ======================================================================================
// Reddit
// ======================================================================================

private fun CardPainter.redditLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, RD_LIGHT, RD_DARK)
    canvas.drawColor(s.bg)
    val orange = 0xFFFF4500.toInt()
    var y = 30f
    // Comunidad: icono circular, r/nombre y autor.
    val sub = "r/" + post.location.removePrefix("r/").ifBlank { user() }
    fill.color = orange
    canvas.drawCircle(PAD + 40f, y + 44f, 40f, fill)
    val rPaint = textPaint(42f, true, Color.WHITE)
    canvas.drawText("r", PAD + 40f - rPaint.measureText("r") / 2f, y + 58f, rPaint)
    val tx = PAD + 100f
    text(sub, tx, y + 36f, 38f, s.ink, true)
    text("u/${post.displayName}  ·  ${post.datetime}", tx, y + 82f, 32f, s.muted)
    val join = post.labels.join
    val jw = measure(join, 34f, true) + 64f
    pillText(width - PAD - jw - 70f, y + 14f, 68f, join, 34f, 0xFF0045AC.toInt(), Color.WHITE, padX = 32f)
    icon(Ic.DOTS, width - PAD - 56f, y + 28f, 56f, s.muted)
    y += 112f

    val lines = post.body.lines()
    val title = lines.firstOrNull().orEmpty()
    val rest = lines.drop(1).joinToString("\n").trim()
    y = rich(title, y, 56f, s.ink, s.ink, bold = true)
    if (rest.isNotBlank()) y = rich(rest, y, 42f, s.ink, 0xFF0079D3.toInt())
    val img = images.firstOrNull()
    if (img != null && post.format != SocialFormat.TEXT && post.format != SocialFormat.THREAD) {
        val rect = RectF(PAD, y + 8f, width - PAD, y + 8f + content * if (post.format == SocialFormat.VIDEO) 0.5625f else 0.8f)
        coverRect(rect, img, 32f)
        if (post.format == SocialFormat.VIDEO) playButton(rect.centerY())
        y = rect.bottom + 16f
    }
    if (post.link.isNotBlank()) {
        text(post.link, PAD, y + 38f, 38f, 0xFF0079D3.toInt())
        y += 60f
    }

    // Barra de acciones: votos, comentarios, compartir.
    y += 14f
    val h = 80f
    var x = PAD
    val votes = if (post.likes > 0) abbr(post.likes) else "0"
    val vw = measure(votes, 38f, true)
    fill.color = s.chip
    val vTotal = 30f + 44f + 20f + vw + 20f + 44f + 30f
    canvas.drawRoundRect(RectF(x, y, x + vTotal, y + h), h / 2f, h / 2f, fill)
    icon(Ic.UP, x + 30f, y + 18f, 44f, s.ink)
    text(votes, x + 30f + 44f + 20f, y + 54f, 38f, s.ink, true)
    icon(Ic.DOWN, x + 30f + 44f + 20f + vw + 20f, y + 18f, 44f, s.ink)
    x += vTotal + 20f
    val cm = abbr(post.comments)
    val cw = measure(cm, 38f, true)
    fill.color = s.chip
    canvas.drawRoundRect(RectF(x, y, x + 30f + 44f + 18f + cw + 30f, y + h), h / 2f, h / 2f, fill)
    icon(Ic.COMMENT, x + 30f, y + 18f, 44f, s.ink)
    text(cm, x + 30f + 44f + 18f, y + 54f, 38f, s.ink, true)
    x += 30f + 44f + 18f + cw + 30f + 20f
    val sh = post.labels.share
    val shw = measure(sh, 38f, true)
    fill.color = s.chip
    canvas.drawRoundRect(RectF(x, y, x + 30f + 44f + 18f + shw + 30f, y + h), h / 2f, h / 2f, fill)
    icon(Ic.SHARE, x + 30f, y + 18f, 44f, s.ink)
    text(sh, x + 30f + 44f + 18f, y + 54f, 38f, s.ink, true)
    y += h + 24f

    // Comentarios con la barra de sangria de Reddit.
    if (post.format == SocialFormat.THREAD || post.replies.isNotEmpty()) {
        hline(y, s.line, 0f, width)
        y += 8f
        post.replies.forEach { r ->
            val author = r.author.ifBlank { post.displayName }
            who(avatar, PAD, y + 22f, 60f, r.author)
            text("u/$author", PAD + 80f, y + 58f, 36f, s.ink, true)
            var ry = rich(r.text, y + 66f, 42f, s.ink, 0xFF0079D3.toInt(), x = PAD + 80f, maxWidth = width - PAD * 2 - 80f)
            icon(Ic.UP, PAD + 80f, ry + 4f, 36f, s.muted)
            text(abbr(r.likes), PAD + 80f + 52f, ry + 34f, 34f, s.muted, true)
            icon(Ic.DOWN, PAD + 80f + 52f + measure(abbr(r.likes), 34f, true) + 18f, ry + 4f, 36f, s.muted)
            ry += 60f
            stroke.color = s.line
            stroke.strokeWidth = 4f
            canvas.drawLine(PAD + 30f, y + 92f, PAD + 30f, ry, stroke)
            y = ry + 12f
        }
    }
    return y + 16f
}

// ======================================================================================
// LinkedIn
// ======================================================================================

private fun CardPainter.linkedinLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, LI_LIGHT, LI_DARK)
    canvas.drawColor(s.bg)
    val blue = if (post.dark) 0xFF71B7FB.toInt() else LI_BLUE
    var y = 28f
    avatarCircle(PAD, y, avatar, false, 124f)
    val tx = PAD + 148f
    text(post.displayName, tx, y + 46f, 44f, s.ink, true)
    text(post.handle, tx, y + 92f, 34f, s.muted)
    val sub = listOf(post.datetime, post.location).filter { it.isNotBlank() }.joinToString(" · ")
    text(sub, tx, y + 134f, 32f, s.muted)
    if (sub.isNotBlank()) icon(Ic.GLOBE, tx + measure(sub, 32f, false) + 12f, y + 108f, 30f, s.muted)
    val follow = "+ ${post.labels.follow}"
    text(follow, width - PAD - measure(follow, 40f, true), y + 50f, 40f, blue, true)
    y += 124f + 30f

    y = rich(post.body, y, 44f, s.ink, blue)
    val img = images.firstOrNull()
    if (img != null && post.format != SocialFormat.TEXT) {
        val top = y + 8f
        val rect = RectF(0f, top, width, top + width * if (post.format == SocialFormat.VIDEO) 0.5625f else 0.9f)
        coverRect(rect, img, 0f)
        if (post.format == SocialFormat.VIDEO) playButton(rect.centerY())
        if (post.format == SocialFormat.CAROUSEL && images.size > 1) {
            val label = "1/${images.size}"
            val paint = textPaint(32f, true, Color.WHITE)
            val w = paint.measureText(label) + 44f
            fill.color = 0xB3000000.toInt()
            canvas.drawRoundRect(RectF(width - 28f - w, top + 28f, width - 28f, top + 88f), 30f, 30f, fill)
            canvas.drawText(label, width - 28f - w + 22f, top + 68f, paint)
        }
        y = rect.bottom + 8f
    }
    y += 22f
    if (post.likes > 0) {
        fill.color = blue
        canvas.drawCircle(PAD + 22f, y + 28f, 24f, fill)
        icon(Ic.THUMB, PAD + 8f, y + 14f, 28f, Color.WHITE)
        text(abbr(post.likes), PAD + 60f, y + 40f, 36f, s.muted)
    }
    val right = listOfNotNull(
        post.comments.takeIf { it > 0 }?.let { "${abbr(it)} ${post.labels.comments}" },
        post.shares.takeIf { it > 0 }?.let { "${abbr(it)} ${post.labels.shares}" },
    ).joinToString(" · ")
    if (right.isNotBlank()) text(right, width - PAD - measure(right, 34f, false), y + 40f, 34f, s.muted)
    y += 68f
    hline(y, s.line, PAD, width - PAD)
    y += 10f
    val quarter = (width - PAD * 2) / 4f
    listOf(
        Ic.THUMB to post.labels.like, Ic.COMMENT to post.labels.comment,
        Ic.REPOST to post.labels.repost, Ic.SEND to post.labels.send,
    ).forEachIndexed { i, (kind, label) ->
        val cx = PAD + quarter * i + quarter / 2f
        icon(kind, cx - 26f, y + 10f, 52f, s.muted)
        val paint = textPaint(30f, true, s.muted)
        canvas.drawText(label, cx - paint.measureText(label) / 2f, y + 92f, paint)
    }
    return y + 120f
}

// ======================================================================================
// Pinterest (vista de un pin)
// ======================================================================================

private fun CardPainter.pinterestLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, PI_LIGHT, PI_DARK)
    canvas.drawColor(s.bg)
    val red = 0xFFE60023.toInt()
    var y = 36f
    val rect = RectF(PAD, y, width - PAD, y + content * 1.3f)
    val img = images.firstOrNull()
    if (img != null) {
        coverRect(rect, img, 56f)
    } else {
        fill.color = s.chip
        canvas.drawRoundRect(rect, 56f, 56f, fill)
    }
    y = rect.bottom + 28f
    // Fila de acciones: corazon con cifra, compartir, mas; "Guardar" a la derecha.
    icon(Ic.HEART, PAD, y, 64f, s.ink)
    var x = PAD + 64f + 14f
    if (post.likes > 0) {
        text(abbr(post.likes), x, y + 46f, 40f, s.ink, true)
        x += measure(abbr(post.likes), 40f, true) + 36f
    } else {
        x += 22f
    }
    icon(Ic.SHARE, x, y, 64f, s.ink)
    icon(Ic.DOTS, x + 100f, y, 64f, s.ink)
    val save = post.labels.save
    val sw = measure(save, 40f, true) + 72f
    pillText(width - PAD - sw, y - 6f, 76f, save, 40f, red, Color.WHITE)
    y += 64f + 34f

    val lines = post.body.lines()
    y = rich(lines.firstOrNull().orEmpty(), y, 56f, s.ink, s.ink, bold = true)
    val rest = lines.drop(1).joinToString("\n").trim()
    if (rest.isNotBlank()) y = rich(rest, y, 40f, s.ink, 0xFF0074E8.toInt())
    y += 8f
    avatarCircle(PAD, y, avatar, false, 96f)
    text(post.displayName, PAD + 120f, y + 40f, 40f, s.ink, true)
    if (post.handle.isNotBlank()) text(post.handle, PAD + 120f, y + 84f, 34f, s.muted)
    y += 96f + 20f
    if (post.comments > 0 || post.replies.isNotEmpty()) {
        hline(y, s.line, PAD, width - PAD)
        y += 14f
        post.replies.forEach { r ->
            val author = r.author.ifBlank { post.displayName }
            who(avatar, PAD, y + 12f, 72f, r.author)
            text(author, PAD + 96f, y + 44f, 38f, s.ink, true)
            y = rich(r.text, y + 52f, 40f, s.ink, 0xFF0074E8.toInt(), x = PAD + 96f, maxWidth = content - 96f) + 6f
        }
    }
    return y + 20f
}

// ======================================================================================
// Twitch (chat del directo)
// ======================================================================================

private val TW_NAMES = intArrayOf(
    0xFFFF4A4A.toInt(), 0xFF1E90FF.toInt(), 0xFF00C853.toInt(), 0xFFB22222.toInt(),
    0xFFFF7F50.toInt(), 0xFF9ACD32.toInt(), 0xFFD2691E.toInt(), 0xFF8A2BE2.toInt(),
    0xFF00FF7F.toInt(), 0xFFDAA520.toInt(),
)

private fun CardPainter.twitchChatLayout(): Float {
    val s = skin(post.dark, TW_LIGHT, TW_DARK)
    canvas.drawColor(s.bg)
    val purple = 0xFF9146FF.toInt()
    var y = 24f
    val messages = buildList {
        if (post.body.isNotBlank()) add(SocialComment(post.displayName.ifBlank { user() }, post.body))
        addAll(post.replies)
    }
    messages.forEach { m ->
        val name = m.author.ifBlank { post.displayName.ifBlank { user() } }
        val color = TW_NAMES[abs(name.hashCode()) % TW_NAMES.size]
        // Insignia de suscriptor delante del nombre.
        fill.color = purple
        canvas.drawRoundRect(RectF(PAD, y + 12f, PAD + 40f, y + 52f), 8f, 8f, fill)
        val sb = SpannableStringBuilder(name)
        sb.setSpan(ForegroundColorSpan(color), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val body = SpannableStringBuilder(": ${m.text}")
        body.setSpan(ForegroundColorSpan(s.ink), 0, body.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append(body)
        val tx = PAD + 56f
        val layout = StaticLayout.Builder
            .obtain(sb, 0, sb.length, textPaint(42f, false, s.ink), (width - tx - PAD).toInt())
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .build()
        drawLayout(layout, tx, y + 8f)
        y += layout.height + 34f
    }
    return y + 10f
}

// ======================================================================================
// Perfiles, reel de Instagram, Shorts de YouTube y directo de Twitch
// ======================================================================================

/** Cifras de perfil: seguidores = "Me gusta", siguiendo = "Compartidos", publicaciones = "Comentarios". */
private fun CardPainter.xProfileLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, X_LIGHT, X_DARK)
    canvas.drawColor(s.bg)
    val bh = 360f
    val banner = images.firstOrNull()
    if (banner != null) {
        coverRect(RectF(0f, 0f, width, bh), banner, 0f)
    } else {
        fill.color = if (post.dark) 0xFF333639.toInt() else 0xFFCFD9DE.toInt()
        canvas.drawRect(0f, 0f, width, bh, fill)
    }
    fill.color = s.bg
    canvas.drawCircle(PAD + 110f, bh + 10f, 116f, fill)
    avatarCircle(PAD + 10f, bh - 90f, avatar, false, 200f)
    val fw = measure(post.labels.follow, 38f, true) + 72f
    pillText(width - PAD - fw, bh + 28f, 76f, post.labels.follow, 38f, s.ink, s.bg)
    var y = bh + 136f
    text(post.displayName, PAD, y + 40f, 56f, s.ink, true)
    if (post.verified) badge(PAD + measure(post.displayName, 56f, true) + 12f, y + 40f, 56f, X_BLUE)
    text(post.handle, PAD, y + 94f, 40f, s.muted)
    y += 118f
    y = rich(post.body, y, 44f, s.ink, X_BLUE)
    val meta = listOf(post.location, post.datetime).filter { it.isNotBlank() }.joinToString("   ")
    if (meta.isNotBlank()) {
        text(meta, PAD, y + 36f, 36f, s.muted)
        y += 60f
    }
    if (post.link.isNotBlank()) {
        text(post.link, PAD, y + 38f, 38f, X_BLUE)
        y += 62f
    }
    var x = PAD
    listOf(post.shares to post.labels.following, post.likes to post.labels.followers).forEach { (n, label) ->
        val num = abbr(n)
        text(num, x, y + 40f, 40f, s.ink, true)
        val nw = measure(num, 40f, true)
        text(label, x + nw + 10f, y + 40f, 40f, s.muted)
        x += nw + 10f + measure(label, 40f, false) + 44f
    }
    y += 72f
    hline(y, s.line, 0f, width)
    return y + 20f
}

private fun CardPainter.igProfileLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, IG_LIGHT, IG_DARK)
    canvas.drawColor(s.bg)
    val tag = if (post.dark) 0xFFE0F1FF.toInt() else 0xFF00376B.toInt()
    val name = user()
    text(name, PAD, 90f, 48f, s.ink, true)
    if (post.verified) badge(PAD + measure(name, 48f, true) + 12f, 90f, 48f, IG_BLUE)
    icon(Ic.PLUS, width - PAD - 150f, 44f, 60f, s.ink)
    icon(Ic.DOTS, width - PAD - 60f, 44f, 60f, s.ink)
    var y = 150f
    stroke.shader = LinearGradient(
        PAD, y + 230f, PAD + 230f, y,
        intArrayOf(0xFFFEDA75.toInt(), 0xFFFA7E1E.toInt(), 0xFFD62976.toInt(), 0xFF962FBF.toInt()),
        null, Shader.TileMode.CLAMP,
    )
    stroke.strokeWidth = 6f
    canvas.drawCircle(PAD + 115f, y + 115f, 115f, stroke)
    stroke.shader = null
    avatarCircle(PAD + 14f, y + 14f, avatar, false, 202f)
    val cols = listOf(
        post.comments to post.labels.posts, post.likes to post.labels.followers,
        post.shares to post.labels.following,
    )
    val x0 = PAD + 230f + 20f
    val colW = (width - PAD - x0) / 3f
    cols.forEachIndexed { i, (n, label) ->
        val cx = x0 + colW * i + colW / 2f
        val num = abbr(n)
        val np = textPaint(46f, true, s.ink)
        canvas.drawText(num, cx - np.measureText(num) / 2f, y + 96f, np)
        val lp = textPaint(30f, false, s.ink)
        canvas.drawText(label, cx - lp.measureText(label) / 2f, y + 140f, lp)
    }
    y += 230f + 24f
    text(post.displayName, PAD, y + 36f, 40f, s.ink, true)
    y += 52f
    y = rich(post.body, y, 40f, s.ink, tag)
    if (post.link.isNotBlank()) {
        text(post.link, PAD, y + 38f, 38f, tag, true)
        y += 62f
    }
    y += 12f
    val bw = (content - 16f) / 2f
    centeredButton(PAD, y, bw, 80f, post.labels.follow, IG_BLUE, Color.WHITE)
    centeredButton(PAD + bw + 16f, y, bw, 80f, post.labels.message, s.chip, s.ink)
    y += 80f + 28f
    val gap = 6f
    val cell = (width - gap * 2) / 3f
    val shots = images.take(9)
    val count = if (shots.isEmpty()) 6 else ((shots.size + 2) / 3) * 3
    for (i in 0 until count) {
        val r = i / 3
        val c = i % 3
        val left = c * (cell + gap)
        val top = y + r * (cell + gap)
        val rect = RectF(left, top, left + cell, top + cell)
        val shot = shots.getOrNull(i)
        if (shot != null) {
            coverRect(rect, shot, 0f)
        } else {
            fill.color = s.chip
            canvas.drawRect(rect, fill)
        }
    }
    return y + (count / 3) * (cell + gap)
}

private fun CardPainter.centeredButton(x: Float, y: Float, w: Float, h: Float, label: String, bgColor: Int, fg: Int) {
    fill.color = bgColor
    canvas.drawRoundRect(RectF(x, y, x + w, y + h), 20f, 20f, fill)
    val paint = textPaint(38f, true, fg)
    canvas.drawText(label, x + (w - paint.measureText(label)) / 2f, y + h / 2f + 13f, paint)
}

/** Fondo, degradados y alto comunes a los videos verticales (reel y shorts). */
private fun CardPainter.shortBase(images: List<Bitmap>): Float {
    canvas.drawColor(0xFF000000.toInt())
    images.firstOrNull()?.let { coverRect(RectF(0f, 0f, width, 1920f), it, 0f) }
    scrim(RectF(0f, 0f, width, 300f), top = true)
    scrim(RectF(0f, 1920f - 800f, width, 1920f), top = false)
    return 1920f
}

/** Icono de la columna lateral con su cifra debajo; devuelve la y siguiente. */
private fun CardPainter.sideItem(cx: Float, y: Float, kind: Ic, label: String?, flip: Boolean = false): Float {
    if (flip) {
        canvas.save()
        canvas.scale(1f, -1f, cx, y + 44f)
    }
    icon(kind, cx - 44f, y, 88f, Color.WHITE)
    if (flip) canvas.restore()
    if (!label.isNullOrBlank()) {
        val paint = textPaint(34f, true, Color.WHITE)
        canvas.drawText(label, cx - paint.measureText(label) / 2f, y + 88f + 40f, paint)
    }
    return y + 176f
}

private fun CardPainter.audioSquare(cx: Float, y: Float, avatar: Bitmap?) {
    val sq = RectF(cx - 40f, y, cx + 40f, y + 80f)
    if (avatar != null) {
        coverRect(sq, avatar, 16f)
    } else {
        fill.color = 0xFF333333.toInt()
        canvas.drawRoundRect(sq, 16f, 16f, fill)
    }
    stroke.color = Color.WHITE
    stroke.strokeWidth = 4f
    canvas.drawRoundRect(sq, 16f, 16f, stroke)
}

private fun CardPainter.reelsLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val h = shortBase(images)
    val white = Color.WHITE
    text("Reels", PAD, 120f, 58f, white, true)
    val cx = width - 96f
    var y = h - 1000f
    y = sideItem(cx, y, Ic.HEART, abbr(post.likes))
    y = sideItem(cx, y, Ic.COMMENT, abbr(post.comments))
    y = sideItem(cx, y, Ic.SEND, post.shares.takeIf { it > 0 }?.let { abbr(it) })
    icon(Ic.DOTS, cx - 44f, y - 20f, 88f, white)
    audioSquare(cx, y + 90f, avatar)

    val name = user()
    var by = h - 540f
    avatarCircle(PAD, by, avatar, false, 84f)
    text(name, PAD + 108f, by + 56f, 44f, white, true)
    var nx = PAD + 108f + measure(name, 44f, true) + 12f
    if (post.verified) {
        badge(nx, by + 56f, 44f, IG_BLUE)
        nx += 58f
    }
    val fw = measure(post.labels.follow, 34f, true) + 56f
    stroke.color = white
    stroke.strokeWidth = 3f
    canvas.drawRoundRect(RectF(nx + 16f, by + 8f, nx + 16f + fw, by + 76f), 18f, 18f, stroke)
    text(post.labels.follow, nx + 16f + 28f, by + 54f, 34f, white, true)
    by += 100f
    by = rich(post.body, by, 42f, white, white, maxWidth = width - PAD - 220f)
    icon(Ic.NOTE, PAD, by + 10f, 40f, white)
    text("$name · ${post.labels.sound}", PAD + 56f, by + 42f, 36f, white)
    return h
}

private fun CardPainter.shortsLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val h = shortBase(images)
    val white = Color.WHITE
    icon(Ic.DOTS, width - PAD - 64f, 70f, 64f, white)
    val cx = width - 96f
    var y = h - 1010f
    y = sideItem(cx, y, Ic.THUMB, abbr(post.likes))
    y = sideItem(cx, y, Ic.THUMB, null, flip = true)
    y = sideItem(cx, y, Ic.COMMENT, abbr(post.comments))
    y = sideItem(cx, y, Ic.SHARE, post.labels.share)
    audioSquare(cx, y + 10f, avatar)

    val name = "@" + user()
    val by = h - 470f
    avatarCircle(PAD, by, avatar, false, 76f)
    text(name, PAD + 100f, by + 52f, 42f, white, true)
    val nx = PAD + 100f + measure(name, 42f, true) + 24f
    pillText(nx, by + 4f, 68f, post.labels.subscribe, 34f, white, 0xFF0F0F0F.toInt(), padX = 30f)
    rich(post.body, by + 96f, 44f, white, white, maxWidth = width - PAD - 220f)
    return h
}

private fun CardPainter.twitchStreamLayout(images: List<Bitmap>, avatar: Bitmap?): Float {
    val s = skin(post.dark, TW_LIGHT, TW_DARK)
    val purple = if (post.dark) 0xFFBF94FF.toInt() else 0xFF772CE8.toInt()
    canvas.drawColor(s.bg)
    val rect = RectF(0f, 0f, width, width * 0.5625f)
    val shot = images.firstOrNull()
    if (shot != null) {
        coverRect(rect, shot, 0f)
    } else {
        fill.color = 0xFF0E0E10.toInt()
        canvas.drawRect(rect, fill)
    }
    liveBadge(PAD, PAD)
    if (post.views > 0) {
        val label = abbr(post.views)
        val paint = textPaint(34f, true, Color.WHITE)
        val w = paint.measureText(label) + 82f
        fill.color = 0xB3000000.toInt()
        canvas.drawRoundRect(RectF(PAD, rect.bottom - 98f, PAD + w, rect.bottom - 34f), 14f, 14f, fill)
        fill.color = 0xFFE91916.toInt()
        canvas.drawCircle(PAD + 30f, rect.bottom - 66f, 10f, fill)
        canvas.drawText(label, PAD + 56f, rect.bottom - 54f, paint)
    }
    var y = rect.bottom + 34f
    stroke.color = 0xFFE91916.toInt()
    stroke.strokeWidth = 6f
    canvas.drawCircle(PAD + 60f, y + 60f, 66f, stroke)
    avatarCircle(PAD + 6f, y + 6f, avatar, false, 108f)
    val tx = PAD + 150f
    text(post.displayName, tx, y + 44f, 44f, s.ink, true)
    if (post.verified) badge(tx + measure(post.displayName, 44f, true) + 12f, y + 44f, 44f, 0xFF9146FF.toInt())
    var ny = rich(post.body, y + 56f, 38f, s.ink, purple, x = tx, maxWidth = width - tx - PAD)
    if (post.location.isNotBlank()) {
        text(post.location, tx, ny + 34f, 36f, purple, true)
        ny += 56f
    }
    y = max(ny, y + 130f) + 12f
    centeredButton(PAD, y, content, 88f, post.labels.follow, 0xFF9146FF.toInt(), Color.WHITE)
    return y + 88f + 36f
}
