package de.hotelremote.ui.ac

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import de.hotelremote.R

/*
 * Anzeigenamen für die rohen SmartIR-Namen (gemischte Groß-/Kleinschreibung, teils leer). Zuordnung ohne
 * Rücksicht auf Groß-/Kleinschreibung; Unbekanntes erscheint mit Rohnamen. Gesendet wird immer der Rohname.
 */

@StringRes
internal fun modeName(raw: String): Int? = when (raw.trim().lowercase()) {
    "cool" -> R.string.ac_mode_cool
    "heat" -> R.string.ac_mode_heat
    "dry" -> R.string.ac_mode_dry
    "fan_only", "fan" -> R.string.ac_mode_fan
    "auto" -> R.string.ac_auto
    "heat_cool" -> R.string.ac_mode_heat_cool
    else -> null
}

@StringRes
internal fun fanName(raw: String): Int? = when (raw.trim().lowercase()) {
    "" -> R.string.ac_fan_unnamed
    "auto" -> R.string.ac_auto
    "low", "lo" -> R.string.ac_fan_low
    "mid", "medium", "med", "middle" -> R.string.ac_fan_mid
    "high", "hi" -> R.string.ac_fan_high
    "quiet", "silent", "silence" -> R.string.ac_fan_quiet
    "turbo", "powerful" -> R.string.ac_fan_turbo
    else -> null
}

@StringRes
internal fun swingName(raw: String): Int? = when (raw.trim().lowercase()) {
    "" -> R.string.ac_fan_unnamed
    "on", "swing" -> R.string.ac_swing_on
    "off", "stop" -> R.string.ac_swing_off
    "auto" -> R.string.ac_auto
    "vertical" -> R.string.ac_swing_vertical
    "horizontal" -> R.string.ac_swing_horizontal
    "both" -> R.string.ac_swing_both
    else -> null
}

/** Übersetzter Name oder der Rohname. */
@Composable
internal fun label(raw: String, name: (String) -> Int?): String = name(raw)?.let { stringResource(it) } ?: raw

internal fun modeIcon(raw: String): ImageVector = when (raw.trim().lowercase()) {
    "cool" -> AcIcons.Cool
    "heat" -> AcIcons.Heat
    "dry" -> AcIcons.Dry
    "fan_only", "fan" -> AcIcons.Fan
    "auto" -> AcIcons.Auto
    "heat_cool" -> AcIcons.HeatCool
    else -> AcIcons.OtherMode
}
