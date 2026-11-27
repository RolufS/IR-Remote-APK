package de.hotelremote.data

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.io.encoding.Base64

/** Seite im Raum mit Fernseher und Klimaanlage; die Reihenfolge ist die der Wischseiten. */
@Serializable
enum class RoomPage { TV, AC }

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
    /** Einstellungen der Klima-Automatik (in der Einheit des Geräts); null = Standard. */
    val auto: AcAutoSettings? = null,
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
 * Klima-Automatik „kühler als draußen“: Ziel = draußen − [offset], nicht unter [notColderThan] und (wenn gesetzt)
 * nicht über [notWarmerThan]. Alle Werte in der Einheit des Geräts (°C bzw. °F), Modus immer Kühlen.
 */
@Serializable
data class AcAutoSettings(
    val offset: Double,
    val notColderThan: Double,
    val notWarmerThan: Double? = null,
    val fan: String = "auto",
) {
    companion object {
        /** 5 °C kühler, nicht unter 25 °C; bei °F-Geräten umgerechnet (9 °F, 77 °F). */
        fun defaults(fahrenheit: Boolean) = if (fahrenheit) AcAutoSettings(9.0, 77.0) else AcAutoSettings(5.0, 25.0)

        /** Erlaubter Abstand: 1–10 °C bzw. 2–18 °F. */
        fun offsetRange(fahrenheit: Boolean) = if (fahrenheit) 2.0..18.0 else 1.0..10.0
    }
}

/** Einstellungen der Automatik, ohne gespeicherte die Standardwerte in der Einheit des Geräts. */
val SavedAc.autoSettings get() = auto ?: AcAutoSettings.defaults(fahrenheit)

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
