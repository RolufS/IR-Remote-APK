package de.hotelremote.ui.remotes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.data.SavedRemote
import de.hotelremote.ui.DeviceKind
import de.hotelremote.ui.backArrow
import de.hotelremote.ui.remote.SourceBrush
import de.hotelremote.ui.theme.RemoteColors

private val RowColor = Color(0xFF36383D)
private val CurrentRowColor = Color(0xFF45484F)

/**
 * Liste der gespeicherten Räume: Tippen wechselt, langes Drücken öffnet Umbenennen, Geräte hinzufügen/entfernen
 * und Löschen. Entfernen des letzten Geräts löscht den Raum (die Rückfrage sagt das).
 */
@Composable
fun RemoteListScreen(
    remotes: List<SavedRemote>,
    currentId: String?,
    onSwitch: (String) -> Unit,
    onRename: (String, String?) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    onAddDevice: (String, DeviceKind) -> Unit = { _, _ -> },
    onRemoveDevice: (String, DeviceKind) -> Unit = { _, _ -> },
) {
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var renameFor by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteFor by rememberSaveable { mutableStateOf<String?>(null) }
    var replaceAcFor by rememberSaveable { mutableStateOf<String?>(null) }
    var removeFor by rememberSaveable { mutableStateOf<Pair<String, DeviceKind>?>(null) }
    val backDesc = stringResource(R.string.cd_back)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .height(48.dp)
                    .width(48.dp)
                    .testTag("list_back")
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backDesc },
                contentAlignment = Alignment.Center,
            ) {
                Text(backArrow(), color = RemoteColors.KeyText, fontSize = 20.sp)
            }
            Text(
                stringResource(R.string.list_title),
                color = RemoteColors.KeyText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(remotes, key = { it.id }) { r ->
                RemoteRow(
                    r,
                    isCurrent = r.id == currentId,
                    onClick = { onSwitch(r.id) },
                    onLongClick = { actionsFor = r.id },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SourceBrush)
                .testTag("list_new")
                .clickable(role = Role.Button, onClick = onNew),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.list_new), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    remotes.firstOrNull { it.id == actionsFor }?.let { r ->
        @Composable
        fun Action(label: Int, tag: String, action: () -> Unit) = TextButton(
            onClick = {
                actionsFor = null
                action()
            },
            modifier = Modifier.fillMaxWidth().testTag(tag),
        ) { Text(stringResource(label), Modifier.fillMaxWidth()) }
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            title = { Text(r.title) },
            text = {
                Column {
                    Action(R.string.list_rename, "action_rename") { renameFor = r.id }
                    if (r.ac != null) {
                        Action(R.string.menu_replace_ac, "action_add_ac") { replaceAcFor = r.id }
                    } else {
                        Action(R.string.menu_add_ac, "action_add_ac") { onAddDevice(r.id, DeviceKind.AC) }
                    }
                    if (!r.hasTv) Action(R.string.menu_add_tv, "action_add_tv") { onAddDevice(r.id, DeviceKind.TV) }
                    if (r.hasTv) Action(R.string.list_remove_tv, "action_remove_tv") { removeFor = r.id to DeviceKind.TV }
                    if (r.ac != null) Action(R.string.list_remove_ac, "action_remove_ac") { removeFor = r.id to DeviceKind.AC }
                    Action(R.string.list_delete, "action_delete") { deleteFor = r.id }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionsFor = null }) { Text(stringResource(R.string.list_cancel)) }
            },
        )
    }

    remotes.firstOrNull { it.id == renameFor }?.let { r ->
        var name by rememberSaveable(r.id) { mutableStateOf(r.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text(stringResource(R.string.list_rename)) },
            text = {
                OutlinedTextField(
                    name,
                    { name = it },
                    placeholder = { Text(stringResource(R.string.list_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameFor = null
                    onRename(r.id, name.ifBlank { null })
                }) { Text(stringResource(R.string.list_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameFor = null }) { Text(stringResource(R.string.list_cancel)) }
            },
        )
    }

    remotes.firstOrNull { it.id == deleteFor }?.let { r ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            text = { Text(stringResource(R.string.list_delete_confirm, r.title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteFor = null
                    onDelete(r.id)
                }) { Text(stringResource(R.string.list_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.list_cancel)) }
            },
        )
    }

    remotes.firstOrNull { it.id == replaceAcFor }?.let { r ->
        ReplaceAcDialog(
            r.title,
            onConfirm = {
                replaceAcFor = null
                onAddDevice(r.id, DeviceKind.AC)
            },
            onDismiss = { replaceAcFor = null },
        )
    }

    removeFor?.let { (id, kind) ->
        val r = remotes.firstOrNull { it.id == id } ?: return@let
        val last = if (kind == DeviceKind.TV) r.ac == null else !r.hasTv
        AlertDialog(
            onDismissRequest = { removeFor = null },
            text = {
                Text(
                    stringResource(
                        when {
                            last -> R.string.list_remove_last_confirm
                            kind == DeviceKind.TV -> R.string.list_remove_tv_confirm
                            else -> R.string.list_remove_ac_confirm
                        },
                        r.title,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeFor = null
                        onRemoveDevice(id, kind)
                    },
                    modifier = Modifier.testTag("remove_confirm"),
                ) { Text(stringResource(if (last) R.string.list_delete else R.string.list_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removeFor = null }) { Text(stringResource(R.string.list_cancel)) }
            },
        )
    }
}

/** Rückfrage vor „Klimaanlage ersetzen“: ersetzt wird erst beim Speichern der neuen. */
@Composable
fun ReplaceAcDialog(roomTitle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.replace_ac_confirm, roomTitle)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("replace_ac_confirm")) {
                Text(stringResource(R.string.replace_ac))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) } },
    )
}

/** „TV · Klima“: die Geräte des Raums. */
@Composable
private fun devicesLine(r: SavedRemote): String = listOfNotNull(
    stringResource(R.string.room_page_tv).takeIf { r.hasTv },
    stringResource(R.string.room_page_ac).takeIf { r.ac != null },
).joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteRow(r: SavedRemote, isCurrent: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) CurrentRowColor else RowColor)
            .testTag("remote_${r.id}")
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(r.title, color = RemoteColors.KeyText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            devicesLine(r),
            modifier = Modifier.testTag("devices_" + r.id),
            color = RemoteColors.KeyText.copy(alpha = 0.55f),
            fontSize = 13.sp,
        )
    }
}
