package app.stackd.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Caps content to a readable measure and centers it, so a layout that looks
 * right on a phone doesn't stretch edge-to-edge on a tablet, foldable, or
 * landscape window. Mirrors the web's `max-w-* mx-auto` — a single column that
 * grows to a ceiling, then stops and centers.
 *
 * Horizontal overflow is the failure this prevents: without a ceiling, text
 * lines run absurdly wide and controls sprawl on large screens. Vertical
 * overflow is handled by the callers' own `verticalScroll`.
 *
 * [maxContentWidth] defaults to a comfortable single-column measure. Analytics
 * or grid-heavy screens can widen it. [horizontalPadding] keeps content off the
 * edges on narrow screens, where the cap never binds.
 */
@Composable
fun ResponsiveColumn(
    modifier: Modifier = Modifier,
    // Null → derive the ceiling from the live window (compact uses the whole
    // screen, medium/expanded cap so foldables and tablets don't sprawl). A
    // caller that needs an explicit measure (a wide analytics grid) still passes
    // one and overrides the adaptive default.
    maxContentWidth: Dp? = null,
    horizontalPadding: Dp = 20.dp,
    verticalPadding: Dp = 28.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cap = maxContentWidth ?: rememberWindowInfo().contentCap
    // Outer column fills and centers; inner column carries the width ceiling and
    // the real content. Centering the inner one is what keeps a wide screen from
    // left-aligning a narrow measure against the edge.
    Column(
        // safeDrawingPadding keeps content clear of the status bar, nav bar,
        // display cutout, and IME — the app draws edge-to-edge, so without this
        // the first heading sits under the clock (and the last control under
        // the gesture bar). Applied at this shared chokepoint so every screen
        // routing through ResponsiveColumn is inset once, correctly.
        modifier = modifier
            .fillMaxWidth()
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = cap)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
        ) {
            content()
            // Room to scroll the last item clear of the floating tab bar, which
            // now overlays content (it blurs what's behind it).
            Spacer(Modifier.height(LocalBottomBarInset.current))
        }
    }
}

/** Height a floating bottom bar covers at the end of scroll content; 0 when none. */
val LocalBottomBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/**
 * The lazy counterpart of [ResponsiveColumn] for screens with long lists.
 *
 * Same width ceiling and padding, but items compose on demand instead of all at
 * once — a 100-row leaderboard or 200-item vault used to compose and measure
 * every row before the first frame. Callers put headers in `item {}` and rows
 * in `items(...)`.
 *
 * Insets: the TOP inset is applied outside the list, so scrolled content passes
 * beneath a fixed status-bar gap instead of sliding under the clock (the old
 * inset lived inside the scroll and scrolled away). The bottom inset (nav bar +
 * keyboard) is content padding, so the last row can still scroll clear of it.
 */
@Composable
fun ResponsiveLazyColumn(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp? = null,
    horizontalPadding: Dp = 20.dp,
    verticalPadding: Dp = 28.dp,
    state: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val cap = maxContentWidth ?: rememberWindowInfo().contentCap
    val insets = androidx.compose.foundation.layout.WindowInsets.safeDrawing
    val bottom = insets.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)
        .asPaddingValues().calculateBottomPadding()
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                insets.only(
                    androidx.compose.foundation.layout.WindowInsetsSides.Top +
                        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal,
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = state,
            modifier = Modifier.widthIn(max = cap).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = verticalPadding,
                bottom = verticalPadding + bottom + LocalBottomBarInset.current,
            ),
            content = content,
        )
    }
}

/** A single-column reading measure; wide enough for forms and stats, capped for tablets. */
val DEFAULT_MAX_CONTENT_WIDTH: Dp = 560.dp

/** Wider ceiling for analytics/grid screens that legitimately use more room. */
val WIDE_MAX_CONTENT_WIDTH: Dp = 840.dp
