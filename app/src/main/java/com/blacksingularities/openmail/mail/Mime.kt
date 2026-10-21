package com.blacksingularities.openmail.mail

import android.util.Base64
import com.blacksingularities.openmail.data.Address
import com.blacksingularities.openmail.data.Attachment
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.ContentType
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility

/** Wynik przejścia po strukturze MIME: treść HTML i tekstowa oraz załączniki. */
class ParsedBody(
    val html: String,
    val text: String,
    val attachments: List<Attachment>,
)

object Mime {
    /** Największy obrazek osadzany w treści (cid:) – większe pokazujemy jako załączniki. */
    private const val INLINE_LIMIT = 1_500_000

    /**
     * Przechodzi po częściach wiadomości. Czyta tylko części tekstowe i obrazki osadzone,
     * załączniki są zapisywane jako metadane (pobierane później na żądanie).
     */
    fun parse(root: Part): ParsedBody {
        var html: String? = null
        var text: String? = null
        val attachments = mutableListOf<Attachment>()
        val inline = mutableMapOf<String, String>()

        fun walk(p: Part, path: String, inAlternative: Boolean) {
            val type = runCatching { ContentType(p.contentType) }.getOrNull()
            val base = type?.baseType?.lowercase() ?: "application/octet-stream"
            val disposition = runCatching { p.disposition }.getOrNull()?.lowercase()
            val fileName = runCatching { p.fileName }.getOrNull()?.let { decodeWord(it) }
            if (base.startsWith("multipart/")) {
                val mp = runCatching { p.content as? Multipart }.getOrNull() ?: return
                val alt = base == "multipart/alternative"
                for (i in 0 until mp.count) {
                    walk(mp.getBodyPart(i), if (path.isEmpty()) "${i + 1}" else "$path.${i + 1}", alt)
                }
                return
            }
            if (base == "message/rfc822" && disposition != Part.ATTACHMENT) {
                // Przekazana wiadomość: pokaż jako załącznik .eml.
                attachments += Attachment(fileName ?: "wiadomosc.eml", base, sizeOf(p), path)
                return
            }
            val isAttachment = disposition == Part.ATTACHMENT || (fileName != null && !base.startsWith("text/"))
            if (!isAttachment && base == "text/html") {
                val s = readText(p)
                html = if (html == null || inAlternative) s else html + s
                return
            }
            if (!isAttachment && base == "text/plain") {
                val s = readText(p)
                text = if (text == null || inAlternative) s else text + "\n\n" + s
                return
            }
            val cid = runCatching { (p as? javax.mail.internet.MimePart)?.contentID }.getOrNull()?.trim('<', '>', ' ').orEmpty()
            val size = sizeOf(p)
            if (cid.isNotEmpty() && base.startsWith("image/") && size in 0..INLINE_LIMIT) {
                runCatching {
                    val bytes = p.inputStream.use { it.readBytes() }
                    inline[cid] = "data:$base;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                if (disposition != Part.ATTACHMENT) {
                    attachments += Attachment(fileName ?: "obraz-$cid", base, size, path, cid, inline = true)
                    return
                }
            }
            attachments += Attachment(fileName ?: defaultName(base, attachments.size + 1), base, size, path, cid)
        }

        walk(root, "", false)
        var finalHtml = html.orEmpty()
        val referenced = inline.keys.filter { finalHtml.contains("cid:$it", ignoreCase = true) }.toSet()
        for (cid in referenced) {
            finalHtml = finalHtml.replace("cid:$cid", inline.getValue(cid), ignoreCase = true)
        }
        // Osadzone obrazki, których treść HTML nie używa, pokazujemy jak zwykłe załączniki.
        val visible = attachments.map { a -> if (a.inline && a.contentId !in referenced) a.copy(inline = false) else a }
        return ParsedBody(finalHtml, text.orEmpty(), visible)
    }

    /** Odnajduje część MIME po ścieżce „1.2.1”. */
    fun partAt(root: Part, path: String): Part? {
        if (path.isEmpty()) return root
        var p: Part = root
        for (seg in path.split('.')) {
            val i = seg.toIntOrNull() ?: return null
            val mp = p.content as? Multipart ?: return null
            if (i < 1 || i > mp.count) return null
            p = mp.getBodyPart(i - 1)
        }
        return p
    }

    private fun sizeOf(p: Part): Long = runCatching { p.size.toLong() }.getOrDefault(-1L).let {
        // Rozmiar części w base64 jest o ok. 1/3 większy od pliku.
        if (it > 0 && runCatching { (p as? javax.mail.internet.MimePart)?.encoding }.getOrNull().equals("base64", true)) it * 3 / 4 else it
    }

    private fun readText(p: Part): String = runCatching {
        when (val c = p.content) {
            is String -> c
            is InputStream -> {
                val charset = runCatching { ContentType(p.contentType).getParameter("charset") }.getOrNull()
                val bytes = c.use { s -> ByteArrayOutputStream().also { s.copyTo(it) }.toByteArray() }
                String(bytes, runCatching { charset(MimeUtility.javaCharset(charset ?: "UTF-8")) }.getOrDefault(Charsets.UTF_8))
            }
            else -> c.toString()
        }
    }.getOrDefault("")

    private fun defaultName(mime: String, n: Int): String {
        val ext = when (mime) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "text/calendar" -> "ics"
            "text/plain" -> "txt"
            else -> mime.substringAfter('/').substringBefore('+').take(8)
        }
        return "zalacznik-$n.$ext"
    }

    fun decodeWord(s: String): String = runCatching { MimeUtility.decodeText(s) }.getOrDefault(s)

    fun address(a: javax.mail.Address?): Address? {
        val ia = a as? InternetAddress ?: return null
        val email = ia.address ?: return null
        return Address(email, ia.personal?.let { decodeWord(it) }.orEmpty())
    }

    fun addresses(list: Array<javax.mail.Address>?): List<Address> = list?.mapNotNull { address(it) }.orEmpty()

    /** Tekst z HTML (podgląd, odpowiedź w formacie tekstowym). */
    fun htmlToText(html: String): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parse(html)
        doc.select("style, script, head, title").remove()
        doc.select("br").append("\\n")
        doc.select("p, div, tr, li, h1, h2, h3, h4, blockquote").prepend("\\n")
        return doc.text().replace("\\n", "\n").lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** Krótki podgląd na liście: bez cytatów i nadmiarowych odstępów. */
    fun preview(text: String, html: String): String {
        val source = text.ifBlank { htmlToText(html) }
        return source.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(">") }
            .takeWhile { !(it.startsWith("W dniu") && it.endsWith(":")) && !(it.startsWith("On ") && it.endsWith("wrote:")) && it != "--" }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .take(240)
    }

    /**
     * Treść HTML gotowa do bezpiecznego wyświetlenia: bez skryptów, ramek, formularzy i obsługi
     * zdarzeń. Linki otwierają się poza aplikacją.
     */
    fun sanitize(html: String): Document {
        val doc = Jsoup.parse(html)
        doc.select("script, iframe, frame, frameset, object, embed, applet, form, input, button, textarea, select, meta[http-equiv], base, link[rel=import]").remove()
        doc.getAllElements().forEach { el ->
            el.attributes().asList().map { it.key }.filter { it.startsWith("on", ignoreCase = true) }.forEach { el.removeAttr(it) }
            listOf("href", "src", "action").forEach { attr ->
                if (el.attr(attr).trim().startsWith("javascript:", ignoreCase = true)) el.removeAttr(attr)
            }
        }
        return doc
    }

    fun hasRemoteImages(html: String): Boolean {
        if (html.isBlank()) return false
        val doc = Jsoup.parse(html)
        return doc.select("img[src^=http], img[src^=//], [background^=http]").isNotEmpty() ||
            Regex("url\\(\\s*['\"]?https?://", RegexOption.IGNORE_CASE).containsMatchIn(html)
    }

    /** Temat bez przedrostków odpowiedzi i przekazania – klucz wątku. */
    fun normalizeSubject(subject: String): String {
        var s = subject.trim()
        val prefix = Regex("^(re|fw|fwd|odp|pd|aw|wg|sv|vs|tr|rv|r|antw|ref)(\\[\\d+\\])?\\s*:\\s*", RegexOption.IGNORE_CASE)
        while (true) {
            val n = prefix.replace(s, "").trim()
            if (n == s) break
            s = n
        }
        return s.lowercase().replace(Regex("\\s+"), " ")
    }

    /** Zamienia zwykły tekst na prosty HTML (akapity, linki, cytaty). */
    fun textToHtml(text: String): String {
        val esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val linked = esc.replace(Regex("(https?://[^\\s<]+)")) { "<a href=\"${it.value}\">${it.value}</a>" }
        return linked.lines().joinToString("<br>\n")
    }
}
