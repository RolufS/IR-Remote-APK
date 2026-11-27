package de.hotelremote.acauto

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.hotelremote.data.RemoteStore
import de.hotelremote.ir.DirectSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Alarm der Klima-Automatik (ACTION_TICK, alle 30 min) oder „Stopp“ in der Benachrichtigung (ACTION_STOP).
 * Beim Alarm läuft ein Durchlauf ohne Activity: `goAsync()` und eine Coroutine, insgesamt höchstens
 * [MAX_RECEIVER_MS] (Wetter höchstens [AcAutoRunner.WEATHER_BUDGET_MS], danach das Senden).
 */
class AcAutoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> AcAutoGraph.scheduler(context).stop()
            ACTION_TICK -> {
                // Ohne Broadcast-Kontext (direkter Aufruf) liefert goAsync() null.
                val pending: PendingResult? = goAsync()
                val app = context.applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        withTimeoutOrNull(MAX_RECEIVER_MS) { tick(app) }
                    } finally {
                        pending?.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TICK = "de.hotelremote.action.AC_AUTO_TICK"
        const val ACTION_STOP = "de.hotelremote.action.AC_AUTO_STOP"

        /** Grenze für den ganzen Durchlauf im Receiver (Android beendet ihn nach etwa 10 s). */
        const val MAX_RECEIVER_MS = 8_000L

        /** Ein Durchlauf mit direktem IR-Sender (ohne Warteschlange der App). */
        internal suspend fun tick(context: Context): AcAutoTick {
            val weather = AcAutoGraph.weather(context)
            weather.reload()
            val sender = DirectSender(AcAutoGraph.transmitter(context))
            return AcAutoRunner(
                AcAutoGraph.scheduler(context),
                RemoteStore(File(context.filesDir, "remotes.json")),
                weather,
                sender::send,
            ).tick()
        }

        fun pendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            if (action == ACTION_TICK) 10 else 11,
            Intent(context, AcAutoReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
