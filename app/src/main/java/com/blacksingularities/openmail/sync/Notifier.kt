package com.blacksingularities.openmail.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.blacksingularities.openmail.MainActivity
import com.blacksingularities.openmail.R
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.Account
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.data.MessageHeader
import com.blacksingularities.openmail.mail.Actions

/** Powiadomienia o nowej poczcie: jedno na wiadomość, zgrupowane per konto. */
object Notifier {
    const val CHANNEL_MAIL = "mail"
    const val EXTRA_MESSAGE = "openmail.message"
    const val EXTRA_ACCOUNT = "openmail.account"
    private const val ACTION_READ = "openmail.READ"
    private const val ACTION_DELETE = "openmail.DELETE"
    private const val ACTION_ARCHIVE = "openmail.ARCHIVE"

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MAIL, "Nowa poczta", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Powiadomienia o nowych wiadomościach"
            },
        )
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun newMail(context: Context, a: Account, messages: List<MessageHeader>) {
        if (messages.isEmpty() || !allowed(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val group = "openmail.account.${a.id}"
        val recent = messages.sortedByDescending { it.date }.take(8)
        for (m in recent) {
            val n = Notification.Builder(context, CHANNEL_MAIL)
                .setSmallIcon(R.drawable.ic_stat_mail)
                .setColor(a.color)
                .setContentTitle(m.from.display)
                .setContentText(m.subject.ifBlank { "(bez tematu)" })
                .setStyle(Notification.BigTextStyle().bigText(m.subject + if (m.preview.isNotBlank()) "\n" + m.preview else ""))
                .setSubText(a.title)
                .setWhen(m.date)
                .setShowWhen(true)
                .setGroup(group)
                .setAutoCancel(true)
                .setContentIntent(openIntent(context, m))
                .addAction(action(context, ACTION_READ, m, "Przeczytane"))
                .addAction(action(context, if (Actions.canArchive(m)) ACTION_ARCHIVE else ACTION_DELETE, m, if (Actions.canArchive(m)) "Archiwizuj" else "Usuń"))
                .build()
            runCatching { nm.notify(m.id.toInt(), n) }
        }
        val total = Db.unreadInbox(a.id)
        val summary = Notification.Builder(context, CHANNEL_MAIL)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setColor(a.color)
            .setContentTitle(a.title)
            .setContentText("Nieprzeczytane: $total")
            .setStyle(Notification.InboxStyle().also { s -> recent.forEach { s.addLine("${it.from.display}: ${it.subject}") } }.setSummaryText(a.email))
            .setGroup(group)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    (a.id * 31).toInt(),
                    Intent(context, MainActivity::class.java).putExtra(EXTRA_ACCOUNT, a.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        runCatching { nm.notify((-a.id).toInt(), summary) }
    }

    private fun openIntent(context: Context, m: MessageHeader) = PendingIntent.getActivity(
        context,
        m.id.toInt(),
        Intent(context, MainActivity::class.java).putExtra(EXTRA_MESSAGE, m.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, m: MessageHeader, title: String): Notification.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_MESSAGE, m.id)
        val pi = PendingIntent.getBroadcast(context, (m.id * 7 + action.hashCode()).toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, title, pi).build()
    }

    fun cancel(context: Context, messageId: Long) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(messageId.toInt()) }
    }

    fun cancelAll(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancelAll() }
    }

    internal fun handle(context: Context, action: String?, id: Long) {
        val m = Db.message(id) ?: return cancel(context, id)
        when (action) {
            ACTION_READ -> Actions.setSeen(listOf(m), true)
            ACTION_DELETE -> Actions.delete(listOf(m))
            ACTION_ARCHIVE -> Actions.moveToRole(listOf(m), com.blacksingularities.openmail.data.FolderRole.ARCHIVE)
        }
        cancel(context, id)
    }
}

/** Akcje z powiadomienia (przeczytane, usuń, archiwizuj) bez otwierania aplikacji. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        Db.init(context)
        val id = intent.getLongExtra(Notifier.EXTRA_MESSAGE, 0L)
        if (id != 0L) {
            val pending = goAsync()
            Thread {
                try {
                    Notifier.handle(context.applicationContext, intent.action, id)
                    Thread.sleep(1500)
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }
}
