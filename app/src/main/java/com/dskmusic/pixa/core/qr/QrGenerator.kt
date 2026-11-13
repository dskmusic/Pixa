package com.dskmusic.pixa.core.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.dskmusic.pixa.core.bitmap.BitmapStore
import com.dskmusic.pixa.core.bitmap.BitmapUtils
import com.dskmusic.pixa.core.model.QrErrorLevel
import com.dskmusic.pixa.core.model.QrPayloadKind
import com.dskmusic.pixa.core.model.QrSpec
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import kotlin.math.max

/**
 * Generador de codigos QR como capa (requisito 12).
 *
 * Se usa solo la parte de codificacion de ZXing: no se incluye el escaner, asi que la app
 * no necesita camara ni dependencias extra.
 *
 * Se llama a Encoder directamente en vez de a QRCodeWriter porque asi se obtiene la
 * matriz a resolucion de modulo, que es lo que permite dibujar modulos redondeados, el
 * margen exacto y el hueco del logo central.
 */
object QrGenerator {

    class QrException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Texto que acaba codificado, segun el tipo de contenido elegido. */
    fun payload(spec: QrSpec): String {
        val f = spec.fields
        return when (spec.kind) {
            QrPayloadKind.URL -> f["url"].orEmpty().trim()
            QrPayloadKind.TEXT -> f["text"].orEmpty()
            QrPayloadKind.PHONE -> "tel:" + f["phone"].orEmpty().trim()
            QrPayloadKind.EMAIL -> buildString {
                append("mailto:").append(f["email"].orEmpty().trim())
                val subject = f["subject"].orEmpty()
                val body = f["body"].orEmpty()
                val params = buildList {
                    if (subject.isNotEmpty()) add("subject=" + encode(subject))
                    if (body.isNotEmpty()) add("body=" + encode(body))
                }
                if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
            }
            QrPayloadKind.WIFI -> buildString {
                // Formato de facto que reconocen Android e iOS.
                append("WIFI:T:").append(f["security"]?.ifEmpty { "WPA" } ?: "WPA")
                append(";S:").append(escapeWifi(f["ssid"].orEmpty()))
                val pass = f["password"].orEmpty()
                if (pass.isNotEmpty()) append(";P:").append(escapeWifi(pass))
                if (f["hidden"] == "true") append(";H:true")
                append(";;")
            }
            QrPayloadKind.VCARD -> buildString {
                append("BEGIN:VCARD\nVERSION:3.0\n")
                val name = f["name"].orEmpty()
                if (name.isNotEmpty()) {
                    append("N:").append(name).append("\n")
                    append("FN:").append(name).append("\n")
                }
                f["org"]?.takeIf { it.isNotEmpty() }?.let { append("ORG:").append(it).append("\n") }
                f["title"]?.takeIf { it.isNotEmpty() }?.let { append("TITLE:").append(it).append("\n") }
                f["phone"]?.takeIf { it.isNotEmpty() }?.let { append("TEL:").append(it).append("\n") }
                f["email"]?.takeIf { it.isNotEmpty() }?.let { append("EMAIL:").append(it).append("\n") }
                f["url"]?.takeIf { it.isNotEmpty() }?.let { append("URL:").append(it).append("\n") }
                f["address"]?.takeIf { it.isNotEmpty() }?.let {
                    append("ADR:;;").append(it).append("\n")
                }
                f["note"]?.takeIf { it.isNotEmpty() }?.let { append("NOTE:").append(it).append("\n") }
                append("END:VCARD")
            }
        }
    }

    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** En el formato WIFI hay que escapar los separadores. */
    private fun escapeWifi(s: String): String =
        s.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")

    private fun levelOf(level: QrErrorLevel): ErrorCorrectionLevel = when (level) {
        QrErrorLevel.L -> ErrorCorrectionLevel.L
        QrErrorLevel.M -> ErrorCorrectionLevel.M
        QrErrorLevel.Q -> ErrorCorrectionLevel.Q
        QrErrorLevel.H -> ErrorCorrectionLevel.H
    }

    /**
     * Dibuja el codigo. Lanza [QrException] si el contenido esta vacio o no cabe en un QR
     * (demasiados datos para el nivel de correccion elegido).
     */
    fun generate(spec: QrSpec, store: BitmapStore? = null): Bitmap {
        val content = payload(spec)
        if (content.isBlank()) throw QrException("Contenido de QR vacio")

        val code = try {
            Encoder.encode(
                content,
                levelOf(spec.errorLevel),
                mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"),
            )
        } catch (e: Exception) {
            throw QrException("No se ha podido codificar el contenido", e)
        }
        val matrix = code.matrix ?: throw QrException("El codificador no ha devuelto matriz")

        val margin = spec.marginModules.coerceIn(0, 16)
        val modules = matrix.width
        val total = modules + margin * 2
        // El lado se redondea a un multiplo exacto de modulos para que no haya bandas
        // de distinto grosor por redondeo.
        val moduleSize = max(1, spec.sizePx / total)
        val side = moduleSize * total

        val out = BitmapUtils.create(side, side)
        val canvas = Canvas(out)
        if (Color.alpha(spec.bgColor) > 0) canvas.drawColor(spec.bgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.fgColor }
        val ms = moduleSize.toFloat()
        // Solapa minima entre modulos cuadrados: tapa las costuras del antialias.
        val bleed = if (spec.roundedModules) 0f else 0.5f

        for (y in 0 until matrix.height) {
            for (x in 0 until modules) {
                if (matrix.get(x, y).toInt() != 1) continue
                val left = (x + margin) * ms
                val top = (y + margin) * ms
                if (spec.roundedModules) {
                    // Puntos redondos, un poco mayores que el modulo para que los
                    // vecinos se toquen y el codigo siga siendo legible.
                    canvas.drawCircle(left + ms / 2f, top + ms / 2f, ms * 0.56f, paint)
                } else {
                    canvas.drawRect(left, top, left + ms + bleed, top + ms + bleed, paint)
                }
            }
        }

        drawLogo(canvas, out, spec, store)
        return out
    }

    /**
     * Logo central opcional. Se despeja un cuadrado del 24 % y se avisa en la interfaz de
     * que conviene subir el nivel de correccion: la redundancia del QR tolera esta
     * perdida con nivel Q o H.
     */
    private fun drawLogo(canvas: Canvas, out: Bitmap, spec: QrSpec, store: BitmapStore?) {
        val logo = store?.get(spec.logoBitmapId) ?: return
        val side = out.width
        val hole = side * 0.24f
        val cx = side / 2f
        val cy = side / 2f
        val holeRect = RectF(cx - hole / 2f, cy - hole / 2f, cx + hole / 2f, cy + hole / 2f)

        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (Color.alpha(spec.bgColor) > 0) spec.bgColor else Color.WHITE
        }
        canvas.drawRoundRect(holeRect, hole * 0.18f, hole * 0.18f, clearPaint)

        // El logo se encaja dentro del hueco conservando su proporcion.
        val pad = hole * 0.08f
        val dst = RectF(
            holeRect.left + pad, holeRect.top + pad,
            holeRect.right - pad, holeRect.bottom - pad,
        )
        val k = minOf(dst.width() / logo.width, dst.height() / logo.height)
        val w = logo.width * k
        val h = logo.height * k
        val fitted = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        canvas.drawBitmap(
            logo,
            Rect(0, 0, logo.width, logo.height),
            fitted,
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
    }
}
