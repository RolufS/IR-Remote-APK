package de.hotelremote.acauto

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import de.hotelremote.MainActivity
import de.hotelremote.R
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.acTempText
import de.hotelremote.ir.AndroidIrTransmitter
import de.hotelremote.ir.IrTransmitter
import de.hotelremote.sleeptimer.AndroidSleepTimerNotifier
import de.hotelremote.weather.OpenMeteoClient
import de.hotelremote.weather.OutdoorWeather
import de.hotelremote.weather.PrefsWeatherStore
import de.hotelremote.weather.WeatherApi
import de.hotelremote.weather.celsiusToFahrenheit
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

/** SharedPreferences als JSON; `commit()`, weil der Prozess direkt nach dem Receiver enden kann. */
class PrefsAcAutoStore(private val prefs: SharedPreferences) : AcAutoStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): AcAutoRun? {
        val text = prefs.getString(KEY, null) ?: return null
        return try {
            json.decodeFromString(AcAutoRun.serializer(), text)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun save(run: AcAutoRun) {
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(AcAutoRun.serializer(), run)) }
    }

    override fun clear() {
        prefs.edit(commit = true) { remove(KEY) }
    }

    companion object {
        const val PREFS = "ac_auto"
        private const val KEY = "run"
    }
}

/** Ungenauer Alarm (`setAndAllowWhileIdle`, auch im Doze-Modus) an [AcAutoReceiver]; kein Exakt-Alarm nötig. */
class AndroidAcAutoAlarm(private val context: Context) : AcAutoAlarm {
    private val am = context.getSystemService(AlarmManager::class.java)

    override fun schedule(atMillis: Long) = am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, tickIntent())

    override fun cancel() = am.cancel(tickIntent())

    private fun tickIntent() = AcAutoReceiver.pendingIntent(context, AcAutoReceiver.ACTION_TICK)
}

/** Laufende Benachrichtigung im eigenen Kanal (niedrige Wichtigkeit); ohne Erlaubnis still nichts. */
class AndroidAcAutoNotifier(private val context: Context) : AcAutoNotifier {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // geprüft mit canPostNotifications()
    override fun showRunning(run: AcAutoRun) {
        if (!AndroidSleepTimerNotifier.canPostNotifications(context)) return
        val room = runCatching { RemoteStore(File(context.filesDir, "remotes.json")).all() }.getOrDefault(emptyList())
            .firstOrNull { it.id == run.roomId }
        val fahrenheit = room?.ac?.fahrenheit == true
        val values = if (run.outdoorC != null && run.target != null) {
            context.getString(R.string.acauto_values, outdoorText(run.outdoorC, fahrenheit), acTempText(run.target, fahrenheit))
        } else {
            null
        }
        val title = when {
            run.problem == AcAutoProblem.WEATHER -> context.getString(R.string.acauto_notif_weather_failed)
            run.problem == AcAutoProblem.SEND -> context.getString(R.string.acauto_notif_send_failed)
            values != null -> context.getString(R.string.acauto_notif_title, values)
            else -> context.getString(R.string.acauto_notif_running)
        }
        val text = listOfNotNull(
            room?.title,
            values?.takeIf { run.problem != null }?.let { context.getString(R.string.acauto_notif_last, it) },
        ).joinToString(" · ")
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ac)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.acauto_stop), AcAutoReceiver.pendingIntent(context, AcAutoReceiver.ACTION_STOP))
            .build()
        try {
            manager.notify(ID, n)
        } catch (e: SecurityException) {
            // Erlaubnis gerade entzogen: die Automatik läuft ohne Benachrichtigung.
        }
    }

    override fun cancelRunning() = manager.cancel(ID)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.acauto_channel), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL = "ac_auto"

        /** 1 und 2 Sleep-Timer, 3 Schnellsteuerung. */
        const val ID = 4
    }
}

/** Außentemperatur gerundet in der Einheit des Geräts, z. B. „31 °C“. */
fun outdoorText(celsius: Double, fahrenheit: Boolean): String =
    acTempText((if (fahrenheit) celsiusToFahrenheit(celsius) else celsius).roundToInt().toDouble(), fahrenheit)

/** Eine Instanz je Prozess für Wetter und Klima-Automatik, damit App und Receiver denselben Zustand sehen. */
object AcAutoGraph {
    @Volatile private var scheduler: AcAutoScheduler? = null
    @Volatile private var weather: OutdoorWeather? = null

    /** Für Tests austauschbar (kein echtes Netz, kein IR). */
    @VisibleForTesting
    @Volatile
    var weatherApi: () -> WeatherApi = { OpenMeteoClient() }

    @VisibleForTesting
    @Volatile
    var transmitter: (Context) -> IrTransmitter = ::AndroidIrTransmitter

    fun weather(context: Context): OutdoorWeather = weather ?: synchronized(this) {
        weather ?: OutdoorWeather(
            PrefsWeatherStore(context.applicationContext.getSharedPreferences(PrefsWeatherStore.PREFS, Context.MODE_PRIVATE)),
            weatherApi(),
        ).also { weather = it }
    }

    fun scheduler(context: Context): AcAutoScheduler = scheduler ?: synchronized(this) {
        val app = context.applicationContext
        scheduler ?: AcAutoScheduler(
            PrefsAcAutoStore(app.getSharedPreferences(PrefsAcAutoStore.PREFS, Context.MODE_PRIVATE)),
            AndroidAcAutoAlarm(app),
            AndroidAcAutoNotifier(app),
        ).also { scheduler = it }
    }

    @VisibleForTesting
    fun reset() {
        scheduler = null
        weather = null
        weatherApi = { OpenMeteoClient() }
        transmitter = ::AndroidIrTransmitter
    }
}
