package app.stackd.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd

/**
 * Shimmering placeholder in the shape of the content that's coming. Showing
 * the final layout immediately (instead of "Loading…") makes waits feel
 * shorter and avoids the jump when data lands.
 */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RadiusMd) {
    val base = Stackd.colors.textPrimary
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    Box(
        modifier.background(
            Brush.linearGradient(
                colors = listOf(base.copy(alpha = 0.04f), base.copy(alpha = 0.10f), base.copy(alpha = 0.04f)),
                start = Offset(x * 600f, 0f),
                end = Offset(x * 600f + 600f, 0f),
            ),
            shape,
        ),
    )
}

/** A card-shaped skeleton: eyebrow, title line, two body lines. */
@Composable
fun SkeletonCard(height: Dp = 132.dp) {
    Column(Modifier.fillMaxWidth()) {
        SkeletonBlock(Modifier.fillMaxWidth().height(height), Radius2Xl)
        Spacer(Modifier.height(12.dp))
    }
}
