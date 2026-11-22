package de.hotelremote.automation

import de.hotelremote.data.AutomationStep
import de.hotelremote.ir.IrSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Laufende Automation: Schritt [step] (ab 1) von [total]. */
data class AutomationProgress(val name: String, val step: Int, val total: Int)

/** Ergebnis eines Laufs: [missing] sind Tasten, die diese Fernbedienung nicht hat (übersprungen). */
data class AutomationResult(val name: String, val missing: List<String>, val cancelled: Boolean)

/** Gesamte Wartezeit einer Folge; nach dem letzten Schritt wird nicht gewartet. */
fun automationDurationMs(steps: List<AutomationStep>): Long = steps.dropLast(1).sumOf { it.delayAfterMs }

/**
 * Sendet die Schritte nacheinander und wartet nach jedem `delayAfterMs`, nach dem letzten gesendeten nicht.
 * Fehlt eine Taste in [keys], wird der Schritt samt Wartezeit übersprungen. [onStep] bekommt den Index
 * des Schritts in [steps] vor dem Senden. Liefert die fehlenden Tasten.
 */
suspend fun runSteps(
    keys: Map<String, IrSignal>,
    steps: List<AutomationStep>,
    send: (IrSignal) -> Unit,
    onStep: (Int) -> Unit = {},
): List<String> {
    val missing = steps.filter { it.key !in keys }.map { it.key }.distinct()
    val sendable = steps.withIndex().filter { it.value.key in keys }
    sendable.forEachIndexed { n, (index, step) ->
        onStep(index)
        send(keys.getValue(step.key))
        if (n < sendable.lastIndex) delay(step.delayAfterMs)
    }
    return missing
}

/** Führt immer höchstens eine Automation aus; Fortschritt als [progress], abbrechbar. Ohne Android-Abhängigkeiten. */
class AutomationRunner(
    private val scope: CoroutineScope,
    private val send: (IrSignal) -> Unit,
    private val onFinished: (AutomationResult) -> Unit = {},
) {
    private val _progress = MutableStateFlow<AutomationProgress?>(null)
    val progress: StateFlow<AutomationProgress?> = _progress.asStateFlow()
    private var job: Job? = null

    /**
     * Startet die Folge; false, wenn schon eine läuft oder sie leer ist. Ein abgebrochener, aber noch nicht
     * beendeter Lauf zählt noch als laufend, damit sein Abschluss nicht den Fortschritt des neuen löscht.
     */
    fun start(name: String, keys: Map<String, IrSignal>, steps: List<AutomationStep>): Boolean {
        if (job?.isCompleted == false || steps.isEmpty()) return false
        _progress.value = AutomationProgress(name, 1, steps.size)
        var missing: List<String>? = null
        val started = scope.launch {
            missing = runSteps(keys, steps, send) { i -> _progress.value = AutomationProgress(name, i + 1, steps.size) }
        }
        job = started
        // Auch bei Abbruch vor dem ersten Schritt: Fortschritt zurücksetzen und Ergebnis melden.
        started.invokeOnCompletion {
            _progress.value = null
            val done = missing
            onFinished(AutomationResult(name, done.orEmpty(), cancelled = done == null))
        }
        return true
    }

    fun cancel() {
        job?.cancel()
    }
}
