package de.hotelremote.acauto

import de.hotelremote.data.AcAutoSettings
import de.hotelremote.data.AcCodes
import de.hotelremote.data.AcState
import de.hotelremote.data.SWING_PATHS
import de.hotelremote.data.acBlob
import de.hotelremote.data.autoSettings
import de.hotelremote.data.testAc
import org.junit.Assert.assertEquals
import org.junit.Test

class AcAutoTargetTest {
    private val c = AcAutoSettings.defaults(fahrenheit = false)

    private fun target(outdoor: Double, s: AcAutoSettings = c, min: Double = 16.0, max: Double = 30.0, precision: Double = 1.0) =
        acAutoTarget(outdoor, s, min, max, precision, fahrenheit = false)

    @Test fun specExamples() {
        assertEquals(28.0, target(33.0), 0.0)
        assertEquals(25.0, target(28.0), 0.0)
    }

    @Test fun defaultsAreFiveBelowAndNotColderThan25() {
        assertEquals(AcAutoSettings(5.0, 25.0, null, "auto"), c)
        assertEquals(1.0..10.0, AcAutoSettings.offsetRange(false))
    }

    @Test fun roundsToPrecision() {
        assertEquals(29.0, target(33.6), 0.0) // 28,6 → 29
        assertEquals(28.0, target(33.4), 0.0)
        assertEquals(28.5, target(33.4, precision = 0.5), 0.0)
        assertEquals(28.5, target(33.25, precision = 0.5), 0.0) // 28,25 → 28,5 (halbe Schritte aufwärts)
        assertEquals(28.0, roundToPrecision(28.2, 0.0), 0.0)
    }

    @Test fun optionalCeilingAndDeviceRange() {
        assertEquals(27.0, target(40.0, c.copy(notWarmerThan = 27.0)), 0.0)
        assertEquals(30.0, target(45.0), 0.0) // Höchsttemperatur des Geräts
        assertEquals(26.0, target(10.0, c.copy(notColderThan = 20.0), min = 26.0), 0.0) // Untergrenze des Geräts
        // Unsinnige Grenzen (oben unter unten) brechen nicht: unten gewinnt.
        assertEquals(25.0, target(40.0, c.copy(notWarmerThan = 22.0)), 0.0)
        assertEquals(30.0, target(20.0, c.copy(notColderThan = 35.0)), 0.0)
    }

    @Test fun fahrenheitDevicesCalculateInFahrenheit() {
        val f = AcAutoSettings.defaults(fahrenheit = true)
        assertEquals(AcAutoSettings(9.0, 77.0), f)
        assertEquals(2.0..18.0, AcAutoSettings.offsetRange(true))
        // 33 °C = 91,4 °F − 9 = 82,4 → 82 °F
        assertEquals(82.0, acAutoTarget(33.0, f, 60.0, 88.0, 1.0, fahrenheit = true), 0.0)
        // 28 °C = 82,4 °F − 9 = 73,4 → Untergrenze 77 °F
        assertEquals(77.0, acAutoTarget(28.0, f, 60.0, 88.0, 1.0, fahrenheit = true), 0.0)
        // Ohne gespeicherte Einstellungen nimmt ein °F-Gerät die °F-Standardwerte.
        assertEquals(f, testAc(maxTemp = 88.0).autoSettings)
    }

    @Test fun stateIsOnCoolTargetFanSnappedInsideBounds() {
        // PLAIN_PATHS: Kühlen Auto 20–24, Low 22–23.
        val ac = testAc(state = AcState(false, "heat", 19.0, "auto"))
        val codes = ac.decodeCodes()
        val s = AcAutoSettings(5.0, 22.0, fan = "LOW")
        assertEquals(AcState(true, "cool", 23.0, "low"), acAutoState(codes, ac, s, 23.0))
        // Ziel 28 gibt es nicht: nächste vorhandene Temperatur innerhalb der Grenzen.
        assertEquals(AcState(true, "cool", 24.0, "auto"), acAutoState(codes, ac, s.copy(fan = "auto"), 28.0))
        assertEquals(listOf("auto", "low"), acAutoFans(codes))
    }

    @Test fun snappingNeverGoesBelowTheFloorWhenAWarmerValueExists() {
        // Vorhanden 22, 22,5, 23 (Swing): Untergrenze 22,6, Ziel 22,6 → 23 statt der näheren 22,5.
        val ac = testAc(paths = SWING_PATHS, state = AcState(true, "cool", 22.0, "auto", "off"))
        val codes = AcCodes.decode(acBlob(SWING_PATHS))
        val s = AcAutoSettings(5.0, 22.6)
        assertEquals(AcState(true, "cool", 23.0, "auto", "off"), acAutoState(codes, ac, s, 22.6))
    }
}
