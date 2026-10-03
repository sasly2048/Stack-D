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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** How long the ember fill takes to cross a control (web 1.2s; long enough to be seen, not waited on). */
private const val FillMs = 650

/** Symmetric ease-in-out: the fill visibly travels instead of jumping on touch-down. */
private val FillEase = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/**
 * Web `.btn-ember` activation on touch: the fill starts growing from the left
 * edge on touch-down and [onClick] fires only once it has crossed the control.
 * ONE continuous animation per press: releasing early never restarts it (that
 * restart was the visible stutter) — the click simply waits for the running
 * fill to finish, holds full for a beat, then fires. Dragged off (Cancel), it
 * retracts and nothing fires. After firing it resets 600ms later in case the
 * control stays on screen; a control that leaves composition starts fresh.
 *
 * Returns the fill progress (0..1, read it at draw time) and the click to wire
 * into the control in place of [onClick].
 */
@Composable
fun rememberFillClick(interactionSource: InteractionSource, onClick: () -> Unit): Pair<() -> Float, () -> Unit> {
    val fill = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onClick)
    val firing = remember { booleanArrayOf(false) }
    val run = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    fun startFill() {
        if (run[0]?.isActive == true) return
        val remaining = ((1f - fill.value) * FillMs).toInt()
        run[0] = scope.launch { if (remaining > 0) fill.animateTo(1f, tween(remaining, easing = FillEase)) }
    }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (firing[0]) return@collect
            when (it) {
                is PressInteraction.Press -> startFill()
                is PressInteraction.Cancel -> {
                    run[0]?.cancel()
                    launch { fill.animateTo(0f, tween(260, easing = FillEase)) }
                }
            }
        }
    }
    val click: () -> Unit = {
        if (!firing[0]) {
            firing[0] = true
            scope.launch {
                startFill()          // no-op if the press already started it
                run[0]?.join()       // wait for the same fill, never restart it
                delay(120)           // a beat on full so the completion registers
                latest()
                delay(600)
                fill.snapTo(0f)
                firing[0] = false
            }
        }
    }
    return remember(fill) { { fill.value } } to click
}

/**
 * The light variant for secondary buttons: the fill sweeps across while held
 * and fades back on release; the click is not delayed.
 */
@Composable
fun rememberPressFill(interactionSource: InteractionSource): () -> Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val fill = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) fill.animateTo(1f, tween(FillMs, easing = FillEase))
        else fill.animateTo(0f, tween(360, easing = FillEase))
    }
    return remember(fill) { { fill.value } }
}

/**
 * Paints the fill behind the control's content: [brush] from the left edge to
 * [progress] of the width, with a soft glowing leading edge. Clip to the
 * control's shape first and put it after the container background.
 */
fun Modifier.fillSweep(progress: () -> Float, brush: Brush, edge: Color = Color.Transparent): Modifier =
    drawBehind {
        val p = progress()
        if (p <= 0f) return@drawBehind
        val x = size.width * p
        drawRect(brush, size = Size(x, size.height))
        if (edge != Color.Transparent && p < 1f) {
            val glow = 28.dp.toPx()
            drawRect(
                Brush.horizontalGradient(listOf(edge, Color.Transparent), startX = x, endX = x + glow),
                topLeft = Offset(x, 0f),
                size = Size(glow, size.height),
            )
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
