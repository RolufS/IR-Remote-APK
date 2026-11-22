package de.hotelremote.ui.remote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Blitz für den Knopf „Automationen und Sleep-Timer“ als Vektor (das Emoji wäre bunt und je nach Schrift verschieden). */
val BoltIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bolt",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.White)) {
        moveTo(13.5f, 2f)
        lineTo(4.5f, 13.5f)
        lineTo(11f, 13.5f)
        lineTo(9.5f, 22f)
        lineTo(19.5f, 9.5f)
        lineTo(13f, 9.5f)
        close()
    }.build()
}
