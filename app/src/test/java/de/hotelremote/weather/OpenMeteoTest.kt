package de.hotelremote.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Beispielantworten nach der öffentlichen API-Doku von Open-Meteo (Geokodierung und Vorhersage); kein echtes Netz. */
class OpenMeteoTest {
    private val geocoding = """
        {"results":[
          {"id":1607532,"name":"Pattaya","latitude":12.92721,"longitude":100.87677,"elevation":6.0,
           "feature_code":"PPL","country_code":"TH","admin1_id":1611108,"timezone":"Asia/Bangkok",
           "population":119532,"country_id":1605651,"country":"Thailand","admin1":"Chon Buri"},
          {"id":2950159,"name":"Berlin","latitude":52.52437,"longitude":13.41053,"country":"Deutschland","admin1":"Berlin"},
          {"id":1,"name":"Nirgendwo","latitude":1.5,"longitude":-2.25}
        ],"generationtime_ms":0.5}
    """.trimIndent()

    private val forecast = """
        {"latitude":12.875,"longitude":100.875,"generationtime_ms":0.02,"utc_offset_seconds":0,"timezone":"GMT",
         "timezone_abbreviation":"GMT","elevation":6.0,
         "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C"},
         "current":{"time":"2026-11-27T15:00","interval":900,"temperature_2m":31.4}}
    """.trimIndent()

    @Test fun parsesPlacesWithCountryAndRegion() {
        val places = OpenMeteo.parsePlaces(geocoding)
        assertEquals(3, places.size)
        assertEquals(Place("Pattaya", 12.92721, 100.87677, "Thailand", "Chon Buri"), places[0])
        assertEquals("Chon Buri, Thailand", places[0].detail)
        // Region gleich dem Namen wird nicht wiederholt; fehlende Felder sind leer.
        assertEquals("Deutschland", places[1].detail)
        assertEquals("", places[2].detail)
    }

    @Test fun noResultsMeansEmptyList() {
        assertEquals(emptyList<Place>(), OpenMeteo.parsePlaces("""{"generationtime_ms":0.3}"""))
    }

    @Test fun parsesCurrentTemperature() {
        assertEquals(31.4, OpenMeteo.parseTemperature(forecast), 0.0)
    }

    @Test fun malformedAnswersAreIoErrors() {
        for (body in listOf("<html>", """{"current":{}}""", """{"results":[{"name":"x"}]}""")) {
            try {
                if (body.contains("results")) OpenMeteo.parsePlaces(body) else OpenMeteo.parseTemperature(body)
                throw AssertionError("keine IOException für $body")
            } catch (e: IOException) {
                // erwartet
            }
        }
    }

    @Test fun urlsEncodeNameAndUseLanguageAndCoordinates() {
        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=Ko+Samui+%26+S%C3%BCd&count=5&language=de",
            OpenMeteo.searchUrl(" Ko Samui & Süd ", "de"),
        )
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=12.9272&longitude=-100.8768&current=temperature_2m",
            OpenMeteo.forecastUrl(12.92721, -100.87677),
        )
    }

    @Test fun languageFromAppLanguage() {
        assertEquals("de", OpenMeteo.language("de-DE"))
        assertEquals("zh", OpenMeteo.language("zh-CN"))
        assertEquals("id", OpenMeteo.language("in"))
        assertEquals("pt", OpenMeteo.language("pt_BR"))
        assertEquals("en", OpenMeteo.language(""))
    }

    @Test fun clientRequestsExactlyTheDocumentedUrls() {
        val requested = mutableListOf<String>()
        val client = OpenMeteoClient { url ->
            requested += url
            if (url.contains("geocoding")) geocoding else forecast
        }
        assertEquals("Pattaya", client.searchPlaces("Pattaya", "en").first().name)
        assertEquals(31.4, client.currentTemperature(12.92721, 100.87677), 0.0)
        assertEquals(
            listOf(
                "https://geocoding-api.open-meteo.com/v1/search?name=Pattaya&count=5&language=en",
                "https://api.open-meteo.com/v1/forecast?latitude=12.9272&longitude=100.8768&current=temperature_2m",
            ),
            requested,
        )
        assertTrue(requested.all { it.startsWith("https://") })
    }
}
