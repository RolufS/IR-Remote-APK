package de.hotelremote.ui

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.hotelremote.data.CodeDatabase
import de.hotelremote.data.CodeRepository
import de.hotelremote.data.RemoteStore
import de.hotelremote.ir.AndroidIrTransmitter
import de.hotelremote.ir.SignalSender
import de.hotelremote.quick.QuickControls
import de.hotelremote.quick.QuickPrefs
import de.hotelremote.sleeptimer.AndroidAlarmScheduler
import de.hotelremote.sleeptimer.SleepTimer
import de.hotelremote.sleeptimer.SleepTimerGraph
import de.hotelremote.ui.sleeptimer.SleepTimerHost
import de.hotelremote.ui.sleeptimer.TimerRequest
import de.hotelremote.ui.language.AppLanguage
import de.hotelremote.ui.language.AppLanguages
import de.hotelremote.ui.language.LanguageChoice
import de.hotelremote.ui.remote.KeyHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Verdrahtet Android-Teile (IR-Sender, Dateien, Ressourcen) mit dem [AppController]. */
class AppViewModel(app: Application) : AndroidViewModel(app), KeyHandler, SleepTimerHost {
    private val transmitter = AndroidIrTransmitter(app)
    private val sender: SignalSender = SignalSender(transmitter, viewModelScope, Dispatchers.IO) {
        controller.showMessage(UiMessage.SEND_FAILED)
    }
    private val languageChoice = LanguageChoice(app.getSharedPreferences(LanguageChoice.PREFS, Context.MODE_PRIVATE))

    val controller: AppController = AppController(
        store = RemoteStore(File(app.filesDir, "remotes.json")),
        openCatalog = { CodeRepository(CodeDatabase.open(app)) },
        send = sender::press,
        scope = viewModelScope,
        io = Dispatchers.IO,
        irAvailable = transmitter.available,
        languageChosen = !languageChoice.needed,
        hold = sender::hold,
        recorderClock = SystemClock::elapsedRealtime,
    )

    private val timerScheduler = SleepTimerGraph.scheduler(app)
    private val timerStore = SleepTimerGraph.store(app)
    private val alarms = AndroidAlarmScheduler(app)

    /** Handy-Sleep-Timer (null = keiner); App und Alarm-Receiver teilen sich den Zustand. */
    val sleepTimer: StateFlow<SleepTimer?> = timerScheduler.state

    override val batteryHintNeeded: Boolean get() = !timerStore.batteryHintShown

    override fun markBatteryHintShown() {
        timerStore.batteryHintShown = true
    }

    override fun canScheduleExact(): Boolean = alarms.canScheduleExact()

    override fun startSleepTimer(request: TimerRequest) {
        val remote = controller.state.value.current ?: return
        timerScheduler.start(request.minutes, remote.id, request.action)
    }

    fun cancelSleepTimer() = timerScheduler.cancel()

    private val quickSettings = QuickControls.settings(app)
    private val _quickPrefs = MutableStateFlow(quickSettings.load())

    /** Einstellungen „Schnellsteuerung“. */
    val quickPrefs: StateFlow<QuickPrefs> = _quickPrefs.asStateFlow()

    fun setQuickInputKey(key: String) {
        quickSettings.inputKey = key
        _quickPrefs.update { it.copy(inputKey = key) }
    }

    fun setLockScreenControls(on: Boolean) {
        quickSettings.lockScreen = on
        _quickPrefs.update { it.copy(lockScreen = on) }
        refreshQuickControls()
    }

    /**
     * Widget und Sperrbildschirm-Benachrichtigung zeigen die zuletzt benutzte Fernbedienung; eine weggewischte
     * Benachrichtigung (ab Android 14 möglich) oder eine inzwischen erteilte Erlaubnis kommt so zurück.
     */
    fun refreshQuickControls() {
        viewModelScope.launch(Dispatchers.IO) { QuickControls.refresh(getApplication()) }
    }

    init {
        // Nach dem Laden und bei jedem Wechsel (oder Umbenennen) der aktuellen = zuletzt benutzten Fernbedienung.
        viewModelScope.launch {
            controller.state
                .filter { !it.loading }
                .map { it.current?.id to it.current?.title }
                .distinctUntilChanged()
                .collect { refreshQuickControls() }
        }
    }

    /** Rückkehr in die App: Alarm neu setzen (kann verloren gegangen sein), ggf. jetzt genau. */
    fun rearmSleepTimer() = timerScheduler.rearm()

    /** Nur neu lesen (z. B. ob der Receiver den Timer inzwischen ausgeführt hat). */
    fun reloadSleepTimer() = timerScheduler.reload()

    /** Vorauswahl für „Sprache wählen“ aus gewählter App-Sprache und Systemsprachen. */
    fun preselectedLanguage(): AppLanguage = AppLanguages.preselect(
        appTags = AppCompatDelegate.getApplicationLocales().tags(),
        systemTags = ConfigurationCompat.getLocales(Resources.getSystem().configuration).tags(),
        firstStart = controller.state.value.languageFirstStart,
    )

    /**
     * Merkt die Wahl, kehrt zum vorigen Bildschirm zurück und setzt die App-Sprache. Ändert sie sich,
     * erzeugt AppCompat die Activity neu; dieses ViewModel und die Fernbedienungen bleiben.
     */
    fun chooseLanguage(language: AppLanguage) {
        languageChoice.markChosen()
        controller.languageChosen()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    private fun LocaleListCompat.tags(): List<String> = (0 until size()).mapNotNull { get(it)?.toLanguageTag() }

    // Tasten laufen über den Controller, damit eine Aufnahme jeden Druck genau einmal sieht.
    override fun press(key: String) = controller.press(key)

    override fun hold(key: String): Job? = controller.hold(key)

    override fun onCleared() {
        controller.close()
    }
}
