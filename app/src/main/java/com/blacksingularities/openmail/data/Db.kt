package com.blacksingularities.openmail.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.blacksingularities.openmail.core.Crypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lokalna baza poczty (SQLite): konta, foldery, nagłówki wiadomości, treści, kolejka operacji
 * i książka adresowa z historii. Interfejs obserwuje [changes] i odświeża listy po każdej zmianie.
 */
object Db {
    private lateinit var helper: Helper
    private val db: SQLiteDatabase get() = helper.writableDatabase

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes

    fun init(context: Context) {
        if (!::helper.isInitialized) helper = Helper(context.applicationContext)
    }

    fun notifyChanged() {
        _changes.value = _changes.value + 1
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "openmail.db", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
            db.enableWriteAheadLogging()
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE accounts(
                id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT NOT NULL, display_name TEXT, label TEXT, color INTEGER,
                imap_host TEXT, imap_port INTEGER, imap_sec TEXT, imap_user TEXT,
                smtp_host TEXT, smtp_port INTEGER, smtp_sec TEXT, smtp_user TEXT,
                auth TEXT, secret TEXT, signature TEXT, provider TEXT,
                sync_enabled INTEGER, notify INTEGER, save_sent INTEGER, sort_order INTEGER,
                last_sync INTEGER, last_error TEXT)""",
            )
            db.execSQL(
                """CREATE TABLE folders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                path TEXT NOT NULL, name TEXT, role TEXT, sep TEXT, uid_validity INTEGER,
                unread INTEGER, total INTEGER, selectable INTEGER, sync INTEGER, last_sync INTEGER,
                UNIQUE(account_id, path))""",
            )
            db.execSQL(
                """CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                folder_id INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
                uid INTEGER NOT NULL, message_id TEXT, in_reply_to TEXT, refs TEXT,
                subject TEXT, from_email TEXT, from_name TEXT, to_json TEXT, cc_json TEXT, reply_to_json TEXT,
                date INTEGER, preview TEXT, seen INTEGER, flagged INTEGER, answered INTEGER, draft INTEGER,
                has_att INTEGER, size INTEGER, thread_key TEXT,
                UNIQUE(folder_id, uid))""",
            )
            db.execSQL("CREATE INDEX idx_msg_folder_date ON messages(folder_id, date DESC)")
            db.execSQL("CREATE INDEX idx_msg_thread ON messages(account_id, thread_key)")
            db.execSQL(
                """CREATE TABLE bodies(
                message_id INTEGER PRIMARY KEY REFERENCES messages(id) ON DELETE CASCADE,
                html TEXT, text TEXT, attachments TEXT, remote_images INTEGER)""",
            )
            db.execSQL(
                """CREATE TABLE pending_ops(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                folder_path TEXT, uid INTEGER, type TEXT, arg TEXT, attempts INTEGER)""",
            )
            db.execSQL("CREATE TABLE contacts(email TEXT PRIMARY KEY, name TEXT, uses INTEGER, last_used INTEGER)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    // ---------- Konta ----------

    fun accounts(): List<Account> = db.rawQuery("SELECT * FROM accounts ORDER BY sort_order, id", null).use { c ->
        buildList { while (c.moveToNext()) add(c.account()) }
    }

    fun account(id: Long): Account? = db.rawQuery("SELECT * FROM accounts WHERE id=?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) c.account() else null
    }

    fun saveAccount(a: Account): Long {
        val v = ContentValues().apply {
            put("email", a.email)
            put("display_name", a.displayName)
            put("label", a.label)
            put("color", a.color)
            put("imap_host", a.imap.host)
            put("imap_port", a.imap.port)
            put("imap_sec", a.imap.security.name)
            put("imap_user", a.imap.username)
            put("smtp_host", a.smtp.host)
            put("smtp_port", a.smtp.port)
            put("smtp_sec", a.smtp.security.name)
            put("smtp_user", a.smtp.username)
            put("auth", a.auth.name)
            put("secret", Crypto.encrypt(a.secret))
            put("signature", a.signature)
            put("provider", a.providerId)
            put("sync_enabled", a.syncEnabled.int)
            put("notify", a.notify.int)
            put("save_sent", a.saveSent.int)
            put("sort_order", a.sortOrder)
            put("last_sync", a.lastSync)
            put("last_error", a.lastError)
        }
        val id = if (a.id == 0L) {
            db.insertOrThrow("accounts", null, v)
        } else {
            db.update("accounts", v, "id=?", arrayOf(a.id.toString()))
            a.id
        }
        notifyChanged()
        return id
    }

    fun setAccountStatus(id: Long, lastSync: Long, error: String) {
        val v = ContentValues().apply {
            if (lastSync > 0) put("last_sync", lastSync)
            put("last_error", error)
        }
        db.update("accounts", v, "id=?", arrayOf(id.toString()))
        notifyChanged()
    }

    fun deleteAccount(id: Long) {
        db.delete("accounts", "id=?", arrayOf(id.toString()))
        notifyChanged()
    }

    private fun Cursor.account() = Account(
        id = long("id"),
        email = str("email"),
        displayName = str("display_name"),
        label = str("label"),
        color = int("color"),
        imap = ServerConfig(str("imap_host"), int("imap_port"), enumOr(str("imap_sec"), Security.SSL), str("imap_user")),
        smtp = ServerConfig(str("smtp_host"), int("smtp_port"), enumOr(str("smtp_sec"), Security.SSL), str("smtp_user")),
        auth = enumOr(str("auth"), AuthType.PASSWORD),
        secret = Crypto.decrypt(str("secret")),
        signature = str("signature"),
        providerId = str("provider"),
        syncEnabled = bool("sync_enabled"),
        notify = bool("notify"),
        saveSent = bool("save_sent"),
        sortOrder = int("sort_order"),
        lastSync = long("last_sync"),
        lastError = str("last_error"),
    )

    // ---------- Foldery ----------

    fun folders(accountId: Long): List<Folder> =
        db.rawQuery("SELECT * FROM folders WHERE account_id=?", arrayOf(accountId.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.folder()) }
        }.sortedWith(compareBy<Folder>({ it.role.order }, { it.path.lowercase() }))

    fun allFolders(): List<Folder> = db.rawQuery("SELECT * FROM folders", null).use { c ->
        buildList { while (c.moveToNext()) add(c.folder()) }
    }

    fun folder(id: Long): Folder? = db.rawQuery("SELECT * FROM folders WHERE id=?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) c.folder() else null
    }

    fun folderByPath(accountId: Long, path: String): Folder? =
        db.rawQuery("SELECT * FROM folders WHERE account_id=? AND path=?", arrayOf(accountId.toString(), path)).use { c ->
            if (c.moveToFirst()) c.folder() else null
        }

    fun folderByRole(accountId: Long, role: FolderRole): Folder? =
        folders(accountId).firstOrNull { it.role == role }

    /** Dodaje folder albo aktualizuje jego nazwę, rolę i atrybuty; zwraca identyfikator. */
    fun upsertFolder(f: Folder): Long {
        val existing = folderByPath(f.accountId, f.path)
        val v = ContentValues().apply {
            put("account_id", f.accountId)
            put("path", f.path)
            put("name", f.name)
            put("role", f.role.name)
            put("sep", f.separator.toString())
            put("selectable", f.selectable.int)
        }
        return if (existing == null) {
            v.put("uid_validity", f.uidValidity)
            v.put("unread", 0)
            v.put("total", 0)
            v.put("sync", f.sync.int)
            v.put("last_sync", 0L)
            db.insertOrThrow("folders", null, v)
        } else {
            db.update("folders", v, "id=?", arrayOf(existing.id.toString()))
            existing.id
        }
    }

    fun deleteFoldersExcept(accountId: Long, keep: Collection<String>) {
        folders(accountId).filter { it.path !in keep }.forEach {
            db.delete("folders", "id=?", arrayOf(it.id.toString()))
        }
    }

    fun setFolderSync(id: Long, sync: Boolean) {
        db.update("folders", ContentValues().apply { put("sync", sync.int) }, "id=?", arrayOf(id.toString()))
        notifyChanged()
    }

    fun setUidValidity(folderId: Long, value: Long) {
        db.update("folders", ContentValues().apply { put("uid_validity", value) }, "id=?", arrayOf(folderId.toString()))
    }

    fun setFolderSynced(folderId: Long, total: Int) {
        db.update(
            "folders",
            ContentValues().apply {
                put("last_sync", System.currentTimeMillis())
                put("total", total)
            },
            "id=?",
            arrayOf(folderId.toString()),
        )
    }

    /** Licznik nieprzeczytanych w folderze z lokalnej bazy. */
    fun refreshUnread(folderId: Long) {
        db.execSQL(
            "UPDATE folders SET unread=(SELECT COUNT(*) FROM messages WHERE folder_id=? AND seen=0) WHERE id=?",
            arrayOf(folderId, folderId),
        )
    }

    private fun Cursor.folder() = Folder(
        id = long("id"),
        accountId = long("account_id"),
        path = str("path"),
        name = str("name"),
        role = enumOr(str("role"), FolderRole.OTHER),
        separator = str("sep").firstOrNull() ?: '/',
        uidValidity = long("uid_validity"),
        unread = int("unread"),
        total = int("total"),
        selectable = bool("selectable"),
        sync = bool("sync"),
        lastSync = long("last_sync"),
    )

    // ---------- Wiadomości ----------

    /**
     * Lista wiadomości skrzynki z filtrem i wyszukiwaniem lokalnym (temat, nadawca, adresaci, podgląd).
     */
    fun messages(mailbox: Mailbox, filter: ListFilter, query: String, limit: Int): List<MessageHeader> {
        val where = StringBuilder()
        val args = mutableListOf<String>()
        when (mailbox) {
            is Mailbox.Of -> {
                where.append("m.folder_id=?")
                args += mailbox.folderId.toString()
            }
            is Mailbox.Unified -> when (mailbox.kind) {
                UnifiedKind.INBOX -> where.append("f.role='INBOX'")
                UnifiedKind.UNREAD -> where.append("f.role IN ('INBOX','OTHER','ARCHIVE') AND m.seen=0")
                UnifiedKind.FLAGGED -> where.append("m.flagged=1 AND f.role NOT IN ('TRASH','JUNK','ALL')")
                UnifiedKind.DRAFTS -> where.append("f.role='DRAFTS'")
                UnifiedKind.SENT -> where.append("f.role='SENT'")
            }
        }
        when (filter) {
            ListFilter.ALL -> Unit
            ListFilter.UNREAD -> where.append(" AND m.seen=0")
            ListFilter.FLAGGED -> where.append(" AND m.flagged=1")
            ListFilter.ATTACHMENTS -> where.append(" AND m.has_att=1")
        }
        val q = query.trim()
        if (q.isNotEmpty()) {
            q.split(Regex("\\s+")).forEach { word ->
                where.append(" AND (m.subject LIKE ? OR m.from_email LIKE ? OR m.from_name LIKE ? OR m.to_json LIKE ? OR m.preview LIKE ?)")
                val like = "%$word%"
                repeat(5) { args += like }
            }
        }
        val sql = "SELECT m.* FROM messages m JOIN folders f ON f.id=m.folder_id WHERE $where ORDER BY m.date DESC LIMIT $limit"
        return db.rawQuery(sql, args.toTypedArray()).use { c -> buildList { while (c.moveToNext()) add(c.message()) } }
    }

    /** Wyszukiwanie we wszystkich folderach wszystkich kont (poza koszem). */
    fun searchEverywhere(query: String, limit: Int): List<MessageHeader> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val where = StringBuilder("f.role NOT IN ('TRASH','ALL')")
        val args = mutableListOf<String>()
        q.split(Regex("\\s+")).forEach { word ->
            where.append(" AND (m.subject LIKE ? OR m.from_email LIKE ? OR m.from_name LIKE ? OR m.to_json LIKE ? OR m.preview LIKE ?)")
            repeat(5) { args += "%$word%" }
        }
        val sql = "SELECT m.* FROM messages m JOIN folders f ON f.id=m.folder_id WHERE $where ORDER BY m.date DESC LIMIT $limit"
        return db.rawQuery(sql, args.toTypedArray()).use { c -> buildList { while (c.moveToNext()) add(c.message()) } }
    }

    fun message(id: Long): MessageHeader? = db.rawQuery("SELECT * FROM messages WHERE id=?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) c.message() else null
    }

    fun messageByUid(folderId: Long, uid: Long): MessageHeader? =
        db.rawQuery("SELECT * FROM messages WHERE folder_id=? AND uid=?", arrayOf(folderId.toString(), uid.toString())).use { c ->
            if (c.moveToFirst()) c.message() else null
        }

    /** Wiadomości wątku (wszystkie foldery konta poza koszem), od najstarszej. */
    fun thread(accountId: Long, threadKey: String): List<MessageHeader> {
        if (threadKey.isBlank()) return emptyList()
        return db.rawQuery(
            "SELECT m.* FROM messages m JOIN folders f ON f.id=m.folder_id WHERE m.account_id=? AND m.thread_key=? AND f.role NOT IN ('TRASH','ALL','JUNK') ORDER BY m.date",
            arrayOf(accountId.toString(), threadKey),
        ).use { c -> buildList { while (c.moveToNext()) add(c.message()) } }
            .distinctBy { it.messageId.ifBlank { it.id.toString() } }
    }

    /** UID i flagi wiadomości zapisanych lokalnie w folderze. */
    fun localFlags(folderId: Long): Map<Long, Pair<Boolean, Boolean>> =
        db.rawQuery("SELECT uid, seen, flagged FROM messages WHERE folder_id=?", arrayOf(folderId.toString())).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), (c.getInt(1) == 1) to (c.getInt(2) == 1)) }
        }

    fun insertMessages(list: List<MessageHeader>) {
        if (list.isEmpty()) return
        db.beginTransaction()
        try {
            for (m in list) {
                val v = ContentValues().apply {
                    put("account_id", m.accountId)
                    put("folder_id", m.folderId)
                    put("uid", m.uid)
                    put("message_id", m.messageId)
                    put("in_reply_to", m.inReplyTo)
                    put("refs", m.references)
                    put("subject", m.subject)
                    put("from_email", m.from.email)
                    put("from_name", m.from.name)
                    put("to_json", encode(m.to))
                    put("cc_json", encode(m.cc))
                    put("reply_to_json", encode(m.replyTo))
                    put("date", m.date)
                    put("preview", m.preview)
                    put("seen", m.seen.int)
                    put("flagged", m.flagged.int)
                    put("answered", m.answered.int)
                    put("draft", m.draft.int)
                    put("has_att", m.hasAttachments.int)
                    put("size", m.size)
                    put("thread_key", m.threadKey)
                }
                db.insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateFlags(folderId: Long, flags: Map<Long, Triple<Boolean, Boolean, Boolean>>) {
        if (flags.isEmpty()) return
        db.beginTransaction()
        try {
            for ((uid, f) in flags) {
                val v = ContentValues().apply {
                    put("seen", f.first.int)
                    put("flagged", f.second.int)
                    put("answered", f.third.int)
                }
                db.update("messages", v, "folder_id=? AND uid=?", arrayOf(folderId.toString(), uid.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setSeen(ids: Collection<Long>, seen: Boolean) = setColumn(ids, "seen", seen.int)
    fun setFlagged(ids: Collection<Long>, flagged: Boolean) = setColumn(ids, "flagged", flagged.int)
    fun setAnswered(ids: Collection<Long>) = setColumn(ids, "answered", 1)

    private fun setColumn(ids: Collection<Long>, column: String, value: Int) {
        if (ids.isEmpty()) return
        val folders = mutableSetOf<Long>()
        db.beginTransaction()
        try {
            for (id in ids) {
                db.update("messages", ContentValues().apply { put(column, value) }, "id=?", arrayOf(id.toString()))
                message(id)?.let { folders += it.folderId }
            }
            folders.forEach { refreshUnread(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    fun setPreview(id: Long, preview: String, hasAttachments: Boolean) {
        val v = ContentValues().apply {
            put("preview", preview)
            put("has_att", hasAttachments.int)
        }
        db.update("messages", v, "id=?", arrayOf(id.toString()))
    }

    fun deleteMessages(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val folders = ids.mapNotNull { message(it)?.folderId }.toSet()
        db.beginTransaction()
        try {
            ids.forEach { db.delete("messages", "id=?", arrayOf(it.toString())) }
            folders.forEach { refreshUnread(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    fun deleteUids(folderId: Long, uids: Collection<Long>) {
        if (uids.isEmpty()) return
        db.beginTransaction()
        try {
            uids.chunked(400).forEach { chunk ->
                db.delete("messages", "folder_id=? AND uid IN (${chunk.joinToString(",")})", arrayOf(folderId.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearFolder(folderId: Long) {
        db.delete("messages", "folder_id=?", arrayOf(folderId.toString()))
    }

    /** Usuwa najstarsze wiadomości ponad limit synchronizacji. */
    fun trimFolder(folderId: Long, keep: Int) {
        db.execSQL(
            "DELETE FROM messages WHERE folder_id=? AND id NOT IN (SELECT id FROM messages WHERE folder_id=? ORDER BY uid DESC LIMIT ?)",
            arrayOf(folderId, folderId, keep),
        )
    }

    fun unreadUnified(): Int = db.rawQuery(
        "SELECT COUNT(*) FROM messages m JOIN folders f ON f.id=m.folder_id WHERE f.role='INBOX' AND m.seen=0",
        null,
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun unreadInbox(accountId: Long): Int = db.rawQuery(
        "SELECT COUNT(*) FROM messages m JOIN folders f ON f.id=m.folder_id WHERE f.role='INBOX' AND m.seen=0 AND m.account_id=?",
        arrayOf(accountId.toString()),
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun countUnified(kind: UnifiedKind): Int = when (kind) {
        UnifiedKind.INBOX, UnifiedKind.UNREAD -> unreadUnified()
        UnifiedKind.FLAGGED -> db.rawQuery(
            "SELECT COUNT(*) FROM messages m JOIN folders f ON f.id=m.folder_id WHERE m.flagged=1 AND f.role NOT IN ('TRASH','JUNK','ALL')",
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        UnifiedKind.DRAFTS -> db.rawQuery(
            "SELECT COUNT(*) FROM messages m JOIN folders f ON f.id=m.folder_id WHERE f.role='DRAFTS'",
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        UnifiedKind.SENT -> 0
    }

    private fun Cursor.message() = MessageHeader(
        id = long("id"),
        accountId = long("account_id"),
        folderId = long("folder_id"),
        uid = long("uid"),
        messageId = str("message_id"),
        inReplyTo = str("in_reply_to"),
        references = str("refs"),
        subject = str("subject"),
        from = Address(str("from_email"), str("from_name")),
        to = decode(str("to_json")),
        cc = decode(str("cc_json")),
        replyTo = decode(str("reply_to_json")),
        date = long("date"),
        preview = str("preview"),
        seen = bool("seen"),
        flagged = bool("flagged"),
        answered = bool("answered"),
        draft = bool("draft"),
        hasAttachments = bool("has_att"),
        size = int("size"),
        threadKey = str("thread_key"),
    )

    // ---------- Treści ----------

    fun body(messageId: Long): MessageBody? =
        db.rawQuery("SELECT * FROM bodies WHERE message_id=?", arrayOf(messageId.toString())).use { c ->
            if (!c.moveToFirst()) return null
            MessageBody(
                messageId = messageId,
                html = c.str("html"),
                text = c.str("text"),
                attachments = decodeAttachments(c.str("attachments")),
                hasRemoteImages = c.bool("remote_images"),
            )
        }

    fun saveBody(b: MessageBody) {
        val v = ContentValues().apply {
            put("message_id", b.messageId)
            put("html", b.html)
            put("text", b.text)
            put("attachments", encodeAttachments(b.attachments))
            put("remote_images", b.hasRemoteImages.int)
        }
        db.insertWithOnConflict("bodies", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun hasBody(messageId: Long): Boolean =
        db.rawQuery("SELECT 1 FROM bodies WHERE message_id=?", arrayOf(messageId.toString())).use { it.moveToFirst() }

    // ---------- Kolejka operacji ----------

    fun addOp(op: PendingOp) {
        val v = ContentValues().apply {
            put("account_id", op.accountId)
            put("folder_path", op.folderPath)
            put("uid", op.uid)
            put("type", op.type.name)
            put("arg", op.arg)
            put("attempts", 0)
        }
        db.insert("pending_ops", null, v)
    }

    fun ops(accountId: Long): List<PendingOp> =
        db.rawQuery("SELECT * FROM pending_ops WHERE account_id=? ORDER BY id", arrayOf(accountId.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        PendingOp(
                            id = c.long("id"),
                            accountId = c.long("account_id"),
                            folderPath = c.str("folder_path"),
                            uid = c.long("uid"),
                            type = enumOr(c.str("type"), OpType.SEEN),
                            arg = c.str("arg"),
                            attempts = c.int("attempts"),
                        ),
                    )
                }
            }
        }

    fun removeOp(id: Long) {
        db.delete("pending_ops", "id=?", arrayOf(id.toString()))
    }

    fun failOp(op: PendingOp) {
        if (op.attempts >= 5) {
            removeOp(op.id)
        } else {
            db.update("pending_ops", ContentValues().apply { put("attempts", op.attempts + 1) }, "id=?", arrayOf(op.id.toString()))
        }
    }

    // ---------- Książka adresowa ----------

    fun touchContacts(addresses: Collection<Address>) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (a in addresses) {
                val email = a.email.trim().lowercase()
                if (email.isEmpty() || '@' !in email) continue
                db.execSQL(
                    "INSERT INTO contacts(email, name, uses, last_used) VALUES(?, ?, 1, ?) " +
                        "ON CONFLICT(email) DO UPDATE SET uses=uses+1, last_used=excluded.last_used, " +
                        "name=CASE WHEN excluded.name<>'' THEN excluded.name ELSE name END",
                    arrayOf(email, a.name, now),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Podpowiedzi adresów: najpierw używane przy wysyłaniu, potem nadawcy z odebranej poczty. */
    fun suggest(prefix: String, limit: Int = 6): List<Address> {
        val p = prefix.trim()
        if (p.length < 1) return emptyList()
        val like = "%$p%"
        val used = db.rawQuery(
            "SELECT email, name FROM contacts WHERE email LIKE ? OR name LIKE ? ORDER BY uses DESC, last_used DESC LIMIT ?",
            arrayOf(like, like, limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Address(c.getString(0), c.getString(1) ?: "")) } }
        if (used.size >= limit) return used
        val seen = db.rawQuery(
            "SELECT from_email, MAX(from_name), COUNT(*) n FROM messages WHERE from_email LIKE ? OR from_name LIKE ? GROUP BY lower(from_email) ORDER BY n DESC LIMIT ?",
            arrayOf(like, like, limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Address(c.getString(0), c.getString(1) ?: "")) } }
        return (used + seen).distinctBy { it.email.lowercase() }.take(limit)
    }

    // ---------- Pomocnicze ----------

    private val Boolean.int get() = if (this) 1 else 0
    private fun Cursor.idx(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.str(name: String): String = getString(idx(name)) ?: ""
    private fun Cursor.long(name: String): Long = getLong(idx(name))
    private fun Cursor.int(name: String): Int = getInt(idx(name))
    private fun Cursor.bool(name: String): Boolean = getInt(idx(name)) == 1

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        runCatching { enumValueOf<E>(name) }.getOrDefault(default)

    fun encode(list: List<Address>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("e", it.email).put("n", it.name)) }
    }.toString()

    fun decode(json: String): List<Address> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(json)
            List(a.length()) { i -> a.getJSONObject(i).let { Address(it.optString("e"), it.optString("n")) } }
        }.getOrDefault(emptyList())
    }

    private fun encodeAttachments(list: List<Attachment>): String = JSONArray().apply {
        list.forEach {
            put(
                JSONObject()
                    .put("name", it.name)
                    .put("mime", it.mime)
                    .put("size", it.size)
                    .put("part", it.part)
                    .put("cid", it.contentId)
                    .put("inline", it.inline),
            )
        }
    }.toString()

    private fun decodeAttachments(json: String): List<Attachment> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(json)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Attachment(o.optString("name"), o.optString("mime"), o.optLong("size"), o.optString("part"), o.optString("cid"), o.optBoolean("inline"))
            }
        }.getOrDefault(emptyList())
    }
}
