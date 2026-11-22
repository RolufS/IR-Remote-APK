package de.hotelremote.ir

import android.content.Context
import android.hardware.ConsumerIrManager

interface IrTransmitter {
    val available: Boolean
    fun transmit(freq: Int, pattern: IntArray)
}

class AndroidIrTransmitter(context: Context) : IrTransmitter {
    private val consumerIrManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    override val available: Boolean
        get() = consumerIrManager?.hasIrEmitter() == true

    /** Ohne IR-Sender wirft es, damit der Aufrufer "Senden fehlgeschlagen" melden kann. */
    override fun transmit(freq: Int, pattern: IntArray) {
        val manager = consumerIrManager?.takeIf { it.hasIrEmitter() }
            ?: throw UnsupportedOperationException("Kein Infrarot-Sender")
        manager.transmit(freq, pattern)
    }
}
