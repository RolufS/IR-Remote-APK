package de.hotelremote.ui.ac

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.acauto.outdoorText
import de.hotelremote.ui.theme.RemoteColors
import de.hotelremote.weather.OutdoorWeather
import de.hotelremote.weather.WeatherSettings
import kotlinx.coroutines.delay

/**
 * Außentemperatur und Klima-Automatik auf dem Klima-Bildschirm. [autoTarget]: Ziel der laufenden Automatik dieses
 * Raums (Einheit des Geräts; null = noch keins); [autoRunning] = sie läuft hier.
 */
class AcClimate(
    val weather: WeatherSettings,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val autoRunning: Boolean = false,
    val autoTarget: Double? = null,
    /** Zeile angetippt: abrufen (eingeschaltet) bzw. Einrichtung öffnen. */
    val onOutdoor: () -> Unit = {},
    val onAuto: () -> Unit = {},
    val now: () -> Long = System::currentTimeMillis,
)

/** „gerade eben“, „vor 12 min“, „vor 3 h“. */
@Composable
internal fun agoText(at: Long, now: Long): String {
    val minutes = ((now - at).coerceAtLeast(0) / 60_000L).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.weather_just_now)
        minutes < 60 -> stringResource(R.string.weather_ago_min, minutes)
        else -> stringResource(R.string.weather_ago_h, minutes / 60)
    }
}

/**
 * Zeile „Draußen 31 °C · Pattaya · vor 12 min“ (ab 3 h als veraltet markiert) mit Quellenangabe; antippen ruft neu ab.
 * Ausgeschaltet nur der dezente Link „Außentemperatur anzeigen“.
 */
@Composable
internal fun OutdoorLine(climate: AcClimate, fahrenheit: Boolean) {
    val w = climate.weather
    val place = w.place
    if (!w.enabled || place == null) {
        Text(
            stringResource(R.string.weather_show),
            color = RemoteColors.KeyText.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier
                .testTag("ac_outdoor_link")
                .clickable(role = Role.Button, onClick = climate.onOutdoor)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        return
    }
    var now by remember { mutableLongStateOf(climate.now()) }
    LaunchedEffect(w.last) {
        while (true) {
            now = climate.now()
            delay(30_000)
        }
    }
    val last = w.last
    val stale = last != null && OutdoorWeather.isStale(last, now)
    val text = when {
        last != null -> {
            val parts = listOfNotNull(
                stringResource(R.string.weather_outdoor, outdoorText(last.celsius, fahrenheit)),
                place.name,
                if (climate.refreshing) "…" else agoText(last.at, now),
                stringResource(R.string.weather_stale).takeIf { stale },
            )
            parts.joinToString(" · ")
        }
        climate.refreshing -> stringResource(R.string.weather_outdoor, "…") + " · " + place.name
        else -> stringResource(R.string.weather_failed) + " · " + place.name
    }
    Column(
        Modifier
            .testTag("ac_outdoor")
            .clickable(role = Role.Button, onClick = climate.onOutdoor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            color = RemoteColors.KeyText.copy(alpha = if (stale || last == null) 0.5f else 0.85f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("ac_outdoor_text"),
        )
        Text(
            stringResource(R.string.weather_source),
            color = RemoteColors.KeyText.copy(alpha = 0.4f),
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}
