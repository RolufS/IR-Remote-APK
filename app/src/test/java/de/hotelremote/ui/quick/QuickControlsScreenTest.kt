package de.hotelremote.ui.quick

import android.Manifest
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.quick.LockStyle
import de.hotelremote.quick.QuickButton
import de.hotelremote.quick.QuickPrefs
import de.hotelremote.quick.QuickWidgetEntry
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

    /** Erlaubnis in den Systemeinstellungen erteilt: nach der Rückkehr verschwindet der Hinweis. */
    @Test fun blockedHintRecomputedOnResume() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                HotelRemoteTheme { QuickControlsScreen(remote(Keys.POWER), QuickPrefs(null, true), {}, {}, onBack = {}) }
            }
        }
        rule.onNodeWithTag("quick_notif_blocked").assertExists()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.onNodeWithTag("quick_notif_blocked").assertDoesNotExist()
    }

    @Test fun widgetListWithEdit() {
        val edited = mutableListOf<Int>()
        rule.setContent {
            HotelRemoteTheme {
                QuickControlsScreen(
                    remote(Keys.POWER),
                    QuickPrefs(null, false),
                    {},
                    {},
                    onBack = {},
                    widgets = listOf(QuickWidgetEntry(12, 4), QuickWidgetEntry(15, 2)),
                    onEditWidget = { edited += it },
                )
            }
        }
        rule.onNodeWithText("Widget 2 · Tasten: 2").performScrollTo().assertExists()
        rule.onNodeWithTag("quick_widget_1_edit").performClick()
        assertEquals(listOf(15), edited)
        rule.onNodeWithTag("quick_widgets_none").assertDoesNotExist()
    }

    @Test fun withoutWidgetsShowsHint() {
        show(remote(Keys.POWER))
        rule.onNodeWithTag("quick_widgets_none").performScrollTo().assertExists()
    }

    @Test fun editNotificationButtons() {
        val saved = mutableListOf<List<QuickButton>>()
        rule.setContent {
            HotelRemoteTheme {
                QuickControlsScreen(remote(Keys.POWER, Keys.MUTE), QuickPrefs(null, true), {}, {}, onBack = {}, onNotificationButtons = { saved += it })
            }
        }
        rule.onNodeWithTag("quick_notif_buttons").performScrollTo().performClick()
        rule.onNodeWithText("Tasten der Benachrichtigung").assertExists()
        rule.onNodeWithTag("qb_row_0").performClick()
        rule.onNodeWithTag("pick_common_key_${Keys.MUTE}").performClick()
        rule.onNodeWithTag("qb_save").performClick()
        assertEquals(listOf(QuickButton.Key(Keys.MUTE), QuickButton.Key(Keys.VOL_DOWN), QuickButton.Key(Keys.VOL_UP), QuickButton.Input), saved.single())
        // Zurück auf „Schnellsteuerung“.
        rule.onNodeWithTag("quick_lock").assertExists()
    }

    @Test fun addTileButtonsFromAndroid13() {
        show(remote(Keys.POWER))
        rule.onNodeWithText("Kachel „TV Eingang“ hinzufügen").performScrollTo().assertExists()
    }

    @Test fun lockStyleChoiceOnlyWhenLockScreenOn() {
        show(remote(Keys.POWER))
        rule.onNodeWithTag("quick_lock_small").assertDoesNotExist()
        rule.onNodeWithTag("quick_lock_expanded").assertDoesNotExist()
    }

    @Test fun lockStyleChoice() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val styles = mutableListOf<LockStyle>()
        rule.setContent {
            HotelRemoteTheme {
                QuickControlsScreen(remote(Keys.POWER), QuickPrefs(null, true), {}, {}, onBack = {}, onLockStyle = { styles += it })
            }
        }
        rule.onNodeWithTag("quick_lock_expanded").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("quick_lock_small").performScrollTo().performClick()
        assertEquals(listOf(LockStyle.SMALL), styles)
    }

    @Test fun fullRemoteSwitch() {
        val full = mutableListOf<Boolean>()
        rule.setContent {
            HotelRemoteTheme {
                QuickControlsScreen(remote(Keys.POWER), QuickPrefs(null, false), {}, {}, onBack = {}, onFullRemote = { full += it })
            }
        }
        rule.onNodeWithText("Jeder, der dein Handy in der Hand hat, kann dann den Fernseher bedienen – sonst nichts.").performScrollTo().assertExists()
        rule.onNodeWithTag("quick_full_remote").performScrollTo().assertIsOff().performClick()
        assertEquals(listOf(true), full)
    }

    @Test fun addRemoteTileButton() {
        show(remote(Keys.POWER))
        rule.onNodeWithText("Kachel „TV Fernbedienung“ hinzufügen").performScrollTo().assertExists()
    }
}
