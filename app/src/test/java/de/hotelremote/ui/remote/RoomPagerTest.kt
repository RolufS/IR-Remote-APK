package de.hotelremote.ui.remote

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.hotelremote.Keys
import de.hotelremote.data.SavedRemote
import de.hotelremote.data.testAc
import de.hotelremote.ir.IrSignal
import de.hotelremote.ui.AcAction
import de.hotelremote.data.RoomPage
import de.hotelremote.ui.ac.AcPanel
import de.hotelremote.ui.ac.acView
import de.hotelremote.ui.automation.RecordingBarState
import de.hotelremote.ui.theme.HotelRemoteTheme
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val SIGNAL = IrSignal(freq = 38000, frame = listOf(9000, 4500, 560), periodMs = 108)

private class HoldHandler : KeyHandler {
    val pressed = mutableListOf<String>()
    val held = mutableListOf<String>()
    val job = Job()

    override fun press(key: String) {
        pressed += key
    }

    override fun hold(key: String): Job {
        held += key
        return job
    }
}

/** Sicher länger als die Long-Press-Zeit, nach der eine Haltetaste im Wischbereich zu wiederholen beginnt. */
private const val HOLD_START_MS = 1000L

private const val DIGIT_1 = "DIGIT_1"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomPagerTest {
    @get:Rule
    val rule = createComposeRule()

    private val pages = mutableListOf<RoomPage>()
    private val actions = mutableListOf<AcAction>()
    private val holdSteps = mutableListOf<AcAction>()
    private var holdEnds = 0

    private fun room(tv: Boolean = true) = SavedRemote(
        id = "r1",
        brand = if (tv) "Samsung" else "",
        codeLabel = if (tv) "Code 1" else "",
        keys = if (tv) listOf(Keys.POWER, Keys.VOL_UP, Keys.MUTE, DIGIT_1).associateWith { SIGNAL } else emptyMap(),
        lastUsed = 0,
        ac = testAc(),
    )

    private fun show(
        remote: SavedRemote = room(),
        page: RoomPage = RoomPage.TV,
        withAc: Boolean = true,
        handler: KeyHandler = HoldHandler(),
        recording: RecordingBarState? = null,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        rule.setContent {
            HotelRemoteTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    RemoteScreen(
                        remote = remote,
                        remotes = listOf(remote),
                        irAvailable = true,
                        handler = handler,
                        onSwitch = {},
                        onOpenList = {},
                        onMenu = {},
                        recording = recording,
                        ac = if (withAc) AcPanel(acView(), { actions += it }, { holdSteps += it }, { holdEnds++ }, page, { pages += it }) else null,
                    )
                }
            }
        }
    }

    @Test
    fun tvAndAcStartsOnTvWithChips() {
        show()
        rule.onNodeWithTag("page_tv").assertIsSelected()
        rule.onNodeWithTag("page_ac").assertIsNotSelected()
        rule.onNodeWithTag("key_POWER").assertIsDisplayed()
        rule.onNodeWithTag("header_title").assertExists()
        rule.onNodeWithTag("ac_screen").assertDoesNotExist()
    }

    @Test
    fun chipSwitchesPageAndPageIsReported() {
        show()
        rule.onNodeWithTag("page_ac").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ac_screen").assertIsDisplayed()
        rule.onNodeWithTag("page_ac").assertIsSelected()
        rule.onNodeWithTag("header_title").assertExists()
        assertEquals(listOf(RoomPage.AC), pages)
        rule.onNodeWithTag("key_ac_power").performClick()
        assertEquals(listOf<AcAction>(AcAction.Power), actions)
    }

    @Test
    fun rememberedPageIsShownFirst() {
        show(page = RoomPage.AC)
        rule.onNodeWithTag("ac_screen").assertIsDisplayed()
        rule.onNodeWithTag("key_POWER").assertDoesNotExist()
        assertTrue(pages.isEmpty())
    }

    @Test
    fun swipeChangesPage() {
        show()
        rule.onNodeWithTag("room_pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("ac_screen").assertIsDisplayed()
        assertEquals(listOf(RoomPage.AC), pages)
        rule.onNodeWithTag("room_pager").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithTag("key_POWER").assertIsDisplayed()
        assertEquals(listOf(RoomPage.AC, RoomPage.TV), pages)
    }

    @Test
    fun rtlPageOrderFollowsReadingDirection() {
        show(direction = LayoutDirection.Rtl)
        val tv = rule.onNodeWithTag("page_tv").fetchSemanticsNode().boundsInRoot.left
        val ac = rule.onNodeWithTag("page_ac").fetchSemanticsNode().boundsInRoot.left
        assertTrue("TV-Chip rechts", tv > ac)
        rule.onNodeWithTag("room_pager").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithTag("ac_screen").assertIsDisplayed()
    }

    @Test
    fun pureAcRoomHasNoPager() {
        show(room(tv = false))
        rule.onNodeWithTag("ac_screen").assertIsDisplayed()
        rule.onNodeWithTag("page_tv").assertDoesNotExist()
        rule.onNodeWithTag("room_pager").assertDoesNotExist()
        rule.onNodeWithTag("key_SOURCE").assertDoesNotExist()
    }

    @Test
    fun tvOnlyRoomHasNoPager() {
        show(withAc = false)
        rule.onNodeWithTag("page_tv").assertDoesNotExist()
        rule.onNodeWithTag("room_pager").assertDoesNotExist()
        rule.onNodeWithTag("key_POWER").assertIsDisplayed()
    }

    @Test
    fun recordingHidesAcPage() {
        show(recording = RecordingBarState(0, true, {}, {}, {}))
        rule.onNodeWithTag("page_ac").assertDoesNotExist()
        rule.onNodeWithTag("ac_screen").assertDoesNotExist()
        rule.onNodeWithTag("key_VOL_UP").assertIsDisplayed()
    }

    @Test
    fun holdingVolumeIsNotBrokenBySwiping() {
        val handler = HoldHandler()
        show(handler = handler)
        val node = rule.onNodeWithTag("key_VOL_UP")
        node.performTouchInput { down(center) }
        assertTrue("Wiederholung startet nicht beim Herunterdrücken", handler.held.isEmpty())
        rule.mainClock.advanceTimeBy(HOLD_START_MS)
        assertEquals(listOf("VOL_UP"), handler.held)
        assertFalse("Pager steht beim Halten still", dragBy("key_VOL_UP", 40))
        assertFalse(handler.job.isCancelled)
        node.performTouchInput { up() }
        assertTrue(handler.job.isCancelled)
        rule.waitForIdle()
        rule.onNodeWithTag("page_tv").assertIsSelected()
        assertTrue(pages.isEmpty())
        assertTrue(handler.pressed.isEmpty())
    }

    @Test
    fun holdingAcPlusIsNotBrokenBySwiping() {
        show(page = RoomPage.AC)
        rule.mainClock.autoAdvance = false
        val node = rule.onNodeWithTag("key_ac_warmer")
        node.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(HOLD_START_MS)
        assertTrue(holdSteps.isNotEmpty())
        assertFalse(dragBy("key_ac_warmer", 40, anchor = "key_ac_power", towardsTv = true))
        node.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue(holdSteps.all { it == AcAction.Warmer })
        assertEquals(1, holdEnds)
        assertTrue(actions.isEmpty())
        assertTrue(pages.isEmpty())
    }

    @Test
    fun swipeStartingOnDigitPagesAndSendsNothing() {
        val handler = HoldHandler()
        show(handler = handler)
        swipeFrom("key_$DIGIT_1")
        rule.onNodeWithTag("ac_screen").assertIsDisplayed()
        assertEquals(listOf(RoomPage.AC), pages)
        assertTrue(handler.pressed.isEmpty())
    }

    @Test
    fun swipeStartingOnVolumePagesAndSendsNothing() {
        val handler = HoldHandler()
        show(handler = handler)
        swipeFrom("key_VOL_UP")
        rule.onNodeWithTag("ac_screen").assertIsDisplayed()
        assertTrue(handler.pressed.isEmpty())
        assertTrue(handler.held.isEmpty())
    }

    @Test
    fun swipeStartingOnAcPlusPagesAndChangesNothing() {
        show(page = RoomPage.AC)
        swipeFrom("key_ac_warmer", towardsTv = true)
        rule.onNodeWithTag("key_POWER").assertIsDisplayed()
        assertTrue(actions.isEmpty())
        assertTrue(holdSteps.isEmpty())
        assertEquals(0, holdEnds)
    }

    @Test
    fun tapOnDigitSendsOnceOnRelease() {
        val handler = HoldHandler()
        show(handler = handler)
        val node = rule.onNodeWithTag("key_$DIGIT_1")
        node.performTouchInput { down(center) }
        assertTrue("nicht schon beim Herunterdrücken", handler.pressed.isEmpty())
        node.performTouchInput { up() }
        assertEquals(listOf(DIGIT_1), handler.pressed)
    }

    @Test
    fun tapOnVolumeSendsOnce() {
        val handler = HoldHandler()
        show(handler = handler)
        rule.onNodeWithTag("key_VOL_UP").performClick()
        rule.mainClock.advanceTimeBy(HOLD_START_MS)
        assertEquals(listOf("VOL_UP"), handler.pressed)
        assertTrue(handler.held.isEmpty())
    }

    @Test
    fun tvOnlyRoomStillSendsOnPressDown() {
        val handler = HoldHandler()
        show(withAc = false, handler = handler)
        rule.onNodeWithTag("key_$DIGIT_1").performTouchInput { down(center) }
        assertEquals(listOf(DIGIT_1), handler.pressed)
        rule.onNodeWithTag("key_$DIGIT_1").performTouchInput { up() }
        rule.onNodeWithTag("key_VOL_UP").performTouchInput { down(center) }
        assertEquals("Halten beginnt sofort", listOf("VOL_UP"), handler.held)
    }

    /** Wischt von [key] aus 200 dp weit zur Nachbarseite und lässt los. */
    private fun swipeFrom(key: String, towardsTv: Boolean = false) {
        val node = rule.onNodeWithTag(key)
        node.performTouchInput { down(center) }
        val step = with(rule.density) { 20.dp.toPx() } * if (towardsTv) 1 else -1
        repeat(10) { node.performTouchInput { moveBy(Offset(step, 0f), delayMillis = 10) } }
        node.performTouchInput { up() }
        rule.waitForIdle()
    }

    /**
     * Verschiebt den schon gedrückten Finger auf [key] um [distanceDp] zur Nachbarseite (innerhalb der Taste;
     * [towardsTv]: nach rechts) und liefert true, wenn der Pager mitging.
     */
    private fun dragBy(key: String, distanceDp: Int, anchor: String = "key_POWER", towardsTv: Boolean = false): Boolean {
        val node = rule.onNodeWithTag(key)
        val before = left(anchor)
        val step = with(rule.density) { 10.dp.toPx() } * if (towardsTv) 1 else -1
        repeat(distanceDp / 10) {
            node.performTouchInput { moveBy(Offset(step, 0f)) }
            nextFrame()
        }
        return left(anchor) != before
    }

    /** Ein Bild weiter, auch wenn die Uhr angehalten ist (gehaltenes Klima-+ wiederholt endlos). */
    private fun nextFrame() {
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    /** Linke Kante einer Taste der Seite: verschiebt sich, sobald der Pager mitgeht. */
    private fun left(tag: String): Float = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left
}
