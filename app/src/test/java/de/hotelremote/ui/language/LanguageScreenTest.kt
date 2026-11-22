package de.hotelremote.ui.language

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
class LanguageScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val chosen = mutableListOf<String>()
    private var backs = 0

    private fun show(initial: String, withBack: Boolean = false) {
        rule.setContent {
            HotelRemoteTheme {
                LanguageScreen(
                    languages = AppLanguages.all,
                    initial = AppLanguages.all.first { it.tag == initial },
                    onContinue = { chosen += it.tag },
                    onBack = if (withBack) ({ backs++ }) else null,
                )
            }
        }
    }

    @Test fun listsElevenLanguagesInTheirOwnNames() {
        show("en")
        val names = listOf(
            "Deutsch", "English", "中文（简体）", "हिन्दी", "Español", "Français",
            "العربية", "বাংলা", "Português", "Русский", "Bahasa Indonesia",
        )
        assertEquals(names, AppLanguages.all.map { it.nativeName })
        AppLanguages.all.forEach { lang ->
            rule.onNodeWithTag("language_list").performScrollToNode(hasTestTag("language_${lang.tag}"))
            rule.onNodeWithText(lang.nativeName).assertExists()
        }
    }

    @Test fun preselectedLanguageIsSelectedAndContinueReportsIt() {
        show("fr")
        rule.onNodeWithTag("language_fr").assertIsSelected()
        rule.onNodeWithTag("language_de").assertIsNotSelected()
        rule.onNodeWithTag("language_continue").performClick()
        assertEquals(listOf("fr"), chosen)
    }

    @Test fun tappingAnotherLanguageChangesSelection() {
        show("en")
        rule.onNodeWithTag("language_de").performClick()
        rule.onNodeWithTag("language_de").assertIsSelected()
        rule.onNodeWithTag("language_en").assertIsNotSelected()
        rule.onNodeWithTag("language_continue").performClick()
        assertEquals(listOf("de"), chosen)
    }

    @Test fun firstStartHasNoBackArrow() {
        show("de")
        rule.onNodeWithTag("language_back").assertDoesNotExist()
        rule.onNodeWithText("Sprache wählen").assertExists()
        rule.onNodeWithText("Weiter").assertExists()
    }

    @Test fun backArrowLeavesWithoutChoosing() {
        show("de", withBack = true)
        rule.onNodeWithContentDescription("Zurück").performClick()
        assertEquals(1, backs)
        assertEquals(emptyList<String>(), chosen)
    }
}
