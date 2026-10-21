package com.blacksingularities.openmail.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AllInbox
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openmail.R
import com.blacksingularities.openmail.data.Account
import com.blacksingularities.openmail.data.Folder
import com.blacksingularities.openmail.data.FolderRole
import com.blacksingularities.openmail.data.Mailbox
import com.blacksingularities.openmail.data.UnifiedKind

fun FolderRole.icon(): ImageVector = when (this) {
    FolderRole.INBOX -> Icons.Rounded.Inbox
    FolderRole.FLAGGED -> Icons.Rounded.Star
    FolderRole.DRAFTS -> Icons.Rounded.Drafts
    FolderRole.SENT -> Icons.AutoMirrored.Rounded.Send
    FolderRole.ARCHIVE -> Icons.Rounded.Archive
    FolderRole.ALL -> Icons.Rounded.AllInbox
    FolderRole.JUNK -> Icons.Rounded.Report
    FolderRole.TRASH -> Icons.Rounded.Delete
    FolderRole.OTHER -> Icons.Rounded.Folder
}

fun UnifiedKind.icon(): ImageVector = when (this) {
    UnifiedKind.INBOX -> Icons.Rounded.AllInbox
    UnifiedKind.UNREAD -> Icons.Rounded.MarkEmailUnread
    UnifiedKind.FLAGGED -> Icons.Rounded.Star
    UnifiedKind.DRAFTS -> Icons.Rounded.Drafts
    UnifiedKind.SENT -> Icons.AutoMirrored.Rounded.Send
}

/** Dane szuflady: konta z folderami i liczniki wspólnych skrzynek. */
class DrawerData(
    val accounts: List<Account>,
    val folders: Map<Long, List<Folder>>,
    val unified: Map<UnifiedKind, Int>,
)

/** Szuflada: wspólne skrzynki, konta z drzewem folderów, ustawienia. */
@Composable
fun MailDrawer(data: DrawerData, current: Mailbox, onOpen: (Mailbox) -> Unit, onScreen: (Screen) -> Unit) {
    val collapsed = remember { mutableStateMapOf<Long, Boolean>() }
    Column(
        Modifier
            .fillMaxHeight()
            .width(316.dp)
            .background(Palette.Card)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.openmail_mark), null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("OpenMail", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            RoundIcon(Icons.Rounded.Settings, "Ustawienia", size = 40.dp, flat = true) { onScreen(Screen.Settings) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
            if (data.accounts.size > 1) {
                SectionTitle("Wszystkie konta")
                UnifiedKind.entries.forEach { k ->
                    val box = Mailbox.Unified(k)
                    DrawerItem(k.icon(), k.label, data.unified[k] ?: 0, current == box, depth = 0) { onOpen(box) }
                }
                Spacer(Modifier.height(8.dp))
            }
            data.accounts.forEach { a ->
                val isCollapsed = collapsed[a.id] ?: false
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { collapsed[a.id] = !isCollapsed }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(a.displayName.ifBlank { a.email }, a.email, size = 34.dp, color = Color(a.color))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.email, color = Palette.TextFaint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (a.lastError.isNotBlank()) {
                        Icon(Icons.Rounded.Warning, "Błąd konta", tint = Palette.Star, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(Icons.Rounded.ExpandMore, null, tint = Palette.TextFaint, modifier = Modifier.size(20.dp).rotate(if (isCollapsed) -90f else 0f))
                }
                if (!isCollapsed) {
                    val folders = data.folders[a.id].orEmpty()
                    if (folders.isEmpty()) {
                        Text("Pobieranie folderów…", color = Palette.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(start = 54.dp, bottom = 8.dp))
                    }
                    folders.filter { it.selectable }.forEach { f ->
                        val box = Mailbox.Of(f.id)
                        val depth = if (f.role == FolderRole.OTHER) f.depth.coerceAtMost(4) else 0
                        val name = if (f.role == FolderRole.OTHER) f.name.substringAfterLast(f.separator) else f.name
                        val count = if (f.role == FolderRole.DRAFTS) f.total else if (f.role == FolderRole.SENT || f.role == FolderRole.TRASH) 0 else f.unread
                        DrawerItem(f.role.icon(), name, count, current == box, depth, accent = Color(a.color)) { onOpen(box) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        Column(Modifier.padding(10.dp)) {
            DrawerItem(Icons.Rounded.PersonAdd, "Dodaj konto", 0, false, 0) { onScreen(Screen.AccountSetup()) }
            DrawerItem(Icons.Rounded.ManageAccounts, "Zarządzaj kontami", 0, false, 0) { onScreen(Screen.Accounts) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Palette.TextFaint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, count: Int, selected: Boolean, depth: Int, accent: Color = Color.White, onClick: () -> Unit) {
    val tap = rememberTap()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable { tap(onClick) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Palette.Ink else if (depth == 0 && accent != Color.White) accent.copy(alpha = 0.9f) else Palette.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = if (selected) Palette.Ink else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected || count > 0) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) Palette.Ink.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) {
                Text(if (count > 999) "999+" else count.toString(), color = if (selected) Palette.Ink else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
