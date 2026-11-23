package de.hotelremote.ui.quick

import android.Manifest
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.hotelremote.R
import de.hotelremote.data.SavedRemote
import de.hotelremote.quick.InputTileService
import de.hotelremote.quick.LockStyle
import de.hotelremote.quick.PowerTileService
import de.hotelremote.quick.QuickAction
import de.hotelremote.quick.QuickButton
import de.hotelremote.quick.QuickPrefs
import de.hotelremote.quick.QuickWidgetEntry
import de.hotelremote.quick.QuickWidgetProvider
import de.hotelremote.quick.RemoteTileService
import de.hotelremote.quick.VolumeDownTileService
import de.hotelremote.quick.VolumeUpTileService
import de.hotelremote.quick.inputChoices
import de.hotelremote.quick.quickKey
import de.hotelremote.sleeptimer.AndroidSleepTimerNotifier
import de.hotelremote.ui.automation.keyName
import de.hotelremote.ui.backArrow
import de.hotelremote.ui.theme.RemoteColors

/**
 * Einstellungen „Schnellsteuerung“: Eingangstaste, Sperrbildschirm-Benachrichtigung (fragt ab Android 13 nach der
 * Erlaubnis für Benachrichtigungen) mit eigener Tastenbelegung und Ansicht (Klein/Erweitert), die ganze
 * Fernbedienung über dem Sperrbildschirm (Benachrichtigungs-Knopf und Kachel), Anleitung für Kacheln und Widget,
 * ggf. mit Knöpfen zum direkten Hinzufügen, und die Liste der Widgets ([widgets]) zum Bearbeiten ihrer Knöpfe.
 */
@Composable
fun QuickControlsScreen(
    remote: SavedRemote,
    prefs: QuickPrefs,
    onInputKey: (String) -> Unit,
    onLockScreen: (Boolean) -> Unit,
    onBack: () -> Unit,
    widgets: List<QuickWidgetEntry> = emptyList(),
    onEditWidget: (Int) -> Unit = {},
    onNotificationButtons: (List<QuickButton>) -> Unit = {},
    onLockStyle: (LockStyle) -> Unit = {},
    onFullRemote: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var editNotification by rememberSaveable { mutableStateOf(false) }
    if (editNotification) {
        QuickButtonsEditor(
            target = QuickButtonsTarget.NOTIFICATION,
            remote = remote,
            inputKey = prefs.inputKey,
            initial = prefs.notificationButtons,
            onSave = {
                onNotificationButtons(it)
                editNotification = false
            },
            onCancel = { editNotification = false },
        )
        return
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    // Bei jeder Rückkehr neu prüfen (z. B. aus den Systemeinstellungen), sonst bliebe der Hinweis stehen.
    var canPost by remember { mutableStateOf(AndroidSleepTimerNotifier.canPostNotifications(context)) }
    LifecycleResumeEffect(Unit) {
        canPost = AndroidSleepTimerNotifier.canPostNotifications(context)
        if (canPost) denied = false
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        canPost = AndroidSleepTimerNotifier.canPostNotifications(context)
        if (granted) onLockScreen(true)
    }
    fun setLockScreen(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPost) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            denied = false
            onLockScreen(on)
        }
    }
    val backDesc = stringResource(R.string.cd_back)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .height(48.dp)
                    .width(48.dp)
                    .testTag("quick_back")
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backDesc },
                contentAlignment = Alignment.Center,
            ) {
                Text(backArrow(), color = RemoteColors.KeyText, fontSize = 20.sp)
            }
            Text(stringResource(R.string.menu_quick), color = RemoteColors.KeyText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Body(stringResource(R.string.quick_target, remote.title))

            Section(stringResource(R.string.quick_input_section))
            val choices = inputChoices(remote.keys.keys)
            val selected = quickKey(QuickAction.INPUT, remote.keys.keys, prefs.inputKey)
            if (choices.isEmpty()) Body(stringResource(R.string.quick_input_none))
            choices.forEach { key ->
                Choice(keyName(context.resources, key), key == selected, Modifier.testTag("quick_input_$key")) { onInputKey(key) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.quick_lock_switch),
                    color = RemoteColors.KeyText,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(checked = prefs.lockScreen, onCheckedChange = ::setLockScreen, modifier = Modifier.testTag("quick_lock"))
            }
            Body(stringResource(R.string.quick_lock_hint))
            if (prefs.lockScreen) {
                Choice(
                    stringResource(R.string.quick_lock_small),
                    prefs.lockStyle == LockStyle.SMALL,
                    Modifier.testTag("quick_lock_small"),
                ) { onLockStyle(LockStyle.SMALL) }
                Choice(
                    stringResource(R.string.quick_lock_expanded),
                    prefs.lockStyle == LockStyle.EXPANDED,
                    Modifier.testTag("quick_lock_expanded"),
                ) { onLockStyle(LockStyle.EXPANDED) }
            }
            Action(stringResource(R.string.qb_edit_notification), Modifier.testTag("quick_notif_buttons")) { editNotification = true }
            if (denied || (prefs.lockScreen && !canPost)) {
                Body(stringResource(R.string.quick_notif_blocked), Modifier.testTag("quick_notif_blocked"))
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.quick_full_remote_switch),
                    color = RemoteColors.KeyText,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(checked = prefs.fullRemote, onCheckedChange = onFullRemote, modifier = Modifier.testTag("quick_full_remote"))
            }
            Body(stringResource(R.string.quick_full_remote_hint))
            Body(stringResource(R.string.quick_full_remote_howto))

            Section(stringResource(R.string.quick_tiles_section))
            Body(stringResource(R.string.quick_tiles_howto))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                tiles().forEach { tile ->
                    val label = stringResource(tile.label)
                    Action(stringResource(R.string.quick_add_tile, label)) { requestAddTile(context, tile, label) }
                }
            }

            Section(stringResource(R.string.quick_widget_section))
            Body(stringResource(R.string.quick_widget_howto))
            if (widgetPinSupported(context)) {
                Action(stringResource(R.string.quick_add_widget), Modifier.testTag("quick_add_widget")) { requestPinWidget(context) }
            }
            if (widgets.isEmpty()) Body(stringResource(R.string.qb_widgets_none), Modifier.testTag("quick_widgets_none"))
            widgets.forEachIndexed { i, widget ->
                WidgetRow(stringResource(R.string.qb_widget_entry, i + 1, widget.buttonCount), "quick_widget_$i") { onEditWidget(widget.id) }
            }
            if (widgets.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Body(stringResource(R.string.qb_widget_reconfigure))
        }
    }
}

private class TileEntry(val service: Class<*>, val label: Int, val icon: Int)

/** Eine Funktion statt einer Liste auf oberster Ebene: vor Android 7 darf keine TileService-Klasse geladen werden. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun tiles() = listOf(
    TileEntry(PowerTileService::class.java, R.string.tile_power, R.drawable.ic_quick_power),
    TileEntry(VolumeDownTileService::class.java, R.string.tile_vol_down, R.drawable.ic_quick_vol_down),
    TileEntry(VolumeUpTileService::class.java, R.string.tile_vol_up, R.drawable.ic_quick_vol_up),
    TileEntry(InputTileService::class.java, R.string.tile_input, R.drawable.ic_quick_input),
    TileEntry(RemoteTileService::class.java, R.string.tile_remote, R.drawable.ic_quick_remote),
)

/** Systemdialog „Kachel hinzufügen“ (ab Android 13); das Ergebnis braucht die App nicht. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context, tile: TileEntry, label: String) {
    val bar = context.getSystemService(StatusBarManager::class.java) ?: return
    try {
        bar.requestAddTileService(
            ComponentName(context, tile.service),
            label,
            Icon.createWithResource(context, tile.icon),
            context.mainExecutor,
        ) { }
    } catch (e: RuntimeException) {
        // Manche Hersteller unterstützen den Dialog nicht; die Anleitung bleibt.
    }
}

private fun widgetPinSupported(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true

private fun requestPinWidget(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    try {
        AppWidgetManager.getInstance(context)?.requestPinAppWidget(ComponentName(context, QuickWidgetProvider::class.java), null, null)
    } catch (e: RuntimeException) {
        // Launcher lehnt ab: die Anleitung bleibt.
    }
}

@Composable
private fun Section(text: String) =
    Text(
        text,
        color = RemoteColors.KeyText.copy(alpha = 0.6f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp),
    )

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) =
    Text(text, color = RemoteColors.KeyText.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp, modifier = modifier)

/** Auswahlzeile; die gewählte in der Farbe der SOURCE-Taste. */
@Composable
private fun Choice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = if (selected) {
        listOf(RemoteColors.SourceTop, RemoteColors.SourceBottom)
    } else {
        listOf(RemoteColors.KeyTop, RemoteColors.KeyBottom)
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(colors))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = RemoteColors.KeyText, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Widget in der Liste mit „Bearbeiten“. */
@Composable
private fun WidgetRow(text: String, tag: String, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(RemoteColors.KeyTop, RemoteColors.KeyBottom)))
            .testTag(tag)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = RemoteColors.KeyText, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .testTag("${tag}_edit")
                .clickable(role = Role.Button, onClick = onEdit)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.qb_edit), color = RemoteColors.KeyText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Action(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RemoteColors.KeyBottom)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = RemoteColors.KeyText, fontSize = 15.sp)
    }
}
