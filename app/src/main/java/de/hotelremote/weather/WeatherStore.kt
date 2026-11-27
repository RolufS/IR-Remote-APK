package de.hotelremote.weather

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Gemessene Außentemperatur in °C und wann sie abgerufen wurde. */
@Serializable
data class Reading(val celsius: Double, val at: Long)

/**
 * Einstellung „Außentemperatur anzeigen“ (Standard aus), gewählter Ort und letzter Wert. Nur lokal gespeichert.
 */
@Serializable
data class WeatherSettings(
    val enabled: Boolean = false,
    val place: Place? = null,
    val last: Reading? = null,
) {
    /** Abrufbereit: eingeschaltet und mit Ort. Nur dann darf die App eine Verbindung aufbauen. */
    val active get() = enabled && place != null
}

interface WeatherStore {
    fun load(): WeatherSettings
    fun save(settings: WeatherSettings)
}

/** SharedPreferences als JSON; `commit()`, weil der Prozess direkt nach dem Alarm-Receiver enden kann. */
class PrefsWeatherStore(private val prefs: SharedPreferences) : WeatherStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): WeatherSettings {
        val text = prefs.getString(KEY, null) ?: return WeatherSettings()
        return try {
            json.decodeFromString(WeatherSettings.serializer(), text)
        } catch (e: IllegalArgumentException) {
            WeatherSettings()
        }
    }

    override fun save(settings: WeatherSettings) {
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(WeatherSettings.serializer(), settings)) }
    }

    companion object {
        const val PREFS = "weather"
        private const val KEY = "settings"
    }
}
