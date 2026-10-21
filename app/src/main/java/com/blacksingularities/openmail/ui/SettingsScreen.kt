package com.blacksingularities.openmail.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DensityMedium
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openmail.BuildConfig
import com.blacksingularities.openmail.core.Density as ListDensity
import com.blacksingularities.openmail.core.ImagePolicy
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.core.SwipeAction
import com.blacksingularities.openmail.mail.OAuth
import com.blacksingularities.openmail.sync.Sync
import com.blacksingularities.openmail.update.UpdateManager
import com.blacksingularities.openmail.update.UpdateState
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }
    var clearCache by remember { mutableStateOf(false) }
    var oauthDialog by remember { mutableStateOf(false) }
    BackHandler { Nav.pop() }

    ScrollScreen("Ustawienia", "OpenMail ${BuildConfig.VERSION_NAME}", onBack = { Nav.pop() }) {
        UpdateBanner()

        Group("Konta") {
            ActionRow(Icons.Rounded.AccountCircle, "Zarządzaj kontami", "Dodawanie, kolejność, podpisy, foldery") { Nav.push(Screen.Accounts) }
        }

        Group("Lista wiadomości") {
            CompactChoice(Icons.Rounded.DensityMedium, "Układ listy", ListDensity.entries, Prefs.density, { it.label }) { Prefs.density = it }
            CompactChoice(Icons.AutoMirrored.Rounded.Notes, "Wiersze podglądu", listOf(0, 1, 2, 3), Prefs.previewLines, { if (it == 0) "Bez podglądu" else "$it" }) { Prefs.previewLines = it }
            SwitchRow(Icons.Rounded.AccountCircle, "Awatary nadawców", null, Prefs.showAvatars) { Prefs.showAvatars = it }
            SwitchRow(Icons.Rounded.Forum, "Grupuj w wątki", "Wiadomości o tym samym temacie jako jeden wiersz", Prefs.groupThreads) { Prefs.groupThreads = it }
            ChipChoice(Icons.Rounded.SwipeRight, "Przesunięcie w prawo", SwipeAction.entries, Prefs.swipeRight, { it.label }) { Prefs.swipeRight = it }
            ChipChoice(Icons.Rounded.SwipeLeft, "Przesunięcie w lewo", SwipeAction.entries, Prefs.swipeLeft, { it.label }) { Prefs.swipeLeft = it }
            SwitchRow(Icons.Rounded.Warning, "Potwierdzaj usuwanie", null, Prefs.confirmDelete) { Prefs.confirmDelete = it }
        }

        Group("Czytanie") {
            CompactChoice(Icons.Rounded.Image, "Obrazy z internetu", ImagePolicy.entries, Prefs.images, { it.label }) { Prefs.images = it }
            SwitchRow(Icons.Rounded.DarkMode, "Ciemne wiadomości HTML", "Przyciemnia jasne treści (może zmienić kolory grafik)", Prefs.darkMessages) { Prefs.darkMessages = it }
            SwitchRow(Icons.Rounded.MarkEmailRead, "Oznaczaj jako przeczytane", "Po otwarciu wiadomości", Prefs.markReadOnOpen) { Prefs.markReadOnOpen = it }
            CompactChoice(Icons.Rounded.TouchApp, "Wielkość tekstu", listOf(85, 100, 115, 130), Prefs.messageTextZoom.let { z -> listOf(85, 100, 115, 130).minBy { kotlin.math.abs(it - z) } }, { "$it%" }) { Prefs.messageTextZoom = it }
            if (Prefs.trustedSenders.isNotEmpty()) {
                ActionRow(Icons.Rounded.Policy, "Zaufani nadawcy (${Prefs.trustedSenders.size})", "Obrazy ładowane zawsze – dotknij, aby wyczyścić listę") {
                    Prefs.trustedSenders = emptySet()
                }
            }
        }

        Group("Pisanie") {
            CompactChoice(Icons.AutoMirrored.Rounded.Undo, "Cofanie wysyłki", listOf(0, 5, 10, 20), Prefs.undoSendSeconds, { if (it == 0) "Wyłączone" else "$it s" }) { Prefs.undoSendSeconds = it }
            SwitchRow(Icons.Rounded.FormatQuote, "Cytuj oryginał w odpowiedzi", null, Prefs.quoteOriginal) { Prefs.quoteOriginal = it }
            SwitchRow(Icons.Rounded.QuestionAnswer, "Pytaj przed wysłaniem", null, Prefs.confirmSend) { Prefs.confirmSend = it }
        }

        Group("Synchronizacja") {
            ChipChoice(Icons.Rounded.Schedule, "Sprawdzanie poczty w tle", listOf(15, 30, 60, 180, 0), Prefs.syncMinutes, { m ->
                when (m) {
                    0 -> "Ręcznie"
                    in 1..59 -> "co $m min"
                    60 -> "co 1 godz."
                    else -> "co ${m / 60} godz."
                }
            }) {
                Prefs.syncMinutes = it
                Sync.schedule(context)
            }
            SwitchRow(Icons.Rounded.Wifi, "Tylko przez Wi-Fi", "Synchronizacja w tle bez danych komórkowych", Prefs.wifiOnly) {
                Prefs.wifiOnly = it
                Sync.schedule(context)
            }
            CompactChoice(Icons.Rounded.Download, "Wiadomości na folder", listOf(100, 250, 500, 1000), Prefs.syncWindow, { "$it najnowszych" }, short = { it.toString() }) { Prefs.syncWindow = it }
        }

        Group("Powiadomienia") {
            SwitchRow(Icons.Rounded.Notifications, "Powiadomienia o nowej poczcie", if (notifGranted) null else "Brak zgody systemu – dotknij poniżej", Prefs.notifications) { Prefs.notifications = it }
            if (!notifGranted && Build.VERSION.SDK_INT >= 33) {
                ActionRow(Icons.Rounded.Notifications, "Zezwól na powiadomienia") { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            ActionRow(Icons.Rounded.Notifications, "Dźwięk i styl powiadomień", "Ustawienia kanału w systemie") {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
        }

        Group("Ogólne") {
            SwitchRow(Icons.Rounded.Vibration, "Wibracje przy dotyku", null, Prefs.haptics) { Prefs.haptics = it }
            ActionRow(Icons.Rounded.DeleteSweep, "Wyczyść pobrane załączniki", "Pliki w pamięci podręcznej aplikacji") { clearCache = true }
        }

        Group("Aktualizacje") {
            SwitchRow(Icons.Rounded.SystemUpdate, "Sprawdzaj automatycznie", "Przy uruchomieniu aplikacji (GitHub Releases)", Prefs.autoUpdate) { Prefs.autoUpdate = it }
            val u = UpdateManager.state
            ActionRow(
                Icons.Rounded.SystemUpdate,
                if (u is UpdateState.Checking) "Sprawdzanie…" else "Sprawdź teraz",
                "Zainstalowana wersja: ${BuildConfig.VERSION_NAME}",
            ) { UpdateManager.check(context, manual = true) }
        }

        Group("Zaawansowane", collapsible = true, initiallyExpanded = false) {
            ActionRow(
                Icons.Rounded.Key,
                "Identyfikatory klientów OAuth",
                "Google: ${if (OAuth.available(com.blacksingularities.openmail.data.AuthType.OAUTH_GOOGLE)) "skonfigurowany" else "brak"} · Microsoft: ${if (OAuth.available(com.blacksingularities.openmail.data.AuthType.OAUTH_MICROSOFT)) "skonfigurowany" else "brak"}",
            ) { oauthDialog = true }
        }

        Group("Informacje") {
            ActionRow(Icons.Rounded.Code, "Kod źródłowy", "github.com/${BuildConfig.UPDATE_REPO}") {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO}"))) }
            }
            ActionRow(Icons.Rounded.Warning, "Zgłoś problem", "Otwiera zgłoszenia na GitHubie") {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO}/issues/new"))) }
            }
            ActionRow(Icons.Rounded.Info, "Licencja", "MIT · Jakarta Mail (EPL 2.0) · jsoup (MIT)") {}
            ActionRow(Icons.AutoMirrored.Rounded.ViewList, "Wersja", BuildConfig.VERSION_NAME) {}
        }
    }

    if (clearCache) {
        ConfirmDialog("Wyczyścić załączniki?", "Pobrane pliki zostaną usunięte z pamięci telefonu. W razie potrzeby pobiorą się ponownie.", "Wyczyść", onDismiss = { clearCache = false }) {
            listOf("attachments", "share").forEach { File(context.cacheDir, it).deleteRecursively() }
            Toast.makeText(context, "Wyczyszczono", Toast.LENGTH_SHORT).show()
        }
    }
    if (oauthDialog) {
        var google by remember { mutableStateOf(Prefs.googleClientId) }
        var microsoft by remember { mutableStateOf(Prefs.microsoftClientId) }
        DarkDialog("Klienci OAuth", { oauthDialog = false }, buttons = {
            Pill("Anuluj") { oauthDialog = false }
            Pill("Zapisz", primary = true) {
                Prefs.googleClientId = google.trim()
                Prefs.microsoftClientId = microsoft.trim()
                oauthDialog = false
            }
        }) {
            Text(
                "Własne identyfikatory aplikacji z Google Cloud Console i Microsoft Entra (aplikacja publiczna, PKCE). Przekierowanie Microsoft: com.blacksingularities.openmail://oauth. Puste pole = identyfikator wbudowany w aplikację.",
                color = Palette.TextDim,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                DarkField(microsoft, { microsoft = it }, "Microsoft – Application (client) ID")
                DarkField(google, { google = it }, "Google – identyfikator klienta")
            }
            if (google.isNotBlank() && google != BuildConfig.GOOGLE_CLIENT_ID) {
                Text(
                    "Uwaga: przekierowanie Google działa tylko z identyfikatorem wbudowanym w wydanie aplikacji.",
                    color = Palette.Star,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
