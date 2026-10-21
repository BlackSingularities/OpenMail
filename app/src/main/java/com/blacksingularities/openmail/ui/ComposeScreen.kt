package com.blacksingularities.openmail.ui

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.Account
import com.blacksingularities.openmail.data.Address
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.mail.ComposeMode
import com.blacksingularities.openmail.mail.Draft
import com.blacksingularities.openmail.mail.Mime
import com.blacksingularities.openmail.mail.OutgoingAttachment
import com.blacksingularities.openmail.mail.Outbox
import com.blacksingularities.openmail.mail.mailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Zamienia wpisany tekst („Jan <jan@x.pl>”, „a@b.pl, c@d.pl”) na adresy. */
fun parseAddresses(text: String): List<Address> = text.split(',', ';', '\n').mapNotNull { part ->
    val p = part.trim()
    if (p.isEmpty()) return@mapNotNull null
    val m = Regex("^\"?([^\"<]*)\"?\\s*<([^>]+)>$").find(p)
    if (m != null) Address(m.groupValues[2].trim(), m.groupValues[1].trim()) else Address(p.trim('<', '>'))
}

fun isValidEmail(s: String) = EMAIL.matches(s.trim())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(initial: Draft) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by produceState(emptyList<Account>()) { value = withContext(Dispatchers.IO) { Db.accounts() } }
    var accountId by remember { mutableStateOf(initial.accountId) }
    val to = remember { mutableStateOf(initial.to) }
    val cc = remember { mutableStateOf(initial.cc) }
    val bcc = remember { mutableStateOf(initial.bcc) }
    var showCc by remember { mutableStateOf(initial.cc.isNotEmpty() || initial.bcc.isNotEmpty()) }
    var subject by remember { mutableStateOf(initial.subject) }
    var body by remember { mutableStateOf(initial.body) }
    var quoted by remember { mutableStateOf(initial.quotedHtml) }
    var showQuote by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(initial.attachments) }
    var pickAccount by remember { mutableStateOf(false) }
    var askClose by remember { mutableStateOf(false) }
    var askEmptySubject by remember { mutableStateOf(false) }
    var askSend by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }
    val toFocus = remember { FocusRequester() }
    val account = accounts.firstOrNull { it.id == accountId }

    LaunchedEffect(Unit) {
        runCatching { if (initial.to.isEmpty()) toFocus.requestFocus() else bodyFocus.requestFocus() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = withContext(Dispatchers.IO) { uris.map { Outbox.describe(context, it) } }
                attachments = attachments + added
                val total = attachments.sumOf { it.size.coerceAtLeast(0) }
                if (total > 25L * 1024 * 1024) Toast.makeText(context, "Załączniki mają ${Fmt.size(total)} – wiele serwerów odrzuca wiadomości powyżej 25 MB", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun current() = initial.copy(
        accountId = accountId,
        to = to.value,
        cc = cc.value,
        bcc = bcc.value,
        subject = subject,
        body = body,
        quotedHtml = quoted,
        attachments = attachments,
    )

    val changed = to.value != initial.to || cc.value != initial.cc || subject != initial.subject || body != initial.body || attachments != initial.attachments

    fun send() {
        val d = current()
        val all = d.to + d.cc + d.bcc
        val bad = all.filter { !isValidEmail(it.email) }
        when {
            all.isEmpty() -> Toast.makeText(context, "Dodaj co najmniej jednego adresata", Toast.LENGTH_SHORT).show()
            bad.isNotEmpty() -> Toast.makeText(context, "Nieprawidłowy adres: ${bad.first().email}", Toast.LENGTH_LONG).show()
            else -> {
                Outbox.send(context, d)
                Nav.pop()
            }
        }
    }

    fun trySend() {
        when {
            subject.isBlank() -> askEmptySubject = true
            Prefs.confirmSend -> askSend = true
            else -> send()
        }
    }

    fun saveDraft(close: Boolean) {
        saving = true
        scope.launch {
            val ok = runCatching { Outbox.saveDraft(context, current()) }
            saving = false
            ok.onSuccess { saved ->
                Toast.makeText(context, if (saved) "Zapisano wersję roboczą" else "Konto nie ma folderu wersji roboczych", Toast.LENGTH_SHORT).show()
                if (saved && close) Nav.pop()
            }.onFailure { Toast.makeText(context, "Nie zapisano: ${it.mailMessage()}", Toast.LENGTH_LONG).show() }
        }
    }

    BackHandler { if (changed) askClose = true else Nav.pop() }

    Column(Modifier.fillMaxSize().background(Palette.Ink).imePadding()) {
        TopBar(
            title = when (initial.mode) {
                ComposeMode.REPLY, ComposeMode.REPLY_ALL -> "Odpowiedź"
                ComposeMode.FORWARD -> "Przekazanie"
                ComposeMode.DRAFT -> "Wersja robocza"
                ComposeMode.NEW -> "Nowa wiadomość"
            },
            onBack = { if (changed) askClose = true else Nav.pop() },
            backIcon = Icons.Rounded.Close,
        ) {
            if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            RoundIcon(Icons.Rounded.Save, "Zapisz wersję roboczą", flat = true) { saveDraft(false) }
            RoundIcon(Icons.Rounded.AttachFile, "Dołącz plik", flat = true) { picker.launch(arrayOf("*/*")) }
            RoundIcon(Icons.AutoMirrored.Rounded.Send, "Wyślij", active = true) { trySend() }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Column(Modifier.widthIn(max = 820.dp).align(Alignment.CenterHorizontally)) {
                // Nadawca
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = accounts.size > 1) { pickAccount = true }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Od", color = Palette.TextFaint, fontSize = 14.sp, modifier = Modifier.width(44.dp))
                    if (account != null) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color(account.color)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account.displayName.ifBlank { account.email }, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(account.email, color = Palette.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (accounts.size > 1) Icon(Icons.Rounded.ExpandMore, null, tint = Palette.TextFaint)
                    }
                }
                Divider()
                RecipientField("Do", to.value, { to.value = it }, Modifier.focusRequester(toFocus)) {
                    Text(
                        if (showCc) "" else "DW/UDW",
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showCc = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Divider()
                AnimatedVisibility(showCc) {
                    Column {
                        RecipientField("DW", cc.value, { cc.value = it })
                        Divider()
                        RecipientField("UDW", bcc.value, { bcc.value = it })
                        Divider()
                    }
                }
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { runCatching { bodyFocus.requestFocus() } }),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (subject.isEmpty()) Text("Temat", color = Palette.TextFaint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            inner()
                        },
                    )
                }
                Divider()

                if (attachments.isNotEmpty()) {
                    FlowRow(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        attachments.forEach { a -> AttachmentChip(a) { attachments = attachments - a } }
                    }
                    Divider()
                }

                BasicTextField(
                    value = body,
                    onValueChange = { body = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 23.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .focusRequester(bodyFocus),
                    decorationBox = { inner ->
                        if (body.isEmpty()) Text("Treść wiadomości", color = Palette.TextFaint, fontSize = 16.sp)
                        inner()
                    },
                )
                if (account?.signature?.isNotBlank() == true && !body.contains(account.signature)) {
                    Text(
                        "-- \n" + account.signature,
                        color = Palette.TextFaint,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }

                if (quoted.isNotBlank()) {
                    Column(
                        Modifier
                            .padding(14.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Palette.Card),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable { showQuote = !showQuote }.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.FormatQuote, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (initial.mode == ComposeMode.FORWARD) "Przekazywana wiadomość" else "Cytowana wiadomość",
                                color = Palette.TextDim,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(if (showQuote) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Palette.TextFaint)
                            RoundIcon(Icons.Rounded.Close, "Usuń cytat", size = 36.dp, flat = true) { quoted = "" }
                        }
                        AnimatedVisibility(showQuote) {
                            Text(
                                initial.quoteHeader + "\n\n" + Mime.htmlToText(quoted).take(4000),
                                color = Palette.TextFaint,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (pickAccount) {
        PickDialog("Wyślij z konta", accounts, { it.title }, { Icons.Rounded.AccountCircle }, subtitle = { it.email }, onDismiss = { pickAccount = false }) {
            accountId = it.id
        }
    }
    if (askClose) {
        DarkDialog("Zapisać wersję roboczą?", { askClose = false }, buttons = {
            Pill("Odrzuć", danger = true) {
                askClose = false
                Nav.pop()
            }
            Pill("Zapisz", primary = true) {
                askClose = false
                saveDraft(true)
            }
        }) {
            Text("Wiadomość nie została wysłana. Możesz zachować ją w wersjach roboczych na serwerze.", color = Palette.TextDim, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
    if (askEmptySubject) {
        ConfirmDialog("Brak tematu", "Wysłać wiadomość bez tematu?", "Wyślij", onDismiss = { askEmptySubject = false }) {
            if (Prefs.confirmSend) askSend = true else send()
        }
    }
    if (askSend) {
        val n = to.value.size + cc.value.size + bcc.value.size
        ConfirmDialog("Wysłać?", "Wiadomość trafi do $n ${if (n == 1) "adresata" else "adresatów"}.", "Wyślij", onDismiss = { askSend = false }) { send() }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Palette.Line, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))

@Composable
private fun AttachmentChip(a: OutgoingAttachment, onRemove: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.CardHigh)
            .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.InsertDriveFile, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(a.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.size >= 0) Text(Fmt.size(a.size), color = Palette.TextFaint, fontSize = 11.sp)
        }
        RoundIcon(Icons.Rounded.Close, "Usuń załącznik", size = 34.dp, flat = true, onClick = onRemove)
    }
}

/**
 * Pole adresatów: adresy jako pigułki, wpisywanie z podpowiedziami z historii i odebranej poczty.
 * Przecinek, spacja lub Enter zatwierdza adres.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipientField(
    label: String,
    value: List<Address>,
    onChange: (List<Address>) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val suggestions by produceState(emptyList<Address>(), text) {
        this.value = if (text.length >= 1) withContext(Dispatchers.IO) { Db.suggest(text) } else emptyList()
    }
    fun commit() {
        val parsed = parseAddresses(text)
        if (parsed.isNotEmpty()) onChange((value + parsed).distinctBy { it.email.lowercase() })
        text = ""
    }
    Column {
        Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Palette.TextFaint, fontSize = 14.sp, modifier = Modifier.width(44.dp))
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                value.forEach { a ->
                    val valid = isValidEmail(a.email)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (valid) Palette.CardHigh else Palette.Danger.copy(alpha = 0.3f))
                            .clickable { onChange(value - a) }
                            .padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(a.name, a.email, size = 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(a.display, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Close, "Usuń ${a.email}", tint = Palette.TextFaint, modifier = Modifier.size(14.dp))
                    }
                }
                BasicTextField(
                    value = text,
                    onValueChange = { v ->
                        if (v.endsWith(",") || v.endsWith(";") || (v.endsWith(" ") && '@' in v && '<' !in v)) {
                            text = v.dropLast(1)
                            commit()
                        } else {
                            text = v
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { commit() }),
                    modifier = modifier
                        .widthIn(min = 120.dp)
                        .padding(vertical = 6.dp)
                        .onFocusChanged {
                            if (focused && !it.isFocused) commit()
                            focused = it.isFocused
                        },
                )
            }
            trailing()
        }
        if (focused && suggestions.isNotEmpty() && text.isNotBlank()) {
            Column(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.CardHigh),
            ) {
                suggestions.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChange((value + s).distinctBy { it.email.lowercase() })
                                text = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(s.name, s.email, size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            if (s.name.isNotBlank()) Text(s.name, color = Color.White, fontSize = 14.sp, maxLines = 1)
                            Text(s.email, color = Palette.TextDim, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
