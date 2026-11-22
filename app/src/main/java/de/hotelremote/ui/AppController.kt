package de.hotelremote.ui

import de.hotelremote.data.Brand
import de.hotelremote.data.CodeCatalog
import de.hotelremote.data.RemoteStorage
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.IrSignal
import de.hotelremote.wizard.ScanRunner
import de.hotelremote.wizard.WizardMachine
import de.hotelremote.wizard.WizardState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
}

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
    val message: String? = null,
)

/** Snackbar-Texte (aus strings.xml), damit der Controller ohne Android-Ressourcen auskommt. */
class AppMessages(val saveFailed: String, val loadFailed: String)

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
    private val messages: AppMessages,
    irAvailable: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(AppUiState(irAvailable = irAvailable))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var catalog: CodeCatalog? = null
    private var machine: WizardMachine? = null
    private var scanRunner: ScanRunner? = null

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
                    message = if (remotes == null) messages.loadFailed else it.message,
                )
            }
            val last = remotes?.firstOrNull()
            if (last != null) {
                _state.update { it.copy(current = last, screen = Screen.Remote) }
            } else {
                openWizard()
            }
        }
    }

    // ---- Fernbedienungen ----

    fun show(screen: Screen) {
        _state.update { it.copy(screen = screen) }
    }

    fun showMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Wechselt auch dann, wenn das Merken der Nutzung scheitert. */
    fun switchTo(id: String) {
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
            showMessage(messages.saveFailed)
            null
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
    }
}
