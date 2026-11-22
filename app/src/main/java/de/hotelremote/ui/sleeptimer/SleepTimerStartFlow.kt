package de.hotelremote.ui.sleeptimer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.hotelremote.R
import de.hotelremote.sleeptimer.AndroidSleepTimerNotifier

/** Was der Start-Ablauf von der App braucht. */
interface SleepTimerHost {
    /** Akku-Hinweis noch nicht gezeigt (nur beim ersten Timer-Start). */
    val batteryHintNeeded: Boolean
    fun markBatteryHintShown()
    fun canScheduleExact(): Boolean
    fun startSleepTimer(request: TimerRequest)
}

private enum class Step { BATTERY, EXACT, NOTIFICATIONS, START }

/**
 * Vor dem Start nacheinander: Akku-Hinweis (einmalig), Erlaubnis für genaue Alarme (ab Android 12, falls
 * nicht erteilt; der Timer startet trotzdem und wird beim Zurückkehren genau geplant) und die Erlaubnis für
 * Benachrichtigungen (ab Android 13). Danach startet der Timer; [onDone] erfährt, ob Benachrichtigungen fehlen.
 */
@Composable
fun SleepTimerStartFlow(request: TimerRequest, host: SleepTimerHost, onDone: (notificationsDenied: Boolean) -> Unit) {
    val context = LocalContext.current
    fun next(after: Step?): Step {
        val order = Step.entries
        val from = if (after == null) 0 else order.indexOf(after) + 1
        return order.drop(from).first { step ->
            when (step) {
                Step.BATTERY -> host.batteryHintNeeded
                Step.EXACT -> !host.canScheduleExact()
                Step.NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !AndroidSleepTimerNotifier.canPostNotifications(context)
                Step.START -> true
            }
        }
    }
    var step by remember(request) { mutableStateOf(next(null)) }
    var notificationsDenied by remember(request) { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsDenied = !granted
        step = Step.START
    }

    when (step) {
        Step.BATTERY -> AlertDialog(
            onDismissRequest = {
                host.markBatteryHintShown()
                step = next(Step.BATTERY)
            },
            title = { Text(stringResource(R.string.timer_battery_title)) },
            text = { Text(stringResource(R.string.timer_battery_text)) },
            confirmButton = {
                TextButton(onClick = {
                    host.markBatteryHintShown()
                    openBatterySettings(context)
                    step = next(Step.BATTERY)
                }, modifier = Modifier.testTag("battery_open")) { Text(stringResource(R.string.timer_battery_open)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    host.markBatteryHintShown()
                    step = next(Step.BATTERY)
                }, modifier = Modifier.testTag("battery_later")) { Text(stringResource(R.string.timer_not_now)) }
            },
        )
        Step.EXACT -> AlertDialog(
            onDismissRequest = { step = next(Step.EXACT) },
            title = { Text(stringResource(R.string.timer_exact_title)) },
            text = { Text(stringResource(R.string.timer_exact_text)) },
            confirmButton = {
                TextButton(onClick = {
                    openExactAlarmSettings(context)
                    step = next(Step.EXACT)
                }, modifier = Modifier.testTag("exact_allow")) { Text(stringResource(R.string.timer_exact_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { step = next(Step.EXACT) }, modifier = Modifier.testTag("exact_later")) {
                    Text(stringResource(R.string.timer_not_now))
                }
            },
        )
        Step.NOTIFICATIONS -> LaunchedEffect(request) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Step.START -> LaunchedEffect(request) {
            host.startSleepTimer(request)
            onDone(notificationsDenied)
        }
    }
}

/** App-Details (dort „Akku“ → „Keine Einschränkungen“, auch bei Xiaomi/HyperOS); sonst die allgemeine Akku-Liste. */
private fun openBatterySettings(context: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    if (!tryStart(context, details)) tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

/** „Wecker und Erinnerungen“ für diese App (ab Android 12). */
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
    if (!tryStart(context, intent)) {
        tryStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}

private fun tryStart(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
