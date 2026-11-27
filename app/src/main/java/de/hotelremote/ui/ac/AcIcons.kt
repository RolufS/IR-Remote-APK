package de.hotelremote.ui.ac

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Symbole des Klima-Bildschirms als eigene Vektoren (24 × 24, weiß; eingefärbt über `tint`). */
internal object AcIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> ImageVector.Builder): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).block().build()

    private fun ImageVector.Builder.stroke(width: Float = 2f, pathBuilder: PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )

    private fun ImageVector.Builder.fill(pathBuilder: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.White), pathBuilder = pathBuilder)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    /** Schneeflocke: drei Achsen mit Spitzen. */
    val Cool: ImageVector by lazy {
        icon("AcCool") {
            for (angle in listOf(0f, 60f, 120f)) {
                group(rotate = angle, pivotX = 12f, pivotY = 12f) {
                    stroke {
                        moveTo(12f, 2.5f)
                        verticalLineTo(21.5f)
                        moveTo(9.5f, 4.5f)
                        lineTo(12f, 7f)
                        lineTo(14.5f, 4.5f)
                        moveTo(9.5f, 19.5f)
                        lineTo(12f, 17f)
                        lineTo(14.5f, 19.5f)
                    }
                }
            }
            this
        }
    }

    /** Sonne: Kreis mit acht Strahlen. */
    val Heat: ImageVector by lazy {
        icon("AcHeat") {
            stroke { circle(12f, 12f, 4.2f) }
            for (i in 0 until 8) {
                group(rotate = i * 45f, pivotX = 12f, pivotY = 12f) {
                    stroke {
                        moveTo(12f, 2.5f)
                        verticalLineTo(5f)
                    }
                }
            }
            this
        }
    }

    /** Tropfen. */
    val Dry: ImageVector by lazy {
        icon("AcDry") {
            fill {
                moveTo(12f, 2.5f)
                curveTo(12f, 2.5f, 5.5f, 10f, 5.5f, 14.5f)
                arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 18.5f, y1 = 14.5f)
                curveTo(18.5f, 10f, 12f, 2.5f, 12f, 2.5f)
                close()
            }
            this
        }
    }

    /** Ventilator: drei Flügel um eine Nabe. */
    val Fan: ImageVector by lazy {
        icon("AcFan") {
            for (angle in listOf(0f, 120f, 240f)) {
                group(rotate = angle, pivotX = 12f, pivotY = 12f) {
                    fill {
                        moveTo(12f, 12f)
                        curveTo(8.5f, 9.5f, 8.5f, 3f, 12.5f, 2.5f)
                        curveTo(16.5f, 2.5f, 16f, 8.5f, 12f, 12f)
                        close()
                    }
                }
            }
            fill { circle(12f, 12f, 1.8f) }
            this
        }
    }

    /** Buchstabe A im Kreis. */
    val Auto: ImageVector by lazy {
        icon("AcAuto") {
            stroke(1.8f) { circle(12f, 12f, 9.5f) }
            stroke {
                moveTo(8f, 17f)
                lineTo(12f, 7f)
                lineTo(16f, 17f)
                moveTo(9.6f, 13.5f)
                horizontalLineTo(14.4f)
            }
            this
        }
    }

    /** Pfeil hoch und Pfeil runter (Heizen oder Kühlen nach Bedarf). */
    val HeatCool: ImageVector by lazy {
        icon("AcHeatCool") {
            stroke {
                moveTo(8f, 20f)
                verticalLineTo(4f)
                moveTo(4.5f, 7.5f)
                lineTo(8f, 4f)
                lineTo(11.5f, 7.5f)
                moveTo(16f, 4f)
                verticalLineTo(20f)
                moveTo(12.5f, 16.5f)
                lineTo(16f, 20f)
                lineTo(19.5f, 16.5f)
            }
            this
        }
    }

    /** Unbekannter Modus: Ring mit Punkt. */
    val OtherMode: ImageVector by lazy {
        icon("AcOtherMode") {
            stroke { circle(12f, 12f, 8f) }
            fill { circle(12f, 12f, 2.5f) }
            this
        }
    }

    /** Swing: Bogen mit Pfeilspitzen an beiden Enden. */
    val Swing: ImageVector by lazy {
        icon("AcSwing") {
            stroke {
                moveTo(4f, 9f)
                quadTo(12f, 23f, 20f, 9f)
                moveTo(3f, 13f)
                lineTo(4f, 9f)
                lineTo(8f, 10f)
                moveTo(21f, 13f)
                lineTo(20f, 9f)
                lineTo(16f, 10f)
            }
            fill { circle(12f, 5f, 2f) }
            this
        }
    }
}
