package de.hotelremote.quick

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Einrichten beim Hinzufügen: Ergebnis mit EXTRA_APPWIDGET_ID, Belegung gespeichert, Widget neu aufgebaut. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetConfigActivityTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val widgets get() = shadowOf(AppWidgetManager.getInstance(app))
    private val settings get() = QuickControls.settings(app)

    private fun launch(widgetId: Int): ActivityScenario<WidgetConfigActivity> =
        ActivityScenario.launchActivityForResult(WidgetConfigActivity.intent(app, widgetId))

    private fun ActivityScenario<*>.resultId() = result.resultData?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

    @Test fun saveReturnsOkWithWidgetIdAndStoresButtons() {
        val sig = IrSignal(freq = 38000, frame = listOf(1, 2, 3), periodMs = 100)
        RemoteStore(File(app.filesDir, "remotes.json")).add(
            SavedRemote(id = "r1", brand = "LG", codeLabel = "Code 1", keys = mapOf(Keys.POWER to sig, Keys.VOL_UP to sig), lastUsed = 1),
        )
        val id = widgets.createWidget(QuickWidgetProvider::class.java, R.layout.widget_quick)
        val scenario = launch(id)
        rule.onNodeWithTag("qb_list").performScrollToNode(hasTestTag("qb_remove_3"))
        rule.onNodeWithTag("qb_remove_3").performClick()
        rule.onNodeWithTag("qb_save").performClick()
        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(id, scenario.resultId())
        assertEquals(QuickButtons.DEFAULT.take(3), settings.widgetButtons(id))
        // Vor Android 12 baut das System das Widget nach dem Einrichten nicht selbst neu auf.
        val view = widgets.getViewFor(id)
        assertEquals(View.GONE, view.findViewById<View>(slotRootId(3)).visibility)
    }

    @Test fun cancelReturnsCanceledWithWidgetId() {
        val id = widgets.createWidget(QuickWidgetProvider::class.java, R.layout.widget_quick)
        settings.setWidgetButtons(id, listOf(QuickButton.Key(Keys.MUTE)))
        val scenario = launch(id)
        rule.onNodeWithTag("qb_cancel").performClick()
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertEquals(id, scenario.resultId())
        assertEquals(listOf(QuickButton.Key(Keys.MUTE)), settings.widgetButtons(id))
    }

    /** Exportiert: fremde oder fehlende IDs werden nicht eingerichtet. */
    @Test fun unknownWidgetIdFinishesCanceled() {
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(Intent(app, WidgetConfigActivity::class.java))
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        val other = launch(4711)
        assertEquals(Activity.RESULT_CANCELED, other.result.resultCode)
        assertEquals(4711, other.resultId())
        assertEquals(QuickButtons.DEFAULT, settings.widgetButtons(4711))
    }
}
