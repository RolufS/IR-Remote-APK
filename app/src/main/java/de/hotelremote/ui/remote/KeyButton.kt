package de.hotelremote.ui.remote

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.hotelremote.Keys
import de.hotelremote.ui.theme.RemoteColors

val KeyShape: Shape = RoundedCornerShape(14.dp)

val KeyBrush: Brush = Brush.verticalGradient(listOf(RemoteColors.KeyTop, RemoteColors.KeyBottom))
val SourceBrush: Brush = Brush.verticalGradient(listOf(RemoteColors.SourceTop, RemoteColors.SourceBottom))
val PowerBrush: Brush = Brush.verticalGradient(listOf(RemoteColors.PowerTop, RemoteColors.PowerBottom))

private const val DISABLED_ALPHA = 0.35f

/** Wie stark eine gedrückte Taste abgedunkelt wird (Schwarz-Deckkraft). */
internal const val PRESS_SHADE = 0.3f

/** Dauer, in der die Taste nach dem Loslassen zur Originalfarbe zurückblendet. */
internal const val PRESS_FADE_MS = 250

/** Aktuelle Abdunkelung einer Taste – nur für Tests sichtbar gemacht. */
internal val PressShade = SemanticsPropertyKey<Float>("PressShade")
private var SemanticsPropertyReceiver.pressShadeValue by PressShade

/** Tastenfläche: Tag `key_<key>`, Alpha 0,35 und "disabled" für Semantik, wenn die Taste fehlt. */
@Composable
private fun PressableKey(
    key: String,
    enabled: Boolean,
    modifier: Modifier,
    shape: Shape,
    brush: Brush?,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
    onPressStart: (() -> (() -> Unit)?)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val pressStart by rememberUpdatedState(onPressStart)
    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // Gedrückt: Fläche sofort abdunkeln, nach dem Loslassen weich zur Originalfarbe zurück.
    val pressShade = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    var surface = modifier
        .testTag("key_$key")
        .alpha(if (enabled) 1f else DISABLED_ALPHA)
    if (brush != null) {
        surface = surface
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius
                    ?: CornerRadius(size.minDimension / 2)
                drawRoundRect(
                    RemoteColors.KeyShadow,
                    topLeft = Offset(0f, 2.dp.toPx()),
                    size = size,
                    cornerRadius = radius,
                )
            }
            .clip(shape)
            .background(brush)
    }
    Box(
        modifier = surface
            // Abdunkeln über der Tastenfläche, aber unter der Beschriftung. Der Umriss folgt [shape];
            // Wippen-Hälften und Pfeilflächen werden zusätzlich vom Elternelement zugeschnitten.
            .drawWithContent {
                val shade = pressShade.value
                if (shade > 0f) {
                    drawOutline(
                        shape.createOutline(size, layoutDirection, this),
                        Color.Black.copy(alpha = shade),
                    )
                }
                drawContent()
            }
            .semantics(mergeDescendants = true) {
                pressShadeValue = pressShade.value
                role = Role.Button
                if (enabled) onClick {
                    tap()
                    true
                } else disabled()
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        haptic()
                        scope.launch { pressShade.snapTo(PRESS_SHADE) }
                        val release = pressStart?.invoke()
                        try {
                            tryAwaitRelease()
                        } finally {
                            release?.invoke()
                            scope.launch { pressShade.animateTo(0f, tween(PRESS_FADE_MS)) }
                        }
                    },
                    onTap = if (onPressStart == null) {
                        { tap() }
                    } else {
                        null
                    },
                    onLongPress = if (onLongPress != null) {
                        { longPress?.invoke() }
                    } else {
                        null
                    },
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Normale Taste. Nicht wiederholende Tasten senden beim Herunterdrücken, Tasten aus
 * [Keys.REPEATING] wiederholen, bis losgelassen wird.
 */
@Composable
fun KeyButton(
    key: String,
    enabled: Boolean,
    handler: KeyHandler,
    modifier: Modifier = Modifier,
    shape: Shape = KeyShape,
    brush: Brush? = KeyBrush,
    sendOnPress: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentHandler by rememberUpdatedState(handler)
    val repeating = key in Keys.REPEATING
    PressableKey(
        key = key,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        brush = brush,
        onTap = { currentHandler.press(key) },
        onLongPress = null,
        onPressStart = when {
            repeating -> {
                {
                    val job = currentHandler.hold(key)
                    ({ job?.cancel(); Unit })
                }
            }
            sendOnPress -> {
                {
                    currentHandler.press(key)
                    null
                }
            }
            else -> null
        },
        content = content,
    )
}

/** Power: Tippen und langes Drücken senden je nach [powerAction] eine andere Taste. */
@Composable
fun PowerButton(
    keys: Set<String>,
    handler: KeyHandler,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentHandler by rememberUpdatedState(handler)
    val currentKeys by rememberUpdatedState(keys)
    PressableKey(
        key = Keys.POWER,
        enabled = powerAction(keys, false) != null,
        modifier = modifier,
        shape = CircleShape,
        brush = PowerBrush,
        onTap = { powerAction(currentKeys, false)?.let(currentHandler::press) },
        onLongPress = { powerAction(currentKeys, true)?.let(currentHandler::press) },
        onPressStart = null,
        content = content,
    )
}
