package com.blacksingularities.openmail.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openmail.R
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.core.SwipeAction
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.data.FolderRole
import com.blacksingularities.openmail.data.ListFilter
import com.blacksingularities.openmail.data.Mailbox
import com.blacksingularities.openmail.data.MessageHeader
import com.blacksingularities.openmail.data.UnifiedKind
import com.blacksingularities.openmail.mail.Actions
import com.blacksingularities.openmail.mail.Draft
import com.blacksingularities.openmail.mail.MailClient
import com.blacksingularities.openmail.mail.Outbox
import com.blacksingularities.openmail.mail.mailMessage
import com.blacksingularities.openmail.sync.Sync
import com.blacksingularities.openmail.update.UpdateManager
import com.blacksingularities.openmail.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Wiersz listy: wiadomość albo cały wątek (najnowsza wiadomość + liczba). */
private class ListItem(val m: MessageHeader, val count: Int)

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val version by Db.changes.collectAsState()
    val drawerData by produceState(DrawerData(emptyList(), emptyMap(), emptyMap()), version) {
        value = withContext(Dispatchers.IO) {
            val accounts = Db.accounts()
            DrawerData(
                accounts,
                accounts.associate { it.id to Db.folders(it.id) },
                UnifiedKind.entries.associateWith { Db.countUnified(it) },
            )
        }
    }
    val loaded by produceState(false, version) { value = withContext(Dispatchers.IO) { Db.accounts(); true } }

    if (loaded && drawerData.accounts.isEmpty()) {
        Welcome()
        return
    }

    // Skrzynka konkretnego konta, a konto usunięte → wróć do wspólnej.
    val box = Nav.mailbox
    LaunchedEffect(box, drawerData) {
        if (box is Mailbox.Of && drawerData.accounts.isNotEmpty() && drawerData.folders.values.flatten().none { it.id == box.folderId }) {
            val inbox = drawerData.folders.values.flatten().firstOrNull { it.role == FolderRole.INBOX }
            Nav.open(if (drawerData.accounts.size == 1 && inbox != null) Mailbox.Of(inbox.id) else Mailbox.Unified(UnifiedKind.INBOX))
        }
        if (box is Mailbox.Unified && drawerData.accounts.size == 1) {
            val inbox = drawerData.folders.values.flatten().firstOrNull { it.role == FolderRole.INBOX }
            if (inbox != null && box.kind == UnifiedKind.INBOX) Nav.open(Mailbox.Of(inbox.id))
        }
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            MailDrawer(
                drawerData,
                Nav.mailbox,
                onOpen = {
                    Nav.open(it)
                    scope.launch { drawer.close() }
                    if (it is Mailbox.Of) Sync.refreshFolder(context, it.folderId)
                },
                onScreen = {
                    scope.launch { drawer.close() }
                    Nav.push(it)
                },
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Ink)) {
            val wide = maxWidth >= 840.dp
            val listWidth = if (maxWidth >= 1200.dp) 460.dp else 400.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(listWidth).fillMaxHeight()) {
                        MailList(drawerData, wide = true) { scope.launch { drawer.open() } }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val sel = Nav.selected
                        if (sel != null) {
                            MessageScreen(sel, embedded = true)
                        } else {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                EmptyState(Icons.Rounded.Inbox, "Wybierz wiadomość", "Treść pojawi się tutaj, obok listy.")
                            }
                        }
                    }
                }
            } else {
                MailList(drawerData, wide = false) { scope.launch { drawer.open() } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MailList(data: DrawerData, wide: Boolean, openDrawer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by Db.changes.collectAsState()
    val box = Nav.mailbox
    var filter by rememberSaveable { mutableStateOf(ListFilter.ALL) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var everywhere by rememberSaveable { mutableStateOf(false) }
    var limit by remember(box) { mutableIntStateOf(150) }
    var serverResults by remember(box, query) { mutableStateOf<List<Long>?>(null) }
    var serverBusy by remember { mutableStateOf(false) }
    val selection: SnapshotStateList<Long> = remember { mutableStateListOf() }
    var moveDialog by remember { mutableStateOf<List<MessageHeader>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<MessageHeader>?>(null) }

    val folder = (box as? Mailbox.Of)?.let { b -> data.folders.values.flatten().firstOrNull { it.id == b.folderId } }
    val account = folder?.let { f -> data.accounts.firstOrNull { it.id == f.accountId } }
    val title = when (box) {
        is Mailbox.Unified -> box.kind.label
        is Mailbox.Of -> folder?.name ?: "Folder"
    }
    val subtitle = when {
        account != null -> account.title
        else -> "${data.accounts.size} ${if (data.accounts.size == 1) "konto" else if (data.accounts.size in 2..4) "konta" else "kont"}"
    }
    val showRecipient = folder?.role == FolderRole.SENT || folder?.role == FolderRole.DRAFTS ||
        (box as? Mailbox.Unified)?.kind.let { it == UnifiedKind.SENT || it == UnifiedKind.DRAFTS }

    val items by produceState(emptyList<ListItem>(), version, box, filter, query, everywhere, limit, serverResults) {
        value = withContext(Dispatchers.IO) {
            val q = if (searching) query else ""
            val list = when {
                serverResults != null -> serverResults!!.mapNotNull { Db.message(it) }.sortedByDescending { it.date }
                everywhere && q.isNotBlank() -> Db.searchEverywhere(q, 300)
                else -> Db.messages(box, filter, q, limit)
            }
            if (Prefs.groupThreads && q.isBlank()) {
                list.groupBy { it.accountId to it.threadKey.ifBlank { "#${it.id}" } }
                    .map { (_, msgs) -> ListItem(msgs.first().let { first -> msgs.firstOrNull { !it.seen }?.let { first.copy(seen = false) } ?: first }, msgs.size) }
            } else {
                list.map { ListItem(it, 1) }
            }
        }
    }
    val byId = remember(items) { items.associateBy { it.m.id } }
    val selectedMsgs = selection.mapNotNull { byId[it]?.m }
    LaunchedEffect(items) { selection.removeAll { it !in byId } }

    val refreshing = when (box) {
        is Mailbox.Of -> box.folderId in Sync.busyFolders || (account?.id in Sync.busyAccounts)
        else -> Sync.busyAccounts.isNotEmpty()
    }
    fun refresh() {
        when (box) {
            is Mailbox.Of -> Sync.refreshFolder(context, box.folderId)
            else -> Sync.refreshAll(context)
        }
    }

    BackHandler(selection.isNotEmpty()) { selection.clear() }
    BackHandler(searching && selection.isEmpty()) {
        searching = false
        query = ""
        everywhere = false
        serverResults = null
    }

    fun openMessage(m: MessageHeader) {
        if (m.draft || Db.folder(m.folderId)?.role == FolderRole.DRAFTS) {
            scope.launch {
                val body = withContext(Dispatchers.IO) {
                    Db.account(m.accountId)?.let { a -> runCatching { MailClient.loadBody(a, m) }.getOrNull() }
                }
                Nav.push(Screen.Compose(Outbox.fromDraft(m, body)))
            }
            return
        }
        if (wide) Nav.selected = m.id else Nav.push(Screen.Message(m.id))
    }

    fun act(action: SwipeAction, list: List<MessageHeader>) {
        when (action) {
            SwipeAction.NONE -> Unit
            SwipeAction.READ -> Actions.setSeen(list, list.any { !it.seen })
            SwipeAction.STAR -> Actions.setFlagged(list, list.any { !it.flagged })
            SwipeAction.ARCHIVE -> {
                val n = Actions.moveToRole(list, FolderRole.ARCHIVE)
                Toast.makeText(context, if (n > 0) "Zarchiwizowano ($n)" else "Konto nie ma folderu Archiwum", Toast.LENGTH_SHORT).show()
            }
            SwipeAction.SPAM -> {
                val n = Actions.moveToRole(list, FolderRole.JUNK)
                Toast.makeText(context, if (n > 0) "Przeniesiono do spamu ($n)" else "Konto nie ma folderu Spam", Toast.LENGTH_SHORT).show()
            }
            SwipeAction.DELETE -> if (Prefs.confirmDelete) confirmDelete = list else {
                Actions.delete(list)
                Toast.makeText(context, if (list.size == 1) "Usunięto wiadomość" else "Usunięto ${list.size} wiadomości", Toast.LENGTH_SHORT).show()
            }
        }
        if (list.any { it.id == Nav.selected } && action in setOf(SwipeAction.ARCHIVE, SwipeAction.DELETE, SwipeAction.SPAM)) Nav.selected = null
    }

    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    // Doczytywanie starszych przy końcu listy.
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(nearEnd, items.size) {
        if (items.size >= limit && nearEnd >= items.size - 10) limit += 150
    }
    LaunchedEffect(box) { listState.scrollToItem(0) }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = when {
                    selection.isNotEmpty() -> 2
                    searching -> 1
                    else -> 0
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "bar",
            ) { mode ->
                when (mode) {
                    2 -> SelectionBar(
                        count = selection.size,
                        allRead = selectedMsgs.all { it.seen },
                        allStarred = selectedMsgs.all { it.flagged },
                        onClose = { selection.clear() },
                        onAll = { selection.clear(); selection.addAll(items.map { it.m.id }) },
                        onRead = { Actions.setSeen(selectedMsgs, selectedMsgs.any { !it.seen }); selection.clear() },
                        onStar = { Actions.setFlagged(selectedMsgs, selectedMsgs.any { !it.flagged }); selection.clear() },
                        onArchive = { act(SwipeAction.ARCHIVE, selectedMsgs); selection.clear() },
                        onDelete = { act(SwipeAction.DELETE, selectedMsgs); selection.clear() },
                        onMove = { moveDialog = selectedMsgs },
                    )
                    1 -> SearchBar(
                        query = query,
                        onQuery = { query = it; serverResults = null },
                        onClose = {
                            searching = false
                            query = ""
                            everywhere = false
                            serverResults = null
                        },
                    )
                    else -> TopBar(title, subtitle, onBack = openDrawer, backIcon = Icons.Rounded.Menu) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        RoundIcon(Icons.Rounded.Search, "Szukaj") { searching = true }
                        val defaultAccount = data.accounts.firstOrNull { it.id == Prefs.defaultAccount } ?: data.accounts.firstOrNull()
                        if (defaultAccount != null && !wide) {
                            Box(Modifier.pressable { Nav.push(Screen.Accounts) }) {
                                Avatar(defaultAccount.displayName.ifBlank { defaultAccount.email }, defaultAccount.email, size = 40.dp, color = Color(defaultAccount.color))
                            }
                        }
                    }
                }
            }

            if (searching) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip("W tej skrzynce", !everywhere && serverResults == null) { everywhere = false; serverResults = null }
                    FilterChip("Wszędzie", everywhere) { everywhere = true; serverResults = null }
                    if (folder != null && account != null) {
                        FilterChip("Na serwerze", serverResults != null, icon = Icons.Rounded.CloudSync) {
                            if (query.isBlank()) return@FilterChip
                            serverBusy = true
                            scope.launch {
                                val res = withContext(Dispatchers.IO) { runCatching { MailClient.search(account, folder, query.trim()) } }
                                serverBusy = false
                                res.onSuccess { serverResults = it; everywhere = false }
                                    .onFailure { Toast.makeText(context, "Wyszukiwanie nieudane: ${it.mailMessage()}", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }
                }
                if (serverBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 14.dp), color = Color.White, trackColor = Palette.CardHigh)
            } else if (selection.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ListFilter.entries.forEach { f ->
                        val icon = when (f) {
                            ListFilter.ALL -> null
                            ListFilter.UNREAD -> Icons.Rounded.MarkEmailUnread
                            ListFilter.FLAGGED -> Icons.Rounded.Star
                            ListFilter.ATTACHMENTS -> Icons.Rounded.AttachFile
                        }
                        FilterChip(f.label, filter == f, icon = icon) { filter = f }
                    }
                }
            }

            AccountErrors(data)
            UpdateBanner()

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refresh() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (items.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().clickable(enabled = false) {},
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when {
                            searching && query.isNotBlank() -> EmptyState(Icons.Rounded.SearchOff, "Brak wyników", "Spróbuj innych słów albo wyszukaj na serwerze.")
                            searching -> EmptyState(Icons.Rounded.Search, "Szukaj w poczcie", "Temat, nadawca, adresat albo fragment treści.")
                            refreshing -> EmptyState(Icons.Rounded.CloudSync, "Pobieranie poczty…", "Pierwsza synchronizacja może chwilę potrwać.")
                            filter != ListFilter.ALL -> EmptyState(Icons.Rounded.Inbox, "Nic tu nie ma", "Żadna wiadomość nie pasuje do filtra „${filter.label}”.", action = "Pokaż wszystkie") { filter = ListFilter.ALL }
                            else -> EmptyState(Icons.Rounded.Inbox, "Pusto", "W tej skrzynce nie ma wiadomości.", action = "Odśwież") { refresh() }
                        }
                    }
                } else {
                    val multi = data.accounts.size > 1 && box is Mailbox.Unified
                    val colors = remember(data.accounts) { data.accounts.associate { it.id to Color(it.color) } }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(items, key = { it.m.id }) { item ->
                            val m = item.m
                            MessageRow(
                                m = m,
                                selected = m.id in selection,
                                selectionMode = selection.isNotEmpty(),
                                open = wide && Nav.selected == m.id,
                                accountColor = if (multi) colors[m.accountId] else null,
                                threadCount = item.count,
                                showRecipient = showRecipient,
                                onClick = {
                                    if (selection.isNotEmpty()) {
                                        if (m.id in selection) selection.remove(m.id) else selection.add(m.id)
                                    } else {
                                        openMessage(m)
                                    }
                                },
                                onLongClick = { if (m.id in selection) selection.remove(m.id) else selection.add(m.id) },
                                onStar = { Actions.setFlagged(listOf(m), !m.flagged) },
                                onSwipe = { act(it, listOf(m)) },
                            )
                        }
                        item { Spacer(Modifier.height(96.dp).windowInsetsPadding(WindowInsets.navigationBars)) }
                    }
                }
            }
        }

        // Przycisk „Napisz” i okno cofania wysyłki.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(selection.isEmpty() && !searching, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                ComposeFab(expanded = fabExpanded) {
                    val accountId = account?.id ?: Prefs.defaultAccount.takeIf { id -> data.accounts.any { it.id == id } } ?: data.accounts.first().id
                    Nav.push(Screen.Compose(Draft(accountId = accountId)))
                }
            }
        }
        UndoSendBar(Modifier.align(Alignment.BottomCenter))
    }

    moveDialog?.let { list ->
        val accountIds = list.map { it.accountId }.toSet()
        if (accountIds.size != 1) {
            LaunchedEffect(Unit) {
                Toast.makeText(context, "Przenoszenie działa w obrębie jednego konta", Toast.LENGTH_SHORT).show()
                moveDialog = null
            }
        } else {
            val targets = data.folders[accountIds.first()].orEmpty().filter { f -> f.selectable && list.none { it.folderId == f.id } }
            PickDialog(
                "Przenieś do folderu",
                targets,
                label = { it.name },
                icon = { it.role.icon() },
                subtitle = { if (it.role == FolderRole.OTHER && it.path != it.name) it.path else null },
                onDismiss = { moveDialog = null },
            ) { target ->
                Actions.moveTo(list, target)
                selection.clear()
                Toast.makeText(context, "Przeniesiono do: ${target.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    confirmDelete?.let { list ->
        ConfirmDialog(
            "Usunąć?",
            if (list.size == 1) "Wiadomość trafi do kosza (z kosza – zostanie usunięta trwale)." else "Wiadomości (${list.size}) trafią do kosza.",
            "Usuń",
            danger = true,
            onDismiss = { confirmDelete = null },
        ) { Actions.delete(list) }
    }
}

@Composable
private fun ComposeFab(expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(horizontal = if (expanded) 20.dp else 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Edit, "Napisz", tint = Palette.Ink, modifier = Modifier.size(22.dp))
        AnimatedVisibility(expanded) {
            Text("Napisz", color = Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** Pasek „Wysyłanie… Cofnij” w czasie opóźnienia wysyłki. */
@Composable
fun UndoSendBar(modifier: Modifier = Modifier) {
    val s = Outbox.sending
    var left by remember(s) { mutableIntStateOf(0) }
    LaunchedEffect(s) {
        while (s != null) {
            left = ((s.until - System.currentTimeMillis()) / 1000.0).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(0)
            kotlinx.coroutines.delay(250)
        }
    }
    AnimatedVisibility(s != null, modifier = modifier, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.CardTop)
                .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Wysyłanie za $left s…", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.width(16.dp))
            Pill("Cofnij", primary = true) { Outbox.undo() }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.CardHigh)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(Icons.Rounded.Close, "Zamknij wyszukiwanie", flat = true, size = 42.dp, onClick = onClose)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp).focusRequester(focus),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Szukaj w poczcie", color = Palette.TextFaint, fontSize = 16.sp)
                inner()
            },
        )
        if (query.isNotEmpty()) RoundIcon(Icons.Rounded.Close, "Wyczyść", flat = true, size = 38.dp) { onQuery("") }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allRead: Boolean,
    allStarred: Boolean,
    onClose: () -> Unit,
    onAll: () -> Unit,
    onRead: () -> Unit,
    onStar: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(Icons.Rounded.Close, "Zakończ zaznaczanie", onClick = onClose)
        Text(count.toString(), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp).weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoundIcon(Icons.Rounded.SelectAll, "Zaznacz wszystkie", flat = true, onClick = onAll)
            RoundIcon(if (allRead) Icons.Rounded.MarkEmailUnread else Icons.Rounded.MarkEmailRead, if (allRead) "Oznacz jako nieprzeczytane" else "Oznacz jako przeczytane", flat = true, onClick = onRead)
            RoundIcon(if (allStarred) Icons.Rounded.StarBorder else Icons.Rounded.Star, "Gwiazdka", flat = true, onClick = onStar)
            RoundIcon(Icons.AutoMirrored.Rounded.DriveFileMove, "Przenieś", flat = true, onClick = onMove)
            RoundIcon(Icons.Rounded.Archive, "Archiwizuj", flat = true, onClick = onArchive)
            RoundIcon(Icons.Rounded.Delete, "Usuń", flat = true, tint = Palette.Danger, onClick = onDelete)
        }
    }
}

/** Ostrzeżenia o kontach, których nie udało się zsynchronizować. */
@Composable
private fun AccountErrors(data: DrawerData) {
    val broken = data.accounts.filter { it.lastError.isNotBlank() }
    broken.take(2).forEach { a ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Star.copy(alpha = 0.12f))
                .clickable { Nav.push(Screen.AccountEdit(a.id)) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Warning, null, tint = Palette.Star, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${a.title}: brak synchronizacji", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.lastError, color = Palette.TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Baner nowej wersji: jedno dotknięcie pobiera APK z GitHuba i instaluje go. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val u = UpdateManager.state
    val visible = !UpdateManager.bannerHidden && (u is UpdateState.Available || u is UpdateState.Downloading || u is UpdateState.Ready || u is UpdateState.Installing)
    AnimatedVisibility(visible) {
        val (title, action) = when (u) {
            is UpdateState.Available -> "Nowa wersja ${u.info.version}" to "Aktualizuj"
            is UpdateState.Downloading -> "Pobieranie ${(u.progress * 100).roundToInt()}%" to null
            is UpdateState.Ready -> "Wersja ${u.info.version} pobrana" to "Zainstaluj"
            is UpdateState.Installing -> "Instalowanie ${u.info.version}…" to null
            else -> "" to null
        }
        val notes = (u as? UpdateState.Available)?.info?.notes
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.CardHigh)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (u is UpdateState.Available) "Pobierze się i zainstaluje automatycznie" else "Nie zamykaj aplikacji",
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                    )
                }
                if (action != null) Pill(action, primary = true) { UpdateManager.updateNow(context) }
                if (u is UpdateState.Available) {
                    RoundIcon(Icons.Rounded.Close, "Później", size = 34.dp, flat = true) { UpdateManager.bannerHidden = true }
                }
            }
            if (u is UpdateState.Downloading) {
                LinearProgressIndicator(
                    progress = { u.progress },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            if (!notes.isNullOrBlank()) {
                Text(notes, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 34.dp))
            }
        }
    }
}

/** Ekran powitalny bez kont. */
@Composable
private fun Welcome() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.openmail_mark), null, modifier = Modifier.size(132.dp))
        Spacer(Modifier.height(20.dp))
        Text("OpenMail", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Otwarty klient poczty dla wszystkich Twoich skrzynek – Gmail, Outlook, iCloud, WP, Onet, Interia i każdy serwer IMAP.",
            color = Palette.TextDim,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                "Wiele kont i wspólna skrzynka odbiorcza",
                "Powiadomienia i synchronizacja w tle",
                "Hasła szyfrowane w Android Keystore",
                "Bez reklam, bez śledzenia, open source",
            ).forEach {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(12.dp))
                    Text(it, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Pill("Dodaj konto", primary = true, modifier = Modifier.fillMaxWidth()) { Nav.push(Screen.AccountSetup()) }
        Spacer(Modifier.height(10.dp))
        Pill("Ustawienia", modifier = Modifier.fillMaxWidth()) { Nav.push(Screen.Settings) }
        UpdateBanner()
    }
}
