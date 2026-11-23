package de.hotelremote.ui.automation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.automation.AutomationTemplate
import de.hotelremote.automation.SleepTemplates
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.ui.AutomationDraft
import de.hotelremote.ui.remote.QuickSheetContent
import de.hotelremote.ui.theme.HotelRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TemplateUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val res get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val vestelKeys = setOf("X:Sleep", Keys.MENU, Keys.HOME, Keys.OK, Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT, Keys.EXIT, Keys.BACK)

    private fun list(templates: de.hotelremote.automation.TemplateSections, onTemplate: (AutomationTemplate) -> Unit = {}) {
        rule.setContent {
            HotelRemoteTheme {
                AutomationListScreen(
                    automations = listOf(Automation("a1", "Abend", listOf(AutomationStep(Keys.OK)))),
                    progress = null,
                    onRun = {},
                    onCancelRun = {},
                    onEdit = {},
                    onDelete = {},
                    onBack = {},
                    templates = templates,
                    onTemplate = onTemplate,
                )
            }
        }
    }

    private fun scrollTo(tag: String) = rule.onNodeWithTag("automation_list").performScrollToNode(hasTestTag(tag))

    @Test fun listShowsMatchingTemplatesFirstAndOthersOnDemand() {
        var picked: AutomationTemplate? = null
        list(SleepTemplates.forRemote("Vestel", vestelKeys)) { picked = it }
        rule.onNodeWithTag("automation_a1").assertExists()
        scrollTo("template_sleep_key_60")
        rule.onNodeWithText("Sleep-Taste 6× (≈ 60 min)").assertExists()
        rule.onAllNodesWithText("Angenommene Stufen: 10, 20, 30, … min", substring = true).assertCountEquals(2)
        // Andere Marken erst nach dem Aufklappen.
        rule.onNodeWithTag("template_samsung_tizen_2021").assertDoesNotExist()
        scrollTo("templates_more")
        rule.onNodeWithTag("templates_more").performClick()
        scrollTo("template_samsung_tizen_2021")
        rule.onNodeWithText("Samsung Tizen ≤ 2021: Menüweg (≈ 60 min)").assertExists()
        rule.onNodeWithTag("template_samsung_tizen_2021").performClick()
        assertEquals("samsung_tizen_2021", picked?.id)
        scrollTo("template_sleep_key_60")
        rule.onNodeWithTag("template_sleep_key_60").performClick()
        assertEquals(List(6) { "X:Sleep" }, picked?.steps?.map { it.key })
    }

    @Test fun listSaysWhenNothingMatches() {
        list(SleepTemplates.forRemote("LG", setOf(Keys.POWER)))
        scrollTo("templates_none")
        rule.onNodeWithTag("templates_more").assertDoesNotExist()
    }

    @Test fun editorFromTemplateShowsNoticesAndSavesANormalAutomation() {
        val template = SleepTemplates.forRemote("LG", vestelKeys + "X:Q Menu").matching.first { it.id == "lg_webos" }
        var saved: Automation? = null
        rule.setContent {
            HotelRemoteTheme {
                AutomationEditorScreen(
                    initial = null,
                    keys = vestelKeys + "X:Q Menu",
                    progress = null,
                    onTest = { _, _ -> },
                    onCancelRun = {},
                    onSave = { saved = it },
                    onCancel = {},
                    draft = AutomationDraft(templateName(res, template), template.steps),
                    template = template,
                )
            }
        }
        rule.onNodeWithTag("template_notice_0")
            .assertTextEquals("Pfad laut Handbuch, Tastenzahl geschätzt – bitte mit „Testen“ prüfen.")
        rule.onNodeWithTag("template_notice_1").assertExists()
        rule.onNodeWithTag("editor_steps").performScrollToNode(hasTestTag("step_key_3"))
        rule.onNodeWithTag("step_key_3").assertTextEquals("4. Q Menu")
        rule.onNodeWithTag("step_wait_3").assertTextEquals("Danach warten: 2,5 s")
        rule.onNodeWithTag("auto_save").performClick()
        assertNotNull(saved)
        assertEquals("Sleep-Timer 60 min", saved!!.name)
        assertEquals(template.steps, saved!!.steps)
    }

    @Test fun textsNameConfidenceAndNote() {
        val sony = SleepTemplates.forRemote("Sony", setOf("X:Sleep")).matching.first { it.minutes == 60 }
        assertEquals("Sleep-Taste 4× (≈ 60 min)", templateTitle(res, sony))
        assertEquals("Angenommene Stufen: 15, 30, 45, 60, 90, 120 min", templateSubtitle(res, sony))
        assertEquals(
            listOf(
                "Stufen laut Handbuch, ungeprüft – bitte mit „Testen“ prüfen.",
                "Der Fernseher zeigt den eingestellten Wert an – bitte beim ersten Mal prüfen.",
            ),
            templateNotices(res, sony),
        )
        val vestel = SleepTemplates.forRemote("Vestel", setOf("X:Sleep")).matching.first()
        assertEquals("Geschätzt, ungeprüft – bitte mit „Testen“ prüfen.", templateNotices(res, vestel).first())
        val panasonic = SleepTemplates.forRemote("Panasonic", vestelKeys).matching.first { it.id == "panasonic_viera" }
        assertEquals("Panasonic Viera: Menüweg (≈ 30 min)", templateTitle(res, panasonic))
        assertEquals("Ungeprüft – bitte einmal testen", templateSubtitle(res, panasonic))
    }

    private fun sheet(automations: List<Automation>, onEdit: () -> Unit = {}) {
        rule.setContent {
            HotelRemoteTheme {
                QuickSheetContent(automations, null, null, 0L, {}, {}, {}, {}, onEdit)
            }
        }
    }

    @Test fun sheetPointsToTemplatesWithoutOwnAutomations() {
        var edited = 0
        sheet(emptyList()) { edited++ }
        rule.onNodeWithTag("quick_templates").assertTextEquals("Sleep-Timer-Vorlagen ansehen").performClick()
        assertEquals(1, edited)
    }

    @Test fun sheetHidesTemplateHintWithOwnAutomations() {
        sheet(listOf(Automation("a1", "Abend", listOf(AutomationStep(Keys.OK)))))
        rule.onNodeWithTag("quick_templates").assertDoesNotExist()
    }
}
