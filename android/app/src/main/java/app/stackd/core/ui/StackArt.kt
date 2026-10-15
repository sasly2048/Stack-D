package app.stackd.core.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.Stackd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Brand art, drawn in code: a stack of phones lying face-down — the Stack'd
 * ritual. Each phone is a graphite back with a camera island, a warm ember
 * rim light on its top edge, a gloss streak and a soft contact shadow; an
 * ember glow pools beneath the stack.
 *
 * Orientation: the top phone lies square to the viewer; the phones beneath
 * peek out evenly to alternating sides (a tidy, deliberate stack rather than
 * a random fan), each a step lower so their edges read as layers.
 *
 * On first composition the phones drop onto the stack one by one (springy),
 * then the stack floats with a barely-there sway. Fully still when the
 * system's animations are off. Crisp at any size, zero bitmap assets.
 *
 * [icon], when given, is engraved on the top phone (empty states use it to say
 * what the screen is for).
 *
 * [liftOff] plays the session's end instead: the stack starts landed and the
 * phones rise and fade away one by one, top phone first (~220ms apart, last one
 * gone at ~1.4s), leaving only the ember glow, which settles to a soft residue.
 * Overrides [dropIn]. Still mode shows that final state immediately.
 */
@Composable
fun StackArt(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    phones: Int = 4,
    icon: ImageVector? = null,
    dropIn: Boolean = true,
    liftOff: Boolean = false,
) {
    val colors = Stackd.colors
    val ctx = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // Per-phone landing progress (0 = above, 1 = landed). Bottom phone lands first.
    val land = remember { List(phones) { Animatable(if (still || !dropIn || liftOff) 1f else 0f) } }
    // Per-phone lift progress (0 = resting, 1 = picked up and gone) and glow strength.
    val lift = remember { List(phones) { Animatable(if (liftOff && still) 1f else 0f) } }
    val glow = remember { Animatable(if (liftOff && still) 0.35f else 1f) }
    LaunchedEffect(Unit) {
        if (liftOff) {
            if (still) return@LaunchedEffect
            lift.forEachIndexed { i, a ->
                launch {
                    delay(250L + (phones - 1 - i) * 220L) // top phone first
                    a.animateTo(1f, tween(500, easing = EaseRitual))
                }
            }
            delay(250L + (phones - 1) * 220L)
            glow.animateTo(0.35f, tween(900, easing = EaseRitual))
            return@LaunchedEffect
        }
        if (still || !dropIn) return@LaunchedEffect
        land.forEachIndexed { i, a ->
            launch {
                delay(120L + i * 140L)
                a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    val t = rememberInfiniteTransition(label = "stackFloat")
    val phase by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(6000, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val float = if (still) 0f else sin(phase)
    val accent = colors.accent

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val phoneW = w * 0.40f
            val phoneH = w * 0.70f
            val step = w * 0.04f // vertical spacing between phones in the stack

            // Ember glow pooling under the stack.
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.26f * glow.value), accent.copy(alpha = 0.06f * glow.value), Color.Transparent),
                    center = Offset(c.x, c.y + w * 0.12f), radius = w * 0.52f,
                ),
                radius = w * 0.52f, center = Offset(c.x, c.y + w * 0.12f),
            )

            val baseY = c.y + (phones - 1) * step / 2
            for (i in 0 until phones) {
                val p = land[i].value
                val up = lift[i].value
                val top = i == phones - 1
                // Distance from the top phone: 0 for the top, 1, 2, 3 below it.
                val depth = phones - 1 - i
                // Alternate sides, growing a little with depth: 0°, -5°, +4°, -7° …
                val side = if (depth % 2 == 1) -1f else 1f
                val tilt = if (top) 0f else side * (3.5f + depth * 1.5f)
                val nudge = if (top) 0f else side * w * (0.012f + depth * 0.006f)
                val sway = if (top) float * 0.8f else 0f
                val dropOffset = (1f - p) * -w * 0.55f
                val cx = c.x + nudge
                val cy = baseY - i * step + dropOffset - up * this.size.height * 0.40f +
                    (if (top) float * w * 0.006f else 0f)
                rotate(tilt + sway, pivot = Offset(cx, cy)) {
                    drawPhoneBack(
                        center = Offset(cx, cy),
                        size = Size(phoneW, phoneH),
                        accent = accent,
                        alpha = (p * (1f - up)).coerceIn(0f, 1f),
                        showCamera = !(top && icon != null),
                        dim = depth * 0.12f,
                    )
                }
            }
        }
        icon?.let {
            val p = land.last().value * (1f - lift.last().value)
            Icon(
                it,
                contentDescription = null,
                tint = colors.accent.copy(alpha = p.coerceIn(0f, 1f)),
                modifier = Modifier
                    .offset(y = (-(size.value * 0.04f * (phones - 1) / 2f)).dp + (float * 1.2f).dp - size * 0.40f * lift.last().value)
                    .size(size * 0.17f),
            )
        }
    }
}

/** One phone, back facing up: graphite body, camera island, ember rim, gloss, contact shadow. */
private fun DrawScope.drawPhoneBack(
    center: Offset,
    size: Size,
    accent: Color,
    alpha: Float,
    showCamera: Boolean,
    dim: Float,
) {
    if (alpha <= 0f) return
    val tl = Offset(center.x - size.width / 2, center.y - size.height / 2)
    val r = CornerRadius(size.width * 0.16f)

    // Contact shadow, offset down-right so the stack reads as resting on a surface.
    drawRoundRect(
        Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.55f * alpha), Color.Transparent),
            center = Offset(center.x + size.width * 0.05f, center.y + size.height * 0.06f),
            radius = size.height * 0.62f,
        ),
        topLeft = Offset(tl.x - size.width * 0.1f, tl.y),
        size = Size(size.width * 1.25f, size.height * 1.15f),
        cornerRadius = r,
    )
    // Graphite body: lit from the top-left; lower phones a touch darker.
    val k = (1f - dim).coerceIn(0.5f, 1f)
    fun shade(c: Color) = Color(c.red * k, c.green * k, c.blue * k, alpha)
    drawRoundRect(
        Brush.linearGradient(
            listOf(shade(Color(0xFF2A2A2D)), shade(Color(0xFF141416)), shade(Color(0xFF0B0B0C))),
            start = tl,
            end = Offset(tl.x + size.width, tl.y + size.height),
        ),
        topLeft = tl, size = size, cornerRadius = r,
    )
    // Gloss streak across the back.
    translate(tl.x, tl.y) {
        drawRoundRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.42f to Color.White.copy(alpha = 0.0f),
                0.5f to Color.White.copy(alpha = 0.07f * alpha * k),
                0.58f to Color.White.copy(alpha = 0.0f),
                1f to Color.Transparent,
                start = Offset(0f, size.height * 0.15f),
                end = Offset(size.width, size.height * 0.55f),
            ),
            size = size, cornerRadius = r,
        )
    }
    // Ember rim light: bright on the top-left edge, fading around the body.
    drawRoundRect(
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.95f * alpha * k),
                accent.copy(alpha = 0.25f * alpha * k),
                Color.White.copy(alpha = 0.04f * alpha),
            ),
            start = tl,
            end = Offset(tl.x + size.width * 0.9f, tl.y + size.height * 0.75f),
        ),
        topLeft = tl, size = size, cornerRadius = r,
        style = Stroke(width = size.width * 0.018f),
    )
    if (showCamera) {
        // Camera island with two lenses — instantly reads as "phone, face-down".
        val islandW = size.width * 0.34f
        val islandTl = Offset(tl.x + size.width * 0.1f, tl.y + size.width * 0.1f)
        drawRoundRect(
            shade(Color(0xFF1E1E21)),
            topLeft = islandTl, size = Size(islandW, islandW * 1.15f),
            cornerRadius = CornerRadius(islandW * 0.3f),
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.08f * alpha),
            topLeft = islandTl, size = Size(islandW, islandW * 1.15f),
            cornerRadius = CornerRadius(islandW * 0.3f),
            style = Stroke(width = size.width * 0.008f),
        )
        val lensR = islandW * 0.2f
        listOf(0.32f, 0.72f).forEach { fy ->
            val lc = Offset(islandTl.x + islandW * 0.42f, islandTl.y + islandW * 1.15f * fy)
            drawCircle(Color(0xFF070708).copy(alpha = alpha), radius = lensR, center = lc)
            drawCircle(accent.copy(alpha = 0.35f * alpha), radius = lensR, center = lc, style = Stroke(width = lensR * 0.18f))
            drawCircle(Color.White.copy(alpha = 0.18f * alpha), radius = lensR * 0.22f, center = Offset(lc.x - lensR * 0.3f, lc.y - lensR * 0.3f))
        }
    }
}
