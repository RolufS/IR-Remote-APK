package de.hotelremote.data

import kotlinx.serialization.Serializable

/** Gespeicherte Tastenfolge einer Fernbedienung. */
@Serializable
data class Automation(val id: String, val name: String, val steps: List<AutomationStep>)

/** Ein Schritt: Taste (kanonischer Name oder `X:<Label>`) und Wartezeit danach. */
@Serializable
data class AutomationStep(val key: String, val delayAfterMs: Long = DEFAULT_DELAY_MS) {
    companion object {
        const val DEFAULT_DELAY_MS = 1500L
        const val MIN_DELAY_MS = 500L
        const val MAX_DELAY_MS = 10_000L
        const val DELAY_STEP_MS = 500L
    }
}
