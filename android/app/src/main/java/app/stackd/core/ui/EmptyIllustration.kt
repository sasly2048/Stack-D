package app.stackd.core.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.Stackd

/**
 * Brand illustration for empty states: three phone "plates" stacked in
 * perspective (the Stack'd ritual) over a soft ember glow, the screen's own
 * glyph on the top plate. Drawn, not bitmap — crisp at any density, tinted by
 * the theme, zero assets. Floats gently; still when animations are off.
 */
@Composable
fun EmptyIllustration(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val colors = Stackd.colors
    val ctx = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val t = rememberInfiniteTransition(label = "float")
    val f by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "f",
    )
    val drift = if (still) 0.5f else f

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.toPx()
            val c = Offset(w / 2, w / 2)
            // Ambient glow — the web's warm hero gradient, as a halo.
            drawCircle(
                Brush.radialGradient(
                    listOf(colors.accent.copy(alpha = 0.18f), colors.accent.copy(alpha = 0.04f), Color.Transparent),
                    center = c, radius = w * 0.55f,
                ),
                radius = w * 0.55f, center = c,
            )
            val plateW = w * 0.42f
            val plateH = w * 0.62f
            val r = CornerRadius(w * 0.07f)
            // Back to front: each plate a little higher, lighter, more rotated.
            for (i in 0..2) {
                val lift = (2 - i) * w * 0.055f + (if (i == 2) drift * w * 0.025f else 0f)
                val tilt = -14f + i * 4f
                val topLeft = Offset(c.x - plateW / 2, c.y - plateH / 2 + lift - w * 0.02f)
                rotate(tilt, pivot = Offset(c.x, c.y + lift)) {
                    drawRoundRect(
                        color = if (i == 2) colors.surfaceRaised else colors.surface,
                        topLeft = topLeft, size = Size(plateW, plateH), cornerRadius = r,
                    )
                    drawRoundRect(
                        color = if (i == 2) colors.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f + i * 0.03f),
                        topLeft = topLeft, size = Size(plateW, plateH), cornerRadius = r,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
        }
        // The screen's glyph rides the top plate.
        Icon(
            icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier
                .offset(y = (-(drift * 3f)).dp - 4.dp)
                .size(size * 0.2f),
        )
    }
}
