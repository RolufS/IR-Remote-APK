package de.hotelremote.sleeptimer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import de.hotelremote.MainActivity
import de.hotelremote.R
import de.hotelremote.data.RemoteStore
import de.hotelremote.ir.AndroidIrTransmitter
import de.hotelremote.ir.IrTransmitter
import java.io.File

/** SharedPreferences; `commit()`, weil der Prozess direkt nach dem Receiver enden kann. */
class AndroidSleepTimerStore(private val prefs: SharedPreferences) : SleepTimerStore {
    override fun load(): SleepTimer? {
        val endAt = prefs.getLong(END_AT, 0L).takeIf { it > 0 } ?: return null
        val remoteId = prefs.getString(REMOTE_ID, null) ?: return null
        val action = when (val automation = prefs.getString(AUTOMATION_ID, null)) {
            null -> SleepAction.PowerOff
            else -> SleepAction.RunAutomation(automation)
        }
        return SleepTimer(endAt, remoteId, action, prefs.getBoolean(EXACT, false))
    }

    override fun save(timer: SleepTimer) {
        prefs.edit(commit = true) {
            putLong(END_AT, timer.endAt)
            putString(REMOTE_ID, timer.remoteId)
            putString(AUTOMATION_ID, (timer.action as? SleepAction.RunAutomation)?.automationId)
            putBoolean(EXACT, timer.exact)
        }
    }

    override fun clear() {
        prefs.edit(commit = true) {
            remove(END_AT)
            remove(REMOTE_ID)
            remove(AUTOMATION_ID)
            remove(EXACT)
        }
    }

    /** Akku-Hinweis nur beim ersten Timer-Start. */
    var batteryHintShown: Boolean
        get() = prefs.getBoolean(BATTERY_HINT, false)
        set(value) = prefs.edit { putBoolean(BATTERY_HINT, value) }

    companion object {
        const val PREFS = "sleep_timer"
        private const val END_AT = "end_at"
        private const val REMOTE_ID = "remote_id"
        private const val AUTOMATION_ID = "automation_id"
        private const val EXACT = "exact"
        private const val BATTERY_HINT = "battery_hint_shown"
    }
}

/** Ein Alarm (RTC_WAKEUP, auch im Doze-Modus) an [SleepTimerReceiver]. */
class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val am = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    @SuppressLint("MissingPermission") // geprüft mit canScheduleExact(); SecurityException fängt der Scheduler ab
    override fun scheduleExact(atMillis: Long) = am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, fireIntent())

    override fun scheduleInexact(atMillis: Long) = am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, fireIntent())

    override fun cancel() = am.cancel(fireIntent())

    private fun fireIntent(): PendingIntent = SleepTimerReceiver.pendingIntent(context, SleepTimerReceiver.ACTION_FIRE)
}

/** Benachrichtigungen im Kanal „Sleep-Timer“; ohne Erlaubnis (ab Android 13) wird still nichts angezeigt. */
class AndroidSleepTimerNotifier(private val context: Context) : SleepTimerNotifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun showRunning(timer: SleepTimer) {
        val remotes = RemoteStore(remotesFile(context)).let { runCatching { it.all() }.getOrDefault(emptyList()) }
        val remote = remotes.firstOrNull { it.id == timer.remoteId }
        val action = when (val a = timer.action) {
            SleepAction.PowerOff -> context.getString(R.string.timer_action_off)
            is SleepAction.RunAutomation -> remote?.automations?.firstOrNull { it.id == a.automationId }?.name.orEmpty()
        }
        val n = builder()
            .setContentTitle(context.getString(R.string.timer_notif_running))
            .setContentText(context.getString(R.string.timer_notif_text, remote?.title.orEmpty(), action))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(timer.endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .addAction(0, context.getString(R.string.list_cancel), SleepTimerReceiver.pendingIntent(context, SleepTimerReceiver.ACTION_CANCEL))
            .build()
        notify(ID_RUNNING, n)
    }

    override fun cancelRunning() = manager.cancel(ID_RUNNING)

    /** Ergebnis kurz anzeigen (verschwindet nach einer Minute von selbst). */
    fun showResult(result: FireResult) {
        val text = when (result) {
            FireResult.DONE, FireResult.NO_TIMER -> null
            FireResult.REMOTE_MISSING -> R.string.timer_fail_remote
            FireResult.ACTION_MISSING -> R.string.timer_fail_action
            FireResult.SEND_FAILED -> R.string.send_failed
            FireResult.INCOMPLETE -> R.string.timer_fail_incomplete
        }
        val title = if (result == FireResult.DONE) R.string.timer_notif_done else R.string.timer_notif_failed
        val n = builder()
            .setContentTitle(context.getString(title))
            .apply { if (text != null) setContentText(context.getString(text)) }
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_TIMEOUT_MS)
            .build()
        notify(ID_RESULT, n)
    }

    private fun builder(): NotificationCompat.Builder {
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.timer_channel), NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // geprüft mit canPostNotifications()
    private fun notify(id: Int, n: android.app.Notification) {
        if (!canPostNotifications(context)) return
        try {
            manager.notify(id, n)
        } catch (e: SecurityException) {
            // Erlaubnis gerade entzogen: der Timer läuft ohne Benachrichtigung.
        }
    }

    companion object {
        const val CHANNEL = "sleep_timer"
        const val ID_RUNNING = 1
        const val ID_RESULT = 2
        private const val RESULT_TIMEOUT_MS = 60_000L

        fun canPostNotifications(context: Context): Boolean =
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

internal fun remotesFile(context: Context) = File(context.filesDir, "remotes.json")

/** Eine Scheduler-Instanz pro Prozess, damit App und Receiver denselben Zustand sehen. */
object SleepTimerGraph {
    @Volatile private var scheduler: SleepTimerScheduler? = null

    /** Für Tests austauschbar. */
    @VisibleForTesting
    @Volatile
    var transmitter: (Context) -> IrTransmitter = ::AndroidIrTransmitter

    fun store(context: Context) =
        AndroidSleepTimerStore(context.applicationContext.getSharedPreferences(AndroidSleepTimerStore.PREFS, Context.MODE_PRIVATE))

    fun notifier(context: Context) = AndroidSleepTimerNotifier(context.applicationContext)

    fun scheduler(context: Context): SleepTimerScheduler = scheduler ?: synchronized(this) {
        scheduler ?: SleepTimerScheduler(
            store(context),
            AndroidAlarmScheduler(context.applicationContext),
            notifier(context),
        ).also { scheduler = it }
    }

    @VisibleForTesting
    fun reset() {
        scheduler = null
        transmitter = ::AndroidIrTransmitter
    }
}
