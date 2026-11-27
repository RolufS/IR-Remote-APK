package de.hotelremote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SavedAcTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(): File = File(tmp.root, "remotes.json")

    /** remotes.json wie von Version 1.7 geschrieben (ohne Feld `ac`). */
    private val oldJson = """[{"id":"a","name":"Zimmer 12","brand":"Samsung","codeLabel":"Code 3",""" +
        """"keys":{"POWER":{"freq":38000,"frame":[9000,4500,560],"repeat":[9000,2250,560],"periodMs":108}},""" +
        """"lastUsed":100,"automations":[{"id":"x","name":"Aus","steps":[{"key":"POWER","delayAfterMs":1500}]}]}]"""

    @Test fun oldFileWithoutAcIsReadable() {
        file().writeText(oldJson)
        val r = RemoteStore(file()).all().single()
        assertEquals("Zimmer 12", r.title)
        assertEquals(setOf("POWER"), r.keys.keys)
        assertEquals(1, r.automations.size)
        assertNull(r.ac)
        assertNull(r.lastPage)
        assertTrue(r.hasTv)
    }

    @Test fun roundTripWithAcAndDecodableCommands() {
        val store = RemoteStore(file())
        val ac = testAc(SWING_PATHS, AcState(true, "cool", 22.5, "auto", "swing"))
        val room = SavedRemote(id = "a", brand = "LG", codeLabel = "Code 1", keys = emptyMap(), lastUsed = 1, ac = ac)
        store.add(room)
        val back = RemoteStore(file()).all().single()
        assertEquals(room, back)
        assertEquals(pulsesOf(SWING_PATHS, "cool/auto/swing/22.5"), back.ac!!.decodeCodes().pulses("cool/auto/swing/22.5")!!.toList())
    }

    @Test fun pureAcRoomTitleFallsBackToAcName() {
        val room = SavedRemote(id = "a", lastUsed = 1, ac = testAc())
        assertFalse(room.hasTv)
        assertEquals("Acme · AC-1", room.title)
        assertEquals("Wohnzimmer", room.copy(name = "Wohnzimmer").title)
        val store = RemoteStore(file())
        store.add(room)
        assertEquals(room, RemoteStore(file()).all().single())
    }

    @Test fun setRemoveAndUpdateAcState() {
        val store = RemoteStore(file())
        store.add(SavedRemote(id = "a", brand = "LG", codeLabel = "Code 1", keys = emptyMap(), lastUsed = 1))
        store.updateAcState("a", AcState(false, "heat", 20.0, "auto"))
        assertNull("ohne Klimaanlage keine Änderung", store.all().single().ac)

        store.setAc("a", testAc())
        val off = AcState(false, "heat", 19.0, "auto")
        store.updateAcState("a", off)
        assertEquals(off, RemoteStore(file()).all().single().ac!!.state)

        store.setAc("a", null)
        assertNull(RemoteStore(file()).all().single().ac)
        store.setAc("unbekannt", testAc())
        assertEquals(1, store.all().size)
    }

    @Test fun lastPageIsStored() {
        val store = RemoteStore(file())
        store.add(SavedRemote(id = "a", brand = "LG", codeLabel = "Code 1", lastUsed = 1, ac = testAc()))
        store.setLastPage("a", RoomPage.AC)
        assertEquals(RoomPage.AC, RemoteStore(file()).all().single().lastPage)
        store.setLastPage("a", RoomPage.TV)
        assertEquals(RoomPage.TV, RemoteStore(file()).all().single().lastPage)
    }

    @Test fun fahrenheitDetectedFromMaxTemp() {
        assertFalse(acTempsInFahrenheit(30.0))
        assertFalse(acTempsInFahrenheit(32.0))
        assertTrue(acTempsInFahrenheit(86.0))
        assertTrue(testAc(maxTemp = 88.0).fahrenheit)
        assertFalse(testAc().fahrenheit)
    }

    @Test fun temperatureText() {
        assertEquals("22 °C", acTempText(22.0, fahrenheit = false))
        assertEquals("22.5 °C", acTempText(22.5, fahrenheit = false))
        assertEquals("72 °F", acTempText(72.0, fahrenheit = true))
    }
}
