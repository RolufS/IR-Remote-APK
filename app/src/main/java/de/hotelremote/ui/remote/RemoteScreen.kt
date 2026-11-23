package de.hotelremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hotelremote.Keys
import de.hotelremote.R
import de.hotelremote.data.SavedRemote
import de.hotelremote.ui.automation.RecordingBar
import de.hotelremote.ui.automation.RecordingBarState
import de.hotelremote.ui.theme.RemoteColors

enum class RemoteMenu { EXTRA_KEYS, AUTOMATIONS, WIZARD, LANGUAGE, ABOUT }

private val SideMargin = 12.dp
private val Gap = 8.dp
private val OkBrush = Brush.verticalGradient(listOf(Color(0xFF2C2E32), Color(0xFF25272A)))

@Composable
fun RemoteScreen(
    remote: SavedRemote,
    remotes: List<SavedRemote>,
    irAvailable: Boolean,
    handler: KeyHandler,
    onSwitch: (String) -> Unit,
    onOpenList: () -> Unit,
    onMenu: (RemoteMenu) -> Unit,
    onQuick: () -> Unit = {},
    /** Während einer Aufnahme: Leiste unter der Kopfzeile; ☰, ⚡ und Wechseln sind dann gesperrt. */
    recording: RecordingBarState? = null,
) {
    val keys = remote.keys.keys
    val inputRow = showInputRow(keys)
    val numberWeight = if (inputRow) 0.85f else 1f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RemoteColors.BackgroundTop, RemoteColors.BackgroundBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = SideMargin, end = SideMargin, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        if (!irAvailable) NoIrBanner()
        Header(remote, remotes, recording != null, onSwitch, onOpenList, onMenu, onQuick)
        recording?.let { RecordingBar(it) }
        // Tastenfeld immer LTR, auch bei Arabisch: links/rechts, Wippen und Steuerkreuz bleiben an ihrem Platz.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            KeyRow(1.25f) {
                TextKey(Keys.SOURCE, "SOURCE", keys, handler, brush = SourceBrush)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    PowerButton(keys, handler, Modifier.fillMaxHeight().aspectRatio(1f)) {
                        Icon(PowerIcon, stringResource(R.string.wiz_key_power), Modifier.size(26.dp), tint = Color.White)
                    }
                }
                TextKey(Keys.MUTE, "MUTE", keys, handler)
            }
            if (inputRow) {
                KeyRow(1.05f) {
                    Keys.HDMI.forEachIndexed { i, key ->
                        KeyButton(key, key in keys, handler, Modifier.weight(1f).fillMaxHeight()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Label("HDMI", 10.sp)
                                Label("${i + 1}", 15.sp)
                            }
                        }
                    }
                }
            }
            listOf("123", "456", "789").forEach { digits ->
                KeyRow(numberWeight) {
                    digits.forEach { d -> TextKey("DIGIT_$d", "$d", keys, handler, fontSize = 18.sp) }
                }
            }
            KeyRow(numberWeight) {
                TextKey(Keys.LIST, "LIST", keys, handler, fontSize = 11.sp)
                TextKey(Keys.DIGIT_0, "0", keys, handler, fontSize = 18.sp)
                TextKey(Keys.PRE_CH, "PRE-CH", keys, handler, fontSize = 11.sp)
            }
            KeyRow(2.2f) {
                Rocker("VOL", "+", "−", Keys.VOL_UP, Keys.VOL_DOWN, keys, handler, Modifier.weight(1f))
                Column(Modifier.weight(1.35f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
                    MenuKey(Keys.MENU, "MENU", keys, handler, Modifier.weight(1f))
                    MenuKey(Keys.HOME, "HOME", keys, handler, Modifier.weight(1f))
                }
                Rocker("CH", "▲", "▼", Keys.CH_UP, Keys.CH_DOWN, keys, handler, Modifier.weight(1f))
            }
            Box(
                Modifier.fillMaxWidth().weight(if (inputRow) 3f else 3.4f),
                contentAlignment = Alignment.Center,
            ) {
                DPad(keys, handler)
            }
            KeyRow(0.9f) {
                TextKey(Keys.BACK, "BACK", keys, handler)
                TextKey(Keys.INFO, "INFO", keys, handler)
                TextKey(Keys.EXIT, "EXIT", keys, handler)
            }
            Row(Modifier.fillMaxWidth().height(20.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                ColorKey(Keys.RED, RemoteColors.ColorRed, R.string.cd_color_red, keys, handler)
                ColorKey(Keys.GREEN, RemoteColors.ColorGreen, R.string.cd_color_green, keys, handler)
                ColorKey(Keys.YELLOW, RemoteColors.ColorYellow, R.string.cd_color_yellow, keys, handler)
                ColorKey(Keys.BLUE, RemoteColors.ColorBlue, R.string.cd_color_blue, keys, handler)
            }
        }
    }
}

@Composable
private fun ColumnScope.KeyRow(
    weight: Float,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().weight(weight),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        content = content,
    )
}

@Composable
private fun Label(text: String, size: TextUnit, color: Color = RemoteColors.KeyText) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        textAlign = TextAlign.Center,
    )
}

/** Taste in einer Zeile; fehlt der Code, wird sie abgeblendet und ist nicht klickbar. */
@Composable
private fun RowScope.TextKey(
    key: String,
    label: String,
    keys: Set<String>,
    handler: KeyHandler,
    fontSize: TextUnit = 12.sp,
    brush: Brush = KeyBrush,
) {
    KeyButton(key, key in keys, handler, Modifier.weight(1f).fillMaxHeight(), brush = brush) {
        Label(label, fontSize)
    }
}

@Composable
private fun MenuKey(key: String, label: String, keys: Set<String>, handler: KeyHandler, modifier: Modifier) {
    KeyButton(key, key in keys, handler, modifier.fillMaxWidth()) { Label(label, 12.sp) }
}

@Composable
private fun RowScope.ColorKey(
    key: String,
    color: Color,
    description: Int,
    keys: Set<String>,
    handler: KeyHandler,
) {
    val text = stringResource(description)
    KeyButton(
        key,
        key in keys,
        handler,
        Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = text },
        shape = RoundedCornerShape(8.dp),
        brush = Brush.verticalGradient(listOf(color, color)),
    ) {}
}

/** Eine Wippe: ein Element, zwei einzeln drückbare Hälften. */
@Composable
private fun RowScope.Rocker(
    name: String,
    upLabel: String,
    downLabel: String,
    upKey: String,
    downKey: String,
    keys: Set<String>,
    handler: KeyHandler,
    modifier: Modifier,
) {
    Box(modifier.fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(KeyBrush)) {
        Column(Modifier.fillMaxSize()) {
            KeyButton(upKey, upKey in keys, handler, Modifier.weight(1f).fillMaxWidth(), RectangleShape, null) {
                Label(upLabel, 20.sp)
            }
            KeyButton(downKey, downKey in keys, handler, Modifier.weight(1f).fillMaxWidth(), RectangleShape, null) {
                Label(downLabel, 20.sp)
            }
        }
        Text(
            name,
            color = RemoteColors.KeyText.copy(alpha = 0.7f),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Großes Steuerkreuz: vier Pfeilflächen und OK in der Mitte. */
@Composable
private fun DPad(keys: Set<String>, handler: KeyHandler) {
    Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(CircleShape).background(KeyBrush)) {
        @Composable
        fun Arrow(key: String, text: String, align: Alignment, w: Float, h: Float) {
            KeyButton(
                key,
                key in keys,
                handler,
                Modifier.align(align).fillMaxWidth(w).fillMaxHeight(h),
                RectangleShape,
                null,
            ) { Label(text, 14.sp) }
        }
        Arrow(Keys.UP, "▲", Alignment.TopCenter, 0.4f, 0.31f)
        Arrow(Keys.DOWN, "▼", Alignment.BottomCenter, 0.4f, 0.31f)
        Arrow(Keys.LEFT, "◀", Alignment.CenterStart, 0.31f, 0.4f)
        Arrow(Keys.RIGHT, "▶", Alignment.CenterEnd, 0.31f, 0.4f)
        KeyButton(
            Keys.OK,
            Keys.OK in keys,
            handler,
            Modifier.align(Alignment.Center).fillMaxWidth(0.38f).aspectRatio(1f),
            CircleShape,
            OkBrush,
        ) { Label("OK", 14.sp) }
    }
}

/** Deutlicher Hinweis bei Handys ohne IR-Sender (Fernbedienung und Assistent). */
@Composable
internal fun NoIrBanner() {
    Text(
        stringResource(R.string.no_ir),
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .testTag("no_ir_banner")
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RemoteColors.PowerBottom)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Header(
    remote: SavedRemote,
    remotes: List<SavedRemote>,
    recording: Boolean,
    onSwitch: (String) -> Unit,
    onOpenList: () -> Unit,
    onMenu: (RemoteMenu) -> Unit,
    onQuick: () -> Unit,
) {
    var switchOpen by remember { mutableStateOf(false) }
    var titleWidth by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Links ein Platzhalter in Symbolbreite, damit links und rechts (⚡ + ⋮) gleich breit sind
        // und der Titel genau in der Bildschirmmitte steht.
        Row {
            HeaderIcon("☰", stringResource(R.string.cd_open_list), "header_list", enabled = !recording, onClick = onOpenList)
            Box(Modifier.height(40.dp).aspectRatio(1f))
        }
        // Titel nimmt den Platz zwischen den Symbolen und wird bei Bedarf gekürzt.
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // Anker ist der Titel selbst; das Menü (mindestens so breit wie der Titel) sitzt mittig darunter,
            // die Namen darin sind ebenfalls zentriert.
            Box {
                Text(
                    remote.title + " ▾",
                    color = RemoteColors.KeyText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .testTag("header_title")
                        .onSizeChanged { titleWidth = it.width }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !recording) { switchOpen = true }
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                if (switchOpen) {
                    // Eigenes Popup statt DropdownMenu: der Positionsgeber kennt die gemessene Menübreite schon beim
                    // ersten Bild, das Menü öffnet daher sofort mittig (kein Messen-und-Verschieben).
                    Popup(
                        popupPositionProvider = CenteredBelowAnchor,
                        onDismissRequest = { switchOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Surface(
                            shape = MenuDefaults.shape,
                            color = MenuDefaults.containerColor,
                            tonalElevation = MenuDefaults.TonalElevation,
                            shadowElevation = MenuDefaults.ShadowElevation,
                        ) {
                            Column(
                                Modifier
                                    .testTag("switch_menu")
                                    .widthIn(min = maxOf(112.dp, with(LocalDensity.current) { titleWidth.toDp() }), max = 280.dp)
                                    .width(IntrinsicSize.Max)
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp),
                            ) {
                                remotes.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text(r.title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                        onClick = {
                                            switchOpen = false
                                            onSwitch(r.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        HeaderButton(stringResource(R.string.cd_quick), "header_quick", enabled = !recording, onClick = onQuick) {
            Icon(BoltIcon, null, Modifier.size(20.dp), tint = RemoteColors.KeyText.copy(alpha = 0.75f))
        }
        Box {
            HeaderIcon("⋮", stringResource(R.string.cd_more_menu), "header_menu") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // Während der Aufnahme nur „Weitere Tasten“: die anderen Einträge verließen die Fernbedienung.
                listOf(
                    R.string.menu_extra to RemoteMenu.EXTRA_KEYS,
                    R.string.menu_automations to RemoteMenu.AUTOMATIONS,
                    R.string.menu_wizard to RemoteMenu.WIZARD,
                    R.string.menu_language to RemoteMenu.LANGUAGE,
                    R.string.menu_about to RemoteMenu.ABOUT,
                ).filter { !recording || it.second == RemoteMenu.EXTRA_KEYS }.forEach { (label, menu) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            menuOpen = false
                            onMenu(menu)
                        },
                    )
                }
            }
        }
    }
}

/** Waagerechter Versatz (px), der ein Menü der Breite [menu] mittig unter einen Anker der Breite [anchor] setzt. */
internal fun centeredMenuOffset(anchor: Int, menu: Int): Int = (anchor - menu) / 2

/** Setzt ein Popup mittig unter den Anker, im Fenster gehalten; passt es unten nicht hin, darüber. */
internal object CenteredBelowAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + centeredMenuOffset(anchorBounds.width, popupContentSize.width))
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) below else {
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

@Composable
private fun HeaderIcon(symbol: String, description: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    HeaderButton(description, tag, enabled, onClick) {
        Text(symbol, color = RemoteColors.KeyText.copy(alpha = 0.75f), fontSize = 18.sp)
    }
}

@Composable
private fun HeaderButton(
    description: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .testTag(tag)
            .height(40.dp)
            .aspectRatio(1f)
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
