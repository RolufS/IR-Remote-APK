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

    override fun transmit(freq: Int, pattern: IntArray) {
        consumerIrManager?.transmit(freq, pattern)
    }
}
