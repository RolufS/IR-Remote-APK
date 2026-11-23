package de.hotelremote.ui.remote

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.down
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import de.hotelremote.data.SavedRemote
import de.hotelremote.ui.automation.RecordingBarState
import de.hotelremote.ir.IrSignal
import de.hotelremote.ui.theme.HotelRemoteTheme
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class FakeHandler : KeyHandler {
    val pressed = mutableListOf<String>()
    override fun press(key: String) {
        pressed += key
    }

    val held = mutableListOf<String>()
    var holdJob: Job? = null

    override fun hold(key: String): Job? {
        held += key
        return holdJob
    }
}

private val SIGNAL = IrSignal(freq = 38000, frame = listOf(9000, 4500, 560), periodMs = 108)

private fun remote(vararg keys: String) = SavedRemote(
    id = "r1",
    brand = "Samsung",
    codeLabel = "Code 1",
    keys = keys.associateWith { SIGNAL },
    lastUsed = 0L,
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(
        remote: SavedRemote,
        irAvailable: Boolean = true,
        handler: KeyHandler = FakeHandler(),
        onQuick: () -> Unit = {},
        recording: RecordingBarState? = null,
        onMenu: (RemoteMenu) -> Unit = {},
    ) {
        rule.setContent {
            HotelRemoteTheme {
                RemoteScreen(
                    remote = remote,
                    remotes = listOf(remote),
                    irAvailable = irAvailable,
                    handler = handler,
                    onSwitch = {},
                    onOpenList = {},
                    onMenu = onMenu,
                    onQuick = onQuick,
                    recording = recording,
                )
            }
        }
    }

    @Test
    fun inputRowHiddenWithoutHdmi() {
        show(remote("MUTE", "HDMI2"))
        rule.onNodeWithTag("key_HDMI1").assertDoesNotExist()
    }

    @Test
    fun inputRowShownWithHdmi() {
        show(remote("HDMI1"))
        rule.onNodeWithTag("key_HDMI1").assertExists()
        rule.onNodeWithTag("key_HDMI4").assertExists().assertIsNotEnabled()
    }

    @Test
    fun missingKeyIsDisabled() {
        show(remote("MUTE"))
        rule.onNodeWithTag("key_RED").assertIsNotEnabled()
    }

    @Test
    fun tapSendsKey() {
        val handler = FakeHandler()
        show(remote("MUTE"), handler = handler)
        rule.onNodeWithTag("key_MUTE").performClick()
        assertEquals(listOf("MUTE"), handler.pressed)
    }

    @Test
    fun noIrBannerShownWhenUnavailable() {
        show(remote("MUTE"), irAvailable = false)
        rule.onNodeWithTag("no_ir_banner").assertExists()
    }

    @Test
    fun noIrBannerHiddenWhenAvailable() {
        show(remote("MUTE"), irAvailable = true)
        rule.onNodeWithTag("no_ir_banner").assertDoesNotExist()
    }

    @Test
    fun repeatingKeyHoldsAndCancelsOnRelease() {
        val handler = FakeHandler()
        val job = Job()
        handler.holdJob = job
        show(remote("VOL_UP"), handler = handler)
        rule.onNodeWithTag("key_VOL_UP").performTouchInput { down(center) }
        assertEquals(listOf("VOL_UP"), handler.held)
        assertEquals(emptyList<String>(), handler.pressed)
        assertEquals(false, job.isCancelled)
        rule.onNodeWithTag("key_VOL_UP").performTouchInput { up() }
        assertEquals(true, job.isCancelled)
    }

    @Test
    fun extraKeysSwipeSendsNothingTapSendsOnce() {
        val handler = FakeHandler()
        val keys = (1..30).map { "X:Key%02d".format(it) }
        rule.setContent {
            HotelRemoteTheme {
                ExtraKeysScreen(remote(*keys.toTypedArray()), handler, onBack = {})
            }
        }
        rule.onNodeWithTag("key_X:Key01").performClick()
        assertEquals(listOf("X:Key01"), handler.pressed)
        rule.onNodeWithTag("key_X:Key02").performTouchInput { swipeUp() }
        assertEquals(listOf("X:Key01"), handler.pressed)
    }

    @Test
    fun powerKeyShowsIconWithDescription() {
        show(remote("POWER"))
        rule.onNodeWithTag("key_POWER").assertContentDescriptionEquals("Ein/Aus")
    }

    /** Arabisch (RTL): Texte werden gespiegelt, das Tastenfeld nicht. */
    @Test
    @Config(sdk = [35], qualifiers = "ar")
    fun keypadStaysLeftToRightInArabic() {
        show(remote("SOURCE", "MUTE", "LEFT", "RIGHT", "VOL_UP", "CH_UP", "POWER"))
        fun left(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().left
        assertTrue(left("key_SOURCE") < left("key_MUTE"))
        assertTrue(left("key_LEFT") < left("key_RIGHT"))
        assertTrue(left("key_VOL_UP") < left("key_CH_UP"))
        rule.onNodeWithTag("key_POWER").assertContentDescriptionEquals("تشغيل/إيقاف")
    }

    @Test
    fun quickButtonOpensSheetCallback() {
        var opened = 0
        show(remote("MUTE"), onQuick = { opened++ })
        rule.onNodeWithTag("header_quick").assertContentDescriptionEquals("Automationen und Sleep-Timer").performClick()
        assertEquals(1, opened)
    }

    // ---- Aufnahme ----

    private val calls = mutableListOf<String>()

    private fun bar(count: Int, hint: Boolean = false) = RecordingBarState(
        recordedCount = count,
        hint = hint,
        onUndo = { calls += "undo" },
        onDone = { calls += "done" },
        onCancel = { calls += "cancel" },
    )

    @Test
    fun recordingBarShowsCountAndCallsBack() {
        val handler = FakeHandler()
        show(remote("MUTE"), handler = handler, recording = bar(3))
        rule.onNodeWithTag("recording_status").assertTextEquals("● Aufnahme · Schritte: 3")
        rule.onNodeWithTag("recording_hint").assertDoesNotExist()
        rule.onNodeWithTag("recording_undo").performClick()
        rule.onNodeWithTag("recording_done").performClick()
        rule.onNodeWithTag("recording_cancel").performClick()
        assertEquals(listOf("undo", "done", "cancel"), calls)
        // Tasten senden weiter normal (der Controller nimmt sie auf).
        rule.onNodeWithTag("key_MUTE").performClick()
        assertEquals(listOf("MUTE"), handler.pressed)
    }

    @Test
    fun recordingBarShowsHintAndNoUndoWithoutSteps() {
        show(remote("MUTE"), recording = bar(0, hint = true))
        rule.onNodeWithTag("recording_hint")
            .assertTextEquals("Drücke die Tasten wie gewohnt – Pausen werden als Wartezeit übernommen.")
        rule.onNodeWithTag("recording_undo").assertIsNotEnabled()
        rule.onNodeWithTag("recording_done").assertIsEnabled()
    }

    @Test
    fun quickListAndSwitchDisabledWhileRecording() {
        var quick = 0
        show(remote("MUTE"), onQuick = { quick++ }, recording = bar(1))
        rule.onNodeWithTag("header_quick").assertIsNotEnabled()
        rule.onNodeWithTag("header_list").assertIsNotEnabled()
        rule.onNodeWithTag("header_title").assertIsNotEnabled()
        rule.onNodeWithTag("header_quick").performClick()
        assertEquals(0, quick)
    }

    @Test
    fun menuOffersOnlyExtraKeysWhileRecording() {
        val menus = mutableListOf<RemoteMenu>()
        show(remote("MUTE"), recording = bar(0), onMenu = { menus += it })
        rule.onNodeWithTag("header_menu").performClick()
        rule.onNodeWithText("Automationen").assertDoesNotExist()
        rule.onNodeWithText("Weitere Tasten").performClick()
        assertEquals(listOf(RemoteMenu.EXTRA_KEYS), menus)
    }

    @Test
    fun headerEnabledWithoutRecording() {
        show(remote("MUTE"))
        rule.onNodeWithTag("recording_bar").assertDoesNotExist()
        rule.onNodeWithTag("header_quick").assertIsEnabled()
        rule.onNodeWithTag("header_list").assertIsEnabled()
    }

    @Test
    fun extraKeysShowRecordingBar() {
        val handler = FakeHandler()
        rule.setContent {
            HotelRemoteTheme {
                ExtraKeysScreen(remote("X:Sleep"), handler, onBack = {}, recording = bar(2))
            }
        }
        rule.onNodeWithTag("recording_status").assertTextEquals("● Aufnahme · Schritte: 2")
        rule.onNodeWithTag("key_X:Sleep").performClick()
        assertEquals(listOf("X:Sleep"), handler.pressed)
        rule.onNodeWithTag("recording_done").performClick()
        assertEquals(listOf("done"), calls)
    }
}
