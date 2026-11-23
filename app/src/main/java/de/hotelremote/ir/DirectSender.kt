package de.hotelremote.ir

/** Merkt pro Signal (Schlüssel: frame), ob als Nächstes frameAlt gesendet wird. */
interface ToggleStore {
    operator fun get(frame: List<Int>): Boolean

    operator fun set(frame: List<Int>, nextIsAlt: Boolean)
}

/** Toggle-Zustand nur im Speicher (für einen Sleep-Timer-Lauf). */
class MemoryToggleStore : ToggleStore {
    private val map = HashMap<List<Int>, Boolean>()

    override fun get(frame: List<Int>) = map[frame] == true

    override fun set(frame: List<Int>, nextIsAlt: Boolean) {
        map[frame] = nextIsAlt
    }
}

/**
 * Sendet ein Signal sofort und synchron über den IR-Sender, ohne Warteschlange (für Receiver und Kacheln
 * außerhalb der App). Der Toggle-Zustand (RC5/RC6: frame/frameAlt) wechselt pro Signal wie im [SignalSender];
 * [toggles] bestimmt, wie lange er gemerkt wird. Fehler des Senders (z. B. kein IR-Sender) werden geworfen.
 */
class DirectSender(private val tx: IrTransmitter, private val toggles: ToggleStore = MemoryToggleStore()) {
    @Synchronized
    fun send(signal: IrSignal) {
        val alt = signal.frameAlt
        val useAlt = alt != null && toggles[signal.frame]
        if (alt != null) toggles[signal.frame] = !useAlt
        tx.transmit(signal.freq, (if (useAlt) alt!! else signal.frame).toIntArray())
    }
}
