package io.github.halilozel1903.deezer.sample

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons drawn as vectors, so the sample needs no icon library. Tinted by the Icon that shows them. */
object AppIcons {
    private fun stroke(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
            .build()

    private fun fill(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black), pathBuilder = block)
            .build()

    val Play: ImageVector = fill("play") {
        moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
    }

    val Pause: ImageVector = fill("pause") {
        moveTo(6.5f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6.5f, 19f); close()
        moveTo(14f, 5f); lineTo(17.5f, 5f); lineTo(17.5f, 19f); lineTo(14f, 19f); close()
    }

    val Back: ImageVector = stroke("back") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }

    val Close: ImageVector = stroke("close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val Search: ImageVector = stroke("search") {
        moveTo(10.5f, 4f)
        arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 10.49f, y1 = 4f)
        close()
        moveTo(15.2f, 15.2f); lineTo(20f, 20f)
    }

    /** Bars of a chart. */
    val Charts: ImageVector = stroke("charts") {
        moveTo(5f, 20f); lineTo(5f, 13f)
        moveTo(10f, 20f); lineTo(10f, 6f)
        moveTo(15f, 20f); lineTo(15f, 10f)
        moveTo(20f, 20f); lineTo(20f, 4f)
    }

    /** A car seen from the front. */
    val Car: ImageVector = stroke("car") {
        moveTo(4f, 17f); lineTo(4f, 12f); lineTo(6.5f, 6.5f); lineTo(17.5f, 6.5f); lineTo(20f, 12f); lineTo(20f, 17f); close()
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(7.5f, 14.5f); lineTo(7.6f, 14.5f)
        moveTo(16.5f, 14.5f); lineTo(16.6f, 14.5f)
        moveTo(6f, 17f); lineTo(6f, 19.5f)
        moveTo(18f, 17f); lineTo(18f, 19.5f)
    }

    /** A star, for editor's picks. */
    val Star: ImageVector = stroke("star") {
        moveTo(12f, 3.5f); lineTo(14.6f, 8.9f); lineTo(20.5f, 9.6f); lineTo(16.1f, 13.6f); lineTo(17.3f, 19.5f)
        lineTo(12f, 16.6f); lineTo(6.7f, 19.5f); lineTo(7.9f, 13.6f); lineTo(3.5f, 9.6f); lineTo(9.4f, 8.9f); close()
    }

    /** Four tiles, for genres. */
    val Grid: ImageVector = stroke("grid") {
        moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
        moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
        moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
        moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
    }

    /** Radio waves. */
    val Radio: ImageVector = stroke("radio") {
        moveTo(12f, 11f)
        arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 11.99f, y1 = 11f)
        moveTo(8f, 8f); arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 8f, y1 = 16f)
        moveTo(16f, 8f); arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 16f, y1 = 16f)
        moveTo(5f, 5f); arcTo(9.5f, 9.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 5f, y1 = 19f)
        moveTo(19f, 5f); arcTo(9.5f, 9.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 19f)
    }

    /** A chevron, for browsable rows. */
    val Chevron: ImageVector = stroke("chevron") {
        moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
    }
}
