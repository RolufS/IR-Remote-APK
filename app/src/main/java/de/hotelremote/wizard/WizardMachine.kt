package de.hotelremote.wizard

import de.hotelremote.Keys
import de.hotelremote.data.ScanEntry
import de.hotelremote.ir.IrSignal

sealed interface WizardState {
    data object PickBrand : WizardState
    data class TestCode(val brandId: Long, val index: Int, val total: Int, val codeSetId: Long, val testKey: String) : WizardState
    data class Confirm(val brandId: Long, val codeSetId: Long, val confirmKey: String, val fromScan: Boolean) : WizardState
    data class Scanning(val position: Int, val total: Int, val brandLabel: String) : WizardState
    data class ScanVerify(val candidates: List<ScanEntry>, val index: Int, val testKey: String) : WizardState
    data class TestInputs(val brandId: Long, val codeSetId: Long, val variantIndex: Int, val variantCount: Int) : WizardState
    data class Save(val brandName: String, val codeLabel: String, val keys: Map<String, IrSignal>, val hasInputs: Boolean) : WizardState
    data class NoMatch(val brandId: Long?, val canResumeScanAt: Int?) : WizardState
}

/** Einrichtungsassistent als reiner Zustandsautomat, ohne Android-Abhängigkeiten. */
class WizardMachine(private val data: WizardData) {
    var state: WizardState = WizardState.PickBrand
        private set

    /** Suchlauf-Liste, einmal pro Assistent geladen (die echte Quelle liest die ganze Tabelle). */
    private val scan: List<ScanEntry> by lazy { data.scanEntries() }

    /** Eingangs-Varianten des gefundenen Code-Satzes, einmal beim Eintritt in den Eingangstest geladen. */
    private var variants: List<Map<String, IrSignal>> = emptyList()

    /** TV-aus-Modus: gilt für die restliche Code-Folge der gewählten Marke. */
    private var tvOffMode = false

    /** Position, an der der Suchlauf gestoppt wurde. */
    private var scanStopPosition = 0

    /** Test-Zustand (TestCode oder ScanVerify), aus dem der gefundene Code-Satz stammt. */
    private var origin: WizardState? = null
    private var confirmed: WizardState.Confirm? = null

    /** Gefundener Code-Satz (Marke, Code-Satz), der gespeichert wird. */
    private var found: Pair<Long, Long>? = null

    /** Suchlauf-Treffer: Kandidat plus Code-Sätze mit gleichem POWER-Code, nacheinander bestätigt. */
    private var scanGroup: List<Long> = emptyList()
    private var scanGroupIndex = 0
    private var lastInputs: WizardState.TestInputs? = null

    fun pickBrand(brandId: Long) {
        tvOffMode = false
        state = testCodeFrom(brandId, 0, step = 1)
    }

    fun useTvOffMode() {
        val s = state as? WizardState.TestCode ?: return
        tvOffMode = true
        state = testCodeFrom(s.brandId, s.index, step = 1)
    }

    fun answer(yes: Boolean) {
        when (val s = state) {
            is WizardState.TestCode -> if (yes) onFound(s) else state = testCodeFrom(s.brandId, s.index + 1, step = 1)
            is WizardState.ScanVerify -> if (yes) onFound(s) else state = verifyFrom(s.candidates, s.index + 1)
            is WizardState.Confirm -> if (yes) {
                confirmed = s
                enterInputs(s.brandId, s.codeSetId)
            } else {
                rejectOrigin()
            }
            is WizardState.TestInputs -> when {
                yes -> state = save(s.variantIndex)
                s.variantIndex + 1 < s.variantCount -> showInputs(s.copy(variantIndex = s.variantIndex + 1))
                else -> state = save(null)
            }
            else -> Unit
        }
    }

    fun startScan(fromPosition: Int = 0) {
        state = if (fromPosition in scan.indices) {
            WizardState.Scanning(fromPosition, scan.size, scan[fromPosition].brandName)
        } else {
            WizardState.NoMatch(null, null)
        }
    }

    fun currentScanSignal(): IrSignal? {
        val s = state as? WizardState.Scanning ?: return null
        return scan.getOrNull(s.position)?.signal
    }

    fun scanAdvance() {
        val s = state as? WizardState.Scanning ?: return
        startScan(s.position + 1)
    }

    fun stopScan() {
        val s = state as? WizardState.Scanning ?: return
        scanStopPosition = s.position
        val candidates = (s.position downTo maxOf(0, s.position - 4)).map { scan[it] }
        state = verifyFrom(candidates, 0)
    }

    fun testSignal(): IrSignal? = when (val s = state) {
        is WizardState.TestCode -> data.keys(s.codeSetId)[s.testKey]
        is WizardState.Confirm -> data.keys(s.codeSetId)[s.confirmKey]
        is WizardState.ScanVerify -> data.keys(s.candidates[s.index].codeSetId)[s.testKey]
        is WizardState.TestInputs -> variants[s.variantIndex][Keys.HDMI1]
        is WizardState.Scanning -> currentScanSignal()
        else -> null
    }

    fun back(): Boolean {
        state = when (val s = state) {
            WizardState.PickBrand -> return false
            is WizardState.TestCode ->
                if (s.index > 0) testCodeFrom(s.brandId, s.index - 1, step = -1) else WizardState.PickBrand
            is WizardState.Confirm -> backToOrigin()
            is WizardState.TestInputs -> confirmed?.also { lastInputs = null } ?: backToOrigin()
            is WizardState.Save -> lastInputs ?: confirmed ?: backToOrigin()
            is WizardState.Scanning, is WizardState.ScanVerify, is WizardState.NoMatch -> WizardState.PickBrand
        }
        return true
    }

    private fun testKeyCandidates() =
        if (tvOffMode) listOf(Keys.POWER, Keys.POWER_ON)
        else listOf(Keys.MUTE, Keys.VOL_DOWN, Keys.POWER, Keys.POWER_ON)

    /** Erster testbarer Code-Satz ab [start] in Richtung [step]; überspringt Code-Sätze ohne Testtaste. */
    private fun testCodeFrom(brandId: Long, start: Int, step: Int): WizardState {
        val codeSets = data.codeSetsForBrand(brandId)
        var i = start
        while (i in codeSets.indices) {
            val keys = data.keys(codeSets[i])
            val testKey = testKeyCandidates().firstOrNull { it in keys }
            if (testKey != null) return WizardState.TestCode(brandId, i, codeSets.size, codeSets[i], testKey)
            i += step
        }
        return if (step > 0) WizardState.NoMatch(brandId, null) else WizardState.PickBrand
    }

    /** Erster prüfbarer Kandidat ab [start]; überspringt Kandidaten ohne POWER_ON und POWER. */
    private fun verifyFrom(candidates: List<ScanEntry>, start: Int): WizardState {
        for (i in start until candidates.size) {
            val keys = data.keys(candidates[i].codeSetId)
            val testKey = listOf(Keys.POWER_ON, Keys.POWER).firstOrNull { it in keys }
            if (testKey != null) return WizardState.ScanVerify(candidates, i, testKey)
        }
        val resumeAt = scanStopPosition + 1
        return WizardState.NoMatch(null, if (resumeAt < scan.size) resumeAt else null)
    }

    private fun onFound(from: WizardState) {
        origin = from
        confirmed = null
        lastInputs = null
        when (from) {
            is WizardState.TestCode -> offer(from.brandId, from.codeSetId, fromScan = false)
            is WizardState.ScanVerify -> {
                val entry = from.candidates[from.index]
                scanGroup = listOf(entry.codeSetId) + data.powerSiblings(entry.codeSetId).filter { it != entry.codeSetId }
                offerScanGroup(0)
            }
            else -> error("Kein Test-Zustand: $from")
        }
    }

    /** Bietet das [index]-te Mitglied der Suchlauf-Gruppe zur Bestätigung an (Geschwister unter ihrer Hauptmarke). */
    private fun offerScanGroup(index: Int) {
        val entry = (origin as WizardState.ScanVerify).let { it.candidates[it.index] }
        scanGroupIndex = index
        val codeSetId = scanGroup[index]
        val brandId = if (index == 0) entry.brandId else data.topBrand(codeSetId) ?: entry.brandId
        offer(brandId, codeSetId, fromScan = true)
    }

    /** Bestätigungsschritt, falls der Code-Satz eine Bestätigungstaste hat; sonst gilt er als angenommen. */
    private fun offer(brandId: Long, codeSetId: Long, fromScan: Boolean) {
        val confirmKeys = if (fromScan) listOf(Keys.MUTE, Keys.VOL_UP) else listOf(Keys.VOL_UP)
        val keys = data.keys(codeSetId)
        val confirmKey = confirmKeys.firstOrNull { it in keys }
        if (confirmKey != null) {
            state = WizardState.Confirm(brandId, codeSetId, confirmKey, fromScan)
        } else {
            enterInputs(brandId, codeSetId)
        }
    }

    /**
     * "Nein" im Bestätigungsschritt: beim Suchlauf zuerst die Geschwister mit gleichem POWER-Code,
     * danach wie "Nein" im Test-Zustand davor.
     */
    private fun rejectOrigin() {
        val from = origin
        if (from is WizardState.ScanVerify && scanGroupIndex + 1 < scanGroup.size) {
            offerScanGroup(scanGroupIndex + 1)
            return
        }
        state = from ?: WizardState.PickBrand
        if (from != null) answer(false)
    }

    private fun backToOrigin(): WizardState {
        confirmed = null
        lastInputs = null
        return origin ?: WizardState.PickBrand
    }

    private fun enterInputs(brandId: Long, codeSetId: Long) {
        found = brandId to codeSetId
        variants = data.inputVariants(codeSetId, brandId)
        if (variants.isEmpty()) {
            state = save(null)
        } else {
            showInputs(WizardState.TestInputs(brandId, codeSetId, 0, variants.size))
        }
    }

    private fun showInputs(s: WizardState.TestInputs) {
        lastInputs = s
        state = s
    }

    private fun save(variantIndex: Int?): WizardState.Save {
        val (brandId, codeSetId) = found ?: error("Kein Code-Satz gefunden")
        var keys = data.keys(codeSetId)
        if (variantIndex != null) keys = keys + variants[variantIndex]
        val (brandName, codeLabel) = when (val from = origin) {
            is WizardState.TestCode -> data.brandName(brandId) to "Code ${from.index + 1}"
            is WizardState.ScanVerify -> {
                val entry = from.candidates[from.index]
                val name = if (codeSetId == entry.codeSetId) entry.brandName else data.brandName(brandId)
                val pos = data.codeSetsForBrand(brandId).indexOf(codeSetId)
                name to (if (pos >= 0) "Code ${pos + 1}" else "Suchlauf")
            }
            else -> error("Kein Test-Zustand: $from")
        }
        return WizardState.Save(brandName, codeLabel, keys, hasInputs = variantIndex != null)
    }
}
