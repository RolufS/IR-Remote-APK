package de.hotelremote.sleeptimer

import de.hotelremote.Keys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Was der Timer am Ende tut. */
sealed interface SleepAction {
    /** Sendet POWER_OFF, sonst den Umschalter POWER. */
    data object PowerOff : SleepAction

    data class RunAutomation(val automationId: String) : SleepAction
}

/** Geplanter Timer; [exact] = als genauer Alarm geplant (sonst kann er einige Minuten später auslösen). */
data class SleepTimer(val endAt: Long, val remoteId: String, val action: SleepAction, val exact: Boolean)

/** Dauerhafte Ablage (überlebt App-Ende und Neustart). */
interface SleepTimerStore {
    fun load(): SleepTimer?
    fun save(timer: SleepTimer)
    fun clear()
}

/** Hülle um AlarmManager; es gibt immer höchstens einen Alarm. */
interface AlarmScheduler {
    fun canScheduleExact(): Boolean
    fun scheduleExact(atMillis: Long)
    fun scheduleInexact(atMillis: Long)
    fun cancel()
}

/** Laufende Benachrichtigung mit Restzeit. */
interface SleepTimerNotifier {
    fun showRunning(timer: SleepTimer)
    fun cancelRunning()
}

/** Taste für „Ausschalten“: POWER_OFF vor dem Umschalter POWER; null ohne beides. */
fun powerOffKey(keys: Set<String>): String? = listOf(Keys.POWER_OFF, Keys.POWER).firstOrNull { it in keys }

/** Nur ein Ein/Aus-Umschalter: ist der Fernseher schon aus, schaltet der Timer ihn wieder ein. */
fun onlyPowerToggle(keys: Set<String>) = powerOffKey(keys) == Keys.POWER

/**
 * Plant, bricht ab und stellt den Handy-Sleep-Timer wieder her. Ohne Android-Abhängigkeiten; App,
 * Alarm-Receiver und Boot-Receiver teilen sich eine Instanz pro Prozess.
 */
class SleepTimerScheduler(
    private val store: SleepTimerStore,
    private val alarms: AlarmScheduler,
    private val notifier: SleepTimerNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<SleepTimer?> = _state.asStateFlow()

    @Synchronized
    fun start(minutes: Int, remoteId: String, action: SleepAction): SleepTimer {
        require(minutes in MIN_MINUTES..MAX_MINUTES) { "minutes=$minutes" }
        val endAt = clock() + minutes * 60_000L
        val timer = SleepTimer(endAt, remoteId, action, exact = schedule(endAt))
        store.save(timer)
        _state.value = timer
        notifier.showRunning(timer)
        return timer
    }

    @Synchronized
    fun cancel() {
        alarms.cancel()
        clear()
    }

    /** Neu lesen (z. B. wenn die App wieder sichtbar wird). */
    @Synchronized
    fun reload() {
        _state.value = store.load()
    }

    /** Nach dem Neustart: ein noch offener Timer wird neu geplant, ein abgelaufener verworfen. */
    @Synchronized
    fun restoreAfterBoot() {
        val timer = store.load() ?: return
        if (timer.endAt <= clock()) {
            cancel()
            return
        }
        val restored = timer.copy(exact = schedule(timer.endAt))
        store.save(restored)
        _state.value = restored
        notifier.showRunning(restored)
    }

    /**
     * App-Start bzw. Rückkehr in die App: Der Alarm kann fehlen (z. B. „Beenden erzwingen“, Wischen aus den
     * zuletzt verwendeten Apps auf HyperOS, entzogene Erlaubnis „Wecker und Erinnerungen“). Ein offener Timer
     * wird neu geplant (gleicher PendingIntent ersetzt den alten; genau, wenn erlaubt), ein bis zu
     * [OVERDUE_GRACE_MS] überfälliger löst sofort aus, ein älterer wird verworfen.
     */
    @Synchronized
    fun rearm() {
        val timer = store.load()
        val now = clock()
        when {
            timer == null -> _state.value = null
            timer.endAt > now -> {
                val rearmed = timer.copy(exact = schedule(timer.endAt))
                store.save(rearmed)
                _state.value = rearmed
                notifier.showRunning(rearmed)
            }
            now - timer.endAt <= OVERDUE_GRACE_MS -> {
                schedule(now)
                _state.value = timer
            }
            else -> cancel()
        }
    }

    /** Alarm ausgelöst: liefert den Timer genau einmal und löscht ihn (null, wenn er abgebrochen wurde). */
    @Synchronized
    fun takeDue(): SleepTimer? {
        val timer = store.load() ?: return null
        clear()
        return timer
    }

    private fun clear() {
        store.clear()
        _state.value = null
        notifier.cancelRunning()
    }

    /** Genau, wenn erlaubt; sonst (oder bei SecurityException) ungenau. Liefert, ob genau geplant wurde. */
    private fun schedule(at: Long): Boolean {
        if (alarms.canScheduleExact()) {
            try {
                alarms.scheduleExact(at)
                return true
            } catch (e: SecurityException) {
                // Berechtigung zwischen Prüfung und Planen entzogen: ungenau planen.
            }
        }
        alarms.scheduleInexact(at)
        return false
    }

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 480
        val PRESETS = listOf(15, 30, 45, 60, 90, 120)

        /** Bis zu so lange überfällig: beim Neuplanen sofort auslösen statt verwerfen. */
        const val OVERDUE_GRACE_MS = 15 * 60_000L
    }
}
