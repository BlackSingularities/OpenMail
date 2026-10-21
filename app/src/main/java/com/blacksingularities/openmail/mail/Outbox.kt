package com.blacksingularities.openmail.mail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.Account
import com.blacksingularities.openmail.data.Address
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.data.FolderRole
import com.blacksingularities.openmail.data.MessageBody
import com.blacksingularities.openmail.data.MessageHeader
import com.blacksingularities.openmail.data.OpType
import com.blacksingularities.openmail.data.PendingOp
import com.blacksingularities.openmail.sync.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.activation.DataHandler
import javax.mail.Flags
import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

/** Załącznik dodany w edytorze (plik z urządzenia albo przekazany z oryginału). */
data class OutgoingAttachment(
    val name: String,
    val mime: String,
    val size: Long,
    val uri: Uri? = null,
    val file: java.io.File? = null,
)

enum class ComposeMode { NEW, REPLY, REPLY_ALL, FORWARD, DRAFT }

/** Treść edytowanej wiadomości. */
data class Draft(
    val accountId: Long,
    val to: List<Address> = emptyList(),
    val cc: List<Address> = emptyList(),
    val bcc: List<Address> = emptyList(),
    val subject: String = "",
    val body: String = "",
    /** Cytowana treść oryginału (HTML) dołączana pod tekstem. */
    val quotedHtml: String = "",
    val quoteHeader: String = "",
    val attachments: List<OutgoingAttachment> = emptyList(),
    val inReplyTo: String = "",
    val references: String = "",
    /** Wiadomość, na którą odpowiadamy (oznaczana jako „odpowiedziano”). */
    val replyToMessageId: Long = 0,
    /** Poprzednia wersja robocza na serwerze – usuwana po wysłaniu lub ponownym zapisie. */
    val draftMessageId: Long = 0,
    val mode: ComposeMode = ComposeMode.NEW,
)

/** Wiadomość czekająca na wysłanie (okno „Cofnij”). */
data class Sending(val id: Long, val draft: Draft, val until: Long)

object Outbox {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var sending by mutableStateOf<Sending?>(null)
        private set

    /** Szkic przywrócony po cofnięciu wysyłki – edytor otwiera się ponownie. */
    var restored by mutableStateOf<Draft?>(null)

    private var job: Job? = null

    fun send(context: Context, draft: Draft) {
        val app = context.applicationContext
        val delayMs = Prefs.undoSendSeconds * 1000L
        val s = Sending(System.nanoTime(), draft, System.currentTimeMillis() + delayMs)
        sending = s
        job = scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (sending?.id != s.id) return@launch
            sending = null
            sendNow(app, draft)
        }
    }

    fun undo() {
        val s = sending ?: return
        job?.cancel()
        sending = null
        restored = s.draft
    }

    private suspend fun sendNow(context: Context, d: Draft) {
        val a = Db.account(d.accountId) ?: return
        runCatching {
            val msg = build(context, a, d, forDraft = false)
            MailClient.send(a, msg)
            Db.touchContacts(d.to + d.cc + d.bcc)
            if (a.saveSent) {
                Db.folderByRole(a.id, FolderRole.SENT)?.let { sent ->
                    runCatching { MailClient.append(a, sent, msg, Flags(Flags.Flag.SEEN)) }
                }
            }
            if (d.replyToMessageId != 0L) {
                Db.setAnswered(listOf(d.replyToMessageId))
            }
            deleteOldDraft(a, d)
            toast(context, "Wysłano: ${d.subject.ifBlank { "(bez tematu)" }}")
            Db.folderByRole(a.id, FolderRole.SENT)?.let { Sync.refreshFolder(context, it.id) }
        }.onFailure { e ->
            // Nie gubimy treści: wersja robocza na serwerze i ponowne otwarcie edytora.
            runCatching { saveDraft(context, d) }
            restored = d
            toast(context, "Nie wysłano: ${e.mailMessage()}")
        }
    }

    private suspend fun deleteOldDraft(a: Account, d: Draft) {
        if (d.draftMessageId == 0L) return
        val old = Db.message(d.draftMessageId) ?: return
        val folder = Db.folder(old.folderId) ?: return
        Db.deleteMessages(listOf(old.id))
        val op = PendingOp(accountId = a.id, folderPath = folder.path, uid = old.uid, type = OpType.DELETE)
        runCatching { MailClient.apply(a, op) }.onFailure { Db.addOp(op) }
    }

    /** Zapis wersji roboczej w folderze „Wersje robocze” na serwerze. */
    suspend fun saveDraft(context: Context, d: Draft): Boolean = withContext(Dispatchers.IO) {
        val a = Db.account(d.accountId) ?: return@withContext false
        val folder = Db.folderByRole(a.id, FolderRole.DRAFTS) ?: return@withContext false
        val msg = build(context, a, d, forDraft = true)
        MailClient.append(a, folder, msg, Flags().apply { add(Flags.Flag.DRAFT); add(Flags.Flag.SEEN) })
        deleteOldDraft(a, d)
        Sync.refreshFolder(context, folder.id)
        true
    }

    private suspend fun toast(context: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    /** Buduje wiadomość MIME: tekst + HTML (z cytatem) i załączniki. */
    fun build(context: Context, a: Account, d: Draft, forDraft: Boolean): MimeMessage {
        val msg = MimeMessage(MailClient.composeSession())
        msg.setFrom(InternetAddress(a.email, a.displayName.ifBlank { null }, "UTF-8"))
        fun list(l: List<Address>) = l.map { InternetAddress(it.email, it.name.ifBlank { null }, "UTF-8") }.toTypedArray()
        if (d.to.isNotEmpty()) msg.setRecipients(Message.RecipientType.TO, list(d.to))
        if (d.cc.isNotEmpty()) msg.setRecipients(Message.RecipientType.CC, list(d.cc))
        if (d.bcc.isNotEmpty()) msg.setRecipients(Message.RecipientType.BCC, list(d.bcc))
        msg.setSubject(d.subject, "UTF-8")
        msg.sentDate = Date()
        msg.setHeader("User-Agent", "OpenMail")
        if (d.inReplyTo.isNotBlank()) msg.setHeader("In-Reply-To", d.inReplyTo)
        if (d.references.isNotBlank()) msg.setHeader("References", d.references)

        val signature = a.signature.takeIf { it.isNotBlank() && !forDraft && !d.body.contains(it) }
        val text = buildString {
            append(d.body)
            if (signature != null) append("\n\n-- \n").append(signature)
            if (d.quotedHtml.isNotBlank()) {
                append("\n\n").append(d.quoteHeader).append("\n")
                append(Mime.htmlToText(d.quotedHtml).lines().joinToString("\n") { "> $it" })
            }
        }
        val html = buildString {
            append("<div dir=\"auto\" style=\"font-family:sans-serif;font-size:14px\">")
            append(Mime.textToHtml(d.body))
            if (signature != null) append("<br><br>-- <br>").append(Mime.textToHtml(signature))
            append("</div>")
            if (d.quotedHtml.isNotBlank()) {
                append("<br><div>").append(Mime.textToHtml(d.quoteHeader)).append("</div>")
                append("<blockquote style=\"margin:8px 0;padding:8px 12px;background:#f3f4f7;border-radius:8px\">")
                append(d.quotedHtml)
                append("</blockquote>")
            }
        }
        val alternative = MimeMultipart("alternative").apply {
            addBodyPart(MimeBodyPart().apply { setText(text, "UTF-8", "plain") })
            addBodyPart(MimeBodyPart().apply { setText(html, "UTF-8", "html") })
        }
        if (d.attachments.isEmpty()) {
            msg.setContent(alternative)
        } else {
            val mixed = MimeMultipart("mixed")
            mixed.addBodyPart(MimeBodyPart().apply { setContent(alternative) })
            for (att in d.attachments) {
                val bytes = when {
                    att.file != null -> att.file.readBytes()
                    att.uri != null -> context.contentResolver.openInputStream(att.uri)?.use { it.readBytes() } ?: continue
                    else -> continue
                }
                mixed.addBodyPart(
                    MimeBodyPart().apply {
                        dataHandler = DataHandler(ByteArrayDataSource(bytes, att.mime.ifBlank { "application/octet-stream" }))
                        fileName = javax.mail.internet.MimeUtility.encodeText(att.name, "UTF-8", null)
                        disposition = MimeBodyPart.ATTACHMENT
                    },
                )
            }
            msg.setContent(mixed)
        }
        msg.saveChanges()
        return msg
    }

    // ---------- Odpowiedzi i przekazywanie ----------

    private val dateFmt = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.forLanguageTag("pl"))

    private fun prefixed(prefix: String, subject: String): String {
        val norm = subject.trim()
        return if (norm.startsWith(prefix, ignoreCase = true)) norm else "$prefix $norm"
    }

    fun reply(m: MessageHeader, body: MessageBody?, all: Boolean): Draft {
        val a = Db.account(m.accountId)
        val me = a?.email?.lowercase().orEmpty()
        val primary = m.replyTo.ifEmpty { listOf(m.from) }
        val fromMe = m.from.email.lowercase() == me
        val to = if (fromMe) m.to else primary
        val cc = if (all) (m.to + m.cc).filter { it.email.lowercase() != me && to.none { t -> t.email.equals(it.email, true) } } else emptyList()
        return Draft(
            accountId = m.accountId,
            to = to.distinctBy { it.email.lowercase() },
            cc = cc.distinctBy { it.email.lowercase() },
            subject = prefixed("Re:", m.subject),
            quotedHtml = if (Prefs.quoteOriginal) quoted(body) else "",
            quoteHeader = "W dniu ${dateFmt.format(Date(m.date))} ${m.from} napisał(a):",
            inReplyTo = m.messageId,
            references = (m.references + " " + m.messageId).trim(),
            replyToMessageId = m.id,
            mode = if (all) ComposeMode.REPLY_ALL else ComposeMode.REPLY,
        )
    }

    fun forward(m: MessageHeader, body: MessageBody?, files: List<OutgoingAttachment>): Draft = Draft(
        accountId = m.accountId,
        subject = prefixed("Fwd:", m.subject),
        quotedHtml = quoted(body),
        quoteHeader = buildString {
            append("---------- Przekazana wiadomość ----------\n")
            append("Od: ${m.from}\n")
            append("Data: ${dateFmt.format(Date(m.date))}\n")
            append("Temat: ${m.subject}\n")
            append("Do: ${m.to.joinToString(", ")}")
        },
        attachments = files,
        mode = ComposeMode.FORWARD,
    )

    private fun quoted(body: MessageBody?): String {
        if (body == null) return ""
        return if (body.html.isNotBlank()) Mime.sanitize(body.html).body().html() else Mime.textToHtml(body.text)
    }

    /** Wersja robocza z serwera otwarta do dalszej edycji. */
    fun fromDraft(m: MessageHeader, body: MessageBody?): Draft = Draft(
        accountId = m.accountId,
        to = m.to,
        cc = m.cc,
        subject = m.subject,
        body = body?.text?.ifBlank { Mime.htmlToText(body.html) }.orEmpty(),
        draftMessageId = m.id,
        inReplyTo = m.inReplyTo,
        references = m.references,
        mode = ComposeMode.DRAFT,
    )

    /** Metadane pliku wybranego z urządzenia. */
    fun describe(context: Context, uri: Uri): OutgoingAttachment {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "plik"
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return OutgoingAttachment(name, mime, size, uri = uri)
    }
}
