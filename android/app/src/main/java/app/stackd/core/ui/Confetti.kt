package app.stackd.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * A one-shot confetti burst — the Android shape of the web's completion
 * celebration. Pure Compose + Canvas, no assets: [count] pieces launch from a
 * point near the top and fall with a little horizontal drift and spin, driven
 * by a single 0→1 progress animation so the whole burst is one state read.
 *
 * Deterministic per-piece angle/speed (seeded by index) rather than
 * Math.random — the runtime forbids argless randomness, and a fixed spread
 * looks the same to the eye across the ~2s the burst is visible.
 */
@Composable
fun Confetti(
    modifier: Modifier = Modifier,
    count: Int = 60,
    colors: List<Color> = DEFAULT_CONFETTI,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 2200, easing = LinearEasing),
        label = "confetti",
    )
    if (progress >= 1f) return // burst finished — stop drawing

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val originX = w / 2f
        for (i in 0 until count) {
            // Spread pieces across a fan; seed everything off the index so the
            // pattern is stable without a RNG.
            val angle = (-90f + (i - count / 2f) * (120f / count)) * (Math.PI / 180f)
            val speed = 0.6f + (i % 7) * 0.06f
            val dx = cos(angle).toFloat() * w * 0.5f * speed
            // Gravity: pieces rise a touch then fall the height of the screen.
            val riseFall = -0.15f + progress * progress * 1.25f
            val x = originX + dx * progress
            val y = size.height * riseFall * speed + size.height * 0.1f
            val alpha = (1f - progress).coerceIn(0f, 1f)
            drawCircle(
                color = colors[i % colors.size].copy(alpha = alpha),
                radius = 6f + (i % 3) * 2f,
                center = Offset(x, y),
            )
        }
    }
}

private val DEFAULT_CONFETTI = listOf(
    Color(0xFFF0A968), // ember
    Color(0xFFE2E2E2), // silver
    Color(0xFF6EE7B7),
    Color(0xFFF87171),
    Color(0xFFA78BFA),
)
