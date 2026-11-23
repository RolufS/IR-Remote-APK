package de.hotelremote

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import de.hotelremote.quick.InputTileService
import de.hotelremote.quick.PowerTileService
import de.hotelremote.quick.QuickControls
import de.hotelremote.quick.QuickNotification
import de.hotelremote.quick.RemoteTileService
import de.hotelremote.quick.VolumeDownTileService
import de.hotelremote.quick.VolumeUpTileService
import de.hotelremote.sleeptimer.AndroidSleepTimerNotifier
import de.hotelremote.sleeptimer.FakeTransmitter
import de.hotelremote.sleeptimer.SleepAction
import de.hotelremote.sleeptimer.SleepTimer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Android 6.0–7.1 (API 23–25, minSdk 23): die Stellen, die Lint als NewApi meldete, laufen dort ohne Absturz und
 * mit Ersatzverhalten (Kacheln aus, Sleep-Timer ohne Countdown-Chronometer).
 */
@RunWith(RobolectricTestRunner::class)
class LegacyAndroidTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private val tiles = listOf(
        PowerTileService::class.java.name,
        VolumeDownTileService::class.java.name,
        VolumeUpTileService::class.java.name,
        InputTileService::class.java.name,
        RemoteTileService::class.java.name,
    )

    /** Nur über den Namen (kein Klassenliteral): unter API 23 darf keine TileService-Klasse geladen werden. */
    private fun tileEnabled(name: String): Boolean =
        app.packageManager.getServiceInfo(ComponentName(app.packageName, name), PackageManager.MATCH_DISABLED_COMPONENTS).enabled

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        QuickControls.reset()
    }

    @After fun tearDown() = QuickControls.reset()

    @Config(sdk = [23])
    @Test fun tilesDisabledBeforeNougat() {
        for (name in tiles) assertFalse("$name auf API 23 aktiv", tileEnabled(name))
    }

    @Config(sdk = [24, 35])
    @Test fun tilesEnabledFromNougat() {
        for (name in tiles) assertTrue("$name ab API 24 nicht aktiv", tileEnabled(name))
    }

    private fun runningNotification(): Notification {
        AndroidSleepTimerNotifier(app).showRunning(SleepTimer(System.currentTimeMillis() + 600_000L, "r1", SleepAction.PowerOff, exact = true))
        return shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(AndroidSleepTimerNotifier.ID_RUNNING)
            ?: error("keine Benachrichtigung")
    }

    /** Ohne Countdown-Chronometer (erst ab API 24) zeigt die Benachrichtigung die Endzeit statt einer aufwärts zählenden Uhr. */
    @Config(sdk = [23])
    @Test fun sleepTimerNotificationWithoutCountdownBeforeNougat() {
        val n = runningNotification()
        assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
    }

    @Config(sdk = [24, 35])
    @Test fun sleepTimerNotificationCountsDownFromNougat() {
        val n = runningNotification()
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    }

    /** Sperrbildschirm-Benachrichtigung mit Hinweis-Symbol auch unter Android 6 (DecoratedCustomViewStyle über AndroidX). */
    @Config(sdk = [23])
    @Test fun lockNotificationOnMarshmallow() {
        QuickControls.transmitter = { FakeTransmitter() }
        val settings = QuickControls.settings(app)
        settings.fullRemote = true
        settings.lockScreen = true
        QuickControls.refresh(app)
        val n = shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(QuickNotification.ID)
        assertNotNull(n)
        val big = (n.bigContentView ?: error("keine erweiterte Ansicht")).apply(app, FrameLayout(app))
        assertEquals(View.VISIBLE, big.findViewById<View>(R.id.mini_full_hint).visibility)
        assertEquals(View.VISIBLE, n.contentView.apply(app, FrameLayout(app)).findViewById<View>(R.id.quick_full_hint).visibility)
    }
}
