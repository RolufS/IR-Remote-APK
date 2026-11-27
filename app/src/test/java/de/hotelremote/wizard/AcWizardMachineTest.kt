package de.hotelremote.wizard

import de.hotelremote.data.AcCodes
import de.hotelremote.data.AcDevice
import de.hotelremote.data.AcState
import de.hotelremote.data.PLAIN_PATHS
import de.hotelremote.data.SWING_PATHS
import de.hotelremote.data.acBlob
import de.hotelremote.data.pulsesOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class Dev(val device: AcDevice, val paths: List<String>)

private fun dev(
    id: Long,
    brandId: Long,
    paths: List<String>,
    models: List<String> = listOf("M$id"),
    maxTemp: Double = 30.0,
    blob: ByteArray = acBlob(paths),
) = Dev(
    AcDevice(
        id = id, brandId = brandId, brandName = if (brandId == 1L) "Daikin" else "Gree", models = models,
        source = "$id.json", minTemp = 16.0, maxTemp = maxTemp, precision = 1.0,
        modes = listOf("cool"), fanModes = listOf("auto"), swingModes = emptyList(), commands = blob,
    ),
    paths,
)

/** Kühlen fehlt: Heizen, Lüfter low, 18–22. */
private val NO_COOL = listOf("off") + (18..22).map { "heat/low/$it" }

/** Kein Auto-Lüfter: Kühlen low/high, 16–30. */
private val NO_AUTO = listOf("off") + listOf("low", "high").flatMap { f -> (16..30).map { "cool/$f/$it" } }

/** °F-Gerät 60–86. */
private val FAHRENHEIT = listOf("off") + (60..86).map { "cool/auto/$it" }

/** 23 und 25: Gleichstand um 24, die niedrigere gewinnt. */
private val TIE = listOf("off", "cool/auto/23", "cool/auto/25")

private class FakeAcData : AcWizardData {
    val devices = listOf(
        dev(11, 1, PLAIN_PATHS, models = listOf("FTXS25", "FTXS35")),
        dev(12, 1, listOf("cool/auto/24")), // ohne „Aus“: übersprungen
        dev(13, 1, NO_COOL, models = listOf("Unknown")),
        dev(14, 1, emptyList(), blob = byteArrayOf(1, 2, 3)), // unlesbar: übersprungen
        dev(15, 1, NO_AUTO),
        dev(16, 1, FAHRENHEIT, maxTemp = 86.0),
        dev(17, 1, SWING_PATHS),
        dev(18, 1, TIE),
        dev(21, 2, PLAIN_PATHS),
    )
    val loads = mutableListOf<Long>()

    override fun deviceIds(brandId: Long?) =
        (if (brandId == null) devices.sortedByDescending { it.device.brandId } else devices.filter { it.device.brandId == brandId })
            .map { it.device.id }

    override fun device(id: Long): AcDevice? {
        loads += id
        return devices.firstOrNull { it.device.id == id }?.device
    }

    fun paths(id: Long) = devices.first { it.device.id == id }.paths
}

class AcWizardMachineTest {
    private val data = FakeAcData()
    private val m = AcWizardMachine(data)

    private fun test() = m.state as AcWizardState.TestCode

    private fun assertSends(id: Long, path: String) =
        assertEquals(pulsesOf(data.paths(id), path), m.testSignal()!!.frame)

    @Test fun startsOnBrandPick() {
        assertEquals(AcWizardState.PickBrand, m.state)
        assertNull(m.testSignal())
        m.answer(true)
        assertEquals(AcWizardState.PickBrand, m.state)
    }

    @Test fun brandTestsFirstDeviceWithCoolAutoAt24() {
        m.pickBrand(1)
        val s = test()
        assertEquals(1L, s.brandId)
        assertEquals(0, s.index)
        assertEquals(8, s.total)
        assertEquals(11L, s.deviceId)
        assertEquals("Daikin", s.brandName)
        assertEquals(AcState(power = true, mode = "cool", temp = 24.0, fan = "auto"), s.testState)
        assertFalse(s.fahrenheit)
        assertSends(11, "cool/auto/24")
        assertEquals(m.testSignal(), m.testSignal()) // „Nochmal senden“: dasselbe Signal
        assertEquals(38_000, m.testSignal()!!.freq)
    }

    @Test fun noGoesThroughDevicesWithNearestCombinationAndSkipsUnusable() {
        m.pickBrand(1)
        m.answer(false)
        // 12 ohne „Aus“ übersprungen; 13 ohne Kühlen: erster Modus, sein Lüfter, nächste Temperatur
        assertEquals(2, test().index)
        assertEquals(AcState(true, "heat", 22.0, "low"), test().testState)
        assertSends(13, "heat/low/22")
        m.answer(false)
        // 14 unlesbar übersprungen; 15 ohne Auto-Lüfter: erster Lüfter
        assertEquals(4, test().index)
        assertEquals(AcState(true, "cool", 24.0, "low"), test().testState)
        m.answer(false)
        assertEquals(AcState(true, "cool", 75.0, "auto"), test().testState)
        assertTrue(test().fahrenheit)
        assertSends(16, "cool/auto/75")
        m.answer(false)
        assertEquals(AcState(true, "cool", 23.0, "auto", swing = "off"), test().testState)
        assertSends(17, "cool/auto/off/23")
        m.answer(false)
        assertEquals(AcState(true, "cool", 23.0, "auto"), test().testState)
        assertEquals(7, test().index)
        m.answer(false)
        assertEquals(AcWizardState.NoMatch(1), m.state)
        assertNull(m.testSignal())
    }

    @Test fun yesSendsOffForConfirmationThenSaves() {
        m.pickBrand(1)
        m.answer(true)
        assertEquals(AcWizardState.ConfirmOff(1, 0, 8, 11), m.state)
        assertSends(11, "off")
        m.answer(true)
        val ac = (m.state as AcWizardState.Save).ac
        assertEquals("Daikin", ac.brand)
        assertEquals("FTXS25", ac.deviceLabel)
        assertEquals(AcState(power = false, mode = "cool", temp = 24.0, fan = "auto"), ac.state)
        assertEquals(16.0, ac.minTemp, 0.0)
        assertEquals(30.0, ac.maxTemp, 0.0)
        assertArrayEquals(data.devices[0].device.commands, kotlin.io.encoding.Base64.decode(ac.commands))
        assertEquals(PLAIN_PATHS.toSet(), ac.decodeCodes().paths)
        assertNull(m.testSignal())
    }

    @Test fun deviceWithoutModelNameIsLabelledByPosition() {
        m.pickBrand(1)
        m.answer(false)
        m.answer(true)
        m.answer(true)
        val ac = (m.state as AcWizardState.Save).ac
        assertEquals("Code 3", ac.deviceLabel)
        assertEquals(AcState(false, "heat", 22.0, "low"), ac.state)
    }

    @Test fun noOnOffConfirmationGoesToNextDevice() {
        m.pickBrand(1)
        m.answer(true)
        m.answer(false)
        assertEquals(13L, test().deviceId)
        assertEquals(2, test().index)
    }

    @Test fun noMatchAtEndOfListAfterRejectedConfirmation() {
        m.pickBrand(2)
        assertEquals(1, test().total)
        m.answer(true)
        m.answer(false)
        assertEquals(AcWizardState.NoMatch(2), m.state)
        assertTrue(m.back())
        assertEquals(AcWizardState.PickBrand, m.state)
    }

    @Test fun unknownBrandTriesAllDevicesWithTheirBrandNames() {
        m.pickBrand(null)
        assertNull(test().brandId)
        assertEquals(9, test().total)
        assertEquals("Gree", test().brandName)
        m.answer(false)
        assertEquals("Daikin", test().brandName)
        assertEquals(11L, test().deviceId)
        m.answer(true)
        assertEquals(AcWizardState.ConfirmOff(null, 1, 9, 11), m.state)
    }

    @Test fun brandWithoutDevicesIsNoMatch() {
        m.pickBrand(99)
        assertEquals(AcWizardState.NoMatch(99), m.state)
    }

    @Test fun backGoesOneStepAndSkipsUnusableDevices() {
        assertFalse(m.back())
        m.pickBrand(1)
        m.answer(false)
        m.answer(false)
        assertEquals(4, test().index)
        assertTrue(m.back())
        assertEquals(2, test().index) // 14 (unlesbar) übersprungen
        assertTrue(m.back())
        assertEquals(0, test().index) // 12 (ohne „Aus“) übersprungen
        assertTrue(m.back())
        assertEquals(AcWizardState.PickBrand, m.state)
    }

    @Test fun backFromConfirmAndSave() {
        m.pickBrand(1)
        m.answer(false)
        m.answer(true)
        val confirm = m.state
        m.answer(true)
        assertTrue(m.state is AcWizardState.Save)
        assertTrue(m.back())
        assertEquals(confirm, m.state)
        assertTrue(m.back())
        assertEquals(13L, test().deviceId)
        assertEquals(2, test().index)
    }

    @Test fun deviceIsLoadedOncePerStep() {
        m.pickBrand(2)
        data.loads.clear()
        m.testSignal()
        m.testSignal()
        m.answer(true)
        m.testSignal()
        assertEquals(emptyList<Long>(), data.loads)
    }

    @Test fun testStateHelperRastsLikeTheController() {
        val codes = AcCodes.decode(acBlob(TIE))
        assertEquals(AcState(true, "cool", 23.0, "auto"), AcWizardMachine.testState(codes, 30.0))
        assertNull(AcWizardMachine.testState(AcCodes.decode(acBlob(listOf("off"))), 30.0))
    }
}
