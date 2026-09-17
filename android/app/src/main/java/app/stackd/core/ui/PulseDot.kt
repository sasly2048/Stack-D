package app.stackd.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.EaseRitual
import app.stackd.core.theme.LocalReduceMotion
import app.stackd.core.theme.Stackd

/**
 * The recurring status pip — ember by default, `pulse` green for live sessions.
 * Mirrors the web app's `animate-breathing` + `--dot-glow` treatment: a soft
 * halo breathing around a solid core.
 *
 * Honours reduce-motion by holding the dot at its resting frame, matching the
 * web app freezing this loop under `prefers-reduced-motion: reduce`.
 */
@Composable
fun PulseDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    color: Color = Stackd.colors.accent,
    animated: Boolean = true,
) {
    val reduceMotion = LocalReduceMotion.current
    val shouldAnimate = animated && !reduceMotion

    val scale = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "pulse-dot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = EaseRitual),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse-dot-scale",
        ).value
    } else {
        1.3f
    }

    val haloAlpha = if (shouldAnimate) (1.8f - scale) * 0.55f else 0.28f

    Canvas(modifier = modifier.size(size * 2.5f)) {
        val core = this.size.minDimension / 5f
        drawCircle(color = color.copy(alpha = haloAlpha), radius = core * scale)
        drawCircle(color = color, radius = core)
    }
}
