package de.hotelremote.ui.sleeptimer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.hotelremote.Keys
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.sleeptimer.SleepAction
import de.hotelremote.ui.theme.HotelRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleepTimerDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val sig = IrSignal(freq = 38000, frame = listOf(1, 2, 3), periodMs = 100)
    private val short = Automation("short", "Kurz", listOf(AutomationStep(Keys.MENU, 1000), AutomationStep(Keys.OK)))
    private val long = Automation("long", "Lang", List(3) { AutomationStep(Keys.OK, 5000) })

    private var started: TimerRequest? = null

    private fun show(vararg keys: String) {
        val remote = SavedRemote("r1", null, "LG", "Code 1", keys.associateWith { sig }, 0, listOf(short, long))
        rule.setContent { HotelRemoteTheme { SleepTimerDialog(remote, onStart = { started = it }, onDismiss = {}) } }
    }

    @Test fun toggleOnlyRemoteShowsHint() {
        show(Keys.POWER, Keys.MENU, Keys.OK)
        rule.onNodeWithTag("timer_toggle_hint").assertExists()
        rule.onNodeWithTag("timer_point_hint").assertExists()
        rule.onNodeWithTag("timer_preset_45").performClick()
        rule.onNodeWithTag("timer_start").performClick()
        assertEquals(TimerRequest(45, SleepAction.PowerOff), started)
    }

    @Test fun discreteOffHasNoToggleHint() {
        show(Keys.POWER, Keys.POWER_OFF)
        rule.onNodeWithTag("timer_toggle_hint").assertDoesNotExist()
    }

    @Test fun longAutomationIsNotSelectableAndShortOneIs() {
        show(Keys.POWER, Keys.MENU, Keys.OK)
        rule.onNodeWithTag("timer_action_long").assertIsNotEnabled()
        rule.onNodeWithTag("timer_action_short").assertIsEnabled().performClick()
        rule.onNodeWithTag("timer_toggle_hint").assertDoesNotExist()
        rule.onNodeWithTag("timer_start").performClick()
        assertEquals(TimerRequest(30, SleepAction.RunAutomation("short")), started)
    }

    @Test fun customMinutesAreValidated() {
        show(Keys.POWER)
        rule.onNodeWithTag("timer_custom").performClick()
        rule.onNodeWithTag("timer_start").assertIsNotEnabled()
        rule.onNodeWithTag("timer_custom_field").performTextInput("481")
        rule.onNodeWithTag("timer_start").assertIsNotEnabled()
        assertNull(started)
        assertEquals(7, parseMinutes("7"))
        assertNull(parseMinutes("0"))
    }

    @Test fun withoutPowerKeyOffIsDisabled() {
        show(Keys.POWER_ON, Keys.MENU, Keys.OK)
        rule.onNodeWithTag("timer_action_off").assertIsNotEnabled()
        rule.onNodeWithTag("timer_start").performClick()
        assertEquals(TimerRequest(30, SleepAction.RunAutomation("short")), started)
    }

    @Test fun remainingMinutesRoundsUp() {
        assertEquals(42, remainingMinutes(42 * 60_000L - 1, 0))
        assertEquals(1, remainingMinutes(1, 0))
        assertEquals(0, remainingMinutes(0, 10))
    }
}
