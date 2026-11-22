package de.hotelremote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import de.hotelremote.ui.App
import de.hotelremote.ui.AppViewModel
import de.hotelremote.ui.theme.HotelRemoteTheme

/** AppCompatActivity für die App-Sprache (`AppCompatDelegate.setApplicationLocales`) auch vor Android 13. */
class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HotelRemoteTheme {
                App(vm, onExit = ::finish)
            }
        }
    }

    /**
     * Im Hintergrund kein Suchlauf: er hält an und der Assistent prüft die letzten Kandidaten.
     * Beim Neuaufbau der Activity (Konfigurationswechsel) läuft er weiter. Ein Sprachwechsel in der App
     * erzeugt die Activity ebenfalls neu; der Bildschirm „Sprache wählen“ hat den Suchlauf vorher angehalten.
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.controller.onAppStopped()
    }
}
