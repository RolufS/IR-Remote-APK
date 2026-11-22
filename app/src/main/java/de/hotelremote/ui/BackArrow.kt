package de.hotelremote.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Pfeil für „Zurück“: zeigt bei Rechts-nach-links-Sprachen (Arabisch) nach rechts. */
@Composable
fun backArrow(): String = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "→" else "←"
