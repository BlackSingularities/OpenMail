package com.blacksingularities.openmail.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.Account
import com.blacksingularities.openmail.data.AccountColors
import com.blacksingularities.openmail.data.AuthType
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.data.FolderRole
import com.blacksingularities.openmail.data.Mailbox
import com.blacksingularities.openmail.data.Security
import com.blacksingularities.openmail.data.ServerConfig
import com.blacksingularities.openmail.mail.DetectedConfig
import com.blacksingularities.openmail.mail.MailClient
import com.blacksingularities.openmail.mail.OAuth
import com.blacksingularities.openmail.mail.Provider
import com.blacksingularities.openmail.mail.Providers
import com.blacksingularities.openmail.mail.mailMessage
import com.blacksingularities.openmail.sync.Sync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Edytowalne ustawienia jednego serwera. */
private class ServerForm(c: ServerConfig) {
    var host by mutableStateOf(c.host)
    var port by mutableStateOf(c.port.toString())
    var security by mutableStateOf(c.security)
    var user by mutableStateOf(c.username)
    fun config() = ServerConfig(host.trim(), port.toIntOrNull() ?: 0, security, user.trim())
    fun set(c: ServerConfig) {
        host = c.host
        port = c.port.toString()
        security = c.security
        user = c.username
    }
}

/** Kreator konta: wybór usługi → adres i hasło (albo OAuth) → sprawdzenie i zapis. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountSetupScreen(providerId: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(providerId?.let { Providers.byId(it) }) }
    var step by remember { mutableStateOf(if (providerId != null) 1 else 0) }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    val imap = remember { ServerForm(ServerConfig("", 993, Security.SSL, "")) }
    val smtp = remember { ServerForm(ServerConfig("", 465, Security.SSL, "")) }
    var detected by remember { mutableStateOf<DetectedConfig?>(null) }
    var detectedFor by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    BackHandler(step == 1 && providerId == null) { step = 0; error = null }

    fun applyDetected(d: DetectedConfig) {
        detected = d
        imap.set(d.imap)
        smtp.set(d.smtp)
    }

    /** Uzupełnia serwery dla wpisanego adresu (raz na adres, chyba że użytkownik je zmienił). */
    suspend fun ensureConfig(): Boolean {
        if (detectedFor == email.trim().lowercase() && imap.host.isNotBlank()) return true
        busy = "Wyszukiwanie ustawień serwera…"
        val p = provider
        val d = withContext(Dispatchers.IO) {
            if (p != null && (p.domains.isEmpty() || Providers.forEmail(email) == null || Providers.forEmail(email) == p)) Providers.configFor(p, email.trim())
            else Providers.detect(email.trim())
        }
        applyDetected(d)
        detectedFor = email.trim().lowercase()
        if (provider == null) provider = d.provider
        return true
    }

    suspend fun finish(account: Account) {
        busy = "Logowanie do ${account.imap.host}…"
        val res = withContext(Dispatchers.IO) { runCatching { MailClient.test(account) } }
        res.onFailure {
            busy = null
            error = it.mailMessage()
            advanced = advanced || it.mailMessage().startsWith("Nie znaleziono serwera")
            return
        }
        busy = "Zapisywanie konta…"
        val id = withContext(Dispatchers.IO) {
            val existing = Db.accounts()
            val id = Db.saveAccount(account.copy(sortOrder = existing.size, color = AccountColors[existing.size % AccountColors.size]))
            if (existing.isEmpty()) Prefs.defaultAccount = id
            runCatching { MailClient.syncFolders(account.copy(id = id)) }
            id
        }
        busy = null
        Db.account(id)?.let { Sync.refreshFolders(context, it) }
        Db.folderByRole(id, FolderRole.INBOX)?.let { Nav.open(Mailbox.Of(it.id)) }
        Sync.schedule(context)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        Toast.makeText(context, "Dodano konto ${account.email}", Toast.LENGTH_SHORT).show()
        Nav.home()
    }

    fun signInPassword() {
        error = null
        if (!isValidEmail(email)) {
            error = "Wpisz poprawny adres e-mail"
            return
        }
        if (password.isEmpty()) {
            error = "Wpisz hasło"
            return
        }
        scope.launch {
            ensureConfig()
            val p = provider
            finish(
                Account(
                    email = email.trim(),
                    displayName = name.trim(),
                    label = "",
                    color = AccountColors[0],
                    imap = imap.config(),
                    smtp = smtp.config(),
                    auth = AuthType.PASSWORD,
                    secret = password,
                    providerId = p?.id ?: "",
                    saveSent = !(p?.savesSent ?: false),
                ),
            )
        }
    }

    fun signInOAuth(type: AuthType) {
        error = null
        scope.launch {
            busy = "Logowanie w przeglądarce…"
            val res = runCatching { OAuth.signIn(context, type, email.trim()) }
            val r = res.getOrElse {
                busy = null
                if (it !is CancellationException) error = it.mailMessage()
                return@launch
            }
            email = r.email
            if (name.isBlank()) name = r.name
            val p = provider ?: Providers.forEmail(r.email)
            val cfg = if (p != null) Providers.configFor(p, r.email) else withContext(Dispatchers.IO) { Providers.detect(r.email) }
            finish(
                Account(
                    email = r.email,
                    displayName = name.ifBlank { r.name },
                    label = "",
                    color = AccountColors[0],
                    imap = cfg.imap.copy(username = r.email),
                    smtp = cfg.smtp.copy(username = r.email),
                    auth = type,
                    secret = r.refreshToken,
                    providerId = p?.id ?: "",
                    saveSent = !(p?.savesSent ?: true),
                ),
            )
        }
    }

    ScrollScreen(
        title = if (step == 0) "Dodaj konto" else provider?.name ?: "Inne konto (IMAP)",
        subtitle = if (step == 0) "Wybierz usługę pocztową" else "Zaloguj się",
        onBack = { if (step == 1 && providerId == null) { step = 0; error = null } else Nav.pop() },
    ) {
        if (step == 0) {
            Text("Popularne usługi", color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                Providers.all.forEach { p ->
                    ProviderTile(p, Modifier.weight(1f)) {
                        provider = p
                        step = 1
                    }
                }
                ProviderTile(null, Modifier.weight(1f)) {
                    provider = null
                    step = 1
                }
            }
            Spacer(Modifier.height(4.dp))
            Group(null) {
                ActionRow(Icons.Rounded.Info, "Twoje dane zostają na telefonie", "OpenMail łączy się bezpośrednio z serwerem poczty. Hasła są szyfrowane kluczem z Android Keystore i nie trafiają do kopii zapasowej.") {}
            }
            return@ScrollScreen
        }

        val p = provider
        Group(null) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkField(email, { email = it.trim(); error = null }, "Adres e-mail", keyboard = KeyboardType.Email, imeAction = ImeAction.Next)
                DarkField(name, { name = it }, "Imię i nazwisko (nadawca)", imeAction = ImeAction.Next)

                val oauthType = p?.oauth
                if (oauthType != null) {
                    if (OAuth.available(oauthType)) {
                        Pill(
                            if (oauthType == AuthType.OAUTH_GOOGLE) "Zaloguj przez Google" else "Zaloguj przez Microsoft",
                            icon = Icons.AutoMirrored.Rounded.Login,
                            primary = true,
                            enabled = busy == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { signInOAuth(oauthType) }
                        Text("albo hasłem aplikacji:", color = Palette.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    } else {
                        Text(
                            "Logowanie przez ${if (oauthType == AuthType.OAUTH_GOOGLE) "Google" else "Microsoft"} wymaga identyfikatora klienta OAuth (Ustawienia → Zaawansowane). Możesz użyć hasła aplikacji.",
                            color = Palette.TextFaint,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                DarkField(password, { password = it; error = null }, if (p?.hint?.contains("hasło aplikacji", true) == true || p?.hint?.contains("hasła aplikacji", true) == true) "Hasło aplikacji" else "Hasło", password = true, imeAction = ImeAction.Done)
            }
        }

        if (p != null && p.hint.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(p.color).copy(alpha = 0.12f))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Info, null, tint = Color(p.color), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.hint, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                    if (p.helpUrl.isNotBlank()) {
                        Row(
                            Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.helpUrl))) }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Otwórz stronę ustawień", color = Color(p.color), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Color(p.color), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Ustawienia serwerów (wykryte automatycznie, do ręcznej zmiany).
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    advanced = !advanced
                    if (advanced && imap.host.isBlank() && isValidEmail(email)) scope.launch { ensureConfig(); busy = null }
                }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Dns, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Ustawienia serwerów", color = Color.White, fontSize = 14.sp)
                Text(
                    detected?.let { "Źródło: ${it.source}" } ?: "Wykrywane automatycznie",
                    color = Palette.TextFaint,
                    fontSize = 12.sp,
                )
            }
            Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Palette.TextFaint)
        }
        AnimatedVisibility(advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ServerEditor("Poczta przychodząca (IMAP)", imap, onEdit = { detectedFor = email.trim().lowercase() })
                ServerEditor("Poczta wychodząca (SMTP)", smtp, onEdit = { detectedFor = email.trim().lowercase() })
            }
        }

        if (error != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Danger.copy(alpha = 0.14f)).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Warning, null, tint = Palette.Danger, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(error.orEmpty(), color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        if (busy != null) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(busy.orEmpty(), color = Palette.TextDim, fontSize = 14.sp)
            }
        } else {
            Pill("Zaloguj się", icon = Icons.Rounded.CheckCircle, primary = true, modifier = Modifier.fillMaxWidth()) { signInPassword() }
        }
    }
}

@Composable
private fun ProviderTile(p: Provider?, modifier: Modifier, onClick: () -> Unit) {
    val color = p?.let { Color(it.color) } ?: Color.White
    Column(
        modifier
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Card)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            if (p == null) {
                Icon(Icons.Rounded.AlternateEmail, null, tint = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Text(initials(p.name).take(1), color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(p?.name ?: "Inny (IMAP)", color = Color.White, fontSize = 12.sp, maxLines = 2, lineHeight = 14.sp, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ServerEditor(title: String, form: ServerForm, onEdit: () -> Unit = {}) {
    Group(title) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DarkField(form.host, { form.host = it.trim(); onEdit() }, "Serwer", keyboard = KeyboardType.Uri)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkField(form.port, { form.port = it.filter(Char::isDigit).take(5); onEdit() }, "Port", keyboard = KeyboardType.Number, modifier = Modifier.weight(0.35f))
                DarkField(form.user, { form.user = it.trim(); onEdit() }, "Login", keyboard = KeyboardType.Email, modifier = Modifier.weight(0.65f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Szyfrowanie", color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Security.entries.forEach { s ->
                    FilterChip(s.label, form.security == s) {
                        form.security = s
                        // Typowe porty dla wybranego szyfrowania.
                        val imapLike = form.port in setOf("993", "143")
                        if (imapLike) form.port = if (s == Security.SSL) "993" else "143"
                        else if (form.port in setOf("465", "587", "25")) form.port = if (s == Security.SSL) "465" else "587"
                        onEdit()
                    }
                }
            }
        }
    }
}

/** Lista kont: kolejność, konto domyślne, stan synchronizacji. */
@Composable
fun AccountsScreen() {
    val version by Db.changes.collectAsState()
    val accounts by produceState(emptyList<Account>(), version) { value = withContext(Dispatchers.IO) { Db.accounts() } }
    val context = LocalContext.current
    ScrollScreen("Konta", "${accounts.size} ${if (accounts.size == 1) "konto" else "kont(a)"}", onBack = { Nav.pop() }) {
        Group(null) {
            accounts.forEachIndexed { i, a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { Nav.push(Screen.AccountEdit(a.id)) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(a.displayName.ifBlank { a.email }, a.email, size = 42.dp, color = Color(a.color))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.email, color = Palette.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (a.lastError.isNotBlank()) a.lastError else "Synchronizacja: ${Fmt.ago(a.lastSync)}",
                            color = if (a.lastError.isNotBlank()) Palette.Star else Palette.TextFaint,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val isDefault = Prefs.defaultAccount == a.id
                    RoundIcon(if (isDefault) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (isDefault) "Konto domyślne" else "Ustaw jako domyślne", size = 38.dp, flat = true, tint = if (isDefault) Palette.Star else Palette.TextFaint) {
                        Prefs.defaultAccount = a.id
                        Toast.makeText(context, "Domyślne konto: ${a.title}", Toast.LENGTH_SHORT).show()
                    }
                    if (accounts.size > 1) {
                        Column {
                            RoundIcon(Icons.Rounded.ArrowUpward, "Wyżej", size = 30.dp, flat = true, enabled = i > 0) { move(accounts, i, -1) }
                            RoundIcon(Icons.Rounded.ArrowDownward, "Niżej", size = 30.dp, flat = true, enabled = i < accounts.lastIndex) { move(accounts, i, 1) }
                        }
                    }
                }
            }
        }
        Pill("Dodaj konto", icon = Icons.Rounded.PersonAdd, primary = true, modifier = Modifier.fillMaxWidth()) { Nav.push(Screen.AccountSetup()) }
        Pill("Synchronizuj wszystkie", icon = Icons.Rounded.Sync, modifier = Modifier.fillMaxWidth()) { Sync.refreshAll(context) }
    }
}

private fun move(accounts: List<Account>, index: Int, delta: Int) {
    val list = accounts.toMutableList()
    val j = index + delta
    if (j !in list.indices) return
    val a = list.removeAt(index)
    list.add(j, a)
    list.forEachIndexed { i, acc -> if (acc.sortOrder != i) Db.saveAccount(acc.copy(sortOrder = i)) }
}

/** Ustawienia konta: nazwa, podpis, kolor, powiadomienia, foldery, serwery, usuwanie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(id: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by Db.changes.collectAsState()
    val loaded by produceState<Account?>(Db.account(id), id) { value = withContext(Dispatchers.IO) { Db.account(id) } }
    val a = loaded ?: run {
        Box(Modifier.background(Palette.Ink))
        return
    }
    val folders by produceState(Db.folders(id), id, version) { value = withContext(Dispatchers.IO) { Db.folders(id) } }
    val status by produceState(a, version) { value = withContext(Dispatchers.IO) { Db.account(id) } ?: a }
    var label by remember { mutableStateOf(a.label) }
    var displayName by remember { mutableStateOf(a.displayName) }
    var signature by remember { mutableStateOf(a.signature) }
    var color by remember { mutableStateOf(a.color) }
    var syncEnabled by remember { mutableStateOf(a.syncEnabled) }
    var notify by remember { mutableStateOf(a.notify) }
    var saveSent by remember { mutableStateOf(a.saveSent) }
    var password by remember { mutableStateOf("") }
    var servers by remember { mutableStateOf(false) }
    val imap = remember { ServerForm(a.imap) }
    val smtp = remember { ServerForm(a.smtp) }
    var confirmRemove by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    fun edited() = (Db.account(id) ?: a).copy(
        label = label.trim(),
        displayName = displayName.trim(),
        signature = signature.trimEnd(),
        color = color,
        syncEnabled = syncEnabled,
        notify = notify,
        saveSent = saveSent,
        imap = imap.config(),
        smtp = smtp.config(),
        secret = if (password.isNotEmpty() && a.auth == AuthType.PASSWORD) password else (Db.account(id) ?: a).secret,
    )

    fun save(quiet: Boolean = false) {
        Db.saveAccount(edited())
        MailClient.disconnect(id)
        if (!quiet) Toast.makeText(context, "Zapisano", Toast.LENGTH_SHORT).show()
    }

    BackHandler {
        save(quiet = true)
        Nav.pop()
    }

    ScrollScreen(
        title = a.title,
        subtitle = a.email,
        onBack = {
            save(quiet = true)
            Nav.pop()
        },
        actions = { RoundIcon(Icons.Rounded.Save, "Zapisz", active = true) { save() } },
    ) {
        if (status.lastError.isNotBlank()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Star.copy(alpha = 0.12f)).padding(14.dp)) {
                Icon(Icons.Rounded.Warning, null, tint = Palette.Star, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(status.lastError, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Group("Tożsamość") {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkField(label, { label = it }, "Nazwa konta w aplikacji", placeholder = a.email)
                DarkField(displayName, { displayName = it }, "Imię i nazwisko (nadawca)")
                DarkField(signature, { signature = it }, "Podpis", singleLine = false, minLines = 3)
                Text("Kolor konta", color = Palette.TextDim, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountColors.forEach { c -> ColorDot(c, c == color) { color = c } }
                }
            }
        }
        Group("Synchronizacja i powiadomienia") {
            SwitchRow(Icons.Rounded.Sync, "Synchronizuj konto", "Pobieranie nowej poczty w tle", syncEnabled) { syncEnabled = it; save(true) }
            SwitchRow(Icons.Rounded.Notifications, "Powiadomienia", "O nowych wiadomościach w Odebranych", notify) { notify = it; save(true) }
            SwitchRow(Icons.AutoMirrored.Rounded.Send, "Kopia w Wysłanych", "Wyłącz, jeśli serwer sam zapisuje wysłane (Gmail, Outlook)", saveSent) { saveSent = it; save(true) }
            InfoRow(Icons.Rounded.CloudSync, "Ostatnia synchronizacja", Fmt.ago(status.lastSync))
            ActionRow(Icons.Rounded.Refresh, "Odśwież foldery i pocztę") { Sync.refreshFolders(context, edited()) }
        }
        Group("Foldery synchronizowane w tle", collapsible = true, initiallyExpanded = false) {
            if (folders.isEmpty()) Text("Brak folderów – odśwież konto.", color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            folders.filter { it.selectable }.forEach { f ->
                SwitchRow(
                    f.role.icon(),
                    f.name,
                    if (f.role == FolderRole.OTHER) f.path else "${f.total} wiadomości",
                    f.sync || f.role == FolderRole.INBOX,
                    enabled = f.role != FolderRole.INBOX,
                ) { Db.setFolderSync(f.id, it) }
            }
        }
        Group("Logowanie") {
            InfoRow(
                Icons.Rounded.Key,
                "Metoda",
                when (a.auth) {
                    AuthType.PASSWORD -> "Hasło"
                    AuthType.OAUTH_GOOGLE -> "Konto Google (OAuth 2.0)"
                    AuthType.OAUTH_MICROSOFT -> "Konto Microsoft (OAuth 2.0)"
                },
            )
            if (a.auth == AuthType.PASSWORD) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    DarkField(password, { password = it }, "Nowe hasło (zostaw puste, by nie zmieniać)", password = true)
                }
            }
            ActionRow(Icons.Rounded.Dns, "Ustawienia serwerów", "${a.imap.host}:${a.imap.port} · ${a.smtp.host}:${a.smtp.port}", trailing = {
                Icon(if (servers) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Palette.TextFaint)
            }) { servers = !servers }
            AnimatedVisibility(servers) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ServerEditor("IMAP", imap)
                    ServerEditor("SMTP", smtp)
                }
            }
            ActionRow(Icons.Rounded.CheckCircle, if (testing) "Sprawdzanie…" else "Sprawdź połączenie", enabled = !testing) {
                testing = true
                scope.launch {
                    val acc = edited()
                    val res = withContext(Dispatchers.IO) { runCatching { MailClient.test(acc) } }
                    testing = false
                    res.onSuccess {
                        save(true)
                        Db.setAccountStatus(id, 0, "")
                        Toast.makeText(context, "Połączenie działa", Toast.LENGTH_SHORT).show()
                    }.onFailure { Toast.makeText(context, it.mailMessage(), Toast.LENGTH_LONG).show() }
                }
            }
        }
        Group(null) {
            ActionRow(Icons.Rounded.Delete, "Usuń konto z aplikacji", "Poczta na serwerze zostaje nietknięta", danger = true) { confirmRemove = true }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            "Usunąć konto?",
            "Konto ${a.email} i pobrane wiadomości znikną z telefonu. Poczta na serwerze zostaje.",
            "Usuń",
            danger = true,
            onDismiss = { confirmRemove = false },
        ) {
            MailClient.disconnect(id)
            Db.deleteAccount(id)
            if (Prefs.defaultAccount == id) Prefs.defaultAccount = Db.accounts().firstOrNull()?.id ?: 0L
            Nav.open(Mailbox.Unified(com.blacksingularities.openmail.data.UnifiedKind.INBOX))
            Nav.home()
        }
    }
}
