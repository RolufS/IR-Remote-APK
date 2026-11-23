package de.hotelremote.ui.quick

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.quick.QuickPrefs
import de.hotelremote.ui.theme.HotelRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickControlsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val sig = IrSignal(freq = 38000, frame = listOf(1, 2, 3), periodMs = 100)

    private fun remote(vararg keys: String) =
        SavedRemote(id = "r1", name = "Zimmer 412", brand = "LG", codeLabel = "Code 1", keys = keys.associateWith { sig }, lastUsed = 0)

    private val inputs = mutableListOf<String>()
    private val lock = mutableListOf<Boolean>()

    private fun show(remote: SavedRemote, prefs: QuickPrefs = QuickPrefs(null, false)) = rule.setContent {
        HotelRemoteTheme { QuickControlsScreen(remote, prefs, { inputs += it }, { lock += it }, onBack = {}) }
    }

    @Test fun offersOnlyPresentInputKeys() {
        show(remote(Keys.POWER, Keys.HDMI1, Keys.HDMI2, Keys.SOURCE))
        rule.onNodeWithText("Sendet an die zuletzt benutzte Fernbedienung: Zimmer 412").assertExists()
        rule.onNodeWithTag("quick_input_${Keys.HDMI1}").assertExists()
        rule.onNodeWithTag("quick_input_${Keys.SOURCE}").assertExists()
        rule.onNodeWithTag("quick_input_${Keys.HDMI3}").assertDoesNotExist()
        rule.onNodeWithTag("quick_input_${Keys.HDMI2}").performClick()
        assertEquals(listOf(Keys.HDMI2), inputs)
    }

    @Test fun withoutInputKeysShowsHint() {
        show(remote(Keys.POWER))
        rule.onNodeWithText("Diese Fernbedienung hat keine Eingangstaste.").assertExists()
    }

    @Test fun lockScreenSwitchWithPermission() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show(remote(Keys.POWER), QuickPrefs(null, lockScreen = true))
        rule.onNodeWithTag("quick_lock").assertIsOn().performClick()
        assertEquals(listOf(false), lock)
    }

    @Test fun addTileButtonsFromAndroid13() {
        show(remote(Keys.POWER))
        rule.onNodeWithText("Kachel „TV Eingang“ hinzufügen").performScrollTo().assertExists()
    }
}
