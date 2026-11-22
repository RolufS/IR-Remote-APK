package de.hotelremote.data

import de.hotelremote.ir.IrSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RemoteStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(): File = File(tmp.root, "remotes.json")

    private fun sig(n: Int) = IrSignal(
        freq = 38000,
        frame = listOf(9000, 4500, 560, n),
        repeat = listOf(9000, 2250, 560),
        frameAlt = listOf(1, 2, 3),
        periodMs = 108,
    )

    private fun remote(id: String, lastUsed: Long, name: String? = null) = SavedRemote(
        id = id,
        name = name,
        brand = "Samsung",
        codeLabel = "Code 3",
        keys = mapOf("POWER" to sig(1), "X:Extra" to sig(2)),
        lastUsed = lastUsed,
    )

    @Test fun addAndReadBack() {
        val store = RemoteStore(file())
        val a = remote("a", 100)
        val b = remote("b", 200)
        store.add(a)
        store.add(b)
        assertEquals(listOf(b, a), RemoteStore(file()).all())
        assertEquals(b, store.lastUsed())
    }

    @Test fun renameAndBlankNameFallsBackToLabel() {
        val store = RemoteStore(file())
        store.add(remote("a", 1))
        store.rename("a", "Zimmer 12")
        assertEquals("Zimmer 12", store.all().single().title)
        store.rename("a", "  ")
        assertEquals("Samsung · Code 3", store.all().single().title)
        store.rename("unbekannt", "x")
        assertEquals(1, store.all().size)
    }

    @Test fun deleteRemoves() {
        val store = RemoteStore(file())
        store.add(remote("a", 1))
        store.add(remote("b", 2))
        store.delete("a")
        store.delete("unbekannt")
        assertEquals(listOf("b"), store.all().map { it.id })
    }

    @Test fun markUsedChangesLastUsed() {
        val store = RemoteStore(file())
        store.add(remote("a", 1))
        store.add(remote("b", 2))
        store.markUsed("a", 500)
        store.markUsed("unbekannt", 900)
        assertEquals(listOf("a", "b"), store.all().map { it.id })
        assertEquals(500L, store.lastUsed()?.lastUsed)
    }

    @Test fun corruptFileIsBackedUpAndIgnored() {
        val f = file()
        f.writeText("{kaputt")
        val store = RemoteStore(f)
        assertEquals(emptyList<SavedRemote>(), store.all())
        val bak = File(f.path + ".bak")
        assertTrue(bak.exists())
        assertEquals("{kaputt", bak.readText())
        store.add(remote("a", 1))
        assertEquals(listOf("a"), RemoteStore(f).all().map { it.id })
    }

    @Test fun missingFileIsEmpty() {
        val store = RemoteStore(file())
        assertEquals(emptyList<SavedRemote>(), store.all())
        assertNull(store.lastUsed())
    }
    @Test fun automationsAreSavedAndLoaded() {
        val store = RemoteStore(file())
        store.add(remote("a", 1))
        store.add(remote("b", 2))
        val list = listOf(
            Automation("x", "Einschlafen", listOf(AutomationStep("MENU"), AutomationStep("X:Sleep", delayAfterMs = 500))),
        )
        store.updateAutomations("a", list)
        store.updateAutomations("unbekannt", list)
        val loaded = RemoteStore(file()).all().associateBy { it.id }
        assertEquals(list, loaded.getValue("a").automations)
        assertEquals(emptyList<Automation>(), loaded.getValue("b").automations)
        assertEquals(1500L, loaded.getValue("a").automations.single().steps.first().delayAfterMs)
    }

    @Test fun oldJsonWithoutAutomationsLoadsWithEmptyList() {
        val f = file()
        f.writeText(
            """[{"id":"a","brand":"LG","codeLabel":"Code 1","keys":{"POWER":{"freq":38000,"frame":[1,2,3],"periodMs":108}},"lastUsed":5}]""",
        )
        val r = RemoteStore(f).all().single()
        assertEquals("a", r.id)
        assertEquals(emptyList<Automation>(), r.automations)
    }
}
