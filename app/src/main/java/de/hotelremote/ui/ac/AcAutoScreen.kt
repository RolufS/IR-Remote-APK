package de.hotelremote.ui.ac

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.acauto.AcAutoProblem
import de.hotelremote.acauto.AcAutoRun
import de.hotelremote.acauto.acAutoTarget
import de.hotelremote.acauto.outdoorText
import de.hotelremote.data.AcAutoSettings
import de.hotelremote.data.SavedAc
import de.hotelremote.data.acTempText
import de.hotelremote.data.autoSettings
import de.hotelremote.ui.remote.PowerBrush
import de.hotelremote.ui.theme.RemoteColors
import de.hotelremote.ui.wizard.RowColor
import de.hotelremote.ui.wizard.WizardButton
import de.hotelremote.weather.WeatherSettings

/**
 * Einstellung der Klima-Automatik eines Raums: Abstand und Grenzen (Einheit des Geräts), Lüfter, Vorschau, Hinweis
 * zum Handy im Raum, Start/Stopp. Start ohne eingeschaltete Außentemperatur führt zuerst zu deren Einrichtung.
 * [fans]: Lüfterstufen im Kühlmodus; [run]: laufende Automatik (auch eines anderen Raums).
 */
@Composable
fun AcAutoScreen(
    roomId: String,
    ac: SavedAc,
    fans: List<String>,
    weather: WeatherSettings,
    run: AcAutoRun?,
    onSettings: (AcAutoSettings) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onWeatherSetup: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val s = ac.autoSettings
    val f = ac.fahrenheit
    val step = if (ac.precision > 0) ac.precision else 1.0
    val offsetRange = AcAutoSettings.offsetRange(f)
    val runningHere = run?.roomId == roomId
    ClimateFrame(stringResource(R.string.acauto_title), onBack, "acauto_back") {
        Stepper(
            key = "offset",
            title = stringResource(R.string.acauto_offset),
            value = acTempText(s.offset, f),
            canMinus = s.offset - 1 >= offsetRange.start,
            canPlus = s.offset + 1 <= offsetRange.endInclusive,
            onMinus = { onSettings(s.copy(offset = s.offset - 1)) },
            onPlus = { onSettings(s.copy(offset = s.offset + 1)) },
        )
        val ceilingFloor = s.notWarmerThan ?: ac.maxTemp
        Stepper(
            key = "floor",
            title = stringResource(R.string.acauto_floor),
            value = acTempText(s.notColderThan, f),
            canMinus = s.notColderThan - step >= ac.minTemp,
            canPlus = s.notColderThan + step <= minOf(ceilingFloor, ac.maxTemp),
            onMinus = { onSettings(s.copy(notColderThan = s.notColderThan - step)) },
            onPlus = { onSettings(s.copy(notColderThan = s.notColderThan + step)) },
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RowColor).padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.acauto_ceiling), color = RemoteColors.KeyText, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = s.notWarmerThan != null,
                    onCheckedChange = { on ->
                        val default = (s.notColderThan + defaultCeilingGap(f)).coerceAtMost(ac.maxTemp)
                        onSettings(s.copy(notWarmerThan = if (on) default else null))
                    },
                    modifier = Modifier.testTag("acauto_ceiling_switch"),
                )
            }
            s.notWarmerThan?.let { ceiling ->
                StepperRow(
                    key = "ceiling",
                    value = acTempText(ceiling, f),
                    canMinus = ceiling - step >= s.notColderThan,
                    canPlus = ceiling + step <= ac.maxTemp,
                    onMinus = { onSettings(s.copy(notWarmerThan = ceiling - step)) },
                    onPlus = { onSettings(s.copy(notWarmerThan = ceiling + step)) },
                )
            }
        }
        if (fans.size > 1) {
            val fan = fans.firstOrNull { it.equals(s.fan, ignoreCase = true) } ?: fans.first()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RowColor)
                    .clickable(role = Role.Button) { onSettings(s.copy(fan = fans[(fans.indexOf(fan) + 1) % fans.size])) }
                    .testTag("acauto_fan")
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.ac_fan), color = RemoteColors.KeyText, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(label(fan, ::fanName), color = RemoteColors.KeyText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // Vorschau mit dem letzten Wert, sonst mit dem Beispiel 33 °C.
        val outdoorC = weather.last?.takeIf { weather.active }?.celsius ?: PREVIEW_OUTDOOR_C
        val target = acAutoTarget(outdoorC, ac, s)
        Text(
            stringResource(R.string.acauto_preview, outdoorText(outdoorC, f), acTempText(target, f)),
            color = RemoteColors.KeyText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("acauto_preview"),
        )
        Body(stringResource(R.string.acauto_hint), Modifier.testTag("acauto_hint"))
        if (runningHere) Status(run!!, f)
        if (!weather.active) Body(stringResource(R.string.acauto_needs_weather), Modifier.testTag("acauto_needs_weather"))
        if (runningHere) {
            WizardButton(stringResource(R.string.acauto_stop), onStop, Modifier.fillMaxWidth().testTag("acauto_stop"), brush = PowerBrush)
        } else {
            WizardButton(
                stringResource(R.string.acauto_start),
                { if (weather.active) onStart() else onWeatherSetup() },
                Modifier.fillMaxWidth().testTag("acauto_start"),
                brush = StartBrush,
            )
        }
        Body(stringResource(R.string.weather_source_full))
    }
}

/** Beispiel für die Vorschau ohne Wetterwert (wie in der Beschreibung: 33 °C → 28 °C). */
private const val PREVIEW_OUTDOOR_C = 33.0

/** Abstand der Obergrenze zur Untergrenze beim Einschalten: 3 °C bzw. 5 °F. */
private fun defaultCeilingGap(fahrenheit: Boolean) = if (fahrenheit) 5.0 else 3.0

private val StartBrush = Brush.verticalGradient(listOf(RemoteColors.YesTop, RemoteColors.YesBottom))

@Composable
private fun Status(run: AcAutoRun, fahrenheit: Boolean) {
    val values = if (run.outdoorC != null && run.target != null) {
        stringResource(R.string.acauto_values, outdoorText(run.outdoorC, fahrenheit), acTempText(run.target, fahrenheit))
    } else {
        null
    }
    val text = when (run.problem) {
        AcAutoProblem.WEATHER -> stringResource(R.string.weather_failed)
        AcAutoProblem.SEND -> stringResource(R.string.send_failed)
        null -> null
    }
    val line = listOfNotNull(stringResource(R.string.acauto_notif_running), text, values).joinToString(" · ")
    Text(line, color = RemoteColors.YesTop, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("acauto_status"))
}

/** Zeile mit Überschrift und −/+. */
@Composable
private fun Stepper(
    key: String,
    title: String,
    value: String,
    canMinus: Boolean,
    canPlus: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RowColor).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(title, color = RemoteColors.KeyText, fontSize = 15.sp)
        StepperRow(key, value, canMinus, canPlus, onMinus, onPlus)
    }
}

@Composable
private fun StepperRow(
    key: String,
    value: String,
    canMinus: Boolean,
    canPlus: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton("acauto_${key}_minus", "−", stringResource(R.string.cd_less), canMinus, onMinus)
        Text(
            value,
            color = RemoteColors.KeyText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).testTag("acauto_${key}_value"),
        )
        StepButton("acauto_${key}_plus", "+", stringResource(R.string.cd_more), canPlus, onPlus)
    }
}

@Composable
private fun StepButton(tag: String, text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(12.dp))
            .background(RemoteColors.KeyTop)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = RemoteColors.KeyText, fontSize = 24.sp)
    }
}
