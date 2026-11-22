package de.hotelremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.hotelremote.ui.App
import de.hotelremote.ui.AppViewModel
import de.hotelremote.ui.theme.HotelRemoteTheme

class MainActivity : ComponentActivity() {
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

    /** Im Hintergrund kein Suchlauf: er hält an und der Assistent prüft die letzten Kandidaten. */
    override fun onStop() {
        super.onStop()
        vm.controller.onAppStopped()
    }
}
