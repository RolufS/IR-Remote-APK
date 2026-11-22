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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hotelremote.ui.about.AboutScreen
import de.hotelremote.ui.remote.ExtraKeysScreen
import de.hotelremote.ui.remote.RemoteMenu
import de.hotelremote.ui.remote.RemoteScreen
import de.hotelremote.ui.remotes.RemoteListScreen
import de.hotelremote.ui.theme.RemoteColors
import de.hotelremote.ui.wizard.WizardScreen

/** Wählt den Bildschirm nach dem Zustand des ViewModels; Fehlermeldungen als Snackbar. */
@Composable
fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.clearMessage()
    }
    BackHandler(enabled = !state.loading && state.screen in setOf(Screen.RemoteList, Screen.ExtraKeys, Screen.About)) {
        vm.show(Screen.Remote)
    }
    Box(Modifier.fillMaxSize().background(RemoteColors.BackgroundBottom)) {
        val current = state.current
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom))),
            )
            state.screen == Screen.Wizard -> WizardScreen(
                state = state.wizard,
                brands = state.brands,
                onPickBrand = vm::pickBrand,
                onStartScan = { vm.startScan(0) },
                onTest = vm::testSignal,
                onAnswer = vm::answer,
                onTvOff = vm::useTvOffMode,
                onStopScan = vm::stopScan,
                onResumeScan = vm::startScan,
                onSave = vm::saveWizard,
                onBack = vm::wizardBack,
            )
            state.screen == Screen.RemoteList -> RemoteListScreen(
                remotes = state.remotes,
                currentId = current?.id,
                onSwitch = vm::switchTo,
                onRename = vm::rename,
                onDelete = vm::delete,
                onNew = vm::openWizard,
                onBack = { vm.show(Screen.Remote) },
            )
            state.screen == Screen.About -> AboutScreen(meta = state.meta, onBack = { vm.show(Screen.Remote) })
            current == null -> Unit
            state.screen == Screen.ExtraKeys -> ExtraKeysScreen(current, vm, onBack = { vm.show(Screen.Remote) })
            else -> RemoteScreen(
                remote = current,
                remotes = state.remotes,
                irAvailable = state.irAvailable,
                handler = vm,
                onSwitch = vm::switchTo,
                onOpenList = { vm.show(Screen.RemoteList) },
                onMenu = { menu ->
                    when (menu) {
                        RemoteMenu.EXTRA_KEYS -> vm.show(Screen.ExtraKeys)
                        RemoteMenu.WIZARD -> vm.openWizard()
                        RemoteMenu.ABOUT -> vm.show(Screen.About)
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
