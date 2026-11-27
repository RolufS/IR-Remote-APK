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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Wischbereich zwischen TV und Klima. Zählt gehaltene Tasten (Lautstärke, Kanal, Pfeile, Klima −/+), damit das
 * Wischen pausiert, solange eine davon gehalten wird: ein Verrutschen des Fingers blättert dann nicht.
 */
@Stable
class SwipeLock {
    var holds by mutableIntStateOf(0)
        internal set

    val locked get() = holds > 0
}

/** Sperre des umgebenden Wischbereichs (null = keiner; Tasten senden dann wie gewohnt beim Herunterdrücken). */
val LocalSwipeLock = staticCompositionLocalOf<SwipeLock?> { null }

/**
 * Tastenfläche: Tag `key_<key>`, Alpha 0,35 und "disabled" für Semantik, wenn die Taste fehlt.
 * Gemeinsam für TV- und Klima-Tasten (Schatten, Verlauf, Abdunkeln beim Drücken).
 *
 * Im Wischbereich ([LocalSwipeLock] gesetzt) wird erst entschieden, ob die Geste ein Wischen ist: Tippen löst
 * [onTap] beim Loslassen aus, aber nur ohne Bewegung über die Touch-Slop. Haltetasten ([holdsGesture]) starten
 * [onPressStart] erst nach der Long-Press-Zeit und sperren ab dann das Wischen; [onLongPress] ebenso nach dieser Zeit.
 * Bewegt sich der Finger vorher über die Slop, passiert nichts und der Pager blättert.
 */
@Composable
internal fun PressableKey(
    key: String,
    enabled: Boolean,
    modifier: Modifier,
    shape: Shape,
    brush: Brush?,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
    onPressStart: (() -> (() -> Unit)?)?,
    holdsGesture: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val swipeLock = LocalSwipeLock.current
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
            .pointerInput(enabled, swipeLock) {
                if (!enabled) return@pointerInput
                if (swipeLock != null) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        haptic()
                        scope.launch { pressShade.snapTo(PRESS_SHADE) }
                        var locked = false
                        var release: (() -> Unit)? = null
                        try {
                            val holdStart = pressStart.takeIf { holdsGesture }
                            val long = longPress
                            val tapped = if (holdStart != null || long != null) {
                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    awaitTapOrSlop(down, viewConfiguration.touchSlop)
                                }
                            } else {
                                awaitTapOrSlop(down, viewConfiguration.touchSlop)
                            }
                            when (tapped) {
                                true -> tap()
                                false -> Unit
                                null -> {
                                    if (holdStart != null) {
                                        swipeLock.holds++
                                        locked = true
                                        release = holdStart()
                                    } else {
                                        long?.invoke()
                                    }
                                    do {
                                        val event = awaitPointerEvent()
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        } finally {
                            if (locked) swipeLock.holds--
                            release?.invoke()
                            scope.launch { pressShade.animateTo(0f, tween(PRESS_FADE_MS)) }
                        }
                    }
                    return@pointerInput
                }
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
 * Wartet auf Loslassen (true = Tippen) oder Bewegung über [slop] bzw. Übernahme durch andere (false).
 */
private suspend fun AwaitPointerEventScope.awaitTapOrSlop(down: PointerInputChange, slop: Float): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return false
        if (change.isConsumed) return false
        if (!change.pressed) {
            change.consume()
            return true
        }
        if ((change.position - down.position).getDistance() > slop) return false
    }
}

/**
 * Normale Taste. Nicht wiederholende Tasten senden beim Herunterdrücken (im Wischbereich beim Loslassen),
 * Tasten aus [Keys.REPEATING] wiederholen, bis losgelassen wird (im Wischbereich: Tippen sendet einmal,
 * Halten startet die Wiederholung nach der Long-Press-Zeit).
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
        holdsGesture = repeating,
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
