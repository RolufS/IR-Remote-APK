package de.hotelremote.ui.ac

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import de.hotelremote.data.AcState
import de.hotelremote.ui.AcAction
import de.hotelremote.ui.AcView
import de.hotelremote.ui.theme.HotelRemoteTheme
import de.hotelremote.weather.Place
import de.hotelremote.weather.Reading
import de.hotelremote.weather.WeatherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal fun acView(
    state: AcState = AcState(true, "cool", 22.0, "auto"),
    modes: List<String> = listOf("cool", "heat", "dry"),
    fans: List<String> = listOf("auto", "low", "high"),
    swings: List<String> = emptyList(),
    fahrenheit: Boolean = false,
    canWarmer: Boolean = true,
    canCooler: Boolean = true,
) = AcView(state, fahrenheit, modes, fans, swings, canWarmer, canCooler)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AcScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<AcAction>()
    private val holdSteps = mutableListOf<AcAction>()
    private var holdEnds = 0

    private fun show(view: AcView, climate: AcClimate? = null) {
        val panel = AcPanel(view, { actions += it }, { holdSteps += it }, { holdEnds++ }, climate = climate)
        rule.setContent { HotelRemoteTheme { AcScreen(panel) } }
    }

    @Test
    fun showsTemperatureModeAndFanWithoutSwing() {
        show(acView())
        rule.onNodeWithTag("ac_temp").assertTextEquals("22 °C")
        rule.onNodeWithTag("ac_mode_name").assertTextEquals("Kühlen")
        rule.onNodeWithTag("ac_details").assertTextEquals("Lüfter Auto")
        rule.onNodeWithTag("key_ac_swing").assertDoesNotExist()
        rule.onNodeWithTag("ac_off").assertDoesNotExist()
        rule.onNodeWithTag("ac_outdoor_slot").assertExists()
        rule.onNodeWithTag("ac_auto_slot").assertExists()
    }

    @Test
    fun swingFahrenheitAndHalfDegrees() {
        show(acView(AcState(true, "heat", 72.5, "Mid", "SWING"), swings = listOf("off", "SWING"), fahrenheit = true))
        rule.onNodeWithTag("ac_temp").assertTextEquals("72.5 °F")
        rule.onNodeWithTag("ac_details").assertTextEquals("Lüfter Mittel  ·  Swing An")
        rule.onNodeWithTag("key_ac_swing").assertIsEnabled().performClick()
        assertEquals(listOf<AcAction>(AcAction.CycleSwing), actions)
    }

    @Test
    fun unknownAndEmptyNamesShownSensibly() {
        show(acView(AcState(true, "ifeel", 24.0, ""), modes = listOf("ifeel"), fans = listOf("")))
        rule.onNodeWithTag("ac_mode_name").assertTextEquals("ifeel")
        rule.onNodeWithTag("ac_details").assertTextEquals("Lüfter Standard")
        rule.onNodeWithTag("key_ac_mode").assertIsNotEnabled()
        rule.onNodeWithTag("key_ac_fan").assertIsNotEnabled()
    }

    @Test
    fun offShowsHintAndKeysStillWork() {
        show(acView(AcState(false, "cool", 22.0, "auto")))
        rule.onNodeWithTag("ac_off").assertTextEquals("Ausgeschaltet")
        rule.onNodeWithTag("key_ac_warmer").performClick()
        rule.onNodeWithTag("key_ac_power").assertContentDescriptionEquals("Klimaanlage ein/aus").performClick()
        assertEquals(listOf<AcAction>(AcAction.Power), actions)
        assertEquals("ohne Wischbereich ist auch ein Tippen auf + ein kurzes Halten", listOf<AcAction>(AcAction.Warmer), holdSteps)
        assertEquals(1, holdEnds)
    }

    @Test
    fun modeMenuOffersModesAndFanCycles() {
        show(acView())
        rule.onNodeWithTag("key_ac_mode").performClick()
        rule.onNodeWithText("Entfeuchten").assertExists()
        rule.onNodeWithTag("ac_mode_heat").performClick()
        rule.onNodeWithTag("key_ac_fan").performClick()
        assertEquals(listOf(AcAction.SetMode("heat"), AcAction.CycleFan), actions)
    }

    @Test
    fun limitsDisableStepKeys() {
        show(acView(canWarmer = false))
        rule.onNodeWithTag("key_ac_warmer").assertIsNotEnabled()
        rule.onNodeWithTag("key_ac_cooler").assertIsEnabled()
    }

    @Test
    fun holdingPlusRepeatsUntilRelease() {
        show(acView())
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("key_ac_warmer").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(AC_HOLD_REPEAT_MS * 2 + 100)
        rule.onNodeWithTag("key_ac_warmer").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(AC_HOLD_REPEAT_MS * 3)
        assertEquals("sofort, nach 400 und nach 800 ms", List(3) { AcAction.Warmer }, holdSteps)
        assertEquals("Loslassen einmal gemeldet", 1, holdEnds)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun tapOnMinusIsOneStep() {
        show(acView())
        rule.onNodeWithTag("key_ac_cooler").performClick()
        rule.mainClock.advanceTimeBy(AC_HOLD_REPEAT_MS * 3)
        assertEquals(listOf<AcAction>(AcAction.Cooler), holdSteps)
        assertEquals(1, holdEnds)
    }

    private var outdoorTaps = 0
    private var autoTaps = 0
    private val now = 10_000_000L

    private fun climate(weather: WeatherSettings, running: Boolean = false, target: Double? = null, refreshing: Boolean = false) =
        AcClimate(weather, refreshing = refreshing, autoRunning = running, autoTarget = target,
            onOutdoor = { outdoorTaps++ }, onAuto = { autoTaps++ }, now = { now })

    @Test
    fun weatherOffShowsSubtleLinkToSetup() {
        show(acView(), climate(WeatherSettings()))
        rule.onNodeWithTag("ac_outdoor").assertDoesNotExist()
        rule.onNodeWithTag("ac_outdoor_link").assertTextEquals("Außentemperatur anzeigen").performClick()
        assertEquals(1, outdoorTaps)
    }

    @Test
    fun outdoorLineWithPlaceAgeAndSource() {
        show(acView(), climate(WeatherSettings(true, PLACE, Reading(31.2, now - 12 * 60_000L))))
        rule.onNodeWithTag("ac_outdoor_text", useUnmergedTree = true).assertTextEquals("Draußen 31 °C · Pattaya · vor 12 min")
        rule.onNodeWithText("Wetterdaten: Open-Meteo.com").assertExists()
        rule.onNodeWithTag("ac_outdoor").performClick()
        assertEquals(1, outdoorTaps)
    }

    @Test
    fun outdoorLineMarksOldValueAndUsesFahrenheit() {
        show(acView(fahrenheit = true), climate(WeatherSettings(true, PLACE, Reading(30.0, now - 3 * 60 * 60_000L))))
        rule.onNodeWithTag("ac_outdoor_text", useUnmergedTree = true).assertTextEquals("Draußen 86 °F · Pattaya · vor 3 h · veraltet")
    }

    @Test
    fun outdoorLineWithoutValueYet() {
        show(acView(), climate(WeatherSettings(true, PLACE), refreshing = true))
        rule.onNodeWithTag("ac_outdoor_text", useUnmergedTree = true).assertTextEquals("Draußen … · Pattaya")
    }

    @Test
    fun outdoorLineWhenUnreachable() {
        show(acView(), climate(WeatherSettings(true, PLACE)))
        rule.onNodeWithTag("ac_outdoor_text", useUnmergedTree = true).assertTextEquals("Wetter nicht erreichbar · Pattaya")
    }

    @Test
    fun autoKeyOpensSettingsAndShowsRunningTarget() {
        show(acView(), climate(WeatherSettings()))
        rule.onNodeWithTag("ac_auto_running", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("key_ac_auto").performClick()
        assertEquals(1, autoTaps)
    }

    @Test
    fun runningAutomationIsShown() {
        show(acView(), climate(WeatherSettings(true, PLACE), running = true, target = 26.0))
        rule.onNodeWithTag("ac_auto_running", useUnmergedTree = true).assertTextEquals("Läuft · 26 °C")
    }
}

private val PLACE = Place("Pattaya", 12.9, 100.9, "Thailand", "Chon Buri")
