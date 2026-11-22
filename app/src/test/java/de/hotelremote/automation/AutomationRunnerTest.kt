package de.hotelremote.automation

import de.hotelremote.data.AutomationStep
import de.hotelremote.ir.IrSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationRunnerTest {
    private fun sig(n: Int) = IrSignal(freq = 38000, frame = listOf(n), periodMs = 100)

    private val keys = mapOf("MENU" to sig(1), "DOWN" to sig(2), "OK" to sig(3))

    /** Gesendete Signale mit virtueller Zeit. */
    private val sent = mutableListOf<Pair<Int, Long>>()
    private val results = mutableListOf<AutomationResult>()

    private fun TestScope.runner() = AutomationRunner(
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        send = { sent += it.frame.single() to currentTime },
        onFinished = { results += it },
    )

    @Test fun sendsInOrderWithDelaysAndNoWaitAfterLast() = runTest {
        val r = runner()
        assertTrue(
            r.start("Folge", keys, listOf(AutomationStep("MENU", 1000), AutomationStep("DOWN", 500), AutomationStep("OK", 2000))),
        )
        runCurrent()
        assertEquals(AutomationProgress("Folge", step = 1, total = 3), r.progress.value)
        advanceUntilIdle()
        assertEquals(listOf(1 to 0L, 2 to 1000L, 3 to 1500L), sent)
        // Nach dem letzten Schritt keine Wartezeit: fertig zum Zeitpunkt des letzten Sendens.
        assertEquals(listOf(AutomationResult("Folge", missing = emptyList(), cancelled = false)), results)
        assertEquals(1500L, currentTime)
        assertNull(r.progress.value)
    }

    @Test fun progressCountsSteps() = runTest {
        val r = runner()
        r.start("Folge", keys, listOf(AutomationStep("MENU", 1000), AutomationStep("DOWN", 1000), AutomationStep("OK", 1000)))
        runCurrent()
        assertEquals(1, r.progress.value?.step)
        advanceTimeBy(1001)
        assertEquals(AutomationProgress("Folge", step = 2, total = 3), r.progress.value)
    }

    @Test fun missingKeyIsSkippedAndReported() = runTest {
        val r = runner()
        r.start("Folge", keys, listOf(AutomationStep("MENU", 1000), AutomationStep("X:Sleep", 3000), AutomationStep("OK", 500)))
        advanceUntilIdle()
        assertEquals(listOf(1 to 0L, 3 to 1000L), sent)
        assertEquals(listOf("X:Sleep"), results.single().missing)
    }

    @Test fun cancelStopsFurtherSends() = runTest {
        val r = runner()
        r.start("Folge", keys, listOf(AutomationStep("MENU", 1000), AutomationStep("DOWN", 1000), AutomationStep("OK", 1000)))
        runCurrent()
        advanceTimeBy(500)
        r.cancel()
        advanceUntilIdle()
        assertEquals(listOf(1 to 0L), sent)
        assertTrue(results.single().cancelled)
        assertNull(r.progress.value)
    }

    @Test fun onlyOneRunAtATime() = runTest {
        val r = runner()
        assertTrue(r.start("A", keys, listOf(AutomationStep("MENU", 1000), AutomationStep("OK", 1000))))
        runCurrent()
        assertFalse(r.start("B", keys, listOf(AutomationStep("DOWN", 0))))
        advanceUntilIdle()
        assertEquals(listOf(1, 3), sent.map { it.first })
        assertTrue(r.start("B", keys, listOf(AutomationStep("DOWN", 0))))
        advanceUntilIdle()
        assertEquals(listOf(1, 3, 2), sent.map { it.first })
    }

    @Test fun cancelledButUnfinishedRunBlocksNewStart() = runTest {
        val r = runner()
        r.start("A", keys, listOf(AutomationStep("MENU", 1000), AutomationStep("OK", 1000)))
        r.cancel()
        assertFalse(r.start("B", keys, listOf(AutomationStep("DOWN", 0), AutomationStep("OK", 0))))
        runCurrent()
        assertNull(r.progress.value)
        assertTrue(r.start("B", keys, listOf(AutomationStep("DOWN", 1000), AutomationStep("OK", 0))))
        runCurrent()
        assertEquals(AutomationProgress("B", 1, 2), r.progress.value)
        advanceUntilIdle()
        assertEquals(listOf(2, 3), sent.map { it.first })
    }

    @Test fun durationIgnoresLastDelay() {
        assertEquals(1500L, automationDurationMs(listOf(AutomationStep("A", 1000), AutomationStep("B", 500), AutomationStep("C", 9000))))
        assertEquals(0L, automationDurationMs(emptyList()))
    }
}
