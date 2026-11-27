package de.hotelremote.ui.ac

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.R
import de.hotelremote.data.acTempText
import de.hotelremote.ui.AcAction
import de.hotelremote.ui.AcView
import de.hotelremote.ui.RoomPage
import de.hotelremote.ui.remote.KeyBrush
import de.hotelremote.ui.remote.KeyShape
import de.hotelremote.ui.remote.PowerBrush
import de.hotelremote.ui.remote.PowerIcon
import de.hotelremote.ui.remote.PressableKey
import de.hotelremote.ui.theme.RemoteColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Klimaanlage eines Raums für den Fernbedienungs-Bildschirm: Anzeige, Bedienung und (bei Raum mit Fernseher)
 * die zuletzt gezeigte Wischseite.
 */
class AcPanel(
    val view: AcView,
    val onAction: (AcAction) -> Unit,
    val page: RoomPage = RoomPage.TV,
    val onPage: (RoomPage) -> Unit = {},
)

/** −/+ gehalten: so oft wird ein weiterer Schritt ausgelöst. */
internal const val AC_HOLD_REPEAT_MS = 400L

private val Gap = 8.dp
private const val OFF_ALPHA = 0.4f

/**
 * Klima-Bildschirm im Stil der TV-Fernbedienung: oben Ein/Aus, darunter die Anzeige (Soll-Temperatur, Modus,
 * Lüfter, Swing; ausgeschaltet gedimmt), große −/+ (Halten wiederholt), Modus, Lüfter und Swing.
 * Die Zeile für die Außentemperatur und die Taste Klima-Automatik sind als leerer Platz vorgesehen.
 */
@Composable
fun AcScreen(view: AcView, onAction: (AcAction) -> Unit, modifier: Modifier = Modifier) {
    val s = view.state
    val action by rememberUpdatedState(onAction)
    Column(modifier.fillMaxSize().testTag("ac_screen"), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(Modifier.fillMaxWidth().weight(1.25f), horizontalArrangement = Arrangement.Center) {
            val description = stringResource(R.string.cd_ac_power)
            PressableKey(
                key = "ac_power",
                enabled = true,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f).semantics { contentDescription = description },
                shape = CircleShape,
                brush = PowerBrush,
                onTap = { action(AcAction.Power) },
                onLongPress = null,
                onPressStart = null,
            ) {
                Icon(PowerIcon, null, Modifier.size(26.dp), tint = Color.White)
            }
        }
        Display(view, Modifier.weight(3f))
        // −/+ wie das Tastenfeld des Fernsehers immer links/rechts, auch bei Arabisch.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().weight(2f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                StepKey("ac_cooler", "−", stringResource(R.string.cd_ac_cooler), view.canCooler) { action(AcAction.Cooler) }
                StepKey("ac_warmer", "+", stringResource(R.string.cd_ac_warmer), view.canWarmer) { action(AcAction.Warmer) }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1.2f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            ModeKey(view) { action(AcAction.SetMode(it)) }
            SettingKey(
                key = "ac_fan",
                title = stringResource(R.string.ac_fan),
                value = label(s.fan, ::fanName),
                icon = AcIcons.Fan,
                enabled = view.fans.size > 1,
            ) { action(AcAction.CycleFan) }
            if (view.swings.isNotEmpty()) {
                SettingKey(
                    key = "ac_swing",
                    title = stringResource(R.string.ac_swing),
                    value = label(s.swing.orEmpty(), ::swingName),
                    icon = AcIcons.Swing,
                    enabled = view.swings.size > 1,
                ) { action(AcAction.CycleSwing) }
            }
        }
        // Platz für die Taste Klima-Automatik (folgt mit Task 34).
        Box(Modifier.fillMaxWidth().weight(0.9f).testTag("ac_auto_slot"))
    }
}

@Composable
private fun Display(view: AcView, modifier: Modifier) {
    val s = view.state
    val alpha = if (s.power) 1f else OFF_ALPHA
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            acTempText(s.temp, view.fahrenheit),
            color = RemoteColors.KeyText,
            fontSize = 60.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            modifier = Modifier.testTag("ac_temp").alpha(alpha),
        )
        Row(Modifier.alpha(alpha), verticalAlignment = Alignment.CenterVertically) {
            Icon(modeIcon(s.mode), null, Modifier.size(22.dp), tint = RemoteColors.KeyText)
            Spacer(Modifier.width(8.dp))
            Text(
                label(s.mode, ::modeName),
                color = RemoteColors.KeyText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("ac_mode_name"),
            )
        }
        Spacer(Modifier.height(4.dp))
        val details = buildList {
            add(stringResource(R.string.ac_fan) + " " + label(s.fan, ::fanName))
            if (view.swings.isNotEmpty()) add(stringResource(R.string.ac_swing) + " " + label(s.swing.orEmpty(), ::swingName))
        }
        Text(
            details.joinToString("  ·  "),
            color = RemoteColors.KeyText.copy(alpha = 0.75f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ac_details").alpha(alpha),
        )
        if (!s.power) {
            Text(
                stringResource(R.string.ac_power_off),
                color = RemoteColors.KeyText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("ac_off").padding(top = 4.dp),
            )
        }
        // Platz für die Zeile Außentemperatur (folgt mit Task 34).
        Box(Modifier.height(18.dp).testTag("ac_outdoor_slot"))
    }
}

/** Große −/+-Taste: Tippen ein Schritt, Halten wiederholt etwa alle [AC_HOLD_REPEAT_MS]; Wischen pausiert. */
@Composable
private fun RowScope.StepKey(key: String, text: String, description: String, enabled: Boolean, onStep: () -> Unit) {
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    PressableKey(
        key = key,
        enabled = enabled,
        modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = description },
        shape = KeyShape,
        brush = KeyBrush,
        onTap = { step() },
        onLongPress = null,
        onPressStart = {
            val job = scope.launch {
                while (true) {
                    step()
                    delay(AC_HOLD_REPEAT_MS)
                }
            }
            ({ job.cancel(); Unit })
        },
        holdsGesture = true,
    ) {
        Text(text, color = RemoteColors.KeyText, fontSize = 40.sp, fontWeight = FontWeight.Light)
    }
}

/** Taste mit Symbol, kleiner Überschrift und aktuellem Wert. */
@Composable
private fun SettingKey(
    key: String,
    title: String,
    value: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    PressableKey(
        key = key,
        enabled = enabled,
        modifier = modifier.fillMaxHeight(),
        shape = KeyShape,
        brush = KeyBrush,
        onTap = onTap,
        onLongPress = null,
        onPressStart = null,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp), tint = RemoteColors.KeyText)
            Text(title, color = RemoteColors.KeyText.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
            Text(
                value,
                color = RemoteColors.KeyText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun RowScope.SettingKey(
    key: String,
    title: String,
    value: String,
    icon: ImageVector,
    enabled: Boolean,
    onTap: () -> Unit,
) = SettingKey(key, title, value, icon, enabled, Modifier.weight(1f), onTap)

/** Modus: öffnet die Auswahl der vorhandenen Modi. */
@Composable
private fun RowScope.ModeKey(view: AcView, onMode: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.weight(1f).fillMaxHeight()) {
        SettingKey(
            key = "ac_mode",
            title = stringResource(R.string.ac_mode),
            value = label(view.state.mode, ::modeName),
            icon = modeIcon(view.state.mode),
            enabled = view.modes.size > 1,
            modifier = Modifier.fillMaxWidth(),
        ) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            view.modes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(label(mode, ::modeName)) },
                    leadingIcon = { Icon(modeIcon(mode), null, Modifier.size(20.dp)) },
                    onClick = {
                        open = false
                        onMode(mode)
                    },
                    modifier = Modifier.testTag("ac_mode_$mode"),
                )
            }
        }
    }
}
