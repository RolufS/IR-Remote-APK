package de.hotelremote.wizard

import de.hotelremote.data.AcCodes
import de.hotelremote.data.AcDevice
import de.hotelremote.data.AcState
import de.hotelremote.data.SavedAc
import de.hotelremote.data.acTempsInFahrenheit
import de.hotelremote.ir.IrSignal
import de.hotelremote.ui.AcRules

/** Datenquelle des Klima-Assistenten (echte Quelle: `AcCodeDatabase`). */
interface AcWizardData {
    /** Geräte in Testreihenfolge; [brandId] null = alle Marken (häufige zuerst). */
    fun deviceIds(brandId: Long?): List<Long>
    fun device(id: Long): AcDevice?
}

sealed interface AcWizardState {
    data object PickBrand : AcWizardState

    /**
     * Gerät [index] von [total] der Marke [brandId] (null = Marke unbekannt) wird mit [testState] getestet.
     * [brandName] ist die Marke des Geräts (bei „Marke unbekannt“ wechselnd).
     */
    data class TestCode(
        val brandId: Long?,
        val index: Int,
        val total: Int,
        val deviceId: Long,
        val brandName: String,
        val testState: AcState,
        val fahrenheit: Boolean,
    ) : AcWizardState

    /** Das Gerät hat reagiert; zur Bestätigung wird „Aus“ gesendet. */
    data class ConfirmOff(val brandId: Long?, val index: Int, val total: Int, val deviceId: Long) : AcWizardState

    /** Gefunden: [ac] mit eigener Kopie der Kommandos; Startzustand = Test-Zustand, ausgeschaltet. */
    data class Save(val ac: SavedAc) : AcWizardState

    /** Ende der Geräteliste ohne Treffer. */
    data class NoMatch(val brandId: Long?) : AcWizardState
}

/**
 * Klima-Assistent als reiner Zustandsautomat: Marke wählen, je Gerät den Test-Zustand senden
 * („Ein · Kühlen · 24 °C · Lüfter Auto“ bzw. die nächstliegende vorhandene Kombination), bei Reaktion „Aus“
 * zur Bestätigung senden, dann speichern. Geräte ohne Test- oder Aus-Signal werden übersprungen.
 */
class AcWizardMachine(private val data: AcWizardData) {
    var state: AcWizardState = AcWizardState.PickBrand
        private set

    private var ids: List<Long> = emptyList()

    /** Zuletzt geladenes Gerät mit dekodierten Kommandos. */
    private var loaded: Triple<Long, AcDevice, AcCodes>? = null

    /** Bestätigungsschritt, aus dem gespeichert wurde (für Zurück vom Speichern). */
    private var confirmed: AcWizardState.ConfirmOff? = null

    /** [brandId] null = Marke unbekannt: alle Geräte. */
    fun pickBrand(brandId: Long?) {
        ids = data.deviceIds(brandId)
        state = testFrom(brandId, 0, step = 1)
    }

    fun answer(yes: Boolean) {
        when (val s = state) {
            is AcWizardState.TestCode ->
                state = if (yes) AcWizardState.ConfirmOff(s.brandId, s.index, s.total, s.deviceId) else next(s.brandId, s.index)
            is AcWizardState.ConfirmOff -> state = if (yes) save(s) else next(s.brandId, s.index)
            else -> Unit
        }
    }

    /** Signal des aktuellen Schritts: Test-Zustand bzw. „Aus“. */
    fun testSignal(): IrSignal? = when (val s = state) {
        is AcWizardState.TestCode -> load(s.deviceId)?.let { (_, _, codes) ->
            codes.signalFor(true, s.testState.mode, s.testState.fan, s.testState.swing, s.testState.temp)
        }
        is AcWizardState.ConfirmOff -> load(s.deviceId)?.third?.off
        else -> null
    }

    /** Ein Schritt zurück; false auf der Markenwahl. */
    fun back(): Boolean {
        state = when (val s = state) {
            AcWizardState.PickBrand -> return false
            is AcWizardState.TestCode -> if (s.index > 0) testFrom(s.brandId, s.index - 1, step = -1) else AcWizardState.PickBrand
            is AcWizardState.ConfirmOff -> testFrom(s.brandId, s.index, step = -1)
            is AcWizardState.Save -> confirmed ?: AcWizardState.PickBrand
            is AcWizardState.NoMatch -> AcWizardState.PickBrand
        }
        return true
    }

    private fun next(brandId: Long?, index: Int) = testFrom(brandId, index + 1, step = 1)

    /** Erstes testbares Gerät ab [start] in Richtung [step]. */
    private fun testFrom(brandId: Long?, start: Int, step: Int): AcWizardState {
        var i = start
        while (i in ids.indices) {
            val entry = load(ids[i])
            val test = entry?.let { testState(it.third, it.second.maxTemp) }
            if (entry != null && test != null && entry.third.off != null) {
                val device = entry.second
                return AcWizardState.TestCode(
                    brandId, i, ids.size, device.id, device.brandName, test, acTempsInFahrenheit(device.maxTemp),
                )
            }
            i += step
        }
        return if (step > 0) AcWizardState.NoMatch(brandId) else AcWizardState.PickBrand
    }

    private fun load(id: Long): Triple<Long, AcDevice, AcCodes>? {
        loaded?.takeIf { it.first == id }?.let { return it }
        val device = data.device(id) ?: return null
        val codes = try {
            device.codes
        } catch (e: Exception) {
            return null
        }
        return Triple(id, device, codes).also { loaded = it }
    }

    private fun save(s: AcWizardState.ConfirmOff): AcWizardState {
        val (_, device, codes) = load(s.deviceId) ?: return next(s.brandId, s.index)
        val test = testState(codes, device.maxTemp) ?: return next(s.brandId, s.index)
        confirmed = s
        val model = device.models.firstOrNull { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        return AcWizardState.Save(
            SavedAc(
                brand = device.brandName,
                deviceLabel = model ?: "Code ${s.index + 1}",
                minTemp = device.minTemp,
                maxTemp = device.maxTemp,
                precision = device.precision,
                modes = device.modes,
                fanModes = device.fanModes,
                swingModes = device.swingModes,
                commands = SavedAc.encodeCommands(device.commands),
                state = test.copy(power = false),
            ),
        )
    }

    companion object {
        const val TEST_TEMP_C = 24.0

        /** 24 °C entsprechen etwa 75 °F. */
        const val TEST_TEMP_F = 75.0

        /**
         * „Ein · Kühlen · 24 °C · Lüfter Auto“, auf vorhandene Kombinationen gerastet (fehlt Kühlen: erster Modus;
         * Lüfter Auto, sonst der erste; 24 °C bzw. die nächste vorhandene Temperatur). null ohne Kombination.
         */
        fun testState(codes: AcCodes, maxTemp: Double): AcState? {
            val temp = if (acTempsInFahrenheit(maxTemp)) TEST_TEMP_F else TEST_TEMP_C
            val s = AcRules.normalize(codes, AcState(power = true, mode = "cool", temp = temp, fan = "auto"))
            return s.takeIf { codes.signalFor(true, it.mode, it.fan, it.swing, it.temp) != null }
        }
    }
}
