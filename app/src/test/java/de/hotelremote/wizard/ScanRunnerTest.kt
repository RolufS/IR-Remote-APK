package de.hotelremote.wizard

import de.hotelremote.Keys
import de.hotelremote.data.ScanEntry
import de.hotelremote.ir.IrSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun sig(id: Int) = IrSignal(freq = 38000, frame = listOf(id), periodMs = 100)

private class ScanData : WizardData {
    val scan = (0 until 5).map { ScanEntry(it, 100L + it, 1, "Marke", sig(it)) }
    override fun codeSetsForBrand(brandId: Long) = emptyList<Long>()
    override fun keys(codeSetId: Long) = mapOf(Keys.POWER to sig(codeSetId.toInt()))
    override fun inputVariants(codeSetId: Long, brandId: Long) = emptyList<Map<String, IrSignal>>()
    override fun scanEntries() = scan
    override fun brandName(brandId: Long) = "Marke"
    override fun powerSiblings(codeSetId: Long) = emptyList<Long>()
    override fun topBrand(codeSetId: Long): Long? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScanRunnerTest {
    private fun machine() = WizardMachine(ScanData()).also { it.startScan(0) }

    @Test fun sendsOnePerInterval() = runTest {
        val sent = mutableListOf<IrSignal>()
        val runner = ScanRunner(machine(), { sent += it }, backgroundScope)
        runner.start()
        runCurrent()
        assertEquals(1, sent.size)
        advanceTimeBy(1200 * 3 + 1)
        assertEquals(listOf(0, 1, 2, 3), sent.map { it.frame[0] })
    }

    @Test fun stopMovesToVerify() = runTest {
        val sent = mutableListOf<IrSignal>()
        val m = machine()
        val runner = ScanRunner(m, { sent += it }, backgroundScope)
        runner.start()
        advanceTimeBy(2500)
        runner.stop()
        assertTrue(m.state is WizardState.ScanVerify)
        val count = sent.size
        advanceTimeBy(10_000)
        assertEquals(count, sent.size)
        assertFalse(runner.running.value)
    }

    @Test fun quickStopThenStartKeepsRunning() = runTest {
        val m = machine()
        val runner = ScanRunner(m, {}, backgroundScope)
        runner.start()
        runCurrent()
        runner.stop()
        m.startScan(0)
        runner.start()
        runCurrent()
        assertTrue(runner.running.value)
        advanceTimeBy(1201)
        assertTrue(runner.running.value)
        assertEquals(1, (m.state as WizardState.Scanning).position)
    }

    @Test fun notifiesStateChanges() = runTest {
        val m = machine()
        val positions = mutableListOf<Int>()
        val runner = ScanRunner(m, {}, backgroundScope, onStateChanged = {
            (m.state as? WizardState.Scanning)?.let { positions += it.position }
        })
        runner.start()
        advanceTimeBy(1200 * 2 + 1)
        assertEquals(listOf(1, 2), positions)
    }

    @Test fun runsToEndAndStops() = runTest {
        val sent = mutableListOf<IrSignal>()
        val m = machine()
        val runner = ScanRunner(m, { sent += it }, this)
        runner.start()
        advanceUntilIdle()
        assertEquals(5, sent.size)
        assertTrue(m.state is WizardState.NoMatch)
        assertFalse(runner.running.value)
    }
}
