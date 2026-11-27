package de.hotelremote.ui.about

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.hotelremote.BuildConfig
import de.hotelremote.ui.theme.HotelRemoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreenTest {
    @get:Rule
    val rule = createComposeRule()

    /** Die Version kommt aus dem Build, nicht aus den Übersetzungen. */
    @Test fun showsVersionFromBuild() {
        rule.setContent { HotelRemoteTheme { AboutScreen(meta = emptyMap(), dbError = false, onBack = {}) } }
        rule.onNodeWithText("Fernbedienung ${BuildConfig.VERSION_NAME} – werbefrei; Internet nur für die optionale Außentemperatur.")
            .assertExists()
    }

    /** Quellen der Klima-Codes und der Wetterdaten mit Lizenz. */
    @Test fun namesSmartIrAndOpenMeteo() {
        rule.setContent { HotelRemoteTheme { AboutScreen(meta = emptyMap(), dbError = false, onBack = {}) } }
        rule.onNodeWithText("Klima-Codes: SmartIR (MIT), github.com/smartHomeHub/SmartIR").assertExists()
        rule.onNodeWithText("Wetterdaten: Open-Meteo.com (CC BY 4.0)").assertExists()
    }
}
