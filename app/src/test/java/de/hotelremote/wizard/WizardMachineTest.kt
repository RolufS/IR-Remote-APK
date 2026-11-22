package de.hotelremote.wizard

import de.hotelremote.Keys
import de.hotelremote.data.ScanEntry
import de.hotelremote.ir.IrSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun sig(codeSetId: Long, key: String) =
    IrSignal(freq = 38000, frame = listOf(codeSetId.toInt()) + key.map { it.code }, periodMs = 100)

private fun keySet(codeSetId: Long, vararg keys: String): Map<String, IrSignal> =
    keys.associateWith { sig(codeSetId, it) }

private class FakeWizardData : WizardData {
    val brands = mapOf(1L to "Alpha", 2L to "Beta", 3L to "Delta", 4L to "Leer", 5L to "Gamma", 6L to "Epsilon")
    val brandCodeSets = mapOf(
        1L to listOf(10L, 11L),
        2L to listOf(20L),
        3L to listOf(30L, 31L),
        4L to emptyList(),
        6L to listOf(60L, 61L),
    )
    val codeSets = mapOf(
        10L to keySet(10, Keys.MUTE, Keys.VOL_UP, Keys.VOL_DOWN, Keys.POWER),
        11L to keySet(11, Keys.VOL_DOWN, Keys.POWER_ON),
        20L to keySet(20, Keys.MUTE, Keys.POWER),
        30L to keySet(30, Keys.CH_UP),
        31L to keySet(31, Keys.POWER),
        60L to keySet(60, Keys.MUTE),
        61L to keySet(61, Keys.POWER),
        100L to keySet(100, Keys.POWER_ON),
        102L to keySet(102, Keys.POWER_ON, Keys.POWER),
        103L to keySet(103, Keys.POWER_ON),
        104L to keySet(104, Keys.POWER),
        105L to keySet(105, Keys.VOL_UP),
        106L to keySet(106, Keys.POWER_ON),
    )
    val variants = mapOf(
        (10L to 1L) to listOf(
            mapOf(Keys.HDMI1 to sig(10, "V0-HDMI1"), Keys.HDMI2 to sig(10, "V0-HDMI2")),
            mapOf(Keys.HDMI1 to sig(10, "V1-HDMI1"), Keys.HDMI2 to sig(10, "V1-HDMI2")),
        ),
    )
    val scan = listOf(
        ScanEntry(0, 100, 5, "Gamma", sig(100, Keys.POWER_ON)),
        ScanEntry(1, 10, 1, "Alpha", sig(10, Keys.POWER)),
        ScanEntry(2, 102, 5, "Gamma", sig(102, Keys.POWER_ON)),
        ScanEntry(3, 103, 5, "Gamma", sig(103, Keys.POWER_ON)),
        ScanEntry(4, 104, 5, "Gamma", sig(104, Keys.POWER)),
        ScanEntry(5, 105, 5, "Gamma", sig(105, Keys.VOL_UP)),
        ScanEntry(6, 106, 5, "Gamma", sig(106, Keys.POWER_ON)),
    )

    override fun codeSetsForBrand(brandId: Long) = brandCodeSets[brandId] ?: emptyList()
    override fun keys(codeSetId: Long) = codeSets[codeSetId] ?: emptyMap()
    override fun inputVariants(codeSetId: Long, brandId: Long) = variants[codeSetId to brandId] ?: emptyList()
    override fun scanEntries() = scan
    override fun brandName(brandId: Long) = brands.getValue(brandId)
}

class WizardMachineTest {
    private val data = FakeWizardData()
    private val m = WizardMachine(data)

    private fun candidatePositions() = (m.state as WizardState.ScanVerify).candidates.map { it.position }

    @Test
    fun startsAtPickBrand() {
        assertEquals(WizardState.PickBrand, m.state)
        assertNull(m.testSignal())
    }

    @Test
    fun brandFlowHappyPathWithInputs() {
        m.pickBrand(1)
        assertEquals(WizardState.TestCode(1, 0, 2, 10, Keys.MUTE), m.state)
        assertEquals(sig(10, Keys.MUTE), m.testSignal())
        m.answer(true)
        assertEquals(WizardState.Confirm(1, 10, Keys.VOL_UP, fromScan = false), m.state)
        assertEquals(sig(10, Keys.VOL_UP), m.testSignal())
        m.answer(true)
        assertEquals(WizardState.TestInputs(1, 10, 0, 2), m.state)
        assertEquals(sig(10, "V0-HDMI1"), m.testSignal())
        m.answer(false)
        assertEquals(WizardState.TestInputs(1, 10, 1, 2), m.state)
        assertEquals(sig(10, "V1-HDMI1"), m.testSignal())
        m.answer(true)
        val save = m.state as WizardState.Save
        assertEquals("Alpha", save.brandName)
        assertEquals("Code 1", save.codeLabel)
        assertTrue(save.hasInputs)
        assertEquals(sig(10, "V1-HDMI1"), save.keys[Keys.HDMI1])
        assertEquals(sig(10, "V1-HDMI2"), save.keys[Keys.HDMI2])
        assertEquals(sig(10, Keys.MUTE), save.keys[Keys.MUTE])
        assertEquals(6, save.keys.size)
        assertNull(m.testSignal())
    }

    @Test
    fun codeSetWithoutMuteUsesVolDownAndSkipsConfirm() {
        m.pickBrand(1)
        m.answer(false)
        assertEquals(WizardState.TestCode(1, 1, 2, 11, Keys.VOL_DOWN), m.state)
        assertEquals(sig(11, Keys.VOL_DOWN), m.testSignal())
        m.answer(true)
        assertEquals(WizardState.Save("Alpha", "Code 2", data.keys(11), hasInputs = false), m.state)
    }

    @Test
    fun allCodesRejectedGivesNoMatch() {
        m.pickBrand(2)
        m.answer(false)
        assertEquals(WizardState.NoMatch(2, null), m.state)
    }

    @Test
    fun brandWithoutCodeSetsGivesNoMatch() {
        m.pickBrand(4)
        assertEquals(WizardState.NoMatch(4, null), m.state)
    }

    @Test
    fun codeSetWithoutAnyTestKeyIsSkipped() {
        m.pickBrand(3)
        assertEquals(WizardState.TestCode(3, 1, 2, 31, Keys.POWER), m.state)
    }

    @Test
    fun confirmNoContinuesWithNextCode() {
        m.pickBrand(1)
        m.answer(true)
        m.answer(false)
        assertEquals(WizardState.TestCode(1, 1, 2, 11, Keys.VOL_DOWN), m.state)
    }

    @Test
    fun allInputVariantsRejectedSavesWithoutInputs() {
        m.pickBrand(1)
        m.answer(true)
        m.answer(true)
        m.answer(false)
        m.answer(false)
        assertEquals(WizardState.Save("Alpha", "Code 1", data.keys(10), hasInputs = false), m.state)
    }

    @Test
    fun tvOffModeTestsWithPower() {
        m.pickBrand(1)
        m.useTvOffMode()
        assertEquals(WizardState.TestCode(1, 0, 2, 10, Keys.POWER), m.state)
        assertEquals(sig(10, Keys.POWER), m.testSignal())
        m.answer(false)
        assertEquals(WizardState.TestCode(1, 1, 2, 11, Keys.POWER_ON), m.state)
    }

    @Test
    fun tvOffModeSkipsCodeSetsWithoutPower() {
        m.pickBrand(6)
        assertEquals(WizardState.TestCode(6, 0, 2, 60, Keys.MUTE), m.state)
        m.useTvOffMode()
        assertEquals(WizardState.TestCode(6, 1, 2, 61, Keys.POWER), m.state)
    }

    @Test
    fun tvOffModePersistsOnBackAndResetsOnNewBrand() {
        m.pickBrand(1)
        m.answer(false)
        m.useTvOffMode()
        m.back()
        // TV-aus-Modus bleibt für die Marke aktiv, auch beim Zurückgehen.
        assertEquals(WizardState.TestCode(1, 0, 2, 10, Keys.POWER), m.state)
        m.back()
        m.pickBrand(1)
        // Neue Markenwahl setzt den Modus zurück.
        assertEquals(WizardState.TestCode(1, 0, 2, 10, Keys.MUTE), m.state)
    }

    @Test
    fun scanStartsAtPositionWithBrandLabel() {
        m.startScan()
        assertEquals(WizardState.Scanning(0, 7, "Gamma"), m.state)
        assertEquals(data.scan[0].signal, m.currentScanSignal())
        assertEquals(data.scan[0].signal, m.testSignal())
        m.scanAdvance()
        assertEquals(WizardState.Scanning(1, 7, "Alpha"), m.state)
        assertEquals(data.scan[1].signal, m.currentScanSignal())
        m.startScan(fromPosition = 7)
        assertEquals(WizardState.NoMatch(null, null), m.state)
    }

    @Test
    fun scanStopVerifiesLastFiveNewestFirst() {
        m.startScan()
        repeat(6) { m.scanAdvance() }
        m.stopScan()
        assertEquals(listOf(6, 5, 4, 3, 2), candidatePositions())
        val verify = m.state as WizardState.ScanVerify
        assertEquals(0, verify.index)
        assertEquals(Keys.POWER_ON, verify.testKey)
        assertEquals(sig(106, Keys.POWER_ON), m.testSignal())
    }

    @Test
    fun scanStoppedEarlyHasFewerCandidates() {
        m.startScan()
        m.scanAdvance()
        m.stopScan()
        assertEquals(listOf(1, 0), candidatePositions())
        assertEquals(Keys.POWER, (m.state as WizardState.ScanVerify).testKey)
    }

    @Test
    fun scanVerifySkipsCandidatesWithoutPowerAndPrefersPowerOn() {
        m.startScan()
        repeat(5) { m.scanAdvance() }
        m.stopScan()
        // Position 5 (Code-Satz 105) hat weder POWER_ON noch POWER.
        val first = m.state as WizardState.ScanVerify
        assertEquals(1, first.index)
        assertEquals(Keys.POWER, first.testKey)
        m.answer(false)
        assertEquals(Keys.POWER_ON, (m.state as WizardState.ScanVerify).testKey)
        m.answer(false)
        val third = m.state as WizardState.ScanVerify
        assertEquals(3, third.index)
        // Code-Satz 102 hat beides: POWER_ON gewinnt.
        assertEquals(Keys.POWER_ON, third.testKey)
    }

    @Test
    fun scanRunToEndGivesNoMatch() {
        m.startScan()
        repeat(7) { m.scanAdvance() }
        assertEquals(WizardState.NoMatch(null, null), m.state)
        assertNull(m.currentScanSignal())
    }

    @Test
    fun scanVerifyAllNoOffersResume() {
        m.startScan()
        repeat(3) { m.scanAdvance() }
        m.stopScan()
        assertEquals(listOf(3, 2, 1, 0), candidatePositions())
        repeat(4) { m.answer(false) }
        assertEquals(WizardState.NoMatch(null, canResumeScanAt = 4), m.state)
        m.startScan(4)
        assertEquals(WizardState.Scanning(4, 7, "Gamma"), m.state)
    }

    @Test
    fun scanVerifyYesConfirmsWithMuteThenInputs() {
        m.startScan()
        m.scanAdvance()
        m.stopScan()
        m.answer(true)
        assertEquals(WizardState.Confirm(1, 10, Keys.MUTE, fromScan = true), m.state)
        assertEquals(sig(10, Keys.MUTE), m.testSignal())
        m.answer(true)
        assertEquals(WizardState.TestInputs(1, 10, 0, 2), m.state)
        m.answer(true)
        val save = m.state as WizardState.Save
        assertEquals("Alpha", save.brandName)
        assertEquals("Code 1", save.codeLabel)
        assertTrue(save.hasInputs)
        assertEquals(sig(10, "V0-HDMI1"), save.keys[Keys.HDMI1])
    }

    @Test
    fun scanConfirmNoGoesToNextCandidate() {
        m.startScan()
        m.scanAdvance()
        m.stopScan()
        m.answer(true)
        m.answer(false)
        val verify = m.state as WizardState.ScanVerify
        assertEquals(1, verify.index)
        assertEquals(sig(100, Keys.POWER_ON), m.testSignal())
    }

    @Test
    fun scanCodeSetOutsideBrandListIsLabelledSuchlauf() {
        m.startScan()
        m.stopScan()
        m.answer(true)
        assertEquals(WizardState.Save("Gamma", "Suchlauf", data.keys(100), hasInputs = false), m.state)
    }

    @Test
    fun backNavigation() {
        m.pickBrand(1)
        m.answer(false)
        assertEquals(1, (m.state as WizardState.TestCode).index)
        assertTrue(m.back())
        assertEquals(WizardState.TestCode(1, 0, 2, 10, Keys.MUTE), m.state)
        assertTrue(m.back())
        assertEquals(WizardState.PickBrand, m.state)
        assertFalse(m.back())
    }

    @Test
    fun backThroughConfirmInputsAndSave() {
        m.pickBrand(1)
        m.answer(true)
        m.answer(true)
        m.answer(false)
        m.answer(true)
        assertTrue(m.back())
        assertEquals(WizardState.TestInputs(1, 10, 1, 2), m.state)
        assertTrue(m.back())
        assertEquals(WizardState.Confirm(1, 10, Keys.VOL_UP, fromScan = false), m.state)
        assertTrue(m.back())
        assertEquals(WizardState.TestCode(1, 0, 2, 10, Keys.MUTE), m.state)
    }

    @Test
    fun backFromSaveWithoutInputsOrConfirmReturnsToTest() {
        m.pickBrand(1)
        m.answer(false)
        m.answer(true)
        assertTrue(m.back())
        assertEquals(WizardState.TestCode(1, 1, 2, 11, Keys.VOL_DOWN), m.state)
    }

    @Test
    fun backFromScanStatesAndNoMatchGoesToPickBrand() {
        m.startScan()
        assertTrue(m.back())
        assertEquals(WizardState.PickBrand, m.state)
        m.startScan()
        m.stopScan()
        assertTrue(m.back())
        assertEquals(WizardState.PickBrand, m.state)
        m.pickBrand(4)
        assertTrue(m.back())
        assertEquals(WizardState.PickBrand, m.state)
    }

    @Test
    fun backFromScanConfirmReturnsToVerify() {
        m.startScan()
        m.scanAdvance()
        m.stopScan()
        m.answer(true)
        assertTrue(m.back())
        assertEquals(0, (m.state as WizardState.ScanVerify).index)
        assertEquals(listOf(1, 0), candidatePositions())
    }
}
