package de.hotelremote.ui.quick

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import de.hotelremote.Keys
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.quick.QuickButton
import de.hotelremote.quick.QuickButtons
import de.hotelremote.ui.theme.HotelRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickButtonsEditorTest {
    @get:Rule
    val rule = createComposeRule()

    private val sig = IrSignal(freq = 38000, frame = listOf(1, 2, 3), periodMs = 100)
    private val short = Automation("a1", "Sleep 60", listOf(AutomationStep(Keys.MENU, 2000), AutomationStep(Keys.OK)))
    private val long = Automation("a2", "Lang", listOf(AutomationStep(Keys.MENU, 9000), AutomationStep(Keys.OK)))

    private fun remote(vararg keys: String, automations: List<Automation> = listOf(short, long)) = SavedRemote(
        id = "r1",
        name = "Zimmer 412",
        brand = "LG",
        codeLabel = "Code 1",
        keys = keys.associateWith { sig },
        lastUsed = 0,
        automations = automations,
    )

    private val saved = mutableListOf<List<QuickButton>>()
    private var cancelled = 0

    private fun show(
        remote: SavedRemote?,
        initial: List<QuickButton> = QuickButtons.DEFAULT,
        target: QuickButtonsTarget = QuickButtonsTarget.WIDGET,
    ) = rule.setContent {
        HotelRemoteTheme { QuickButtonsEditor(target, remote, null, initial, onSave = { saved += it }, onCancel = { cancelled++ }) }
    }

    /** Eintrag der (lazy) Knopfliste erst sichtbar scrollen. */
    private fun listItem(tag: String): SemanticsNodeInteraction {
        rule.onNodeWithTag("qb_list").performScrollToNode(hasTestTag(tag))
        return rule.onNodeWithTag(tag)
    }

    @Test fun pickerGroupsFromRemote() {
        val groups = pickerGroups(remote(Keys.POWER, Keys.VOL_UP, Keys.HDMI1, Keys.MENU, Keys.OK, "X:Netflix"))
        assertEquals(listOf("common", "all", "extra", "auto"), groups.map { it.tag })
        assertEquals(
            listOf(QuickButton.Key(Keys.POWER), QuickButton.Key(Keys.VOL_UP), QuickButton.Input),
            groups[0].entries.map { it.button },
        )
        assertEquals(listOf(QuickButton.Key("X:Netflix")), groups[2].entries.map { it.button })
        // Automation über 8 s: gesperrt.
        assertEquals(listOf(true, false), groups[3].entries.map { it.enabled })
    }

    @Test fun pickerWithoutRemoteOffersCommonOnly() {
        val groups = pickerGroups(null)
        assertEquals(listOf("common"), groups.map { it.tag })
        assertTrue(QuickButton.Input in groups[0].entries.map { it.button })
    }

    @Test fun saveReturnsDefaultUnchanged() {
        show(remote(Keys.POWER))
        rule.onNodeWithText("Sendet an die zuletzt benutzte Fernbedienung: Zimmer 412").assertExists()
        rule.onNodeWithTag("qb_preview_3").assertExists()
        rule.onNodeWithTag("qb_preview_4").assertDoesNotExist()
        rule.onNodeWithTag("qb_save").performClick()
        assertEquals(listOf(QuickButtons.DEFAULT), saved)
    }

    @Test fun addReorderRemoveAndSave() {
        show(remote(Keys.POWER, Keys.VOL_DOWN, Keys.VOL_UP, Keys.HDMI1, Keys.MUTE, Keys.MENU, Keys.OK))
        listItem("qb_add").performClick()
        rule.onNodeWithTag("qb_picker").performScrollToNode(hasTestTag("pick_auto_auto_a1"))
        rule.onNodeWithTag("pick_auto_auto_a1").performClick()
        listItem("qb_row_4")
        rule.onNodeWithTag("qb_up_4").performClick()
        listItem("qb_remove_0").performClick()
        rule.onNodeWithTag("qb_save").performClick()
        assertEquals(
            listOf(listOf(QuickButton.Key(Keys.VOL_DOWN), QuickButton.Key(Keys.VOL_UP), QuickButton.Run("a1", "Sleep 60"), QuickButton.Input)),
            saved,
        )
    }

    @Test fun tooLongAutomationIsDisabledInPicker() {
        show(remote(Keys.MENU, Keys.OK))
        listItem("qb_add").performClick()
        rule.onNodeWithTag("qb_picker").performScrollToNode(hasTestTag("pick_auto_auto_a2"))
        rule.onNodeWithTag("pick_auto_auto_a2").assertIsNotEnabled()
        rule.onNodeWithText("zu lang für das Widget").assertExists()
        rule.onNodeWithTag("pick_auto_auto_a1").assertIsEnabled()
    }

    @Test fun tapOnRowReplacesButton() {
        show(remote(Keys.POWER, Keys.MUTE))
        rule.onNodeWithTag("qb_row_1").performClick()
        rule.onNodeWithTag("pick_common_key_${Keys.MUTE}").performClick()
        rule.onNodeWithTag("qb_save").performClick()
        assertEquals(QuickButton.Key(Keys.MUTE), saved.single()[1])
        assertEquals(4, saved.single().size)
    }

    @Test fun addDisabledAtMaxOfNotification() {
        show(remote(Keys.POWER), initial = List(5) { QuickButton.Key(Keys.POWER) }, target = QuickButtonsTarget.NOTIFICATION)
        rule.onNodeWithText("Tasten der Benachrichtigung").assertExists()
        listItem("qb_add").assertIsNotEnabled()
    }

    @Test fun singleButtonCannotBeRemoved() {
        show(remote(Keys.POWER), initial = listOf(QuickButton.Key(Keys.POWER)))
        rule.onNodeWithTag("qb_remove_0").assertIsNotEnabled()
    }

    @Test fun missingKeyIsMarked() {
        show(remote(Keys.POWER), initial = listOf(QuickButton.Key(Keys.POWER), QuickButton.Key(Keys.HDMI3)))
        rule.onNodeWithTag("qb_note_0", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("qb_note_1", useUnmergedTree = true).assertExists()
    }

    @Test fun cancelSavesNothing() {
        show(null)
        rule.onNodeWithText("Keine Fernbedienung").assertExists()
        rule.onNodeWithTag("qb_cancel").performClick()
        assertEquals(1, cancelled)
        assertFalse(saved.isNotEmpty())
    }
}
