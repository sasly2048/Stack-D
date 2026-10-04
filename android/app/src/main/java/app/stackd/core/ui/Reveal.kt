package app.stackd.core.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp


/**
 * Fades an element in and lifts it 10dp (web animate-entrance) the first time it composes. In a lazy
 * list, rows compose as they scroll into view, so this doubles as scroll-in
 * choreography; [index] staggers siblings (40ms each, capped) so a screen
 * cascades instead of popping in as one block. Runs once per element (state
 * survives scroll-out/in), and not at all when system animations are off.
 */
@Composable
fun Modifier.reveal(index: Int = 0): Modifier {
    val ctx = LocalContext.current
    val off = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val shown = rememberSaveable { androidx.compose.runtime.mutableStateOf(off) }
    val p = remember { Animatable(if (shown.value) 1f else 0f) }
    val lift = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!shown.value) {
            p.animateTo(1f, tween(560, delayMillis = (index.coerceAtMost(8)) * 40, easing = EaseRitual))
            shown.value = true
        }
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * lift
    }
}
