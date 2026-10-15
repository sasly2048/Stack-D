package app.stackd.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.Stackd

/**
 * Opens the "More" sheet: a 44dp soft-glass disc (white 8%, no border) with a
 * two-stroke glyph — a long line over a shorter right-aligned one — instead of
 * a stock hamburger/grid. Drawn, not an icon font, so the strokes stay crisp
 * and optically centred at this size.
 */
@Composable
fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val ink = Stackd.colors.textPrimary
    Box(
        modifier
            .size(44.dp)
            .pressFeedback(source, pressedScale = 0.92f, sound = app.stackd.core.feedback.Sfx.Kind.OPEN)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Menu" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp, 10.dp)) {
            val w = 1.75.dp.toPx()
            val inset = w / 2
            drawLine(ink, Offset(inset, inset), Offset(size.width - inset, inset), w, StrokeCap.Round)
            drawLine(
                ink,
                Offset(size.width * 0.42f, size.height - inset),
                Offset(size.width - inset, size.height - inset),
                w,
                StrokeCap.Round,
            )
        }
    }
}
