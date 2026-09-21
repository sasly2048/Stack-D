package app.stackd.core.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one spacing scale for the whole app — the Android shape of the web's
 * Tailwind spacing steps (0.25rem base). Screens hardcoded `8.dp / 12.dp /
 * 16.dp / 24.dp` inline, which drifted from screen to screen; naming the steps
 * makes vertical rhythm and gaps consistent and reviewable in one place.
 *
 * Use these for `Spacer`, `padding`, and `Arrangement.spacedBy` instead of raw
 * dp literals. The names are t-shirt sizes, not pixel values, so a later
 * density change happens here once.
 */
object Spacing {
    /** 4dp — hairline gaps: label→value, icon→text. */
    val xxs: Dp = 4.dp

    /** 8dp — tight within-component spacing. */
    val xs: Dp = 8.dp

    /** 12dp — between related controls in a group. */
    val sm: Dp = 12.dp

    /** 16dp — the default gap between sibling blocks. */
    val md: Dp = 16.dp

    /** 20dp — screen horizontal inset on narrow devices. */
    val lg: Dp = 20.dp

    /** 24dp — between major sections. */
    val xl: Dp = 24.dp

    /** 32dp — section breaks / bottom breathing room. */
    val xxl: Dp = 32.dp

    /** 48dp — hero spacing above a screen's first heading. */
    val xxxl: Dp = 48.dp
}
