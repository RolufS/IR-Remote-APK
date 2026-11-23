package de.hotelremote.quick

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import de.hotelremote.MainActivity
import de.hotelremote.R

/** Knopf-IDs in widget_quick.xml und notification_quick.xml. */
private val BUTTONS = listOf(
    R.id.quick_power to QuickAction.POWER,
    R.id.quick_vol_down to QuickAction.VOL_DOWN,
    R.id.quick_vol_up to QuickAction.VOL_UP,
    R.id.quick_input to QuickAction.INPUT,
)

/** Verbindet die vier Knöpfe mit dem [QuickControlReceiver]. */
internal fun RemoteViews.bindQuickButtons(context: Context) {
    for ((id, action) in BUTTONS) setOnClickPendingIntent(id, QuickControlReceiver.pendingIntent(context, action))
}

/** Öffnet die App (gleicher PendingIntent wie in der Sleep-Timer-Benachrichtigung). */
internal fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)
