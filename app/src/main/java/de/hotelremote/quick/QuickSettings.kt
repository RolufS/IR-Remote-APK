package de.hotelremote.quick

import android.content.SharedPreferences
import androidx.core.content.edit

/** Stand der Einstellungen für den Bildschirm „Schnellsteuerung“. */
data class QuickPrefs(val inputKey: String?, val lockScreen: Boolean)

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

    fun load() = QuickPrefs(inputKey, lockScreen)

    companion object {
        const val PREFS = "quick_controls"
        private const val INPUT_KEY = "input_key"
        private const val LOCK_SCREEN = "lock_screen"
    }
}
