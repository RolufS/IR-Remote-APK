package de.hotelremote.quick

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import de.hotelremote.R

/** Rückmeldung ohne App: kurzes Vibrieren beim Senden, sonst ein kurzer Toast. Aufruf auf dem Main-Thread. */
fun reportQuickResult(context: Context, result: QuickResult) {
    val text = when (result) {
        QuickResult.SENT -> {
            tick(context)
            return
        }
        QuickResult.NO_REMOTE -> R.string.quick_no_remote
        QuickResult.KEY_MISSING -> R.string.quick_key_missing
        QuickResult.AUTOMATION_MISSING -> R.string.quick_auto_missing
        QuickResult.TOO_LONG -> R.string.quick_auto_too_long
        QuickResult.BUSY -> R.string.auto_busy
        QuickResult.SEND_FAILED -> R.string.send_failed
    }
    Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
}

/** Haptischer Klick; ohne Vibrationsmotor oder bei abgeschalteter Vibration geschieht nichts. */
@SuppressLint("MissingPermission") // VIBRATE steht im Manifest
@Suppress("DEPRECATION") // Vibrator statt VibratorManager: genügt für einen Klick und geht ab API 23
private fun tick(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() } ?: return
    try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> vibrator.vibrate(VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> vibrator.vibrate(TICK_MS)
        }
    } catch (e: RuntimeException) {
        // Manche Hersteller werfen hier; die Rückmeldung ist verzichtbar.
    }
}

private const val TICK_MS = 20L
