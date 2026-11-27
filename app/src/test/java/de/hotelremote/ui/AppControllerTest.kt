package de.hotelremote.ui

import de.hotelremote.Keys
import de.hotelremote.automation.SleepTemplates
import de.hotelremote.data.AcAutoSettings
import de.hotelremote.data.AcBrand
import de.hotelremote.data.AcCatalog
import de.hotelremote.data.AcDevice
import de.hotelremote.data.AcState
import de.hotelremote.data.acBlob
import de.hotelremote.data.Automation
import de.hotelremote.data.PLAIN_PATHS
import de.hotelremote.data.pulsesOf
import de.hotelremote.data.testAc
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.Brand
import de.hotelremote.data.CodeCatalog
import de.hotelremote.data.RemoteStorage
import de.hotelremote.data.RoomPage
import de.hotelremote.data.SavedAc
import de.hotelremote.data.SavedRemote
import de.hotelremote.data.ScanEntry
import de.hotelremote.ir.IrSignal
import de.hotelremote.wizard.AcWizardState
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
import org.junit.Assert.assertNotNull
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
    /** Anzahl gespeicherter Klima-Zustände. */
    var writes = 0

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
    override fun setTv(id: String, brand: String, codeLabel: String, keys: Map<String, IrSignal>) = write {
        list.replaceAll { if (it.id == id) it.copy(brand = brand, codeLabel = codeLabel, keys = keys) else it }
    }
    override fun setAc(id: String, ac: SavedAc?) = write {
        list.replaceAll { if (it.id == id) it.copy(ac = ac) else it }
    }
    override fun updateAcState(id: String, state: AcState) = write {
        writes++
        list.replaceAll { if (it.id == id) it.copy(ac = it.ac?.copy(state = state)) else it }
    }
    override fun setLastPage(id: String, page: RoomPage) = write {
        list.replaceAll { if (it.id == id) it.copy(lastPage = page) else it }
    }
    override fun setAcAuto(id: String, settings: AcAutoSettings) = write {
        list.replaceAll { if (it.id == id) it.copy(ac = it.ac?.copy(auto = settings)) else it }
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

/** Eine Klima-Marke mit zwei Geräten (beide [PLAIN_PATHS]). */
private class FakeAcCatalog : AcCatalog {
    override fun brands() = listOf(AcBrand(1, "Daikin", 2, commonRank = 0))
    override fun deviceIds(brandId: Long?) = listOf(31L, 32L)
    override fun device(id: Long) = AcDevice(
        id = id, brandId = 1, brandName = "Daikin", models = listOf("FTX$id"), source = "$id.json",
        minTemp = 16.0, maxTemp = 30.0, precision = 1.0, modes = listOf("cool", "heat"), fanModes = listOf("auto"),
        swingModes = emptyList(), commands = acBlob(PLAIN_PATHS),
    )
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
        acCatalog: () -> AcCatalog = { FakeAcCatalog() },
        stopAcAuto: (String) -> Boolean = { false },
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
            openAcCatalog = acCatalog,
            stopAcAuto = stopAcAuto,
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
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
        assertEquals(WizardState.PickBrand, c.state.value.wizard)
        c.chooseDevice(DeviceKind.TV)
        assertEquals(Screen.Wizard, c.state.value.screen)
    }

    @Test fun dbErrorKeepsSavedRemotesUsable() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))), catalog = { throw IllegalStateException("kein Platz") })
        val s = c.state.value
        assertFalse(s.loading)
        assertTrue(s.dbError)
        assertEquals("a", s.current?.id)
        assertEquals(Screen.Remote, s.screen)
        c.openWizard()
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
        c.chooseDevice(DeviceKind.TV)
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertTrue(c.wizardBack())
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
        assertTrue(c.wizardBack())
        assertEquals(Screen.Remote, c.state.value.screen)
    }

    @Test fun dbErrorWithoutRemotesShowsWizardErrorAndBackExits() = runTest {
        val c = controller(FakeStorage(), catalog = { throw RuntimeException("defekt") })
        assertTrue(c.state.value.dbError)
        c.chooseDevice(DeviceKind.TV)
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertTrue(c.wizardBack())
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
        assertFalse(c.wizardBack())
    }

    @Test fun storeLoadFailureShowsMessageAndOpensWizard() = runTest {
        val c = controller(FakeStorage().apply { failLoad = true })
        assertEquals(UiMessage.LOAD_FAILED, c.state.value.message)
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
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
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
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
        c.chooseDevice(DeviceKind.TV)
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

    @Test fun templateOpensEditorWithItsStepsAndSavesANormalAutomation() = runTest {
        val remote = recordingRemote().copy(brand = "Vestel", keys = recordingRemote().keys + ("X:Sleep" to sig(9)))
        val c = controller(FakeStorage(listOf(remote)))
        val template = SleepTemplates.forRemote(remote.brand, remote.keys.keys).matching.first { it.minutes == 60 }
        c.openTemplate(template, "Sleep-Timer 60 min")
        val screen = c.state.value.screen as Screen.AutomationEdit
        assertNull(screen.automationId)
        assertEquals(template, screen.template)
        assertEquals(AutomationDraft("Sleep-Timer 60 min", template.steps), screen.draft)
        assertEquals(List(6) { "X:Sleep" }, screen.draft!!.steps.map { it.key })
        assertTrue(c.saveAutomation(Automation("a1", "Sleep-Timer 60 min", screen.draft!!.steps)))
        assertEquals(listOf("Sleep-Timer 60 min"), c.state.value.current?.automations?.map { it.name })
    }

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
    // ---- Klimaanlage ----

    private fun acRoom(id: String = "k", lastUsed: Long = 5, tv: Boolean = true) = SavedRemote(
        id = id,
        brand = if (tv) "Samsung" else "",
        codeLabel = if (tv) "Code 1" else "",
        keys = if (tv) mapOf(Keys.POWER to sig(1)) else emptyMap(),
        lastUsed = lastUsed,
        ac = testAc(),
    )

    @Test fun turningAcOffStopsRunningAutomationWithHint() = runTest {
        val stopped = mutableListOf<String>()
        var running = true
        val c = controller(FakeStorage(listOf(acRoom())), stopAcAuto = { stopped += it; running.also { running = false } })
        // testAc ist eingeschaltet: Ein/Aus schaltet aus.
        c.ac(AcAction.Power)
        assertEquals(listOf("k"), stopped)
        assertEquals(UiMessage.AC_AUTO_STOPPED, c.state.value.message)
        c.clearMessage()
        // Einschalten und andere Tasten stoppen nichts; Ausschalten ohne laufende Automatik ohne Hinweis.
        c.ac(AcAction.Power)
        c.ac(AcAction.Warmer)
        assertEquals(1, stopped.size)
        c.ac(AcAction.Power)
        assertEquals(2, stopped.size)
        assertNull(c.state.value.message)
    }

    @Test fun removingAcOrRoomStopsAutomation() = runTest {
        val stopped = mutableListOf<String>()
        val c = controller(FakeStorage(listOf(acRoom("k"), acRoom("m", 1))), stopAcAuto = { stopped += it; true })
        c.removeDevice("k", DeviceKind.TV)
        assertTrue(stopped.isEmpty())
        c.removeDevice("k", DeviceKind.AC)
        assertEquals(listOf("k"), stopped)
        c.delete("m")
        assertEquals(listOf("k", "m"), stopped)
    }

    @Test fun replacingAcStopsItsAutomation() = runTest {
        val stopped = mutableListOf<String>()
        val c = controller(FakeStorage(listOf(acRoom("k"))), stopAcAuto = { stopped += it; true })
        c.addDevice("k", DeviceKind.AC)
        assertTrue(stopped.isEmpty())
        c.findAc()
        assertEquals(listOf("k"), stopped)
    }

    @Test fun acAutoSettingsAreStoredPerRoom() = runTest {
        val store = FakeStorage(listOf(acRoom("k"), remote("a", 1)))
        val c = controller(store)
        val s = AcAutoSettings(6.0, 24.0, 27.0, "low")
        assertTrue(c.setAcAuto(s))
        assertEquals(s, store.list.first { it.id == "k" }.ac?.auto)
        assertEquals(s, c.state.value.current?.ac?.auto)
        assertEquals(listOf("auto", "low"), c.acAutoFans(c.state.value.current!!))
        store.failWrite = true
        assertFalse(c.setAcAuto(s.copy(offset = 7.0)))
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
        c.switchTo("a")
        assertFalse(c.setAcAuto(s))
    }

    @Test fun stateSentByAutomationIsShownWithoutStoring() = runTest {
        val store = FakeStorage(listOf(acRoom("k")))
        val c = controller(store)
        val sentState = AcState(true, "cool", 23.0, "auto")
        c.acStateSent("k", sentState)
        assertEquals(sentState, c.state.value.current?.ac?.state)
        assertEquals(0, store.writes)
    }

    @Test fun acActionUpdatesStateStoresAndSendsAfterDebounce() = runTest {
        val store = FakeStorage(listOf(acRoom(), remote("a", 1)))
        val c = controller(store)
        c.ac(AcAction.Warmer)
        val expected = AcState(true, "cool", 23.0, "auto")
        assertEquals(expected, c.state.value.current?.ac?.state)
        assertEquals(expected, c.state.value.remotes.first { it.id == "k" }.ac?.state)
        assertEquals(expected, store.list.first { it.id == "k" }.ac?.state)
        assertEquals(listOf("k", "a"), c.state.value.remotes.map { it.id })
        assertTrue(sent.isEmpty())
        advanceTimeBy(AcController.DEBOUNCE_MS + 1)
        assertEquals(listOf(pulsesOf(PLAIN_PATHS, "cool/auto/23")), sent.map { it.frame })
        assertEquals(23.0, c.acView(c.state.value.current!!)!!.state.temp, 0.0)
    }

    @Test fun acSaveFailureKeepsStateForSessionAndShowsMessage() = runTest {
        val store = FakeStorage(listOf(acRoom()))
        val c = controller(store)
        store.failWrite = true
        c.ac(AcAction.Power)
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
        assertFalse(c.state.value.current!!.ac!!.state.power)
        assertEquals(listOf(pulsesOf(PLAIN_PATHS, "off")), sent.map { it.frame })
    }

    @Test fun acKeysAreNotRecordable() = runTest {
        val c = controller(FakeStorage(listOf(acRoom())))
        c.recordNew(returnTo = Screen.Remote)
        c.ac(AcAction.Power)
        advanceTimeBy(1000)
        assertTrue(sent.isEmpty())
        assertTrue(c.state.value.current!!.ac!!.state.power)
        assertEquals(emptyList<AutomationStep>(), c.recordedSteps.value)
    }

    @Test fun acWithoutAcInRoomDoesNothing() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.ac(AcAction.Power)
        advanceTimeBy(1000)
        assertTrue(sent.isEmpty())
        assertNull(c.acView(c.state.value.current!!))
    }

    @Test fun roomPageIsStoredPerRoomAndSurvivesRestart() = runTest {
        val store = FakeStorage(listOf(acRoom("k"), acRoom("m", lastUsed = 1)))
        val c = controller(store)
        assertNull(c.state.value.current?.lastPage)
        c.showRoomPage("k", RoomPage.AC)
        assertEquals(RoomPage.AC, c.state.value.current?.lastPage)
        c.switchTo("m")
        assertNull(c.state.value.current?.lastPage)
        c.switchTo("k")
        assertEquals(RoomPage.AC, c.state.value.current?.lastPage)

        // Neustart: neuer Controller auf derselben Ablage.
        val restarted = controller(store)
        assertEquals("k", restarted.state.value.current?.id)
        assertEquals(RoomPage.AC, restarted.state.value.current?.lastPage)
        assertNull(restarted.state.value.remotes.first { it.id == "m" }.lastPage)
    }

    @Test fun holdingPlusSendsAndStoresOnlyAfterRelease() = runTest {
        val store = FakeStorage(listOf(acRoom()))
        val c = controller(store)
        // + etwa 1200 ms gehalten: Schritte bei 0, 400, 800, 1200 ms (22 -> 24, dann Grenze).
        repeat(4) {
            c.acHoldStep(AcAction.Warmer)
            advanceTimeBy(400)
        }
        assertEquals(24.0, c.state.value.current!!.ac!!.state.temp, 0.0)
        assertTrue("während des Haltens nichts gesendet", sent.isEmpty())
        assertEquals("und nicht gespeichert", 0, store.writes)
        c.acHoldEnd()
        assertEquals(1, store.writes)
        assertEquals(24.0, store.list.single().ac!!.state.temp, 0.0)
        advanceTimeBy(AcController.DEBOUNCE_MS - 1)
        assertTrue(sent.isEmpty())
        advanceTimeBy(2)
        assertEquals("genau ein Signal mit der Endtemperatur", listOf(pulsesOf(PLAIN_PATHS, "cool/auto/24")), sent.map { it.frame })
        advanceTimeBy(1000)
        assertEquals(1, sent.size)
    }

    @Test fun recordingIsNotOfferedInPureAcRoom() = runTest {
        val c = controller(FakeStorage(listOf(acRoom(tv = false))))
        c.recordNew(returnTo = Screen.Remote)
        assertNull(c.state.value.recording)
        c.recordInto(null, "x", emptyList())
        assertNull(c.state.value.recording)
    }

    @Test fun pureAcRoomWithoutKeysDoesNotBreakTvFunctions() = runTest {
        val c = controller(FakeStorage(listOf(acRoom(tv = false))))
        val s = c.state.value
        assertEquals(Screen.Remote, s.screen)
        assertEquals("Acme · AC-1", s.current?.title)
        c.press(Keys.POWER)
        assertNull(c.hold(Keys.VOL_UP))
        c.runAutomation("A", listOf(AutomationStep(Keys.POWER)))
        advanceTimeBy(5000)
        assertTrue(sent.isEmpty())
        c.ac(AcAction.Power)
        assertEquals(listOf(pulsesOf(PLAIN_PATHS, "off")), sent.map { it.frame })
    }

    // ---- Klima-Assistent und Geräte eines Raums ----

    private fun tvAcRoom(id: String, lastUsed: Long = 1) = remote(id, lastUsed).copy(ac = testAc())

    private fun pureAcRoom(id: String) = SavedRemote(id = id, lastUsed = 1, ac = testAc())

    /** Erstes Gerät bestätigen (Test-Zustand und „Aus“), dann speichern. */
    private fun AppController.findAc(name: String? = null) {
        acPickBrand(1)
        acAnswer(true)
        acAnswer(true)
        assertTrue(state.value.acWizard is AcWizardState.Save)
        saveAcWizard(name)
    }

    @Test fun newRoomWithAirConditioner() = runTest {
        val store = FakeStorage()
        val c = controller(store)
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
        assertFalse(c.state.value.acDbError)
        assertEquals(listOf("Daikin"), c.state.value.acBrands.map { it.name })
        c.chooseDevice(DeviceKind.AC)
        assertEquals(Screen.AcWizard, c.state.value.screen)
        assertEquals(AcWizardState.PickBrand, c.state.value.acWizard)
        c.acPickBrand(1)
        val test = c.state.value.acWizard as AcWizardState.TestCode
        assertEquals(0, test.index)
        assertEquals(2, test.total)
        c.acTestSignal()
        c.acTestSignal() // Nochmal senden
        assertEquals(List(2) { pulsesOf(PLAIN_PATHS, "cool/auto/24") }, sent.map { it.frame })
        c.acAnswer(true)
        assertTrue(c.state.value.acWizard is AcWizardState.ConfirmOff)
        c.acTestSignal()
        assertEquals(pulsesOf(PLAIN_PATHS, "off"), sent.last().frame)
        c.acAnswer(true)
        c.saveAcWizard("Zimmer 5")
        val room = store.list.single()
        assertEquals("Zimmer 5", room.name)
        assertFalse(room.hasTv)
        assertEquals("Daikin", room.ac?.brand)
        assertEquals("FTX31", room.ac?.deviceLabel)
        assertEquals(AcState(power = false, mode = "cool", temp = 24.0, fan = "auto"), room.ac?.state)
        assertEquals(room, c.state.value.current)
        assertEquals(Screen.Remote, c.state.value.screen)
        assertNotNull(c.acView(room))
    }

    @Test fun acWizardNoGoesToNextDeviceAndBackGoesOneStep() = runTest {
        val c = controller(FakeStorage())
        c.chooseDevice(DeviceKind.AC)
        c.acPickBrand(1)
        c.acAnswer(false)
        assertEquals(32L, (c.state.value.acWizard as AcWizardState.TestCode).deviceId)
        c.acAnswer(false)
        assertEquals(AcWizardState.NoMatch(1), c.state.value.acWizard)
        assertTrue(c.wizardBack())
        assertEquals(AcWizardState.PickBrand, c.state.value.acWizard)
        c.acPickBrand(1)
        c.acAnswer(false)
        assertTrue(c.wizardBack())
        assertEquals(31L, (c.state.value.acWizard as AcWizardState.TestCode).deviceId)
        assertTrue(c.wizardBack())
        assertEquals(AcWizardState.PickBrand, c.state.value.acWizard)
        assertTrue(c.wizardBack())
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
        assertFalse(c.wizardBack()) // kein Raum: App verlassen
    }

    @Test fun acNoAfterFirstSendSendsNextDeviceAtOnce() = runTest {
        val c = controller(FakeStorage())
        c.chooseDevice(DeviceKind.AC)
        c.acPickBrand(1)
        // Noch nie gesendet: Nein wechselt nur das Gerät.
        c.acAnswer(false)
        assertEquals(32L, (c.state.value.acWizard as AcWizardState.TestCode).deviceId)
        assertTrue(sent.isEmpty())
        assertFalse(c.state.value.acStepSent)
        c.wizardBack()
        c.acTestSignal()
        assertEquals(1, sent.size)
        // Ab jetzt sendet Nein das nächste Gerät sofort (ein Tipp je Gerät).
        c.acAnswer(false)
        assertEquals(32L, (c.state.value.acWizard as AcWizardState.TestCode).deviceId)
        assertEquals(2, sent.size)
        assertEquals(pulsesOf(PLAIN_PATHS, "cool/auto/24"), sent.last().frame)
        assertTrue(c.state.value.acStepSent)
        // Ja führt zur Aus-Bestätigung: dort wird nichts automatisch gesendet.
        c.acAnswer(true)
        assertTrue(c.state.value.acWizard is AcWizardState.ConfirmOff)
        assertEquals(2, sent.size)
        assertFalse(c.state.value.acStepSent)
        // Am Ende der Liste (NoMatch) gibt es nichts zu senden.
        c.acAnswer(false)
        assertEquals(AcWizardState.NoMatch(1), c.state.value.acWizard)
        assertEquals(2, sent.size)
    }

    @Test fun freshWizardDoesNotAutoSend() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.addDevice("a", DeviceKind.AC)
        c.acPickBrand(1)
        c.acTestSignal()
        c.wizardBack()
        c.wizardBack()
        c.addDevice("a", DeviceKind.AC)
        c.acPickBrand(1)
        c.acAnswer(false)
        assertEquals(1, sent.size)
    }

    @Test fun closingWizardForgetsTargetRoom() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.addDevice("a", DeviceKind.AC)
        assertEquals("a", c.state.value.wizardRoom)
        assertTrue(c.wizardBack())
        assertEquals(Screen.Remote, c.state.value.screen)
        assertNull(c.state.value.wizardRoom)
    }

    @Test fun tvWizardBackFromBrandPickReturnsToDeviceChoice() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.openWizard()
        c.chooseDevice(DeviceKind.TV)
        assertTrue(c.wizardBack())
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
        c.chooseDevice(DeviceKind.AC)
        assertEquals(Screen.AcWizard, c.state.value.screen)
        assertTrue(c.wizardBack())
        assertTrue(c.wizardBack())
        assertEquals(Screen.Remote, c.state.value.screen)
    }

    @Test fun chooseDeviceOnlyOnDeviceChoice() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.chooseDevice(DeviceKind.AC)
        assertEquals(Screen.Remote, c.state.value.screen)
    }

    @Test fun addAcToExistingRoomKeepsTvAndShowsAcPage() = runTest {
        val store = FakeStorage(listOf(remote("a", 1), remote("b", 2)))
        val c = controller(store)
        c.addDevice("a", DeviceKind.AC)
        assertEquals(Screen.AcWizard, c.state.value.screen)
        assertEquals("a", c.state.value.wizardRoom)
        c.findAc(name = "ignoriert")
        val a = store.list.first { it.id == "a" }
        assertEquals("Daikin", a.ac?.brand)
        assertEquals(remote("a", 1).keys, a.keys)
        assertNull(a.name)
        assertEquals(RoomPage.AC, a.lastPage)
        assertEquals(1000L, a.lastUsed)
        assertEquals(2, store.list.size)
        assertEquals(a, c.state.value.current)
        assertEquals(listOf("a", "b"), c.state.value.remotes.map { it.id })
        assertEquals(Screen.Remote, c.state.value.screen)
        assertNull(c.state.value.wizardRoom)
    }

    @Test fun addingToRoomBackFromBrandPickClosesWizard() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.addDevice("a", DeviceKind.AC)
        assertTrue(c.wizardBack())
        assertEquals(Screen.Remote, c.state.value.screen)
        c.addDevice("a", DeviceKind.TV)
        assertEquals(Screen.Wizard, c.state.value.screen)
        assertTrue(c.wizardBack())
        assertEquals(Screen.Remote, c.state.value.screen)
    }

    @Test fun replacingAcHappensOnlyOnSave() = runTest {
        val store = FakeStorage(listOf(tvAcRoom("a")))
        val c = controller(store)
        c.addDevice("a", DeviceKind.AC)
        c.acPickBrand(1)
        c.wizardBack()
        c.wizardBack()
        assertEquals("Acme", store.list.single().ac?.brand)
        c.addDevice("a", DeviceKind.AC)
        c.findAc()
        assertEquals("Daikin", store.list.single().ac?.brand)
        assertEquals("Daikin", c.state.value.current?.ac?.brand)
    }

    @Test fun addTvToPureAcRoomSavesIntoSameRoom() = runTest {
        val store = FakeStorage(listOf(pureAcRoom("k")))
        val c = controller(store)
        c.addDevice("k", DeviceKind.TV)
        c.pickBrand(1)
        c.answer(true)
        c.saveWizard("ignoriert", "Samsung")
        val k = store.list.single()
        assertTrue(k.hasTv)
        assertEquals("Samsung", k.brand)
        assertEquals("Code 1", k.codeLabel)
        assertNull(k.name)
        assertEquals("Acme", k.ac?.brand)
        assertEquals(RoomPage.TV, k.lastPage)
        assertEquals(k, c.state.value.current)
        assertEquals(Screen.Remote, c.state.value.screen)
    }

    @Test fun saveAcFailureKeepsRoomForSession() = runTest {
        val store = FakeStorage().apply { failWrite = true }
        val c = controller(store)
        c.chooseDevice(DeviceKind.AC)
        c.findAc()
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
        assertEquals("Daikin", c.state.value.current?.ac?.brand)
        assertEquals(listOf(c.state.value.current), c.state.value.remotes)
        assertEquals(Screen.Remote, c.state.value.screen)
    }

    @Test fun saveIntoRoomFailureStillShowsDeviceForSession() = runTest {
        val store = FakeStorage(listOf(remote("a", 1)))
        val c = controller(store)
        c.addDevice("a", DeviceKind.AC)
        store.failWrite = true
        c.findAc()
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
        assertEquals("Daikin", c.state.value.current?.ac?.brand)
        assertEquals(RoomPage.AC, c.state.value.current?.lastPage)
        assertEquals(1, c.state.value.remotes.size)
    }

    @Test fun removeOneDeviceKeepsRoom() = runTest {
        val store = FakeStorage(listOf(tvAcRoom("a"), tvAcRoom("b", lastUsed = 2)))
        val c = controller(store)
        c.removeDevice("a", DeviceKind.AC)
        assertNull(store.list.first { it.id == "a" }.ac)
        assertTrue(store.list.first { it.id == "a" }.hasTv)
        c.removeDevice("b", DeviceKind.TV)
        val b = store.list.first { it.id == "b" }
        assertFalse(b.hasTv)
        assertEquals("", b.brand)
        assertEquals("Acme", b.ac?.brand)
        assertEquals(b, c.state.value.current)
        assertEquals("Acme · AC-1", c.state.value.current?.title)
    }

    @Test fun removingLastDeviceDeletesRoom() = runTest {
        val store = FakeStorage(listOf(pureAcRoom("k"), remote("t", 2)))
        val c = controller(store)
        c.removeDevice("k", DeviceKind.AC)
        assertEquals(listOf("t"), store.list.map { it.id })
        c.removeDevice("t", DeviceKind.TV)
        assertTrue(store.list.isEmpty())
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
        assertNull(c.state.value.current)
    }

    @Test fun removeFailureShowsMessage() = runTest {
        val store = FakeStorage(listOf(tvAcRoom("a")))
        val c = controller(store)
        store.failWrite = true
        c.removeDevice("a", DeviceKind.AC)
        assertEquals(UiMessage.SAVE_FAILED, c.state.value.message)
        assertNotNull(c.state.value.current?.ac)
    }

    @Test fun addDeviceIgnoredForUnknownRoomAndWhileRecording() = runTest {
        val c = controller(FakeStorage(listOf(remote("a", 1))))
        c.addDevice("x", DeviceKind.AC)
        assertEquals(Screen.Remote, c.state.value.screen)
        c.recordNew(returnTo = Screen.Remote)
        c.addDevice("a", DeviceKind.AC)
        assertEquals(Screen.Remote, c.state.value.screen)
        assertNull(c.state.value.wizardRoom)
    }

    @Test fun acDbErrorShowsHintOnlyInAcWizard() = runTest {
        val c = controller(FakeStorage(), acCatalog = { throw IllegalStateException("defekt") })
        assertTrue(c.state.value.acDbError)
        assertFalse(c.state.value.dbError)
        c.chooseDevice(DeviceKind.AC)
        assertEquals(Screen.AcWizard, c.state.value.screen)
        c.acPickBrand(1)
        c.acTestSignal()
        assertTrue(sent.isEmpty())
        assertTrue(c.wizardBack())
        assertEquals(Screen.DeviceChoice, c.state.value.screen)
    }
}
