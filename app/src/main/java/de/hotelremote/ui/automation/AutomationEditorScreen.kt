package de.hotelremote.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.automation.AutomationProgress
import de.hotelremote.automation.AutomationTemplate
import de.hotelremote.data.Automation
import de.hotelremote.data.AutomationStep
import de.hotelremote.ui.AutomationDraft
import de.hotelremote.ui.remote.KeyBrush
import de.hotelremote.ui.remote.PowerBrush
import de.hotelremote.ui.remote.SourceBrush
import de.hotelremote.ui.sleeptimer.Hint
import de.hotelremote.ui.theme.RemoteColors
import java.text.NumberFormat
import java.util.UUID

/** Schrittliste als flache Liste [Taste, Wartezeit, Taste, Wartezeit, …]. */
private val StepsSaver = listSaver<SnapshotStateList<AutomationStep>, Any>(
    save = { list -> list.flatMap { listOf(it.key, it.delayAfterMs) } },
    restore = { flat ->
        mutableStateListOf<AutomationStep>().apply {
            flat.chunked(2).forEach { (key, delay) -> add(AutomationStep(key as String, delay as Long)) }
        }
    },
)

/** Wartezeit eine Stufe (0,5 s) länger bzw. kürzer, begrenzt auf 0,5–10 s. */
internal fun changeDelay(ms: Long, up: Boolean): Long =
    (ms + if (up) AutomationStep.DELAY_STEP_MS else -AutomationStep.DELAY_STEP_MS)
        .coerceIn(AutomationStep.MIN_DELAY_MS, AutomationStep.MAX_DELAY_MS)

/**
 * Editor einer Automation: Name (Pflicht) und Schritte (Taste, Wartezeit danach, verschieben, entfernen).
 * „Testen“ führt die ungespeicherte Folge aus, „Per Fernbedienung aufnehmen“ hängt weitere Schritte an.
 * [draft] (z. B. nach einer Aufnahme) ersetzt Name und Schritte von [initial]; stammt er aus [template],
 * stehen oben deren Hinweise (Vertrauensstufe, Quelle).
 */
@Composable
fun AutomationEditorScreen(
    initial: Automation?,
    keys: Set<String>,
    progress: AutomationProgress?,
    onTest: (String, List<AutomationStep>) -> Unit,
    onCancelRun: () -> Unit,
    onSave: (Automation) -> Unit,
    onCancel: () -> Unit,
    onRecord: (String, List<AutomationStep>) -> Unit = { _, _ -> },
    draft: AutomationDraft? = null,
    template: AutomationTemplate? = null,
) {
    val res = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val seconds = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 } }
    // Saveable: Eingaben überstehen das Neuerzeugen der Activity (z. B. Sprachwechsel, Prozess-Ende im Hintergrund).
    var name by rememberSaveable(initial?.id, draft) { mutableStateOf(draft?.name ?: initial?.name.orEmpty()) }
    val steps = rememberSaveable(initial?.id, draft, saver = StepsSaver) {
        mutableStateListOf<AutomationStep>().apply { addAll(draft?.steps ?: initial?.steps.orEmpty()) }
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    val canSave = name.isNotBlank() && steps.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(
            stringResource(if (initial == null) R.string.auto_new_title else R.string.auto_edit_title),
            "editor_back",
            onCancel,
        )
        OutlinedTextField(
            name,
            { name = it },
            label = { Text(stringResource(R.string.auto_name)) },
            placeholder = { Text(stringResource(R.string.auto_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auto_name"),
        )
        if (progress != null) AutomationProgressBar(progress, onCancelRun)
        LazyColumn(Modifier.weight(1f).testTag("editor_steps"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (template != null) {
                itemsIndexed(templateNotices(res, template)) { i, text -> Hint(text, "template_notice_$i") }
            }
            if (steps.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.auto_no_steps),
                        color = RemoteColors.KeyText.copy(alpha = 0.6f),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            itemsIndexed(steps) { i, step ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RowColor)
                        .testTag("step_$i")
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}. ${keyName(res, step.key)}",
                            color = RemoteColors.KeyText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).testTag("step_key_$i"),
                        )
                        SymbolButton("▲", stringResource(R.string.cd_step_up), "step_up_$i", enabled = i > 0) {
                            steps.add(i - 1, steps.removeAt(i))
                        }
                        SymbolButton("▼", stringResource(R.string.cd_step_down), "step_down_$i", enabled = i < steps.lastIndex) {
                            steps.add(i + 1, steps.removeAt(i))
                        }
                        SymbolButton("×", stringResource(R.string.cd_step_remove), "step_remove_$i") { steps.removeAt(i) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.auto_wait, seconds.format(step.delayAfterMs / 1000.0)),
                            color = RemoteColors.KeyText.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f).testTag("step_wait_$i"),
                        )
                        SymbolButton("−", "−", "step_wait_minus_$i", enabled = step.delayAfterMs > AutomationStep.MIN_DELAY_MS) {
                            steps[i] = step.copy(delayAfterMs = changeDelay(step.delayAfterMs, up = false))
                        }
                        SymbolButton("+", "+", "step_wait_plus_$i", enabled = step.delayAfterMs < AutomationStep.MAX_DELAY_MS) {
                            steps[i] = step.copy(delayAfterMs = changeDelay(step.delayAfterMs, up = true))
                        }
                    }
                }
            }
            item {
                WideButton(stringResource(R.string.auto_add_step), "auto_add_step", KeyBrush, Modifier.fillMaxWidth()) {
                    picking = true
                }
            }
            item {
                WideButton(stringResource(R.string.rec_into), "auto_record", PowerBrush, Modifier.fillMaxWidth()) {
                    onRecord(name, steps.toList())
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WideButton(stringResource(R.string.auto_test), "auto_test", KeyBrush, Modifier.weight(1f), enabled = steps.isNotEmpty()) {
                onTest(name.trim(), steps.toList())
            }
            WideButton(stringResource(R.string.list_cancel), "auto_cancel", KeyBrush, Modifier.weight(1f), onClick = onCancel)
            WideButton(stringResource(R.string.list_save), "auto_save", SourceBrush, Modifier.weight(1f), enabled = canSave) {
                onSave(Automation(initial?.id ?: UUID.randomUUID().toString(), name.trim(), steps.toList()))
            }
        }
    }

    if (picking) {
        KeyPickerDialog(keys, onPick = { key ->
            picking = false
            steps += AutomationStep(key)
        }, onDismiss = { picking = false })
    }
}

/** Auswahl aller Tasten der Fernbedienung mit lesbaren Namen. */
@Composable
private fun KeyPickerDialog(keys: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val ordered = remember(keys) { orderedKeys(keys) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_pick_key)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(ordered) { _, key ->
                    Text(
                        keyName(res, key),
                        color = RemoteColors.KeyText,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_$key")
                            .clickable(role = Role.Button) { onPick(key) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) } },
    )
}
