package de.hotelremote.quick

import de.hotelremote.Keys
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Belegung der Widget- und Benachrichtigungsknöpfe: Standard, Speicherformat und Auflösung Knopf → Signal. */
class QuickButtonsTest {
    private fun sig(n: Int) = IrSignal(freq = 38000, frame = listOf(n, 100, 200), periodMs = 100)

    private fun remote(vararg keys: Pair<String, Int>, automations: List<Automation> = emptyList()) = SavedRemote(
        id = "r1",
        brand = "LG",
        codeLabel = "Code 1",
        keys = keys.associate { (k, n) -> k to sig(n) },
        lastUsed = 1,
        automations = automations,
    )

    private val sleep60 = Automation("a1", "Sleep 60", listOf(AutomationStep(Keys.MENU, 2000), AutomationStep(Keys.OK)))

    @Test fun defaultIsPowerVolumeInput() {
        assertEquals(
            listOf(QuickButton.Key(Keys.POWER), QuickButton.Key(Keys.VOL_DOWN), QuickButton.Key(Keys.VOL_UP), QuickButton.Input),
            QuickButtons.DEFAULT,
        )
    }

    @Test fun tileActionsMapToButtons() {
        assertEquals(QuickButton.Key(Keys.POWER), QuickAction.POWER.button)
        assertEquals(QuickButton.Key(Keys.VOL_DOWN), QuickAction.VOL_DOWN.button)
        assertEquals(QuickButton.Key(Keys.VOL_UP), QuickAction.VOL_UP.button)
        assertEquals(QuickButton.Input, QuickAction.INPUT.button)
    }

    @Test fun encodeDecodeRoundTrip() {
        val buttons = listOf(QuickButton.Key("X:Netflix"), QuickButton.Input, QuickButton.Run("a1", "Sleep 60"), QuickButton.Key(Keys.HDMI2))
        assertEquals(buttons, QuickButtons.decodeList(QuickButtons.encodeList(buttons)))
        assertEquals(QuickButton.Run("a1", "Sleep 60"), QuickButtons.decode(QuickButtons.encode(QuickButton.Run("a1", "Sleep 60"))))
    }

    @Test fun decodeRejectsGarbage() {
        assertNull(QuickButtons.decodeList("kaputt"))
        assertNull(QuickButtons.decodeList("[]"))
        assertNull(QuickButtons.decode(null))
        assertNull(QuickButtons.decode("{\"type\":\"unbekannt\"}"))
    }

    @Test fun keyResolvesToItsSignal() {
        val r = remote(Keys.MUTE to 5, "X:Netflix" to 9)
        assertEquals(QuickPlan.Send(sig(5)), resolveQuickButton(QuickButton.Key(Keys.MUTE), r, null))
        assertEquals(QuickPlan.Send(sig(9)), resolveQuickButton(QuickButton.Key("X:Netflix"), r, null))
    }

    @Test fun missingKeyFails() {
        assertEquals(QuickPlan.Fail(QuickResult.KEY_MISSING), resolveQuickButton(QuickButton.Key(Keys.HDMI3), remote(Keys.HDMI1 to 1), null))
    }

    /** Ein/Aus wie bisher: fehlt POWER, dann wie ein kurzer Druck in der App (z. B. POWER_ON). */
    @Test fun powerFallsBackToShortPressLogic() {
        assertEquals(QuickPlan.Send(sig(2)), resolveQuickButton(QuickButton.Key(Keys.POWER), remote(Keys.POWER_ON to 2, Keys.POWER_OFF to 3), null))
        assertEquals(QuickPlan.Send(sig(1)), resolveQuickButton(QuickButton.Key(Keys.POWER), remote(Keys.POWER to 1, Keys.POWER_ON to 2), null))
        // POWER_ON gezielt gewählt: kein Ausweichen auf andere Tasten.
        assertEquals(QuickPlan.Fail(QuickResult.KEY_MISSING), resolveQuickButton(QuickButton.Key(Keys.POWER_ON), remote(Keys.POWER to 1), null))
    }

    @Test fun inputUsesChosenInputKey() {
        val r = remote(Keys.HDMI1 to 11, Keys.HDMI2 to 12, Keys.SOURCE to 20)
        assertEquals(QuickPlan.Send(sig(11)), resolveQuickButton(QuickButton.Input, r, null))
        assertEquals(QuickPlan.Send(sig(12)), resolveQuickButton(QuickButton.Input, r, Keys.HDMI2))
        assertEquals(QuickPlan.Fail(QuickResult.KEY_MISSING), resolveQuickButton(QuickButton.Input, remote(Keys.POWER to 1), null))
    }

    @Test fun automationById() {
        val r = remote(Keys.MENU to 1, Keys.OK to 2, automations = listOf(sleep60))
        assertEquals(QuickPlan.Run(sleep60, r.keys), resolveQuickButton(QuickButton.Run("a1", "alt"), r, null))
    }

    /** Andere Fernbedienung mit gleich benannter Automation (z. B. aus derselben Vorlage). */
    @Test fun automationByNameOnOtherRemote() {
        val other = sleep60.copy(id = "b7")
        val r = remote(Keys.MENU to 1, Keys.OK to 2, automations = listOf(other))
        assertEquals(QuickPlan.Run(other, r.keys), resolveQuickButton(QuickButton.Run("a1", "Sleep 60"), r, null))
    }

    @Test fun missingAutomationFails() {
        assertEquals(
            QuickPlan.Fail(QuickResult.AUTOMATION_MISSING),
            resolveQuickButton(QuickButton.Run("a1", "Sleep 60"), remote(Keys.MENU to 1), null),
        )
    }

    @Test fun automationWithoutAnyKeyOfThisRemoteFails() {
        val r = remote(Keys.POWER to 1, automations = listOf(sleep60))
        assertEquals(QuickPlan.Fail(QuickResult.KEY_MISSING), resolveQuickButton(QuickButton.Run("a1", "Sleep 60"), r, null))
    }

    /** Länger als 8 s (z. B. nach dem Einrichten verlängert): nicht ausführen. */
    @Test fun tooLongAutomationFails() {
        val long = Automation("a1", "Lang", listOf(AutomationStep(Keys.MENU, 5000), AutomationStep(Keys.OK, 3500), AutomationStep(Keys.OK)))
        val r = remote(Keys.MENU to 1, Keys.OK to 2, automations = listOf(long))
        assertEquals(QuickPlan.Fail(QuickResult.TOO_LONG), resolveQuickButton(QuickButton.Run("a1", "Lang"), r, null))
    }

    @Test fun fitsQuickControlsUpTo8Seconds() {
        assertTrue(fitsQuickControls(listOf(AutomationStep(Keys.MENU, 8000), AutomationStep(Keys.OK, 9000))))
        assertFalse(fitsQuickControls(listOf(AutomationStep(Keys.MENU, 8000), AutomationStep(Keys.OK, 500), AutomationStep(Keys.OK))))
    }

    @Test fun normalizeKeepsOneToMax() {
        val seven = List(7) { QuickButton.Key("DIGIT_$it") }
        assertEquals(6, QuickButtons.normalize(seven, QuickButtons.MAX_WIDGET).size)
        assertEquals(5, QuickButtons.normalize(seven, QuickButtons.MAX_NOTIFICATION).size)
        assertEquals(QuickButtons.DEFAULT, QuickButtons.normalize(emptyList(), QuickButtons.MAX_WIDGET))
    }
}
