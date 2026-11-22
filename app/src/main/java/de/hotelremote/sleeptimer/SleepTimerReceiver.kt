package de.hotelremote.sleeptimer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.hotelremote.data.RemoteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarm ausgelöst (ACTION_FIRE) oder „Abbrechen“ in der Benachrichtigung (ACTION_CANCEL).
 * Beim Auslösen sendet der Receiver selbst, ohne Activity: `goAsync()` und eine Coroutine; die Aktion
 * dauert höchstens [MAX_TIMER_AUTOMATION_MS] plus Sendezeit und bleibt damit unter der Grenze von 10 s.
 */
class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CANCEL -> SleepTimerGraph.scheduler(context).cancel()
            ACTION_FIRE -> {
                // Ohne Broadcast-Kontext (direkter Aufruf) liefert goAsync() null.
                val pending: PendingResult? = goAsync()
                val app = context.applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        fire(app)
                    } finally {
                        pending?.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "de.hotelremote.action.SLEEP_TIMER_FIRE"
        const val ACTION_CANCEL = "de.hotelremote.action.SLEEP_TIMER_CANCEL"

        /** Sendet die Aktion des fälligen Timers und zeigt das Ergebnis an. */
        internal suspend fun fire(context: Context): FireResult {
            val firer = SleepTimerFirer(
                SleepTimerGraph.scheduler(context),
                RemoteStore(remotesFile(context)),
                SleepTimerGraph.transmitter(context),
            )
            val result = firer.fire()
            if (result != FireResult.NO_TIMER) SleepTimerGraph.notifier(context).showResult(result)
            return result
        }

        fun pendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            if (action == ACTION_FIRE) 0 else 1,
            Intent(context, SleepTimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Nach Neustart (oder App-Update): offenen Timer neu planen, abgelaufenen verwerfen. */
class SleepTimerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            SleepTimerGraph.scheduler(context).restoreAfterBoot()
        }
    }
}
