package com.blacksingularities.openmail

import com.blacksingularities.openmail.data.Security
import com.blacksingularities.openmail.mail.Mime
import com.blacksingularities.openmail.mail.Providers
import com.blacksingularities.openmail.ui.isValidEmail
import com.blacksingularities.openmail.ui.parseAddresses
import com.blacksingularities.openmail.update.isNewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailLogicTest {
    @Test
    fun subjectNormalization() {
        assertEquals("spotkanie", Mime.normalizeSubject("Re: RE: Fwd: Spotkanie"))
        assertEquals("spotkanie", Mime.normalizeSubject("Odp: PD: spotkanie"))
        assertEquals("raport q3", Mime.normalizeSubject("AW:  WG: Raport   Q3"))
        assertEquals("re-design", Mime.normalizeSubject("Re[2]: Re-design"))
    }

    @Test
    fun knownProviders() {
        assertEquals("gmail", Providers.forEmail("jan@gmail.com")?.id)
        assertEquals("wp", Providers.forEmail("ala@WP.pl")?.id)
        assertEquals("onet", Providers.forEmail("x@op.pl")?.id)
        assertEquals("interia", Providers.forEmail("x@poczta.fm")?.id)
        assertEquals("outlook", Providers.forEmail("x@hotmail.com")?.id)
        assertEquals(null, Providers.forEmail("x@firma.example"))
        val icloud = Providers.configFor(Providers.byId("icloud")!!, "jan@icloud.com")
        assertEquals("jan", icloud.imap.username)
    }

    @Test
    fun autoconfigParsing() {
        val xml = """
            <clientConfig version="1.1">
              <emailProvider id="example.com">
                <incomingServer type="pop3"><hostname>pop.example.com</hostname><port>995</port><socketType>SSL</socketType><username>%EMAILADDRESS%</username></incomingServer>
                <incomingServer type="imap"><hostname>imap.example.com</hostname><port>143</port><socketType>STARTTLS</socketType><username>%EMAILLOCALPART%</username></incomingServer>
                <incomingServer type="imap"><hostname>imap.example.com</hostname><port>993</port><socketType>SSL</socketType><username>%EMAILADDRESS%</username></incomingServer>
                <outgoingServer type="smtp"><hostname>smtp.example.com</hostname><port>587</port><socketType>STARTTLS</socketType><username>%EMAILADDRESS%</username></outgoingServer>
              </emailProvider>
            </clientConfig>
        """.trimIndent()
        val c = Providers.parseAutoconfig(xml, "jan@example.com")
        assertNotNull(c)
        assertEquals("imap.example.com", c!!.imap.host)
        assertEquals(993, c.imap.port)
        assertEquals(Security.SSL, c.imap.security)
        assertEquals("jan@example.com", c.imap.username)
        assertEquals(587, c.smtp.port)
        assertEquals(Security.STARTTLS, c.smtp.security)
    }

    @Test
    fun addressParsing() {
        val list = parseAddresses("Jan Kowalski <jan@x.pl>, ala@y.pl; \"Firma\" <biuro@firma.pl>")
        assertEquals(3, list.size)
        assertEquals("jan@x.pl", list[0].email)
        assertEquals("Jan Kowalski", list[0].name)
        assertEquals("ala@y.pl", list[1].email)
        assertEquals("Firma", list[2].name)
        assertTrue(isValidEmail("a.b+c@d.co"))
        assertFalse(isValidEmail("ab@"))
        assertFalse(isValidEmail("a b@c.pl"))
    }

    @Test
    fun versions() {
        assertTrue(isNewer("1.0.12", "1.0.9"))
        assertTrue(isNewer("1.1.0", "1.0.0.57"))
        assertFalse(isNewer("1.0.0", "1.0.0"))
        assertFalse(isNewer("1.0.0", "1.0-dev"))
    }
}
