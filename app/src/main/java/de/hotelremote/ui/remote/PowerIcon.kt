package de.hotelremote.ui.remote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ein/Aus-Symbol als Vektor: offener Kreis mit senkrechtem Strich. Ersetzt das Zeichen U+23FB,
 * das in vielen Systemschriften (z. B. HyperOS) fehlt und dort leer bleibt.
 */
val PowerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Power",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        // Strich von oben bis zur Kreismitte.
        moveTo(12f, 2.5f)
        verticalLineTo(11.5f)
        // Kreis um (12, 13) mit Radius 8, oben offen (je 40° neben der Senkrechten).
        moveTo(6.86f, 6.87f)
        arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 17.14f, y1 = 6.87f)
    }.build()
}
