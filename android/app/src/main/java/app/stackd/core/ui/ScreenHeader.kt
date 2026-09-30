package app.stackd.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.Stackd

/**
 * Top-of-screen breadcrumb + back affordance shared by every sub-screen.
 *
 * Back sits top-left in a 48dp target (where both Android and iOS users reach
 * for it); screens used to end with a BACK button you had to scroll to. The
 * chevron is pulled left by its inner padding so its stroke lines up with the
 * content edge instead of indenting the whole header.
 */
@Composable
fun ScreenHeader(
    path: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Stackd.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(
                Modifier
                    .offset(x = (-20).dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val w = size.width
                    val chevron = Path().apply {
                        moveTo(w * 0.62f, w * 0.18f)
                        lineTo(w * 0.30f, w * 0.50f)
                        lineTo(w * 0.62f, w * 0.82f)
                    }
                    drawPath(
                        chevron,
                        colors.textPrimary,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
        Text(
            path,
            style = MonoLabel,
            color = colors.textMuted,
            // Pulled over the 48dp target's padding: chevron stroke on the content
            // edge, ~10dp to the label.
            modifier = Modifier
                .weight(1f)
                .offset(x = if (onBack != null) (-32).dp else 0.dp),
        )
        trailing()
    }
}

