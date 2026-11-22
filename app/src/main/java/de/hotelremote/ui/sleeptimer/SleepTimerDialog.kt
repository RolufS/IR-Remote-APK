package de.hotelremote.ui.sleeptimer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.data.SavedRemote
import de.hotelremote.sleeptimer.SleepAction
import de.hotelremote.sleeptimer.SleepTimerScheduler
import de.hotelremote.sleeptimer.fitsSleepTimer
import de.hotelremote.sleeptimer.onlyPowerToggle
import de.hotelremote.sleeptimer.powerOffKey
import de.hotelremote.ui.theme.RemoteColors

private val ChipColor = Color(0xFF36383D)
private val ChipSelected = RemoteColors.SourceTop

/** Ein Timer-Wunsch aus dem Dialog. */
data class TimerRequest(val minutes: Int, val action: SleepAction)

/** Speichert einen offenen Timer-Wunsch als „Minuten|Automation-id“ (leer = Ausschalten). */
val TimerRequestSaver: Saver<TimerRequest?, String> = Saver(
    save = { r -> r?.let { "${it.minutes}|${(it.action as? SleepAction.RunAutomation)?.automationId.orEmpty()}" } },
    restore = { text ->
        val (minutes, id) = text.split("|", limit = 2)
        TimerRequest(minutes.toInt(), if (id.isEmpty()) SleepAction.PowerOff else SleepAction.RunAutomation(id))
    },
)

/** Eigener Wert in Minuten (1–480); null bei ungültiger Eingabe. */
internal fun parseMinutes(text: String): Int? =
    text.trim().toIntOrNull()?.takeIf { it in SleepTimerScheduler.MIN_MINUTES..SleepTimerScheduler.MAX_MINUTES }

/** Dauer und Aktion am Ende wählen. Automationen, die zu lange dauern, sind nicht wählbar. */
@Composable
fun SleepTimerDialog(remote: SavedRemote, onStart: (TimerRequest) -> Unit, onDismiss: () -> Unit) {
    val keys = remote.keys.keys
    val canPowerOff = powerOffKey(keys) != null
    var preset by rememberSaveable { mutableStateOf<Int?>(30) }
    var custom by rememberSaveable { mutableStateOf("") }
    // Aktion: "" = Ausschalten, sonst die id der Automation.
    var actionId by rememberSaveable {
        mutableStateOf(if (canPowerOff) "" else remote.automations.firstOrNull { fitsSleepTimer(it.steps) }?.id)
    }
    val minutes = preset ?: parseMinutes(custom)
    val action = when (val id = actionId) {
        null -> null
        "" -> SleepAction.PowerOff
        else -> SleepAction.RunAutomation(id)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepTimerScheduler.PRESETS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { m ->
                            Chip(stringResource(R.string.timer_minutes, m), preset == m, "timer_preset_$m", Modifier.weight(1f)) {
                                preset = m
                            }
                        }
                    }
                }
                Chip(stringResource(R.string.timer_custom), preset == null, "timer_custom", Modifier.fillMaxWidth()) { preset = null }
                if (preset == null) {
                    OutlinedTextField(
                        custom,
                        { custom = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.timer_custom_hint)) },
                        singleLine = true,
                        isError = custom.isNotEmpty() && parseMinutes(custom) == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("timer_custom_field"),
                    )
                }
                Text(stringResource(R.string.timer_action), color = RemoteColors.KeyText.copy(alpha = 0.6f), fontSize = 12.sp)
                Option(
                    stringResource(R.string.timer_action_off),
                    if (canPowerOff) null else stringResource(R.string.timer_no_power),
                    selected = actionId == "",
                    enabled = canPowerOff,
                    tag = "timer_action_off",
                ) { actionId = "" }
                remote.automations.forEach { a ->
                    val fits = fitsSleepTimer(a.steps)
                    Option(
                        a.name,
                        if (fits) null else stringResource(R.string.timer_too_long),
                        selected = actionId == a.id,
                        enabled = fits,
                        tag = "timer_action_${a.id}",
                    ) { actionId = a.id }
                }
                if (action == SleepAction.PowerOff && onlyPowerToggle(keys)) {
                    Hint(stringResource(R.string.timer_toggle_hint), "timer_toggle_hint")
                }
                Hint(stringResource(R.string.timer_hint_point), "timer_point_hint")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (minutes != null && action != null) onStart(TimerRequest(minutes, action)) },
                enabled = minutes != null && action != null,
                modifier = Modifier.testTag("timer_start"),
            ) { Text(stringResource(R.string.timer_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) } },
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ChipSelected else ChipColor)
            .testTag(tag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = RemoteColors.KeyText, fontSize = 14.sp)
    }
}

@Composable
private fun Option(text: String, note: String?, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .testTag(tag)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 8.dp)) {
            Text(text, color = RemoteColors.KeyText, fontSize = 15.sp)
            if (note != null) Text(note, color = RemoteColors.KeyText.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun Hint(text: String, tag: String) {
    Text(
        text,
        color = RemoteColors.KeyText.copy(alpha = 0.75f),
        fontSize = 13.sp,
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChipColor)
            .padding(10.dp),
    )
}

/** Verbleibende Minuten (aufgerundet) bis [endAt]. */
fun remainingMinutes(endAt: Long, now: Long): Int = ((endAt - now + 59_999) / 60_000).coerceAtLeast(0).toInt()
