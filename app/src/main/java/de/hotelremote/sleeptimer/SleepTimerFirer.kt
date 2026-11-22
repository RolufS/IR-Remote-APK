package de.hotelremote.sleeptimer

import de.hotelremote.automation.automationDurationMs
import de.hotelremote.automation.runSteps
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.RemoteStorage
import de.hotelremote.ir.IrSignal
import de.hotelremote.ir.IrTransmitter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

enum class FireResult { DONE, NO_TIMER, REMOTE_MISSING, ACTION_MISSING, SEND_FAILED, INCOMPLETE }

/**
 * Längste Wartezeit einer Automation für den Sleep-Timer. Der Alarm-Receiver läuft mit `goAsync()` und muss
 * nach etwa 10 s fertig sein; längere Folgen bietet die App für den Timer nicht an.
 */
const val MAX_TIMER_AUTOMATION_MS = 8_000L

/** Passt die Folge in die Zeit, die der Alarm-Receiver hat? */
fun fitsSleepTimer(steps: List<AutomationStep>) = automationDurationMs(steps) <= MAX_TIMER_AUTOMATION_MS

/** Führt die Aktion eines ausgelösten Timers aus: Fernbedienung laden, Signale direkt über den IR-Sender senden. */
class SleepTimerFirer(
    private val scheduler: SleepTimerScheduler,
    private val remotes: RemoteStorage,
    private val tx: IrTransmitter,
) {
    suspend fun fire(): FireResult {
        val timer = scheduler.takeDue() ?: return FireResult.NO_TIMER
        val remote = try {
            remotes.all().firstOrNull { it.id == timer.remoteId }
        } catch (e: IOException) {
            null
        } ?: return FireResult.REMOTE_MISSING
        return try {
            when (val action = timer.action) {
                SleepAction.PowerOff -> {
                    val key = powerOffKey(remote.keys.keys) ?: return FireResult.ACTION_MISSING
                    send(remote.keys.getValue(key))
                    FireResult.DONE
                }
                is SleepAction.RunAutomation -> {
                    val automation = remote.automations.firstOrNull { it.id == action.automationId }
                        ?: return FireResult.ACTION_MISSING
                    if (automation.steps.none { it.key in remote.keys }) return FireResult.ACTION_MISSING
                    // Sicherheitsgrenze, falls die Folge nach dem Planen verlängert wurde.
                    withTimeout(MAX_TIMER_AUTOMATION_MS + 500) { runSteps(remote.keys, automation.steps, ::send) }
                    FireResult.DONE
                }
            }
        } catch (e: TimeoutCancellationException) {
            FireResult.INCOMPLETE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FireResult.SEND_FAILED
        }
    }

    private fun send(signal: IrSignal) = tx.transmit(signal.freq, signal.frame.toIntArray())
}
