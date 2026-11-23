package de.hotelremote.quick

import android.os.Build
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
