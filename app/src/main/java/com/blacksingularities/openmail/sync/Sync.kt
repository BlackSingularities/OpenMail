package com.blacksingularities.openmail.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.Account
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.data.Folder
import com.blacksingularities.openmail.data.FolderRole
import com.blacksingularities.openmail.data.MessageHeader
import com.blacksingularities.openmail.mail.Actions
import com.blacksingularities.openmail.mail.MailClient
import com.blacksingularities.openmail.mail.mailMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Synchronizacja poczty: wszystkie konta równolegle, w każdym najpierw kolejka operacji,
 * potem lista folderów (co jakiś czas) i foldery oznaczone do synchronizacji.
 */
object Sync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Konta i foldery właśnie synchronizowane (wskaźnik odświeżania w interfejsie). */
    var busyAccounts by mutableStateOf<Set<Long>>(emptySet())
        private set
    var busyFolders by mutableStateOf<Set<Long>>(emptySet())
        private set

    val busy: Boolean get() = busyAccounts.isNotEmpty() || busyFolders.isNotEmpty()

    private const val FOLDER_LIST_EVERY = 6 * 60 * 60 * 1000L
    private val folderListAt = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    @Synchronized
    private fun mark(account: Long? = null, folder: Long? = null, on: Boolean) {
        if (account != null) busyAccounts = if (on) busyAccounts + account else busyAccounts - account
        if (folder != null) busyFolders = if (on) busyFolders + folder else busyFolders - folder
    }

    fun refreshAll(context: Context) {
        scope.launch { syncAll(context, notify = false, forceFolders = false) }
    }

    fun refreshFolder(context: Context, folderId: Long) {
        scope.launch { syncFolder(context, folderId) }
    }

    /** Pełna synchronizacja wszystkich kont; zwraca nowe wiadomości w Odebranych. */
    suspend fun syncAll(context: Context, notify: Boolean, forceFolders: Boolean): List<MessageHeader> = lock.withLock {
        val accounts = Db.accounts().filter { it.syncEnabled }
        coroutineScope {
            accounts.map { a -> async { syncAccount(context, a, notify, forceFolders) } }.awaitAll().flatten()
        }
    }

    suspend fun syncAccount(context: Context, a: Account, notify: Boolean, forceFolders: Boolean): List<MessageHeader> {
        mark(account = a.id, on = true)
        val fresh = mutableListOf<MessageHeader>()
        try {
            Actions.flush(a)
            val folders = Db.folders(a.id)
            val listAge = System.currentTimeMillis() - (folderListAt[a.id] ?: 0L)
            if (folders.isEmpty() || forceFolders || listAge > FOLDER_LIST_EVERY) {
                MailClient.syncFolders(a)
                folderListAt[a.id] = System.currentTimeMillis()
            }
            val toSync = Db.folders(a.id).filter { it.selectable && (it.sync || it.role == FolderRole.INBOX) }
            for (f in toSync) {
                val news = MailClient.syncFolder(a, f, Prefs.syncWindow, if (f.role == FolderRole.INBOX) 30 else 10)
                if (f.role == FolderRole.INBOX) fresh += news
            }
            Db.setAccountStatus(a.id, System.currentTimeMillis(), "")
            if (notify && a.notify && Prefs.notifications) Notifier.newMail(context, a, fresh.filter { !it.seen })
        } catch (e: Exception) {
            Db.setAccountStatus(a.id, 0, e.mailMessage())
        } finally {
            mark(account = a.id, on = false)
        }
        return fresh
    }

    suspend fun syncFolder(context: Context, folderId: Long) {
        val f: Folder = Db.folder(folderId) ?: return
        val a = Db.account(f.accountId) ?: return
        if (!f.selectable || folderId in busyFolders) return
        mark(folder = folderId, on = true)
        try {
            Actions.flush(a)
            MailClient.syncFolder(a, f, Prefs.syncWindow, 15)
            Db.setAccountStatus(a.id, System.currentTimeMillis(), "")
        } catch (e: Exception) {
            Db.setAccountStatus(a.id, 0, e.mailMessage())
        } finally {
            mark(folder = folderId, on = false)
        }
    }

    /** Odświeżenie listy folderów konta (np. po dodaniu konta lub na żądanie). */
    fun refreshFolders(context: Context, a: Account) {
        scope.launch {
            mark(account = a.id, on = true)
            try {
                MailClient.syncFolders(a)
                folderListAt[a.id] = System.currentTimeMillis()
            } catch (e: Exception) {
                Db.setAccountStatus(a.id, 0, e.mailMessage())
            } finally {
                mark(account = a.id, on = false)
            }
            syncAccount(context, a, notify = false, forceFolders = false)
        }
    }

    /** Zaplanowanie okresowej synchronizacji w tle wg ustawień. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (Prefs.syncMinutes <= 0) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (Prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<SyncWorker>(Prefs.syncMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private const val WORK = "openmail.sync"
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Prefs.init(applicationContext)
        Db.init(applicationContext)
        return runCatching {
            Sync.syncAll(applicationContext, notify = true, forceFolders = false)
            MailClient.disconnectAll()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
