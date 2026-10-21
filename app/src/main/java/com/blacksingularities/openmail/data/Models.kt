package com.blacksingularities.openmail.data

/** Szyfrowanie połączenia z serwerem. */
enum class Security(val label: String) {
    SSL("SSL/TLS"),
    STARTTLS("STARTTLS"),
    NONE("Brak"),
}

/** Sposób logowania: hasło (także hasło aplikacji) albo token OAuth 2.0. */
enum class AuthType { PASSWORD, OAUTH_GOOGLE, OAUTH_MICROSOFT }

data class ServerConfig(
    val host: String,
    val port: Int,
    val security: Security,
    val username: String,
)

data class Account(
    val id: Long = 0,
    val email: String,
    val displayName: String,
    /** Nazwa konta w aplikacji (np. „Praca”). */
    val label: String,
    val color: Int,
    val imap: ServerConfig,
    val smtp: ServerConfig,
    val auth: AuthType = AuthType.PASSWORD,
    /** Hasło lub token odświeżania (zaszyfrowane w bazie). */
    val secret: String = "",
    val signature: String = "",
    val providerId: String = "",
    val syncEnabled: Boolean = true,
    val notify: Boolean = true,
    /** Kopia wysłanej wiadomości w „Wysłanych” (Gmail i Outlook robią to same). */
    val saveSent: Boolean = true,
    val sortOrder: Int = 0,
    val lastSync: Long = 0,
    val lastError: String = "",
) {
    val title: String get() = label.ifBlank { email }
}

/** Rola folderu wg atrybutów SPECIAL-USE (RFC 6154) albo nazwy. */
enum class FolderRole(val order: Int) {
    INBOX(0), FLAGGED(1), DRAFTS(2), SENT(3), ARCHIVE(4), ALL(5), JUNK(6), TRASH(7), OTHER(8);
}

data class Folder(
    val id: Long = 0,
    val accountId: Long,
    /** Pełna ścieżka IMAP (np. „[Gmail]/Wysłane”). */
    val path: String,
    val name: String,
    val role: FolderRole,
    val separator: Char = '/',
    val uidValidity: Long = 0,
    val unread: Int = 0,
    val total: Int = 0,
    val selectable: Boolean = true,
    val sync: Boolean = true,
    val lastSync: Long = 0,
) {
    val depth: Int get() = path.count { it == separator }
}

data class Address(val email: String, val name: String = "") {
    val display: String get() = name.ifBlank { email }
    override fun toString(): String = if (name.isBlank()) email else "$name <$email>"
}

data class MessageHeader(
    val id: Long = 0,
    val accountId: Long,
    val folderId: Long,
    val uid: Long,
    val messageId: String,
    val inReplyTo: String = "",
    val references: String = "",
    val subject: String,
    val from: Address,
    val to: List<Address>,
    val cc: List<Address> = emptyList(),
    val replyTo: List<Address> = emptyList(),
    val date: Long,
    val preview: String = "",
    val seen: Boolean,
    val flagged: Boolean,
    val answered: Boolean = false,
    val draft: Boolean = false,
    val hasAttachments: Boolean = false,
    val size: Int = 0,
    /** Klucz wątku (znormalizowany temat albo pierwszy identyfikator z References). */
    val threadKey: String = "",
)

data class Attachment(
    val name: String,
    val mime: String,
    val size: Long,
    /** Ścieżka części MIME, np. „1.2” – pozwala pobrać tylko ten fragment. */
    val part: String,
    val contentId: String = "",
    val inline: Boolean = false,
)

data class MessageBody(
    val messageId: Long,
    val html: String,
    val text: String,
    val attachments: List<Attachment>,
    val hasRemoteImages: Boolean = false,
)

/** Skrzynka wyświetlana na liście: wspólna (wszystkie konta) albo konkretny folder. */
sealed interface Mailbox {
    val key: String

    /** Wszystkie odebrane / oznaczone / nieprzeczytane ze wszystkich kont. */
    data class Unified(val kind: UnifiedKind) : Mailbox {
        override val key get() = "u:${kind.name}"
    }

    data class Of(val folderId: Long) : Mailbox {
        override val key get() = "f:$folderId"
    }

    companion object {
        fun parse(key: String): Mailbox? = when {
            key.startsWith("u:") -> runCatching { Unified(UnifiedKind.valueOf(key.removePrefix("u:"))) }.getOrNull()
            key.startsWith("f:") -> key.removePrefix("f:").toLongOrNull()?.let { Of(it) }
            else -> null
        }
    }
}

enum class UnifiedKind(val label: String) {
    INBOX("Wszystkie odebrane"),
    UNREAD("Nieprzeczytane"),
    FLAGGED("Oznaczone gwiazdką"),
    DRAFTS("Wersje robocze"),
    SENT("Wysłane"),
}

/** Filtr listy (pigułki nad listą). */
enum class ListFilter(val label: String) {
    ALL("Wszystkie"),
    UNREAD("Nieprzeczytane"),
    FLAGGED("Z gwiazdką"),
    ATTACHMENTS("Z załącznikami"),
}

/** Operacja czekająca na wysłanie na serwer (gdy nie było połączenia). */
data class PendingOp(
    val id: Long = 0,
    val accountId: Long,
    val folderPath: String,
    val uid: Long,
    val type: OpType,
    val arg: String = "",
    val attempts: Int = 0,
)

enum class OpType { SEEN, UNSEEN, FLAG, UNFLAG, MOVE, DELETE, EXPUNGE }

/** Kolory kont (kropki w szufladzie i na liście). */
val AccountColors = listOf(
    0xFF6FA8FF.toInt(), 0xFFFF7EB6.toInt(), 0xFF5CE1A6.toInt(), 0xFFFFB547.toInt(),
    0xFFB69CFF.toInt(), 0xFF4FD8EB.toInt(), 0xFFFF6B6B.toInt(), 0xFFC9CCD6.toInt(),
)
