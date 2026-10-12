package app.stackd.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Stack'd is dark-only by design — there is no light theme to switch to.
 *
 * The bespoke palette travels through [LocalStackdColors]; the Material3
 * scheme below exists only so stock M3 components (ripples, text fields,
 * dialogs) don't render in default purple.
 */
@Composable
fun StackdTheme(content: @Composable () -> Unit) {
    val colors = StackdColors()

    val material = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.background,
        secondary = colors.accentDeep,
        onSecondary = colors.textPrimary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceRaised,
        onSurfaceVariant = colors.textMuted,
        error = colors.breach,
        onError = colors.textPrimary,
        outline = colors.divider,
        outlineVariant = colors.border,
    )

    CompositionLocalProvider(
        LocalStackdColors provides colors,
        LocalReduceMotion provides rememberSystemReduceMotion(),
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = StackdTypography,
            shapes = StackdShapes,
            content = content,
        )
    }
}

/** Shorthand for the bespoke palette: `Stackd.colors.accent`. */
object Stackd {
    val colors: StackdColors
        @Composable get() = LocalStackdColors.current
}
