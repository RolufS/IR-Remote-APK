package de.hotelremote.acauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeAcAutoStore : AcAutoStore {
    var saved: AcAutoRun? = null
    override fun load() = saved
    override fun save(run: AcAutoRun) {
        saved = run
    }
    override fun clear() {
        saved = null
    }
}

internal class FakeAcAutoAlarm : AcAutoAlarm {
    val calls = mutableListOf<String>()
    override fun schedule(atMillis: Long) {
        calls += "at@$atMillis"
    }
    override fun cancel() {
        calls += "cancel"
    }
}

internal class FakeAcAutoNotifier : AcAutoNotifier {
    var running: AcAutoRun? = null
    override fun showRunning(run: AcAutoRun) {
        running = run
    }
    override fun cancelRunning() {
        running = null
    }
}

class AcAutoSchedulerTest {
    private var now = 1_000_000L
    private val store = FakeAcAutoStore()
    private val alarm = FakeAcAutoAlarm()
    private val notifier = FakeAcAutoNotifier()
    private fun scheduler() = AcAutoScheduler(store, alarm, notifier) { now }
    private val interval = AcAutoScheduler.INTERVAL_MS

    @Test fun startPlansEvery30MinutesAndShowsNotification() {
        val s = scheduler()
        val run = s.start("r1")
        assertEquals(AcAutoRun("r1", now + interval), run)
        assertEquals(listOf("at@${now + interval}"), alarm.calls)
        assertEquals(run, store.saved)
        assertEquals(run, s.state.value)
        assertEquals(run, notifier.running)
    }

    @Test fun stopCancelsAlarmAndNotification() {
        val s = scheduler()
        s.start("r1")
        s.stop()
        assertEquals("cancel", alarm.calls.last())
        assertNull(store.saved)
        assertNull(s.state.value)
        assertNull(notifier.running)
    }

    @Test fun stopRoomOnlyStopsThatRoom() {
        val s = scheduler()
        s.start("r1")
        assertFalse(s.stopRoom("r2"))
        assertEquals("r1", store.saved?.roomId)
        assertTrue(s.stopRoom("r1"))
        assertNull(store.saved)
    }

    @Test fun finishedStoresResultAndPlansNext() {
        val s = scheduler()
        s.start("r1")
        now += interval
        val next = s.finished("r1") { it.copy(outdoorC = 31.0, target = 26.0) }!!
        assertEquals(AcAutoRun("r1", now + interval, outdoorC = 31.0, target = 26.0), next)
        assertEquals("at@${now + interval}", alarm.calls.last())
        assertEquals(next, notifier.running)
        // Inzwischen anderer Raum bzw. gestoppt: nichts übernehmen.
        assertNull(s.finished("r2") { it })
        s.stop()
        assertNull(s.finished("r1") { it })
        assertNull(store.saved)
    }

    @Test fun rearmKeepsPlannedTimeOrCatchesUpAtOnce() {
        val s = scheduler()
        s.start("r1")
        alarm.calls.clear()
        s.rearm()
        assertEquals(listOf("at@${now + interval}"), alarm.calls)
        // Alarm verloren (Neustart, Beenden erzwingen) und überfällig: sofort nachholen.
        now += 3 * interval
        s.restoreAfterBoot()
        assertEquals("at@$now", alarm.calls.last())
        assertEquals(now, store.saved?.nextAt)
        assertEquals(store.saved, notifier.running)
    }

    @Test fun rearmWithoutRunDoesNothing() {
        val s = scheduler()
        s.rearm()
        assertTrue(alarm.calls.isEmpty())
        assertNull(notifier.running)
    }

    @Test fun startInOtherRoomReplacesRun() {
        val s = scheduler()
        s.start("r1")
        s.start("r2")
        assertEquals("r2", store.saved?.roomId)
    }
}
