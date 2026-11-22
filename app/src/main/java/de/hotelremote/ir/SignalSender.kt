package de.hotelremote.ir

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Sendet IR-Signale strikt nacheinander. Der Toggle-Zustand (frame/frameAlt) wird pro Signal
 * (Schlüssel: frame) bei jedem press/hold-Start gewechselt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignalSender(
    private val tx: IrTransmitter,
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val onError: (Throwable) -> Unit,
) {
    private val serial = io.limitedParallelism(1)
    private val nextIsAlt = HashMap<List<Int>, Boolean>()

    fun press(signal: IrSignal) {
        val pattern = nextFrame(signal)
        scope.launch(serial) { transmit(signal.freq, pattern)?.let(onError) }
    }

    /**
     * Gedrückt halten: Rahmen sofort, dann Wiederholungen. Mit eigenem Wiederholungs-Burst (NEC-Familie)
     * folgen die Bursts direkt im Signaltakt; ohne wird der Rahmen erst nach [HOLD_DELAY_MS] wiederholt.
     * ATOMIC: auch ein sofort wieder losgelassener Tipp sendet den ersten Rahmen, wenn die Warteschlange belegt war.
     * Fehler werden pro Haltevorgang nur einmal gemeldet (z. B. ohne IR-Sender nicht bei jeder Wiederholung).
     */
    fun hold(signal: IrSignal): Job {
        val frame = nextFrame(signal)
        val repeat = signal.repeat ?: frame
        return scope.launch(serial, start = CoroutineStart.ATOMIC) {
            var reported = false
            fun sendOnce(pattern: List<Int>) {
                val error = transmit(signal.freq, pattern) ?: return
                if (!reported) {
                    reported = true
                    onError(error)
                }
            }
            var start = TimeSource.Monotonic.markNow()
            sendOnce(frame)
            if (signal.repeat == null) {
                delay(HOLD_DELAY_MS)
                start = TimeSource.Monotonic.markNow()
                sendOnce(repeat)
            }
            while (true) {
                delay((signal.periodMs - start.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
                start = TimeSource.Monotonic.markNow()
                sendOnce(repeat)
            }
        }
    }

    private fun nextFrame(signal: IrSignal): List<Int> {
        val alt = signal.frameAlt ?: return signal.frame
        synchronized(nextIsAlt) {
            val useAlt = nextIsAlt[signal.frame] ?: false
            nextIsAlt[signal.frame] = !useAlt
            return if (useAlt) alt else signal.frame
        }
    }

    // Läuft immer auf `serial`; bei Hold gibt delay() den Dispatcher-Slot zwischen Wiederholungen frei.
    /** Sendet; liefert den Fehler statt ihn zu werfen (null bei Erfolg). */
    private fun transmit(freq: Int, pattern: List<Int>): Exception? =
        try {
            tx.transmit(freq, pattern.toIntArray())
            null
        } catch (e: Exception) {
            e
        }

    companion object {
        const val HOLD_DELAY_MS = 400L
    }
}
