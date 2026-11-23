package de.hotelremote.quick

import android.annotation.SuppressLint
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Kachel in den Schnelleinstellungen: sendet beim Antippen sofort, auch auf dem Sperrbildschirm (bewusst ohne
 * `unlockAndRun`). Aktionsknopf (STATE_INACTIVE), ohne Fernbedienung nicht verfügbar; Untertitel = Fernbedienung.
 */
@RequiresApi(Build.VERSION_CODES.N)
abstract class QuickTileService(private val action: QuickAction) : TileService() {
    override fun onStartListening() = updateTile()

    override fun onClick() {
        reportQuickResult(this, QuickControls.send(this, action))
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val model = QuickControls.tileModel(this)
        tile.state = model.state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = model.subtitle
        tile.updateTile()
    }
}

@RequiresApi(Build.VERSION_CODES.N)
class PowerTileService : QuickTileService(QuickAction.POWER)

@RequiresApi(Build.VERSION_CODES.N)
class VolumeDownTileService : QuickTileService(QuickAction.VOL_DOWN)

@RequiresApi(Build.VERSION_CODES.N)
class VolumeUpTileService : QuickTileService(QuickAction.VOL_UP)

@RequiresApi(Build.VERSION_CODES.N)
class InputTileService : QuickTileService(QuickAction.INPUT)

/**
 * Fünfte Kachel „TV Fernbedienung“: öffnet die ganze Fernbedienung über dem Sperrbildschirm
 * ([LockScreenRemoteActivity]). Nur verfügbar, wenn das in „Schnellsteuerung“ erlaubt ist.
 *
 * Ab Android 14 hängt die Kachel den PendingIntent per `Tile.setActivityLaunchForClick` an: dann ruft SystemUI
 * [onClick] nicht auf, sondern startet die Activity selbst und zeigt sie über dem Sperrbildschirm, ohne Entsperren.
 * `startActivityAndCollapse` aus [onClick] fragt dagegen immer nach dem Entsperren; das bleibt der Weg vor Android 14
 * (und die Reserve, falls ein System den angehängten PendingIntent ignoriert).
 */
@RequiresApi(Build.VERSION_CODES.N)
class RemoteTileService : TileService() {
    override fun onStartListening() = updateTile()

    override fun onClick() {
        if (QuickControls.remoteTileModel(this).state == Tile.STATE_UNAVAILABLE) {
            updateTile()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(LockScreenRemoteActivity.pendingIntent(this))
        } else {
            startLegacy()
        }
    }

    /** Vor Android 14 gibt es nur die Intent-Variante (ab 14 bei targetSdk 34+ verboten). */
    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startLegacy() = startActivityAndCollapse(LockScreenRemoteActivity.intent(this))

    private fun updateTile() {
        val tile = qsTile ?: return
        val model = QuickControls.remoteTileModel(this)
        tile.state = model.state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = model.subtitle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setLaunch(tile, model.state != Tile.STATE_UNAVAILABLE)
        tile.updateTile()
    }

    /** Nur verfügbar mit PendingIntent; sonst entfernen, damit ein Tippen nichts startet. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun setLaunch(tile: Tile, available: Boolean) {
        tile.activityLaunchForClick = if (available) LockScreenRemoteActivity.pendingIntent(this) else null
    }
}
