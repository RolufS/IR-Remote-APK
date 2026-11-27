package de.hotelremote.acauto

import de.hotelremote.data.AcAutoSettings
import de.hotelremote.data.AcCodes
import de.hotelremote.data.AcState
import de.hotelremote.data.SavedAc
import de.hotelremote.ui.AcRules
import de.hotelremote.weather.celsiusToFahrenheit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Untere und obere Grenze des Ziels: Einstellungen, begrenzt auf den Bereich des Geräts. */
fun acAutoBounds(s: AcAutoSettings, minTemp: Double, maxTemp: Double): ClosedFloatingPointRange<Double> {
    val low = max(s.notColderThan, minTemp).coerceAtMost(maxTemp)
    val high = min(s.notWarmerThan ?: maxTemp, maxTemp).coerceAtLeast(low)
    return low..high
}

/**
 * Ziel der Klima-Automatik in der Einheit des Geräts:
 * clamp(rundeAufPrecision(außen − Abstand), max(Untergrenze, minTemp), min(Obergrenze, maxTemp)).
 * [outdoorC] kommt immer in °C; bei °F-Geräten wird vorher umgerechnet.
 * Beispiel: draußen 33 °C, Abstand 5, Untergrenze 25 → 28 °C; draußen 28 °C → 25 °C.
 */
fun acAutoTarget(
    outdoorC: Double,
    s: AcAutoSettings,
    minTemp: Double,
    maxTemp: Double,
    precision: Double,
    fahrenheit: Boolean,
): Double {
    val outside = if (fahrenheit) celsiusToFahrenheit(outdoorC) else outdoorC
    val bounds = acAutoBounds(s, minTemp, maxTemp)
    return roundToPrecision(outside - s.offset, precision).coerceIn(bounds.start, bounds.endInclusive)
}

fun acAutoTarget(outdoorC: Double, ac: SavedAc, s: AcAutoSettings): Double =
    acAutoTarget(outdoorC, s, ac.minTemp, ac.maxTemp, ac.precision, ac.fahrenheit)

/** Auf ein Vielfaches von [precision] runden (halbe Schritte aufwärts); ohne sinnvolle Angabe auf ganze Grad. */
fun roundToPrecision(v: Double, precision: Double): Double {
    val p = if (precision > 0) precision else 1.0
    return (v / p).roundToLong() * p
}

/**
 * Zustand, den die Automatik sendet: Ein · Kühlen · Ziel · Lüfter, gerastet auf vorhandene Kombinationen.
 * Unter den vorhandenen Temperaturen gewinnt die nächste innerhalb der Grenzen (sonst die nächste überhaupt).
 */
internal fun acAutoState(codes: AcCodes, ac: SavedAc, s: AcAutoSettings, target: Double): AcState {
    val mode = codes.modes().firstOrNull { it.equals(COOL, ignoreCase = true) } ?: COOL
    val base = AcRules.normalize(codes, AcState(true, mode, target, s.fan, ac.state.swing))
    val temps = codes.temps(base.mode, base.fan, base.swing)
    val bounds = acAutoBounds(s, ac.minTemp, ac.maxTemp)
    val temp = temps.filter { it in bounds }.ifEmpty { temps }.minByOrNull { abs(it - target) } ?: return base
    return base.copy(temp = temp)
}

/** Lüfterstufen im Kühlmodus (für die Auswahl in den Einstellungen). */
internal fun acAutoFans(codes: AcCodes): List<String> {
    val mode = codes.modes().firstOrNull { it.equals(COOL, ignoreCase = true) } ?: codes.modes().firstOrNull() ?: return emptyList()
    return codes.fans(mode)
}

private const val COOL = "cool"
