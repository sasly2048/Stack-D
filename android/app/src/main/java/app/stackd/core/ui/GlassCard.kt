package app.stackd.core.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd

/**
 * The web app's `.glass` / `.glass-strong` utilities: a translucent obsidian
 * fill over a backdrop blur, finished with a 1px white/8% hairline.
 *
 * Compose can only blur a composable's own content, not what is painted behind
 * it (`Modifier.blur` has no backdrop equivalent, and RenderEffect-backed blur
 * needs API 31+ regardless). A higher-opacity fill reads as the same material
 * here and behaves identically on every supported API level, so that is what
 * this draws — worth knowing when comparing side by side with the web build.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radius2Xl,
    strong: Boolean = false,
    contentPadding: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Stackd.colors
    val fillAlpha = when {
        strong -> 0.92f
        supportsBackdropBlur -> 0.72f
        else -> 0.86f
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface.copy(alpha = fillAlpha))
            .border(1.dp, colors.border, shape)
            .padding(contentPadding),
        content = content,
    )
}

private val supportsBackdropBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Hairline divider matching the web app's `border-white/5` rules. */
@Composable
fun hairline(): Color = Stackd.colors.border
