package de.hotelremote.ui.remotes

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import de.hotelremote.Keys
import de.hotelremote.data.SavedRemote
import de.hotelremote.data.testAc
import de.hotelremote.ir.IrSignal
import de.hotelremote.ui.DeviceKind
import de.hotelremote.ui.theme.HotelRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val TV_KEYS = mapOf(Keys.POWER to IrSignal(38000, listOf(1), periodMs = 100))

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteListScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val tv = SavedRemote("tv", "Zimmer 1", "Samsung", "Code 1", TV_KEYS, lastUsed = 3)
    private val both = SavedRemote("both", "Zimmer 2", "LG", "Code 2", TV_KEYS, lastUsed = 2, ac = testAc())
    private val acOnly = SavedRemote("ac", "Zimmer 3", lastUsed = 1, ac = testAc())

    private val added = mutableListOf<Pair<String, DeviceKind>>()
    private val removed = mutableListOf<Pair<String, DeviceKind>>()
    private val deleted = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            HotelRemoteTheme {
                RemoteListScreen(
                    remotes = listOf(tv, both, acOnly),
                    currentId = "tv",
                    onSwitch = {},
                    onRename = { _, _ -> },
                    onDelete = { deleted += it },
                    onNew = {},
                    onBack = {},
                    onAddDevice = { id, kind -> added += id to kind },
                    onRemoveDevice = { id, kind -> removed += id to kind },
                )
            }
        }
    }

    private fun actions(id: String) = rule.onNodeWithTag("remote_$id").performTouchInput { longClick() }

    @Test fun rowsShowTheirDevices() {
        show()
        rule.onNodeWithTag("devices_tv", useUnmergedTree = true).assertTextEquals("TV")
        rule.onNodeWithTag("devices_both", useUnmergedTree = true).assertTextEquals("TV · Klima")
        rule.onNodeWithTag("devices_ac", useUnmergedTree = true).assertTextEquals("Klima")
    }

    @Test fun tvRoomOffersAddAc() {
        show()
        actions("tv")
        rule.onNodeWithTag("action_add_tv").assertDoesNotExist()
        rule.onNodeWithTag("action_remove_ac").assertDoesNotExist()
        rule.onNodeWithTag("action_add_ac").assertTextEquals("Klimaanlage hinzufügen").performClick()
        assertEquals(listOf("tv" to DeviceKind.AC), added)
    }

    @Test fun replacingAcAsksFirst() {
        show()
        actions("both")
        rule.onNodeWithTag("action_add_ac").assertTextEquals("Klimaanlage ersetzen").performClick()
        rule.onNodeWithText("„Zimmer 2“ hat schon eine Klimaanlage. Sie wird ersetzt, sobald du die neue speicherst.")
            .assertExists()
        assertEquals(emptyList<Pair<String, DeviceKind>>(), added)
        rule.onNodeWithTag("replace_ac_confirm").performClick()
        assertEquals(listOf("both" to DeviceKind.AC), added)
    }

    @Test fun replacingAcCanBeCancelled() {
        show()
        actions("both")
        rule.onNodeWithTag("action_add_ac").performClick()
        rule.onNodeWithText("Abbrechen").performClick()
        rule.onNodeWithTag("replace_ac_confirm").assertDoesNotExist()
        assertEquals(emptyList<Pair<String, DeviceKind>>(), added)
    }

    @Test fun removingOneOfTwoDevicesAsks() {
        show()
        actions("both")
        rule.onNodeWithTag("action_remove_ac").performClick()
        rule.onNodeWithText("Klimaanlage aus „Zimmer 2“ entfernen?").assertExists()
        rule.onNodeWithTag("remove_confirm").assertTextEquals("Entfernen").performClick()
        actions("both")
        rule.onNodeWithTag("action_remove_tv").performClick()
        rule.onNodeWithText("Fernseher aus „Zimmer 2“ entfernen?").assertExists()
        rule.onNodeWithTag("remove_confirm").performClick()
        assertEquals(listOf("both" to DeviceKind.AC, "both" to DeviceKind.TV), removed)
    }

    @Test fun removingLastDeviceSaysRoomIsDeleted() {
        show()
        actions("ac")
        rule.onNodeWithTag("action_remove_tv").assertDoesNotExist()
        rule.onNodeWithTag("action_remove_ac").performClick()
        rule.onNodeWithText("„Zimmer 3“ hat dann kein Gerät mehr und wird gelöscht.").assertExists()
        rule.onNodeWithTag("remove_confirm").assertTextEquals("Löschen").performClick()
        assertEquals(listOf("ac" to DeviceKind.AC), removed)
    }

    @Test fun pureAcRoomOffersAddTv() {
        show()
        actions("ac")
        rule.onNodeWithTag("action_add_tv").assertTextEquals("Fernseher hinzufügen").performClick()
        assertEquals(listOf("ac" to DeviceKind.TV), added)
    }

    @Test fun deleteStillAsks() {
        show()
        actions("tv")
        rule.onNodeWithTag("action_delete").performClick()
        rule.onNodeWithText("Fernbedienung „Zimmer 1“ löschen?").assertExists()
        rule.onNodeWithText("Löschen").performClick()
        assertEquals(listOf("tv"), deleted)
    }
}
