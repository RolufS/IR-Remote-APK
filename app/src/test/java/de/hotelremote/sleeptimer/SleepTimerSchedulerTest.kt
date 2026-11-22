package de.hotelremote.sleeptimer

import de.hotelremote.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeTimerStore : SleepTimerStore {
    var saved: SleepTimer? = null
    override fun load() = saved
    override fun save(timer: SleepTimer) {
        saved = timer
    }
    override fun clear() {
        saved = null
    }
}

internal class FakeAlarms(var exactAllowed: Boolean = true) : AlarmScheduler {
    val calls = mutableListOf<String>()
    override fun canScheduleExact() = exactAllowed
    override fun scheduleExact(atMillis: Long) {
        calls += "exact@$atMillis"
    }
    override fun scheduleInexact(atMillis: Long) {
        calls += "inexact@$atMillis"
    }
    override fun cancel() {
        calls += "cancel"
    }
}

internal class FakeNotifier : SleepTimerNotifier {
    var running: SleepTimer? = null
    override fun showRunning(timer: SleepTimer) {
        running = timer
    }
    override fun cancelRunning() {
        running = null
    }
}

class SleepTimerSchedulerTest {
    private var now = 1_000_000L
    private val store = FakeTimerStore()
    private val alarms = FakeAlarms()
    private val notifier = FakeNotifier()
    private fun scheduler() = SleepTimerScheduler(store, alarms, notifier) { now }

    @Test fun startSchedulesExactAlarmAndSavesTimer() {
        val s = scheduler()
        val t = s.start(30, "r1", SleepAction.PowerOff)
        val end = now + 30 * 60_000L
        assertEquals(SleepTimer(end, "r1", SleepAction.PowerOff, exact = true), t)
        assertEquals(listOf("exact@$end"), alarms.calls)
        assertEquals(t, store.saved)
        assertEquals(t, s.state.value)
        assertEquals(t, notifier.running)
    }

    @Test fun withoutExactPermissionFallsBackToInexact() {
        alarms.exactAllowed = false
        val t = scheduler().start(15, "r1", SleepAction.RunAutomation("a1"))
        assertFalse(t.exact)
        assertEquals(listOf("inexact@${now + 15 * 60_000L}"), alarms.calls)
    }

    @Test fun exactSecurityExceptionFallsBackToInexact() {
        val throwing = object : AlarmScheduler by alarms {
            override fun scheduleExact(atMillis: Long) = throw SecurityException("verweigert")
        }
        val t = SleepTimerScheduler(store, throwing, notifier) { now }.start(15, "r1", SleepAction.PowerOff)
        assertFalse(t.exact)
        assertEquals(listOf("inexact@${now + 15 * 60_000L}"), alarms.calls)
    }

    @Test fun minutesOutsideRangeAreRejected() {
        val s = scheduler()
        assertTrue(runCatching { s.start(0, "r1", SleepAction.PowerOff) }.isFailure)
        assertTrue(runCatching { s.start(481, "r1", SleepAction.PowerOff) }.isFailure)
        s.start(480, "r1", SleepAction.PowerOff)
    }

    @Test fun cancelRemovesAlarmTimerAndNotification() {
        val s = scheduler()
        s.start(30, "r1", SleepAction.PowerOff)
        s.cancel()
        assertEquals("cancel", alarms.calls.last())
        assertNull(store.saved)
        assertNull(s.state.value)
        assertNull(notifier.running)
    }

    @Test fun restoreAfterBootReschedulesPendingTimer() {
        store.saved = SleepTimer(now + 60_000L, "r1", SleepAction.PowerOff, exact = true)
        val s = scheduler()
        s.restoreAfterBoot()
        assertEquals(listOf("exact@${now + 60_000L}"), alarms.calls)
        assertEquals(store.saved, notifier.running)
        assertEquals(store.saved, s.state.value)
    }

    @Test fun restoreAfterBootDiscardsExpiredTimer() {
        store.saved = SleepTimer(now - 1, "r1", SleepAction.PowerOff, exact = true)
        val s = scheduler()
        s.restoreAfterBoot()
        assertEquals(listOf("cancel"), alarms.calls)
        assertNull(store.saved)
        assertNull(s.state.value)
        assertNull(notifier.running)
    }

    @Test fun upgradeToExactWhenPermissionGrantedLater() {
        alarms.exactAllowed = false
        val s = scheduler()
        val t = s.start(30, "r1", SleepAction.PowerOff)
        alarms.exactAllowed = true
        s.upgradeToExact()
        assertEquals("exact@${t.endAt}", alarms.calls.last())
        assertTrue(s.state.value!!.exact)
        assertTrue(store.saved!!.exact)
        s.upgradeToExact()
        assertEquals(2, alarms.calls.size)
    }

    @Test fun takeDueClearsTimerOnce() {
        val s = scheduler()
        val t = s.start(30, "r1", SleepAction.PowerOff)
        assertEquals(t, s.takeDue())
        assertNull(s.takeDue())
        assertNull(store.saved)
        assertNull(s.state.value)
        assertNull(notifier.running)
    }

    @Test fun powerOffPrefersDiscreteOff() {
        assertEquals(Keys.POWER_OFF, powerOffKey(setOf(Keys.POWER, Keys.POWER_OFF)))
        assertEquals(Keys.POWER, powerOffKey(setOf(Keys.POWER, Keys.POWER_ON)))
        assertNull(powerOffKey(setOf(Keys.POWER_ON)))
        assertTrue(onlyPowerToggle(setOf(Keys.POWER)))
        assertFalse(onlyPowerToggle(setOf(Keys.POWER, Keys.POWER_OFF)))
    }
}
