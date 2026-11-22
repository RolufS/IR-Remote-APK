package de.hotelremote.ui.wizard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.hotelremote.Keys
import de.hotelremote.data.Brand
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

    private fun show(state: WizardState, irAvailable: Boolean = true) {
        rule.setContent {
            HotelRemoteTheme {
                WizardScreen(
                    state = state,
                    brands = brands,
                    irAvailable = irAvailable,
                    onPickBrand = {}, onStartScan = {}, onTest = {}, onAnswer = {}, onTvOff = {},
                    onStopScan = {}, onResumeScan = {}, onSave = { _, _ -> }, onBack = {},
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
}
