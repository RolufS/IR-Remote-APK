package de.hotelremote.quick

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.service.quicksettings.Tile
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.sleeptimer.FakeTransmitter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID = "http://schemas.android.com/apk/res/android"

/** Sperrbildschirm: Einstellungen, aufziehbare Mini-Fernbedienung, Knopf „Ganze Fernbedienung“, Kachel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LockScreenTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tx = FakeTransmitter()
    private val store = RemoteStore(File(app.filesDir, "remotes.json"))
    private val settings get() = QuickControls.settings(app)

    private fun sig(n: Int) = IrSignal(freq = 38000, frame = listOf(n, 100, 200), periodMs = 100)

    private fun addRemote(vararg keys: String) = store.add(
        SavedRemote(id = "r1", name = "Zimmer 412", brand = "LG", codeLabel = "Code 1", keys = keys.withIndex().associate { (i, k) -> k to sig(i + 1) }, lastUsed = 1),
    )

    private fun notification(): Notification? =
        shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(QuickNotification.ID)

    private fun show(): Notification {
        settings.lockScreen = true
        QuickControls.refresh(app)
        return notification() ?: error("keine Benachrichtigung")
    }

    private fun Notification.big(): View = (bigContentView ?: error("keine erweiterte Ansicht")).apply(app, FrameLayout(app))

    private fun Notification.small(): View = (contentView ?: error("keine Ansicht")).apply(app, FrameLayout(app))

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        QuickControls.reset()
        QuickControls.transmitter = { tx }
    }

    @After fun tearDown() = QuickControls.reset()

    @Test fun lockSettingsDefaultsAndPersist() {
        assertEquals(LockStyle.EXPANDED, settings.lockStyle)
        assertFalse(settings.fullRemote)
        settings.lockStyle = LockStyle.SMALL
        settings.fullRemote = true
        val again = QuickSettings(app.getSharedPreferences(QuickSettings.PREFS, Context.MODE_PRIVATE))
        assertEquals(LockStyle.SMALL, again.lockStyle)
        assertTrue(again.fullRemote)
        assertEquals(LockStyle.SMALL, again.load().lockStyle)
        assertTrue(again.load().fullRemote)
        settings.lockStyle = LockStyle.EXPANDED
        assertEquals(LockStyle.EXPANDED, settings.load().lockStyle)
    }

    @Test fun bigViewOnlyWhenExpanded() {
        addRemote(Keys.POWER)
        assertNotNull(show().bigContentView)
        settings.lockStyle = LockStyle.SMALL
        assertNull(show().bigContentView)
        // Eingeklappt bleibt die frei gewählte Knopfreihe.
        assertNotNull(show().contentView)
    }

    @Test fun miniRemoteHidesMissingKeys() {
        addRemote(Keys.POWER, Keys.VOL_UP, Keys.VOL_DOWN, Keys.OK, Keys.UP, Keys.HDMI1)
        val view = show().big()
        fun visible(id: Int) = view.findViewById<View>(id).visibility == View.VISIBLE
        assertTrue(visible(R.id.mini_power))
        assertTrue(visible(R.id.mini_vol_up))
        assertTrue(visible(R.id.mini_vol_down))
        assertTrue(visible(R.id.mini_ok))
        assertTrue(visible(R.id.mini_up))
        assertTrue(visible(R.id.mini_input))
        assertFalse(visible(R.id.mini_mute))
        assertFalse(visible(R.id.mini_ch_up))
        assertFalse(visible(R.id.mini_down))
        assertFalse(visible(R.id.mini_back))
        assertFalse(visible(R.id.mini_home))
        assertEquals(app.getString(R.string.key_vol_up), view.findViewById<View>(R.id.mini_vol_up).contentDescription)
    }

    @Test fun miniRemotePendingIntentsUniquePerKey() {
        val keys = MINI_REMOTE.map { it.second }
        assertEquals(keys.size, keys.toSet().size)
        val intents = keys.map { QuickControlReceiver.intent(app, it, miniRemoteSlot(it)) }
        for (i in intents.indices) for (j in i + 1 until intents.size) {
            assertFalse("${keys[i]} und ${keys[j]} teilen einen PendingIntent", intents[i].filterEquals(intents[j]))
        }
        // Und nicht gleich den Knöpfen der eingeklappten Reihe.
        val row = QuickControlReceiver.intent(app, QuickButton.Key(Keys.POWER), "notification/0")
        assertTrue(intents.none { it.filterEquals(row) })
    }

    @Test fun miniRemoteKeySends() {
        addRemote(Keys.MUTE, Keys.CH_UP)
        val view = show().big()
        assertTrue(view.findViewById<View>(R.id.mini_ch_up).hasOnClickListeners())
        val button = MINI_REMOTE.first { it.first == R.id.mini_ch_up }.second
        QuickControlReceiver().onReceive(app, QuickControlReceiver.intent(app, button, miniRemoteSlot(button)))
        assertEquals(listOf(2), tx.sent)
    }

    @Test fun fullRemoteButtonOnlyWhenAllowed() {
        addRemote(Keys.POWER)
        var n = show()
        assertEquals(View.GONE, n.small().findViewById<View>(R.id.quick_full_remote).visibility)
        assertEquals(View.GONE, n.big().findViewById<View>(R.id.mini_full_remote).visibility)
        settings.fullRemote = true
        n = show()
        assertEquals(View.VISIBLE, n.small().findViewById<View>(R.id.quick_full_remote).visibility)
        assertEquals(View.VISIBLE, n.big().findViewById<View>(R.id.mini_full_remote).visibility)
        // Auch bei „Klein“ als letzter Knopf der Reihe.
        settings.lockStyle = LockStyle.SMALL
        assertEquals(View.VISIBLE, show().small().findViewById<View>(R.id.quick_full_remote).visibility)
    }

    @Test fun fullRemoteIntentOpensLockActivity() {
        val intent = LockScreenRemoteActivity.intent(app)
        assertEquals(ComponentName(app, LockScreenRemoteActivity::class.java), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val pending = shadowOf(LockScreenRemoteActivity.pendingIntent(app))
        assertTrue(pending.isActivity)
        assertTrue(pending.flags and android.app.PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test fun lockActivityManifest() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, LockScreenRemoteActivity::class.java), 0)
        assertFalse(info.exported)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
        assertEquals("", info.taskAffinity ?: "")
        assertTrue(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        // FLAG_SHOW_WHEN_LOCKED ist versteckt: das Attribut direkt im Manifest prüfen.
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val activities = doc.getElementsByTagName("activity")
        val lock = (0 until activities.length).map { activities.item(it) as Element }
            .single { it.getAttributeNS(ANDROID, "name") == ".quick.LockScreenRemoteActivity" }
        assertEquals("true", lock.getAttributeNS(ANDROID, "showWhenLocked"))
        assertEquals("", lock.getAttributeNS(ANDROID, "turnScreenOn"))
    }

    @Test fun remoteTileStatePerOption() {
        val off = QuickControls.remoteTileModel(app)
        assertEquals(Tile.STATE_UNAVAILABLE, off.state)
        assertEquals(app.getString(R.string.tile_enable_in_app), off.subtitle)
        settings.fullRemote = true
        val none = QuickControls.remoteTileModel(app)
        assertEquals(Tile.STATE_UNAVAILABLE, none.state)
        assertEquals(app.getString(R.string.quick_no_remote), none.subtitle)
        addRemote(Keys.POWER)
        val ready = QuickControls.remoteTileModel(app)
        assertEquals(Tile.STATE_INACTIVE, ready.state)
        assertEquals("Zimmer 412", ready.subtitle)
    }

    @Config(sdk = [33])
    @Test fun remoteTileStartsLockActivity() {
        addRemote(Keys.POWER)
        settings.fullRemote = true
        val service = Robolectric.setupService(RemoteTileService::class.java)
        service.onClick()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(ComponentName(app, LockScreenRemoteActivity::class.java), started?.component)
    }

    @Config(sdk = [33])
    @Test fun remoteTileDoesNothingWhenNotAllowed() {
        addRemote(Keys.POWER)
        val service = Robolectric.setupService(RemoteTileService::class.java)
        service.onClick()
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
