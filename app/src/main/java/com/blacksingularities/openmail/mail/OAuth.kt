package com.blacksingularities.openmail.mail

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.blacksingularities.openmail.BuildConfig
import com.blacksingularities.openmail.MainActivity
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.AuthType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Wynik logowania OAuth: adres, nazwa i token odświeżania (zapisywany zaszyfrowany). */
data class OAuthResult(val email: String, val name: String, val refreshToken: String, val accessToken: String, val expiresAt: Long)

/**
 * Logowanie OAuth 2.0 z PKCE (bez sekretu klienta) w karcie przeglądarki – Google i Microsoft.
 * Do IMAP/SMTP trafia token dostępu przez mechanizm SASL XOAUTH2.
 */
object OAuth {
    private class Spec(
        val authUrl: String,
        val tokenUrl: String,
        val scope: String,
        val clientId: () -> String,
        val redirect: () -> String,
        val extra: Map<String, String> = emptyMap(),
    )

    private val google = Spec(
        authUrl = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenUrl = "https://oauth2.googleapis.com/token",
        scope = "https://mail.google.com/ openid email profile",
        clientId = { Prefs.googleClientId.ifBlank { BuildConfig.GOOGLE_CLIENT_ID } },
        redirect = { googleScheme() + ":/oauth2redirect" },
        extra = mapOf("access_type" to "offline", "prompt" to "consent"),
    )

    private val microsoft = Spec(
        authUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        scope = "offline_access openid email profile https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send",
        clientId = { Prefs.microsoftClientId.ifBlank { BuildConfig.MICROSOFT_CLIENT_ID } },
        redirect = { "com.blacksingularities.openmail://oauth" },
        extra = mapOf("prompt" to "select_account"),
    )

    private fun googleScheme(): String {
        val custom = Prefs.googleClientId
        return if (custom.isNotBlank()) {
            "com.googleusercontent.apps." + custom.removeSuffix(".apps.googleusercontent.com")
        } else {
            BuildConfig.GOOGLE_REDIRECT_SCHEME
        }
    }

    private fun spec(type: AuthType) = when (type) {
        AuthType.OAUTH_GOOGLE -> google
        AuthType.OAUTH_MICROSOFT -> microsoft
        AuthType.PASSWORD -> error("brak OAuth dla hasła")
    }

    /** Czy logowanie przez dostawcę jest skonfigurowane (jest identyfikator klienta). */
    fun available(type: AuthType): Boolean = type != AuthType.PASSWORD && spec(type).clientId().isNotBlank()

    /**
     * Dla Google przekierowanie działa tylko przy identyfikatorze wbudowanym w aplikację
     * (schemat musi być zadeklarowany w manifeście).
     */
    fun customGoogleNeedsRebuild(): Boolean =
        Prefs.googleClientId.isNotBlank() && Prefs.googleClientId != BuildConfig.GOOGLE_CLIENT_ID

    private class Pending(val type: AuthType, val verifier: String, val state: String, val loginHint: String, val result: CompletableDeferred<OAuthResult>)

    @Volatile
    private var pending: Pending? = null

    /** Otwiera stronę logowania; wynik przychodzi po powrocie do aplikacji. */
    suspend fun signIn(context: Context, type: AuthType, loginHint: String): OAuthResult {
        val s = spec(type)
        val verifier = random(48)
        val state = random(16)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val params = linkedMapOf(
            "client_id" to s.clientId(),
            "response_type" to "code",
            "redirect_uri" to s.redirect(),
            "scope" to s.scope,
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
        ) + s.extra + (if (loginHint.isNotBlank()) mapOf("login_hint" to loginHint) else emptyMap())
        val url = s.authUrl + "?" + form(params)
        val deferred = CompletableDeferred<OAuthResult>()
        pending?.result?.cancel()
        pending = Pending(type, verifier, state, loginHint, deferred)
        runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
        }.onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return deferred.await()
    }

    /** Powrót z przeglądarki z kodem autoryzacji. */
    internal suspend fun complete(uri: Uri) {
        val p = pending ?: return
        pending = null
        try {
            uri.getQueryParameter("error")?.let {
                error(uri.getQueryParameter("error_description") ?: it)
            }
            if (uri.getQueryParameter("state") != p.state) error("Nieprawidłowa odpowiedź logowania")
            val code = uri.getQueryParameter("code") ?: error("Brak kodu autoryzacji")
            val s = spec(p.type)
            val json = post(
                s.tokenUrl,
                mapOf(
                    "client_id" to s.clientId(),
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to s.redirect(),
                    "code_verifier" to p.verifier,
                ),
            )
            val access = json.optString("access_token").ifBlank { error("Brak tokenu dostępu") }
            val refresh = json.optString("refresh_token")
            val claims = json.optString("id_token").let { jwtClaims(it) }
            val email = claims?.optString("email").orEmpty()
                .ifBlank { claims?.optString("preferred_username").orEmpty() }
                .ifBlank { p.loginHint }
            val name = claims?.optString("name").orEmpty()
            val expires = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
            cache[email.lowercase()] = access to expires
            p.result.complete(OAuthResult(email, name, refresh, access, expires))
        } catch (e: Exception) {
            p.result.completeExceptionally(e)
        }
    }

    internal fun cancelPending() {
        pending?.result?.cancel()
        pending = null
    }

    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()

    /** Token dostępu dla konta – z pamięci albo odświeżony tokenem odświeżania. */
    fun accessToken(type: AuthType, email: String, refreshToken: String): String {
        val key = email.lowercase()
        cache[key]?.let { (token, exp) -> if (exp - 60_000 > System.currentTimeMillis()) return token }
        if (refreshToken.isBlank()) error("Zaloguj się ponownie (brak tokenu)")
        val s = spec(type)
        val params = mutableMapOf(
            "client_id" to s.clientId(),
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        )
        if (type == AuthType.OAUTH_MICROSOFT) params["scope"] = s.scope
        val json = post(s.tokenUrl, params)
        val access = json.optString("access_token").ifBlank { error("Sesja wygasła – zaloguj się ponownie") }
        cache[key] = access to (System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
        return access
    }

    fun invalidate(email: String) {
        cache.remove(email.lowercase())
    }

    private fun post(url: String, params: Map<String, String>): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        try {
            conn.outputStream.use { it.write(form(params).toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code !in 200..299) {
                error(json.optString("error_description").ifBlank { json.optString("error").ifBlank { "HTTP $code" } })
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun form(params: Map<String, String>) =
        params.entries.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }

    private fun random(bytes: Int): String = ByteArray(bytes).also { SecureRandom().nextBytes(it) }.let { b64(it) }

    private fun b64(data: ByteArray) = Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun jwtClaims(jwt: String): JSONObject? = runCatching {
        val part = jwt.split('.')[1]
        JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
    }.getOrNull()
}

/** Odbiera przekierowanie z przeglądarki i wraca do aplikacji. */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri != null) CoroutineScope(Dispatchers.IO).launch { OAuth.complete(uri) }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
