package de.hotelremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hotelremote.R
import de.hotelremote.ui.about.AboutScreen
import de.hotelremote.ui.language.AppLanguages
import de.hotelremote.ui.language.LanguageScreen
import de.hotelremote.ui.remote.ExtraKeysScreen
import de.hotelremote.ui.remote.RemoteMenu
import de.hotelremote.ui.remote.RemoteScreen
import de.hotelremote.ui.remotes.RemoteListScreen
import de.hotelremote.ui.theme.RemoteColors
import de.hotelremote.ui.wizard.WizardDbError
import de.hotelremote.ui.wizard.WizardScreen

/** Wählt den Bildschirm nach dem Zustand des ViewModels; Fehlermeldungen als Snackbar. [onExit] verlässt die App. */
@Composable
fun App(vm: AppViewModel, onExit: () -> Unit) {
    val c = vm.controller
    val state by c.state.collectAsStateWithLifecycle()
    val wizardBack = { if (!c.wizardBack()) onExit() }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(msg.text))
        c.clearMessage()
    }
    BackHandler(enabled = !state.loading && state.screen in setOf(Screen.RemoteList, Screen.ExtraKeys, Screen.About)) {
        c.show(Screen.Remote)
    }
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
            state.screen == Screen.ExtraKeys -> ExtraKeysScreen(current, vm, onBack = { c.show(Screen.Remote) })
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
                        RemoteMenu.WIZARD -> c.openWizard()
                        RemoteMenu.LANGUAGE -> c.openLanguage()
                        RemoteMenu.ABOUT -> c.show(Screen.About)
                    }
                },
            )
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
    }
