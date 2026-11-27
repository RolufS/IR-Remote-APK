package de.hotelremote.ui.ac

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import de.hotelremote.acauto.AcAutoProblem
import de.hotelremote.acauto.AcAutoRun
import de.hotelremote.data.AcAutoSettings
import de.hotelremote.data.SavedAc
import de.hotelremote.data.testAc
import de.hotelremote.ui.WeatherUi
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

private val PATTAYA = Place("Pattaya", 12.9, 100.9, "Thailand", "Chon Buri")

/** Einrichtung der Außentemperatur und Einstellung der Klima-Automatik. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClimateScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<String>()
    private val settings = mutableListOf<AcAutoSettings>()

    private fun setup(weather: WeatherSettings, ui: WeatherUi = WeatherUi()) = rule.setContent {
        HotelRemoteTheme {
            WeatherSetupScreen(
                weather = weather,
                ui = ui,
                onEnabled = { events += "enabled:$it" },
                onSearch = { events += "search:$it" },
                onChoose = { events += "choose:${it.name}" },
                onBack = { events += "back" },
            )
        }
    }

    @Test fun setupOffExplainsWhatIsSentAndHasNoSearch() {
        setup(WeatherSettings())
        rule.onNodeWithText("Außentemperatur anzeigen").assertExists()
        rule.onNodeWithTag("weather_explain").assertTextEquals(
            "Die App fragt dafür Open-Meteo.com. Gesendet werden nur der Ortsname, den du suchst, die Koordinaten des " +
                "gewählten Orts und die App-Sprache – kein Standort, kein Konto. Solange das ausgeschaltet ist, baut die App keine Internetverbindung auf.",
        )
        rule.onNodeWithTag("weather_query").assertDoesNotExist()
        rule.onNodeWithTag("weather_source").assertTextEquals("Wetterdaten: Open-Meteo.com (CC BY 4.0)")
        rule.onNodeWithTag("weather_switch").performClick()
        rule.onNodeWithTag("weather_back").performClick()
        assertEquals(listOf("enabled:true", "back"), events)
    }

    @Test fun setupSearchShowsResultsWithCountryAndRegion() {
        setup(WeatherSettings(enabled = true), WeatherUi(results = listOf(PATTAYA, Place("Pattaya", 1.0, 2.0))))
        rule.onNodeWithTag("weather_query").performTextInput(" Pattaya ")
        rule.onNodeWithTag("weather_search").performClick()
        rule.onNodeWithText("Chon Buri, Thailand").assertExists()
        rule.onNodeWithTag("weather_result_1").performScrollTo().performClick()
        assertEquals(listOf("search:Pattaya", "choose:Pattaya"), events)
    }

    @Test fun setupShowsChosenPlaceNoResultsAndFailure() {
        setup(WeatherSettings(enabled = true, place = PATTAYA), WeatherUi(results = emptyList()))
        rule.onNodeWithTag("weather_place").assertTextEquals("Ort: Pattaya (Chon Buri, Thailand)")
        rule.onNodeWithTag("weather_no_results").assertTextEquals("Keine Treffer.")
        // Leere Suche sendet nichts.
        rule.onNodeWithTag("weather_search").performClick()
        assertTrue(events.isEmpty())
    }

    @Test fun setupSearchFailure() {
        setup(WeatherSettings(enabled = true), WeatherUi(searchFailed = true))
        rule.onNodeWithTag("weather_search_failed").assertTextEquals("Suche fehlgeschlagen. Bitte die Internetverbindung prüfen.")
    }

    private fun auto(ac: SavedAc = testAc(), weather: WeatherSettings = WeatherSettings(true, PATTAYA), run: AcAutoRun? = null) =
        rule.setContent {
            HotelRemoteTheme {
                AcAutoScreen(
                    roomId = "r1",
                    ac = ac,
                    fans = listOf("auto", "low"),
                    weather = weather,
                    run = run,
                    onSettings = { settings += it },
                    onStart = { events += "start" },
                    onStop = { events += "stop" },
                    onWeatherSetup = { events += "setup" },
                    onBack = { events += "back" },
                )
            }
        }

    @Test fun autoDefaultsPreviewAndSteppers() {
        auto()
        rule.onNodeWithTag("acauto_offset_value").assertTextEquals("5 °C")
        rule.onNodeWithTag("acauto_floor_value").assertTextEquals("25 °C")
        rule.onNodeWithTag("acauto_ceiling_value").assertDoesNotExist()
        // Ohne Wetterwert: Beispiel 33 °C wie in der Beschreibung.
        rule.onNodeWithTag("acauto_preview").assertTextEquals("Bei 33 °C draußen → 28 °C")
        rule.onNodeWithTag("acauto_hint").assertExists()
        rule.onNodeWithTag("acauto_offset_plus").performClick()
        rule.onNodeWithTag("acauto_floor_minus").performClick()
        rule.onNodeWithTag("acauto_ceiling_switch").performClick()
        rule.onNodeWithTag("acauto_fan").performScrollTo().performClick()
        assertEquals(
            listOf(
                AcAutoSettings(6.0, 25.0),
                AcAutoSettings(5.0, 24.0),
                AcAutoSettings(5.0, 25.0, notWarmerThan = 28.0),
                AcAutoSettings(5.0, 25.0, fan = "low"),
            ),
            settings,
        )
    }

    @Test fun autoPreviewUsesCurrentOutdoorAndLimits() {
        val ac = testAc().copy(auto = AcAutoSettings(10.0, 25.0, notWarmerThan = 26.0))
        auto(ac, WeatherSettings(true, PATTAYA, Reading(40.0, 0)))
        rule.onNodeWithTag("acauto_preview").assertTextEquals("Bei 40 °C draußen → 26 °C")
        rule.onNodeWithTag("acauto_ceiling_value").assertTextEquals("26 °C")
        rule.onNodeWithTag("acauto_offset_plus").assertIsNotEnabled()
        // Obergrenze nicht unter die Untergrenze und umgekehrt.
        rule.onNodeWithTag("acauto_floor_plus").performClick()
        rule.onNodeWithTag("acauto_ceiling_minus").performClick()
        assertEquals(listOf(AcAutoSettings(10.0, 26.0, 26.0), AcAutoSettings(10.0, 25.0, 25.0)), settings)
    }

    @Test fun autoFahrenheitDeviceShowsFahrenheit() {
        auto(testAc(maxTemp = 88.0))
        rule.onNodeWithTag("acauto_offset_value").assertTextEquals("9 °F")
        rule.onNodeWithTag("acauto_floor_value").assertTextEquals("77 °F")
        // 33 °C ≈ 91 °F − 9 → 82 °F
        rule.onNodeWithTag("acauto_preview").assertTextEquals("Bei 91 °F draußen → 82 °F")
    }

    @Test fun startWithoutWeatherGoesToSetupFirst() {
        auto(weather = WeatherSettings())
        rule.onNodeWithTag("acauto_needs_weather").assertExists()
        rule.onNodeWithTag("acauto_start").performScrollTo().performClick()
        assertEquals(listOf("setup"), events)
    }

    @Test fun startWithWeatherStarts() {
        auto()
        rule.onNodeWithTag("acauto_stop").assertDoesNotExist()
        rule.onNodeWithTag("acauto_start").performScrollTo().performClick()
        assertEquals(listOf("start"), events)
    }

    @Test fun runningHereShowsStatusAndStop() {
        auto(run = AcAutoRun("r1", 0, outdoorC = 31.0, target = 26.0, problem = AcAutoProblem.WEATHER))
        rule.onNodeWithTag("acauto_status")
            .assertTextEquals("Klima-Automatik läuft · Wetter nicht erreichbar · draußen 31 °C → 26 °C")
        rule.onNodeWithTag("acauto_stop").performScrollTo().performClick()
        assertEquals(listOf("stop"), events)
    }

    @Test fun runningElsewhereOffersStart() {
        auto(run = AcAutoRun("other", 0))
        rule.onNodeWithTag("acauto_status").assertDoesNotExist()
        rule.onNodeWithTag("acauto_start").assertExists()
    }
}
