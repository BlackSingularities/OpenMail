package com.blacksingularities.openmail

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.blacksingularities.openmail.core.Prefs
import com.blacksingularities.openmail.data.Db
import com.blacksingularities.openmail.mail.MailClient
import com.blacksingularities.openmail.sync.Notifier
import com.blacksingularities.openmail.sync.Sync

class OpenMailApp : Application() {
    private var lastForegroundSync = 0L

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Db.init(this)
        Notifier.channels(this)
        Sync.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Po powrocie do aplikacji – szybkie odświeżenie (najwyżej co minutę).
                val now = System.currentTimeMillis()
                if (now - lastForegroundSync > 60_000) {
                    lastForegroundSync = now
                    Sync.refreshAll(this@OpenMailApp)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                Thread { MailClient.disconnectAll() }.start()
            }
        })
    }
}
