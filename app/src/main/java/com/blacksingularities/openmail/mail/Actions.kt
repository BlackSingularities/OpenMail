package com.blacksingularities.openmail.mail

import com.blacksingularities.openmail.data.Account
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.data.Folder
import com.blacksingularities.openmail.data.FolderRole
import com.blacksingularities.openmail.data.MessageHeader
import com.blacksingularities.openmail.data.OpType
import com.blacksingularities.openmail.data.PendingOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Działania na wiadomościach: zmiana od razu widoczna lokalnie, a na serwerze wykonywana
 * w tle. Gdy nie ma sieci, operacja czeka w kolejce i ponawia się przy synchronizacji.
 */
object Actions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun ops(list: List<MessageHeader>, type: OpType, arg: (MessageHeader) -> String = { "" }): List<Pair<Account, PendingOp>> {
        val accounts = mutableMapOf<Long, Account?>()
        val folders = mutableMapOf<Long, Folder?>()
        return list.mapNotNull { m ->
            val a = accounts.getOrPut(m.accountId) { Db.account(m.accountId) } ?: return@mapNotNull null
            val f = folders.getOrPut(m.folderId) { Db.folder(m.folderId) } ?: return@mapNotNull null
            a to PendingOp(accountId = a.id, folderPath = f.path, uid = m.uid, type = type, arg = arg(m))
        }
    }

    private fun run(list: List<Pair<Account, PendingOp>>) {
        if (list.isEmpty()) return
        scope.launch {
            for ((a, op) in list) {
                runCatching { MailClient.apply(a, op) }.onFailure { Db.addOp(op) }
            }
        }
    }

    fun setSeen(list: List<MessageHeader>, seen: Boolean) {
        val changed = list.filter { it.seen != seen }
        if (changed.isEmpty()) return
        scope.launch {
            Db.setSeen(changed.map { it.id }, seen)
            run(ops(changed, if (seen) OpType.SEEN else OpType.UNSEEN))
        }
    }

    fun setFlagged(list: List<MessageHeader>, flagged: Boolean) {
        val changed = list.filter { it.flagged != flagged }
        if (changed.isEmpty()) return
        scope.launch {
            Db.setFlagged(changed.map { it.id }, flagged)
            run(ops(changed, if (flagged) OpType.FLAG else OpType.UNFLAG))
        }
    }

    /** Folder docelowy konta wg roli (np. Kosz, Archiwum). */
    fun target(accountId: Long, role: FolderRole): Folder? = Db.folderByRole(accountId, role)

    fun canArchive(m: MessageHeader): Boolean {
        val f = Db.folder(m.folderId) ?: return false
        return f.role != FolderRole.ARCHIVE && target(m.accountId, FolderRole.ARCHIVE) != null
    }

    /** Przeniesienie do folderu o danej roli; zwraca liczbę przeniesionych. */
    fun moveToRole(list: List<MessageHeader>, role: FolderRole): Int {
        val byAccount = list.groupBy { it.accountId }
        var moved = 0
        scope.launch {
            val all = mutableListOf<Pair<Account, PendingOp>>()
            for ((accountId, msgs) in byAccount) {
                val target = target(accountId, role) ?: continue
                val todo = msgs.filter { it.folderId != target.id }
                all += ops(todo, OpType.MOVE) { target.path }
                Db.deleteMessages(todo.map { it.id })
            }
            run(all)
        }
        byAccount.forEach { (accountId, msgs) -> if (target(accountId, role) != null) moved += msgs.size }
        return moved
    }

    fun moveTo(list: List<MessageHeader>, folder: Folder) {
        val todo = list.filter { it.accountId == folder.accountId && it.folderId != folder.id }
        if (todo.isEmpty()) return
        scope.launch {
            val all = ops(todo, OpType.MOVE) { folder.path }
            Db.deleteMessages(todo.map { it.id })
            run(all)
        }
    }

    /**
     * Usuwanie: do Kosza, a z Kosza, Spamu lub gdy konto nie ma Kosza – trwale.
     */
    fun delete(list: List<MessageHeader>) {
        if (list.isEmpty()) return
        scope.launch {
            val all = mutableListOf<Pair<Account, PendingOp>>()
            for ((accountId, msgs) in list.groupBy { it.accountId }) {
                val trash = target(accountId, FolderRole.TRASH)
                val (permanent, toTrash) = msgs.partition { m ->
                    trash == null || m.folderId == trash.id || Db.folder(m.folderId)?.role == FolderRole.JUNK
                }
                all += ops(permanent, OpType.DELETE)
                if (trash != null) all += ops(toTrash, OpType.MOVE) { trash.path }
            }
            Db.deleteMessages(list.map { it.id })
            run(all)
        }
    }

    /** Ponawia operacje z kolejki (wywoływane przy synchronizacji). */
    suspend fun flush(a: Account) {
        for (op in Db.ops(a.id)) {
            runCatching { MailClient.apply(a, op) }
                .onSuccess { Db.removeOp(op.id) }
                .onFailure { Db.failOp(op) }
        }
    }
}
