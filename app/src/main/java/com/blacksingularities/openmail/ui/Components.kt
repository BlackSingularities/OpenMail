package com.blacksingularities.openmail.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blacksingularities.openmail.core.Prefs

val CardShape = RoundedCornerShape(22.dp)
val PillShape = RoundedCornerShape(50)

/** Dotknięcie z lekką haptyką (gdy włączona w ustawieniach). */
@Composable
fun rememberTap(): (() -> Unit) -> Unit {
    val haptic = LocalHapticFeedback.current
    return { action ->
        if (Prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }
}

/** Sprężyste wciśnięcie elementu. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val tap = rememberTap()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (enabled) 1f else 0.38f
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled) { tap(onClick) }
}

/** Okrągły przycisk z ikoną (jak w OpenPDF). */
@Composable
fun RoundIcon(
    icon: ImageVector,
    description: String?,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 44.dp,
    tint: Color? = null,
    flat: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        when {
            active -> Color.White
            flat -> Color.Transparent
            else -> Palette.CardHigh
        },
        tween(160),
        label = "roundBg",
    )
    Box(modifier.size(size).pressable(enabled, onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = tint ?: if (active) Palette.Ink else Color.White, modifier = Modifier.size(size * 0.5f))
        }
        if (badge != null) {
            Text(
                badge,
                color = Palette.Ink,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(PillShape)
                    .background(Color.White)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

/** Przycisk-pigułka: biały (główny), szary albo czerwony (usuwanie). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        danger -> Palette.Danger
        primary -> Color.White
        else -> Palette.CardHigh
    }
    val fg = when {
        danger -> Color.White
        primary -> Palette.Ink
        else -> Color.White
    }
    Row(
        modifier
            .pressable(enabled, onClick)
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Pigułka-filtr (zaznaczona = biała). */
@Composable
fun FilterChip(text: String, selected: Boolean, icon: ImageVector? = null, count: Int = 0, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White else Palette.CardHigh, tween(160), label = "chip")
    val fg = if (selected) Palette.Ink else Palette.TextDim
    Row(
        Modifier
            .pressable(onClick = onClick)
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = fg, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        if (count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(count.toString(), color = fg.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Pasek ekranu: powrót, tytuł z podtytułem, akcje. */
@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIcon(backIcon, "Wstecz", onClick = onBack)
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Palette.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Ekran z paskiem i przewijaną treścią (ustawienia, konta). */
@Composable
fun ScrollScreen(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Palette.Ink).imePadding()) {
        TopBar(title, subtitle, onBack, actions = actions)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** Grupa ustawień: nagłówek i zaokrąglony blok (opcjonalnie zwijany). */
@Composable
fun Group(title: String?, collapsible: Boolean = false, initiallyExpanded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Column {
        if (title != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier)
                    .padding(start = 6.dp, end = 6.dp, bottom = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title.uppercase(),
                    color = Palette.TextFaint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    modifier = Modifier.weight(1f),
                )
                if (collapsible) {
                    Icon(Icons.Rounded.ExpandMore, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp).rotate(if (expanded) 0f else -90f))
                }
            }
        }
        AnimatedVisibility(visible = !collapsible || expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(vertical = 3.dp),
                content = content,
            )
        }
    }
}

@Composable
fun RowIcon(icon: ImageVector, tint: Color = Color.White) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String? = null, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val tap = rememberTap()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { tap { onChange(!value) } }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, color = if (enabled) Color.White else Palette.TextFaint)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = { tap { onChange(it) } },
            enabled = enabled,
            modifier = Modifier.scale(0.82f),
            colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Palette.Ink, uncheckedTrackColor = Palette.CardHigh),
        )
    }
}

/** Wiersz-akcja: ikona, tytuł, opis; dotknięcie wykonuje akcję. */
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    tint: Color = Color.White,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tap = rememberTap()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { tap(onClick) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (danger) Palette.Danger else tint)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                color = when {
                    !enabled -> Palette.TextFaint
                    danger -> Palette.Danger
                    else -> Color.White
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** Wybór w jednej linii z przełącznikiem segmentowym po prawej (jak w OpenPDF). */
@Composable
fun <T> CompactChoice(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    short: (T) -> String = label,
    onSelect: (T) -> Unit,
) {
    val tap = rememberTap()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label(selected), fontSize = 11.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.clip(PillShape).background(Color.White.copy(alpha = 0.06f)).padding(2.dp)) {
            options.forEach { o ->
                val sel = o == selected
                Box(
                    Modifier
                        .clip(PillShape)
                        .background(if (sel) Color.White else Color.Transparent)
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { tap { onSelect(o) } })
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(short(o), fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) Palette.Ink else Palette.TextDim)
                }
            }
        }
    }
}

/** Wybór z listy pigułek pod tytułem (więcej opcji niż mieści się w jednej linii). */
@Composable
fun <T> ChipChoice(icon: ImageVector, title: String, options: List<T>, selected: T, label: (T) -> String, hint: String? = null, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Text(title, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Row(
            Modifier.padding(start = 46.dp, top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { o -> FilterChip(label(o), o == selected) { onSelect(o) } }
        }
        if (hint != null) Text(hint, fontSize = 11.sp, color = Palette.TextDim, modifier = Modifier.padding(start = 46.dp, top = 4.dp))
    }
}

@Composable
fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 11.sp, color = Palette.TextDim)
            Text(value.ifBlank { "—" }, fontSize = 14.sp, color = Color.White)
        }
    }
}

/** Kółko z inicjałem w kolorze wyliczonym z adresu (albo koloru konta). */
@Composable
fun Avatar(name: String, email: String, size: Dp = 40.dp, color: Color? = null, selected: Boolean = false) {
    val c = color ?: avatarColor(email.ifBlank { name })
    val initial = initials(name.ifBlank { email })
    Box(
        Modifier.size(size).clip(CircleShape).background(if (selected) Color.White else c.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = Palette.Ink, modifier = Modifier.size(size * 0.5f))
        } else {
            Text(initial, color = c, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

private val avatarPalette = listOf(
    Color(0xFF6FA8FF), Color(0xFFFF7EB6), Color(0xFF5CE1A6), Color(0xFFFFB547),
    Color(0xFFB69CFF), Color(0xFF4FD8EB), Color(0xFFFF6B6B), Color(0xFFC9CCD6), Color(0xFF9BE15D),
)

fun avatarColor(key: String): Color = avatarPalette[Math.floorMod(key.lowercase().hashCode(), avatarPalette.size)]

fun initials(s: String): String {
    val clean = s.trim().trim('"', '\'').replace(Regex("<.*>"), "").trim()
    val words = clean.split(Regex("[\\s._-]+")).filter { it.isNotEmpty() && it[0].isLetterOrDigit() }
    return when {
        words.size >= 2 -> "${words[0][0]}${words[1][0]}".uppercase()
        words.size == 1 -> words[0].take(1).uppercase()
        else -> "?"
    }
}

/** Pole tekstowe w ciemnym stylu. */
@Composable
fun DarkField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Default,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Palette.TextFaint) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = imeAction),
        trailingIcon = trailing,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Palette.TextDim,
            cursorColor = Color.White,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        ),
    )
}

/** Ciemne okno dialogowe w stylu aplikacji. */
@Composable
fun DarkDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.CardHigh)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = buttons)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DarkDialog(title, onDismiss, buttons = {
        Pill("Anuluj", onClick = onDismiss)
        Pill(confirm, primary = !danger, danger = danger) {
            onDismiss()
            onConfirm()
        }
    }) {
        Text(text, color = Palette.TextDim, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Pusty stan listy: ikona, tytuł, opis i opcjonalna akcja. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Palette.CardHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.TextDim, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Palette.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Pill(action, primary = true, onClick = onAction)
        }
    }
}

/** Lista wyboru w oknie (np. przeniesienie do folderu). */
@Composable
fun <T> PickDialog(title: String, items: List<T>, label: (T) -> String, icon: (T) -> ImageVector, subtitle: (T) -> String? = { null }, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    DarkDialog(title, onDismiss, buttons = { Pill("Anuluj", onClick = onDismiss) }) {
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.05f))) {
            items.forEach { item ->
                ActionRow(icon(item), label(item), subtitle(item)) {
                    onDismiss()
                    onPick(item)
                }
            }
        }
    }
}

/** Kropka koloru do wyboru. */
@Composable
fun ColorDot(color: Int, selected: Boolean, size: Dp = 32.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(if (selected) 3.dp else 2.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}
