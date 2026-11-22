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
import de.hotelremote.ui.backArrow
import de.hotelremote.ui.remote.SourceBrush
import de.hotelremote.ui.theme.RemoteColors

private val RowColor = Color(0xFF36383D)
private val CurrentRowColor = Color(0xFF45484F)

/** Liste der gespeicherten Fernbedienungen: Tippen wechselt, langes Drücken öffnet Umbenennen/Löschen. */
@Composable
fun RemoteListScreen(
    remotes: List<SavedRemote>,
    currentId: String?,
    onSwitch: (String) -> Unit,
    onRename: (String, String?) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
) {
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var renameFor by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteFor by rememberSaveable { mutableStateOf<String?>(null) }
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
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            title = { Text(r.title) },
            confirmButton = {
                TextButton(onClick = {
                    actionsFor = null
                    renameFor = r.id
                }) { Text(stringResource(R.string.list_rename)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    actionsFor = null
                    deleteFor = r.id
                }) { Text(stringResource(R.string.list_delete)) }
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
}

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
            stringResource(R.string.list_subtitle, r.brand, r.codeLabel),
            color = RemoteColors.KeyText.copy(alpha = 0.55f),
            fontSize = 13.sp,
        )
    }
}
