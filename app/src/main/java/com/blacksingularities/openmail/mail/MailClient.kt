package com.blacksingularities.openmail.mail

import android.content.Context
import com.blacksingularities.openmail.data.Account
import com.blacksingularities.openmail.data.Attachment
import com.blacksingularities.openmail.data.AuthType
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.data.Folder
import com.blacksingularities.openmail.data.FolderRole
import com.blacksingularities.openmail.data.MessageBody
import com.blacksingularities.openmail.data.MessageHeader
import com.blacksingularities.openmail.data.OpType
import com.blacksingularities.openmail.data.PendingOp
import com.blacksingularities.openmail.data.Security
import com.blacksingularities.openmail.data.ServerConfig
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPMessage
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.MimeMessage
import javax.mail.search.BodyTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.RecipientStringTerm
import javax.mail.search.SubjectTerm

/** Czytelny opis błędu połączenia. */
fun Throwable.mailMessage(): String {
    val root = generateSequence(this) { it.cause }.last()
    return when {
        this is AuthenticationFailedException || root is AuthenticationFailedException ->
            "Nieprawidłowy login lub hasło" + (message?.takeIf { it.isNotBlank() && it.length < 160 }?.let { " ($it)" } ?: "")
        root is java.net.UnknownHostException -> "Nie znaleziono serwera ${root.message.orEmpty()}"
        root is java.net.SocketTimeoutException -> "Serwer nie odpowiada (przekroczony czas)"
        root is java.net.ConnectException -> "Brak połączenia z serwerem"
        root is javax.net.ssl.SSLException -> "Błąd szyfrowania połączenia: ${root.message.orEmpty()}"
        else -> (message ?: root.message ?: javaClass.simpleName).take(300)
    }
}

/**
 * IMAP i SMTP przez Jakarta Mail. Połączenia IMAP są utrzymywane per konto i używane
 * po kolei (mutex), a zerwane – nawiązywane ponownie.
 */
object MailClient {
    private const val TIMEOUT = 20_000

    private val stores = ConcurrentHashMap<Long, IMAPStore>()
    private val locks = ConcurrentHashMap<Long, Mutex>()

    private fun props(prefix: String, s: ServerConfig, oauth: Boolean) = Properties().apply {
        put("mail.$prefix.host", s.host)
        put("mail.$prefix.port", s.port.toString())
        put("mail.$prefix.connectiontimeout", TIMEOUT.toString())
        put("mail.$prefix.timeout", (TIMEOUT * 3).toString())
        put("mail.$prefix.writetimeout", (TIMEOUT * 3).toString())
        when (s.security) {
            Security.SSL -> {
                put("mail.$prefix.ssl.enable", "true")
                put("mail.$prefix.ssl.checkserveridentity", "true")
            }
            Security.STARTTLS -> {
                put("mail.$prefix.starttls.enable", "true")
                put("mail.$prefix.starttls.required", "true")
                put("mail.$prefix.ssl.checkserveridentity", "true")
            }
            Security.NONE -> Unit
        }
        if (oauth) {
            put("mail.$prefix.auth.mechanisms", "XOAUTH2")
            put("mail.$prefix.auth.login.disable", "true")
            put("mail.$prefix.auth.plain.disable", "true")
        }
    }

    /** Hasło albo świeży token dostępu OAuth. */
    private fun credential(a: Account): String = when (a.auth) {
        AuthType.PASSWORD -> a.secret
        else -> OAuth.accessToken(a.auth, a.email, a.secret)
    }

    private fun imapSession(a: Account): Session {
        val p = props("imap", a.imap, a.auth != AuthType.PASSWORD).apply {
            put("mail.imap.partialfetch", "true")
            put("mail.imap.fetchsize", "262144")
            put("mail.imap.peek", "true")
            put("mail.imap.compress.enable", "true")
        }
        return Session.getInstance(p)
    }

    private fun smtpSession(a: Account): Session {
        val p = props("smtp", a.smtp, a.auth != AuthType.PASSWORD).apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.localhost", "openmail.localdomain")
        }
        return Session.getInstance(p)
    }

    fun openStore(a: Account): IMAPStore {
        val store = imapSession(a).getStore("imap") as IMAPStore
        try {
            store.connect(a.imap.host, a.imap.port, a.imap.username, credential(a))
        } catch (e: AuthenticationFailedException) {
            if (a.auth != AuthType.PASSWORD) OAuth.invalidate(a.email)
            throw e
        }
        return store
    }

    /** Wykonuje [block] na połączeniu IMAP konta (z jednym ponowieniem przy zerwaniu). */
    suspend fun <T> withStore(a: Account, block: (IMAPStore) -> T): T {
        val lock = locks.getOrPut(a.id) { Mutex() }
        return lock.withLock {
            var lastError: Exception? = null
            repeat(2) { attempt ->
                val store = stores[a.id]?.takeIf { it.isConnected } ?: openStore(a).also { stores[a.id] = it }
                try {
                    return@withLock block(store)
                } catch (e: AuthenticationFailedException) {
                    throw e
                } catch (e: MessagingException) {
                    lastError = e
                    runCatching { store.close() }
                    stores.remove(a.id)
                    if (attempt == 1) throw e
                }
            }
            throw lastError ?: IllegalStateException()
        }
    }

    fun disconnect(accountId: Long) {
        stores.remove(accountId)?.let { runCatching { it.close() } }
    }

    fun disconnectAll() {
        stores.keys.toList().forEach { disconnect(it) }
    }

    /** Sprawdzenie ustawień przed zapisaniem konta: logowanie do IMAP i SMTP. */
    fun test(a: Account) {
        val store = openStore(a)
        runCatching { store.close() }
        val t = smtpSession(a).getTransport("smtp")
        t.connect(a.smtp.host, a.smtp.port, a.smtp.username, credential(a))
        t.close()
    }

    // ---------- Foldery ----------

    private fun roleOf(f: IMAPFolder): FolderRole {
        if (f.fullName.equals("INBOX", true)) return FolderRole.INBOX
        val attrs = runCatching { f.attributes.map { it.lowercase() } }.getOrDefault(emptyList())
        when {
            "\\sent" in attrs -> return FolderRole.SENT
            "\\drafts" in attrs -> return FolderRole.DRAFTS
            "\\trash" in attrs -> return FolderRole.TRASH
            "\\junk" in attrs -> return FolderRole.JUNK
            "\\archive" in attrs -> return FolderRole.ARCHIVE
            "\\all" in attrs -> return FolderRole.ALL
            "\\flagged" in attrs -> return FolderRole.FLAGGED
        }
        val n = f.name.lowercase()
        return when {
            n in setOf("sent", "sent items", "sent messages", "wysłane", "wyslane", "elementy wysłane", "gesendet", "envoyés") -> FolderRole.SENT
            n in setOf("drafts", "draft", "robocze", "kopie robocze", "wersje robocze", "szkice", "entwürfe", "brouillons") -> FolderRole.DRAFTS
            n in setOf("trash", "deleted", "deleted items", "deleted messages", "kosz", "elementy usunięte", "usunięte", "papierkorb", "corbeille", "bin") -> FolderRole.TRASH
            n in setOf("spam", "junk", "junk e-mail", "junk email", "wiadomości-śmieci", "bulk mail") -> FolderRole.JUNK
            n in setOf("archive", "archives", "archiwum") -> FolderRole.ARCHIVE
            else -> FolderRole.OTHER
        }
    }

    /** Pobiera listę folderów z serwera i zapisuje ją lokalnie. */
    suspend fun syncFolders(a: Account) = withStore(a) { store ->
        val list = store.defaultFolder.list("*").filterIsInstance<IMAPFolder>()
        val paths = mutableListOf<String>()
        val seenRoles = mutableSetOf<FolderRole>()
        // Najpierw foldery z atrybutami SPECIAL-USE, by heurystyka nazw nie dublowała ról.
        val withRoles = list.map { it to roleOf(it) }
        for ((f, role0) in withRoles.sortedBy { if (it.second == FolderRole.OTHER) 1 else 0 }) {
            val selectable = (f.type and javax.mail.Folder.HOLDS_MESSAGES) != 0
            var role = role0
            if (role != FolderRole.OTHER && role != FolderRole.INBOX && role in seenRoles) role = FolderRole.OTHER
            seenRoles += role
            val sep = runCatching { f.separator }.getOrDefault('/')
            val existing = Db.folderByPath(a.id, f.fullName)
            Db.upsertFolder(
                Folder(
                    accountId = a.id,
                    path = f.fullName,
                    name = displayName(f.name, role),
                    role = role,
                    separator = sep,
                    selectable = selectable,
                    sync = existing?.sync ?: (role in setOf(FolderRole.INBOX, FolderRole.SENT, FolderRole.DRAFTS)),
                ),
            )
            paths += f.fullName
        }
        if (paths.isNotEmpty()) Db.deleteFoldersExcept(a.id, paths)
        Db.notifyChanged()
    }

    private fun displayName(name: String, role: FolderRole) = when (role) {
        FolderRole.INBOX -> "Odebrane"
        FolderRole.SENT -> if (name.equals("sent", true) || name.contains("sent", true)) "Wysłane" else name
        FolderRole.DRAFTS -> if (name.contains("draft", true)) "Wersje robocze" else name
        FolderRole.TRASH -> if (name.contains("trash", true) || name.contains("deleted", true) || name.equals("bin", true)) "Kosz" else name
        FolderRole.JUNK -> if (name.contains("junk", true) || name.equals("spam", true)) "Spam" else name
        FolderRole.ARCHIVE -> if (name.contains("archive", true)) "Archiwum" else name
        FolderRole.ALL -> if (name.contains("all", true)) "Wszystkie wiadomości" else name
        FolderRole.FLAGGED -> if (name.contains("starred", true) || name.contains("flagged", true)) "Oznaczone gwiazdką" else name
        FolderRole.OTHER -> name
    }

    // ---------- Wiadomości ----------

    /**
     * Synchronizacja folderu: nowe nagłówki (do [window] najnowszych), zmiany flag i usunięcia.
     * Zwraca nowe wiadomości (do powiadomień). [bodies] – ile najnowszych treści pobrać od razu.
     */
    suspend fun syncFolder(a: Account, folder: Folder, window: Int, bodies: Int): List<MessageHeader> = withStore(a) { store ->
        val f = store.getFolder(folder.path) as IMAPFolder
        f.open(javax.mail.Folder.READ_ONLY)
        try {
            val validity = f.uidValidity
            if (folder.uidValidity != 0L && folder.uidValidity != validity) Db.clearFolder(folder.id)
            Db.setUidValidity(folder.id, validity)
            val count = f.messageCount
            val local = Db.localFlags(folder.id)
            val fresh = mutableListOf<MessageHeader>()
            if (count > 0) {
                val start = maxOf(1, count - window + 1)
                val msgs = f.getMessages(start, count)
                // Najpierw same UID i flagi – tanie także dla dużych okien.
                f.fetch(msgs, FetchProfile().apply { add(UIDFolder.FetchProfileItem.UID); add(FetchProfile.Item.FLAGS) })
                val serverUids = HashSet<Long>()
                val newMsgs = mutableListOf<IMAPMessage>()
                val flagChanges = mutableMapOf<Long, Triple<Boolean, Boolean, Boolean>>()
                for (m in msgs) {
                    val im = m as IMAPMessage
                    val uid = f.getUID(im)
                    serverUids += uid
                    val seen = im.isSet(Flags.Flag.SEEN)
                    val flagged = im.isSet(Flags.Flag.FLAGGED)
                    val answered = im.isSet(Flags.Flag.ANSWERED)
                    val old = local[uid]
                    if (old == null) {
                        if (!im.isSet(Flags.Flag.DELETED)) newMsgs += im
                    } else if (old.first != seen || old.second != flagged) {
                        flagChanges[uid] = Triple(seen, flagged, answered)
                    }
                }
                Db.updateFlags(folder.id, flagChanges)
                // Usunięte na serwerze (w obrębie okna synchronizacji).
                val minUid = serverUids.minOrNull() ?: 0L
                val gone = local.keys.filter { it >= minUid && it !in serverUids }
                Db.deleteUids(folder.id, gone)
                if (newMsgs.isNotEmpty()) {
                    val arr = newMsgs.toTypedArray<Message>()
                    f.fetch(
                        arr,
                        FetchProfile().apply {
                            add(FetchProfile.Item.ENVELOPE)
                            add(FetchProfile.Item.CONTENT_INFO)
                            add(FetchProfile.Item.SIZE)
                            add("References")
                            add("In-Reply-To")
                        },
                    )
                    val headers = newMsgs.map { header(a, folder, f, it) }
                    Db.insertMessages(headers)
                    fresh += headers
                    // Treść najnowszych (podgląd na liście i szybkie otwieranie).
                    newMsgs.sortedByDescending { f.getUID(it) }.take(bodies).forEach { im ->
                        val uid = f.getUID(im)
                        val saved = Db.messageByUid(folder.id, uid) ?: return@forEach
                        if (im.size in 0..400_000) runCatching { storeBody(saved, im) }
                    }
                }
            } else if (local.isNotEmpty()) {
                Db.clearFolder(folder.id)
            }
            Db.trimFolder(folder.id, maxOf(window, 50))
            Db.refreshUnread(folder.id)
            Db.setFolderSynced(folder.id, count)
            Db.notifyChanged()
            fresh.mapNotNull { Db.messageByUid(folder.id, it.uid) }
        } finally {
            runCatching { f.close(false) }
        }
    }

    private fun header(a: Account, folder: Folder, f: IMAPFolder, m: IMAPMessage): MessageHeader {
        val subject = runCatching { m.subject }.getOrNull().orEmpty()
        val from = runCatching { m.from?.firstOrNull() }.getOrNull()?.let { Mime.address(it) }
            ?: com.blacksingularities.openmail.data.Address("", "(brak nadawcy)")
        val refs = runCatching { m.getHeader("References")?.joinToString(" ") }.getOrNull().orEmpty()
        val inReplyTo = runCatching { m.getHeader("In-Reply-To")?.firstOrNull() }.getOrNull().orEmpty()
        val type = runCatching { m.contentType.lowercase() }.getOrDefault("")
        val hasAtt = type.startsWith("multipart/mixed") || type.startsWith("multipart/report") ||
            (type.startsWith("application/") || type.startsWith("image/") || type.startsWith("audio/") || type.startsWith("video/"))
        val norm = Mime.normalizeSubject(subject)
        return MessageHeader(
            accountId = a.id,
            folderId = folder.id,
            uid = f.getUID(m),
            messageId = runCatching { m.messageID }.getOrNull().orEmpty(),
            inReplyTo = inReplyTo,
            references = refs,
            subject = subject,
            from = from,
            to = Mime.addresses(runCatching { m.getRecipients(Message.RecipientType.TO) }.getOrNull()),
            cc = Mime.addresses(runCatching { m.getRecipients(Message.RecipientType.CC) }.getOrNull()),
            replyTo = Mime.addresses(runCatching { m.replyTo }.getOrNull()).filter { it.email != from.email },
            date = (runCatching { m.sentDate ?: m.receivedDate }.getOrNull()?.time ?: System.currentTimeMillis())
                .coerceAtMost(System.currentTimeMillis() + 86_400_000L),
            seen = m.isSet(Flags.Flag.SEEN),
            flagged = m.isSet(Flags.Flag.FLAGGED),
            answered = m.isSet(Flags.Flag.ANSWERED),
            draft = m.isSet(Flags.Flag.DRAFT) || folder.role == FolderRole.DRAFTS,
            hasAttachments = hasAtt,
            size = runCatching { m.size }.getOrDefault(0),
            threadKey = if (norm.isNotEmpty()) norm else runCatching { m.messageID }.getOrNull().orEmpty(),
        )
    }

    private fun storeBody(h: MessageHeader, m: Message): MessageBody {
        val parsed = Mime.parse(m)
        val body = MessageBody(
            messageId = h.id,
            html = parsed.html,
            text = parsed.text,
            attachments = parsed.attachments,
            hasRemoteImages = Mime.hasRemoteImages(parsed.html),
        )
        Db.saveBody(body)
        Db.setPreview(h.id, Mime.preview(parsed.text, parsed.html), parsed.attachments.any { !it.inline })
        return body
    }

    /** Treść wiadomości: z bazy albo pobrana z serwera. */
    suspend fun loadBody(a: Account, h: MessageHeader): MessageBody {
        Db.body(h.id)?.let { return it }
        val folder = Db.folder(h.folderId) ?: error("Folder nie istnieje")
        return withStore(a) { store ->
            val f = store.getFolder(folder.path) as IMAPFolder
            f.open(javax.mail.Folder.READ_ONLY)
            try {
                val m = f.getMessageByUID(h.uid) ?: error("Wiadomość została usunięta z serwera")
                storeBody(h, m).also { Db.notifyChanged() }
            } finally {
                runCatching { f.close(false) }
            }
        }
    }

    /** Pobiera załącznik do pamięci podręcznej (raz) i zwraca plik. */
    suspend fun downloadAttachment(context: Context, a: Account, h: MessageHeader, att: Attachment): File {
        val dir = File(context.cacheDir, "attachments/${h.id}").apply { mkdirs() }
        val safe = att.name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").ifBlank { "zalacznik" }
        val target = File(dir, safe)
        if (target.exists() && target.length() > 0) return target
        val folder = Db.folder(h.folderId) ?: error("Folder nie istnieje")
        return withStore(a) { store ->
            val f = store.getFolder(folder.path) as IMAPFolder
            f.open(javax.mail.Folder.READ_ONLY)
            try {
                val m = f.getMessageByUID(h.uid) ?: error("Wiadomość została usunięta z serwera")
                val part = Mime.partAt(m, att.part) ?: error("Nie znaleziono załącznika")
                val tmp = File(dir, "$safe.part")
                if (att.mime == "message/rfc822") {
                    tmp.outputStream().use { out -> (part.content as? MimeMessage)?.writeTo(out) ?: part.inputStream.use { it.copyTo(out) } }
                } else {
                    part.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                }
                tmp.renameTo(target)
                target
            } finally {
                runCatching { f.close(false) }
            }
        }
    }

    /** Surowa wiadomość (do edycji wersji roboczej lub podglądu źródła). */
    suspend fun rawMessage(a: Account, h: MessageHeader): MimeMessage {
        val folder = Db.folder(h.folderId) ?: error("Folder nie istnieje")
        return withStore(a) { store ->
            val f = store.getFolder(folder.path) as IMAPFolder
            f.open(javax.mail.Folder.READ_ONLY)
            try {
                val m = f.getMessageByUID(h.uid) as? MimeMessage ?: error("Wiadomość została usunięta z serwera")
                MimeMessage(m)
            } finally {
                runCatching { f.close(false) }
            }
        }
    }

    // ---------- Operacje ----------

    /** Wykonuje operację na serwerze. Błąd sieci → zostaje w kolejce i ponowi się przy synchronizacji. */
    suspend fun apply(a: Account, op: PendingOp) = withStore(a) { store ->
        val f = store.getFolder(op.folderPath) as IMAPFolder
        if (!f.exists()) return@withStore
        f.open(javax.mail.Folder.READ_WRITE)
        try {
            val m = f.getMessageByUID(op.uid) ?: return@withStore
            when (op.type) {
                OpType.SEEN -> m.setFlag(Flags.Flag.SEEN, true)
                OpType.UNSEEN -> m.setFlag(Flags.Flag.SEEN, false)
                OpType.FLAG -> m.setFlag(Flags.Flag.FLAGGED, true)
                OpType.UNFLAG -> m.setFlag(Flags.Flag.FLAGGED, false)
                OpType.MOVE -> {
                    val target = store.getFolder(op.arg) as IMAPFolder
                    if (runCatching { store.hasCapability("MOVE") }.getOrDefault(false)) {
                        f.moveMessages(arrayOf(m), target)
                    } else {
                        f.copyMessages(arrayOf(m), target)
                        m.setFlag(Flags.Flag.DELETED, true)
                        f.expunge(arrayOf(m))
                    }
                }
                OpType.DELETE, OpType.EXPUNGE -> {
                    m.setFlag(Flags.Flag.DELETED, true)
                    runCatching { f.expunge(arrayOf(m)) }.onFailure { f.expunge() }
                }
            }
        } finally {
            runCatching { f.close(false) }
        }
    }

    /** Dopisanie wiadomości do folderu (Wysłane, Wersje robocze); zwraca UID, jeśli serwer go poda. */
    suspend fun append(a: Account, folder: Folder, message: MimeMessage, flags: Flags): Long = withStore(a) { store ->
        val f = store.getFolder(folder.path) as IMAPFolder
        message.setFlags(flags, true)
        val res = f.appendUIDMessages(arrayOf(message))
        res?.firstOrNull()?.uid ?: -1L
    }

    /** Wyszukiwanie na serwerze w folderze; wyniki trafiają do bazy, zwraca ich identyfikatory. */
    suspend fun search(a: Account, folder: Folder, query: String, limit: Int = 100): List<Long> = withStore(a) { store ->
        val f = store.getFolder(folder.path) as IMAPFolder
        f.open(javax.mail.Folder.READ_ONLY)
        try {
            val term = OrTerm(
                arrayOf(
                    SubjectTerm(query),
                    FromStringTerm(query),
                    RecipientStringTerm(Message.RecipientType.TO, query),
                    BodyTerm(query),
                ),
            )
            val found = f.search(term).takeLast(limit).map { it as IMAPMessage }
            if (found.isEmpty()) return@withStore emptyList()
            f.fetch(
                found.toTypedArray(),
                FetchProfile().apply {
                    add(UIDFolder.FetchProfileItem.UID)
                    add(FetchProfile.Item.FLAGS)
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.CONTENT_INFO)
                    add(FetchProfile.Item.SIZE)
                },
            )
            val known = Db.localFlags(folder.id)
            val headers = found.filter { f.getUID(it) !in known }.map { header(a, folder, f, it) }
            Db.insertMessages(headers)
            Db.notifyChanged()
            found.mapNotNull { Db.messageByUid(folder.id, f.getUID(it))?.id }
        } finally {
            runCatching { f.close(false) }
        }
    }

    // ---------- Wysyłanie ----------

    fun send(a: Account, message: MimeMessage) {
        val session = smtpSession(a)
        val t = session.getTransport("smtp") as Transport
        t.connect(a.smtp.host, a.smtp.port, a.smtp.username, credential(a))
        try {
            message.saveChanges()
            t.sendMessage(message, message.allRecipients)
        } finally {
            runCatching { t.close() }
        }
    }

    /** Sesja do budowania wiadomości (bez połączenia). */
    fun composeSession(): Session = Session.getInstance(Properties().apply { put("mail.mime.charset", "UTF-8") })
}
