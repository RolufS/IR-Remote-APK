package de.hotelremote.acauto

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.R
import de.hotelremote.data.AcAutoSettings
import de.hotelremote.data.AcState
import de.hotelremote.data.PLAIN_PATHS
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.data.pulsesOf
import de.hotelremote.data.testAc
import de.hotelremote.sleeptimer.FakeTransmitter
import de.hotelremote.sleeptimer.SleepTimerBootReceiver
import de.hotelremote.weather.FakeWeatherApi
import de.hotelremote.weather.PATTAYA
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Receiver, Benachrichtigung und Boot-Wiederherstellung mit Fake-Wetter und Fake-Sender (kein Netz, kein IR). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AcAutoReceiverTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tx = FakeTransmitter()
    private val api = FakeWeatherApi(celsius = 31.0)
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AcAutoGraph.reset()
        AcAutoGraph.weatherApi = { api }
        AcAutoGraph.transmitter = { tx }
        RemoteStore(File(app.filesDir, "remotes.json")).add(
            SavedRemote(id = "r1", name = "Zimmer 5", lastUsed = 1, ac = testAc().copy(auto = AcAutoSettings(5.0, 22.0))),
        )
        AcAutoGraph.weather(app).choose(PATTAYA)
    }

    @After fun tearDown() = AcAutoGraph.reset()

    private fun notification() = notifications.getNotification(AndroidAcAutoNotifier.ID)

    @Test fun tickSendsShowsValuesAndPlansNextInexactAlarm() = runBlocking {
        val scheduler = AcAutoGraph.scheduler(app)
        scheduler.start("r1")
        val n0 = notification()
        assertEquals(app.getString(R.string.acauto_notif_running), n0.extras.getString("android.title"))
        assertEquals(app.getString(R.string.acauto_stop), n0.actions.single().title)

        assertEquals(AcAutoTick.SENT, AcAutoReceiver.tick(app))
        // 31 − 5 = 26, PLAIN_PATHS hat Kühlen bis 24 → 24
        assertEquals(listOf(pulsesOf(PLAIN_PATHS, "cool/auto/24")), tx.patterns)
        assertEquals(AcState(true, "cool", 24.0, "auto"), RemoteStore(File(app.filesDir, "remotes.json")).all().single().ac?.state)
        val n = notification()
        assertEquals("Klima-Automatik · draußen 31 °C → 24 °C", n.extras.getString("android.title"))
        assertEquals("Zimmer 5", n.extras.getCharSequence("android.text").toString())
        val alarm = alarms.peekNextScheduledAlarm()!!
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue(alarm.triggerAtTime > System.currentTimeMillis() + AcAutoScheduler.INTERVAL_MS - 60_000)

        // Gleiches Wetter: nichts senden.
        assertEquals(AcAutoTick.UNCHANGED, AcAutoReceiver.tick(app))
        assertEquals(1, tx.patterns.size)
    }

    @Test fun weatherFailureShowsHintAndLastValue() = runBlocking {
        AcAutoGraph.scheduler(app).start("r1")
        AcAutoReceiver.tick(app)
        api.fail = true
        assertEquals(AcAutoTick.WEATHER_FAILED, AcAutoReceiver.tick(app))
        assertEquals(1, tx.patterns.size)
        val n = notification()
        assertEquals("Klima-Automatik · Wetter nicht erreichbar", n.extras.getString("android.title"))
        assertEquals("Zimmer 5 · zuletzt draußen 31 °C → 24 °C", n.extras.getCharSequence("android.text").toString())
    }

    @Test fun stopActionEndsAutomation() {
        AcAutoGraph.scheduler(app).start("r1")
        AcAutoReceiver().onReceive(app, Intent(app, AcAutoReceiver::class.java).setAction(AcAutoReceiver.ACTION_STOP))
        assertNull(AcAutoGraph.scheduler(app).current())
        assertNull(notification())
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test fun bootReceiverRestoresRunningAutomation() {
        val prefs = app.getSharedPreferences(PrefsAcAutoStore.PREFS, Context.MODE_PRIVATE)
        val future = System.currentTimeMillis() + 600_000L
        PrefsAcAutoStore(prefs).save(AcAutoRun("r1", future, target = 24.0, outdoorC = 31.0))
        AcAutoGraph.reset()
        AcAutoGraph.weatherApi = { api }
        SleepTimerBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(future, alarms.peekNextScheduledAlarm()!!.triggerAtTime)
        assertEquals("Klima-Automatik · draußen 31 °C → 24 °C", notification().extras.getString("android.title"))
        assertTrue(api.calls.isEmpty())
    }

    @Test fun storeRoundTrip() {
        val store = PrefsAcAutoStore(app.getSharedPreferences(PrefsAcAutoStore.PREFS, Context.MODE_PRIVATE))
        val run = AcAutoRun("r1", 5, AcState(true, "cool", 24.0, "auto"), 31.5, 24.0, AcAutoProblem.WEATHER)
        store.save(run)
        assertEquals(run, store.load())
        store.clear()
        assertNull(store.load())
    }

    @Test fun nothingNetworkedWhileWeatherOff() = runBlocking {
        AcAutoGraph.weather(app).setEnabled(false)
        api.calls.clear()
        AcAutoGraph.scheduler(app).start("r1")
        assertEquals(AcAutoTick.STOPPED, AcAutoReceiver.tick(app))
        assertTrue(api.calls.isEmpty())
        assertTrue(tx.patterns.isEmpty())
    }
}
