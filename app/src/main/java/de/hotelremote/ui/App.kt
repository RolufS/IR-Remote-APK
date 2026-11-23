package de.hotelremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hotelremote.R
import de.hotelremote.automation.SleepTemplates
import de.hotelremote.quick.WidgetConfigActivity
import de.hotelremote.ui.about.AboutScreen
import de.hotelremote.ui.automation.AutomationEditorScreen
import de.hotelremote.ui.automation.AutomationListScreen
import de.hotelremote.ui.automation.AutomationProgressBar
import de.hotelremote.ui.automation.RecordingBarState
import de.hotelremote.ui.automation.keyName
import de.hotelremote.ui.automation.templateName
import de.hotelremote.ui.language.AppLanguages
import de.hotelremote.ui.language.LanguageScreen
import de.hotelremote.ui.quick.QuickControlsScreen
import de.hotelremote.ui.remote.ExtraKeysScreen
import de.hotelremote.ui.remote.QuickSheet
import de.hotelremote.ui.remote.RemoteMenu
import de.hotelremote.ui.remote.RemoteScreen
import de.hotelremote.ui.remotes.RemoteListScreen
import de.hotelremote.ui.sleeptimer.SleepTimerDialog
import de.hotelremote.ui.sleeptimer.SleepTimerStartFlow
import de.hotelremote.ui.sleeptimer.TimerRequest
import de.hotelremote.ui.sleeptimer.TimerRequestSaver
import de.hotelremote.ui.theme.RemoteColors
import de.hotelremote.ui.wizard.WizardDbError
import de.hotelremote.ui.wizard.WizardScreen

/** Wählt den Bildschirm nach dem Zustand des ViewModels; Fehlermeldungen als Snackbar. [onExit] verlässt die App. */
@Composable
fun App(vm: AppViewModel, onExit: () -> Unit) {
    val c = vm.controller
    val state by c.state.collectAsStateWithLifecycle()
    val wizardBack = { if (!c.wizardBack()) onExit() }
    val progress by c.automationProgress.collectAsStateWithLifecycle()
    val sleepTimer by vm.sleepTimer.collectAsStateWithLifecycle()
    val quickPrefs by vm.quickPrefs.collectAsStateWithLifecycle()
    val quickWidgets by vm.quickWidgets.collectAsStateWithLifecycle()
    val recordedSteps by c.recordedSteps.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val recording = state.recording?.let { r ->
        val count = recordedSteps.size - r.existingCount
        RecordingBarState(
            recordedCount = count,
            hint = r.hint,
            onUndo = c::undoRecording,
            onDone = c::finishRecording,
            onCancel = { if (count > 0) confirmDiscard = true else c.cancelRecording() },
        )
    }
    var quickOpen by rememberSaveable { mutableStateOf(false) }
    var timerDialog by rememberSaveable { mutableStateOf(false) }
    var timerRequest by rememberSaveable(stateSaver = TimerRequestSaver) { mutableStateOf<TimerRequest?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        val text = when (msg) {
            UiMessage.KEYS_MISSING ->
                context.getString(R.string.auto_keys_missing, state.missingKeys.joinToString { keyName(context.resources, it) })
            else -> context.getString(msg.text)
        }
        snackbar.showSnackbar(text)
        c.clearMessage()
    }
    // Während eine Automation läuft, bleibt der Bildschirm an.
    val view = LocalView.current
    DisposableEffect(progress != null) {
        view.keepScreenOn = progress != null
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(
        enabled = !state.loading && state.screen in setOf(Screen.RemoteList, Screen.ExtraKeys, Screen.About, Screen.Automations, Screen.QuickControls),
    ) {
        c.show(Screen.Remote)
    }
    BackHandler(enabled = state.screen is Screen.AutomationEdit) { c.show(Screen.Automations) }
    // Systemzurück auf der Fernbedienung während einer Aufnahme = Abbrechen (mit Rückfrage).
    BackHandler(enabled = recording != null && state.screen == Screen.Remote) { recording?.onCancel?.invoke() }
    Box(Modifier.fillMaxSize().background(RemoteColors.BackgroundBottom)) {
        val current = state.current
        when {
            state.screen == Screen.Language -> LanguageScreen(
                languages = AppLanguages.all,
                initial = remember { vm.preselectedLanguage() },
                onContinue = vm::chooseLanguage,
                onBack = if (state.languageFirstStart) null else c::closeLanguage,
                onExit = onExit,
            )
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom))),
            )
            state.screen == Screen.Wizard && state.dbError -> WizardDbError(onBack = wizardBack)
            state.screen == Screen.Wizard -> WizardScreen(
                state = state.wizard,
                brands = state.brands,
                irAvailable = state.irAvailable,
                savedRemotes = state.remotes,
                onPickBrand = c::pickBrand,
                onStartScan = { c.startScan(0) },
                onTest = c::testSignal,
                onAnswer = c::answer,
                onTvOff = c::useTvOffMode,
                onStopScan = c::stopScan,
                onResumeScan = c::startScan,
                onSave = c::saveWizard,
                onBack = wizardBack,
                onLanguage = c::openLanguage,
            )
            state.screen == Screen.RemoteList -> RemoteListScreen(
                remotes = state.remotes,
                currentId = current?.id,
                onSwitch = c::switchTo,
                onRename = c::rename,
                onDelete = c::delete,
                onNew = c::openWizard,
                onBack = { c.show(Screen.Remote) },
            )
            state.screen == Screen.About -> AboutScreen(meta = state.meta, dbError = state.dbError, onBack = { c.show(Screen.Remote) })
            current == null -> Unit
            state.screen == Screen.ExtraKeys -> ExtraKeysScreen(current, vm, onBack = { c.show(Screen.Remote) }, recording = recording)
            state.screen == Screen.Automations -> AutomationListScreen(
                automations = current.automations,
                progress = progress,
                onRun = { c.runAutomation(it.name, it.steps) },
                onCancelRun = c::cancelAutomation,
                onEdit = { c.show(Screen.AutomationEdit(it)) },
                onDelete = c::deleteAutomation,
                onBack = { c.show(Screen.Remote) },
                onRecord = { c.recordNew(returnTo = Screen.Automations) },
                templates = remember(current.brand, current.keys) { SleepTemplates.forRemote(current.brand, current.keys.keys) },
                onTemplate = { c.openTemplate(it, templateName(context.resources, it)) },
            )
            state.screen == Screen.QuickControls -> QuickControlsScreen(
                remote = current,
                prefs = quickPrefs,
                onInputKey = vm::setQuickInputKey,
                onLockScreen = vm::setLockScreenControls,
                onBack = { c.show(Screen.Remote) },
                widgets = quickWidgets,
                onEditWidget = { context.startActivity(WidgetConfigActivity.intent(context, it)) },
                onNotificationButtons = vm::setNotificationButtons,
                onLockStyle = vm::setLockStyle,
                onFullRemote = vm::setFullRemote,
            )
            state.screen is Screen.AutomationEdit -> {
                val edit = state.screen as Screen.AutomationEdit
                val id = edit.automationId
                AutomationEditorScreen(
                    initial = current.automations.firstOrNull { it.id == id },
                    keys = current.keys.keys,
                    progress = progress,
                    onTest = { name, steps -> c.runAutomation(name.ifBlank { context.getString(R.string.auto_test) }, steps) },
                    onCancelRun = c::cancelAutomation,
                    onSave = { if (c.saveAutomation(it)) c.show(Screen.Automations) },
                    onCancel = { c.show(Screen.Automations) },
                    onRecord = { name, steps -> c.recordInto(id, name, steps) },
                    draft = edit.draft,
                    template = edit.template,
                )
            }
            else -> RemoteScreen(
                remote = current,
                remotes = state.remotes,
                irAvailable = state.irAvailable,
                handler = vm,
                onSwitch = c::switchTo,
                onOpenList = { c.show(Screen.RemoteList) },
                onMenu = { menu ->
                    when (menu) {
                        RemoteMenu.EXTRA_KEYS -> c.show(Screen.ExtraKeys)
                        RemoteMenu.AUTOMATIONS -> c.show(Screen.Automations)
                        RemoteMenu.QUICK_CONTROLS -> c.show(Screen.QuickControls)
                        RemoteMenu.WIZARD -> c.openWizard()
                        RemoteMenu.LANGUAGE -> c.openLanguage()
                        RemoteMenu.ABOUT -> c.show(Screen.About)
                    }
                },
                onQuick = { quickOpen = true },
                recording = recording,
            )
        }
        if (current != null && state.screen == Screen.Remote && !state.loading) {
            if (quickOpen) {
                QuickSheet(
                    automations = current.automations,
                    progress = progress,
                    timer = sleepTimer,
                    onRun = { c.runAutomation(it.name, it.steps) },
                    onCancelRun = c::cancelAutomation,
                    onTimer = {
                        quickOpen = false
                        timerDialog = true
                    },
                    onCancelTimer = vm::cancelSleepTimer,
                    onEdit = {
                        quickOpen = false
                        c.show(Screen.Automations)
                    },
                    onRecord = {
                        quickOpen = false
                        c.recordNew(returnTo = Screen.Remote)
                    },
                    onTimerExpired = vm::reloadSleepTimer,
                    onDismiss = { quickOpen = false },
                )
            } else {
                progress?.let {
                    AutomationProgressBar(
                        it,
                        c::cancelAutomation,
                        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
                    )
                }
            }
            if (timerDialog) {
                SleepTimerDialog(
                    remote = current,
                    onStart = {
                        timerDialog = false
                        timerRequest = it
                    },
                    onDismiss = { timerDialog = false },
                )
            }
        }
        if (confirmDiscard && state.recording != null) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                text = { Text(stringResource(R.string.rec_discard_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDiscard = false
                            c.cancelRecording()
                        },
                        modifier = Modifier.testTag("recording_discard"),
                    ) { Text(stringResource(R.string.rec_discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.rec_continue)) }
                },
            )
        }
        timerRequest?.let { request ->
            SleepTimerStartFlow(request, vm) { notificationsDenied ->
                timerRequest = null
                if (notificationsDenied) c.showMessage(UiMessage.TIMER_NO_NOTIFICATIONS)
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

private val UiMessage.text: Int
    get() = when (this) {
        UiMessage.SAVE_FAILED -> R.string.save_failed
        UiMessage.LOAD_FAILED -> R.string.load_failed
        UiMessage.SEND_FAILED -> R.string.send_failed
        UiMessage.KEYS_MISSING -> R.string.auto_keys_missing
        UiMessage.AUTOMATION_BUSY -> R.string.auto_busy
        UiMessage.TIMER_NO_NOTIFICATIONS -> R.string.timer_no_notifications
    }
