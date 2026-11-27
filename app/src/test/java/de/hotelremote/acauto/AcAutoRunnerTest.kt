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
import de.hotelremote.data.SWING_PATHS
import de.hotelremote.weather.WeatherApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    private fun runner(
        w: OutdoorWeather = weather,
        budgetMs: Long = AcAutoRunner.WEATHER_BUDGET_MS,
        io: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = AcAutoRunner(scheduler, remotes, w, {
        if (sendFails) throw UnsupportedOperationException("kein IR")
        sent += it
    }, io, budgetMs)

    /** Wetter, dessen Abruf hängt, bis [release] freigibt (länger als jedes Budget im Test). */
    private fun hangingWeather(release: CountDownLatch) = OutdoorWeather(
        weatherStore,
        object : WeatherApi by api {
            override fun currentTemperature(latitude: Double, longitude: Double): Double {
                release.await(10, TimeUnit.SECONDS)
                return 30.0
            }
        },
    ) { now }

    @Test fun swingChangedByHandDoesNotCauseResend() {
        val ac = testAc(SWING_PATHS, AcState(true, "cool", 22.0, "auto", "off")).copy(auto = AcAutoSettings(5.0, 22.0))
        remotes.add(SavedRemote(id = "r1", lastUsed = 1, ac = ac))
        scheduler.start("r1")
        tick(always = true)
        assertEquals(1, sent.size)
        // Swing von Hand geändert: kein neues Ziel, nichts senden.
        remotes.updateAcState("r1", AcState(true, "cool", 23.0, "auto", "swing"))
        assertEquals(AcAutoTick.UNCHANGED, tick())
        assertEquals(1, sent.size)
    }

    @Test fun nextAlarmIsPlannedBeforeAnyWork() {
        remotes.add(room())
        scheduler.start("r1")
        now += 5 * 60_000L
        alarm.calls.clear()
        api.fail = true
        tick()
        assertEquals("at@${now + AcAutoScheduler.INTERVAL_MS}", alarm.calls.first())
    }

    @Test fun hangingWeatherEndsWithinBudgetSendsNothingAndKeepsAlarm() {
        remotes.add(room())
        scheduler.start("r1")
        val release = CountDownLatch(1)
        try {
            val started = System.nanoTime()
            val result = runBlocking { runner(hangingWeather(release), budgetMs = 300, io = Dispatchers.IO).tick(sendAlways = true) }
            val tookMs = (System.nanoTime() - started) / 1_000_000
            assertEquals(AcAutoTick.WEATHER_FAILED, result)
            assertTrue("dauerte $tookMs ms", tookMs < 2_000)
            assertTrue(sent.isEmpty())
            val run = autoStore.saved!!
            assertEquals(AcAutoProblem.WEATHER, run.problem)
            assertEquals(run, notifier.running)
            assertEquals("at@${now + AcAutoScheduler.INTERVAL_MS}", alarm.calls.last())
        } finally {
            release.countDown()
        }
    }

    @Test fun cancelledTickStillShowsWeatherProblemAndKeepsAlarm() {
        remotes.add(room())
        scheduler.start("r1")
        val release = CountDownLatch(1)
        try {
            // Wie das 8-s-Limit des Receivers, nur kürzer: der Durchlauf wird von außen abgebrochen.
            val result = runBlocking {
                withTimeoutOrNull(300) { runner(hangingWeather(release), budgetMs = 5_000, io = Dispatchers.IO).tick() }
            }
            assertNull(result)
            assertTrue(sent.isEmpty())
            assertEquals(AcAutoProblem.WEATHER, autoStore.saved!!.problem)
            assertEquals(AcAutoProblem.WEATHER, notifier.running!!.problem)
            assertEquals("at@${now + AcAutoScheduler.INTERVAL_MS}", alarm.calls.last())
        } finally {
            release.countDown()
        }
    }

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
