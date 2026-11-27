package de.hotelremote.ui.wizard

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.hotelremote.Keys
import de.hotelremote.data.Brand
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.ui.about.AboutScreen
import de.hotelremote.ui.theme.HotelRemoteTheme
import de.hotelremote.wizard.WizardState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WizardScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val brands = listOf(Brand(1, "Samsung", 1, 2))

    private val resumed = mutableListOf<Int>()
    private var started = 0
    private var languageOpened = 0

    private val saves = mutableListOf<Pair<String?, String>>()

    private fun show(
        state: WizardState,
        irAvailable: Boolean = true,
        saved: List<SavedRemote> = emptyList(),
        addingTo: String? = null,
    ) {
        rule.setContent {
            HotelRemoteTheme {
                WizardScreen(
                    state = state,
                    brands = brands,
                    irAvailable = irAvailable,
                    savedRemotes = saved,
                    onPickBrand = {}, onStartScan = { started++ }, onTest = {}, onAnswer = {}, onTvOff = {},
                    onStopScan = {}, onResumeScan = { resumed += it }, onSave = { n, b -> saves += n to b }, onBack = {},
                    onLanguage = { languageOpened++ },
                    addingTo = addingTo,
                )
            }
        }
    }

    @Test fun noIrBannerOnPickBrand() {
        show(WizardState.PickBrand, irAvailable = false)
        rule.onNodeWithTag("no_ir_banner").assertExists()
    }

    @Test fun noIrBannerOnTestStep() {
        show(WizardState.TestCode(1, 0, 2, 10, Keys.MUTE), irAvailable = false)
        rule.onNodeWithTag("no_ir_banner").assertExists()
    }

    @Test fun noBannerWithIr() {
        show(WizardState.PickBrand, irAvailable = true)
        rule.onNodeWithTag("no_ir_banner").assertDoesNotExist()
    }

    @Test fun dbErrorScreenShowsHintAndGoesBack() {
        var back = 0
        rule.setContent { HotelRemoteTheme { WizardDbError(onBack = { back++ }) } }
        rule.onNodeWithTag("db_error").assertExists()
        rule.onNodeWithText("Zurück").performClick()
        assertEquals(1, back)
    }

    @Test fun aboutShowsDbError() {
        rule.setContent { HotelRemoteTheme { AboutScreen(meta = emptyMap(), dbError = true, onBack = {}) } }
        rule.onNodeWithTag("db_error").assertExists()
    }

    @Test fun aboutShowsPrivacyNote() {
        rule.setContent { HotelRemoteTheme { AboutScreen(meta = emptyMap(), dbError = false, onBack = {}) } }
        rule.onNodeWithTag("about_privacy")
            .assertTextContains("Datenschutz: keine Daten, kein Internet.", substring = true)
            .assertTextContains("github.com/RolufS/IR-Remote-APK/blob/main/PRIVACY.md", substring = true)
    }

    private fun sig() = IrSignal(38000, listOf(1), periodMs = 100)

    @Test fun saveListsAlreadySavedRemotes() {
        val saved = SavedRemote("a", "Zimmer 1", "Samsung", "Code 1", emptyMap(), 0L)
        show(WizardState.Save("Samsung", "Code 2", mapOf(Keys.POWER to sig()), hasInputs = false), saved = listOf(saved))
        rule.onNodeWithText("Zimmer 1").assertExists()
    }

    @Test fun saveSoundCheckMissingWithoutSoundKeys() {
        show(WizardState.Save("Samsung", "Code 2", mapOf(Keys.POWER to sig()), hasInputs = false))
        rule.onNodeWithTag("check_sound").assertTextContains("✕", substring = true)
    }

    @Test fun saveSoundCheckOkWithVolumeKey() {
        show(WizardState.Save("Samsung", "Code 2", mapOf(Keys.POWER to sig(), Keys.VOL_DOWN to sig()), hasInputs = false))
        rule.onNodeWithTag("check_sound").assertTextContains("✔", substring = true)
    }

    @Test fun resumeScanGoesThroughIntro() {
        show(WizardState.NoMatch(null, canResumeScanAt = 4))
        rule.onNodeWithText("Suchlauf fortsetzen").performClick()
        assertEquals(emptyList<Int>(), resumed)
        rule.onNodeWithText("Der Fernseher muss eingeschaltet sein.").assertExists()
        rule.onNodeWithText("Suchlauf starten").performClick()
        assertEquals(listOf(4), resumed)
        assertEquals(0, started)
    }

    @Test fun powerTestButtonShowsIcon() {
        show(WizardState.TestCode(1, 0, 2, 10, Keys.POWER))
        rule.onNodeWithTag("big_test_button").assertContentDescriptionEquals("Ein/Aus")
        rule.onNodeWithText("Richte das Handy auf den Fernseher und tippe auf Ein/Aus.").assertExists()
    }

    @Test fun muteTestButtonShowsText() {
        show(WizardState.TestCode(1, 0, 2, 10, Keys.MUTE))
        rule.onNodeWithTag("big_test_button").assertTextContains("MUTE")
    }

    @Test fun scanVerifyShowsPowerIcon() {
        show(WizardState.ScanVerify(emptyList(), 0, Keys.POWER))
        rule.onNodeWithTag("big_test_button").assertContentDescriptionEquals("Ein/Aus")
    }

    @Test fun brandPickerLinksToLanguage() {
        show(WizardState.PickBrand)
        rule.onNodeWithTag("wizard_language").assertTextContains("Sprache").performClick()
        assertEquals(1, languageOpened)
    }

    @Test fun saveIntoExistingRoomHasNoNameFieldAndNoSavedList() {
        val saved = SavedRemote("a", "Zimmer 1", "Samsung", "Code 1", emptyMap(), 0L)
        show(WizardState.Save("Samsung", "Code 2", mapOf(Keys.POWER to sig()), hasInputs = false), saved = listOf(saved), addingTo = "Zimmer 1")
        rule.onNodeWithTag("wizard_name").assertDoesNotExist()
        rule.onNodeWithText("BEREITS GESPEICHERT").assertDoesNotExist()
        rule.onNodeWithTag("wizard_adding_to").assertTextContains("Wird zu „Zimmer 1“ hinzugefügt.")
        rule.onNodeWithText("Zur Fernbedienung").performClick()
        assertEquals(listOf<Pair<String?, String>>(null to "Samsung"), saves)
    }

    @Test fun saveNewRoomHasNameField() {
        show(WizardState.Save("Samsung", "Code 2", mapOf(Keys.POWER to sig()), hasInputs = false))
        rule.onNodeWithTag("wizard_name").assertExists()
        rule.onNodeWithTag("wizard_adding_to").assertDoesNotExist()
    }
}
