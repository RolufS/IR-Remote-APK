package de.hotelremote.sleeptimer

import de.hotelremote.Keys
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.RemoteStorage
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.ir.IrTransmitter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

internal class FakeTransmitter(var fail: Boolean = false) : IrTransmitter {
    override val available = true
    val sent = mutableListOf<Int>()
    override fun transmit(freq: Int, pattern: IntArray) {
        if (fail) throw UnsupportedOperationException("Kein Infrarot-Sender")
        sent += pattern.first()
    }
}

internal class ListStorage(private val list: List<SavedRemote>) : RemoteStorage {
    override fun all() = list
    override fun add(r: SavedRemote) = Unit
    override fun rename(id: String, name: String?) = Unit
    override fun delete(id: String) = Unit
    override fun markUsed(id: String, now: Long) = Unit
    override fun updateAutomations(id: String, automations: List<Automation>) = Unit
}

private fun sig(n: Int) = IrSignal(freq = 38000, frame = listOf(n, 100, 200), periodMs = 100)

internal fun timerRemote(vararg keys: Pair<String, Int>, automations: List<Automation> = emptyList()) = SavedRemote(
    id = "r1",
    brand = "LG",
    codeLabel = "Code 1",
    keys = keys.associate { (k, n) -> k to sig(n) },
    lastUsed = 0,
    automations = automations,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerFirerTest {
    private val timerStore = FakeTimerStore()
    private val scheduler = SleepTimerScheduler(timerStore, FakeAlarms(), FakeNotifier()) { 0L }
    private val tx = FakeTransmitter()

    private fun firer(vararg remotes: SavedRemote) = SleepTimerFirer(scheduler, ListStorage(remotes.toList()), tx)

    @Test fun powerOffSendsDiscreteOff() = runTest {
        scheduler.start(15, "r1", SleepAction.PowerOff)
        val result = firer(timerRemote(Keys.POWER to 1, Keys.POWER_OFF to 2)).fire()
        assertEquals(FireResult.DONE, result)
        assertEquals(listOf(2), tx.sent)
        assertEquals(null, timerStore.saved)
    }

    @Test fun powerOffFallsBackToToggle() = runTest {
        scheduler.start(15, "r1", SleepAction.PowerOff)
        assertEquals(FireResult.DONE, firer(timerRemote(Keys.POWER to 1)).fire())
        assertEquals(listOf(1), tx.sent)
    }

    @Test fun automationRunsStepsWithDelays() = runTest {
        val a = Automation("a1", "Aus", listOf(AutomationStep(Keys.MENU, 1000), AutomationStep(Keys.OK, 3000)))
        scheduler.start(15, "r1", SleepAction.RunAutomation("a1"))
        assertEquals(FireResult.DONE, firer(timerRemote(Keys.MENU to 5, Keys.OK to 6, automations = listOf(a))).fire())
        assertEquals(listOf(5, 6), tx.sent)
        assertEquals(1000L, currentTime)
    }

    @Test fun deletedRemoteSendsNothing() = runTest {
        scheduler.start(15, "r1", SleepAction.PowerOff)
        assertEquals(FireResult.REMOTE_MISSING, firer().fire())
        assertEquals(emptyList<Int>(), tx.sent)
        assertEquals(null, timerStore.saved)
    }

    @Test fun deletedAutomationOrMissingKeyIsReported() = runTest {
        scheduler.start(15, "r1", SleepAction.RunAutomation("weg"))
        assertEquals(FireResult.ACTION_MISSING, firer(timerRemote(Keys.POWER to 1)).fire())
        scheduler.start(15, "r1", SleepAction.PowerOff)
        assertEquals(FireResult.ACTION_MISSING, firer(timerRemote(Keys.POWER_ON to 1)).fire())
        assertEquals(emptyList<Int>(), tx.sent)
    }

    @Test fun sendErrorIsReported() = runTest {
        tx.fail = true
        scheduler.start(15, "r1", SleepAction.PowerOff)
        assertEquals(FireResult.SEND_FAILED, firer(timerRemote(Keys.POWER to 1)).fire())
    }

    @Test fun cancelledTimerDoesNothing() = runTest {
        assertEquals(FireResult.NO_TIMER, firer(timerRemote(Keys.POWER to 1)).fire())
        assertEquals(emptyList<Int>(), tx.sent)
    }

    @Test fun tooLongAutomationIsCutOffAtBudget() = runTest {
        val a = Automation("a1", "Lang", List(5) { AutomationStep(Keys.OK, 3000) })
        scheduler.start(15, "r1", SleepAction.RunAutomation("a1"))
        assertEquals(FireResult.INCOMPLETE, firer(timerRemote(Keys.OK to 6, automations = listOf(a))).fire())
        assertEquals(3, tx.sent.size)
    }
}
