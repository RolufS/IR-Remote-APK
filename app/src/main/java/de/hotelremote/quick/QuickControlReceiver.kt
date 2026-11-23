package de.hotelremote.quick

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Knöpfe von Widget und Sperrbildschirm-Benachrichtigung (nicht exportiert, nur über eigene PendingIntents).
 * Eine Taste sendet direkt im Receiver: Datei lesen und ein Rahmen senden bleiben weit unter der Zeitgrenze, auch
 * ohne Entsperren. Eine Automation läuft wie beim Sleep-Timer mit `goAsync()` in einer Coroutine (höchstens 8 s).
 */
class QuickControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND) return
        val button = QuickButtons.decode(intent.getStringExtra(EXTRA_BUTTON))
            // Knopf eines Widgets aus Version 1.4, bis es neu aufgebaut ist.
            ?: QuickAction.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_ACTION) }?.button
            ?: return
        when (val plan = QuickControls.plan(context, button)) {
            is QuickPlan.Send -> reportQuickResult(context, QuickControls.transmit(context, plan.signal))
            is QuickPlan.Fail -> reportQuickResult(context, plan.result)
            is QuickPlan.Run -> runAsync(context, plan)
        }
    }

    private fun runAsync(context: Context, plan: QuickPlan.Run) {
        // Ohne Broadcast-Kontext (direkter Aufruf) liefert goAsync() null.
        val pending: PendingResult? = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + QuickControls.automationDispatcher).launch {
            try {
                val result = QuickControls.run(app, plan)
                Handler(Looper.getMainLooper()).post { reportQuickResult(app, result) }
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEND = "de.hotelremote.action.QUICK_SEND"
        private const val EXTRA_BUTTON = "quick_button"
        private const val EXTRA_ACTION = "quick_action"

        /** [slot] macht den Intent eindeutig (z. B. "widget/12/3"), sonst ersetzte ein PendingIntent die anderen. */
        fun intent(context: Context, button: QuickButton, slot: String): Intent =
            Intent(context, QuickControlReceiver::class.java)
                .setAction(ACTION_SEND)
                .setData(Uri.parse("hotelremote://quick/$slot"))
                .putExtra(EXTRA_BUTTON, QuickButtons.encode(button))

        fun pendingIntent(context: Context, button: QuickButton, slot: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent(context, button, slot),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
