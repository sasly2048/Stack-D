package app.stackd.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Form-factor classification for adaptive layout, derived from the current
 * window's dp size — no `material3-window-size-class` dependency, just
 * `LocalConfiguration`, which already recomposes on rotate, fold, resize, and
 * multi-window changes.
 *
 * The Android device spread this targets:
 *  - flip phones folded / narrow slabs  → COMPACT  (< 600dp wide)
 *  - large slabs, foldables half-open   → MEDIUM   (600–839dp)
 *  - foldables unfolded, tablets, desktop-window → EXPANDED (≥ 840dp)
 *
 * [isTall]/[isWide] capture orientation independently of the bucket, so a
 * screen can widen its content cap or switch a stack to a row when there's the
 * room, and tighten vertical rhythm when a flip phone is short.
 */
enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

data class WindowInfo(
    val width: WindowWidth,
    val widthDp: Dp,
    val heightDp: Dp,
) {
    val isCompact: Boolean get() = width == WindowWidth.COMPACT
    val isExpanded: Boolean get() = width == WindowWidth.EXPANDED
    /** Landscape-ish: wider than tall. True for unfolded books, landscape slabs. */
    val isWide: Boolean get() = widthDp > heightDp
    /** Short window — flip-phone cover display, split-screen top half. */
    val isShort: Boolean get() = heightDp < 480.dp

    /** The content-width ceiling this window should use, form-factor aware. */
    val contentCap: Dp
        get() = when (width) {
            WindowWidth.COMPACT -> widthDp // no cap; use the whole narrow screen
            WindowWidth.MEDIUM -> 600.dp
            WindowWidth.EXPANDED -> 720.dp
        }
}

@Composable
fun rememberWindowInfo(): WindowInfo {
    val config = LocalConfiguration.current
    val w = config.screenWidthDp
    val h = config.screenHeightDp
    return remember(w, h) {
        WindowInfo(
            width = when {
                w < 600 -> WindowWidth.COMPACT
                w < 840 -> WindowWidth.MEDIUM
                else -> WindowWidth.EXPANDED
            },
            widthDp = w.dp,
            heightDp = h.dp,
        )
    }
}
