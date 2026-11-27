package de.hotelremote.ui

import de.hotelremote.automation.AutomationProgress
import de.hotelremote.automation.AutomationRecorder
import de.hotelremote.automation.AutomationResult
import de.hotelremote.automation.AutomationRunner
import de.hotelremote.automation.AutomationTemplate
import de.hotelremote.data.AcState
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.data.Brand
import de.hotelremote.data.CodeCatalog
import de.hotelremote.data.RemoteStorage
import de.hotelremote.data.RoomPage
import de.hotelremote.data.SavedAc
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.wizard.ScanRunner
import de.hotelremote.wizard.WizardMachine
import de.hotelremote.wizard.WizardState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

sealed interface Screen {
    data object Remote : Screen
    data object RemoteList : Screen
    data object Wizard : Screen
    data object ExtraKeys : Screen
    data object About : Screen
    data object Language : Screen
    data object Automations : Screen
    data object QuickControls : Screen

    /**
     * Editor; [automationId] null = neue Automation; [draft] ersetzt Name und Schritte (z. B. nach einer Aufnahme).
     * [template]: Vorlage, aus der der Entwurf stammt (der Editor zeigt deren Hinweis).
     */
    data class AutomationEdit(
        val automationId: String?,
        val draft: AutomationDraft? = null,
        val template: AutomationTemplate? = null,
    ) : Screen
}

/** Ungespeicherter Stand im Editor. */
data class AutomationDraft(val name: String, val steps: List<AutomationStep>)

/**
 * Laufende Aufnahme. „Fertig“ öffnet den Editor für [automationId] mit [name], „Abbrechen“ kehrt zu
 * [returnTo] zurück. Die ersten [existingCount] Schritte waren schon vorhanden; [hint] bis zum ersten Tastendruck.
 */
data class Recording(
    val automationId: String?,
    val name: String,
    val existingCount: Int,
    val returnTo: Screen,
    val hint: Boolean = true,
)

/** Snackbar-Meldungen; App.kt setzt sie in Texte der aktuellen Sprache um. */
enum class UiMessage { SAVE_FAILED, LOAD_FAILED, SEND_FAILED, KEYS_MISSING, AUTOMATION_BUSY, TIMER_NO_NOTIFICATIONS }

data class AppUiState(
    val loading: Boolean = true,
    val screen: Screen = Screen.Remote,
    val current: SavedRemote? = null,
    val remotes: List<SavedRemote> = emptyList(),
    val wizard: WizardState = WizardState.PickBrand,
    val irAvailable: Boolean = true,
    /** Code-Datenbank nicht ladbar: gespeicherte Fernbedienungen gehen, Assistent und Über zeigen einen Hinweis. */
    val dbError: Boolean = false,
    val brands: List<Brand> = emptyList(),
    val meta: Map<String, String> = emptyMap(),
    val message: UiMessage? = null,
    /** Zu [UiMessage.KEYS_MISSING]: die übersprungenen Tasten. */
    val missingKeys: List<String> = emptyList(),
    /** Erststart: „Sprache wählen“ kommt vor allem anderen und hat kein Zurück. */
    val languageFirstStart: Boolean = false,
    /** Aufnahme einer Automation läuft (null = keine). */
    val recording: Recording? = null,
)

/**
 * Navigation und Zustand der App ohne Android-Abhängigkeiten: lädt gespeicherte Fernbedienungen und
 * Code-Datenbank unabhängig voneinander, fängt Speicherfehler ab und steuert Assistent und Suchlauf.
 */
class AppController(
    private val store: RemoteStorage,
    private val openCatalog: () -> CodeCatalog,
    private val send: (IrSignal) -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    irAvailable: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    languageChosen: Boolean = true,
    /** Gedrückt halten (wiederholt bis zum Abbrechen des Jobs); Standard: einmal senden. */
    private val hold: (IrSignal) -> Job? = { send(it); null },
    /** Monotone Uhr (ms) für die Pausen der Aufnahme; unabhängig von Änderungen der Uhrzeit. */
    recorderClock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val _state = MutableStateFlow(
        AppUiState(
            irAvailable = irAvailable,
            screen = if (languageChosen) Screen.Remote else Screen.Language,
            languageFirstStart = !languageChosen,
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var catalog: CodeCatalog? = null
    private var machine: WizardMachine? = null
    private var scanRunner: ScanRunner? = null

    private val automationRunner = AutomationRunner(scope, send, ::automationFinished)

    /** Laufende Automation (null = keine). */
    val automationProgress: StateFlow<AutomationProgress?> = automationRunner.progress

    private val recorder = AutomationRecorder(recorderClock)

    /** Schritte der laufenden Aufnahme (vorhandene und aufgenommene). */
    val recordedSteps: StateFlow<List<AutomationStep>> = recorder.steps

    private val acController = AcController(scope, send, ::acChanged)

    /** Bildschirm, zu dem „Sprache wählen“ zurückkehrt. */
    private var languageReturn: Screen = Screen.Remote

    init {
        scope.launch {
            val remotes = withContext(io) {
                try {
                    store.all()
                } catch (e: IOException) {
                    null
                }
            }
            val loaded = withContext(io) {
                try {
                    val c = openCatalog()
                    Triple(c, c.brands(), c.meta())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            catalog = loaded?.first
            _state.update {
                it.copy(
                    loading = false,
                    remotes = remotes.orEmpty(),
                    brands = loaded?.second.orEmpty(),
                    meta = loaded?.third.orEmpty(),
                    dbError = loaded == null,
                    message = if (remotes == null) UiMessage.LOAD_FAILED else it.message,
                )
            }
            val last = remotes?.firstOrNull()
            if (last != null) {
                _state.update { it.copy(current = last, screen = Screen.Remote) }
            } else {
                openWizard()
            }
            // Erststart noch ohne Sprachwahl: der Startbildschirm wartet hinter „Sprache wählen“.
            if (_state.value.languageFirstStart) {
                languageReturn = _state.value.screen
                _state.update { it.copy(screen = Screen.Language) }
            }
        }
    }

    // ---- Tasten ----

    /** Sendet die Taste der aktuellen Fernbedienung einmal; während einer Aufnahme wird sie auch Schritt. */
    fun press(key: String) {
        val signal = _state.value.current?.keys?.get(key) ?: return
        send(signal)
        recordKey(key)
    }

    /** Wie [press], aber wiederholt bis zum Abbrechen des Jobs; aufgenommen wird ein Schritt pro Druck. */
    fun hold(key: String): Job? {
        val signal = _state.value.current?.keys?.get(key) ?: return null
        recordKey(key)
        return hold(signal)
    }

    private fun recordKey(key: String) {
        if (_state.value.recording == null) return
        recorder.record(key)
        _state.update { s -> s.copy(recording = s.recording?.copy(hint = false)) }
    }

    // ---- Klimaanlage ----

    /** Bedienschritt an der Klimaanlage des aktuellen Raums; während einer Aufnahme gesperrt (nicht aufnehmbar). */
    fun ac(action: AcAction) = withAc { id, ac -> acController.perform(id, ac, action) }

    /** Schritt einer gehaltenen −/+-Taste: nur anzeigen; gesendet und gespeichert wird nach [acHoldEnd]. */
    fun acHoldStep(action: AcAction) = withAc { id, ac -> acController.perform(id, ac, action, held = true) }

    /** −/+ losgelassen: speichern und entprellt senden. */
    fun acHoldEnd() = withAc { id, ac -> acController.release(id, ac) }

    private inline fun withAc(action: (String, SavedAc) -> Unit) {
        if (_state.value.recording != null) return
        val current = _state.value.current ?: return
        action(current.id, current.ac ?: return)
    }

    /** Anzeige für die Klimaanlage von [remote]; null ohne (lesbare) Klimaanlage. */
    fun acView(remote: SavedRemote): AcView? = remote.ac?.let(acController::view)

    /** Zeigt den Zustand und merkt ihn ([store]); scheitert das Speichern, gilt er trotzdem für diese Sitzung. */
    private fun acChanged(roomId: String, ac: AcState, store: Boolean) {
        if (store) storing { this.store.updateAcState(roomId, ac) }
        updateRoom(roomId) { r -> r.ac?.let { r.copy(ac = it.copy(state = ac)) } ?: r }
    }

    /** Merkt die gezeigte Wischseite des Raums dauerhaft (auch nach Neustart). */
    fun showRoomPage(roomId: String, page: RoomPage) {
        if ((_state.value.remotes.firstOrNull { it.id == roomId }?.lastPage ?: RoomPage.TV) == page) return
        storing { store.setLastPage(roomId, page) }
        updateRoom(roomId) { it.copy(lastPage = page) }
    }

    /** Ändert den Raum in [AppUiState.current] und [AppUiState.remotes], ohne die Reihenfolge zu ändern. */
    private fun updateRoom(roomId: String, change: (SavedRemote) -> SavedRemote) {
        fun SavedRemote.changed() = if (id == roomId) change(this) else this
        _state.update { s -> s.copy(current = s.current?.changed(), remotes = s.remotes.map { it.changed() }) }
    }

    // ---- Fernbedienungen ----

    fun show(screen: Screen) {
        _state.update { it.copy(screen = screen) }
    }

    fun showMessage(message: UiMessage) {
        _state.update { it.copy(message = message) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Wechselt auch dann, wenn das Merken der Nutzung scheitert. */
    fun switchTo(id: String) {
        if (_state.value.recording != null) return
        val remotes = storing {
            store.markUsed(id, clock())
            store.all()
        } ?: _state.value.remotes
        val target = remotes.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(current = target, remotes = remotes, screen = Screen.Remote) }
    }

    fun rename(id: String, name: String?) {
        val remotes = storing {
            store.rename(id, name?.trim()?.ifBlank { null })
            store.all()
        } ?: return
        _state.update { s -> s.copy(remotes = remotes, current = s.current?.let { c -> remotes.firstOrNull { it.id == c.id } }) }
    }

    fun delete(id: String) {
        val remotes = storing {
            store.delete(id)
            store.all()
        } ?: return
        val wasCurrent = _state.value.current?.id == id
        _state.update { it.copy(remotes = remotes) }
        if (wasCurrent) {
            val next = remotes.firstOrNull()
            if (next != null) _state.update { it.copy(current = next) } else openWizard()
        }
    }

    /** Führt eine Speicheraktion aus; bei IOException Snackbar-Hinweis und null. */
    private fun <T> storing(action: () -> T): T? =
        try {
            action()
        } catch (e: IOException) {
            showMessage(UiMessage.SAVE_FAILED)
            null
        }

    // ---- Automationen ----

    /** Führt eine (auch ungespeicherte) Folge mit den Tasten der aktuellen Fernbedienung aus. */
    fun runAutomation(name: String, steps: List<AutomationStep>) {
        if (_state.value.recording != null) return
        val keys = _state.value.current?.keys ?: return
        if (!automationRunner.start(name, keys, steps) && automationProgress.value != null) {
            showMessage(UiMessage.AUTOMATION_BUSY)
        }
    }

    fun cancelAutomation() = automationRunner.cancel()

    private fun automationFinished(result: AutomationResult) {
        if (result.missing.isNotEmpty()) {
            _state.update { it.copy(message = UiMessage.KEYS_MISSING, missingKeys = result.missing) }
        }
    }

    /** Neu oder geändert (gleiche id ersetzt); true, wenn gespeichert. */
    fun saveAutomation(automation: Automation): Boolean {
        val current = _state.value.current ?: return false
        val list = current.automations.toMutableList()
        val index = list.indexOfFirst { it.id == automation.id }
        if (index >= 0) list[index] = automation else list += automation
        return writeAutomations(current.id, list)
    }

    fun deleteAutomation(id: String) {
        val current = _state.value.current ?: return
        writeAutomations(current.id, current.automations.filterNot { it.id == id })
    }

    /** Öffnet den Editor mit den Schritten der Vorlage als neue Automation namens [name]. */
    fun openTemplate(template: AutomationTemplate, name: String) {
        show(Screen.AutomationEdit(null, AutomationDraft(name, template.steps), template))
    }

    private fun writeAutomations(remoteId: String, list: List<Automation>): Boolean {
        val remotes = storing {
            store.updateAutomations(remoteId, list)
            store.all()
        } ?: return false
        _state.update { s -> s.copy(remotes = remotes, current = s.current?.let { c -> remotes.firstOrNull { it.id == c.id } }) }
        return true
    }

    // ---- Aufnahme ----

    /** Neue Automation per Fernbedienung aufnehmen; „Abbrechen“ führt zurück zu [returnTo]. */
    fun recordNew(returnTo: Screen) = startRecording(Recording(null, "", 0, returnTo), emptyList())

    /** Aus dem Editor: an [existing] anhängen; „Abbrechen“ führt zum Editor mit diesem Stand zurück. */
    fun recordInto(automationId: String?, name: String, existing: List<AutomationStep>) = startRecording(
        Recording(automationId, name, existing.size, Screen.AutomationEdit(automationId, AutomationDraft(name, existing))),
        existing,
    )

    /** Zeigt die Fernbedienung im Aufnahmemodus; eine laufende Automation bzw. ein Suchlauf hält an. */
    private fun startRecording(recording: Recording, existing: List<AutomationStep>) {
        // Ohne Fernseher gibt es nichts aufzunehmen (Klima-Tasten sind nicht aufnehmbar).
        if (_state.value.current?.hasTv != true) return
        automationRunner.cancel()
        if (scanRunner?.running?.value == true) stopScan()
        recorder.start(existing)
        _state.update { it.copy(recording = recording, screen = Screen.Remote) }
    }

    fun undoRecording() = recorder.undo()

    /** Öffnet den Editor mit allen Schritten (vorhandene und aufgenommene). */
    fun finishRecording() {
        val recording = _state.value.recording ?: return
        val steps = recorder.finish()
        _state.update {
            it.copy(
                recording = null,
                screen = Screen.AutomationEdit(recording.automationId, AutomationDraft(recording.name, steps)),
            )
        }
    }

    /** Verwirft die Aufnahme und kehrt zum Ausgangspunkt zurück. */
    fun cancelRecording() {
        val recording = _state.value.recording ?: return
        recorder.cancel()
        _state.update { it.copy(recording = null, screen = recording.returnTo) }
    }

    // ---- Sprache ----

    /** Öffnet „Sprache wählen“ über dem aktuellen Bildschirm; ein laufender Suchlauf hält an. */
    fun openLanguage() {
        if (scanRunner?.running?.value == true) stopScan()
        languageReturn = _state.value.screen
        _state.update { it.copy(screen = Screen.Language) }
    }

    /** Zurück ohne Änderung. */
    fun closeLanguage() {
        _state.update { it.copy(screen = languageReturn) }
    }

    /** Sprache gewählt (das Setzen der Locale übernimmt das ViewModel): weiter zum vorigen bzw. Startbildschirm. */
    fun languageChosen() {
        _state.update { it.copy(screen = languageReturn, languageFirstStart = false) }
    }

    // ---- Assistent ----

    /** Öffnet den Assistenten mit frischem Automaten; ohne Code-Datenbank nur den Hinweis. */
    fun openWizard() {
        scanRunner?.stop()
        scanRunner = null
        machine = null
        val c = catalog
        if (c != null) {
            val m = WizardMachine(c)
            machine = m
            scanRunner = ScanRunner(m, send, scope, onStateChanged = ::pushWizard)
        }
        _state.update { s ->
            s.copy(
                screen = Screen.Wizard,
                wizard = machine?.state ?: WizardState.PickBrand,
                current = if (s.remotes.isEmpty()) null else s.current,
            )
        }
    }

    private fun pushWizard() {
        machine?.let { m -> _state.update { it.copy(wizard = m.state) } }
    }

    private inline fun wizard(action: (WizardMachine) -> Unit) {
        val m = machine ?: return
        action(m)
        pushWizard()
    }

    fun pickBrand(brandId: Long) = wizard { it.pickBrand(brandId) }

    fun answer(yes: Boolean) = wizard { it.answer(yes) }

    fun useTvOffMode() = wizard { it.useTvOffMode() }

    fun testSignal() {
        machine?.testSignal()?.let(send)
    }

    fun startScan(from: Int = 0) = wizard {
        it.startScan(from)
        scanRunner?.start()
    }

    fun stopScan() = wizard { scanRunner?.stop() }

    /** App nicht mehr sichtbar: ein laufender Suchlauf hält an (weiter mit der Prüfung der Kandidaten). */
    fun onAppStopped() {
        if (scanRunner?.running?.value == true) stopScan()
    }

    /** Zurück im Assistenten; false, wenn die App verlassen werden soll (Markenwahl ohne gespeicherte Fernbedienung). */
    fun wizardBack(): Boolean {
        val m = machine
        if (scanRunner?.running?.value == true) scanRunner?.stop()
        if (m != null && m.back()) {
            pushWizard()
            return true
        }
        return closeWizard()
    }

    private fun closeWizard(): Boolean {
        scanRunner?.stop()
        if (_state.value.current == null) return false
        show(Screen.Remote)
        return true
    }

    /** Speichert den Fund; scheitert das Schreiben, bleibt er für diese Sitzung nutzbar. */
    fun saveWizard(name: String?, brandName: String) {
        val s = machine?.state as? WizardState.Save ?: return
        scanRunner?.stop()
        val remote = SavedRemote(
            id = UUID.randomUUID().toString(),
            name = name,
            brand = brandName.trim().ifBlank { s.brandName },
            codeLabel = s.codeLabel,
            keys = s.keys,
            lastUsed = clock(),
        )
        val remotes = storing {
            store.add(remote)
            store.all()
        } ?: (listOf(remote) + _state.value.remotes)
        _state.update { it.copy(current = remote, remotes = remotes, screen = Screen.Remote) }
    }

    fun close() {
        scanRunner?.stop()
        automationRunner.cancel()
    }
}
