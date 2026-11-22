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
            is WizardState.TestCode -> if (yes) found(s) else state = testCodeFrom(s.brandId, s.index + 1, step = 1)
            is WizardState.ScanVerify -> if (yes) found(s) else state = verifyFrom(s.candidates, s.index + 1)
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

    private fun found(from: WizardState) {
        origin = from
        confirmed = null
        lastInputs = null
        val (brandId, codeSetId) = foundCodeSet(from)
        val confirmKeys = if (from is WizardState.ScanVerify) listOf(Keys.MUTE, Keys.VOL_UP) else listOf(Keys.VOL_UP)
        val keys = data.keys(codeSetId)
        val confirmKey = confirmKeys.firstOrNull { it in keys }
        if (confirmKey != null) {
            state = WizardState.Confirm(brandId, codeSetId, confirmKey, fromScan = from is WizardState.ScanVerify)
        } else {
            enterInputs(brandId, codeSetId)
        }
    }

    private fun foundCodeSet(from: WizardState?): Pair<Long, Long> = when (from) {
        is WizardState.TestCode -> from.brandId to from.codeSetId
        is WizardState.ScanVerify -> from.candidates[from.index].let { it.brandId to it.codeSetId }
        else -> error("Kein Test-Zustand: $from")
    }

    /** "Nein" im Bestätigungsschritt wirkt wie "Nein" im Test-Zustand davor. */
    private fun rejectOrigin() {
        val from = origin
        state = from ?: WizardState.PickBrand
        if (from != null) answer(false)
    }

    private fun backToOrigin(): WizardState {
        confirmed = null
        lastInputs = null
        return origin ?: WizardState.PickBrand
    }

    private fun enterInputs(brandId: Long, codeSetId: Long) {
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
        val from = origin
        val (brandId, codeSetId) = foundCodeSet(from)
        var keys = data.keys(codeSetId)
        if (variantIndex != null) keys = keys + variants[variantIndex]
        val (brandName, codeLabel) = when (from) {
            is WizardState.TestCode -> data.brandName(brandId) to "Code ${from.index + 1}"
            is WizardState.ScanVerify -> {
                val entry = from.candidates[from.index]
                val pos = data.codeSetsForBrand(entry.brandId).indexOf(entry.codeSetId)
                entry.brandName to (if (pos >= 0) "Code ${pos + 1}" else "Suchlauf")
            }
            else -> error("Kein Test-Zustand: $from")
        }
        return WizardState.Save(brandName, codeLabel, keys, hasInputs = variantIndex != null)
    }
}
