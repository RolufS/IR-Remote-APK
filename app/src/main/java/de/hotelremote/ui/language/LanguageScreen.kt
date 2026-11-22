package de.hotelremote.ui.language

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.ui.backArrow
import de.hotelremote.ui.remote.SourceBrush
import de.hotelremote.ui.theme.RemoteColors

private val RowColor = Color(0xFF36383D)
private val SelectedColor = Color(0xFF2C4A80)

/**
 * „Sprache wählen“: jede Sprache in ihrem eigenen Namen, [initial] vorausgewählt.
 * Ohne [onBack] (Erststart) gibt es keinen Zurück-Pfeil; die Zurück-Taste ruft dann [onExit].
 */
@Composable
fun LanguageScreen(
    languages: List<AppLanguage>,
    initial: AppLanguage,
    onContinue: (AppLanguage) -> Unit,
    onBack: (() -> Unit)?,
    onExit: () -> Unit = {},
) {
    var selectedTag by rememberSaveable(initial.tag) { mutableStateOf(initial.tag) }
    BackHandler { (onBack ?: onExit)() }
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
            if (onBack != null) {
                Box(
                    Modifier
                        .height(48.dp)
                        .width(48.dp)
                        .testTag("language_back")
                        .clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = backDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(backArrow(), color = RemoteColors.KeyText, fontSize = 20.sp)
                }
            } else {
                Spacer(Modifier.width(4.dp))
            }
            Text(
                stringResource(R.string.lang_title),
                color = RemoteColors.KeyText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyColumn(Modifier.weight(1f).testTag("language_list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(languages, key = { it.tag }) { lang ->
                val selected = lang.tag == selectedTag
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag("language_${lang.tag}")
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) SelectedColor else RowColor)
                        .selectable(selected = selected, role = Role.RadioButton) { selectedTag = lang.tag }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Eigene Locale je Zeile, damit z. B. Chinesisch mit chinesischen Glyphen erscheint.
                    Text(
                        lang.nativeName,
                        color = RemoteColors.KeyText,
                        style = TextStyle(fontSize = 17.sp, localeList = LocaleList(lang.tag)),
                    )
                    if (selected) Text("✔", color = Color(0xFF5FD37A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SourceBrush)
                .testTag("language_continue")
                .clickable(role = Role.Button) { onContinue(languages.first { it.tag == selectedTag }) },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.lang_continue), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
