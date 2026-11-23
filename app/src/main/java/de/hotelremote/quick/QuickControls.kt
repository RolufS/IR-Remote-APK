package de.hotelremote.quick

import android.content.Context
import android.service.quicksettings.Tile
import androidx.annotation.VisibleForTesting
import de.hotelremote.R
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.AndroidIrTransmitter
import de.hotelremote.ir.DirectSender
import de.hotelremote.ir.IrTransmitter
import de.hotelremote.sleeptimer.remotesFile
import java.io.IOException

/** Anzeige einer Kachel: Zustand (Tile.STATE_*) und Untertitel (Name der Fernbedienung). */
data class QuickTileModel(val state: Int, val subtitle: String)

/**
 * Schnellsteuerung außerhalb der App: Kacheln, Widget und Sperrbildschirm-Benachrichtigung senden über diese
 * Stelle an die zuletzt benutzte Fernbedienung. Synchron (Datei lesen und ein Rahmen senden dauern Millisekunden).
 */
object QuickControls {
    /** Für Tests austauschbar. */
    @VisibleForTesting
    @Volatile
    var transmitter: (Context) -> IrTransmitter = ::AndroidIrTransmitter

    /** Ein Sender pro Prozess; der Toggle-Zustand (RC5/RC6) liegt in den Einstellungen und überlebt das Prozessende. */
    @Volatile private var sender: DirectSender? = null

    fun settings(context: Context) =
        QuickSettings(context.applicationContext.getSharedPreferences(QuickSettings.PREFS, Context.MODE_PRIVATE))

    /** Zuletzt benutzte Fernbedienung; null ohne gespeicherte oder wenn die Datei nicht lesbar ist. */
    fun lastRemote(context: Context): SavedRemote? =
        try {
            RemoteStore(remotesFile(context)).lastUsed()
        } catch (e: IOException) {
            null
        }

    fun send(context: Context, action: QuickAction): QuickResult {
        val remote = lastRemote(context) ?: return QuickResult.NO_REMOTE
        val key = quickKey(action, remote.keys.keys, settings(context).inputKey) ?: return QuickResult.KEY_MISSING
        return try {
            sender(context).send(remote.keys.getValue(key))
            QuickResult.SENT
        } catch (e: Exception) {
            QuickResult.SEND_FAILED
        }
    }

    /** Ohne Fernbedienung ist die Kachel nicht verfügbar; sonst ein Aktionsknopf (kein Schalter). */
    fun tileModel(context: Context): QuickTileModel {
        val remote = lastRemote(context)
            ?: return QuickTileModel(Tile.STATE_UNAVAILABLE, context.getString(R.string.quick_no_remote))
        return QuickTileModel(Tile.STATE_INACTIVE, remote.title)
    }

    /** Fernbedienung oder Einstellungen geändert (oder Neustart): Widget und Benachrichtigung neu aufbauen. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val remote = lastRemote(app)
        QuickWidgetProvider.updateAll(app, remote)
        QuickNotification(app).update(remote, settings(app).lockScreen)
    }

    private fun sender(context: Context): DirectSender = sender ?: synchronized(this) {
        sender ?: DirectSender(transmitter(context.applicationContext), settings(context).toggles).also { sender = it }
    }

    @VisibleForTesting
    fun reset() {
        transmitter = ::AndroidIrTransmitter
        sender = null
    }
}
