package de.hotelremote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.hotelremote.R
import de.hotelremote.data.Brand
import de.hotelremote.data.CodeDatabase
import de.hotelremote.data.CodeRepository
import de.hotelremote.data.RemoteStore
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.AndroidIrTransmitter
import de.hotelremote.ir.SignalSender
import de.hotelremote.ui.remote.KeyHandler
import de.hotelremote.wizard.ScanRunner
import de.hotelremote.wizard.WizardMachine
import de.hotelremote.wizard.WizardState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    val brands: List<Brand> = emptyList(),
    val meta: Map<String, String> = emptyMap(),
    val message: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app), KeyHandler {
    private val transmitter = AndroidIrTransmitter(app)
    private val store = RemoteStore(File(app.filesDir, "remotes.json"))
    private val sendFailed = app.getString(R.string.send_failed)
    private val sender = SignalSender(transmitter, viewModelScope, Dispatchers.IO) {
        _state.update { it.copy(message = sendFailed) }
    }

    private val _state = MutableStateFlow(AppUiState(irAvailable = transmitter.available))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var repo: CodeRepository? = null
    private var machine: WizardMachine? = null
    private var scanRunner: ScanRunner? = null

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val r = CodeRepository(CodeDatabase.open(app))
                Loaded(r, r.brands(), r.meta(), store.all())
            }
            repo = loaded.repo
            _state.update {
                it.copy(loading = false, brands = loaded.brands, meta = loaded.meta, remotes = loaded.remotes)
            }
            val last = loaded.remotes.firstOrNull()
            if (last != null) {
                _state.update { it.copy(current = last, screen = Screen.Remote) }
            } else {
                openWizard()
            }
        }
    }

    private class Loaded(
        val repo: CodeRepository,
        val brands: List<Brand>,
        val meta: Map<String, String>,
        val remotes: List<SavedRemote>,
    )

    // ---- Fernbedienung ----

    override fun press(key: String) {
        _state.value.current?.keys?.get(key)?.let(sender::press)
    }

    override fun hold(key: String): Job? = _state.value.current?.keys?.get(key)?.let(sender::hold)

    fun show(screen: Screen) {
        _state.update { it.copy(screen = screen) }
    }

    fun switchTo(id: String) {
        store.markUsed(id, System.currentTimeMillis())
        val remotes = store.all()
        val target = remotes.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(current = target, remotes = remotes, screen = Screen.Remote) }
    }

    fun rename(id: String, name: String?) {
        store.rename(id, name?.trim()?.ifBlank { null })
        val remotes = store.all()
        _state.update { s -> s.copy(remotes = remotes, current = s.current?.let { c -> remotes.firstOrNull { it.id == c.id } }) }
    }

    fun delete(id: String) {
        store.delete(id)
        val remotes = store.all()
        val wasCurrent = _state.value.current?.id == id
        _state.update { it.copy(remotes = remotes) }
        if (wasCurrent) {
            val next = remotes.firstOrNull()
            if (next != null) _state.update { it.copy(current = next) } else openWizard()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    // ---- Assistent ----

    /** Öffnet den Assistenten mit frischem Automaten (und dazu passendem Suchlauf). */
    fun openWizard() {
        val r = repo ?: return
        scanRunner?.stop()
        val m = WizardMachine(r)
        machine = m
        scanRunner = ScanRunner(m, sender::press, viewModelScope, onStateChanged = ::pushWizard)
        _state.update { s ->
            s.copy(
                screen = Screen.Wizard,
                wizard = m.state,
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
        machine?.testSignal()?.let(sender::press)
    }

    fun startScan(from: Int = 0) = wizard {
        it.startScan(from)
        scanRunner?.start()
    }

    fun stopScan() = wizard { scanRunner?.stop() }

    fun wizardBack() {
        val m = machine ?: return
        if (scanRunner?.running?.value == true) scanRunner?.stop()
        if (m.back()) pushWizard() else closeWizard()
    }

    /** Ohne gespeicherte Fernbedienung zurück zur Markenwahl, sonst zur Fernbedienung. */
    fun closeWizard() {
        scanRunner?.stop()
        if (_state.value.current == null) openWizard() else show(Screen.Remote)
    }

    fun saveWizard(name: String?, brandName: String) {
        val s = machine?.state as? WizardState.Save ?: return
        scanRunner?.stop()
        val remote = SavedRemote(
            id = UUID.randomUUID().toString(),
            name = name,
            brand = brandName.trim().ifBlank { s.brandName },
            codeLabel = s.codeLabel,
            keys = s.keys,
            lastUsed = System.currentTimeMillis(),
        )
        store.add(remote)
        _state.update { it.copy(current = remote, remotes = store.all(), screen = Screen.Remote) }
    }

    override fun onCleared() {
        scanRunner?.stop()
    }
}
