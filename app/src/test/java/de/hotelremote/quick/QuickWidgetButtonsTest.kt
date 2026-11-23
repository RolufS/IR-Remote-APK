package de.hotelremote.quick

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.sleeptimer.FakeTransmitter
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File

/** Frei belegte Knöpfe: Speichern je Widget, RemoteViews, Benachrichtigung, Receiver mit Tasten und Automationen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickWidgetButtonsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tx = FakeTransmitter()
    private val store = RemoteStore(File(app.filesDir, "remotes.json"))
    private val settings get() = QuickControls.settings(app)
    private val widgets get() = shadowOf(AppWidgetManager.getInstance(app))
    private val dispatcher = StandardTestDispatcher()

    private fun sig(n: Int) = IrSignal(freq = 38000, frame = listOf(n, 100, 200), periodMs = 100)

    private val sleep = Automation("a1", "Sleep 60", listOf(AutomationStep(Keys.MENU, 1000), AutomationStep(Keys.OK)))

    private fun addRemote(vararg keys: Pair<String, Int>, automations: List<Automation> = emptyList()) = store.add(
        SavedRemote(id = "r1", brand = "LG", codeLabel = "Code 1", keys = keys.associate { (k, n) -> k to sig(n) }, lastUsed = 1, automations = automations),
    )

    private fun press(button: QuickButton) = QuickControlReceiver().onReceive(app, QuickControlReceiver.intent(app, button, "test/0"))

    /** Automationen laufen auf dem Test-Dispatcher (virtuelle Zeit), Toasts kommen über den Main-Looper. */
    private fun finishAsync() {
        dispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        QuickControls.reset()
        QuickControls.transmitter = { tx }
        QuickControls.automationDispatcher = dispatcher
    }

    @After fun tearDown() = QuickControls.reset()

    @Test fun widgetButtonsPerIdWithDefault() {
        assertEquals(QuickButtons.DEFAULT, settings.widgetButtons(7))
        settings.setWidgetButtons(7, listOf(QuickButton.Key(Keys.MUTE), QuickButton.Run("a1", "Sleep 60")))
        settings.setWidgetButtons(8, listOf(QuickButton.Key(Keys.HDMI2)))
        assertEquals(listOf(QuickButton.Key(Keys.MUTE), QuickButton.Run("a1", "Sleep 60")), settings.widgetButtons(7))
        assertEquals(listOf(QuickButton.Key(Keys.HDMI2)), settings.widgetButtons(8))
        settings.setWidgetButtons(9, List(8) { QuickButton.Key("DIGIT_$it") })
        assertEquals(6, settings.widgetButtons(9).size)
    }

    @Test fun deletingWidgetRemovesItsButtons() {
        val id = widgets.createWidget(QuickWidgetProvider::class.java, R.layout.widget_quick)
        settings.setWidgetButtons(id, listOf(QuickButton.Key(Keys.MUTE)))
        settings.setWidgetButtons(id + 1, listOf(QuickButton.Key(Keys.OK)))
        QuickWidgetProvider().onDeleted(app, intArrayOf(id))
        assertEquals(QuickButtons.DEFAULT, settings.widgetButtons(id))
        assertEquals(listOf(QuickButton.Key(Keys.OK)), settings.widgetButtons(id + 1))
    }

    @Test fun notificationButtonsDefaultAndMaxFive() {
        assertEquals(QuickButtons.DEFAULT, settings.notificationButtons)
        settings.notificationButtons = List(6) { QuickButton.Key("DIGIT_$it") }
        assertEquals(5, settings.notificationButtons.size)
        assertEquals(5, settings.load().notificationButtons.size)
    }

    private fun visibleSlots(root: View, max: Int) = (0 until max).count { root.findViewById<View>(slotRootId(it)).visibility == View.VISIBLE }

    @Test fun widgetShowsOneSlotPerButton() {
        addRemote(Keys.POWER to 1)
        val id = widgets.createWidget(QuickWidgetProvider::class.java, R.layout.widget_quick)
        assertEquals(4, visibleSlots(widgets.getViewFor(id), 6))

        settings.setWidgetButtons(id, listOf(QuickButton.Key(Keys.MUTE), QuickButton.Key("X:Netflix")))
        QuickWidgetProvider.update(app, id)
        assertEquals(2, visibleSlots(widgets.getViewFor(id), 6))

        settings.setWidgetButtons(id, List(6) { QuickButton.Key("DIGIT_$it") })
        QuickControls.refresh(app)
        val view = widgets.getViewFor(id)
        assertEquals(6, visibleSlots(view, 6))
        // 5–6 Knöpfe: kleinere Knöpfe, damit sie in 4 Zellen passen.
        val small = view.findViewById<View>(slotButtonId(0)).layoutParams.width
        assertEquals((38 * app.resources.displayMetrics.density).toInt(), small)
        assertEquals("0", view.findViewById<TextView>(slotLabelId(0)).text.toString())
    }

    @Test fun widgetLabelsAndIcons() {
        val id = widgets.createWidget(QuickWidgetProvider::class.java, R.layout.widget_quick)
        settings.setWidgetButtons(
            id,
            listOf(QuickButton.Run("a1", "Einschlafen"), QuickButton.Key(Keys.VOL_UP), QuickButton.Key("X:Netflix"), QuickButton.Key(Keys.RED)),
        )
        QuickWidgetProvider.update(app, id)
        val view = widgets.getViewFor(id)
        fun label(i: Int) = view.findViewById<TextView>(slotLabelId(i))
        fun icon(i: Int) = view.findViewById<View>(slotIconId(i))
        assertEquals("Einsc…", label(0).text.toString())
        assertEquals(View.VISIBLE, icon(0).visibility)
        assertEquals(View.GONE, label(1).visibility)
        assertEquals(View.VISIBLE, icon(1).visibility)
        assertEquals("Netfl…", label(2).text.toString())
        assertEquals(View.GONE, icon(2).visibility)
        assertEquals(View.GONE, label(3).visibility)
        assertEquals("Einschlafen", view.findViewById<View>(slotButtonId(0)).contentDescription)
    }

    @Test fun notificationUsesItsOwnButtons() {
        addRemote(Keys.POWER to 1)
        settings.lockScreen = true
        settings.notificationButtons = listOf(QuickButton.Key(Keys.MUTE), QuickButton.Key(Keys.OK))
        QuickControls.refresh(app)
        val n = shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(QuickNotification.ID)
        assertNotNull(n)
        val custom = n.contentView ?: error("keine Ansicht")
        val view = custom.apply(app, FrameLayout(app))
        assertEquals(2, visibleSlots(view, 5))
        assertEquals("OK", view.findViewById<TextView>(slotLabelId(1)).text.toString())
        assertNull(view.findViewById(slotRootId(5)))
    }

    @Test fun receiverSendsAnyKey() {
        addRemote(Keys.MUTE to 5, "X:Netflix" to 9)
        press(QuickButton.Key(Keys.MUTE))
        press(QuickButton.Key("X:Netflix"))
        assertEquals(listOf(5, 9), tx.sent)
        press(QuickButton.Key(Keys.HDMI4))
        assertEquals(app.getString(R.string.quick_key_missing), ShadowToast.getTextOfLatestToast())
    }

    /** Widgets aus Version 1.4 schicken noch den Namen der Aktion, bis sie neu aufgebaut sind. */
    @Test fun receiverAcceptsOldWidgetIntent() {
        addRemote(Keys.VOL_UP to 3)
        val old = Intent(app, QuickControlReceiver::class.java).setAction(QuickControlReceiver.ACTION_SEND).putExtra("quick_action", "VOL_UP")
        QuickControlReceiver().onReceive(app, old)
        assertEquals(listOf(3), tx.sent)
    }

    @Test fun receiverRunsAutomationWithDelays() {
        addRemote(Keys.MENU to 1, Keys.OK to 2, automations = listOf(sleep))
        press(QuickButton.Run("a1", "Sleep 60"))
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(1), tx.sent)
        dispatcher.scheduler.advanceTimeBy(1001)
        assertEquals(listOf(1, 2), tx.sent)
        finishAsync()
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test fun secondAutomationWhileRunningIsBusy() {
        addRemote(Keys.MENU to 1, Keys.OK to 2, automations = listOf(sleep))
        press(QuickButton.Run("a1", "Sleep 60"))
        dispatcher.scheduler.runCurrent()
        press(QuickButton.Run("a1", "Sleep 60"))
        dispatcher.scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(app.getString(R.string.auto_busy), ShadowToast.getTextOfLatestToast())
        finishAsync()
        assertEquals(listOf(1, 2), tx.sent)
    }

    @Test fun missingOrTooLongAutomationToasts() {
        addRemote(Keys.MENU to 1, Keys.OK to 2, automations = listOf(Automation("long", "Lang", listOf(AutomationStep(Keys.MENU, 9000), AutomationStep(Keys.OK)))))
        press(QuickButton.Run("a1", "Sleep 60"))
        assertEquals(app.getString(R.string.quick_auto_missing), ShadowToast.getTextOfLatestToast())
        press(QuickButton.Run("long", "Lang"))
        assertEquals(app.getString(R.string.quick_auto_too_long), ShadowToast.getTextOfLatestToast())
        finishAsync()
        assertEquals(emptyList<Int>(), tx.sent)
    }

    /** Task 23: Toggle-Bit (RC5/RC6) wechselt auch für frei belegte Tasten und Automationen über Prozessgrenzen. */
    @Test fun toggleSurvivesRestartForAnyButton() {
        val rc5 = IrSignal(freq = 36000, frame = listOf(11, 1, 1), frameAlt = listOf(12, 1, 1), periodMs = 114)
        store.add(
            SavedRemote(
                id = "r1",
                brand = "Philips",
                codeLabel = "RC5",
                keys = mapOf(Keys.MUTE to rc5),
                lastUsed = 1,
                automations = listOf(Automation("m", "Stumm", listOf(AutomationStep(Keys.MUTE)))),
            ),
        )
        fun restart() {
            QuickControls.reset()
            QuickControls.transmitter = { tx }
            QuickControls.automationDispatcher = dispatcher
        }
        press(QuickButton.Key(Keys.MUTE))
        restart()
        press(QuickButton.Run("m", "Stumm"))
        finishAsync()
        restart()
        press(QuickButton.Key(Keys.MUTE))
        assertEquals(listOf(11, 12, 11), tx.sent)
    }
}
