package com.blacksingularities.openmail.mail

import com.blacksingularities.openmail.data.AuthType
import com.blacksingularities.openmail.data.Security
import com.blacksingularities.openmail.data.ServerConfig
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

/** Gotowa konfiguracja popularnej usługi pocztowej. */
data class Provider(
    val id: String,
    val name: String,
    val domains: List<String>,
    val imapHost: String,
    val imapPort: Int = 993,
    val imapSecurity: Security = Security.SSL,
    val smtpHost: String,
    val smtpPort: Int = 465,
    val smtpSecurity: Security = Security.SSL,
    /** Logowanie OAuth dostępne dla tej usługi. */
    val oauth: AuthType? = null,
    /** Usługa sama zapisuje kopię wysłanej wiadomości (nie dokładamy drugiej). */
    val savesSent: Boolean = false,
    /** Wskazówka przy logowaniu (np. hasło aplikacji, włączenie IMAP). */
    val hint: String = "",
    val helpUrl: String = "",
    /** Kolor marki – kafelek na ekranie dodawania konta. */
    val color: Long = 0xFF6FA8FF,
    /** Login to sama nazwa użytkownika (bez domeny). */
    val localPartLogin: Boolean = false,
)

data class DetectedConfig(
    val imap: ServerConfig,
    val smtp: ServerConfig,
    val provider: Provider?,
    /** Skąd pochodzą ustawienia – pokazywane użytkownikowi. */
    val source: String,
)

object Providers {
    val all = listOf(
        Provider(
            id = "gmail",
            name = "Gmail",
            domains = listOf("gmail.com", "googlemail.com"),
            imapHost = "imap.gmail.com",
            smtpHost = "smtp.gmail.com",
            oauth = AuthType.OAUTH_GOOGLE,
            savesSent = true,
            hint = "Włącz weryfikację dwuetapową i utwórz hasło aplikacji (16 znaków) – zwykłe hasło Google nie działa w programach pocztowych.",
            helpUrl = "https://myaccount.google.com/apppasswords",
            color = 0xFFEA4335,
        ),
        Provider(
            id = "outlook",
            name = "Outlook / Hotmail",
            domains = listOf("outlook.com", "hotmail.com", "live.com", "msn.com", "outlook.pl", "hotmail.pl", "live.pl"),
            imapHost = "outlook.office365.com",
            smtpHost = "smtp-mail.outlook.com",
            smtpPort = 587,
            smtpSecurity = Security.STARTTLS,
            oauth = AuthType.OAUTH_MICROSOFT,
            savesSent = true,
            hint = "Konta Microsoft wymagają logowania przez Microsoft (OAuth). Logowanie hasłem działa tylko na kontach z włączonym dostępem podstawowym.",
            helpUrl = "https://support.microsoft.com/office/pop-imap-and-smtp-settings-for-outlook-com-d088b986-291d-42b8-9564-9c414e2aa040",
            color = 0xFF0A84FF,
        ),
        Provider(
            id = "office365",
            name = "Microsoft 365 (praca / szkoła)",
            domains = emptyList(),
            imapHost = "outlook.office365.com",
            smtpHost = "smtp.office365.com",
            smtpPort = 587,
            smtpSecurity = Security.STARTTLS,
            oauth = AuthType.OAUTH_MICROSOFT,
            savesSent = true,
            hint = "Zaloguj się przez Microsoft. Administrator organizacji może musieć zezwolić na dostęp IMAP.",
            color = 0xFFF25022,
        ),
        Provider(
            id = "yahoo",
            name = "Yahoo",
            domains = listOf("yahoo.com", "yahoo.pl", "ymail.com", "rocketmail.com", "yahoo.co.uk", "yahoo.de"),
            imapHost = "imap.mail.yahoo.com",
            smtpHost = "smtp.mail.yahoo.com",
            savesSent = true,
            hint = "Utwórz hasło aplikacji w ustawieniach bezpieczeństwa konta Yahoo.",
            helpUrl = "https://login.yahoo.com/account/security",
            color = 0xFF7B3FE4,
        ),
        Provider(
            id = "icloud",
            name = "iCloud",
            domains = listOf("icloud.com", "me.com", "mac.com"),
            imapHost = "imap.mail.me.com",
            smtpHost = "smtp.mail.me.com",
            smtpPort = 587,
            smtpSecurity = Security.STARTTLS,
            savesSent = true,
            hint = "Wygeneruj hasło dla aplikacji na appleid.apple.com (Logowanie i bezpieczeństwo).",
            helpUrl = "https://appleid.apple.com/account/manage",
            color = 0xFFC9CCD6,
            localPartLogin = true,
        ),
        Provider(
            id = "wp",
            name = "Wirtualna Polska",
            domains = listOf("wp.pl"),
            imapHost = "imap.wp.pl",
            smtpHost = "smtp.wp.pl",
            hint = "W poczcie WP włącz dostęp przez programy pocztowe (Ustawienia → Konto → POP3/IMAP/SMTP).",
            helpUrl = "https://poczta.wp.pl",
            color = 0xFFFF3B47,
        ),
        Provider(
            id = "o2",
            name = "o2.pl",
            domains = listOf("o2.pl", "go2.pl", "tlen.pl", "prokonto.pl"),
            imapHost = "poczta.o2.pl",
            smtpHost = "poczta.o2.pl",
            hint = "Włącz dostęp IMAP w ustawieniach poczty o2.",
            color = 0xFF2E7DFF,
        ),
        Provider(
            id = "onet",
            name = "Onet Poczta",
            domains = listOf("onet.pl", "op.pl", "onet.eu", "poczta.onet.pl", "vp.pl", "autograf.pl", "buziaczek.pl", "spoko.pl", "amorki.pl"),
            imapHost = "imap.poczta.onet.pl",
            smtpHost = "smtp.poczta.onet.pl",
            hint = "W Onet Poczcie włącz obsługę programów pocztowych (Ustawienia → Konto → Programy pocztowe).",
            color = 0xFFFFB547,
        ),
        Provider(
            id = "interia",
            name = "Interia",
            domains = listOf("interia.pl", "interia.eu", "poczta.fm", "intmail.pl", "adresik.net", "vip.interia.pl", "pacz.to", "ogarnij.se", "interiowy.pl", "playinteria.pl"),
            imapHost = "poczta.interia.pl",
            smtpHost = "poczta.interia.pl",
            hint = "Włącz dostęp IMAP/SMTP w ustawieniach poczty Interia.",
            color = 0xFFFF9F0A,
        ),
        Provider(
            id = "gmx",
            name = "GMX",
            domains = listOf("gmx.com", "gmx.net", "gmx.de", "gmx.at", "gmx.ch", "gmx.pl"),
            imapHost = "imap.gmx.com",
            smtpHost = "mail.gmx.com",
            smtpPort = 587,
            smtpSecurity = Security.STARTTLS,
            hint = "Włącz dostęp POP3/IMAP w ustawieniach GMX.",
            color = 0xFF1C449B,
        ),
        Provider(
            id = "zoho",
            name = "Zoho Mail",
            domains = listOf("zoho.com", "zohomail.com", "zohomail.eu", "zoho.eu"),
            imapHost = "imap.zoho.eu",
            smtpHost = "smtp.zoho.eu",
            hint = "Dla kont spoza UE serwery to imap.zoho.com / smtp.zoho.com. Przy weryfikacji dwuetapowej użyj hasła aplikacji.",
            color = 0xFFE42527,
        ),
        Provider(
            id = "fastmail",
            name = "Fastmail",
            domains = listOf("fastmail.com", "fastmail.fm", "messagingengine.com"),
            imapHost = "imap.fastmail.com",
            smtpHost = "smtp.fastmail.com",
            savesSent = false,
            hint = "Użyj hasła aplikacji z ustawień Fastmail (Prywatność i bezpieczeństwo).",
            color = 0xFF0067B9,
        ),
        Provider(
            id = "aol",
            name = "AOL",
            domains = listOf("aol.com", "aim.com"),
            imapHost = "imap.aol.com",
            smtpHost = "smtp.aol.com",
            savesSent = true,
            hint = "Utwórz hasło aplikacji w ustawieniach bezpieczeństwa konta AOL.",
            color = 0xFF39A0FF,
        ),
        Provider(
            id = "yandex",
            name = "Yandex",
            domains = listOf("yandex.com", "yandex.ru", "ya.ru"),
            imapHost = "imap.yandex.com",
            smtpHost = "smtp.yandex.com",
            hint = "Utwórz hasło aplikacji dla poczty w ustawieniach Yandex ID.",
            color = 0xFFFFCC00,
        ),
        Provider(
            id = "proton",
            name = "Proton Mail (Bridge)",
            domains = listOf("proton.me", "protonmail.com", "pm.me"),
            imapHost = "127.0.0.1",
            imapPort = 1143,
            imapSecurity = Security.STARTTLS,
            smtpHost = "127.0.0.1",
            smtpPort = 1025,
            smtpSecurity = Security.STARTTLS,
            savesSent = true,
            hint = "Proton wymaga aplikacji Proton Mail Bridge (dostępnej na komputery). Wpisz adres i hasło z Bridge.",
            color = 0xFF6D4AFF,
        ),
    )

    fun byId(id: String): Provider? = all.firstOrNull { it.id == id }

    fun forEmail(email: String): Provider? {
        val domain = email.substringAfter('@', "").lowercase().trim()
        if (domain.isEmpty()) return null
        return all.firstOrNull { domain in it.domains }
    }

    fun configFor(p: Provider, email: String) = DetectedConfig(
        imap = ServerConfig(p.imapHost, p.imapPort, p.imapSecurity, login(p, email)),
        smtp = ServerConfig(p.smtpHost, p.smtpPort, p.smtpSecurity, login(p, email)),
        provider = p,
        source = p.name,
    )

    private fun login(p: Provider, email: String) = if (p.localPartLogin) email.substringBefore('@') else email

    /**
     * Wykrycie serwerów: znane usługi → autokonfiguracja domeny (Thunderbird ISPDB, plik
     * autoconfig na serwerze domeny) → zgadywanie imap./smtp.<domena>.
     */
    fun detect(email: String): DetectedConfig {
        forEmail(email)?.let { return configFor(it, email) }
        val domain = email.substringAfter('@', "").lowercase().trim()
        val urls = listOf(
            "https://autoconfig.$domain/mail/config-v1.1.xml?emailaddress=$email",
            "https://$domain/.well-known/autoconfig/mail/config-v1.1.xml",
            "https://autoconfig.thunderbird.net/v1.1/$domain",
        )
        for (u in urls) {
            val xml = runCatching { httpGet(u) }.getOrNull() ?: continue
            parseAutoconfig(xml, email)?.let { return it.copy(source = "autokonfiguracja ($domain)") }
        }
        return DetectedConfig(
            imap = ServerConfig("imap.$domain", 993, Security.SSL, email),
            smtp = ServerConfig("smtp.$domain", 465, Security.SSL, email),
            provider = null,
            source = "domyślne ustawienia dla $domain",
        )
    }

    /** Format autokonfiguracji Thunderbirda (clientConfig v1.1). */
    fun parseAutoconfig(xml: String, email: String): DetectedConfig? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        fun servers(tag: String, type: String): List<ServerConfig> {
            val nodes = doc.getElementsByTagName(tag)
            return (0 until nodes.length).mapNotNull { i ->
                val e = nodes.item(i) as Element
                if (e.getAttribute("type") != type) return@mapNotNull null
                fun child(name: String) = e.getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
                val host = child("hostname").ifBlank { return@mapNotNull null }
                val port = child("port").toIntOrNull() ?: return@mapNotNull null
                val sec = when (child("socketType").uppercase()) {
                    "SSL" -> Security.SSL
                    "STARTTLS" -> Security.STARTTLS
                    else -> Security.NONE
                }
                val user = child("username")
                    .replace("%EMAILADDRESS%", email)
                    .replace("%EMAILLOCALPART%", email.substringBefore('@'))
                    .replace("%EMAILDOMAIN%", email.substringAfter('@'))
                    .ifBlank { email }
                ServerConfig(host, port, sec, user)
            }
        }
        // Preferujemy szyfrowane połączenia.
        fun pick(list: List<ServerConfig>) = list.firstOrNull { it.security == Security.SSL }
            ?: list.firstOrNull { it.security == Security.STARTTLS } ?: list.firstOrNull()
        val imap = pick(servers("incomingServer", "imap")) ?: return null
        val smtp = pick(servers("outgoingServer", "smtp")) ?: return null
        DetectedConfig(imap, smtp, null, "autokonfiguracja")
    }.getOrNull()

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OpenMail")
        }
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
