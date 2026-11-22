package de.hotelremote.ir

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignalSenderTest {

    private class FakeTx(private val failWith: Throwable? = null) : IrTransmitter {
        override val available = true
        val calls = mutableListOf<Pair<Int, List<Int>>>()
        override fun transmit(freq: Int, pattern: IntArray) {
            calls += freq to pattern.toList()
            failWith?.let { throw it }
        }
    }

    private val nec = IrSignal(38000, listOf(9000, 4500, 560), repeat = listOf(9000, 2250, 560), periodMs = 108)
    private val rc5 = IrSignal(36000, listOf(889, 889, 1778), frameAlt = listOf(1778, 889, 889), periodMs = 114)

    private fun TestScope.sender(tx: IrTransmitter, errors: MutableList<Throwable> = mutableListOf()): SignalSender {
        val d = StandardTestDispatcher(testScheduler)
        return SignalSender(tx, CoroutineScope(d), d) { errors += it }
    }

    @Test fun pressSendsFrame() = runTest {
        val tx = FakeTx()
        sender(tx).press(nec)
        runCurrent()
        assertEquals(listOf(38000 to nec.frame), tx.calls)
    }

    @Test fun rapidPressesAreSerializedAndToggleAlternates() = runTest {
        val tx = FakeTx()
        val s = sender(tx)
        repeat(3) { s.press(rc5) }
        runCurrent()
        assertEquals(listOf(rc5.frame, rc5.frameAlt, rc5.frame), tx.calls.map { it.second })
    }

    @Test fun holdRepeatsAfterDelayThenEveryPeriod() = runTest {
        val tx = FakeTx()
        val job = sender(tx).hold(nec)
        runCurrent()
        advanceTimeBy(400 + 108 * 2 + 1)
        runCurrent()
        assertEquals(listOf(nec.frame, nec.repeat, nec.repeat, nec.repeat), tx.calls.map { it.second })
        job.cancel()
        val n = tx.calls.size
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(n, tx.calls.size)
    }

    @Test fun holdRepeatsKeepToggle() = runTest {
        val tx = FakeTx()
        val s = sender(tx)
        s.press(rc5) // frame, next is frameAlt
        val job = s.hold(rc5)
        runCurrent()
        advanceTimeBy(400 + 114 * 2 + 1)
        runCurrent()
        job.cancel()
        assertEquals(listOf(rc5.frame) + List(4) { rc5.frameAlt }, tx.calls.map { it.second })
    }

    @Test fun transmitErrorIsReportedNotThrown() = runTest {
        val errors = mutableListOf<Throwable>()
        val bad = FakeTx(IllegalArgumentException("x"))
        val s = sender(bad, errors)
        s.press(nec)
        runCurrent()
        assertEquals(1, errors.size)
        s.press(nec)
        runCurrent()
        assertEquals(2, bad.calls.size)
    }
}
