package de.hotelremote.ui

import de.hotelremote.data.AcCodes
import de.hotelremote.data.AcState
import de.hotelremote.data.SavedAc
import de.hotelremote.ir.IrSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bedienschritte am Klima-Bildschirm. */
sealed interface AcAction {
    data object Power : AcAction
    data object Warmer : AcAction
    data object Cooler : AcAction
    data class SetMode(val mode: String) : AcAction
    data object CycleFan : AcAction
    data object CycleSwing : AcAction
}

/** Was der Klima-Bildschirm zeigt und anbietet; [state] ist auf vorhandene Kombinationen gerastet. */
data class AcView(
    val state: AcState,
    val fahrenheit: Boolean,
    val modes: List<String>,
    val fans: List<String>,
    /** Leer bei Geräten ohne Swing. */
    val swings: List<String>,
    val canWarmer: Boolean,
    val canCooler: Boolean,
)

/**
 * Regeln für Klima-Zustände: nur Kombinationen, die es in [AcCodes] gibt (Lüfter und Swing je Modus,
 * Temperaturen je Modus/Lüfter/Swing). Keine stille Ersatzwahl beim Senden – stattdessen rastet der Zustand.
 */
internal object AcRules {
    /** Nächste vorhandene Kombination: gleicher Name bevorzugt, sonst ohne Groß-/Kleinschreibung, sonst der erste. */
    fun normalize(codes: AcCodes, s: AcState): AcState {
        val mode = pick(codes.modes(), s.mode) ?: return s
        val fan = pick(codes.fans(mode), s.fan) ?: return s
        val swing = if (codes.hasSwing) pick(codes.swings(mode, fan), s.swing) ?: return s else null
        val temp = codes.temps(mode, fan, swing).minByOrNull { Math.abs(it - s.temp) } ?: return s
        return AcState(s.power, mode, temp, fan, swing)
    }

    fun apply(codes: AcCodes, state: AcState, action: AcAction): AcState {
        val s = normalize(codes, state)
        return when (action) {
            AcAction.Power -> s.copy(power = !s.power)
            AcAction.Warmer -> temps(codes, s).firstOrNull { it > s.temp }?.let { s.copy(temp = it) } ?: s
            AcAction.Cooler -> temps(codes, s).lastOrNull { it < s.temp }?.let { s.copy(temp = it) } ?: s
            is AcAction.SetMode -> normalize(codes, s.copy(mode = action.mode))
            AcAction.CycleFan -> normalize(codes, s.copy(fan = next(codes.fans(s.mode), s.fan)))
            AcAction.CycleSwing ->
                if (!codes.hasSwing) s else normalize(codes, s.copy(swing = next(codes.swings(s.mode, s.fan), s.swing)))
        }
    }

    fun view(codes: AcCodes, ac: SavedAc): AcView {
        val s = normalize(codes, ac.state)
        val temps = temps(codes, s)
        return AcView(
            state = s,
            fahrenheit = ac.fahrenheit,
            modes = codes.modes(),
            fans = codes.fans(s.mode),
            swings = if (codes.hasSwing) codes.swings(s.mode, s.fan) else emptyList(),
            canWarmer = temps.any { it > s.temp },
            canCooler = temps.any { it < s.temp },
        )
    }

    private fun temps(codes: AcCodes, s: AcState) = codes.temps(s.mode, s.fan, s.swing)

    private fun pick(options: List<String>, wanted: String?): String? =
        options.firstOrNull { it == wanted } ?: options.firstOrNull { it.equals(wanted, ignoreCase = true) } ?: options.firstOrNull()

    private fun <T> next(options: List<T>, current: T): T {
        if (options.isEmpty()) return current
        return options[(options.indexOf(current) + 1) % options.size]
    }
}

/**
 * Bedient die Klimaanlage des aktuellen Raums. Jede Änderung meldet den neuen Zustand an [onChange]
 * (Speichern und Anzeige). Gesendet wird immer der vollständige Zustand: Änderungen an Temperatur, Modus,
 * Lüfter und Swing entprellt (der letzte Zustand nach [debounceMs]), Ein/Aus sofort. Ausgeschaltet werden
 * Änderungen nur gemerkt; Einschalten sendet den gemerkten Zustand, Ausschalten `off`.
 */
class AcController(
    private val scope: CoroutineScope,
    private val send: (IrSignal) -> Unit,
    private val onChange: (roomId: String, state: AcState) -> Unit,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    private var cached: Pair<String, AcCodes?>? = null
    private val pending = HashMap<String, Job>()

    /** Dekodiert die Kommandos einmal je Klimaanlage; null, wenn die gespeicherte Kopie unlesbar ist. */
    private fun codes(ac: SavedAc): AcCodes? {
        cached?.let { (commands, codes) -> if (commands == ac.commands) return codes }
        val codes = try {
            ac.decodeCodes()
        } catch (e: Exception) {
            null
        }
        cached = ac.commands to codes
        return codes
    }

    fun view(ac: SavedAc): AcView? = codes(ac)?.let { AcRules.view(it, ac) }

    fun perform(roomId: String, ac: SavedAc, action: AcAction) {
        val codes = codes(ac) ?: return
        val new = AcRules.apply(codes, ac.state, action)
        if (new == ac.state) return
        onChange(roomId, new)
        when {
            action == AcAction.Power -> {
                pending.remove(roomId)?.cancel()
                transmit(codes, new)
            }
            new.power -> {
                pending.remove(roomId)?.cancel()
                pending[roomId] = scope.launch {
                    delay(debounceMs)
                    pending.remove(roomId)
                    transmit(codes, new)
                }
            }
        }
    }

    private fun transmit(codes: AcCodes, s: AcState) {
        codes.signalFor(s.power, s.mode, s.fan, s.swing, s.temp)?.let(send)
    }

    companion object {
        const val DEBOUNCE_MS = 300L
    }
}
