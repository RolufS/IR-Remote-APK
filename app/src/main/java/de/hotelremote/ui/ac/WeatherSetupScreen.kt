package de.hotelremote.ui.ac

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.ui.WeatherUi
import de.hotelremote.ui.backArrow
import de.hotelremote.ui.remote.SourceBrush
import de.hotelremote.ui.theme.RemoteColors
import de.hotelremote.ui.wizard.RowColor
import de.hotelremote.ui.wizard.WizardButton
import de.hotelremote.weather.Place
import de.hotelremote.weather.WeatherSettings

/**
 * Einrichtung der Außentemperatur: an/aus (Standard aus), Erklärung, was gesendet wird, Ortssuche mit Treffern
 * (Land/Region) und Quellenangabe. Gesucht wird erst nach dem Einschalten.
 */
@Composable
fun WeatherSetupScreen(
    weather: WeatherSettings,
    ui: WeatherUi,
    onEnabled: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onChoose: (Place) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    ClimateFrame(stringResource(R.string.weather_title), onBack, "weather_back") {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RowColor)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.weather_show), color = RemoteColors.KeyText, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(checked = weather.enabled, onCheckedChange = onEnabled, modifier = Modifier.testTag("weather_switch"))
        }
        Body(stringResource(R.string.weather_explain), Modifier.testTag("weather_explain"))
        if (weather.enabled) {
            weather.place?.let { place ->
                Text(
                    stringResource(R.string.weather_place, placeText(place)),
                    color = RemoteColors.KeyText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("weather_place"),
                )
            }
            val search = { onSearch(query.trim()) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.weather_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth().testTag("weather_query"),
            )
            WizardButton(
                stringResource(if (ui.searching) R.string.weather_searching else R.string.weather_search),
                { if (query.isNotBlank() && !ui.searching) search() },
                Modifier.fillMaxWidth().testTag("weather_search"),
                brush = SourceBrush,
            )
            when {
                ui.searchFailed -> Body(stringResource(R.string.weather_search_failed), Modifier.testTag("weather_search_failed"))
                ui.results?.isEmpty() == true -> Body(stringResource(R.string.weather_no_results), Modifier.testTag("weather_no_results"))
                ui.results != null -> ui.results.forEachIndexed { i, place -> PlaceRow(place, i) { onChoose(place) } }
            }
        }
        Body(stringResource(R.string.weather_source_full), Modifier.testTag("weather_source"))
    }
}

/** „Pattaya (Chon Buri, Thailand)“. */
private fun placeText(place: Place) = if (place.detail.isEmpty()) place.name else "${place.name} (${place.detail})"

@Composable
private fun PlaceRow(place: Place, index: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RowColor)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("weather_result_$index")
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(place.name, color = RemoteColors.KeyText, fontSize = 16.sp)
        if (place.detail.isNotEmpty()) {
            Text(place.detail, color = RemoteColors.KeyText.copy(alpha = 0.55f), fontSize = 13.sp)
        }
    }
}

/** Rahmen der Klima-Einstellungen: Kopf mit Zurück und Titel, darunter scrollbarer Inhalt. */
@Composable
internal fun ClimateFrame(title: String, onBack: () -> Unit, backTag: String, content: @Composable () -> Unit) {
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
                    .testTag(backTag)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backDesc },
                contentAlignment = Alignment.Center,
            ) {
                Text(backArrow(), color = RemoteColors.KeyText, fontSize = 20.sp)
            }
            Text(title, color = RemoteColors.KeyText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun Body(text: String, modifier: Modifier = Modifier) =
    Text(text, color = RemoteColors.KeyText.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp, modifier = modifier)
