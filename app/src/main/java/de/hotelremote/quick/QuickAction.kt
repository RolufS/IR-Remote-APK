package de.hotelremote.quick

import de.hotelremote.Keys
import de.hotelremote.ui.remote.powerAction

/** Die vier Tasten der Schnellsteuerung (Kacheln, Widget, Sperrbildschirm-Benachrichtigung). */
enum class QuickAction { POWER, VOL_DOWN, VOL_UP, INPUT }

/** Ergebnis eines Tastendrucks außerhalb der App. */
enum class QuickResult { SENT, NO_REMOTE, KEY_MISSING, SEND_FAILED }

/** Mögliche Eingangstasten in fester Reihenfolge. */
private val INPUT_KEYS = Keys.HDMI + Keys.SOURCE

/** Eingangstasten, die diese Fernbedienung hat (HDMI 1–4, dann SOURCE). */
fun inputChoices(keys: Set<String>): List<String> = INPUT_KEYS.filter { it in keys }

/**
 * Taste für [action]; null, wenn die Fernbedienung keine passende hat. Ein/Aus wie ein kurzer Druck in der App;
 * Eingang: [inputKey] (gewählt in „Schnellsteuerung“), falls vorhanden, sonst HDMI 1, sonst SOURCE.
 */
fun quickKey(action: QuickAction, keys: Set<String>, inputKey: String?): String? = when (action) {
    QuickAction.POWER -> powerAction(keys, longPress = false)
    QuickAction.VOL_DOWN -> Keys.VOL_DOWN.takeIf { it in keys }
    QuickAction.VOL_UP -> Keys.VOL_UP.takeIf { it in keys }
    QuickAction.INPUT -> inputKey?.takeIf { it in INPUT_KEYS && it in keys }
        ?: listOf(Keys.HDMI1, Keys.SOURCE).firstOrNull { it in keys }
}
