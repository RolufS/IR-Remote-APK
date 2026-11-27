package de.hotelremote.ui.wizard

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.hotelremote.data.AcBrand
import de.hotelremote.data.AcState
import de.hotelremote.data.testAc
import de.hotelremote.ui.DeviceKind
import de.hotelremote.ui.theme.HotelRemoteTheme
import de.hotelremote.wizard.AcWizardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AcWizardScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val brands = listOf(
        AcBrand(1, "Acson", 3),
        AcBrand(2, "LG", 14, commonRank = 2),
        AcBrand(3, "Daikin", 28, commonRank = 0),
    )
    private val picked = mutableListOf<Long?>()
    private val answers = mutableListOf<Boolean>()
    private val saved = mutableListOf<String?>()
    private var tests = 0
    private var backs = 0

    private fun show(state: AcWizardState, addingTo: String? = null, irAvailable: Boolean = true, stepSent: Boolean = false) {
        rule.setContent {
            HotelRemoteTheme {
                AcWizardScreen(
                    state = state,
                    brands = brands,
                    irAvailable = irAvailable,
                    onPickBrand = { picked += it },
                    onTest = { tests++ },
                    onAnswer = { answers += it },
                    onSave = { saved += it },
                    onBack = { backs++ },
                    addingTo = addingTo,
                    stepSent = stepSent,
                )
            }
        }
    }

    private val test = AcWizardState.TestCode(
        brandId = 3, index = 2, total = 28, deviceId = 7, brandName = "Daikin",
        testState = AcState(power = true, mode = "cool", temp = 24.0, fan = "auto"), fahrenheit = false,
    )

    @Test fun deviceChoiceOffersTvAndAc() {
        val chosen = mutableListOf<DeviceKind>()
        var language = 0
        rule.setContent {
            HotelRemoteTheme { DeviceChoiceScreen(onChoose = { chosen += it }, onBack = {}, onLanguage = { language++ }) }
        }
        rule.onNodeWithText("Was möchtest du einrichten?").assertExists()
        rule.onNodeWithTag("choose_tv").assertTextEquals("Fernseher").performClick()
        rule.onNodeWithTag("choose_ac").assertTextEquals("Klimaanlage").performClick()
        rule.onNodeWithTag("wizard_language").performClick()
        assertEquals(listOf(DeviceKind.TV, DeviceKind.AC), chosen)
        assertEquals(1, language)
    }

    @Test fun brandPickShowsCommonBrandsFirstAndAll() {
        show(AcWizardState.PickBrand)
        rule.onNodeWithText("Welche Marke steht auf der Klimaanlage?").assertExists()
        rule.onNodeWithText("HÄUFIGE MARKEN").assertExists()
        // Daikin und LG je in „Häufig“ und „Alle“, Acson nur in „Alle“
        assertEquals(2, rule.onAllNodesWithText("Daikin").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("Acson").fetchSemanticsNodes().size)
        val daikin = rule.onAllNodesWithText("Daikin").fetchSemanticsNodes().first().boundsInRoot.top
        val lg = rule.onAllNodesWithText("LG").fetchSemanticsNodes().first().boundsInRoot.top
        val acson = rule.onAllNodesWithText("Acson").fetchSemanticsNodes().first().boundsInRoot.top
        assertTrue(daikin < lg && lg < acson)
        assertEquals(2, rule.onAllNodesWithText("Geräte: 28").fetchSemanticsNodes().size)
        rule.onAllNodesWithText("LG")[0].performClick()
        assertEquals(listOf<Long?>(2), picked)
    }

    @Test fun brandSearchFiltersAndUnknownBrandTriesAll() {
        show(AcWizardState.PickBrand)
        rule.onNodeWithTag("ac_brand_search").performTextInput("acs")
        rule.onNodeWithText("Daikin").assertDoesNotExist()
        rule.onNodeWithText("Acson").performClick()
        rule.onNodeWithTag("ac_unknown_brand").assertTextEquals("Marke unbekannt → alle Geräte probieren").performClick()
        assertEquals(listOf<Long?>(1, null), picked)
    }

    @Test fun testStepShowsProgressStateAndSendAgain() {
        show(test)
        rule.onNodeWithText("Daikin · Code 3 von 28").assertExists()
        rule.onNodeWithTag("ac_test_state").assertTextEquals("Test: Ein · Kühlen · 24 °C · Lüfter Auto")
        rule.onNodeWithText("Hat die Klimaanlage reagiert (Piepen oder Anlaufen)?").assertExists()
        rule.onNodeWithTag("ac_send_again").assertDoesNotExist()
        rule.onNodeWithTag("big_test_button").assertContentDescriptionEquals("Senden").performClick()
        rule.onNodeWithTag("ac_send_again").assertTextEquals("Nochmal senden").performClick()
        assertEquals(2, tests)
        rule.onNodeWithText("Nein, nächster").performClick()
        rule.onNodeWithText("Ja").performClick()
        assertEquals(listOf(false, true), answers)
    }

    @Test fun autoSentStepOffersSendAgainAtOnce() {
        show(test, stepSent = true)
        rule.onNodeWithTag("ac_send_again").assertTextEquals("Nochmal senden").performClick()
        assertEquals(1, tests)
    }

    @Test fun fahrenheitTestState() {
        show(test.copy(testState = AcState(true, "dry", 75.0, "low"), fahrenheit = true))
        rule.onNodeWithTag("ac_test_state").assertTextEquals("Test: Ein · Entfeuchten · 75 °F · Lüfter Niedrig")
    }

    @Test fun confirmOffAsksIfItWentOff() {
        show(AcWizardState.ConfirmOff(3, 2, 28, 7))
        rule.onNodeWithText("Zur Bestätigung: tippe auf Senden, die App sendet jetzt „Aus“.").assertExists()
        rule.onNodeWithTag("big_test_button").performClick()
        assertEquals(1, tests)
        rule.onNodeWithText("Ist die Klimaanlage ausgegangen?").assertExists()
        rule.onNodeWithText("Ja").performClick()
        rule.onNodeWithText("Nein, nächster").performClick()
        assertEquals(listOf(true, false), answers)
    }

    @Test fun saveNewRoomWithName() {
        show(AcWizardState.Save(testAc()))
        rule.onNodeWithText("Gefunden: Acme, AC-1").assertExists()
        rule.onNodeWithTag("wizard_adding_to").assertDoesNotExist()
        rule.onNodeWithTag("wizard_name").performTextInput("  Zimmer 7 ")
        rule.onNodeWithTag("ac_save").performClick()
        assertEquals(listOf<String?>("Zimmer 7"), saved)
    }

    @Test fun saveNewRoomWithoutName() {
        show(AcWizardState.Save(testAc()))
        rule.onNodeWithTag("ac_save").performClick()
        assertEquals(listOf<String?>(null), saved)
    }

    @Test fun saveIntoExistingRoomHasNoNameField() {
        show(AcWizardState.Save(testAc()), addingTo = "Zimmer 1")
        rule.onNodeWithTag("wizard_name").assertDoesNotExist()
        rule.onNodeWithTag("wizard_adding_to").assertTextEquals("Wird zu „Zimmer 1“ hinzugefügt.")
        rule.onNodeWithTag("ac_save").performClick()
        assertEquals(listOf<String?>(null), saved)
    }

    @Test fun noMatchGoesBackToBrandPick() {
        show(AcWizardState.NoMatch(3))
        rule.onNodeWithText("Keine passende Klimaanlage gefunden.").assertExists()
        rule.onNode(hasText("Andere Marke wählen")).performClick()
        assertEquals(1, backs)
    }

    @Test fun noIrBanner() {
        show(AcWizardState.PickBrand, irAvailable = false)
        rule.onNodeWithTag("no_ir_banner").assertExists()
    }
}
