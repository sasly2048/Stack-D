package app.stackd.core.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The web app's signature transition curve — cubic-bezier(0.32, 0.72, 0, 1),
 * named `--ease-ritual` in styles.css. Used for essentially every entrance and
 * hover transition, so entrance timing feels identical across platforms.
 */
val EaseRitual = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

const val DurationEntranceMs = 800
const val DurationShakeMs = 450

/**
 * Mirrors the web app's `prefers-reduced-motion: reduce` handling, which
 * freezes every continuous/vestibular-triggering loop (breathing dots, meteors,
 * marquees, ripples) to a static frame.
 *
 * Compose has no built-in reduce-motion flag, so this reads the system
 * "Remove animations" setting. Read it once at the theme boundary rather than
 * per-animation: the value only changes via a system settings trip, which
 * recreates the Activity anyway.
 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
