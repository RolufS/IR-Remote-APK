package de.hotelremote.ui.automation

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.automation.AutomationProgress
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.sleeptimer.SleepAction
import de.hotelremote.sleeptimer.SleepTimer
import de.hotelremote.ui.AutomationDraft
import de.hotelremote.ui.remote.QuickSheetContent
import de.hotelremote.ui.theme.HotelRemoteTheme
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
class AutomationUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val keys = setOf(Keys.MENU, Keys.DOWN, Keys.OK, Keys.DIGIT_5, "X:Sleep")

    private var saved: Automation? = null
    private var tested: List<AutomationStep>? = null

    private fun editor(initial: Automation? = null) {
        rule.setContent {
            HotelRemoteTheme {
                AutomationEditorScreen(
                    initial = initial,
                    keys = keys,
                    progress = null,
                    onTest = { _, steps -> tested = steps },
                    onCancelRun = {},
                    onSave = { saved = it },
                    onCancel = {},
                )
            }
        }
    }

    private fun addStep(key: String) {
        rule.onNodeWithTag("auto_add_step").performClick()
        rule.onNodeWithTag("pick_$key").performClick()
    }

    @Test fun editorAddsMovesAndRemovesSteps() {
        editor()
        rule.onNodeWithTag("auto_save").assertIsNotEnabled()
        addStep(Keys.MENU)
        addStep("X:Sleep")
        addStep(Keys.OK)
        rule.onNodeWithTag("step_key_0").assertTextEquals("1. Menü")
        rule.onNodeWithTag("step_key_1").assertTextEquals("2. Sleep")
        rule.onNodeWithTag("step_wait_0").assertTextEquals("Danach warten: 1,5 s")
        rule.onNodeWithTag("step_up_0").assertIsNotEnabled()
        rule.onNodeWithTag("step_down_2").assertIsNotEnabled()
        // Sleep nach oben, OK entfernen, Wartezeit des ersten Schritts verlängern.
        rule.onNodeWithTag("step_up_1").performClick()
        rule.onNodeWithTag("step_remove_2").performClick()
        rule.onNodeWithTag("step_wait_plus_0").performClick()
        rule.onNodeWithTag("step_wait_0").assertTextEquals("Danach warten: 2 s")
        rule.onNodeWithTag("auto_test").performClick()
        assertEquals(listOf(AutomationStep("X:Sleep", 2000), AutomationStep(Keys.MENU)), tested)
        // Ohne Namen kein Speichern.
        rule.onNodeWithTag("auto_save").assertIsNotEnabled()
        rule.onNodeWithTag("auto_name").performTextInput("Einschlafen")
        rule.onNodeWithTag("auto_save").assertIsEnabled().performClick()
        assertEquals("Einschlafen", saved?.name)
        assertEquals(listOf(AutomationStep("X:Sleep", 2000), AutomationStep(Keys.MENU)), saved?.steps)
    }

    @Test fun editorInputSurvivesRecreation() {
        val restore = StateRestorationTester(rule)
        restore.setContent {
            HotelRemoteTheme {
                AutomationEditorScreen(null, keys, null, { _, _ -> }, {}, { saved = it }, {})
            }
        }
        rule.onNodeWithTag("auto_name").performTextInput("Abend")
        addStep(Keys.MENU)
        rule.onNodeWithTag("step_wait_plus_0").performClick()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("auto_save").performClick()
        assertEquals("Abend", saved?.name)
        assertEquals(listOf(AutomationStep(Keys.MENU, 2000)), saved?.steps)
    }

    @Test fun editorKeepsIdOfExistingAutomation() {
        editor(Automation("a1", "Alt", listOf(AutomationStep(Keys.OK, 500))))
        rule.onNodeWithTag("step_wait_minus_0").assertIsNotEnabled()
        rule.onNodeWithTag("auto_save").performClick()
        assertEquals(Automation("a1", "Alt", listOf(AutomationStep(Keys.OK, 500))), saved)
    }

    @Test fun keyNamesAreReadable() {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        assertEquals("Lauter", keyName(res, Keys.VOL_UP))
        assertEquals("Pfeil runter", keyName(res, Keys.DOWN))
        assertEquals("Ziffer 5", keyName(res, Keys.DIGIT_5))
        assertEquals("HDMI 2", keyName(res, Keys.HDMI2))
        assertEquals("Sleep", keyName(res, "X:Sleep"))
        assertEquals(listOf(Keys.DIGIT_5, Keys.MENU, Keys.OK, Keys.DOWN, "X:Sleep"), orderedKeys(keys))
    }

    @Test fun changeDelayStaysInRange() {
        assertEquals(500L, changeDelay(500, up = false))
        assertEquals(10_000L, changeDelay(10_000, up = true))
        assertEquals(2_000L, changeDelay(1_500, up = true))
    }

    private fun sheet(
        timer: SleepTimer?,
        progress: AutomationProgress? = null,
        onRun: (Automation) -> Unit = {},
        onTimer: () -> Unit = {},
        onRecord: () -> Unit = {},
    ) {
        rule.setContent {
            HotelRemoteTheme {
                QuickSheetContent(
                    automations = listOf(Automation("a1", "Einschlafen", listOf(AutomationStep(Keys.OK)))),
                    progress = progress,
                    timer = timer,
                    now = 0L,
                    onRun = onRun,
                    onCancelRun = {},
                    onTimer = onTimer,
                    onCancelTimer = {},
                    onEdit = {},
                    onRecord = onRecord,
                )
            }
        }
    }

    @Test fun sheetShowsAutomationsAndStartsTimer() {
        var run: Automation? = null
        var timer = 0
        sheet(timer = null, onRun = { run = it }, onTimer = { timer++ })
        rule.onNodeWithTag("quick_run_a1").performClick()
        assertEquals("a1", run?.id)
        rule.onNodeWithText("Sleep-Timer (Handy)").performClick()
        assertEquals(1, timer)
        rule.onNodeWithTag("quick_point_hint").assertExists()
        rule.onNodeWithTag("quick_edit").assertExists()
        rule.onNodeWithTag("automation_progress").assertDoesNotExist()
    }

    @Test fun sheetShowsRunningTimerAndProgress() {
        sheet(
            timer = SleepTimer(endAt = 42 * 60_000L - 30_000L, remoteId = "r1", action = SleepAction.PowerOff, exact = false),
            progress = AutomationProgress("Einschlafen", 2, 3),
        )
        rule.onNodeWithText("Sleep-Timer: noch 42 min").assertExists()
        rule.onNodeWithTag("quick_timer_cancel").assertExists()
        rule.onNodeWithTag("quick_timer_inexact").assertExists()
        rule.onNodeWithText("Einschlafen · Schritt 2 von 3").assertExists()
        rule.onNodeWithTag("quick_timer").assertDoesNotExist()
    }

    @Test fun sheetStartsRecording() {
        var recorded = 0
        sheet(timer = null, onRecord = { recorded++ })
        rule.onNodeWithTag("quick_record").assertTextEquals("● Neue Automation aufnehmen").performClick()
        assertEquals(1, recorded)
    }

    @Test fun listStartsRecording() {
        var recorded = 0
        var edited = false
        rule.setContent {
            HotelRemoteTheme {
                AutomationListScreen(emptyList(), null, {}, {}, { edited = true }, {}, {}, onRecord = { recorded++ })
            }
        }
        rule.onNodeWithText("● Aufnehmen").performClick()
        assertEquals(1, recorded)
        assertEquals(false, edited)
    }

    /** Raum ohne Fernseher: App.kt übergibt null, dann gibt es keinen Aufnahme-Knopf. */
    @Test fun noRecordButtonsWithoutRecordAction() {
        rule.setContent {
            HotelRemoteTheme {
                Column {
                    AutomationListScreen(emptyList(), null, {}, {}, {}, {}, {}, onRecord = null)
                    QuickSheetContent(emptyList(), null, null, 0L, {}, {}, {}, {}, {}, onRecord = null)
                }
            }
        }
        rule.onNodeWithTag("automation_record").assertDoesNotExist()
        rule.onNodeWithTag("quick_record").assertDoesNotExist()
        rule.onNodeWithTag("automation_new").assertExists()
    }

    @Test fun editorRecordsWithCurrentNameAndStepsAndShowsDraft() {
        var recordedWith: Pair<String, List<AutomationStep>>? = null
        val draft = AutomationDraft("Abend", listOf(AutomationStep(Keys.MENU, 2_500), AutomationStep(Keys.OK)))
        rule.setContent {
            HotelRemoteTheme {
                AutomationEditorScreen(
                    initial = Automation("a1", "Alt", listOf(AutomationStep(Keys.MENU))),
                    keys = keys,
                    progress = null,
                    onTest = { _, _ -> },
                    onCancelRun = {},
                    onSave = { saved = it },
                    onCancel = {},
                    onRecord = { name, steps -> recordedWith = name to steps },
                    draft = draft,
                )
            }
        }
        rule.onNodeWithTag("step_key_1").assertTextEquals("2. OK")
        rule.onNodeWithTag("step_wait_0").assertTextEquals("Danach warten: 2,5 s")
        rule.onNodeWithTag("auto_record").performClick()
        assertEquals("Abend" to draft.steps, recordedWith)
        rule.onNodeWithTag("auto_save").performClick()
        assertEquals(Automation("a1", "Abend", draft.steps), saved)
    }
}
