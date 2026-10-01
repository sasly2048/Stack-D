package app.stackd.core.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.stackd.core.feedback.Sfx

/** Web `--ease-ritual`: every product motion shares this curve. */
val EaseRitual = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * Tactile press feedback shared by every tappable surface: a quick spring
 * down to [pressedScale] while held, a light haptic on touch-down, and the
 * web's matching UI sound on a completed tap. The sound fires on Release, not
 * Press, so a scroll that starts on a card (Press then Cancel) stays silent.
 * Pass `sound = null` where the caller plays its own.
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
    haptic: Boolean = true,
    sound: Sfx.Kind? = Sfx.Kind.TAP,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    if (haptic) {
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    if (sound != null) {
        LaunchedEffect(interactionSource, sound) {
            interactionSource.interactions.collect { if (it is PressInteraction.Release) Sfx.play(sound) }
        }
    }
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Web `.btn-ember` on touch: a 120° band of [color] sweeps left-to-right
 * across the control while it's held, then fades on release. Hover doesn't
 * exist on a phone, so press is the moment it plays. Clip to the control's
 * shape before applying.
 */
@Composable
fun Modifier.emberSweep(interactionSource: InteractionSource, color: Color, strength: Float = 0.35f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val travel = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            alpha.snapTo(1f)
            travel.snapTo(0f)
            travel.animateTo(1f, tween(520, easing = EaseRitual))
        } else {
            alpha.animateTo(0f, tween(320, easing = EaseRitual))
        }
    }
    return drawWithContent {
        drawContent()
        val a = alpha.value
        if (a > 0f) {
            val w = size.width
            val x = (travel.value - 1f) * w
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.45f to color.copy(alpha = strength * a),
                    0.55f to color.copy(alpha = strength * a),
                    1f to Color.Transparent,
                    start = Offset(x, size.height),
                    end = Offset(x + w * 1.15f, 0f),
                ),
            )
        }
    }
}

/**
 * Web `.btn-ember:hover { letter-spacing: 0.25em }`: the label breathes out
 * while pressed. Returns the label's letter spacing for this frame.
 */
@Composable
fun pressedTracking(interactionSource: InteractionSource): TextUnit {
    val pressed by interactionSource.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed) 1f else 0f, tween(300, easing = EaseRitual), label = "tracking")
    val base = MaterialTheme.typography.labelLarge.letterSpacing
    return when {
        base.isEm -> (base.value + 0.08f * p).em
        base.isSp -> (base.value + 1.2f * p).sp
        else -> (0.08f * p).em
    }
}

/**
 * Web `animate-shake`: a 450ms horizontal judder whenever [trigger] becomes
 * true. Used where input is rejected, paired with the error sound.
 */
@Composable
fun Modifier.shake(trigger: Boolean): Modifier {
    val x = remember { Animatable(0f) }
    val px = with(LocalDensity.current) { 1.dp.toPx() }
    LaunchedEffect(trigger) {
        if (trigger) {
            x.animateTo(
                0f,
                keyframes {
                    durationMillis = 450
                    -6f at 90 using EaseRitual
                    6f at 180 using EaseRitual
                    -4f at 270 using EaseRitual
                    4f at 360 using EaseRitual
                },
            )
        }
    }
    return graphicsLayer { translationX = x.value * px }
}

/** Web `animate-breathing`: a 4s 1.5% swell for things that are alive (timers, live rings). */
@Composable
fun Modifier.breathing(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val t = rememberInfiniteTransition(label = "breathing")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2000, easing = EaseRitual), RepeatMode.Reverse),
        label = "breath",
    )
    return graphicsLayer {
        val s = 1f + 0.015f * p
        scaleX = s
        scaleY = s
        alpha = 0.85f + 0.15f * p
    }
}
