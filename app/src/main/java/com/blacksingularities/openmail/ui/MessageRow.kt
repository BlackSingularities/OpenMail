package com.blacksingularities.openmail.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openmail.core.Density
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.core.SwipeAction
import com.blacksingularities.openmail.data.MessageHeader
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wygląd akcji przesunięcia (ikona i kolor tła). */
fun SwipeAction.visual(m: MessageHeader): Pair<ImageVector, Color> = when (this) {
    SwipeAction.NONE -> Icons.Rounded.Edit to Color.Transparent
    SwipeAction.READ -> Icons.Rounded.MarkEmailRead to Palette.Unread
    SwipeAction.STAR -> (if (m.flagged) Icons.Rounded.StarBorder else Icons.Rounded.Star) to Palette.Star
    SwipeAction.ARCHIVE -> Icons.Rounded.Archive to Palette.Ok
    SwipeAction.DELETE -> Icons.Rounded.Delete to Palette.Danger
    SwipeAction.SPAM -> Icons.Rounded.Report to Color(0xFFFF9F0A)
}

/**
 * Wiersz wiadomości: awatar (albo znacznik zaznaczenia), nadawca, data, temat, podgląd,
 * ikony stanu. Przesunięcie w lewo/prawo wykonuje akcje z ustawień.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    m: MessageHeader,
    selected: Boolean,
    selectionMode: Boolean,
    open: Boolean,
    accountColor: Color?,
    threadCount: Int,
    showRecipient: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStar: () -> Unit,
    onSwipe: (SwipeAction) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val threshold = width * 0.28f
    val left = Prefs.swipeLeft
    val right = Prefs.swipeRight
    val compact = Prefs.density == Density.COMPACT
    val bg by animateColorAsState(
        when {
            selected -> Color.White.copy(alpha = 0.14f)
            open -> Color.White.copy(alpha = 0.08f)
            else -> Palette.Ink
        },
        tween(150),
        label = "rowBg",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width },
    ) {
        // Tło akcji pod przesuwanym wierszem.
        val dx = offset.value
        if (dx != 0f) {
            val action = if (dx > 0) right else left
            val (icon, color) = action.visual(m)
            val armed = abs(dx) > threshold
            Row(
                Modifier
                    .matchParentSize()
                    .background(if (armed) color else color.copy(alpha = 0.35f))
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (dx > 0) Arrangement.Start else Arrangement.End,
            ) {
                Icon(icon, action.label, tint = Color.White, modifier = Modifier.size(if (armed) 28.dp else 22.dp))
            }
        }
        Row(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(bg)
                .pointerInput(left, right, selectionMode) {
                    if (selectionMode) return@pointerInput
                    var armed = false
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val v = offset.value
                            val action = if (v > 0) right else left
                            scope.launch {
                                if (abs(v) > threshold && action != SwipeAction.NONE) {
                                    val removes = action == SwipeAction.ARCHIVE || action == SwipeAction.DELETE || action == SwipeAction.SPAM
                                    if (removes) offset.animateTo(if (v > 0) width.toFloat() else -width.toFloat(), tween(160))
                                    onSwipe(action)
                                    if (!removes) offset.animateTo(0f, spring(stiffness = 500f)) else offset.snapTo(0f)
                                } else {
                                    offset.animateTo(0f, spring(stiffness = 600f))
                                }
                            }
                            armed = false
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, amount ->
                        val next = offset.value + amount
                        val action = if (next > 0) right else left
                        if (action == SwipeAction.NONE) return@detectHorizontalDragGestures
                        change.consume()
                        scope.launch { offset.snapTo(next.coerceIn(-width.toFloat(), width.toFloat())) }
                        val nowArmed = abs(next) > threshold
                        if (nowArmed != armed) {
                            armed = nowArmed
                            if (Prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                .combinedClickable(onClick = onClick, onLongClick = {
                    if (Prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                })
                .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val name = if (showRecipient) m.to.firstOrNull()?.display?.let { "Do: $it" } ?: "(brak adresata)" else m.from.display
            if (Prefs.showAvatars || selectionMode) {
                Box(Modifier.pressable(onClick = onLongClick)) {
                    Avatar(
                        name = if (showRecipient) m.to.firstOrNull()?.display.orEmpty() else m.from.name,
                        email = if (showRecipient) m.to.firstOrNull()?.email.orEmpty() else m.from.email,
                        size = if (compact) 34.dp else 42.dp,
                        selected = selected,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!m.seen) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Unread))
                        Spacer(Modifier.width(6.dp))
                    }
                    if (accountColor != null) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(accountColor))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        name,
                        color = if (m.seen) Palette.TextDim else Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (m.seen) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (threadCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            threadCount.toString(),
                            color = Palette.TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.CardHigh).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    if (m.draft) {
                        Spacer(Modifier.width(6.dp))
                        Text("Wersja robocza", color = Palette.Danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    if (m.answered) {
                        Icon(Icons.AutoMirrored.Rounded.Reply, "Odpowiedziano", tint = Palette.TextFaint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (m.hasAttachments) {
                        Icon(Icons.Rounded.AttachFile, "Załącznik", tint = Palette.TextFaint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        Fmt.short(m.date),
                        color = if (m.seen) Palette.TextFaint else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (m.seen) FontWeight.Normal else FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.subject.ifBlank { "(bez tematu)" },
                        color = if (m.seen) Palette.TextDim else Color.White,
                        fontSize = 14.sp,
                        fontWeight = if (m.seen) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(30.dp).pressable(onClick = onStar), contentAlignment = Alignment.Center) {
                        Icon(
                            if (m.flagged) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            if (m.flagged) "Usuń gwiazdkę" else "Oznacz gwiazdką",
                            tint = if (m.flagged) Palette.Star else Palette.TextFaint,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                if (Prefs.previewLines > 0 && m.preview.isNotBlank() && !compact) {
                    Text(
                        m.preview,
                        color = Palette.TextFaint,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = Prefs.previewLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 30.dp),
                    )
                }
            }
        }
    }
}
