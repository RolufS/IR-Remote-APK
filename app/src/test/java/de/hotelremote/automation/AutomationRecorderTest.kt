package de.hotelremote.automation

import de.hotelremote.Keys
import de.hotelremote.data.AutomationStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRecorderTest {
    private var now = 0L
    private val recorder = AutomationRecorder { now }

    private fun recordAt(ms: Long, key: String) {
        now = ms
        recorder.record(key)
    }

    @Test fun recordsKeysInOrderWithLastStepDefaultDelay() {
        recorder.start(emptyList())
        assertTrue(recorder.isRecording)
        recordAt(1_000, Keys.MENU)
        recordAt(3_000, Keys.DOWN)
        recordAt(4_000, Keys.OK)
        assertEquals(listOf(Keys.MENU, Keys.DOWN, Keys.OK), recorder.steps.value.map { it.key })
        assertEquals(AutomationStep.DEFAULT_DELAY_MS, recorder.steps.value.last().delayAfterMs)
    }

    @Test fun pauseBecomesDelayOfPreviousStepRoundedAndClamped() {
        recorder.start(emptyList())
        recordAt(0, Keys.MENU)
        recordAt(2_300, Keys.DOWN) // 2,3 s → 2,5 s
        recordAt(2_400, Keys.DOWN) // 0,1 s → 0,5 s
        recordAt(32_400, Keys.OK) // 30 s → 10 s
        recordAt(33_200, Keys.EXIT) // 0,8 s → 1,0 s
        assertEquals(
            listOf(2_500L, 500L, 10_000L, 1_000L, AutomationStep.DEFAULT_DELAY_MS),
            recorder.steps.value.map { it.delayAfterMs },
        )
    }

    @Test fun undoRemovesLastStepAndItBecomesLast() {
        recorder.start(emptyList())
        recordAt(0, Keys.MENU)
        recordAt(3_000, Keys.DOWN)
        recorder.undo()
        assertEquals(listOf(AutomationStep(Keys.MENU, AutomationStep.DEFAULT_DELAY_MS)), recorder.steps.value)
        recordAt(5_000, Keys.OK)
        assertEquals(listOf(5_000L, AutomationStep.DEFAULT_DELAY_MS), recorder.steps.value.map { it.delayAfterMs })
    }

    @Test fun appendsToExistingStepsWithoutTouchingThem() {
        val existing = listOf(AutomationStep(Keys.POWER, 4_000), AutomationStep(Keys.HDMI1, 2_000))
        recorder.start(existing)
        assertEquals(existing, recorder.steps.value)
        recordAt(10_000, Keys.MENU)
        recordAt(11_000, Keys.OK)
        assertEquals(existing + listOf(AutomationStep(Keys.MENU, 1_000), AutomationStep(Keys.OK)), recorder.steps.value)
        assertEquals(2, recorder.recordedCount)
        // Rückgängig entfernt nur Aufgenommenes, nie vorhandene Schritte.
        recorder.undo()
        recorder.undo()
        recorder.undo()
        assertEquals(existing, recorder.steps.value)
        assertEquals(0, recorder.recordedCount)
    }

    @Test fun finishReturnsStepsAndStops() {
        recorder.start(emptyList())
        recordAt(0, Keys.MENU)
        recordAt(1_000, Keys.OK)
        val steps = recorder.finish()
        assertEquals(listOf(AutomationStep(Keys.MENU, 1_000), AutomationStep(Keys.OK)), steps)
        assertFalse(recorder.isRecording)
        assertEquals(emptyList<AutomationStep>(), recorder.steps.value)
    }

    @Test fun cancelClearsAndIgnoresFurtherKeys() {
        recorder.start(listOf(AutomationStep(Keys.POWER)))
        recordAt(0, Keys.MENU)
        recorder.cancel()
        assertFalse(recorder.isRecording)
        assertEquals(emptyList<AutomationStep>(), recorder.steps.value)
        recordAt(1_000, Keys.OK)
        assertEquals(emptyList<AutomationStep>(), recorder.steps.value)
    }
}
