package de.hotelremote.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

internal class MemoryWeatherStore(var saved: WeatherSettings = WeatherSettings()) : WeatherStore {
    override fun load() = saved
    override fun save(settings: WeatherSettings) {
        saved = settings
    }
}

/** Zählt jeden Aufruf: jeder wäre eine Netzverbindung. */
internal class FakeWeatherApi(var celsius: Double = 31.0, var fail: Boolean = false) : WeatherApi {
    val calls = mutableListOf<String>()
    val places = listOf(Place("Pattaya", 12.9, 100.9, "Thailand", "Chon Buri"))

    override fun searchPlaces(name: String, language: String): List<Place> {
        calls += "search:$name:$language"
        if (fail) throw IOException("offline")
        return places
    }

    override fun currentTemperature(latitude: Double, longitude: Double): Double {
        calls += "temp:$latitude,$longitude"
        if (fail) throw IOException("offline")
        return celsius
    }
}

internal val PATTAYA = Place("Pattaya", 12.9, 100.9, "Thailand", "Chon Buri")

class OutdoorWeatherTest {
    private var now = 1_000_000L
    private val store = MemoryWeatherStore()
    private val api = FakeWeatherApi()
    private fun weather() = OutdoorWeather(store, api) { now }

    @Test fun offByDefaultAndNoNetworkWithoutActivation() {
        val w = weather()
        assertFalse(w.state.value.enabled)
        assertEquals(WeatherResult.Inactive, w.refresh())
        assertEquals(emptyList<Place>(), w.search("Pattaya", "de"))
        assertFalse(w.needsRefresh())
        // Eingeschaltet, aber noch ohne Ort: Suche geht, Abruf nicht.
        w.setEnabled(true)
        assertEquals(WeatherResult.Inactive, w.refresh())
        assertEquals(api.places, w.search("Pattaya", "de"))
        assertEquals(listOf("search:Pattaya:de"), api.calls)
        // Wieder aus (Ort bleibt gemerkt): keine Verbindung mehr.
        w.choose(PATTAYA)
        w.setEnabled(false)
        api.calls.clear()
        assertEquals(WeatherResult.Inactive, w.refresh())
        assertEquals(emptyList<Place>(), w.search("Pattaya", "de"))
        assertFalse(w.needsRefresh())
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test fun chooseAndRefreshStoresReadingWithTime() {
        val w = weather()
        w.choose(PATTAYA)
        assertTrue(w.state.value.active)
        assertTrue(w.needsRefresh())
        assertEquals(WeatherResult.Ok(Reading(31.0, now)), w.refresh())
        assertEquals(listOf("temp:12.9,100.9"), api.calls)
        assertEquals(Reading(31.0, now), store.saved.last)
        assertEquals(PATTAYA, store.saved.place)
        // Neu abrufen erst ab 15 min.
        now += OutdoorWeather.REFRESH_AFTER_MS - 1
        assertFalse(w.needsRefresh())
        now += 1
        assertTrue(w.needsRefresh())
    }

    @Test fun failureKeepsLastReading() {
        val w = weather()
        w.choose(PATTAYA)
        w.refresh()
        api.fail = true
        assertEquals(WeatherResult.Failed, w.refresh())
        assertEquals(Reading(31.0, now), w.state.value.last)
    }

    @Test fun otherPlaceDropsOldReading() {
        val w = weather()
        w.choose(PATTAYA)
        w.refresh()
        w.choose(PATTAYA)
        assertEquals(31.0, w.state.value.last!!.celsius, 0.0)
        w.choose(Place("Berlin", 52.5, 13.4))
        assertNull(w.state.value.last)
    }

    @Test fun staleAfterThreeHours() {
        val r = Reading(30.0, 0)
        assertFalse(OutdoorWeather.isStale(r, OutdoorWeather.STALE_AFTER_MS - 1))
        assertTrue(OutdoorWeather.isStale(r, 3 * 60 * 60_000L))
    }

    @Test fun reloadReadsStore() {
        val w = weather()
        store.saved = WeatherSettings(enabled = true, place = PATTAYA, last = Reading(29.0, 5))
        w.reload()
        assertEquals(store.saved, w.state.value)
    }

    @Test fun celsiusToFahrenheitConverts() {
        assertEquals(91.4, celsiusToFahrenheit(33.0), 1e-9)
        assertEquals(32.0, celsiusToFahrenheit(0.0), 0.0)
    }
}
