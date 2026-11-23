package de.hotelremote.quick

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Knöpfe von Widget und Sperrbildschirm-Benachrichtigung (nicht exportiert, nur über eigene PendingIntents).
 * Sendet direkt im Receiver: Datei lesen und ein Rahmen senden bleiben weit unter der Zeitgrenze, auch ohne Entsperren.
 */
class QuickControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND) return
        val name = intent.getStringExtra(EXTRA_ACTION)
        val action = QuickAction.entries.firstOrNull { it.name == name } ?: return
        reportQuickResult(context, QuickControls.send(context, action))
    }

    companion object {
        const val ACTION_SEND = "de.hotelremote.action.QUICK_SEND"
        private const val EXTRA_ACTION = "quick_action"

        fun intent(context: Context, action: QuickAction): Intent =
            Intent(context, QuickControlReceiver::class.java).setAction(ACTION_SEND).putExtra(EXTRA_ACTION, action.name)

        /** Eigener requestCode je Aktion, sonst ersetzte ein PendingIntent die anderen. */
        fun pendingIntent(context: Context, action: QuickAction): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.ordinal,
            intent(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
