package de.hotelremote.ui.about

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.ui.theme.RemoteColors

/** Über-Seite: Kurztext und Herkunft der Code-Daten aus der `meta`-Tabelle. */
@Composable
fun AboutScreen(meta: Map<String, String>, onBack: () -> Unit) {
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
                    .testTag("about_back")
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backDesc },
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = RemoteColors.KeyText, fontSize = 20.sp)
            }
            Text(
                stringResource(R.string.menu_about),
                color = RemoteColors.KeyText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Body(stringResource(R.string.about_text))
            Text(
                stringResource(R.string.about_codes),
                color = RemoteColors.KeyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Body(stringResource(R.string.about_flipper, meta["flipper_commit"].orEmpty().take(7)))
            if (!meta["irdb_commit"].isNullOrEmpty()) {
                Body(stringResource(R.string.about_irdb), Modifier.testTag("about_irdb"))
            }
            Body(stringResource(R.string.about_counts, meta["code_sets"].orEmpty(), meta["brands"].orEmpty()))
        }
    }
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) =
    Text(text, color = RemoteColors.KeyText.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp, modifier = modifier)
