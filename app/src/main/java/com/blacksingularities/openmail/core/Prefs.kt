package com.blacksingularities.openmail.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Akcje przesunięcia wiadomości na liście. */
enum class SwipeAction(val label: String) {
    NONE("Brak"),
    READ("Przeczytana / nieprzeczytana"),
    STAR("Oznacz gwiazdką"),
    ARCHIVE("Archiwizuj"),
    DELETE("Usuń"),
    SPAM("Spam"),
}

enum class ImagePolicy(val label: String) {
    ASK("Pytaj"),
    ALWAYS("Zawsze"),
    NEVER("Nigdy"),
}

enum class Density(val label: String) {
    COMFORTABLE("Wygodny"),
    COMPACT("Zwarty"),
}

/**
 * Ustawienia aplikacji – obserwowalne w Compose (każda zmiana odświeża interfejs)
 * i zapisywane od razu w SharedPreferences.
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("openmail", Context.MODE_PRIVATE)
        loaded.forEach { it() }
    }

    private val loaded = mutableListOf<() -> Unit>()

    private class Pref<T>(private val key: String, private val default: T, private val read: SharedPreferences.(String, T) -> T, private val write: SharedPreferences.Editor.(String, T) -> Unit) :
        ReadWriteProperty<Any?, T> {
        private var state by mutableStateOf(default)

        init {
            loaded += { state = sp.read(key, default) }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state = value
            if (::sp.isInitialized) sp.edit().apply { write(key, value) }.apply()
        }
    }

    private fun bool(key: String, d: Boolean) = Pref(key, d, { k, v -> getBoolean(k, v) }, { k, v -> putBoolean(k, v) })
    private fun int(key: String, d: Int) = Pref(key, d, { k, v -> getInt(k, v) }, { k, v -> putInt(k, v) })
    private fun long(key: String, d: Long) = Pref(key, d, { k, v -> getLong(k, v) }, { k, v -> putLong(k, v) })
    private fun str(key: String, d: String) = Pref(key, d, { k, v -> getString(k, v) ?: v }, { k, v -> putString(k, v) })
    private fun strSet(key: String) = Pref<Set<String>>(key, emptySet(), { k, v -> getStringSet(k, v)?.toSet() ?: v }, { k, v -> putStringSet(k, v) })
    private inline fun <reified E : Enum<E>> enum(key: String, d: E) = Pref(
        key,
        d,
        { k, v -> runCatching { enumValueOf<E>(getString(k, v.name)!!) }.getOrDefault(v) },
        { k, v -> putString(k, v.name) },
    )

    // Ogólne
    var haptics by bool("haptics", true)
    var autoUpdate by bool("autoUpdate", true)

    // Lista wiadomości
    var density by enum("density", Density.COMFORTABLE)
    var showAvatars by bool("showAvatars", true)
    var previewLines by int("previewLines", 2)
    var swipeLeft by enum("swipeLeft", SwipeAction.DELETE)
    var swipeRight by enum("swipeRight", SwipeAction.ARCHIVE)
    var confirmDelete by bool("confirmDelete", false)
    var groupThreads by bool("groupThreads", false)

    // Czytanie
    var images by enum("images", ImagePolicy.ASK)
    var darkMessages by bool("darkMessages", true)
    var markReadOnOpen by bool("markReadOnOpen", true)
    var messageTextZoom by int("messageTextZoom", 100)
    var trustedSenders by strSet("trustedSenders")

    // Pisanie
    var undoSendSeconds by int("undoSendSeconds", 5)
    var quoteOriginal by bool("quoteOriginal", true)
    var confirmSend by bool("confirmSend", false)

    // Synchronizacja i powiadomienia
    var syncMinutes by int("syncMinutes", 15)
    var wifiOnly by bool("wifiOnly", false)
    var notifications by bool("notifications", true)
    var syncWindow by int("syncWindow", 250)

    // Stan interfejsu
    var defaultAccount by long("defaultAccount", 0L)
    var lastMailbox by str("lastMailbox", "")
    var onboardingDone by bool("onboardingDone", false)

    // Własne identyfikatory klientów OAuth (zastępują wbudowane).
    var googleClientId by str("googleClientId", "")
    var microsoftClientId by str("microsoftClientId", "")

    fun trustSender(address: String) {
        trustedSenders = trustedSenders + address.lowercase()
    }
}
