package de.hotelremote.ui.quick

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.SavedRemote
import de.hotelremote.quick.QuickButton
import de.hotelremote.quick.QuickButtons
import de.hotelremote.quick.QuickPlan
import de.hotelremote.quick.QuickResult
import de.hotelremote.quick.QuickTone
import de.hotelremote.quick.buttonKey
import de.hotelremote.quick.fitsQuickControls
import de.hotelremote.quick.inputChoices
import de.hotelremote.quick.quickButtonLook
import de.hotelremote.quick.quickButtonName
import de.hotelremote.quick.resolveQuickButton
import de.hotelremote.ui.automation.RowColor
import de.hotelremote.ui.automation.ScreenHeader
import de.hotelremote.ui.automation.SymbolButton
import de.hotelremote.ui.automation.WideButton
import de.hotelremote.ui.automation.orderedKeys
import de.hotelremote.ui.remote.KeyBrush
import de.hotelremote.ui.remote.PowerBrush
import de.hotelremote.ui.remote.SourceBrush
import de.hotelremote.ui.theme.RemoteColors

/** Wofür die Knöpfe eingerichtet werden: Höchstzahl, Titel und Hinweis für zu lange Automationen. */
enum class QuickButtonsTarget(val max: Int, @param:StringRes val title: Int, @param:StringRes val tooLong: Int) {
    WIDGET(QuickButtons.MAX_WIDGET, R.string.qb_widget_title, R.string.qb_too_long_widget),
    NOTIFICATION(QuickButtons.MAX_NOTIFICATION, R.string.qb_notification_title, R.string.qb_too_long_notification),
}

/** Eintrag der Auswahl; [enabled] = false bei zu langen Automationen. */
data class PickerEntry(val button: QuickButton, val enabled: Boolean)

/** [tag] für Tests (common, all, extra, auto). */
data class PickerGroup(@param:StringRes val title: Int, val tag: String, val entries: List<PickerEntry>)

private val COMMON_KEYS = listOf(Keys.POWER, Keys.VOL_DOWN, Keys.VOL_UP, Keys.MUTE, Keys.CH_UP, Keys.CH_DOWN)

/**
 * Auswahl für einen Knopf, aus der zuletzt benutzten Fernbedienung: „Häufig“ (inkl. „Eingang“), alle kanonischen
 * Tasten, Extra-Tasten und Automationen (zu lange gesperrt). Ohne Fernbedienung nur „Häufig“. Leere Gruppen fehlen.
 */
fun pickerGroups(remote: SavedRemote?): List<PickerGroup> {
    val keys = remote?.keys?.keys
    val common = COMMON_KEYS.map { QuickButton.Key(it) }.filter { keys == null || buttonKey(it, keys, null) != null } +
        listOfNotNull(QuickButton.Input.takeIf { keys == null || inputChoices(keys).isNotEmpty() })
    val ordered = keys?.let(::orderedKeys).orEmpty()
    return listOf(
        PickerGroup(R.string.qb_group_common, "common", common.map { PickerEntry(it, true) }),
        PickerGroup(R.string.qb_group_all, "all", ordered.filterNot(Keys::isExtra).map { PickerEntry(QuickButton.Key(it), true) }),
        PickerGroup(R.string.qb_group_extra, "extra", ordered.filter(Keys::isExtra).map { PickerEntry(QuickButton.Key(it), true) }),
        PickerGroup(
            R.string.auto_title,
            "auto",
            remote?.automations.orEmpty().map { PickerEntry(QuickButton.Run(it.id, it.name), fitsQuickControls(it.steps)) },
        ),
    ).filter { it.entries.isNotEmpty() }
}

/** Testkennung eines Knopfs (Auswahl, Liste). */
internal fun buttonTag(button: QuickButton) = when (button) {
    is QuickButton.Key -> "key_${button.key}"
    QuickButton.Input -> "input"
    is QuickButton.Run -> "auto_${button.id}"
}

private val ButtonsSaver = listSaver<SnapshotStateList<QuickButton>, String>(
    save = { list -> list.map(QuickButtons::encode) },
    restore = { saved -> mutableStateListOf<QuickButton>().apply { addAll(saved.mapNotNull(QuickButtons::decode)) } },
)

/**
 * Knöpfe von Widget oder Sperrbildschirm-Benachrichtigung einrichten: Liste in Reihenfolge (Ändern per Tipp,
 * hoch/runter, entfernen), „+ Knopf hinzufügen“ bis [QuickButtonsTarget.max], Live-Vorschau, Speichern/Abbrechen.
 * Systemzurück = Abbrechen.
 */
@Composable
fun QuickButtonsEditor(
    target: QuickButtonsTarget,
    remote: SavedRemote?,
    inputKey: String?,
    initial: List<QuickButton>,
    onSave: (List<QuickButton>) -> Unit,
    onCancel: () -> Unit,
) {
    val res = LocalContext.current.resources
    val buttons = rememberSaveable(saver = ButtonsSaver) {
        mutableStateListOf<QuickButton>().apply { addAll(QuickButtons.normalize(initial, target.max)) }
    }
    // Index des zu ändernden Knopfs; buttons.size = neuer Knopf; null = Auswahl zu.
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(onBack = onCancel)

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(stringResource(target.title), "qb_back", onCancel)
        LazyColumn(Modifier.weight(1f).testTag("qb_list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text(
                    remote?.let { stringResource(R.string.quick_target, it.title) } ?: stringResource(R.string.quick_no_remote),
                    color = RemoteColors.KeyText.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item { SectionLabel(stringResource(R.string.qb_preview)) }
            item { Preview(buttons) }
            item { SectionLabel(stringResource(R.string.qb_buttons, buttons.size, target.max)) }
            itemsIndexed(buttons) { i, button ->
                val note = remote?.let { r ->
                    when ((resolveQuickButton(button, r, inputKey) as? QuickPlan.Fail)?.result) {
                        QuickResult.TOO_LONG -> stringResource(target.tooLong)
                        null -> null
                        else -> stringResource(R.string.qb_missing)
                    }
                }
                ButtonRow(
                    index = i,
                    name = quickButtonName(res, button),
                    note = note,
                    canUp = i > 0,
                    canDown = i < buttons.lastIndex,
                    canRemove = buttons.size > 1,
                    onChange = { picking = i },
                    onUp = { buttons.add(i - 1, buttons.removeAt(i)) },
                    onDown = { buttons.add(i + 1, buttons.removeAt(i)) },
                    onRemove = { buttons.removeAt(i) },
                )
            }
            item {
                WideButton(
                    stringResource(R.string.qb_add),
                    "qb_add",
                    KeyBrush,
                    Modifier.fillMaxWidth(),
                    enabled = buttons.size < target.max,
                ) { picking = buttons.size }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WideButton(stringResource(R.string.list_cancel), "qb_cancel", KeyBrush, Modifier.weight(1f), onClick = onCancel)
            WideButton(stringResource(R.string.list_save), "qb_save", SourceBrush, Modifier.weight(1f), enabled = buttons.isNotEmpty()) {
                onSave(buttons.toList())
            }
        }
    }

    picking?.let { index ->
        PickerDialog(
            groups = remember(remote) { pickerGroups(remote) },
            tooLong = stringResource(target.tooLong),
            onPick = { button ->
                picking = null
                if (index < buttons.size) buttons[index] = button else if (buttons.size < target.max) buttons += button
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(
        text,
        color = RemoteColors.KeyText.copy(alpha = 0.6f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )

@Composable
private fun ButtonRow(
    index: Int,
    name: String,
    note: String?,
    canUp: Boolean,
    canDown: Boolean,
    canRemove: Boolean,
    onChange: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RowColor)
            .testTag("qb_row_$index")
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onChange)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("${index + 1}. $name", color = RemoteColors.KeyText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (note != null) {
                Text(note, color = RemoteColors.KeyText.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.testTag("qb_note_$index"))
            }
        }
        SymbolButton("▲", stringResource(R.string.cd_step_up), "qb_up_$index", enabled = canUp, onClick = onUp)
        SymbolButton("▼", stringResource(R.string.cd_step_down), "qb_down_$index", enabled = canDown, onClick = onDown)
        SymbolButton("×", stringResource(R.string.qb_remove), "qb_remove_$index", enabled = canRemove, onClick = onRemove)
    }
}

/** Reihe wie im Widget: runde Knöpfe in Farbe, mit Symbol und/oder kurzer Beschriftung. */
@Composable
private fun Preview(buttons: List<QuickButton>) {
    val res = LocalContext.current.resources
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .testTag("qb_preview")
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { i, button ->
            val look = quickButtonLook(res, button)
            Column(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(look.tone.brush)
                    .testTag("qb_preview_$i")
                    .semantics { contentDescription = look.description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                look.icon?.let { Image(painterResource(it), contentDescription = null) }
                look.label?.let {
                    Text(
                        it,
                        color = RemoteColors.KeyText,
                        fontSize = (look.labelSp * 0.9f).sp,
                        lineHeight = (look.labelSp * 0.95f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

private val QuickTone.brush: Brush
    get() = when (this) {
        QuickTone.KEY -> KeyBrush
        QuickTone.POWER -> PowerBrush
        QuickTone.INPUT -> SourceBrush
        QuickTone.RED -> Brush.verticalGradient(listOf(RemoteColors.ColorRed, RemoteColors.ColorRed))
        QuickTone.GREEN -> Brush.verticalGradient(listOf(RemoteColors.ColorGreen, RemoteColors.ColorGreen))
        QuickTone.YELLOW -> Brush.verticalGradient(listOf(RemoteColors.ColorYellow, RemoteColors.ColorYellow))
        QuickTone.BLUE -> Brush.verticalGradient(listOf(RemoteColors.ColorBlue, RemoteColors.ColorBlue))
    }

/** Auswahl in Gruppen; zu lange Automationen ausgegraut mit Hinweis. */
@Composable
private fun PickerDialog(groups: List<PickerGroup>, tooLong: String, onPick: (QuickButton) -> Unit, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qb_pick)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp).testTag("qb_picker")) {
                groups.forEach { group ->
                    item(key = group.tag) { SectionLabel(stringResource(group.title)) }
                    itemsIndexed(group.entries, key = { _, e -> "${group.tag}/${buttonTag(e.button)}" }) { _, entry ->
                        val name = when (val b = entry.button) {
                            is QuickButton.Run -> b.name
                            else -> quickButtonName(res, b)
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (entry.enabled) 1f else 0.5f)
                                .testTag("pick_${group.tag}_${buttonTag(entry.button)}")
                                .clickable(enabled = entry.enabled, role = Role.Button) { onPick(entry.button) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(name, color = RemoteColors.KeyText, fontSize = 16.sp)
                            if (!entry.enabled) Text(tooLong, color = RemoteColors.KeyText.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) } },
    )
}
