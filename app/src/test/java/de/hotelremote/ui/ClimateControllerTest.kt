package de.hotelremote.ui

import de.hotelremote.acauto.AcAutoRunner
import de.hotelremote.acauto.AcAutoScheduler
import de.hotelremote.acauto.FakeAcAutoAlarm
import de.hotelremote.acauto.FakeAcAutoNotifier
import de.hotelremote.acauto.FakeAcAutoStore
import de.hotelremote.data.AcAutoSettings
import de.hotelremote.data.AcState
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.data.testAc
import de.hotelremote.ir.IrSignal
import de.hotelremote.weather.FakeWeatherApi
import de.hotelremote.weather.MemoryWeatherStore
import de.hotelremote.weather.OutdoorWeather
import de.hotelremote.weather.PATTAYA
import de.hotelremote.weather.Reading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ClimateControllerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000_000L
    private val api = FakeWeatherApi(celsius = 30.0)
    private val weather = OutdoorWeather(MemoryWeatherStore(), api) { now }
    private val autoStore = FakeAcAutoStore()
    private val scheduler = AcAutoScheduler(autoStore, FakeAcAutoAlarm(), FakeAcAutoNotifier()) { now }
    private val sent = mutableListOf<IrSignal>()
    private val remotes by lazy {
        RemoteStore(File(tmp.root, "remotes.json")).also {
            it.add(SavedRemote(id = "r1", lastUsed = 1, ac = testAc().copy(auto = AcAutoSettings(5.0, 20.0))))
        }
    }

    private fun TestScope.climate(): ClimateController {
        val d = StandardTestDispatcher(testScheduler)
        val runner = AcAutoRunner(scheduler, remotes, weather, { sent += it }, Dispatchers.Unconfined)
        return ClimateController(weather, scheduler, runner, backgroundScope, d)
    }

    @Test fun noNetworkUntilEnabledAndPlaceChosen() = runTest {
        val c = climate()
        c.acScreenShown()
        c.refresh()
        c.search("Pattaya", "de-DE")
        c.startAuto("r1")
        runCurrent()
        assertTrue(api.calls.isEmpty())
        assertNull(scheduler.current())
        assertFalse(c.ui.value.refreshing)
    }

    @Test fun searchUsesAppLanguageAndShowsResults() = runTest {
        val c = climate()
        c.setWeatherEnabled(true)
        c.search("Pattaya", "in")
        assertTrue(c.ui.value.searching)
        runCurrent()
        assertEquals(listOf("search:Pattaya:id"), api.calls)
        assertEquals(api.places, c.ui.value.results)
        api.fail = true
        c.search("Pattaya", "de")
        runCurrent()
        assertTrue(c.ui.value.searchFailed)
        assertEquals(api.places, c.ui.value.results)
    }

    @Test fun chooseFetchesAndScreenRefreshesOnlyWhenOlderThan15Minutes() = runTest {
        val c = climate()
        c.choose(PATTAYA)
        runCurrent()
        assertEquals(Reading(30.0, now), c.weather.value.last)
        assertEquals(1, api.calls.size)
        now += 14 * 60_000L
        c.acScreenShown()
        runCurrent()
        assertEquals(1, api.calls.size)
        now += 60_000L
        c.acScreenShown()
        runCurrent()
        assertEquals(2, api.calls.size)
        api.fail = true
        c.refresh()
        runCurrent()
        assertTrue(c.ui.value.refreshFailed)
    }

    @Test fun startSendsAtOnceAndSwitchingWeatherOffStops() = runTest {
        val c = climate()
        c.choose(PATTAYA)
        runCurrent()
        c.startAuto("r1")
        runCurrent()
        assertEquals(1, sent.size)
        assertEquals(AcState(true, "cool", 24.0, "auto"), c.run.value?.sent)
        // Geänderte Einstellungen: gleich neu rechnen, gesendet nur bei neuem Ziel.
        c.settingsChanged("r1")
        runCurrent()
        assertEquals(1, sent.size)
        remotes.setAcAuto("r1", AcAutoSettings(9.0, 20.0))
        c.settingsChanged("r1")
        c.settingsChanged("other")
        runCurrent()
        assertEquals(2, sent.size)
        assertEquals(21.0, c.run.value?.sent?.temp)
        assertTrue(c.setWeatherEnabled(false))
        assertNull(c.run.value)
        assertFalse(c.setWeatherEnabled(false))
    }

    @Test fun stopAutoForOnlyThatRoom() = runTest {
        val c = climate()
        scheduler.start("r1")
        assertFalse(c.stopAutoFor("r2"))
        assertTrue(c.stopAutoFor("r1"))
        assertNull(c.run.value)
    }
}
