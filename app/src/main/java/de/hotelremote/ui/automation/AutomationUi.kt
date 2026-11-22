package de.hotelremote.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.automation.AutomationProgress
import de.hotelremote.ui.backArrow
import de.hotelremote.ui.theme.RemoteColors

internal val RowColor = Color(0xFF36383D)

/** Kopfzeile mit Zurück-Pfeil und Titel (wie „Weitere Tasten“). */
@Composable
internal fun ScreenHeader(title: String, backTag: String, onBack: () -> Unit) {
    val backDesc = stringResource(R.string.cd_back)
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .testTag(backTag)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backDesc },
            contentAlignment = Alignment.Center,
        ) {
            Text(backArrow(), color = RemoteColors.KeyText, fontSize = 20.sp)
        }
        Text(title, color = RemoteColors.KeyText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Breiter, farbiger Knopf. */
@Composable
internal fun WideButton(
    text: String,
    tag: String,
    brush: Brush,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .testTag(tag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Kleiner quadratischer Symbol-Knopf (Zeichen aus der Grundschrift). */
@Composable
internal fun SymbolButton(
    symbol: String,
    description: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(RoundedCornerShape(10.dp))
            .testTag(tag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = RemoteColors.KeyText, fontSize = 17.sp)
    }
}

/** Fortschritt einer laufenden Automation mit „Abbrechen“. */
@Composable
fun AutomationProgressBar(progress: AutomationProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .testTag("automation_progress")
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RowColor)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.auto_progress, progress.name, progress.step, progress.total),
                color = RemoteColors.KeyText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel, modifier = Modifier.testTag("automation_cancel")) {
                Text(stringResource(R.string.list_cancel))
            }
        }
        LinearProgressIndicator(
            progress = { progress.step.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
        )
    }
}
