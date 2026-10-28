package pt.hexa.empresarial.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import pt.hexa.empresarial.domain.model.CenterSymbol
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Geometric symbols drawn with vectors (centre of the orbital drawer and groups). */
fun DrawScope.drawCenterSymbol(symbol: CenterSymbol, center: Offset, radius: Float, brush: Brush, accent: Color) {
    val w = radius * 0.06f
    when (symbol) {
        CenterSymbol.NETWORK -> {
            // A globe-like web of nodes, as in the reference composition.
            drawCircle(brush, radius, center, style = Stroke(w))
            val nodes = (0 until 9).map { i ->
                val a = i * 2.39996f
                val r = radius * (0.25f + 0.65f * ((i * 37 % 9) / 9f))
                Offset(center.x + r * cos(a), center.y + r * sin(a))
            }
            for (i in nodes.indices) for (j in i + 1 until nodes.size) {
                if ((i * 7 + j * 3) % 4 == 0) drawLine(brush, nodes[i], nodes[j], w * 0.7f)
            }
            for (i in 0 until 3) {
                drawArc(brush, -60f + i * 40f, 220f, false, Offset(center.x - radius * (0.4f + i * 0.2f), center.y - radius), androidx.compose.ui.geometry.Size(radius * (0.8f + i * 0.4f), radius * 2f), style = Stroke(w * 0.7f))
            }
            nodes.forEach { drawCircle(brush, w * 1.6f, it) }
        }
        CenterSymbol.HEXAGON -> {
            drawPath(polygon(center, radius * 0.92f, 6, -PI / 2), brush, style = Stroke(w * 1.4f))
            drawPath(polygon(center, radius * 0.5f, 6, -PI / 2), brush, style = Stroke(w))
            drawCircle(accent, w * 2f, center)
        }
        CenterSymbol.DIAMOND -> {
            drawPath(polygon(center, radius * 0.9f, 4, -PI / 2), brush, style = Stroke(w * 1.4f))
            drawPath(polygon(center, radius * 0.45f, 4, -PI / 2), brush)
        }
        CenterSymbol.ORBIT -> {
            drawCircle(brush, radius * 0.9f, center, style = Stroke(w))
            drawCircle(brush, radius * 0.55f, center, style = Stroke(w))
            drawCircle(brush, radius * 0.18f, center)
            drawCircle(accent, w * 2.2f, Offset(center.x + radius * 0.9f * cos(-0.8f), center.y + radius * 0.9f * sin(-0.8f)))
        }
        CenterSymbol.MONOGRAM -> {
            drawPath(polygon(center, radius * 0.92f, 6, -PI / 2), brush, style = Stroke(w * 1.2f))
            val h = radius * 0.42f
            drawLine(brush, Offset(center.x - h * 0.6f, center.y - h), Offset(center.x - h * 0.6f, center.y + h), w * 2f, StrokeCap.Round)
            drawLine(brush, Offset(center.x + h * 0.6f, center.y - h), Offset(center.x + h * 0.6f, center.y + h), w * 2f, StrokeCap.Round)
            drawLine(brush, Offset(center.x - h * 0.6f, center.y), Offset(center.x + h * 0.6f, center.y), w * 2f, StrokeCap.Round)
        }
    }
}

fun polygon(center: Offset, radius: Float, sides: Int, rotation: Double): Path = Path().apply {
    for (i in 0 until sides) {
        val a = rotation + 2 * PI * i / sides
        val x = center.x + radius * cos(a).toFloat()
        val y = center.y + radius * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Small UI glyphs drawn in a [size]-sized box. */
enum class Glyph { SEARCH, GRID, ORBIT, SETTINGS, CLOSE, PLUS, EDIT, WIDGET, GROUP, CHECK, TRASH, PAGE }

fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val s = size.minDimension
    val o = Offset((size.width - s) / 2f, (size.height - s) / 2f)
    fun p(x: Float, y: Float) = Offset(o.x + x * s, o.y + y * s)
    val w = s * 0.09f
    when (glyph) {
        Glyph.SEARCH -> {
            drawCircle(color, s * 0.26f, p(0.43f, 0.43f), style = Stroke(w))
            drawLine(color, p(0.62f, 0.62f), p(0.86f, 0.86f), w, StrokeCap.Round)
        }
        Glyph.GRID -> for (i in 0..2) for (j in 0..2) {
            drawRoundRect(color, p(0.14f + i * 0.26f, 0.14f + j * 0.26f), androidx.compose.ui.geometry.Size(s * 0.18f, s * 0.18f), androidx.compose.ui.geometry.CornerRadius(s * 0.04f))
        }
        Glyph.ORBIT -> {
            drawCircle(color, s * 0.4f, p(0.5f, 0.5f), style = Stroke(w * 0.7f))
            drawCircle(color, s * 0.22f, p(0.5f, 0.5f), style = Stroke(w * 0.7f))
            drawCircle(color, s * 0.08f, p(0.5f, 0.5f))
            drawCircle(color, s * 0.07f, p(0.5f + 0.4f * 0.7071f, 0.5f - 0.4f * 0.7071f))
        }
        Glyph.SETTINGS -> {
            drawCircle(color, s * 0.18f, p(0.5f, 0.5f), style = Stroke(w))
            for (i in 0 until 8) {
                val a = i * PI / 4
                drawLine(color, p(0.5f + 0.27f * cos(a).toFloat(), 0.5f + 0.27f * sin(a).toFloat()), p(0.5f + 0.4f * cos(a).toFloat(), 0.5f + 0.4f * sin(a).toFloat()), w * 1.2f, StrokeCap.Round)
            }
        }
        Glyph.CLOSE -> {
            drawLine(color, p(0.25f, 0.25f), p(0.75f, 0.75f), w, StrokeCap.Round)
            drawLine(color, p(0.75f, 0.25f), p(0.25f, 0.75f), w, StrokeCap.Round)
        }
        Glyph.PLUS -> {
            drawLine(color, p(0.5f, 0.2f), p(0.5f, 0.8f), w, StrokeCap.Round)
            drawLine(color, p(0.2f, 0.5f), p(0.8f, 0.5f), w, StrokeCap.Round)
        }
        Glyph.EDIT -> {
            drawLine(color, p(0.22f, 0.78f), p(0.76f, 0.24f), w * 1.2f, StrokeCap.Round)
            drawLine(color, p(0.18f, 0.84f), p(0.32f, 0.82f), w, StrokeCap.Round)
        }
        Glyph.WIDGET -> {
            drawRoundRect(color, p(0.15f, 0.15f), androidx.compose.ui.geometry.Size(s * 0.7f, s * 0.32f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(w))
            drawRoundRect(color, p(0.15f, 0.55f), androidx.compose.ui.geometry.Size(s * 0.3f, s * 0.3f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(w))
            drawRoundRect(color, p(0.55f, 0.55f), androidx.compose.ui.geometry.Size(s * 0.3f, s * 0.3f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(w))
        }
        Glyph.GROUP -> {
            drawCircle(color, s * 0.38f, p(0.5f, 0.5f), style = Stroke(w * 0.8f))
            for (i in 0 until 5) {
                val a = -PI / 2 + i * 2 * PI / 5
                drawCircle(color, s * 0.08f, p(0.5f + 0.38f * cos(a).toFloat(), 0.5f + 0.38f * sin(a).toFloat()))
            }
            drawCircle(color, s * 0.1f, p(0.5f, 0.5f))
        }
        Glyph.CHECK -> {
            drawLine(color, p(0.2f, 0.52f), p(0.42f, 0.74f), w * 1.2f, StrokeCap.Round)
            drawLine(color, p(0.42f, 0.74f), p(0.8f, 0.28f), w * 1.2f, StrokeCap.Round)
        }
        Glyph.TRASH -> {
            drawLine(color, p(0.2f, 0.28f), p(0.8f, 0.28f), w, StrokeCap.Round)
            drawRoundRect(color, p(0.28f, 0.34f), androidx.compose.ui.geometry.Size(s * 0.44f, s * 0.5f), androidx.compose.ui.geometry.CornerRadius(s * 0.05f), style = Stroke(w))
            drawLine(color, p(0.42f, 0.18f), p(0.58f, 0.18f), w, StrokeCap.Round)
        }
        Glyph.PAGE -> {
            drawRoundRect(color, p(0.22f, 0.12f), androidx.compose.ui.geometry.Size(s * 0.56f, s * 0.76f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f), style = Stroke(w))
            drawLine(color, p(0.5f, 0.38f), p(0.5f, 0.62f), w, StrokeCap.Round)
            drawLine(color, p(0.38f, 0.5f), p(0.62f, 0.5f), w, StrokeCap.Round)
        }
    }
}
