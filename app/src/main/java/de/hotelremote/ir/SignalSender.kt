package de.hotelremote.ir

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
        scope.launch(serial) { send(signal.freq, pattern) }
    }

    fun hold(signal: IrSignal): Job {
        val frame = nextFrame(signal)
        val repeat = signal.repeat ?: frame
        return scope.launch(serial) {
            send(signal.freq, frame)
            delay(HOLD_DELAY_MS)
            while (true) {
                val start = TimeSource.Monotonic.markNow()
                send(signal.freq, repeat)
                delay((signal.periodMs - start.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
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
    private fun send(freq: Int, pattern: List<Int>) {
        try {
            tx.transmit(freq, pattern.toIntArray())
        } catch (e: Exception) {
            onError(e)
        }
    }

    companion object {
        const val HOLD_DELAY_MS = 400L
    }
}
