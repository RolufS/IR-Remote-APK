package de.hotelremote.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.ui.theme.RemoteColors

/** Was die Aufnahmeleiste zeigt und auslöst. */
class RecordingBarState(
    /** In dieser Aufnahme hinzugekommene Schritte. */
    val recordedCount: Int,
    /** Hinweis bis zum ersten Tastendruck. */
    val hint: Boolean,
    val onUndo: () -> Unit,
    val onDone: () -> Unit,
    val onCancel: () -> Unit,
)

/** Rote Leiste während einer Aufnahme: Schrittzahl, ggf. Hinweis, Rückgängig / Fertig / Abbrechen. */
@Composable
fun RecordingBar(state: RecordingBarState, modifier: Modifier = Modifier) {
    val buttonColors = ButtonDefaults.textButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.4f),
    )
    Column(
        modifier
            .testTag("recording_bar")
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RemoteColors.PowerBottom)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp),
    ) {
        Text(
            stringResource(R.string.rec_status, state.recordedCount),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("recording_status"),
        )
        if (state.hint) {
            Text(
                stringResource(R.string.rec_hint),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                modifier = Modifier.testTag("recording_hint").padding(top = 2.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = state.onUndo,
                enabled = state.recordedCount > 0,
                colors = buttonColors,
                modifier = Modifier.weight(1f).testTag("recording_undo"),
            ) { BarLabel(stringResource(R.string.rec_undo)) }
            TextButton(
                onClick = state.onDone,
                colors = buttonColors,
                modifier = Modifier.weight(1f).testTag("recording_done"),
            ) { BarLabel(stringResource(R.string.rec_done)) }
            TextButton(
                onClick = state.onCancel,
                colors = buttonColors,
                modifier = Modifier.weight(1f).testTag("recording_cancel"),
            ) { BarLabel(stringResource(R.string.list_cancel)) }
        }
    }
}

@Composable
private fun BarLabel(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
