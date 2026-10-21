package com.blacksingularities.openmail.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.Mailbox
import com.blacksingularities.openmail.data.UnifiedKind
import com.blacksingularities.openmail.mail.Draft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data class Message(val id: Long) : Screen
    data class Compose(val draft: Draft, val key: Long = System.nanoTime()) : Screen
    data class AccountSetup(val providerId: String? = null) : Screen
    data class AccountEdit(val id: Long) : Screen
    data object Accounts : Screen
    data object Settings : Screen
}

/** Prosty stos ekranów i wybrana skrzynka. */
object Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val top: Screen get() = stack.last()

    var mailbox by mutableStateOf<Mailbox>(Mailbox.Unified(UnifiedKind.INBOX))
        private set

    /** Wiadomość otwarta obok listy na szerokim ekranie. */
    var selected by mutableStateOf<Long?>(null)

    fun open(mailbox: Mailbox) {
        this.mailbox = mailbox
        selected = null
        Prefs.lastMailbox = mailbox.key
    }

    fun restore() {
        Mailbox.parse(Prefs.lastMailbox)?.let { mailbox = it }
    }

    fun push(s: Screen) {
        if (stack.last() != s) stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Zastępuje bieżący ekran (np. po zapisaniu konta wróć do listy). */
    fun replace(s: Screen) {
        stack.removeAt(stack.lastIndex)
        stack.add(s)
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

object Fmt {
    private val pl = Locale.forLanguageTag("pl")
    private val time = SimpleDateFormat("HH:mm", pl)
    private val weekday = SimpleDateFormat("EEE", pl)
    private val dayMonth = SimpleDateFormat("d MMM", pl)
    private val full = SimpleDateFormat("d.MM.yyyy", pl)
    private val long = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", pl)

    /** Data na liście: godzina dziś, „wczoraj”, dzień tygodnia, dzień i miesiąc, pełna data. */
    fun short(ms: Long): String {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val sameYear = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
        val dayDiff = if (sameYear) now.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) else 999
        return when {
            dayDiff == 0 -> time.format(Date(ms))
            dayDiff == 1 -> "wczoraj"
            dayDiff in 2..6 -> weekday.format(Date(ms))
            sameYear -> dayMonth.format(Date(ms))
            else -> full.format(Date(ms))
        }
    }

    fun long(ms: Long): String = long.format(Date(ms)).replaceFirstChar { it.uppercase() }

    fun size(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(pl, "%.1f MB", bytes / 1048576.0)
    }

    fun ago(ms: Long): String {
        if (ms <= 0) return "nigdy"
        val d = (System.currentTimeMillis() - ms) / 1000
        return when {
            d < 60 -> "przed chwilą"
            d < 3600 -> "${d / 60} min temu"
            d < 86400 -> "${d / 3600} godz. temu"
            else -> short(ms)
        }
    }
}
