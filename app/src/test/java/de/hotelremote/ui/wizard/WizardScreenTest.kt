package de.hotelremote.ui.wizard

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

    private fun show(state: WizardState, irAvailable: Boolean = true, saved: List<SavedRemote> = emptyList()) {
        rule.setContent {
            HotelRemoteTheme {
                WizardScreen(
                    state = state,
                    brands = brands,
                    irAvailable = irAvailable,
                    savedRemotes = saved,
                    onPickBrand = {}, onStartScan = { started++ }, onTest = {}, onAnswer = {}, onTvOff = {},
                    onStopScan = {}, onResumeScan = { resumed += it }, onSave = { _, _ -> }, onBack = {},
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
}
