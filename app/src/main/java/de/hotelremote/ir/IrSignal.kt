package de.hotelremote.ir

import kotlinx.serialization.Serializable

/** Pulsfolgen in Mikrosekunden, abwechselnd an/aus, beginnend und endend mit "an". */
@Serializable
data class IrSignal(
    val freq: Int,
    val frame: List<Int>,
    val repeat: List<Int>? = null,
    val frameAlt: List<Int>? = null,
    val periodMs: Int,
)
