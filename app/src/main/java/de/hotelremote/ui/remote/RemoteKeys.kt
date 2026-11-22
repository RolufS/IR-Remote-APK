package de.hotelremote.ui.remote

import de.hotelremote.Keys
import kotlinx.coroutines.Job

/** Welche Power-Taste gesendet wird; null, wenn die Fernbedienung keine hat. */
fun powerAction(keys: Set<String>, longPress: Boolean): String? = when {
    Keys.POWER in keys -> Keys.POWER
    longPress -> listOf(Keys.POWER_OFF, Keys.POWER_ON).firstOrNull { it in keys }
    else -> listOf(Keys.POWER_ON, Keys.POWER_OFF).firstOrNull { it in keys }
}

/** Die Eingangsleiste HDMI 1-4 erscheint nur, wenn es direkte HDMI-Codes gibt. */
fun showInputRow(keys: Set<String>) = Keys.HDMI1 in keys

/** Bindeglied zum Sender: einmal senden oder beim Halten wiederholen (Job abbrechen = Loslassen). */
interface KeyHandler {
    fun press(key: String)

    fun hold(key: String): Job?
}
