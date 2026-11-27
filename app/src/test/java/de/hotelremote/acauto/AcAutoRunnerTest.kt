package de.hotelremote.acauto

import de.hotelremote.data.AcAutoSettings
import de.hotelremote.data.AcState
import de.hotelremote.data.PLAIN_PATHS
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.data.pulsesOf
import de.hotelremote.data.testAc
import de.hotelremote.ir.IrSignal
import de.hotelremote.weather.FakeWeatherApi
import de.hotelremote.weather.MemoryWeatherStore
import de.hotelremote.weather.OutdoorWeather
import de.hotelremote.weather.PATTAYA
import de.hotelremote.weather.WeatherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Durchlauf mit Fake-Wetter, Fake-Sender und echter remotes.json (ohne Netz, ohne IR). */
class AcAutoRunnerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000_000L
    private val autoStore = FakeAcAutoStore()
    private val alarm = FakeAcAutoAlarm()
    private val notifier = FakeAcAutoNotifier()
    private val scheduler = AcAutoScheduler(autoStore, alarm, notifier) { now }
    private val api = FakeWeatherApi(celsius = 28.0)
    private val weatherStore = MemoryWeatherStore(WeatherSettings(enabled = true, place = PATTAYA))
    private val weather = OutdoorWeather(weatherStore, api) { now }
    private val sent = mutableListOf<IrSignal>()
    private var sendFails = false
    private val remotes by lazy { RemoteStore(File(tmp.root, "remotes.json")) }

    /** Kühlen Auto 20–24 (PLAIN_PATHS): Untergrenze 22, damit Ziele im vorhandenen Bereich liegen. */
    private fun room(settings: AcAutoSettings = AcAutoSettings(5.0, 22.0)) = SavedRemote(
        id = "r1",
        lastUsed = 1,
        ac = testAc(state = AcState(false, "heat", 19.0, "auto")).copy(auto = settings),
    )

    private fun runner() = AcAutoRunner(scheduler, remotes, weather, {
        if (sendFails) throw UnsupportedOperationException("kein IR")
        sent += it
    }, Dispatchers.Unconfined)

    private fun tick(always: Boolean = false) = runBlocking { runner().tick(always) }

    private fun frames() = sent.map { it.frame }

    @Test fun notRunningDoesNothing() {
        remotes.add(room())
        assertEquals(AcAutoTick.NOT_RUNNING, tick())
        assertTrue(api.calls.isEmpty())
        assertTrue(sent.isEmpty())
    }

    @Test fun startSendsAtOnceAndStoresState() {
        remotes.add(room())
        scheduler.start("r1")
        assertEquals(AcAutoTick.SENT, tick(always = true))
        // draußen 28 − 5 = 23 → Ein · Kühlen · 23 · Auto
        assertEquals(listOf(pulsesOf(PLAIN_PATHS, "cool/auto/23")), frames())
        val state = AcState(true, "cool", 23.0, "auto")
        assertEquals(state, remotes.all().single().ac?.state)
        val run = autoStore.saved!!
        assertEquals(state, run.sent)
        assertEquals(28.0, run.outdoorC!!, 0.0)
        assertEquals(23.0, run.target!!, 0.0)
        assertNull(run.problem)
        assertEquals(now + AcAutoScheduler.INTERVAL_MS, run.nextAt)
        assertEquals(run, notifier.running)
        // Start sendet auch, wenn sich das Ziel nicht geändert hat.
        assertEquals(AcAutoTick.SENT, tick(always = true))
        assertEquals(2, sent.size)
    }

    @Test fun sendsOnlyWhenTargetChanges() {
        remotes.add(room())
        scheduler.start("r1")
        tick(always = true)
        now += AcAutoScheduler.INTERVAL_MS
        api.celsius = 28.3 // gleiches Ziel 23
        assertEquals(AcAutoTick.UNCHANGED, tick())
        assertEquals(1, sent.size)
        assertEquals(28.3, autoStore.saved!!.outdoorC!!, 0.0)
        assertEquals("at@${now + AcAutoScheduler.INTERVAL_MS}", alarm.calls.last())
        api.celsius = 29.0 // Ziel 24
        assertEquals(AcAutoTick.SENT, tick())
        assertEquals(pulsesOf(PLAIN_PATHS, "cool/auto/24"), frames().last())
        assertEquals(24.0, remotes.all().single().ac!!.state.temp, 0.0)
    }

    @Test fun weatherFailureSendsNothingAndTriesAgainNextInterval() {
        remotes.add(room())
        scheduler.start("r1")
        tick(always = true)
        api.fail = true
        now += AcAutoScheduler.INTERVAL_MS
        assertEquals(AcAutoTick.WEATHER_FAILED, tick())
        assertEquals(1, sent.size)
        val run = autoStore.saved!!
        assertEquals(AcAutoProblem.WEATHER, run.problem)
        // Der letzte Wert bleibt für die Benachrichtigung.
        assertEquals(28.0, run.outdoorC!!, 0.0)
        assertEquals(23.0, run.target!!, 0.0)
        assertEquals(now + AcAutoScheduler.INTERVAL_MS, run.nextAt)
        assertEquals(run, notifier.running)
        api.fail = false
        api.celsius = 29.0
        assertEquals(AcAutoTick.SENT, tick())
        assertNull(autoStore.saved!!.problem)
    }

    @Test fun sendFailureIsRetriedNextTime() {
        remotes.add(room())
        scheduler.start("r1")
        sendFails = true
        assertEquals(AcAutoTick.SEND_FAILED, tick(always = true))
        assertEquals(AcAutoProblem.SEND, autoStore.saved!!.problem)
        assertNull(autoStore.saved!!.sent)
        assertEquals(AcState(false, "heat", 19.0, "auto"), remotes.all().single().ac!!.state)
        sendFails = false
        assertEquals(AcAutoTick.SENT, tick())
    }

    @Test fun removedRoomOrAcStopsAutomation() {
        remotes.add(room())
        scheduler.start("r1")
        remotes.setAc("r1", null)
        assertEquals(AcAutoTick.STOPPED, tick())
        assertNull(autoStore.saved)
        assertNull(notifier.running)
        assertEquals("cancel", alarm.calls.last())

        scheduler.start("gone")
        assertEquals(AcAutoTick.STOPPED, tick())
        assertNull(autoStore.saved)
        assertTrue(sent.isEmpty())
    }

    @Test fun weatherSwitchedOffStopsWithoutNetwork() {
        remotes.add(room())
        scheduler.start("r1")
        weather.setEnabled(false)
        assertEquals(AcAutoTick.STOPPED, tick())
        assertTrue(api.calls.isEmpty())
        assertNull(autoStore.saved)
    }

    @Test fun ceilingAndFanFromRoomSettings() {
        remotes.add(room(AcAutoSettings(5.0, 20.0, notWarmerThan = 22.0, fan = "low")))
        scheduler.start("r1")
        api.celsius = 35.0
        tick(always = true)
        assertEquals(listOf(pulsesOf(PLAIN_PATHS, "cool/low/22")), frames())
    }
}
