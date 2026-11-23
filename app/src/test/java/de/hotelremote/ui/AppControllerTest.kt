package de.hotelremote.ui

import de.hotelremote.Keys
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.Brand
import de.hotelremote.data.CodeCatalog
import de.hotelremote.data.RemoteStorage
import de.hotelremote.data.SavedRemote
import de.hotelremote.data.ScanEntry
import de.hotelremote.ir.IrSignal
import de.hotelremote.wizard.WizardState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    override fun updateAutomations(id: String, automations: List<Automation>) = write {
        list.replaceAll { if (it.id == id) it.copy(automations = automations) else it }
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
    private val sent = mutableListOf<IrSignal>()

    private fun TestScope.controller(
        store: RemoteStorage,
        languageChosen: Boolean = true,
        hold: ((IrSignal) -> Job?)? = null,
        recorderClock: () -> Long = { 0L },
        catalog: () -> CodeCatalog = { FakeCatalog() },
    ): AppController {
        val d = StandardTestDispatcher(testScheduler)
        return AppController(
            store = store,
            openCatalog = catalog,
            send = { sent += it },
            scope = backgroundScope,
            io = d,
            irAvailable = true,
            clock = { 1000L },
            languageChosen = languageChosen,
            hold = hold ?: { sent += it; null },
            recorderClock = recorderClock,
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
        assertEquals(UiMessage.LOAD_FAILED, c.state.value.message)
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertFalse(c.state.value.dbError)
    }

    @Test fun renameFailureShowsMessageAndKeepsState() = runTest {
        val store = FakeStorage(listOf(remote("a", 1)))
        val c = controller(store)
        store.failWrite = true
        c.rename("a", "Zimmer 1")
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
        assertNull(c.state.value.current?.name)
    }

    @Test fun deleteFailureShowsMessageAndKeepsRemote() = runTest {
        val store = FakeStorage(listOf(remote("a", 1)))
        val c = controller(store)
        store.failWrite = true
        c.delete("a")
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
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
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
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
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
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

    // ---- Sprache ----

    @Test fun firstStartShowsLanguageBeforeRemote() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))), languageChosen = false)
        assertEquals(Screen.Language, c.state.value.screen)
        assertTrue(c.state.value.languageFirstStart)
        c.languageChosen()
        assertEquals(Screen.Remote, c.state.value.screen)
        assertFalse(c.state.value.languageFirstStart)
    }

    @Test fun firstStartShowsLanguageBeforeWizard() = runTest {
        val c = controller(FakeStorage(), languageChosen = false)
        assertEquals(Screen.Language, c.state.value.screen)
        c.languageChosen()
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertEquals(WizardState.PickBrand, c.state.value.wizard)
    }

    @Test fun languageChosenWhileLoadingStillRoutesAfterLoad() = runTest {
        val d = StandardTestDispatcher(testScheduler)
        val c = AppController(
            store = FakeStorage(listOf(remote("a", 1))), openCatalog = { FakeCatalog() }, send = {},
            scope = backgroundScope, io = d, irAvailable = true, languageChosen = false,
        )
        assertEquals(Screen.Language, c.state.value.screen)
        assertTrue(c.state.value.loading)
        c.languageChosen()
        runCurrent()
        assertFalse(c.state.value.loading)
        assertEquals(Screen.Remote, c.state.value.screen)
        assertEquals("a", c.state.value.current?.id)
    }

    @Test fun noLanguageScreenWhenAlreadyChosen() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        assertEquals(Screen.Remote, c.state.value.screen)
        assertFalse(c.state.value.languageFirstStart)
    }

    @Test fun languageFromRemoteMenuReturnsToRemote() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.openLanguage()
        assertEquals(Screen.Language, c.state.value.screen)
        assertFalse(c.state.value.languageFirstStart)
        c.closeLanguage()
        assertEquals(Screen.Remote, c.state.value.screen)
        c.openLanguage()
        c.languageChosen()
        assertEquals(Screen.Remote, c.state.value.screen)
        assertEquals(1, c.state.value.remotes.size)
    }

    @Test fun languageFromWizardKeepsWizardState() = runTest {
        val c = controller(FakeStorage())
        c.pickBrand(1)
        val wizard = c.state.value.wizard
        c.openLanguage()
        c.closeLanguage()
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertEquals(wizard, c.state.value.wizard)
    }

    @Test fun openingLanguageStopsRunningScan() = runTest {
        val c = controller(FakeStorage())
        c.startScan(0)
        runCurrent()
        assertTrue(c.state.value.wizard is WizardState.Scanning)
        c.openLanguage()
        val sentBefore = sent.size
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(sentBefore, sent.size)
        assertFalse(c.state.value.wizard is WizardState.Scanning)
    }
    @Test fun saveAndDeleteAutomationOfCurrentRemote() = runTest {
        val store = FakeStorage(listOf(remote("a", 1), remote("b", 2)))
        val c = controller(store)
        val a = Automation("x", "Einschlafen", listOf(AutomationStep(Keys.POWER)))
        assertTrue(c.saveAutomation(a))
        assertEquals(listOf(a), c.state.value.current?.automations)
        assertEquals(listOf(a), store.list.first { it.id == "b" }.automations)
        val renamed = a.copy(name = "Aus")
        c.saveAutomation(renamed)
        assertEquals(listOf(renamed), c.state.value.current?.automations)
        c.deleteAutomation("x")
        assertEquals(emptyList<Automation>(), c.state.value.current?.automations)
    }

    @Test fun saveAutomationFailureShowsMessage() = runTest {
        val store = FakeStorage(listOf(remote("a", 1)))
        val c = controller(store)
        store.failWrite = true
        assertFalse(c.saveAutomation(Automation("x", "A", listOf(AutomationStep(Keys.POWER)))))
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
        assertEquals(emptyList<Automation>(), c.state.value.current?.automations)
    }

    @Test fun runAutomationSendsCurrentKeysAndReportsMissing() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.runAutomation("Test", listOf(AutomationStep(Keys.POWER, 1000), AutomationStep(Keys.MENU), AutomationStep(Keys.POWER)))
        runCurrent()
        assertEquals("Test", c.automationProgress.value?.name)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(listOf(sig(1), sig(1)), sent)
        assertEquals(UiMessage.KEYS_MISSING, c.state.value.message)
        assertEquals(listOf(Keys.MENU), c.state.value.missingKeys)
        assertNull(c.automationProgress.value)
    }

    @Test fun secondAutomationWhileRunningIsRejected() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.runAutomation("A", listOf(AutomationStep(Keys.POWER, 1000), AutomationStep(Keys.POWER)))
        runCurrent()
        c.runAutomation("B", listOf(AutomationStep(Keys.POWER)))
        assertEquals(UiMessage.AUTOMATION_BUSY, c.state.value.message)
        c.cancelAutomation()
        runCurrent()
        assertNull(c.automationProgress.value)
        assertEquals(1, sent.size)
    }

    // ---- Aufnahme ----

    private fun recordingRemote(automations: List<Automation> = emptyList()) = SavedRemote(
        id = "r",
        brand = "Samsung",
        codeLabel = "Code 1",
        keys = mapOf(Keys.POWER to sig(1), Keys.MENU to sig(2), Keys.OK to sig(3), Keys.VOL_UP to sig(4)),
        lastUsed = 1,
        automations = automations,
    )

    @Test fun recordingPausesUseTheMonotonicClockNotTheWallClock() = runTest {
        // Wanduhr steht (clock = 1000), die monotone Uhr läuft 3 s weiter: die Pause wird 3 s.
        var t = 0L
        val c = controller(FakeStorage(listOf(recordingRemote())), recorderClock = { t })
        c.recordNew(returnTo = Screen.Remote)
        c.press(Keys.MENU)
        t = 3_000
        c.press(Keys.OK)
        assertEquals(3_000L, c.recordedSteps.value.first().delayAfterMs)
    }

    @Test fun pressWhileRecordingSendsAndRecords() = runTest {
        val c = controller(FakeStorage(listOf(recordingRemote())))
        c.press(Keys.MENU)
        assertEquals(listOf(sig(2)), sent)
        assertEquals(emptyList<AutomationStep>(), c.recordedSteps.value)
        c.recordNew(returnTo = Screen.Automations)
        assertEquals(Screen.Remote, c.state.value.screen)
        assertEquals(0, c.state.value.recording?.existingCount)
        c.press(Keys.MENU)
        c.press(Keys.OK)
        assertEquals(listOf(sig(2), sig(2), sig(3)), sent)
        assertEquals(listOf(Keys.MENU, Keys.OK), c.recordedSteps.value.map { it.key })
        // Tasten, die diese Fernbedienung nicht hat, werden weder gesendet noch aufgenommen.
        c.press(Keys.EXIT)
        assertEquals(2, c.recordedSteps.value.size)
        c.undoRecording()
        assertEquals(listOf(Keys.MENU), c.recordedSteps.value.map { it.key })
    }

    @Test fun holdingRepeatingKeyRecordsOneStep() = runTest {
        var repeats = 0
        val c = controller(FakeStorage(listOf(recordingRemote())), hold = { signal ->
            backgroundScope.launch {
                while (true) {
                    sent += signal
                    repeats++
                    delay(100)
                }
            }
        })
        c.recordNew(returnTo = Screen.Remote)
        val job = c.hold(Keys.VOL_UP)
        advanceTimeBy(1_050)
        job?.cancel()
        runCurrent()
        assertTrue("Wiederholungen: $repeats", repeats > 5)
        assertEquals(listOf(Keys.VOL_UP), c.recordedSteps.value.map { it.key })
    }

    @Test fun finishOpensEditorWithRecordedSteps() = runTest {
        val c = controller(FakeStorage(listOf(recordingRemote())))
        c.recordNew(returnTo = Screen.Automations)
        c.press(Keys.MENU)
        c.press(Keys.OK)
        c.finishRecording()
        val screen = c.state.value.screen as Screen.AutomationEdit
        assertNull(screen.automationId)
        assertEquals(AutomationDraft("", listOf(AutomationStep(Keys.MENU, 500), AutomationStep(Keys.OK))), screen.draft)
        assertNull(c.state.value.recording)
        c.press(Keys.MENU)
        assertEquals(emptyList<AutomationStep>(), c.recordedSteps.value)
    }

    @Test fun recordingFromEditorAppendsAndKeepsName() = runTest {
        val existing = listOf(AutomationStep(Keys.POWER, 3_000))
        val a = Automation("x", "Abends", existing)
        val c = controller(FakeStorage(listOf(recordingRemote(listOf(a)))))
        c.show(Screen.AutomationEdit("x"))
        c.recordInto("x", "Abends neu", existing)
        assertEquals(Screen.Remote, c.state.value.screen)
        assertEquals(1, c.state.value.recording?.existingCount)
        c.press(Keys.OK)
        c.finishRecording()
        assertEquals(
            Screen.AutomationEdit("x", AutomationDraft("Abends neu", existing + AutomationStep(Keys.OK))),
            c.state.value.screen,
        )
    }

    @Test fun cancelReturnsToOrigin() = runTest {
        val c = controller(FakeStorage(listOf(recordingRemote())))
        c.show(Screen.Automations)
        c.recordNew(returnTo = Screen.Automations)
        c.press(Keys.MENU)
        c.cancelRecording()
        assertEquals(Screen.Automations, c.state.value.screen)
        assertNull(c.state.value.recording)
        assertEquals(emptyList<AutomationStep>(), c.recordedSteps.value)

        val existing = listOf(AutomationStep(Keys.POWER))
        c.recordInto(null, "Neu", existing)
        c.press(Keys.OK)
        c.cancelRecording()
        assertEquals(Screen.AutomationEdit(null, AutomationDraft("Neu", existing)), c.state.value.screen)
    }

    @Test fun startingRecordingCancelsRunningAutomationAndBlocksNewRuns() = runTest {
        val c = controller(FakeStorage(listOf(recordingRemote())))
        c.runAutomation("A", listOf(AutomationStep(Keys.POWER, 1000), AutomationStep(Keys.POWER)))
        runCurrent()
        assertEquals("A", c.automationProgress.value?.name)
        c.recordNew(returnTo = Screen.Remote)
        runCurrent()
        assertNull(c.automationProgress.value)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(1, sent.size)
        c.runAutomation("B", listOf(AutomationStep(Keys.POWER)))
        runCurrent()
        assertNull(c.automationProgress.value)
        assertEquals(1, sent.size)
    }
}
