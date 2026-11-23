package de.hotelremote.ui.remote

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
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
import de.hotelremote.Keys
import de.hotelremote.data.SavedRemote
import de.hotelremote.ui.automation.RecordingBarState
import de.hotelremote.ir.IrSignal
import de.hotelremote.ui.theme.HotelRemoteTheme
import kotlinx.coroutines.Job
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
        locked: Boolean = false,
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
                    locked = locked,
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

    /** Beschriftungen einer Taste (ungemergter Baum) mit ihrem Text-Layout. */
    private fun labels(key: String): List<Pair<String, TextLayoutResult>> =
        rule.onAllNodes(
            hasAnyAncestor(hasTestTag(key)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { node ->
            val layout = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
            node.config[SemanticsProperties.Text].joinToString("") { it.text } to layout.single()
        }

    private val allKeys = arrayOf(
        "SOURCE", "POWER", "MUTE", "HDMI1", "HDMI2", "HDMI3", "HDMI4", "DIGIT_1", "DIGIT_0", "VOL_UP", "CH_UP", "OK",
    )

    @Test
    @Config(sdk = [35], qualifiers = "de-w360dp-h640dp-port-xxhdpi")
    fun hdmiLabelsTwoLinesWithRoom() {
        show(remote(*allKeys))
        Keys.HDMI.forEachIndexed { i, key ->
            val labels = labels("key_$key")
            assertEquals(listOf("HDMI", "${i + 1}"), labels.map { it.first })
            assertTrue("$key abgeschnitten", labels.none { it.second.didOverflowHeight })
        }
    }

    /** Kleines Handy mit Aufnahmeleiste: die HDMI-Reihe ist niedrig, die Ziffer darf nicht abgeschnitten werden. */
    @Test
    @Config(sdk = [35], qualifiers = "de-w360dp-h640dp-port-xxhdpi")
    fun hdmiLabelsFitWithRecordingBar() = assertHdmiLabelsFit(bar(3))

    @Test
    @Config(sdk = [35], qualifiers = "de-w360dp-h640dp-port-xxhdpi")
    fun hdmiLabelsFitWithRecordingBarAndHint() = assertHdmiLabelsFit(bar(0, hint = true))

    private fun assertHdmiLabelsFit(recording: RecordingBarState) {
        show(remote(*allKeys), recording = recording)
        Keys.HDMI.forEachIndexed { i, key ->
            val labels = labels("key_$key")
            assertEquals("HDMI ${i + 1}", labels.joinToString(" ") { it.first })
            assertTrue("$key abgeschnitten", labels.none { it.second.didOverflowHeight })
        }
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

    /** Über dem Sperrbildschirm: kein ☰, kein ⚡, kein Umschalten, ⋮ nur „Weitere Tasten“; Tasten senden. */
    @Test
    fun lockedHeaderIsRestricted() {
        val menus = mutableListOf<RemoteMenu>()
        val handler = FakeHandler()
        show(remote("MUTE").copy(name = "Zimmer 412"), handler = handler, onMenu = { menus += it }, locked = true)
        rule.onNodeWithTag("header_list").assertDoesNotExist()
        rule.onNodeWithTag("header_quick").assertDoesNotExist()
        rule.onNodeWithTag("header_title").assertTextEquals("Zimmer 412")
        rule.onNodeWithTag("header_title").performClick()
        rule.onNodeWithTag("switch_menu").assertDoesNotExist()
        rule.onNodeWithTag("header_menu").performClick()
        rule.onNodeWithText("Automationen").assertDoesNotExist()
        rule.onNodeWithText("Schnellsteuerung").assertDoesNotExist()
        rule.onNodeWithText("Weitere Tasten").performClick()
        assertEquals(listOf(RemoteMenu.EXTRA_KEYS), menus)
        rule.onNodeWithTag("key_MUTE").performClick()
        assertEquals(listOf("MUTE"), handler.pressed)
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

    // ---- Kopfzeile ----

    @Test
    fun switchMenuOpensCenteredUnderTitle() {
        // Kurze Namen: das Menü (mind. 112 dp) ist breiter als der Titel, aber schmaler als der Bildschirm.
        val r = remote("MUTE").copy(name = "Pattaya")
        val other = r.copy(id = "r2", name = "Zimmer 12")
        rule.setContent {
            HotelRemoteTheme {
                RemoteScreen(r, listOf(r, other), true, FakeHandler(), {}, {}, {})
            }
        }
        rule.onNodeWithTag("header_title").performClick()
        rule.waitForIdle()
        fun centerOnScreen(tag: String): Float {
            val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
            return node.positionOnScreen.x + node.size.width / 2f
        }
        val title = centerOnScreen("header_title")
        val menu = centerOnScreen("switch_menu")
        val density = rule.density.density
        assertTrue("Titelmitte $title, Menümitte $menu", kotlin.math.abs(title - menu) < 4 * density)
        val titleWidth = rule.onNodeWithTag("header_title").fetchSemanticsNode().size.width
        val menuWidth = rule.onNodeWithTag("switch_menu").fetchSemanticsNode().size.width
        assertTrue("Menü $menuWidth schmaler als Titel $titleWidth", menuWidth >= titleWidth)
    }

    @Test
    fun titleSitsInTheMiddleOfTheScreen() {
        show(remote("MUTE").copy(name = "Pattaya"))
        val title = rule.onNodeWithTag("header_title").fetchSemanticsNode()
        val list = rule.onNodeWithTag("header_list").fetchSemanticsNode()
        val more = rule.onNodeWithTag("header_menu").fetchSemanticsNode()
        val titleCenter = title.positionOnScreen.x + title.size.width / 2f
        // Mitte zwischen dem linken Rand von ☰ und dem rechten Rand von ⋮ = Mitte der Kopfzeile.
        val headerCenter = (list.positionOnScreen.x + more.positionOnScreen.x + more.size.width) / 2f
        assertTrue("Titelmitte $titleCenter, Kopfzeilenmitte $headerCenter", kotlin.math.abs(titleCenter - headerCenter) < 2 * rule.density.density)
    }

    @Test
    fun centeredMenuOffsetCentersBothWays() {
        assertEquals(-50, centeredMenuOffset(anchor = 100, menu = 200))
        assertEquals(30, centeredMenuOffset(anchor = 160, menu = 100))
    }

    @Test
    fun centeredPopupPositionIsKnownFromTheMeasuredSize() {
        val window = IntSize(1000, 2000)
        val anchor = IntRect(left = 400, top = 100, right = 600, bottom = 150)
        // Breiter als der Anker: mittig darunter.
        assertEquals(IntOffset(350, 150), CenteredBelowAnchor.calculatePosition(anchor, window, LayoutDirection.Ltr, IntSize(300, 400)))
        // Am Rand: im Fenster gehalten.
        val edge = IntRect(left = 0, top = 100, right = 100, bottom = 150)
        assertEquals(IntOffset(0, 150), CenteredBelowAnchor.calculatePosition(edge, window, LayoutDirection.Ltr, IntSize(300, 400)))
        // Passt unten nicht hin: darüber.
        val low = IntRect(left = 400, top = 1900, right = 600, bottom = 1950)
        assertEquals(IntOffset(350, 1500), CenteredBelowAnchor.calculatePosition(low, window, LayoutDirection.Ltr, IntSize(300, 400)))
    }
}
