package de.hotelremote.weather

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/** Ergebnis eines Abrufs. */
sealed interface WeatherResult {
    data class Ok(val reading: Reading) : WeatherResult

    /** Nicht eingeschaltet oder kein Ort: es wurde keine Verbindung aufgebaut. */
    data object Inactive : WeatherResult

    data object Failed : WeatherResult
}

/**
 * Außentemperatur für Klima-Bildschirm und Klima-Automatik. Ohne [WeatherSettings.active] baut sie keine
 * Verbindung auf (auch die Ortssuche erst nach dem Einschalten). Alle Netz-Aufrufe blockieren: nur aus IO aufrufen.
 * App und Alarm-Receiver teilen sich eine Instanz pro Prozess.
 */
class OutdoorWeather(
    private val store: WeatherStore,
    private val api: WeatherApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<WeatherSettings> = _state.asStateFlow()

    /** Ein: Ort danach wählen. Aus: Ort und letzter Wert bleiben gemerkt, werden aber nicht mehr abgerufen. */
    @Synchronized
    fun setEnabled(on: Boolean) = change { it.copy(enabled = on) }

    /** Neuer Ort; ein Wert für den alten Ort gilt nicht mehr. */
    @Synchronized
    fun choose(place: Place) = change { it.copy(enabled = true, place = place, last = if (it.place == place) it.last else null) }

    /** Ortssuche; nur eingeschaltet (sonst leer, ohne Verbindung). @throws IOException */
    fun search(name: String, language: String): List<Place> {
        if (!_state.value.enabled || name.isBlank()) return emptyList()
        return api.searchPlaces(name, language)
    }

    /** Ruft die Temperatur des gewählten Orts ab und merkt sie mit Zeitstempel. */
    fun refresh(): WeatherResult {
        val place = _state.value.takeIf { it.active }?.place ?: return WeatherResult.Inactive
        val celsius = try {
            api.currentTemperature(place.latitude, place.longitude)
        } catch (e: IOException) {
            return WeatherResult.Failed
        }
        val reading = Reading(celsius, clock())
        synchronized(this) {
            // Inzwischen ausgeschaltet oder anderer Ort: Wert verwerfen.
            if (_state.value.place != place || !_state.value.enabled) return WeatherResult.Inactive
            change { it.copy(last = reading) }
        }
        return WeatherResult.Ok(reading)
    }

    /** Älter als [maxAgeMs] (oder noch kein Wert) und abrufbereit. */
    fun needsRefresh(maxAgeMs: Long = REFRESH_AFTER_MS): Boolean {
        val s = _state.value
        if (!s.active) return false
        val last = s.last ?: return true
        return clock() - last.at >= maxAgeMs
    }

    /** Neu lesen (der Receiver kann in einem anderen Prozessstart geschrieben haben). */
    @Synchronized
    fun reload() {
        _state.value = store.load()
    }

    private fun change(f: (WeatherSettings) -> WeatherSettings) {
        val new = f(_state.value)
        if (new == _state.value) return
        store.save(new)
        _state.value = new
    }

    companion object {
        /** Beim Öffnen des Klima-Bildschirms neu abrufen, wenn der Wert älter ist. */
        const val REFRESH_AFTER_MS = 15 * 60_000L

        /** Ab diesem Alter gilt der Wert als veraltet. */
        const val STALE_AFTER_MS = 3 * 60 * 60_000L

        fun isStale(reading: Reading, now: Long) = now - reading.at >= STALE_AFTER_MS
    }
}

/** °C in °F. */
fun celsiusToFahrenheit(c: Double) = c * 9.0 / 5.0 + 32.0
