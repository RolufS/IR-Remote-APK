package de.hotelremote.sleeptimer

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.RemoteStore
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
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File

/** Sendepfad des Alarm-Receivers mit Fake-Sender, echten Dateien, AlarmManager und Benachrichtigungen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SleepTimerReceiverTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tx = FakeTransmitter()
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        SleepTimerGraph.reset()
        SleepTimerGraph.transmitter = { tx }
        RemoteStore(File(app.filesDir, "remotes.json")).add(timerRemote(Keys.POWER to 1, Keys.POWER_OFF to 2))
    }

    @After fun tearDown() = SleepTimerGraph.reset()

    private fun text(id: Int) = app.getString(id)

    @Test fun startPlansExactAlarmAndShowsCountdown() {
        val timer = SleepTimerGraph.scheduler(app).start(30, "r1", SleepAction.PowerOff)
        val alarm = alarms.peekNextScheduledAlarm()!!
        assertEquals(timer.endAt, alarm.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        val n = notifications.getNotification(AndroidSleepTimerNotifier.ID_RUNNING)
        assertEquals(text(R.string.timer_notif_running), n.extras.getString("android.title"))
        assertTrue(n.extras.getBoolean("android.chronometerCountDown"))
        assertEquals(timer.endAt, n.`when`)
        assertEquals(text(R.string.list_cancel), n.actions.single().title)
    }

    @Test fun fireSendsPowerOffClearsTimerAndReports() = runBlocking {
        SleepTimerGraph.scheduler(app).start(30, "r1", SleepAction.PowerOff)
        assertEquals(FireResult.DONE, SleepTimerReceiver.fire(app))
        assertEquals(listOf(2), tx.sent)
        assertNull(SleepTimerGraph.store(app).load())
        assertNull(notifications.getNotification(AndroidSleepTimerNotifier.ID_RUNNING))
        val done = notifications.getNotification(AndroidSleepTimerNotifier.ID_RESULT)
        assertEquals(text(R.string.timer_notif_done), done.extras.getString("android.title"))
    }

    @Test fun deletedRemoteSendsNothingAndShowsError() = runBlocking {
        SleepTimerGraph.scheduler(app).start(30, "r1", SleepAction.PowerOff)
        RemoteStore(File(app.filesDir, "remotes.json")).delete("r1")
        assertEquals(FireResult.REMOTE_MISSING, SleepTimerReceiver.fire(app))
        assertEquals(emptyList<Int>(), tx.sent)
        val n = notifications.getNotification(AndroidSleepTimerNotifier.ID_RESULT)
        assertEquals(text(R.string.timer_notif_failed), n.extras.getString("android.title"))
        assertEquals(text(R.string.timer_fail_remote), n.extras.getCharSequence("android.text").toString())
    }

    @Test fun cancelActionRemovesAlarmAndTimer() {
        SleepTimerGraph.scheduler(app).start(30, "r1", SleepAction.PowerOff)
        SleepTimerReceiver().onReceive(app, Intent(app, SleepTimerReceiver::class.java).setAction(SleepTimerReceiver.ACTION_CANCEL))
        assertNull(alarms.peekNextScheduledAlarm())
        assertNull(SleepTimerGraph.store(app).load())
        assertNull(notifications.getNotification(AndroidSleepTimerNotifier.ID_RUNNING))
    }

    @Test fun withoutExactPermissionUsesInexactAlarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val timer = SleepTimerGraph.scheduler(app).start(15, "r1", SleepAction.PowerOff)
        assertEquals(false, timer.exact)
        assertEquals(timer.endAt, alarms.peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun bootReceiverRestoresPendingAndDiscardsExpired() {
        val prefs = app.getSharedPreferences(AndroidSleepTimerStore.PREFS, Context.MODE_PRIVATE)
        val store = AndroidSleepTimerStore(prefs)
        val future = System.currentTimeMillis() + 600_000L
        store.save(SleepTimer(future, "r1", SleepAction.RunAutomation("a1"), exact = true))
        SleepTimerBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(future, alarms.peekNextScheduledAlarm()!!.triggerAtTime)
        assertEquals(SleepAction.RunAutomation("a1"), store.load()?.action)

        SleepTimerGraph.reset()
        alarms.scheduledAlarms.clear()
        store.save(SleepTimer(System.currentTimeMillis() - 1, "r1", SleepAction.PowerOff, exact = true))
        SleepTimerBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(store.load())
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test fun notificationsDeniedTimerStillRuns() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        SleepTimerGraph.scheduler(app).start(30, "r1", SleepAction.PowerOff)
        assertNull(notifications.getNotification(AndroidSleepTimerNotifier.ID_RUNNING))
        assertTrue(alarms.peekNextScheduledAlarm() != null)
    }
}
