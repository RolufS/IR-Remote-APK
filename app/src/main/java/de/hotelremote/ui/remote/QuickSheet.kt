package de.hotelremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.automation.AutomationProgress
import de.hotelremote.data.Automation
import de.hotelremote.sleeptimer.SleepTimer
import de.hotelremote.ui.automation.AutomationProgressBar
import de.hotelremote.ui.sleeptimer.Hint
import de.hotelremote.ui.sleeptimer.remainingMinutes
import de.hotelremote.ui.theme.RemoteColors
import kotlinx.coroutines.delay

private val SheetRow = Color(0xFF36383D)

/** ⚡-Sheet: Automationen dieser Fernbedienung (Tipp = ausführen), Aufnahme, Sleep-Timer und „Automationen bearbeiten“. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSheet(
    automations: List<Automation>,
    progress: AutomationProgress?,
    timer: SleepTimer?,
    onRun: (Automation) -> Unit,
    onCancelRun: () -> Unit,
    onTimer: () -> Unit,
    onCancelTimer: () -> Unit,
    onEdit: () -> Unit,
    onRecord: () -> Unit,
    onTimerExpired: () -> Unit,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) {
        while (timer != null) {
            now = System.currentTimeMillis()
            if (timer.endAt <= now) onTimerExpired()
            delay(1_000)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RemoteColors.BackgroundTop,
    ) {
        QuickSheetContent(automations, progress, timer, now, onRun, onCancelRun, onTimer, onCancelTimer, onEdit, onRecord)
    }
}

@Composable
fun QuickSheetContent(
    automations: List<Automation>,
    progress: AutomationProgress?,
    timer: SleepTimer?,
    now: Long,
    onRun: (Automation) -> Unit,
    onCancelRun: () -> Unit,
    onTimer: () -> Unit,
    onCancelTimer: () -> Unit,
    onEdit: () -> Unit,
    onRecord: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (progress != null) AutomationProgressBar(progress, onCancelRun)
        automations.forEach { a ->
            SheetItem("▶  ${a.name}", "quick_run_${a.id}") { onRun(a) }
        }
        SheetItem(stringResource(R.string.rec_new_quick), "quick_record", onClick = onRecord)
        if (timer == null) {
            SheetItem(stringResource(R.string.timer_title), "quick_timer", onClick = onTimer)
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SheetRow)
                    .testTag("quick_timer_running")
                    .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.timer_running, remainingMinutes(timer.endAt, now)),
                    color = RemoteColors.KeyText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancelTimer, modifier = Modifier.testTag("quick_timer_cancel")) {
                    Text(stringResource(R.string.list_cancel))
                }
            }
            if (!timer.exact) Hint(stringResource(R.string.timer_inexact), "quick_timer_inexact")
        }
        Hint(stringResource(R.string.timer_hint_point), "quick_point_hint")
        SheetItem(stringResource(R.string.auto_edit), "quick_edit", onClick = onEdit)
    }
}

@Composable
private fun SheetItem(text: String, tag: String, onClick: () -> Unit) {
    Text(
        text,
        color = RemoteColors.KeyText,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SheetRow)
            .testTag(tag)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    )
}
