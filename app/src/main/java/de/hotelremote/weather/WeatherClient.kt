package de.hotelremote.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Gewählter Ort für die Außentemperatur; [region] und [country] wie von der Geokodierung geliefert. */
@Serializable
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val region: String = "",
) {
    /** „Chon Buri, Thailand“: Region und Land, ohne leere oder doppelte Teile. */
    val detail: String get() = listOf(region, country).filter { it.isNotBlank() && it != name }.distinct().joinToString(", ")
}

/** Wetterdienst; alle Aufrufe blockieren und werfen bei Netz- oder Formatfehlern [IOException]. */
interface WeatherApi {
    /** Bis zu fünf Orte zum Namen, Namen in [language] (ISO-639-1), wenn vorhanden. */
    fun searchPlaces(name: String, language: String): List<Place>

    /** Aktuelle Temperatur in 2 m Höhe in °C. */
    fun currentTemperature(latitude: Double, longitude: Double): Double
}

/**
 * Open-Meteo (kein Schlüssel, Daten CC BY 4.0). Gesendet werden nur der eingegebene Ortsname bzw. die Koordinaten
 * des gewählten Orts. [get] lädt eine URL als Text (in Tests ersetzt, ohne echtes Netz).
 */
class OpenMeteoClient(private val get: (String) -> String = ::httpGet) : WeatherApi {
    override fun searchPlaces(name: String, language: String): List<Place> =
        OpenMeteo.parsePlaces(get(OpenMeteo.searchUrl(name, language)))

    override fun currentTemperature(latitude: Double, longitude: Double): Double =
        OpenMeteo.parseTemperature(get(OpenMeteo.forecastUrl(latitude, longitude)))
}

/** URLs und Antworten der Open-Meteo-APIs (Geokodierung und Vorhersage). */
object OpenMeteo {
    const val TIMEOUT_MS = 5_000

    private val json = Json { ignoreUnknownKeys = true }

    fun searchUrl(name: String, language: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(name.trim(), "UTF-8")}" +
            "&count=5&language=${URLEncoder.encode(language, "UTF-8")}"

    fun forecastUrl(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${coordinate(latitude)}&longitude=${coordinate(longitude)}" +
            "&current=temperature_2m"

    /** Vier Nachkommastellen (etwa 10 m) mit Punkt, unabhängig von der Sprache. */
    private fun coordinate(v: Double) = String.format(Locale.ROOT, "%.4f", v)

    /**
     * Sprache für die Ortsnamen aus der App-Sprache (BCP-47): „zh-CN“ → „zh“; Indonesisch heißt in alten
     * Java-Versionen „in“, bei Open-Meteo „id“.
     */
    fun language(tag: String): String {
        val lang = tag.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        return when (lang) {
            "in" -> "id"
            "" -> "en"
            else -> lang
        }
    }

    /** Treffer der Geokodierung; ohne Treffer fehlt `results` ganz. */
    fun parsePlaces(body: String): List<Place> = decode<GeoResponse>(body).results.map {
        Place(it.name, it.latitude, it.longitude, it.country.orEmpty(), it.admin1.orEmpty())
    }

    fun parseTemperature(body: String): Double = decode<ForecastResponse>(body).current.temperature

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString<T>(body)
        } catch (e: IllegalArgumentException) {
            // SerializationException ist eine IllegalArgumentException.
            throw IOException("Unerwartete Antwort von Open-Meteo", e)
        }

    @Serializable
    private class GeoResponse(val results: List<GeoResult> = emptyList())

    @Serializable
    private class GeoResult(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val admin1: String? = null,
    )

    @Serializable
    private class ForecastResponse(val current: Current)

    @Serializable
    private class Current(@SerialName("temperature_2m") val temperature: Double)
}

/** GET mit [OpenMeteo.TIMEOUT_MS] für Verbindung und Lesen; Antwort als UTF-8, sonst [IOException]. */
internal fun httpGet(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = OpenMeteo.TIMEOUT_MS
        connection.readTimeout = OpenMeteo.TIMEOUT_MS
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
        return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    } finally {
        connection.disconnect()
    }
}
