package de.hotelremote.quick

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.hotelremote.R
import de.hotelremote.data.SavedRemote
import de.hotelremote.sleeptimer.AndroidSleepTimerNotifier

/**
 * Steuerung auf dem Sperrbildschirm: dauerhafte, stille Benachrichtigung (Kanal „Schnellsteuerung“, öffentlich
 * sichtbar) mit 1–5 frei belegbaren Knöpfen, die per Broadcast ohne Entsperren senden. Bei „Erweitert“ lässt sie sich
 * zur Mini-Fernbedienung aufziehen; wenn erlaubt, öffnet ein Knopf die ganze Fernbedienung über dem Sperrbildschirm
 * ([LockScreenRemoteActivity]). Kein Foreground-Service.
 */
class QuickNotification(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Zeigt die Benachrichtigung nach [prefs], wenn eingeschaltet und erlaubt; sonst entfernt sie sie. */
    fun update(remote: SavedRemote?, prefs: QuickPrefs) {
        if (prefs.lockScreen && AndroidSleepTimerNotifier.canPostNotifications(context)) show(remote, prefs) else manager.cancel(ID)
    }

    @SuppressLint("MissingPermission") // geprüft mit canPostNotifications()
    private fun show(remote: SavedRemote?, prefs: QuickPrefs) {
        ensureChannel()
        val views = RemoteViews(context.packageName, R.layout.notification_quick).apply {
            bindQuickButtons(context, prefs.notificationButtons, QuickButtons.MAX_NOTIFICATION, "notification")
            bindFullRemote(context, R.id.quick_full_remote, R.id.quick_full_remote_btn, prefs.fullRemote)
        }
        val big = if (prefs.lockStyle == LockStyle.EXPANDED) {
            RemoteViews(context.packageName, R.layout.notification_quick_big).apply {
                bindMiniRemote(context, remote, prefs.inputKey, prefs.fullRemote)
            }
        } else {
            null
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_quick_power)
            .setContentTitle(context.getString(R.string.menu_quick))
            .setSubText(remote?.title ?: context.getString(R.string.quick_no_remote))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setCustomBigContentView(big)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context))
            .build()
        try {
            manager.notify(ID, n)
        } catch (e: SecurityException) {
            // Erlaubnis gerade entzogen: keine Steuerung auf dem Sperrbildschirm.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.menu_quick), NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL = "quick_controls"

        /** 1 und 2 belegt der Sleep-Timer. */
        const val ID = 3
    }
}
