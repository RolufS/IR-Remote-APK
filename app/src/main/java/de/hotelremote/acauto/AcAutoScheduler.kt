package de.hotelremote.acauto

import de.hotelremote.data.AcState
import de.hotelremote.data.RemoteStorage
import de.hotelremote.data.autoSettings
import de.hotelremote.ir.IrSignal
import de.hotelremote.weather.OutdoorWeather
import de.hotelremote.weather.WeatherResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.io.IOException

/** Was beim letzten Durchlauf nicht geklappt hat. */
@Serializable
enum class AcAutoProblem { WEATHER, SEND }

/**
 * Laufende Klima-Automatik (es gibt höchstens eine, für den Raum [roomId]). [sent] ist der zuletzt von der
 * Automatik gesendete Zustand; [outdoorC] (°C) und [target] (Einheit des Geräts) stammen vom letzten Wetterwert.
 */
@Serializable
data class AcAutoRun(
    val roomId: String,
    val nextAt: Long,
    val sent: AcState? = null,
    val outdoorC: Double? = null,
    val target: Double? = null,
    val problem: AcAutoProblem? = null,
)

/** Dauerhafte Ablage (überlebt App-Ende und Neustart). */
interface AcAutoStore {
    fun load(): AcAutoRun?
    fun save(run: AcAutoRun)
    fun clear()
}

/** Hülle um AlarmManager (ungenauer Alarm, auch im Doze-Modus); es gibt höchstens einen. */
interface AcAutoAlarm {
    fun schedule(atMillis: Long)
    fun cancel()
}

/** Laufende Benachrichtigung „Klima-Automatik · draußen 31 °C → 26 °C“ mit „Stopp“. */
interface AcAutoNotifier {
    fun showRunning(run: AcAutoRun)
    fun cancelRunning()
}

/**
 * Startet, stoppt und plant die Klima-Automatik alle [INTERVAL_MS] neu; ohne Android-Abhängigkeiten.
 * App, Alarm-Receiver und Boot-Receiver teilen sich eine Instanz pro Prozess.
 */
class AcAutoScheduler(
    private val store: AcAutoStore,
    private val alarm: AcAutoAlarm,
    private val notifier: AcAutoNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AcAutoRun?> = _state.asStateFlow()

    /** Startet für [roomId] (eine laufende Automatik eines anderen Raums endet); den ersten Durchlauf startet der Aufrufer. */
    @Synchronized
    fun start(roomId: String): AcAutoRun {
        val run = AcAutoRun(roomId, nextAt = clock() + INTERVAL_MS)
        alarm.schedule(run.nextAt)
        put(run)
        return run
    }

    @Synchronized
    fun stop() {
        alarm.cancel()
        store.clear()
        _state.value = null
        notifier.cancelRunning()
    }

    /** Stoppt, wenn die Automatik für [roomId] läuft; liefert, ob sie lief. */
    @Synchronized
    fun stopRoom(roomId: String): Boolean {
        if (store.load()?.roomId != roomId) return false
        stop()
        return true
    }

    @Synchronized
    fun current(): AcAutoRun? = store.load()

    /** Ergebnis eines Durchlaufs für [roomId] merken und den nächsten planen; null, wenn sie inzwischen nicht mehr läuft. */
    @Synchronized
    fun finished(roomId: String, update: (AcAutoRun) -> AcAutoRun): AcAutoRun? {
        val run = store.load()?.takeIf { it.roomId == roomId } ?: return null
        val next = update(run).copy(nextAt = clock() + INTERVAL_MS)
        alarm.schedule(next.nextAt)
        put(next)
        return next
    }

    /**
     * App-Start, Rückkehr in die App und nach dem Neustart: der Alarm kann fehlen (Neustart, „Beenden erzwingen“).
     * Ein überfälliger Durchlauf wird gleich nachgeholt.
     */
    @Synchronized
    fun rearm() {
        val run = store.load()
        if (run == null) {
            _state.value = null
            return
        }
        val at = maxOf(run.nextAt, clock())
        alarm.schedule(at)
        put(run.copy(nextAt = at))
    }

    fun restoreAfterBoot() = rearm()

    /** Neu lesen (z. B. ob der Receiver inzwischen gesendet hat). */
    @Synchronized
    fun reload() {
        _state.value = store.load()
    }

    private fun put(run: AcAutoRun) {
        store.save(run)
        _state.value = run
        notifier.showRunning(run)
    }

    companion object {
        const val INTERVAL_MS = 30 * 60_000L
    }
}

/** Ergebnis eines Durchlaufs. */
enum class AcAutoTick { NOT_RUNNING, STOPPED, SENT, UNCHANGED, WEATHER_FAILED, SEND_FAILED }

/**
 * Ein Durchlauf: Wetter holen → Ziel berechnen → nur bei geändertem Ziel senden (Ein · Kühlen · Ziel · Lüfter)
 * und den Zustand des Raums speichern. Wetter nicht erreichbar: nichts senden, Hinweis in der Benachrichtigung.
 * Raum, Klimaanlage oder Wetter weg: die Automatik endet. Durchläufe laufen nie gleichzeitig (auch nicht App und
 * Receiver). Der Wetterabruf hat höchstens [WEATHER_BUDGET_MS], damit der Receiver unter 8 s bleibt.
 */
class AcAutoRunner(
    private val scheduler: AcAutoScheduler,
    private val remotes: RemoteStorage,
    private val weather: OutdoorWeather,
    private val send: (IrSignal) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** [sendAlways]: beim Start auch senden, wenn das Ziel gleich geblieben ist. */
    suspend fun tick(sendAlways: Boolean = false): AcAutoTick = mutex.withLock { runOnce(sendAlways) }

    private suspend fun runOnce(sendAlways: Boolean): AcAutoTick {
        val run = scheduler.current() ?: return AcAutoTick.NOT_RUNNING
        val id = run.roomId
        val room = try {
            remotes.all().firstOrNull { it.id == id }
        } catch (e: IOException) {
            scheduler.finished(id) { it }
            return AcAutoTick.SEND_FAILED
        }
        val ac = room?.ac
        val codes = ac?.let { runCatching { it.decodeCodes() }.getOrNull() }
        if (ac == null || codes == null) {
            scheduler.stopRoom(id)
            return AcAutoTick.STOPPED
        }
        val result = withTimeoutOrNull(WEATHER_BUDGET_MS) { runInterruptible(io) { weather.refresh() } } ?: WeatherResult.Failed
        val reading = when (result) {
            WeatherResult.Inactive -> {
                scheduler.stopRoom(id)
                return AcAutoTick.STOPPED
            }
            WeatherResult.Failed -> {
                scheduler.finished(id) { it.copy(problem = AcAutoProblem.WEATHER) }
                return AcAutoTick.WEATHER_FAILED
            }
            is WeatherResult.Ok -> result.reading
        }
        // Inzwischen gestoppt oder anderer Raum: nichts senden.
        if (scheduler.current()?.roomId != id) return AcAutoTick.NOT_RUNNING
        val settings = ac.autoSettings
        val target = acAutoTarget(reading.celsius, ac, settings)
        val state = acAutoState(codes, ac, settings, target)
        fun AcAutoRun.measured() = copy(outdoorC = reading.celsius, target = state.temp)
        if (!sendAlways && state == run.sent) {
            scheduler.finished(id) { it.measured().copy(problem = null) }
            return AcAutoTick.UNCHANGED
        }
        val signal = codes.signalFor(state.power, state.mode, state.fan, state.swing, state.temp)
        val sent = signal != null && try {
            send(signal)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!sent) {
            scheduler.finished(id) { it.measured().copy(problem = AcAutoProblem.SEND) }
            return AcAutoTick.SEND_FAILED
        }
        try {
            remotes.updateAcState(id, state)
        } catch (e: IOException) {
            // Gesendet ist gesendet; die Anzeige zeigt dann den vorigen Zustand.
        }
        scheduler.finished(id) { it.measured().copy(sent = state, problem = null) }
        return AcAutoTick.SENT
    }

    companion object {
        /** Höchstens so lange auf das Wetter warten (Verbindung und Lesen je 5 s wären sonst zu lang). */
        const val WEATHER_BUDGET_MS = 6_000L

        private val mutex = Mutex()
    }
}
