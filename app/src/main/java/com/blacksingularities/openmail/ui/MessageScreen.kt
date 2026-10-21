package com.blacksingularities.openmail.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.ReplyAll
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.blacksingularities.openmail.core.ImagePolicy
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.Account
import com.blacksingularities.openmail.data.Address
import com.blacksingularities.openmail.data.Attachment
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.data.Folder
import com.blacksingularities.openmail.data.FolderRole
import com.blacksingularities.openmail.data.MessageBody
import com.blacksingularities.openmail.data.MessageHeader
import com.blacksingularities.openmail.mail.Actions
import com.blacksingularities.openmail.mail.Draft
import com.blacksingularities.openmail.mail.MailClient
import com.blacksingularities.openmail.mail.Mime
import com.blacksingularities.openmail.mail.OutgoingAttachment
import com.blacksingularities.openmail.mail.Outbox
import com.blacksingularities.openmail.mail.mailMessage
import com.blacksingularities.openmail.sync.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Zamyka widok wiadomości (na telefonie – powrót, na tablecie – pusty panel). */
private fun closeMessage(embedded: Boolean) {
    if (embedded) Nav.selected = null else Nav.pop()
}

@Composable
fun MessageScreen(id: Long, embedded: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by Db.changes.collectAsState()
    val header by produceState<MessageHeader?>(Db.message(id), id, version) { value = withContext(Dispatchers.IO) { Db.message(id) } }
    val m = header
    if (m == null) {
        LaunchedEffect(Unit) { closeMessage(embedded) }
        Box(Modifier.fillMaxSize().background(Palette.Ink))
        return
    }
    val account by produceState<Account?>(null, m.accountId) { value = withContext(Dispatchers.IO) { Db.account(m.accountId) } }
    val folder by produceState<Folder?>(null, m.folderId, version) { value = withContext(Dispatchers.IO) { Db.folder(m.folderId) } }
    var body by remember(id) { mutableStateOf<MessageBody?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var loadImages by remember(id) { mutableStateOf(Prefs.images == ImagePolicy.ALWAYS || m.from.email.lowercase() in Prefs.trustedSenders) }
    var menu by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var details by remember(id) { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(Prefs.messageTextZoom) }

    if (!embedded) BackHandler { Nav.pop() }

    LaunchedEffect(id, account) {
        val a = account ?: return@LaunchedEffect
        Notifier.cancel(context, id)
        error = null
        val res = withContext(Dispatchers.IO) { runCatching { MailClient.loadBody(a, m) } }
        res.onSuccess { body = it }.onFailure { error = it.mailMessage() }
        if (Prefs.markReadOnOpen && !m.seen) Actions.setSeen(listOf(m), true)
    }
    val thread by produceState(emptyList<MessageHeader>(), m.threadKey, version) {
        value = withContext(Dispatchers.IO) { Db.thread(m.accountId, m.threadKey).filter { it.id != m.id } }
    }

    fun reply(all: Boolean) = Nav.push(Screen.Compose(Outbox.reply(m, body, all)))
    fun forward() {
        val a = account ?: return
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                body?.attachments.orEmpty().filter { !it.inline }.mapNotNull { att ->
                    runCatching { MailClient.downloadAttachment(context, a, m, att) }.getOrNull()?.let {
                        OutgoingAttachment(att.name, att.mime, it.length(), file = it)
                    }
                }
            }
            Nav.push(Screen.Compose(Outbox.forward(m, body, files)))
        }
    }
    fun after() = closeMessage(embedded)

    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        TopBar(
            title = "",
            onBack = { closeMessage(embedded) },
            backIcon = if (embedded) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
        ) {
            if (folder?.role != FolderRole.ARCHIVE && Actions.canArchive(m)) {
                RoundIcon(Icons.Rounded.Archive, "Archiwizuj", flat = true) {
                    Actions.moveToRole(listOf(m), FolderRole.ARCHIVE)
                    Toast.makeText(context, "Zarchiwizowano", Toast.LENGTH_SHORT).show()
                    after()
                }
            }
            RoundIcon(Icons.Rounded.Delete, "Usuń", flat = true) {
                if (Prefs.confirmDelete) confirmDelete = true else {
                    Actions.delete(listOf(m))
                    Toast.makeText(context, "Usunięto", Toast.LENGTH_SHORT).show()
                    after()
                }
            }
            RoundIcon(Icons.Rounded.MarkEmailUnread, "Oznacz jako nieprzeczytaną", flat = true) {
                Actions.setSeen(listOf(m), false)
                after()
            }
            Box {
                RoundIcon(Icons.Rounded.MoreVert, "Więcej", flat = true) { menu = true }
                DropdownMenu(menu, { menu = false }, containerColor = Palette.CardTop, shape = RoundedCornerShape(18.dp)) {
                    MenuItem(if (m.flagged) Icons.Rounded.StarBorder else Icons.Rounded.Star, if (m.flagged) "Usuń gwiazdkę" else "Oznacz gwiazdką") {
                        menu = false
                        Actions.setFlagged(listOf(m), !m.flagged)
                    }
                    MenuItem(Icons.AutoMirrored.Rounded.DriveFileMove, "Przenieś do…") { menu = false; moveDialog = true }
                    if (folder?.role != FolderRole.JUNK) {
                        MenuItem(Icons.Rounded.Report, "Zgłoś jako spam") {
                            menu = false
                            val n = Actions.moveToRole(listOf(m), FolderRole.JUNK)
                            Toast.makeText(context, if (n > 0) "Przeniesiono do spamu" else "Konto nie ma folderu Spam", Toast.LENGTH_SHORT).show()
                            if (n > 0) after()
                        }
                    }
                    MenuItem(Icons.Rounded.TextIncrease, "Większy tekst") { zoom = (zoom + 15).coerceAtMost(250); Prefs.messageTextZoom = zoom }
                    MenuItem(Icons.Rounded.TextDecrease, "Mniejszy tekst") { zoom = (zoom - 15).coerceAtLeast(60); Prefs.messageTextZoom = zoom }
                    MenuItem(Icons.Rounded.Share, "Udostępnij treść") {
                        menu = false
                        val text = "${m.subject}\n\nOd: ${m.from}\nData: ${Fmt.long(m.date)}\n\n" + (body?.text?.ifBlank { Mime.htmlToText(body?.html.orEmpty()) } ?: m.preview)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, m.subject).putExtra(Intent.EXTRA_TEXT, text), "Udostępnij"))
                    }
                    MenuItem(Icons.Rounded.Code, "Pokaż źródło") {
                        menu = false
                        val a = account ?: return@MenuItem
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                runCatching {
                                    val raw = MailClient.rawMessage(a, m)
                                    val f = File(context.cacheDir, "share/zrodlo-${m.id}.eml").apply { parentFile?.mkdirs() }
                                    f.outputStream().use { raw.writeTo(it) }
                                    f
                                }
                            }
                            res.onSuccess { openFile(context, it, "text/plain") }
                                .onFailure { Toast.makeText(context, it.mailMessage(), Toast.LENGTH_LONG).show() }
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 96.dp),
            ) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text(
                        m.subject.ifBlank { "(bez tematu)" },
                        color = Color.White,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        folder?.let { Tag(it.role.icon(), it.name) }
                        account?.let { a -> Tag(null, a.title, Color(a.color)) }
                        if (m.flagged) Tag(Icons.Rounded.Star, "Gwiazdka", Palette.Star)
                    }
                }

                if (thread.isNotEmpty()) ThreadStrip(thread, embedded)

                // Nadawca i szczegóły.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { details = !details }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(m.from.name, m.from.email, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.from.display, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (details) m.from.email else "do: " + (m.to.joinToString { it.display }.ifBlank { "—" }),
                            color = Palette.TextDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(Fmt.short(m.date), color = Palette.TextFaint, fontSize = 12.sp)
                    Icon(if (details) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Palette.TextFaint, modifier = Modifier.size(20.dp))
                }
                AnimatedVisibility(details) {
                    Column(
                        Modifier
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.Card)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DetailLine("Od", listOf(m.from))
                        DetailLine("Do", m.to)
                        if (m.cc.isNotEmpty()) DetailLine("DW", m.cc)
                        if (m.replyTo.isNotEmpty()) DetailLine("Odpowiedź do", m.replyTo)
                        Row {
                            Text("Data", color = Palette.TextFaint, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                            Text(Fmt.long(m.date), color = Color.White, fontSize = 12.sp)
                        }
                        if (m.size > 0) Row {
                            Text("Rozmiar", color = Palette.TextFaint, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                            Text(Fmt.size(m.size.toLong()), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                val b = body
                when {
                    error != null && b == null -> Column(Modifier.padding(18.dp)) {
                        Text("Nie udało się pobrać treści", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(error.orEmpty(), color = Palette.TextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Pill("Spróbuj ponownie", primary = true) {
                            val a = account ?: return@Pill
                            error = null
                            scope.launch {
                                withContext(Dispatchers.IO) { runCatching { MailClient.loadBody(a, m) } }
                                    .onSuccess { body = it }.onFailure { error = it.mailMessage() }
                            }
                        }
                    }
                    b == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    }
                    else -> {
                        if (b.hasRemoteImages && !loadImages && Prefs.images != ImagePolicy.NEVER) {
                            Row(
                                Modifier
                                    .padding(horizontal = 18.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Palette.Card)
                                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Image, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Obrazy z internetu zablokowane (ochrona prywatności)", color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(6.dp))
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Pill("Pokaż", primary = true) { loadImages = true }
                                    Text(
                                        "Zawsze od nadawcy",
                                        color = Palette.TextDim,
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable {
                                            Prefs.trustSender(m.from.email)
                                            loadImages = true
                                        }.padding(4.dp),
                                    )
                                }
                            }
                        }
                        if (b.html.isNotBlank()) {
                            HtmlBody(b.html, loadImages, zoom)
                        } else {
                            PlainBody(b.text, zoom)
                        }
                        val files = b.attachments.filter { !it.inline }
                        if (files.isNotEmpty() && account != null) AttachmentList(account!!, m, files)
                    }
                }
            }

            // Odpowiedz / Odpowiedz wszystkim / Przekaż.
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Palette.CardHigh)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill("Odpowiedz", icon = Icons.AutoMirrored.Rounded.Reply, primary = true) { reply(false) }
                if ((m.to.size + m.cc.size) > 1) Pill("Wszystkim", icon = Icons.AutoMirrored.Rounded.ReplyAll) { reply(true) }
                Pill("Przekaż", icon = Icons.AutoMirrored.Rounded.Forward) { forward() }
            }
        }
    }

    if (moveDialog) {
        val targets = remember(m.accountId) { Db.folders(m.accountId).filter { it.selectable && it.id != m.folderId } }
        PickDialog("Przenieś do folderu", targets, { it.name }, { it.role.icon() }, onDismiss = { moveDialog = false }) { target ->
            Actions.moveTo(listOf(m), target)
            Toast.makeText(context, "Przeniesiono do: ${target.name}", Toast.LENGTH_SHORT).show()
            after()
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Usunąć wiadomość?", "Wiadomość trafi do kosza.", "Usuń", danger = true, onDismiss = { confirmDelete = false }) {
            Actions.delete(listOf(m))
            after()
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = Color.White, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = Palette.TextDim) },
        onClick = onClick,
    )
}

@Composable
private fun Tag(icon: ImageVector?, text: String, color: Color = Palette.TextDim) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        } else {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(color))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = if (icon == null) Color.White else color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun DetailLine(label: String, list: List<Address>) {
    Row {
        Text(label, color = Palette.TextFaint, fontSize = 12.sp, modifier = Modifier.width(92.dp))
        SelectionContainer {
            Text(list.joinToString("\n") { it.toString() }.ifBlank { "—" }, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/** Inne wiadomości wątku – zwinięte, dotknięcie otwiera. */
@Composable
private fun ThreadStrip(thread: List<MessageHeader>, embedded: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(start = 18.dp, end = 18.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Card),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Mail, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Wątek: ${thread.size + 1} wiadomości", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Palette.TextFaint)
        }
        AnimatedVisibility(expanded) {
            Column {
                thread.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (embedded) Nav.selected = t.id else Nav.replace(Screen.Message(t.id)) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(t.from.name, t.from.email, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.from.display, color = if (t.seen) Palette.TextDim else Color.White, fontSize = 13.sp, fontWeight = if (t.seen) FontWeight.Normal else FontWeight.Bold, maxLines = 1)
                            Text(t.preview.ifBlank { t.subject }, color = Palette.TextFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(Fmt.short(t.date), color = Palette.TextFaint, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** Treść tekstowa z klikalnymi linkami i adresami. */
@Composable
private fun PlainBody(text: String, zoom: Int) {
    val linkStyle = TextLinkStyles(SpanStyle(color = Palette.Unread, textDecoration = TextDecoration.Underline))
    val annotated = remember(text) {
        buildAnnotatedString {
            val regex = Regex("(https?://[^\\s<>\"]+)|([\\w.+-]+@[\\w-]+\\.[\\w.-]+)")
            var last = 0
            for (match in regex.findAll(text)) {
                append(text.substring(last, match.range.first))
                val v = match.value.trimEnd('.', ',', ')', ';')
                val url = if (v.startsWith("http")) v else "mailto:$v"
                withLink(LinkAnnotation.Url(url, linkStyle)) { append(v) }
                append(match.value.substring(v.length))
                last = match.range.last + 1
            }
            append(text.substring(last))
        }
    }
    SelectionContainer {
        Text(
            annotated,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = (15f * zoom / 100f).sp,
            lineHeight = (22f * zoom / 100f).sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

/**
 * Treść HTML w WebView bez JavaScriptu i bez dostępu do plików. Zdalne zasoby blokowane, dopóki
 * użytkownik ich nie zezwoli. Linki otwierają się w przeglądarce, mailto: – w edytorze.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlBody(html: String, loadImages: Boolean, zoom: Int) {
    val dark = Prefs.darkMessages && WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
    val doc = remember(html) {
        val d = Mime.sanitize(html)
        d.head().prepend(
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>html,body{margin:0;padding:0;word-wrap:break-word;overflow-wrap:anywhere}" +
                "body{padding:8px 14px 16px 14px;font-family:sans-serif}" +
                "img{max-width:100%!important;height:auto!important}" +
                "table{max-width:100%!important}pre{white-space:pre-wrap}" +
                "blockquote{margin:8px 0;padding:6px 12px;background:rgba(127,127,127,.12);border-radius:8px}</style>",
        )
        d.outerHtml()
    }
    Box(
        Modifier
            .padding(horizontal = if (dark) 0.dp else 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (dark) 0.dp else 18.dp))
            .background(if (dark) Palette.Ink else Color.White),
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setBackgroundColor(if (dark) AColor.BLACK else AColor.WHITE)
                    isVerticalScrollBarEnabled = false
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = false
                    if (dark) WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            openLink(view.context, request.url)
                            return true
                        }
                    }
                }
            },
            update = { w ->
                w.settings.blockNetworkLoads = !loadImages
                w.settings.blockNetworkImage = !loadImages
                w.settings.textZoom = zoom
                val key = "${doc.hashCode()}:$loadImages"
                if (w.tag != key) {
                    w.tag = key
                    w.loadDataWithBaseURL("about:blank", doc, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun openLink(context: Context, uri: Uri) {
    if (uri.scheme.equals("mailto", true)) {
        Mailto.parse(uri)?.let { Nav.push(Screen.Compose(it)) }
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Brak aplikacji do otwarcia linku", Toast.LENGTH_SHORT).show()
    }
}

fun openFile(context: Context, file: File, mime: String, share: Boolean = false) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = if (share) {
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Udostępnij")
    } else {
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Brak aplikacji do otwarcia pliku ${file.extension.uppercase()}", Toast.LENGTH_LONG).show()
    }
}

private fun mimeIcon(mime: String, name: String): ImageVector = when {
    mime.startsWith("image/") -> Icons.Rounded.Image
    mime == "application/pdf" || name.endsWith(".pdf", true) -> Icons.Rounded.PictureAsPdf
    mime.startsWith("audio/") -> Icons.Rounded.AudioFile
    mime.startsWith("video/") -> Icons.Rounded.VideoFile
    mime == "text/calendar" || name.endsWith(".ics", true) -> Icons.Rounded.Event
    mime.contains("zip") || mime.contains("compressed") || name.endsWith(".rar", true) || name.endsWith(".7z", true) -> Icons.Rounded.FolderZip
    mime.startsWith("text/") || mime.contains("document") || mime.contains("msword") || mime.contains("sheet") || mime.contains("presentation") -> Icons.Rounded.Description
    mime == "message/rfc822" -> Icons.Rounded.Mail
    else -> Icons.Rounded.InsertDriveFile
}

/** Załączniki: dotknięcie pobiera i otwiera, przytrzymanie – udostępnia. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AttachmentList(a: Account, m: MessageHeader, files: List<Attachment>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    fun fetch(att: Attachment, share: Boolean) {
        if (busy[att.part] == true) return
        busy[att.part] = true
        scope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { MailClient.downloadAttachment(context, a, m, att) } }
            busy[att.part] = false
            res.onSuccess { openFile(context, it, att.mime.ifBlank { "application/octet-stream" }, share) }
                .onFailure { Toast.makeText(context, "Nie pobrano: ${it.mailMessage()}", Toast.LENGTH_LONG).show() }
        }
    }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(
            "ZAŁĄCZNIKI (${files.size})",
            color = Palette.TextFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            files.forEach { att ->
                Row(
                    Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Card)
                        .combinedClickable(onClick = { fetch(att, false) }, onLongClick = { fetch(att, true) })
                        .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        if (busy[att.part] == true) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(mimeIcon(att.mime, att.name), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(att.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(Fmt.size(att.size), att.mime.substringAfter('/').uppercase().take(10)).filter { it.isNotBlank() }.joinToString(" · "),
                            color = Palette.TextFaint,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Visibility, null, tint = Palette.TextFaint, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Linki mailto: (RFC 6068) → szkic nowej wiadomości. */
object Mailto {
    fun parse(uri: Uri): Draft? {
        val raw = uri.toString()
        if (!raw.startsWith("mailto:", true)) return null
        val rest = raw.substring(7)
        val to = Uri.decode(rest.substringBefore('?'))
        val params = rest.substringAfter('?', "").split('&').filter { '=' in it }.associate {
            it.substringBefore('=').lowercase() to Uri.decode(it.substringAfter('='))
        }
        fun list(s: String?) = s.orEmpty().split(',', ';').map { it.trim() }.filter { '@' in it }.map { Address(it) }
        val accountId = Prefs.defaultAccount.takeIf { id -> Db.account(id) != null } ?: Db.accounts().firstOrNull()?.id ?: return null
        return Draft(
            accountId = accountId,
            to = list(to) + list(params["to"]),
            cc = list(params["cc"]),
            bcc = list(params["bcc"]),
            subject = params["subject"].orEmpty(),
            body = params["body"].orEmpty(),
        )
    }
}
