package de.hotelremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Farben aus docs/superpowers/specs/mockups/remote.html (jeweils oben -> unten im Verlauf). */
object RemoteColors {
    val BackgroundTop = Color(0xFF2E3035)
    val BackgroundBottom = Color(0xFF1D1E21)
    val KeyTop = Color(0xFF484B51)
    val KeyBottom = Color(0xFF383A3F)
    val KeyShadow = Color(0xFF141517)
    val KeyText = Color(0xFFECEEF0)
    val SourceTop = Color(0xFF3A67B8)
    val SourceBottom = Color(0xFF2C5199)
    val PowerTop = Color(0xFFCF3B31)
    val PowerBottom = Color(0xFFA72C24)
    val ColorRed = Color(0xFFC8352D)
    val ColorGreen = Color(0xFF2F9E44)
    val ColorYellow = Color(0xFFD4B417)
    val ColorBlue = Color(0xFF2F62C8)
    val YesTop = Color(0xFF2F9E44)
    val YesBottom = Color(0xFF25803A)
}

private val RemoteColorScheme = darkColorScheme(
    primary = RemoteColors.SourceTop,
    onPrimary = RemoteColors.KeyText,
    background = RemoteColors.BackgroundBottom,
    onBackground = RemoteColors.KeyText,
    surface = RemoteColors.BackgroundBottom,
    onSurface = RemoteColors.KeyText,
    surfaceVariant = RemoteColors.KeyBottom,
    onSurfaceVariant = RemoteColors.KeyText,
    error = RemoteColors.PowerTop,
)

@Composable
fun HotelRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RemoteColorScheme, content = content)
}
