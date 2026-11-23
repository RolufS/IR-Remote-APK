package de.hotelremote.quick

import android.content.Context
import android.service.quicksettings.Tile
import androidx.annotation.VisibleForTesting
import de.hotelremote.R
import de.hotelremote.automation.runSteps
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.AndroidIrTransmitter
import de.hotelremote.ir.DirectSender
import de.hotelremote.ir.IrSignal
import de.hotelremote.ir.IrTransmitter
import de.hotelremote.sleeptimer.remotesFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Ein Widget auf dem Startbildschirm und die Zahl seiner Knöpfe (Liste in „Schnellsteuerung“). */
data class QuickWidgetEntry(val id: Int, val buttonCount: Int)

/** Anzeige einer Kachel: Zustand (Tile.STATE_*) und Untertitel (Name der Fernbedienung). */
data class QuickTileModel(val state: Int, val subtitle: String)

/**
 * Schnellsteuerung außerhalb der App: Kacheln, Widget und Sperrbildschirm-Benachrichtigung senden über diese
 * Stelle an die zuletzt benutzte Fernbedienung. Tasten synchron (Datei lesen und ein Rahmen senden dauern
 * Millisekunden), Automationen als Coroutine im Receiver (höchstens 8 s Wartezeit).
 */
object QuickControls {
    /** Für Tests austauschbar. */
    @VisibleForTesting
    @Volatile
    var transmitter: (Context) -> IrTransmitter = ::AndroidIrTransmitter

    /** Ein Sender pro Prozess; der Toggle-Zustand (RC5/RC6) liegt in den Einstellungen und überlebt das Prozessende. */
    @Volatile private var sender: DirectSender? = null

    private val running = AtomicBoolean(false)

    /** Thread für Automationen aus dem Receiver; für Tests austauschbar. */
    @VisibleForTesting
    @Volatile
    var automationDispatcher: CoroutineDispatcher = Dispatchers.IO

    fun settings(context: Context) =
        QuickSettings(context.applicationContext.getSharedPreferences(QuickSettings.PREFS, Context.MODE_PRIVATE))

    /** Zuletzt benutzte Fernbedienung; null ohne gespeicherte oder wenn die Datei nicht lesbar ist. */
    fun lastRemote(context: Context): SavedRemote? =
        try {
            RemoteStore(remotesFile(context)).lastUsed()
        } catch (e: IOException) {
            null
        }

    /** Kachel: sendet sofort (Kacheln sind nie mit Automationen belegt). */
    fun send(context: Context, action: QuickAction): QuickResult = when (val plan = plan(context, action.button)) {
        is QuickPlan.Send -> transmit(context, plan.signal)
        is QuickPlan.Fail -> plan.result
        is QuickPlan.Run -> QuickResult.SEND_FAILED
    }

    /** Was [button] bei der zuletzt benutzten Fernbedienung tut. */
    fun plan(context: Context, button: QuickButton): QuickPlan {
        val remote = lastRemote(context) ?: return QuickPlan.Fail(QuickResult.NO_REMOTE)
        return resolveQuickButton(button, remote, settings(context).inputKey)
    }

    /** Ein Signal synchron senden. */
    fun transmit(context: Context, signal: IrSignal): QuickResult = try {
        sender(context).send(signal)
        QuickResult.SENT
    } catch (e: Exception) {
        QuickResult.SEND_FAILED
    }

    /**
     * Automation wie beim Sleep-Timer: Schritte mit Wartezeiten über denselben Sender (gemerkter Toggle-Zustand).
     * Höchstens eine gleichzeitig; ein weiterer Druck währenddessen liefert [QuickResult.BUSY].
     */
    suspend fun run(context: Context, plan: QuickPlan.Run): QuickResult {
        if (!running.compareAndSet(false, true)) return QuickResult.BUSY
        return try {
            val sender = sender(context)
            runSteps(plan.keys, plan.automation.steps, sender::send)
            QuickResult.SENT
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            QuickResult.SEND_FAILED
        } finally {
            running.set(false)
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
        val settings = settings(app)
        QuickNotification(app).update(remote, settings.lockScreen, settings.notificationButtons)
    }

    /** Widgets dieser App in der Reihenfolge ihrer IDs (= Reihenfolge des Hinzufügens). */
    fun widgetEntries(context: Context): List<QuickWidgetEntry> {
        val settings = settings(context)
        return QuickWidgetProvider.widgetIds(context.applicationContext).sorted().map { QuickWidgetEntry(it, settings.widgetButtons(it).size) }
    }

    private fun sender(context: Context): DirectSender = sender ?: synchronized(this) {
        sender ?: DirectSender(transmitter(context.applicationContext), settings(context).toggles).also { sender = it }
    }

    @VisibleForTesting
    fun reset() {
        transmitter = ::AndroidIrTransmitter
        automationDispatcher = Dispatchers.IO
        sender = null
        running.set(false)
    }
}
