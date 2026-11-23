package de.hotelremote.ui

import android.app.Application
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.quick.QuickPrefs
import de.hotelremote.ui.automation.AutomationEditorScreen
import de.hotelremote.ui.quick.QuickControlsScreen
import de.hotelremote.ui.theme.HotelRemoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bildschirme mit API-abhängigen Stellen unter Android 6 (API 23, minSdk). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class LegacyAndroidUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Zahlenformat der Wartezeit nach der Sprache der Konfiguration (Configuration.locales erst ab API 24). */
    @Test fun automationEditorFormatsSecondsOnMarshmallow() {
        rule.setContent {
            HotelRemoteTheme {
                AutomationEditorScreen(
                    initial = null,
                    keys = setOf(Keys.OK),
                    progress = null,
                    onTest = { _, _ -> },
                    onCancelRun = {},
                    onSave = {},
                    onCancel = {},
                )
            }
        }
        rule.onNodeWithTag("auto_add_step").performClick()
        rule.onNodeWithTag("pick_${Keys.OK}").performClick()
        rule.onNodeWithTag("step_wait_0").assertTextEquals("Danach warten: 1,5 s")
    }

    /** Vor Android 7 gibt es keine Schnelleinstellungs-Kacheln: Hinweis statt Anleitung. */
    @Test fun quickControlsExplainTilesNeedNougat() {
        val sig = IrSignal(freq = 38000, frame = listOf(1, 2, 3), periodMs = 100)
        val remote = SavedRemote(id = "r1", name = "Zimmer 412", brand = "LG", codeLabel = "Code 1", keys = mapOf(Keys.POWER to sig), lastUsed = 0)
        rule.setContent { HotelRemoteTheme { QuickControlsScreen(remote, QuickPrefs(null, false), {}, {}, onBack = {}) } }
        rule.onNodeWithText(app.getString(R.string.quick_tiles_unsupported)).performScrollTo().assertExists()
        rule.onNodeWithText(app.getString(R.string.quick_tiles_howto)).assertDoesNotExist()
    }
}
