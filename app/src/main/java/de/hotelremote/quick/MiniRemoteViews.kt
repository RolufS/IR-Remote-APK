package de.hotelremote.quick

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.SavedRemote

/**
 * Höchste Höhe der eigenen aufgezogenen Ansicht (Inhaltsbereich von DecoratedCustomViewStyle) in dp: die Plattform
 * garantiert für erweiterte eigene Ansichten nur „as little as 252 dp“ (developer.android.com, Custom notification
 * layouts; früher 256 dp). Kopfzeile (notification_content_margin_top 50 dp) und unterer Rand
 * (notification_content_margin 20 dp) legt das System außen herum. notification_quick_big.xml bleibt mit Reserve darunter:
 * 2 × 3 dp Innenrand + 4 Zeilen × (52 dp Taste + 2 × 2 dp Abstand) = 230 dp.
 */
internal const val MINI_REMOTE_MAX_HEIGHT_DP = 252

/** Mindesthöhe der Tasten der Mini-Fernbedienung in dp (Steuerkreuz [MINI_DPAD_MIN_DP]). */
internal const val MINI_KEY_MIN_DP = 48
internal const val MINI_DPAD_MIN_DP = 44

/** Tasten der Mini-Fernbedienung (notification_quick_big.xml): Ansicht und gesendeter Knopf. */
internal val MINI_REMOTE: List<Pair<Int, QuickButton>> = listOf(
    R.id.mini_power to QuickButton.Key(Keys.POWER),
    R.id.mini_mute to QuickButton.Key(Keys.MUTE),
    R.id.mini_input to QuickButton.Input,
    R.id.mini_vol_down to QuickButton.Key(Keys.VOL_DOWN),
    R.id.mini_vol_up to QuickButton.Key(Keys.VOL_UP),
    R.id.mini_ch_up to QuickButton.Key(Keys.CH_UP),
    R.id.mini_ch_down to QuickButton.Key(Keys.CH_DOWN),
    R.id.mini_up to QuickButton.Key(Keys.UP),
    R.id.mini_left to QuickButton.Key(Keys.LEFT),
    R.id.mini_ok to QuickButton.Key(Keys.OK),
    R.id.mini_right to QuickButton.Key(Keys.RIGHT),
    R.id.mini_down to QuickButton.Key(Keys.DOWN),
    R.id.mini_back to QuickButton.Key(Keys.BACK),
    R.id.mini_home to QuickButton.Key(Keys.HOME),
)

/** Eigene Daten-URI je Taste, damit kein PendingIntent einen anderen (auch der Knopfreihe) ersetzt. */
internal fun miniRemoteSlot(button: QuickButton): String = when (button) {
    is QuickButton.Key -> "notification/mini/${button.key}"
    QuickButton.Input -> "notification/mini/input"
    is QuickButton.Run -> "notification/mini/run/${button.id}"
}

/**
 * Belegt die Mini-Fernbedienung: Tasten, die [remote] fehlt, sind unsichtbar (Platz bleibt, das Steuerkreuz behält
 * seine Form); ohne Fernbedienung bleiben alle sichtbar und melden beim Tippen „Keine Fernbedienung“.
 * Alle Tasten senden per Broadcast (ohne Entsperren); die ganze Fernbedienung öffnet nur der contentIntent.
 */
internal fun RemoteViews.bindMiniRemote(context: Context, remote: SavedRemote?, inputKey: String?) {
    val keys = remote?.keys?.keys
    for ((id, button) in MINI_REMOTE) {
        val present = keys == null || buttonKey(button, keys, inputKey) != null
        setViewVisibility(id, if (present) View.VISIBLE else View.INVISIBLE)
        if (!present) continue
        setContentDescription(id, quickButtonName(context.resources, button))
        setOnClickPendingIntent(id, QuickControlReceiver.pendingIntent(context, button, miniRemoteSlot(button)))
    }
}
