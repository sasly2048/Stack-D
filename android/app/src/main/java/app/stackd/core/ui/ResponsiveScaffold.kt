package app.stackd.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = cap)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/** A single-column reading measure; wide enough for forms and stats, capped for tablets. */
val DEFAULT_MAX_CONTENT_WIDTH: Dp = 560.dp

/** Wider ceiling for analytics/grid screens that legitimately use more room. */
val WIDE_MAX_CONTENT_WIDTH: Dp = 840.dp
