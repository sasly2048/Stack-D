package app.stackd.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

/**
 * A number that counts up to [target] (ease-out) when it first appears or
 * changes — the web's useCountUp. Numbers that arrive with motion read as
 * earned; numbers that snap in read as a static report.
 */
@Composable
fun animatedCount(target: Float, durationMs: Int = 900): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target, tween(durationMs, easing = EaseOutQuart)) }
    return value.value
}
