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
import de.hotelremote.acauto.AcAutoGraph
import de.hotelremote.acauto.AcAutoRunner
import de.hotelremote.data.AcAutoSettings
import de.hotelremote.data.AcCodeDatabase
import de.hotelremote.data.RoomPage
import de.hotelremote.data.CodeDatabase
import de.hotelremote.data.CodeRepository
import de.hotelremote.data.RemoteStore
import de.hotelremote.ir.AndroidIrTransmitter
import de.hotelremote.ir.SignalSender
import de.hotelremote.quick.LockStyle
import de.hotelremote.quick.QuickButton
import de.hotelremote.quick.QuickControls
import de.hotelremote.quick.QuickPrefs
import de.hotelremote.quick.QuickWidgetEntry
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
import kotlinx.coroutines.flow.filterNotNull
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
    private val remotesFile = File(app.filesDir, "remotes.json")

    private val acAutoScheduler = AcAutoGraph.scheduler(app)
    private val outdoorWeather = AcAutoGraph.weather(app)

    /** Außentemperatur und Klima-Automatik; in der App wird über die Warteschlange gesendet. */
    val climate = ClimateController(
        weatherSource = outdoorWeather,
        scheduler = acAutoScheduler,
        runner = AcAutoRunner(acAutoScheduler, RemoteStore(remotesFile), outdoorWeather, sender::press),
        scope = viewModelScope,
        io = Dispatchers.IO,
    )

    val controller: AppController = AppController(
        store = RemoteStore(remotesFile),
        openCatalog = { CodeRepository(CodeDatabase.open(app)) },
        send = sender::press,
        scope = viewModelScope,
        io = Dispatchers.IO,
        irAvailable = transmitter.available,
        languageChosen = !languageChoice.needed,
        hold = sender::hold,
        recorderClock = SystemClock::elapsedRealtime,
        openAcCatalog = { AcCodeDatabase.open(app) },
        stopAcAuto = climate::stopAutoFor,
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

    private val _quickWidgets = MutableStateFlow<List<QuickWidgetEntry>>(emptyList())

    /** Widgets auf dem Startbildschirm (neu gelesen bei jeder Rückkehr in die App). */
    val quickWidgets: StateFlow<List<QuickWidgetEntry>> = _quickWidgets.asStateFlow()

    fun setNotificationButtons(buttons: List<QuickButton>) {
        quickSettings.notificationButtons = buttons
        _quickPrefs.update { it.copy(notificationButtons = quickSettings.notificationButtons) }
        refreshQuickControls()
    }

    fun setLockStyle(style: LockStyle) {
        quickSettings.lockStyle = style
        _quickPrefs.update { it.copy(lockStyle = style) }
        refreshQuickControls()
    }

    /** Ganze Fernbedienung über dem Sperrbildschirm: Knopf in der Benachrichtigung und Kachel. */
    fun setFullRemote(on: Boolean) {
        quickSettings.fullRemote = on
        _quickPrefs.update { it.copy(fullRemote = on) }
        refreshQuickControls()
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
        viewModelScope.launch(Dispatchers.IO) {
            QuickControls.refresh(getApplication())
            _quickWidgets.value = QuickControls.widgetEntries(getApplication())
        }
    }

    init {
        // Von der Klima-Automatik gesendete Zustände (auch aus dem Receiver im selben Prozess) anzeigen.
        viewModelScope.launch {
            acAutoScheduler.state
                .map { run -> run?.sent?.let { run.roomId to it } }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { (roomId, state) -> controller.acStateSent(roomId, state) }
        }
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

    /** Rückkehr in die App: Wetter neu lesen, Alarm der Klima-Automatik neu setzen. */
    fun resumeClimate() {
        climate.resumed()
        val s = controller.state.value
        val room = s.current
        // Klima-Seite sichtbar: ein älterer Wert wird beim Zurückkehren erneuert.
        if (s.screen == Screen.Remote && room?.ac != null && (!room.hasTv || room.lastPage == RoomPage.AC)) climate.acScreenShown()
    }

    /** Außentemperatur aus: eine laufende Automatik endet (mit Hinweis). */
    fun setWeatherEnabled(on: Boolean) {
        if (climate.setWeatherEnabled(on)) controller.showMessage(UiMessage.AC_AUTO_STOPPED_WEATHER)
    }

    /** Neue Einstellungen der Automatik des aktuellen Raums; läuft sie dort, wird gleich neu gerechnet. */
    fun setAcAuto(settings: AcAutoSettings) {
        val id = controller.state.value.current?.id ?: return
        if (controller.setAcAuto(settings)) climate.settingsChanged(id)
    }

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
