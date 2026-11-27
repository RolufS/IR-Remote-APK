package de.hotelremote.ui

import de.hotelremote.acauto.AcAutoRun
import de.hotelremote.acauto.AcAutoRunner
import de.hotelremote.acauto.AcAutoScheduler
import de.hotelremote.weather.OpenMeteo
import de.hotelremote.weather.OutdoorWeather
import de.hotelremote.weather.Place
import de.hotelremote.weather.WeatherResult
import de.hotelremote.weather.WeatherSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Vorübergehender Zustand von Abruf und Ortssuche (nicht gespeichert). */
data class WeatherUi(
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val searching: Boolean = false,
    /** null = noch nicht gesucht. */
    val results: List<Place>? = null,
    val searchFailed: Boolean = false,
)

/**
 * Außentemperatur und Klima-Automatik für die Oberfläche, ohne Android-Abhängigkeiten. Netz und Senden laufen auf
 * [io]; ohne eingeschaltete Außentemperatur gibt es keine Verbindung (siehe [OutdoorWeather]).
 */
class ClimateController(
    private val weatherSource: OutdoorWeather,
    private val scheduler: AcAutoScheduler,
    private val runner: AcAutoRunner,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    val weather: StateFlow<WeatherSettings> = weatherSource.state
    val run: StateFlow<AcAutoRun?> = scheduler.state

    private val _ui = MutableStateFlow(WeatherUi())
    val ui: StateFlow<WeatherUi> = _ui.asStateFlow()

    private var refreshJob: Job? = null
    private var searchJob: Job? = null

    /** Klima-Bildschirm gezeigt: neu abrufen, wenn der Wert älter als 15 min ist. */
    fun acScreenShown() {
        if (weatherSource.needsRefresh()) refresh()
    }

    /** Antippen der Zeile: jetzt abrufen (ein laufender Abruf genügt). */
    fun refresh() {
        if (!weather.value.active || refreshJob?.isActive == true) return
        _ui.update { it.copy(refreshing = true, refreshFailed = false) }
        refreshJob = scope.launch {
            val result = withContext(io) { weatherSource.refresh() }
            _ui.update { it.copy(refreshing = false, refreshFailed = result == WeatherResult.Failed) }
        }
    }

    /** Ausschalten stoppt eine laufende Automatik (sie braucht das Wetter); liefert, ob sie lief. */
    fun setWeatherEnabled(on: Boolean): Boolean {
        weatherSource.setEnabled(on)
        _ui.update { WeatherUi() }
        if (on) return false
        val wasRunning = run.value != null
        if (wasRunning) scheduler.stop()
        return wasRunning
    }

    /** [languageTag]: App-Sprache (BCP-47) für die Ortsnamen. */
    fun search(name: String, languageTag: String) {
        if (!weather.value.enabled || name.isBlank()) return
        searchJob?.cancel()
        _ui.update { it.copy(searching = true, searchFailed = false) }
        searchJob = scope.launch {
            val found = withContext(io) {
                try {
                    weatherSource.search(name, OpenMeteo.language(languageTag))
                } catch (e: IOException) {
                    null
                }
            }
            _ui.update { it.copy(searching = false, results = found ?: it.results, searchFailed = found == null) }
        }
    }

    /** Ort gewählt: merken und gleich abrufen. */
    fun choose(place: Place) {
        weatherSource.choose(place)
        _ui.update { WeatherUi() }
        refresh()
    }

    /** Startet die Automatik für [roomId] und sendet sofort (Wetter holen, Ziel berechnen, senden). */
    fun startAuto(roomId: String) {
        if (!weather.value.active) return
        scheduler.start(roomId)
        scope.launch { withContext(io) { runner.tick(sendAlways = true) } }
    }

    fun stopAuto() = scheduler.stop()

    /** Stoppt die Automatik, wenn sie für [roomId] läuft; liefert, ob sie lief. */
    fun stopAutoFor(roomId: String): Boolean = scheduler.stopRoom(roomId)

    /** Einstellungen des Raums geändert: läuft die Automatik dort, gleich neu rechnen (gesendet nur bei neuem Ziel). */
    fun settingsChanged(roomId: String) {
        if (run.value?.roomId != roomId) return
        scope.launch { withContext(io) { runner.tick() } }
    }

    /** Rückkehr in die App: Stand des Receivers lesen, Alarm neu setzen. */
    fun resumed() {
        weatherSource.reload()
        scheduler.rearm()
    }
}
