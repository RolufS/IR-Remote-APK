package de.hotelremote.ui.language

import android.content.SharedPreferences
import androidx.core.content.edit

/** Merkt dauerhaft, ob die Sprache schon einmal gewählt wurde (Erststart). */
class LanguageChoice(private val prefs: SharedPreferences) {
    val needed: Boolean get() = !prefs.getBoolean(KEY_CHOSEN, false)

    fun markChosen() {
        prefs.edit { putBoolean(KEY_CHOSEN, true) }
    }

    companion object {
        const val PREFS = "settings"
        private const val KEY_CHOSEN = "language_chosen"
    }
}
