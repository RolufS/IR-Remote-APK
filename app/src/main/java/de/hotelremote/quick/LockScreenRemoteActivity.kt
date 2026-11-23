package de.hotelremote.quick

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import de.hotelremote.R
import de.hotelremote.data.SavedRemote
import de.hotelremote.ir.SignalSender
import de.hotelremote.ui.remote.ExtraKeysScreen
import de.hotelremote.ui.remote.KeyHandler
import de.hotelremote.ui.remote.RemoteMenu
import de.hotelremote.ui.remote.RemoteScreen
import de.hotelremote.ui.theme.HotelRemoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/**
 * Ganze Fernbedienung über dem Sperrbildschirm (Tippen auf die Benachrichtigung, Kachel „TV Fernbedienung“): die
 * zuletzt benutzte Fernbedienung, eingeschränkt auf Tasten und „Weitere Tasten“. Nichts ist änderbar, außer Name
 * und Tasten der Fernbedienung wird nichts gezeigt. Bewusst kein `requestDismissKeyguard` und kein
 * `setTurnScreenOn`; eigene Task (leere taskAffinity), damit die App-Task nicht über den Sperrbildschirm kommt.
 * Zurück oder Bildschirm aus schließt sie; wird das Handy dabei entsperrt, läuft sie normal weiter.
 */
class LockScreenRemoteActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        showWhenLocked()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Option inzwischen aus (alter PendingIntent): nichts zeigen.
        if (!QuickControls.settings(this).fullRemote) {
            finish()
            return
        }
        val remote = QuickControls.lastRemote(this)
        if (remote == null) {
            Toast.makeText(applicationContext, R.string.lock_no_remote, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val tx = QuickControls.transmitter(applicationContext)
        val main = Handler(Looper.getMainLooper())
        val sender = SignalSender(tx, lifecycleScope, Dispatchers.IO) {
            main.post { Toast.makeText(applicationContext, R.string.send_failed, Toast.LENGTH_SHORT).show() }
        }
        val handler = RemoteKeys(remote, sender)
        setContent {
            HotelRemoteTheme {
                var extras by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = extras) { extras = false }
                if (extras) {
                    ExtraKeysScreen(remote, handler, onBack = { extras = false })
                } else {
                    RemoteScreen(
                        remote = remote,
                        remotes = listOf(remote),
                        irAvailable = tx.available,
                        handler = handler,
                        onSwitch = {},
                        onOpenList = {},
                        onMenu = { if (it == RemoteMenu.EXTRA_KEYS) extras = true },
                        locked = true,
                    )
                }
            }
        }
    }

    /** Bildschirm aus (Ein/Aus-Taste) oder in den Hintergrund: schließen, der Sperrbildschirm ist wieder da. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    @Suppress("DEPRECATION") // FLAG_SHOW_WHEN_LOCKED nur vor Android 8.1
    private fun showWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    /** Sendet die Tasten der Fernbedienung wie in der App (Halten wiederholt). */
    private class RemoteKeys(private val remote: SavedRemote, private val sender: SignalSender) : KeyHandler {
        override fun press(key: String) {
            remote.keys[key]?.let(sender::press)
        }

        override fun hold(key: String): Job? = remote.keys[key]?.let(sender::hold)
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, LockScreenRemoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
