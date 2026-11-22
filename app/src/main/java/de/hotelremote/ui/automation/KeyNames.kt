package de.hotelremote.ui.automation

import android.content.res.Resources
import de.hotelremote.Keys
import de.hotelremote.R

/** Reihenfolge der kanonischen Tasten in der Tastenauswahl; Extras folgen alphabetisch. */
private val ORDER: List<String> = listOf(
    Keys.POWER, Keys.POWER_ON, Keys.POWER_OFF, Keys.MUTE,
    Keys.VOL_UP, Keys.VOL_DOWN, Keys.CH_UP, Keys.CH_DOWN,
    Keys.DIGIT_1, Keys.DIGIT_2, Keys.DIGIT_3, Keys.DIGIT_4, Keys.DIGIT_5,
    Keys.DIGIT_6, Keys.DIGIT_7, Keys.DIGIT_8, Keys.DIGIT_9, Keys.DIGIT_0,
    Keys.SOURCE, Keys.HDMI1, Keys.HDMI2, Keys.HDMI3, Keys.HDMI4,
    Keys.MENU, Keys.HOME, Keys.OK, Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT,
    Keys.BACK, Keys.EXIT, Keys.INFO, Keys.LIST, Keys.PRE_CH,
    Keys.RED, Keys.GREEN, Keys.YELLOW, Keys.BLUE,
)

/** Alle Tasten einer Fernbedienung in der Reihenfolge der Auswahl. */
fun orderedKeys(keys: Set<String>): List<String> =
    ORDER.filter { it in keys } +
        keys.filter(Keys::isExtra).sortedBy { Keys.extraLabel(it).lowercase() } +
        keys.filter { it !in ORDER && !Keys.isExtra(it) }.sorted()

/** Lesbarer Name einer Taste in der App-Sprache; Extras mit ihrem Label. */
fun keyName(res: Resources, key: String): String {
    if (Keys.isExtra(key)) return Keys.extraLabel(key)
    if (key.startsWith("DIGIT_")) key.removePrefix("DIGIT_").toIntOrNull()?.let { return res.getString(R.string.key_digit, it) }
    Keys.HDMI.indexOf(key).takeIf { it >= 0 }?.let { return res.getString(R.string.key_hdmi, it + 1) }
    val id = when (key) {
        Keys.POWER -> R.string.wiz_key_power
        Keys.POWER_ON -> R.string.key_power_on
        Keys.POWER_OFF -> R.string.key_power_off
        Keys.MUTE -> R.string.key_mute
        Keys.VOL_UP -> R.string.key_vol_up
        Keys.VOL_DOWN -> R.string.key_vol_down
        Keys.CH_UP -> R.string.key_ch_up
        Keys.CH_DOWN -> R.string.key_ch_down
        Keys.SOURCE -> R.string.key_source
        Keys.MENU -> R.string.key_menu
        Keys.HOME -> R.string.key_home
        Keys.OK -> R.string.key_ok
        Keys.UP -> R.string.key_up
        Keys.DOWN -> R.string.key_down
        Keys.LEFT -> R.string.key_left
        Keys.RIGHT -> R.string.key_right
        Keys.BACK -> R.string.cd_back
        Keys.EXIT -> R.string.key_exit
        Keys.INFO -> R.string.key_info
        Keys.LIST -> R.string.key_list
        Keys.PRE_CH -> R.string.key_pre_ch
        Keys.RED -> R.string.cd_color_red
        Keys.GREEN -> R.string.cd_color_green
        Keys.YELLOW -> R.string.cd_color_yellow
        Keys.BLUE -> R.string.cd_color_blue
        else -> return key
    }
    return res.getString(id)
}
