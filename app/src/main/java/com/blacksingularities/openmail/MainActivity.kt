package com.blacksingularities.openmail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.mail.Draft
import com.blacksingularities.openmail.mail.Outbox
import com.blacksingularities.openmail.sync.Notifier
import com.blacksingularities.openmail.ui.AccountEditScreen
import com.blacksingularities.openmail.ui.AccountSetupScreen
import com.blacksingularities.openmail.ui.AccountsScreen
import com.blacksingularities.openmail.ui.ComposeScreen
import com.blacksingularities.openmail.ui.HomeScreen
import com.blacksingularities.openmail.ui.Mailto
import com.blacksingularities.openmail.ui.MessageScreen
import com.blacksingularities.openmail.ui.Nav
import com.blacksingularities.openmail.ui.OpenMailTheme
import com.blacksingularities.openmail.ui.Palette
import com.blacksingularities.openmail.ui.Screen
import com.blacksingularities.openmail.ui.SettingsScreen
import com.blacksingularities.openmail.ui.UndoSendBar
import com.blacksingularities.openmail.ui.parseAddresses
import com.blacksingularities.openmail.update.UpdateManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        if (savedInstanceState == null) {
            Nav.restore()
            if (Prefs.autoUpdate) UpdateManager.check(this)
            handle(intent)
        }

        setContent {
            OpenMailTheme {
                // Cofnięta albo nieudana wysyłka – edytor otwiera się ponownie.
                val restored = Outbox.restored
                LaunchedEffect(restored) {
                    if (restored != null) {
                        Outbox.restored = null
                        Nav.push(Screen.Compose(restored))
                    }
                }
                BackHandler(Nav.stack.size > 1) { Nav.pop() }
                Box(Modifier.fillMaxSize().background(Palette.Ink)) {
                    AnimatedContent(
                        targetState = Nav.top,
                        transitionSpec = {
                            val back = targetState == Screen.Home || initialState is Screen.Compose
                            if (back) {
                                (fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.97f)) togetherWith
                                    (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 6 })
                            } else {
                                (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 5 }) togetherWith
                                    (fadeOut(tween(140)) + scaleOut(tween(220), targetScale = 0.97f))
                            }
                        },
                        label = "nav",
                    ) { s ->
                        when (s) {
                            Screen.Home -> HomeScreen()
                            is Screen.Message -> MessageScreen(s.id)
                            is Screen.Compose -> ComposeScreen(s.draft)
                            is Screen.AccountSetup -> AccountSetupScreen(s.providerId)
                            is Screen.AccountEdit -> AccountEditScreen(s.id)
                            Screen.Accounts -> AccountsScreen()
                            Screen.Settings -> SettingsScreen()
                        }
                    }
                    if (Nav.top != Screen.Home) UndoSendBar(Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** Otwarcie z powiadomienia, linku mailto: albo „Udostępnij” z innej aplikacji. */
    private fun handle(intent: Intent?) {
        intent ?: return
        val messageId = intent.getLongExtra(Notifier.EXTRA_MESSAGE, 0L)
        if (messageId != 0L) {
            intent.removeExtra(Notifier.EXTRA_MESSAGE)
            if (Db.message(messageId) != null) {
                Nav.home()
                Nav.push(Screen.Message(messageId))
            }
            return
        }
        val accountId = intent.getLongExtra(Notifier.EXTRA_ACCOUNT, 0L)
        if (accountId != 0L) {
            intent.removeExtra(Notifier.EXTRA_ACCOUNT)
            Db.folderByRole(accountId, com.blacksingularities.openmail.data.FolderRole.INBOX)?.let {
                Nav.home()
                Nav.open(com.blacksingularities.openmail.data.Mailbox.Of(it.id))
            }
            return
        }
        val draft = when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SENDTO -> intent.data?.let { Mailto.parse(it) }?.let { withExtras(it, intent) }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val base = intent.data?.let { Mailto.parse(it) } ?: defaultDraft() ?: return
                withExtras(base, intent)
            }
            else -> null
        } ?: return
        intent.action = null
        Nav.push(Screen.Compose(draft))
    }

    private fun defaultDraft(): Draft? {
        val id = Prefs.defaultAccount.takeIf { Db.account(it) != null } ?: Db.accounts().firstOrNull()?.id ?: return null
        return Draft(accountId = id)
    }

    @Suppress("DEPRECATION")
    private fun withExtras(d: Draft, intent: Intent): Draft {
        fun list(key: String) = intent.getStringArrayExtra(key)?.flatMap { parseAddresses(it) }.orEmpty()
        val streams: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            else -> emptyList()
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        return d.copy(
            to = (d.to + list(Intent.EXTRA_EMAIL)).distinctBy { it.email.lowercase() },
            cc = d.cc + list(Intent.EXTRA_CC),
            bcc = d.bcc + list(Intent.EXTRA_BCC),
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: d.subject,
            body = if (text.isNotBlank()) text else d.body,
            attachments = d.attachments + streams.map { Outbox.describe(this, it) },
        )
    }
}
