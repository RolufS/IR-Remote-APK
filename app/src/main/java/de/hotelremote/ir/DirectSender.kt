package de.hotelremote.ir

/**
 * Sendet ein Signal sofort und synchron über den IR-Sender, ohne Warteschlange (für Receiver und Kacheln
 * außerhalb der App). Der Toggle-Zustand (RC5/RC6: frame/frameAlt) wechselt pro Signal wie im [SignalSender].
 * Fehler des Senders (z. B. kein IR-Sender) werden geworfen.
 */
class DirectSender(private val tx: IrTransmitter) {
    private val nextIsAlt = HashMap<List<Int>, Boolean>()

    @Synchronized
    fun send(signal: IrSignal) {
        val alt = signal.frameAlt
        val useAlt = alt != null && nextIsAlt[signal.frame] == true
        if (alt != null) nextIsAlt[signal.frame] = !useAlt
        tx.transmit(signal.freq, (if (useAlt) alt!! else signal.frame).toIntArray())
    }
}
