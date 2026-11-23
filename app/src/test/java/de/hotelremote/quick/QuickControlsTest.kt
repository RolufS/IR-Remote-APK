package de.hotelremote.quick

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.sleeptimer.FakeTransmitter
import de.hotelremote.sleeptimer.SleepTimerBootReceiver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File

/** Sendepfad, Einstellungen, Kachel-Zustand, Widget und Sperrbildschirm-Benachrichtigung mit Fake-Sender. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickControlsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tx = FakeTransmitter()
    private val store = RemoteStore(File(app.filesDir, "remotes.json"))
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    private fun sig(n: Int) = IrSignal(freq = 38000, frame = listOf(n, 100, 200), periodMs = 100)

    private fun remote(id: String, lastUsed: Long, vararg keys: Pair<String, Int>, name: String? = null) =
        SavedRemote(id = id, name = name, brand = "LG", codeLabel = "Code 1", keys = keys.associate { (k, n) -> k to sig(n) }, lastUsed = lastUsed)

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        QuickControls.reset()
        QuickControls.transmitter = { tx }
    }

    @After fun tearDown() = QuickControls.reset()

    @Test fun sendsToLastUsedRemote() {
        store.add(remote("old", 1, Keys.VOL_UP to 1))
        store.add(remote("new", 2, Keys.VOL_UP to 2, Keys.VOL_DOWN to 3))
        assertEquals(QuickResult.SENT, QuickControls.send(app, QuickAction.VOL_UP))
        assertEquals(QuickResult.SENT, QuickControls.send(app, QuickAction.VOL_DOWN))
        assertEquals(listOf(2, 3), tx.sent)
    }

    @Test fun powerAndInputFollowKeyChoice() {
        store.add(remote("r1", 1, Keys.POWER_ON to 1, Keys.POWER_OFF to 2, Keys.HDMI1 to 11, Keys.HDMI2 to 12, Keys.SOURCE to 20))
        QuickControls.send(app, QuickAction.POWER)
        QuickControls.send(app, QuickAction.INPUT)
        QuickControls.settings(app).inputKey = Keys.HDMI2
        QuickControls.send(app, QuickAction.INPUT)
        assertEquals(listOf(1, 11, 12), tx.sent)
    }

    /** RC5/RC6: aufeinanderfolgende Tipps wechseln frame/frameAlt, auch wenn der Prozess dazwischen endet. */
    @Test fun toggleAlternatesAcrossProcessRestart() {
        val rc5 = IrSignal(freq = 36000, frame = listOf(11, 1, 1), frameAlt = listOf(12, 1, 1), periodMs = 114)
        store.add(SavedRemote(id = "r1", brand = "Philips", codeLabel = "RC5", keys = mapOf(Keys.VOL_UP to rc5), lastUsed = 1))
        QuickControls.send(app, QuickAction.VOL_UP)
        // Neuer Prozess: frischer QuickControls-Zustand und neuer DirectSender.
        QuickControls.reset()
        QuickControls.transmitter = { tx }
        QuickControls.send(app, QuickAction.VOL_UP)
        QuickControls.reset()
        QuickControls.transmitter = { tx }
        QuickControls.send(app, QuickAction.VOL_UP)
        assertEquals(listOf(11, 12, 11), tx.sent)
    }

    @Test fun errorsSendNothing() {
        assertEquals(QuickResult.NO_REMOTE, QuickControls.send(app, QuickAction.POWER))
        store.add(remote("r1", 1, Keys.POWER to 1))
        assertEquals(QuickResult.KEY_MISSING, QuickControls.send(app, QuickAction.VOL_UP))
        assertEquals(emptyList<Int>(), tx.sent)
        tx.fail = true
        assertEquals(QuickResult.SEND_FAILED, QuickControls.send(app, QuickAction.POWER))
    }

    @Test fun receiverSendsAndToastsErrors() {
        QuickControlReceiver().onReceive(app, QuickControlReceiver.intent(app, QuickAction.POWER))
        assertEquals(app.getString(R.string.quick_no_remote), ShadowToast.getTextOfLatestToast())
        store.add(remote("r1", 1, Keys.POWER to 7))
        QuickControlReceiver().onReceive(app, QuickControlReceiver.intent(app, QuickAction.POWER))
        assertEquals(listOf(7), tx.sent)
        // Unbekannte Aktion wird ignoriert.
        QuickControlReceiver().onReceive(app, Intent(app, QuickControlReceiver::class.java).setAction("x"))
        assertEquals(listOf(7), tx.sent)
    }

    @Test fun tileUnavailableWithoutRemote() {
        val none = QuickControls.tileModel(app)
        assertEquals(Tile.STATE_UNAVAILABLE, none.state)
        assertEquals(app.getString(R.string.quick_no_remote), none.subtitle)
        store.add(remote("r1", 1, Keys.POWER to 1, name = "Zimmer 412"))
        val ready = QuickControls.tileModel(app)
        assertEquals(Tile.STATE_INACTIVE, ready.state)
        assertEquals("Zimmer 412", ready.subtitle)
    }

    @Test fun settingsPersist() {
        val settings = QuickControls.settings(app)
        assertNull(settings.inputKey)
        assertFalse(settings.lockScreen)
        settings.inputKey = Keys.HDMI3
        settings.lockScreen = true
        val again = QuickSettings(app.getSharedPreferences(QuickSettings.PREFS, Context.MODE_PRIVATE))
        assertEquals(Keys.HDMI3, again.inputKey)
        assertTrue(again.lockScreen)
    }

    @Test fun lockScreenNotificationOnOff() {
        store.add(remote("r1", 1, Keys.POWER to 1, name = "Zimmer 412"))
        QuickControls.refresh(app)
        assertNull(notifications.getNotification(QuickNotification.ID))

        QuickControls.settings(app).lockScreen = true
        QuickControls.refresh(app)
        val n = notifications.getNotification(QuickNotification.ID)
        assertNotNull(n)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility)
        assertEquals("Zimmer 412", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(n.channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)

        QuickControls.settings(app).lockScreen = false
        QuickControls.refresh(app)
        assertNull(notifications.getNotification(QuickNotification.ID))
    }

    @Test fun bootRestoresNotificationOnlyWhenEnabled() {
        store.add(remote("r1", 1, Keys.POWER to 1))
        SleepTimerBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(notifications.getNotification(QuickNotification.ID))
        QuickControls.settings(app).lockScreen = true
        SleepTimerBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNotNull(notifications.getNotification(QuickNotification.ID))
    }

    @Test fun widgetShowsNameOfLastUsedRemote() {
        val widgets = shadowOf(AppWidgetManager.getInstance(app))
        val id = widgets.createWidget(QuickWidgetProvider::class.java, R.layout.widget_quick)
        fun name() = widgets.getViewFor(id).findViewById<TextView>(R.id.quick_name).text.toString()
        assertEquals(app.getString(R.string.quick_no_remote), name())
        store.add(remote("r1", 1, Keys.POWER to 1, name = "Zimmer 412"))
        QuickControls.refresh(app)
        assertEquals("Zimmer 412", name())
    }
}
