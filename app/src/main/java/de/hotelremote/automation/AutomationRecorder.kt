package de.hotelremote.automation

import de.hotelremote.data.AutomationStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToLong

/**
 * Nimmt eine Tastenfolge auf: jede gedrückte Taste wird ein Schritt, die Zeit bis zum nächsten Druck
 * wird zu dessen Wartezeit (auf 0,5 s gerundet, begrenzt auf 0,5–10 s); der letzte Schritt behält die
 * Standard-Wartezeit. Vorhandene Schritte ([start]) bleiben unverändert, auch beim Rückgängigmachen.
 */
class AutomationRecorder(private val clock: () -> Long) {
    private val _steps = MutableStateFlow<List<AutomationStep>>(emptyList())

    /** Vorhandene und aufgenommene Schritte. */
    val steps: StateFlow<List<AutomationStep>> = _steps.asStateFlow()

    var isRecording = false
        private set

    /** Zeitpunkte der aufgenommenen Tastendrücke (parallel zu den Schritten nach den vorhandenen). */
    private val pressedAt = ArrayList<Long>()

    /** Anzahl der in dieser Aufnahme hinzugekommenen Schritte. */
    val recordedCount: Int get() = pressedAt.size

    fun start(existing: List<AutomationStep>) {
        pressedAt.clear()
        _steps.value = existing
        isRecording = true
    }

    fun record(key: String) {
        if (!isRecording) return
        val now = clock()
        val list = _steps.value.toMutableList()
        pressedAt.lastOrNull()?.let { previous ->
            list[list.lastIndex] = list.last().copy(delayAfterMs = pauseToDelay(now - previous))
        }
        list += AutomationStep(key)
        pressedAt += now
        _steps.value = list
    }

    /** Entfernt den letzten aufgenommenen Schritt; der davor wird wieder letzter (Standard-Wartezeit). */
    fun undo() {
        if (!isRecording || pressedAt.isEmpty()) return
        pressedAt.removeAt(pressedAt.lastIndex)
        val list = _steps.value.dropLast(1).toMutableList()
        if (pressedAt.isNotEmpty()) {
            list[list.lastIndex] = list.last().copy(delayAfterMs = AutomationStep.DEFAULT_DELAY_MS)
        }
        _steps.value = list
    }

    /** Beendet die Aufnahme und liefert alle Schritte. */
    fun finish(): List<AutomationStep> {
        val result = _steps.value
        cancel()
        return result
    }

    fun cancel() {
        isRecording = false
        pressedAt.clear()
        _steps.value = emptyList()
    }

    private fun pauseToDelay(ms: Long): Long =
        ((ms.toDouble() / AutomationStep.DELAY_STEP_MS).roundToLong() * AutomationStep.DELAY_STEP_MS)
            .coerceIn(AutomationStep.MIN_DELAY_MS, AutomationStep.MAX_DELAY_MS)
}
