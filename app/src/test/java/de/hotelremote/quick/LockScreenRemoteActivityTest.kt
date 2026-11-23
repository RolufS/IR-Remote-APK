package de.hotelremote.quick

import android.app.Application
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.sleeptimer.FakeTransmitter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File

/** Ganze Fernbedienung über dem Sperrbildschirm: eingeschränkt, ohne Fernbedienung oder Erlaubnis sofort zu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockScreenRemoteActivityTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tx = FakeTransmitter()
    private val store = RemoteStore(File(app.filesDir, "remotes.json"))

    private fun sig(n: Int) = IrSignal(freq = 38000, frame = listOf(n, 100, 200), periodMs = 100)

    private fun addRemote(id: String, lastUsed: Long, name: String) = store.add(
        SavedRemote(
            id = id,
            name = name,
            brand = "LG",
            codeLabel = "Code 1",
            keys = mapOf(Keys.MUTE to sig(5), Keys.VOL_UP to sig(6), "X:Netflix" to sig(9)),
            lastUsed = lastUsed,
        ),
    )

    private fun launch(): ActivityScenario<LockScreenRemoteActivity> = ActivityScenario.launch(LockScreenRemoteActivity.intent(app))

    @Before fun setUp() {
        QuickControls.reset()
        QuickControls.transmitter = { tx }
    }

    @After fun tearDown() = QuickControls.reset()

    @Test fun showsWhenLockedAndIsRestricted() {
        addRemote("old", 1, "Wohnzimmer")
        addRemote("r1", 2, "Zimmer 412")
        QuickControls.settings(app).fullRemote = true
        val scenario = launch()
        scenario.onActivity { assertTrue(shadowOf(it).showWhenLocked) }
        rule.onNodeWithTag("header_title").assertExists()
        rule.onNodeWithText("Zimmer 412").assertExists()
        rule.onNodeWithTag("header_list").assertDoesNotExist()
        rule.onNodeWithTag("header_quick").assertDoesNotExist()
        // Kein Umschalten der Fernbedienung.
        rule.onNodeWithTag("header_title").performClick()
        rule.onNodeWithTag("switch_menu").assertDoesNotExist()
        // ⋮ nur „Weitere Tasten“.
        rule.onNodeWithTag("header_menu").performClick()
        rule.onNodeWithText(app.getString(R.string.menu_extra)).assertExists()
        rule.onNodeWithText(app.getString(R.string.menu_quick)).assertDoesNotExist()
        rule.onNodeWithText(app.getString(R.string.menu_automations)).assertDoesNotExist()
        rule.onNodeWithText(app.getString(R.string.menu_wizard)).assertDoesNotExist()
        rule.onNodeWithText(app.getString(R.string.menu_extra)).performClick()
        rule.onNodeWithText("Netflix").performClick()
        rule.waitUntil { tx.sent.isNotEmpty() }
        assertEquals(listOf(9), tx.sent)
        rule.onNodeWithTag("extra_back").performClick()
        rule.onNodeWithTag("key_MUTE").performClick()
        rule.waitUntil { tx.sent.size == 2 }
        assertEquals(listOf(9, 5), tx.sent)
    }

    @Test fun withoutRemoteShowsHintAndCloses() {
        QuickControls.settings(app).fullRemote = true
        val scenario = launch()
        assertEquals(app.getString(R.string.lock_no_remote), ShadowToast.getTextOfLatestToast())
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test fun closesWhenNotAllowed() {
        addRemote("r1", 1, "Zimmer 412")
        val scenario = launch()
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    /** Ein/Aus-Taste des Handys (Bildschirm aus): die Activity schließt, der Sperrbildschirm ist wieder da. */
    @Test fun closesWhenStopped() {
        addRemote("r1", 1, "Zimmer 412")
        QuickControls.settings(app).fullRemote = true
        val scenario = launch()
        var activity: LockScreenRemoteActivity? = null
        scenario.onActivity { activity = it }
        scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(activity!!.isFinishing)
    }
}
