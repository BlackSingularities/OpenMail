package com.blacksingularities.openmail.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openmail.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val notes: String,
    val assetUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Installing(val info: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Porównanie numeryczne wersji „1.1.12” > „1.1.9”; przyrostki typu „-dev” są pomijane. */
fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
    val r = parts(remote)
    val l = parts(local)
    if (l.isEmpty()) return false
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

fun parseRelease(json: String): ReleaseInfo {
    val o = JSONObject(json)
    val assets = o.optJSONArray("assets")
    var url = ""
    var size = 0L
    if (assets != null) for (i in 0 until assets.length()) {
        val a = assets.getJSONObject(i)
        if (a.optString("name").endsWith(".apk")) {
            url = a.optString("browser_download_url")
            size = a.optLong("size")
            break
        }
    }
    if (url.isEmpty()) error("Wydanie nie zawiera pliku APK")
    return ReleaseInfo(
        version = o.optString("tag_name").removePrefix("v"),
        notes = o.optString("body").substringBefore("\n---").trim(),
        assetUrl = url,
        sizeBytes = size,
        pageUrl = o.optString("html_url"),
    )
}

/**
 * Aktualizacje z GitHub Releases: sprawdzenie najnowszego wydania, pobranie APK i instalacja
 * przez PackageInstaller (od Androida 12 kolejne aktualizacje mogą przejść bez potwierdzenia).
 */
object UpdateManager {
    const val ACTION_STATUS = "com.blacksingularities.openmail.UPDATE_STATUS"
    private const val API = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var bannerHidden by mutableStateOf(false)

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    fun check(context: Context, manual: Boolean = false) {
        if (job?.isActive == true) return
        if (state is UpdateState.Downloading || state is UpdateState.Installing) return
        val app = context.applicationContext
        job = scope.launch {
            state = UpdateState.Checking
            state = runCatching {
                val info = withContext(Dispatchers.IO) { parseRelease(httpGet(API)) }
                if (isNewer(info.version, currentVersion)) UpdateState.Available(info) else UpdateState.UpToDate
            }.getOrElse { UpdateState.Failed(it.message ?: it.javaClass.simpleName) }
            if (manual) bannerHidden = false
            if (manual && state is UpdateState.UpToDate) Toast.makeText(app, "Masz najnowszą wersję ($currentVersion)", Toast.LENGTH_SHORT).show()
            if (manual && state is UpdateState.Failed) Toast.makeText(app, "Nie udało się sprawdzić: ${(state as UpdateState.Failed).message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Pobranie i instalacja jednym dotknięciem. */
    fun updateNow(context: Context) {
        val app = context.applicationContext
        when (val s = state) {
            is UpdateState.Available -> {
                if (!canInstall(app)) {
                    Toast.makeText(app, "Zezwól OpenMail na instalowanie aplikacji i dotknij ponownie", Toast.LENGTH_LONG).show()
                    openInstallPermissionSettings(app)
                    return
                }
                job = scope.launch {
                    state = UpdateState.Downloading(s.info, 0f)
                    runCatching {
                        val f = download(app, s.info) { p -> scope.launch { state = UpdateState.Downloading(s.info, p) } }
                        state = UpdateState.Ready(s.info, f)
                        state = UpdateState.Installing(s.info)
                        install(app, f)
                    }.onFailure {
                        state = UpdateState.Failed(it.message ?: "błąd")
                        Toast.makeText(app, "Aktualizacja nieudana: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            is UpdateState.Ready -> scope.launch {
                state = UpdateState.Installing(s.info)
                runCatching { install(app, s.file) }.onFailure { state = UpdateState.Failed(it.message ?: "błąd") }
            }
            else -> check(app, manual = true)
        }
    }

    /** Instalator wrócił bez sukcesu – pozwól spróbować ponownie. */
    internal fun installAborted() {
        val s = state
        if (s is UpdateState.Installing) state = UpdateState.Available(s.info)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OpenMail/" + BuildConfig.VERSION_NAME)
        }
        try {
            if (conn.responseCode != 200) error("serwer odpowiedział kodem ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun download(context: Context, info: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "OpenMail-${info.version}.apk")
        val part = File(dir, target.name + ".part")
        val conn = (URL(info.assetUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OpenMail/" + BuildConfig.VERSION_NAME)
        }
        try {
            if (conn.responseCode !in 200..299) error("pobieranie nie powiodło się (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0 && read - lastReport > 128 * 1024) {
                            lastReport = read
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!part.renameTo(target)) error("nie można zapisać pliku aktualizacji")
            onProgress(1f)
            target
        } finally {
            conn.disconnect()
        }
    }

    private fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    private fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private suspend fun install(context: Context, file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        // Znacznik dla nowej wersji: po instalacji uruchom aplikację ponownie.
        context.getSharedPreferences("openmail_update", Context.MODE_PRIVATE).edit()
            .putLong("restart", System.currentTimeMillis()).apply()
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    fun relaunch(context: Context) {
        val prefs = context.getSharedPreferences("openmail_update", Context.MODE_PRIVATE)
        val requested = prefs.getLong("restart", 0L)
        if (requested == 0L || System.currentTimeMillis() - requested > 30 * 60_000L) return
        prefs.edit().remove("restart").apply()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
        runCatching { context.startActivity(launch) }
    }
}

/** Status instalacji; gdy system wymaga potwierdzenia – pokazuje jego okno. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateManager.relaunch(context)
            else -> {
                UpdateManager.installAborted()
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "kod $status"
                    Toast.makeText(context, "Aktualizacja nieudana: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/** Nowa wersja zainstalowana – otwórz aplikację ponownie. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateManager.relaunch(context)
    }
}
