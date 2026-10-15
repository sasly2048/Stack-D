package app.stackd.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Empty-state illustration: the brand [StackArt] (three phones, face-down)
 * with the screen's own glyph engraved on the top phone, so every empty
 * screen shares one recognisable, premium image.
 */
@Composable
fun EmptyIllustration(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 150.dp) {
    StackArt(modifier = modifier, size = size, phones = 3, icon = icon)
}
