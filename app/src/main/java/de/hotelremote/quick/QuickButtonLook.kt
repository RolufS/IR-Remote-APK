package de.hotelremote.quick

import android.content.res.Resources
import androidx.annotation.DrawableRes
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.ui.automation.keyName

/** Farbe eines runden Knopfs (wie die Tasten der App). */
enum class QuickTone { KEY, POWER, INPUT, RED, GREEN, YELLOW, BLUE }

/**
 * Aussehen eines Knopfs in Widget, Benachrichtigung und Vorschau: Symbol und/oder kurze Beschriftung,
 * [labelSp] als Schriftgröße, [description] für Bedienungshilfen.
 */
data class QuickButtonLook(
    val tone: QuickTone,
    @param:DrawableRes val icon: Int?,
    val label: String?,
    val labelSp: Float,
    val description: String,
)

/** Längste Beschriftung im Knopf (Zeichen); länger wird mit „…“ gekürzt. */
const val MAX_LABEL_CHARS = 6

private const val BIG_SP = 18f
private const val SMALL_SP = 11f
private const val AUTOMATION_SP = 10f

/** Symbole bekannter Tasten. */
private val ICONS = mapOf(
    Keys.POWER to R.drawable.ic_quick_power,
    Keys.VOL_DOWN to R.drawable.ic_quick_vol_down,
    Keys.VOL_UP to R.drawable.ic_quick_vol_up,
    Keys.MUTE to R.drawable.ic_quick_mute,
    Keys.UP to R.drawable.ic_quick_up,
    Keys.DOWN to R.drawable.ic_quick_down,
    Keys.LEFT to R.drawable.ic_quick_left,
    Keys.RIGHT to R.drawable.ic_quick_right,
)

/** Kurze Beschriftungen wie auf einer Fernbedienung (sprachunabhängig). */
private val LABELS = mapOf(
    Keys.POWER_ON to "ON",
    Keys.POWER_OFF to "OFF",
    Keys.CH_UP to "CH ▲",
    Keys.CH_DOWN to "CH ▼",
    Keys.OK to "OK",
    Keys.SOURCE to "SOURCE",
    Keys.EXIT to "EXIT",
    Keys.INFO to "INFO",
    Keys.LIST to "LIST",
    Keys.PRE_CH to "PRE-CH",
)

private val TONES = mapOf(
    Keys.POWER to QuickTone.POWER,
    Keys.POWER_ON to QuickTone.POWER,
    Keys.POWER_OFF to QuickTone.POWER,
    Keys.SOURCE to QuickTone.INPUT,
    Keys.RED to QuickTone.RED,
    Keys.GREEN to QuickTone.GREEN,
    Keys.YELLOW to QuickTone.YELLOW,
    Keys.BLUE to QuickTone.BLUE,
) + Keys.HDMI.associateWith { QuickTone.INPUT }

private val COLOR_KEYS = setOf(Keys.RED, Keys.GREEN, Keys.YELLOW, Keys.BLUE)

/** Kürzt auf [MAX_LABEL_CHARS] Zeichen (Unicode-Codepoints, Emoji bleiben ganz). */
fun shortLabel(text: String): String {
    val trimmed = text.trim()
    if (trimmed.codePointCount(0, trimmed.length) <= MAX_LABEL_CHARS) return trimmed
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_LABEL_CHARS - 1)).trimEnd() + "…"
}

/** Lesbarer Name im Einrichten (Liste, Auswahl). */
fun quickButtonName(res: Resources, button: QuickButton): String = when (button) {
    is QuickButton.Key -> keyName(res, button.key)
    QuickButton.Input -> res.getString(R.string.qb_input)
    is QuickButton.Run -> res.getString(R.string.qb_automation, button.name)
}

fun quickButtonLook(res: Resources, button: QuickButton): QuickButtonLook {
    val description = when (button) {
        is QuickButton.Run -> button.name
        else -> quickButtonName(res, button)
    }
    return when (button) {
        QuickButton.Input -> QuickButtonLook(QuickTone.INPUT, R.drawable.ic_quick_input, null, SMALL_SP, description)
        is QuickButton.Run -> QuickButtonLook(QuickTone.KEY, R.drawable.ic_quick_bolt, shortLabel(button.name), AUTOMATION_SP, description)
        is QuickButton.Key -> {
            val key = button.key
            val tone = TONES[key] ?: QuickTone.KEY
            val icon = ICONS[key]
            val label = when {
                icon != null || key in COLOR_KEYS -> null
                key in LABELS -> LABELS.getValue(key)
                key.startsWith("DIGIT_") -> key.removePrefix("DIGIT_")
                key in Keys.HDMI -> "HDMI\n${Keys.HDMI.indexOf(key) + 1}"
                else -> shortLabel(keyName(res, key))
            }
            val size = if (label != null && label.length <= 2) BIG_SP else SMALL_SP
            QuickButtonLook(tone, icon, label, size, description)
        }
    }
}
