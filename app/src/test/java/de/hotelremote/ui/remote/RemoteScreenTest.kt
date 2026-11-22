package de.hotelremote.ui.remote

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.ui.theme.HotelRemoteTheme
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
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

    override fun hold(key: String): Job? = null
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
                    onMenu = {},
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
}
