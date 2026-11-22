package de.hotelremote.ui

import de.hotelremote.Keys
import de.hotelremote.data.Brand
import de.hotelremote.data.CodeCatalog
import de.hotelremote.data.RemoteStorage
import de.hotelremote.data.SavedRemote
import de.hotelremote.data.ScanEntry
import de.hotelremote.ir.IrSignal
import de.hotelremote.wizard.WizardState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private fun sig(n: Int) = IrSignal(freq = 38000, frame = listOf(n), periodMs = 100)

private fun remote(id: String, lastUsed: Long, name: String? = null) =
    SavedRemote(id = id, name = name, brand = "Samsung", codeLabel = "Code 1", keys = mapOf(Keys.POWER to sig(1)), lastUsed = lastUsed)

private class FakeStorage(initial: List<SavedRemote> = emptyList()) : RemoteStorage {
    val list = initial.toMutableList()
    var failLoad = false
    var failWrite = false

    override fun all(): List<SavedRemote> {
        if (failLoad) throw IOException("lesen")
        return list.sortedByDescending { it.lastUsed }
    }

    private fun write(change: () -> Unit) {
        if (failWrite) throw IOException("voll")
        change()
    }

    override fun add(r: SavedRemote) = write { list += r }
    override fun rename(id: String, name: String?) = write {
        list.replaceAll { if (it.id == id) it.copy(name = name) else it }
    }
    override fun delete(id: String) = write { list.removeAll { it.id == id } }
    override fun markUsed(id: String, now: Long) = write {
        list.replaceAll { if (it.id == id) it.copy(lastUsed = now) else it }
    }
}

/** Eine Marke mit einem Code-Satz (POWER, MUTE) und zwei Suchlauf-Einträgen. */
private class FakeCatalog : CodeCatalog {
    override fun brands() = listOf(Brand(1, "Samsung", 1, 1))
    override fun meta() = mapOf("code_sets" to "1")
    override fun codeSetsForBrand(brandId: Long) = listOf(10L)
    override fun keys(codeSetId: Long) = mapOf(Keys.POWER to sig(10), Keys.MUTE to sig(11))
    override fun inputVariants(codeSetId: Long, brandId: Long) = emptyList<Map<String, IrSignal>>()
    override fun scanEntries() = listOf(ScanEntry(0, 10, 1, "Samsung", sig(10)), ScanEntry(1, 10, 1, "Samsung", sig(12)))
    override fun brandName(brandId: Long) = "Samsung"
    override fun powerSiblings(codeSetId: Long) = emptyList<Long>()
    override fun topBrand(codeSetId: Long): Long? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppControllerTest {
    private val messages = AppMessages(saveFailed = "Speichern fehlgeschlagen", loadFailed = "Laden fehlgeschlagen")
    private val sent = mutableListOf<IrSignal>()

    private fun TestScope.controller(
        store: RemoteStorage,
        catalog: () -> CodeCatalog = { FakeCatalog() },
    ): AppController {
        val d = StandardTestDispatcher(testScheduler)
        return AppController(
            store = store,
            openCatalog = catalog,
            send = { sent += it },
            scope = backgroundScope,
            io = d,
            messages = messages,
            irAvailable = true,
            clock = { 1000L },
        ).also { runCurrent() }
    }

    @Test fun startsWithLastUsedRemote() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1), remote("b", 2))))
        val s = c.state.value
        assertFalse(s.loading)
        assertFalse(s.dbError)
        assertEquals("b", s.current?.id)
        assertEquals(Screen.Remote, s.screen)
        assertEquals(1, s.brands.size)
    }

    @Test fun startsWizardWithoutSavedRemote() = runTest {
        val c = controller(FakeStorage())
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertEquals(WizardState.PickBrand, c.state.value.wizard)
    }

    @Test fun dbErrorKeepsSavedRemotesUsable() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1)))) { throw IllegalStateException("kein Platz") }
        val s = c.state.value
        assertFalse(s.loading)
        assertTrue(s.dbError)
        assertEquals("a", s.current?.id)
        assertEquals(Screen.Remote, s.screen)
        c.openWizard()
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertTrue(c.wizardBack())
        assertEquals(Screen.Remote, c.state.value.screen)
    }

    @Test fun dbErrorWithoutRemotesShowsWizardErrorAndBackExits() = runTest {
        val c = controller(FakeStorage()) { throw RuntimeException("defekt") }
        assertTrue(c.state.value.dbError)
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertFalse(c.wizardBack())
    }

    @Test fun storeLoadFailureShowsMessageAndOpensWizard() = runTest {
        val c = controller(FakeStorage().apply { failLoad = true })
        assertEquals(messages.loadFailed, c.state.value.message)
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertFalse(c.state.value.dbError)
    }

    @Test fun renameFailureShowsMessageAndKeepsState() = runTest {
        val store = FakeStorage(listOf(remote("a", 1)))
        val c = controller(store)
        store.failWrite = true
        c.rename("a", "Zimmer 1")
        assertEquals(messages.saveFailed, c.state.value.message)
        assertNull(c.state.value.current?.name)
    }

    @Test fun deleteFailureShowsMessageAndKeepsRemote() = runTest {
        val store = FakeStorage(listOf(remote("a", 1)))
        val c = controller(store)
        store.failWrite = true
        c.delete("a")
        assertEquals(messages.saveFailed, c.state.value.message)
        assertEquals(listOf("a"), c.state.value.remotes.map { it.id })
        assertEquals(Screen.Remote, c.state.value.screen)
    }

    @Test fun switchFailureStillSwitchesAndShowsMessage() = runTest {
        val store = FakeStorage(listOf(remote("a", 1), remote("b", 2)))
        val c = controller(store)
        store.failWrite = true
        c.switchTo("a")
        assertEquals("a", c.state.value.current?.id)
        assertEquals(Screen.Remote, c.state.value.screen)
        assertEquals(messages.saveFailed, c.state.value.message)
    }

    private fun AppController.findAndSave(name: String?) {
        pickBrand(1)
        answer(true) // MUTE hat gewirkt; ohne VOL_UP und Eingänge direkt zum Speichern
        assertTrue(state.value.wizard is WizardState.Save)
        saveWizard(name, "Samsung")
    }

    @Test fun saveWizardStoresAndShowsRemote() = runTest {
        val store = FakeStorage()
        val c = controller(store)
        c.findAndSave("Zimmer 412")
        assertEquals(1, store.list.size)
        assertEquals("Zimmer 412", c.state.value.current?.name)
        assertEquals(Screen.Remote, c.state.value.screen)
        assertNull(c.state.value.message)
    }

    @Test fun saveWizardFailureKeepsRemoteForSessionAndShowsMessage() = runTest {
        val store = FakeStorage().apply { failWrite = true }
        val c = controller(store)
        c.findAndSave(null)
        assertEquals(messages.saveFailed, c.state.value.message)
        assertEquals(Screen.Remote, c.state.value.screen)
        assertEquals(listOf(c.state.value.current), c.state.value.remotes)
        assertTrue(store.list.isEmpty())
    }

    @Test fun backOnPickBrandWithoutRemoteRequestsExit() = runTest {
        val c = controller(FakeStorage())
        assertFalse(c.wizardBack())
    }

    @Test fun backOnPickBrandWithRemoteReturnsToRemote() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.openWizard()
        assertTrue(c.wizardBack())
        assertEquals(Screen.Remote, c.state.value.screen)
    }

    @Test fun appStoppedDuringScanStopsSendingAndVerifies() = runTest {
        val c = controller(FakeStorage())
        c.startScan(0)
        runCurrent()
        assertEquals(1, sent.size)
        c.onAppStopped()
        assertTrue(c.state.value.wizard is WizardState.ScanVerify)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(1, sent.size)
    }

    @Test fun appStoppedOutsideScanChangesNothing() = runTest {
        val c = controller(FakeStorage())
        c.onAppStopped()
        assertEquals(WizardState.PickBrand, c.state.value.wizard)
    }
}
