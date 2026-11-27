package de.hotelremote.data

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.io.encoding.Base64

/**
 * Zuletzt gesendeter (bzw. bei ausgeschalteter Anlage gemerkter) Zustand einer Klimaanlage. Die Anlage meldet
 * nichts zurück; die App zeigt diesen Zustand an. Namen von Modus, Lüfter und Swing roh wie in SmartIR.
 */
@Serializable
data class AcState(
    val power: Boolean,
    val mode: String,
    val temp: Double,
    val fan: String,
    /** null bei Geräten ohne Swing-Ebene. */
    val swing: String? = null,
)

/** Klimaanlage eines Raums mit eigener Kopie der Kommandos (unabhängig von `ac_codes.db`). */
@Serializable
data class SavedAc(
    val brand: String,
    val deviceLabel: String,
    val minTemp: Double,
    val maxTemp: Double,
    val precision: Double,
    val modes: List<String>,
    val fanModes: List<String>,
    val swingModes: List<String> = emptyList(),
    /** Base64 des Blobs aus `ac_device.commands` (Format siehe [AcCodes]). */
    val commands: String,
    val state: AcState,
) {
    val title get() = "$brand · $deviceLabel"

    /** Temperaturen in °F (siehe [acTempsInFahrenheit]). */
    val fahrenheit get() = acTempsInFahrenheit(maxTemp)

    fun decodeCodes(): AcCodes = AcCodes.decode(Base64.decode(commands))

    companion object {
        fun encodeCommands(blob: ByteArray): String = Base64.encode(blob)
    }
}

/**
 * SmartIR kennt kein Einheitenfeld; einige Geräte haben Werte etwa 60–88 (°F). Celsius-Geräte enden bei etwa 32,
 * daher gilt eine Höchsttemperatur über 40 als Fahrenheit.
 */
fun acTempsInFahrenheit(maxTemp: Double): Boolean = maxTemp > 40.0

/** „22 °C“, „22.5 °C“ bzw. „72 °F“ (Ziffern immer lateinisch wie auf den TV-Tasten). */
fun acTempText(temp: Double, fahrenheit: Boolean): String {
    val number = if (temp == Math.floor(temp)) temp.toLong().toString() else String.format(Locale.ROOT, "%.1f", temp)
    return "$number ${if (fahrenheit) "°F" else "°C"}"
}
