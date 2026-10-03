package app.stackd.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.stackd.core.theme.Stackd

/**
 * Page-wide light used on every main tab (Regain-style landing glow, in ember): a wide radial
 * pool at the top plus a long vertical fall-off that reaches the bottom of the
 * screen, so the whole page is lit rather than one box. Static on purpose: the
 * old 9s breathing loop invalidated a full-screen draw every frame on every tab,
 * which made scrolling stutter. drawWithCache builds the brushes once per size.
 */
fun Modifier.pageGlow(): Modifier = composed {
    val accent = Stackd.colors.accent
    drawWithCache {
        val wash = Brush.verticalGradient(
            0f to accent.copy(alpha = 0.20f),
            0.35f to accent.copy(alpha = 0.09f),
            0.75f to accent.copy(alpha = 0.025f),
            1f to Color.Transparent,
        )
        val r = size.width * 1.1f
        val c = Offset(size.width * 0.7f, 0f)
        val pool = Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent), center = c, radius = r)
        onDrawBehind {
            drawRect(wash)
            drawCircle(pool, radius = r, center = c)
        }
    }
}
