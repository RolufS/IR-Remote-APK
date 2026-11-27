package de.hotelremote.ui

import de.hotelremote.data.AcState
import de.hotelremote.data.PLAIN_PATHS
import de.hotelremote.data.SWING_PATHS
import de.hotelremote.data.SavedAc
import de.hotelremote.data.pulsesOf
import de.hotelremote.data.testAc
import de.hotelremote.ir.IrSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcControllerTest {
    private val sent = mutableListOf<IrSignal>()
    private val changes = mutableListOf<Pair<String, AcState>>()

    /** Zustände, die gespeichert werden sollten. */
    private val stored = mutableListOf<AcState>()

    /** Klimaanlage wie in AppController: der gemeldete Zustand wird der neue gespeicherte. */
    private var ac: SavedAc = testAc()

    private fun TestScope.controller() = AcController(backgroundScope, { sent += it }, { id, s, store ->
        changes += id to s
        if (store) stored += s
        ac = ac.copy(state = s)
    })

    private fun AcController.does(vararg actions: AcAction) = actions.forEach { perform("r1", ac, it) }

    private fun sentPaths(paths: List<String> = PLAIN_PATHS) =
        sent.map { s -> paths.single { pulsesOf(paths, it) == s.frame } }

    @Test fun rapidChangesSendOnlyTheLastStateAfterDebounce() = runTest {
        val c = controller()
        c.does(AcAction.Warmer, AcAction.Warmer)
        advanceTimeBy(299)
        assertTrue(sent.isEmpty())
        c.does(AcAction.Cooler)
        advanceTimeBy(299)
        assertTrue("jede Änderung startet die Wartezeit neu", sent.isEmpty())
        runCurrent()
        advanceTimeBy(2)
        assertEquals(listOf("cool/auto/23"), sentPaths())
        assertEquals("nach jeder Änderung gespeichert", listOf(23.0, 24.0, 23.0), changes.map { it.second.temp })
        assertTrue(changes.all { it.first == "r1" })
    }

    @Test fun heldStepsOnlyDisplayReleaseStoresAndSendsOnceAfterDebounce() = runTest {
        ac = testAc(state = AcState(true, "cool", 20.0, "auto"))
        val c = controller()
        // + gehalten: Schritte bei 0, 400, 800 und 1200 ms.
        repeat(4) {
            c.perform("r1", ac, AcAction.Warmer, held = true)
            advanceTimeBy(400)
        }
        assertEquals(24.0, ac.state.temp, 0.0)
        assertEquals(4, changes.size)
        assertTrue("während des Haltens nichts gesendet", sent.isEmpty())
        assertTrue("und nichts gespeichert", stored.isEmpty())
        c.release("r1", ac)
        assertEquals(listOf(ac.state), stored)
        advanceTimeBy(299)
        assertTrue(sent.isEmpty())
        advanceTimeBy(2)
        assertEquals(listOf("cool/auto/24"), sentPaths())
        advanceTimeBy(1000)
        assertEquals(1, sent.size)
    }

    @Test fun tapAfterReleaseRestartsDebounce() = runTest {
        val c = controller()
        c.perform("r1", ac, AcAction.Warmer, held = true)
        c.release("r1", ac)
        advanceTimeBy(200)
        c.does(AcAction.Warmer)
        advanceTimeBy(299)
        assertTrue(sent.isEmpty())
        advanceTimeBy(2)
        assertEquals(listOf("cool/auto/24"), sentPaths())
    }

    @Test fun releaseWithoutChangeSendsNothing() = runTest {
        ac = testAc(state = AcState(true, "cool", 24.0, "auto"))
        val c = controller()
        c.perform("r1", ac, AcAction.Warmer, held = true)
        c.release("r1", ac)
        c.release("r1", ac)
        advanceTimeBy(1000)
        assertTrue(sent.isEmpty())
        assertTrue(stored.isEmpty())
    }

    @Test fun heldWhileOffIsStoredButNotSent() = runTest {
        ac = testAc(state = AcState(false, "cool", 22.0, "auto"))
        val c = controller()
        c.perform("r1", ac, AcAction.Cooler, held = true)
        c.release("r1", ac)
        advanceTimeBy(1000)
        assertTrue(sent.isEmpty())
        assertEquals(listOf(AcState(false, "cool", 21.0, "auto")), stored)
    }

    @Test fun tempStopsAtAvailableLimits() = runTest {
        val c = controller()
        repeat(5) { c.does(AcAction.Warmer) }
        assertEquals(24.0, ac.state.temp, 0.0)
        assertEquals("am Ende keine Änderung", 2, changes.size)
        repeat(10) { c.does(AcAction.Cooler) }
        assertEquals(20.0, ac.state.temp, 0.0)
        advanceTimeBy(400)
        assertEquals(listOf("cool/auto/20"), sentPaths())
    }

    @Test fun whileOffChangesAreOnlyRememberedPowerSendsOffAndRememberedState() = runTest {
        val c = controller()
        c.does(AcAction.Power)
        assertEquals("aus sofort", listOf("off"), sentPaths())
        c.does(AcAction.Warmer, AcAction.SetMode("heat"))
        advanceTimeBy(1000)
        assertEquals(listOf("off"), sentPaths())
        assertFalse(ac.state.power)
        assertEquals(AcState(false, "heat", 20.0, "auto"), ac.state)
        c.does(AcAction.Power)
        assertEquals("ein sofort mit gemerktem Zustand", listOf("off", "heat/auto/20"), sentPaths())
    }

    @Test fun powerOffCancelsPendingChange() = runTest {
        val c = controller()
        c.does(AcAction.Warmer, AcAction.Power)
        advanceTimeBy(1000)
        assertEquals(listOf("off"), sentPaths())
    }

    @Test fun fanCyclesPerModeAndSnapsTemperature() = runTest {
        ac = testAc(state = AcState(true, "cool", 20.0, "auto"))
        val c = controller()
        c.does(AcAction.CycleFan)
        assertEquals("low hat nur 22 und 23", AcState(true, "cool", 22.0, "low"), ac.state)
        c.does(AcAction.CycleFan)
        assertEquals("auto", ac.state.fan)
        c.does(AcAction.SetMode("fan_only"))
        assertEquals("Lüfter gerastet, Rohname bleibt", AcState(true, "fan_only", 24.0, "High"), ac.state)
        c.does(AcAction.CycleFan)
        assertEquals("nur ein Lüfter: keine Änderung", "High", ac.state.fan)
        advanceTimeBy(400)
        assertEquals(listOf("fan_only/High/24"), sentPaths())
    }

    @Test fun modeChangeSnapsFanAndTemp() = runTest {
        ac = testAc(state = AcState(true, "fan_only", 24.0, "High"))
        val c = controller()
        c.does(AcAction.SetMode("heat"))
        assertEquals(AcState(true, "heat", 20.0, "auto"), ac.state)
        c.does(AcAction.SetMode("unbekannt"))
        assertEquals("unbekannter Modus rastet auf den ersten", "cool", ac.state.mode)
    }

    @Test fun swingCyclesAndHalfDegreeSteps() = runTest {
        ac = testAc(SWING_PATHS, AcState(true, "cool", 22.0, "auto", "off"))
        val c = controller()
        c.does(AcAction.CycleSwing)
        assertEquals("swing", ac.state.swing)
        c.does(AcAction.Warmer)
        assertEquals(22.5, ac.state.temp, 0.0)
        c.does(AcAction.CycleSwing)
        assertEquals("off", ac.state.swing)
        advanceTimeBy(400)
        assertEquals(listOf("cool/auto/off/22.5"), sentPaths(SWING_PATHS))
        c.does(AcAction.SetMode("heat"))
        assertEquals(AcState(true, "heat", 20.0, "auto", "off"), ac.state)
    }

    @Test fun swingIgnoredWithoutSwingLevel() = runTest {
        val c = controller()
        c.does(AcAction.CycleSwing)
        assertTrue(changes.isEmpty())
        assertNull(ac.state.swing)
    }

    @Test fun invalidStoredStateIsSnappedBeforeAction() = runTest {
        ac = testAc(state = AcState(true, "COOL", 21.4, "Auto", swing = "on"))
        val c = controller()
        c.does(AcAction.Warmer)
        assertEquals(AcState(true, "cool", 22.0, "auto"), ac.state)
    }

    @Test fun viewOffersOnlyExistingCombinations() = runTest {
        ac = testAc(state = AcState(false, "cool", 24.0, "auto"))
        val v = controller().view(ac)!!
        assertEquals(listOf("cool", "heat", "fan_only"), v.modes)
        assertEquals(listOf("auto", "low"), v.fans)
        assertTrue(v.swings.isEmpty())
        assertFalse(v.canWarmer)
        assertTrue(v.canCooler)
        assertFalse(v.fahrenheit)

        val sw = controller().view(testAc(SWING_PATHS, AcState(true, "cool", 23.0, "auto", "swing")))!!
        assertEquals(listOf("off", "swing"), sw.swings)
    }

    @Test fun fahrenheitDeviceStepsByAvailableDegrees() = runTest {
        val paths = listOf("off") + (70..72).map { "cool/auto/$it" }
        ac = testAc(paths, AcState(true, "cool", 70.0, "auto"), maxTemp = 72.0)
        val c = controller()
        assertTrue(c.view(ac)!!.fahrenheit)
        c.does(AcAction.Warmer)
        assertEquals(71.0, ac.state.temp, 0.0)
        advanceTimeBy(400)
        assertEquals(listOf("cool/auto/71"), sentPaths(paths))
    }

    @Test fun unreadableCommandsDoNothing() = runTest {
        ac = testAc().copy(commands = "kein base64!")
        val c = controller()
        assertNull(c.view(ac))
        c.does(AcAction.Power)
        assertTrue(changes.isEmpty())
        assertTrue(sent.isEmpty())
    }

    @Test fun pendingSendOfOneRoomSurvivesActionInAnother() = runTest {
        val c = controller()
        c.perform("r1", ac, AcAction.Warmer)
        val other = testAc(state = AcState(true, "heat", 18.0, "auto"))
        c.perform("r2", other, AcAction.Warmer)
        advanceTimeBy(400)
        assertEquals(listOf("cool/auto/23", "heat/auto/19"), sentPaths())
    }
}
