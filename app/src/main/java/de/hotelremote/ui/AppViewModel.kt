package de.hotelremote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.hotelremote.R
import de.hotelremote.data.CodeDatabase
import de.hotelremote.data.CodeRepository
import de.hotelremote.data.RemoteStore
import de.hotelremote.ir.AndroidIrTransmitter
import de.hotelremote.ir.SignalSender
import de.hotelremote.ui.remote.KeyHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.io.File

/** Verdrahtet Android-Teile (IR-Sender, Dateien, Ressourcen) mit dem [AppController]. */
class AppViewModel(app: Application) : AndroidViewModel(app), KeyHandler {
    private val transmitter = AndroidIrTransmitter(app)
    private val sendFailed = app.getString(R.string.send_failed)
    private val sender: SignalSender = SignalSender(transmitter, viewModelScope, Dispatchers.IO) {
        controller.showMessage(sendFailed)
    }

    val controller: AppController = AppController(
        store = RemoteStore(File(app.filesDir, "remotes.json")),
        openCatalog = { CodeRepository(CodeDatabase.open(app)) },
        send = sender::press,
        scope = viewModelScope,
        io = Dispatchers.IO,
        messages = AppMessages(
            saveFailed = app.getString(R.string.save_failed),
            loadFailed = app.getString(R.string.load_failed),
        ),
        irAvailable = transmitter.available,
    )

    override fun press(key: String) {
        controller.state.value.current?.keys?.get(key)?.let(sender::press)
    }

    override fun hold(key: String): Job? = controller.state.value.current?.keys?.get(key)?.let(sender::hold)

    override fun onCleared() {
        controller.close()
    }
}
