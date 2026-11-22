package de.hotelremote.ui.language

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LanguageChoiceTest {
    private fun prefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(LanguageChoice.PREFS, Context.MODE_PRIVATE)

    @Test fun neededUntilChosenAndRemembered() {
        assertTrue(LanguageChoice(prefs()).needed)
        LanguageChoice(prefs()).markChosen()
        // Neue Instanz liest denselben gespeicherten Wert (wie nach einem Neustart der App).
        assertFalse(LanguageChoice(prefs()).needed)
    }
}
