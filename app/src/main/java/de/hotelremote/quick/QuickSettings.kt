package de.hotelremote.quick

import android.content.SharedPreferences
import androidx.core.content.edit
import de.hotelremote.ir.ToggleStore

/** Ansicht der Sperrbildschirm-Benachrichtigung: nur die Knopfreihe oder zusätzlich aufziehbar zur Mini-Fernbedienung. */
enum class LockStyle { SMALL, EXPANDED }

/** Stand der Einstellungen für den Bildschirm „Schnellsteuerung“. */
data class QuickPrefs(
    val inputKey: String?,
    val lockScreen: Boolean,
    val notificationButtons: List<QuickButton> = QuickButtons.DEFAULT,
    val lockStyle: LockStyle = LockStyle.EXPANDED,
    val fullRemote: Boolean = false,
)

/**
 * Einstellungen der Schnellsteuerung. Die Eingangstaste gilt global für alle Fernbedienungen: fehlt sie einer
 * Fernbedienung, greift der Standard (HDMI 1, sonst SOURCE). Das hält das Speicherformat der Fernbedienungen
 * unverändert und reicht, weil die Schnellsteuerung ohnehin nur die zuletzt benutzte Fernbedienung bedient.
 */
class QuickSettings(private val prefs: SharedPreferences) {
    /** Gewählte Eingangstaste (null = Standard). */
    var inputKey: String?
        get() = prefs.getString(INPUT_KEY, null)
        set(value) = prefs.edit { putString(INPUT_KEY, value) }

    /** Steuerung als Benachrichtigung auf dem Sperrbildschirm (Standard aus). */
    var lockScreen: Boolean
        get() = prefs.getBoolean(LOCK_SCREEN, false)
        set(value) = prefs.edit { putBoolean(LOCK_SCREEN, value) }

    /** Ansicht der Sperrbildschirm-Benachrichtigung (Standard „Erweitert“). */
    var lockStyle: LockStyle
        get() = LockStyle.entries.firstOrNull { it.name == prefs.getString(LOCK_STYLE, null) } ?: LockStyle.EXPANDED
        set(value) = prefs.edit { putString(LOCK_STYLE, value.name) }

    /**
     * Ganze Fernbedienung über dem Sperrbildschirm (Standard aus): Knopf in der Benachrichtigung und Kachel
     * „TV Fernbedienung“. Wer das Handy in der Hand hat, kann dann alle Tasten senden, sonst nichts.
     */
    var fullRemote: Boolean
        get() = prefs.getBoolean(FULL_REMOTE, false)
        set(value) = prefs.edit { putBoolean(FULL_REMOTE, value) }

    /** Knöpfe der Sperrbildschirm-Benachrichtigung (eine Belegung für alle, 1 bis 5). */
    var notificationButtons: List<QuickButton>
        get() = QuickButtons.normalize(QuickButtons.decodeList(prefs.getString(NOTIFICATION_BUTTONS, null)) ?: QuickButtons.DEFAULT, QuickButtons.MAX_NOTIFICATION)
        set(value) = prefs.edit { putString(NOTIFICATION_BUTTONS, QuickButtons.encodeList(QuickButtons.normalize(value, QuickButtons.MAX_NOTIFICATION))) }

    /** Knöpfe des Widgets [widgetId] (1 bis 6); ohne eigene Belegung die Standardbelegung. */
    fun widgetButtons(widgetId: Int): List<QuickButton> =
        QuickButtons.normalize(QuickButtons.decodeList(prefs.getString(widgetKey(widgetId), null)) ?: QuickButtons.DEFAULT, QuickButtons.MAX_WIDGET)

    fun setWidgetButtons(widgetId: Int, buttons: List<QuickButton>) =
        prefs.edit { putString(widgetKey(widgetId), QuickButtons.encodeList(QuickButtons.normalize(buttons, QuickButtons.MAX_WIDGET))) }

    /** Widget vom Startbildschirm entfernt: Belegung löschen. */
    fun removeWidget(widgetId: Int) = prefs.edit { remove(widgetKey(widgetId)) }

    fun load() = QuickPrefs(inputKey, lockScreen, notificationButtons, lockStyle, fullRemote)

    /**
     * Toggle-Zustand (RC5/RC6) der Schnellsteuerung, dauerhaft gespeichert: Kachel- und Widget-Tipps laufen oft in
     * einem frischen Prozess, und zwei Drücke mit gleichem Toggle-Bit ignoriert z. B. ein Philips-Fernseher.
     * Schlüssel: Hash der Pulsfolge (List.hashCode ist über Prozesse hinweg stabil).
     */
    val toggles: ToggleStore = object : ToggleStore {
        override fun get(frame: List<Int>) = prefs.getBoolean(toggleKey(frame), false)

        override fun set(frame: List<Int>, nextIsAlt: Boolean) = prefs.edit(commit = true) { putBoolean(toggleKey(frame), nextIsAlt) }
    }

    private fun toggleKey(frame: List<Int>) = TOGGLE_PREFIX + frame.hashCode()

    private fun widgetKey(widgetId: Int) = WIDGET_PREFIX + widgetId

    companion object {
        const val PREFS = "quick_controls"
        private const val INPUT_KEY = "input_key"
        private const val LOCK_SCREEN = "lock_screen"
        private const val LOCK_STYLE = "lock_style"
        private const val FULL_REMOTE = "full_remote"
        private const val TOGGLE_PREFIX = "toggle_"
        private const val NOTIFICATION_BUTTONS = "notification_buttons"
        private const val WIDGET_PREFIX = "widget_buttons_"
    }
}
