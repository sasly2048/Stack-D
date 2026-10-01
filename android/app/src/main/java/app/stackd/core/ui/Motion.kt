package app.stackd.core.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/**
 * Tactile press feedback shared by every tappable surface: a quick spring
 * down to [pressedScale] while held, plus a light keyboard-tap haptic on
 * touch-down. Physical response at the moment of contact is what makes taps
 * feel instant (premium apps never leave a press visually silent).
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
    haptic: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    if (haptic) {
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
