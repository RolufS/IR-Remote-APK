package de.hotelremote.ui.remote

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.SavedRemote
import de.hotelremote.ui.theme.RemoteColors

/** Alle `X:`-Tasten der Fernbedienung als große Zeilen-Buttons, alphabetisch. */
@Composable
fun ExtraKeysScreen(remote: SavedRemote, handler: KeyHandler, onBack: () -> Unit) {
    val extras = remember(remote) {
        remote.keys.keys.filter(Keys::isExtra).sortedBy { Keys.extraLabel(it).lowercase() }
    }
    val backDesc = stringResource(R.string.cd_back)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .height(48.dp)
                    .width(48.dp)
                    .testTag("extra_back")
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = backDesc },
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = RemoteColors.KeyText, fontSize = 20.sp)
            }
            Text(
                stringResource(R.string.menu_extra),
                color = RemoteColors.KeyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (extras.isEmpty()) {
            Text(
                stringResource(R.string.extra_keys_empty),
                color = RemoteColors.KeyText.copy(alpha = 0.7f),
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(extras, key = { it }) { key ->
                    KeyButton(key, true, handler, Modifier.fillMaxWidth().height(64.dp), sendOnPress = false) {
                        Text(
                            Keys.extraLabel(key),
                            color = RemoteColors.KeyText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
