package app.stackd.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Ported 1:1 from the web app's src/styles.css @theme inline block.
// (Not src/lib/design-tokens.ts — that file is a legacy blue-HSL palette from
// an earlier design and is not what ships.)

val Obsidian = Color(0xFF0A0A0A)
val Obsidian2 = Color(0xFF111111)
val Obsidian3 = Color(0xFF181818)

val Silver = Color(0xFFE2E2E2)
val SilverDim = Color(0xFF9A9A9A)

val Muted = Color(0xFF404040)
val Muted2 = Color(0xFF2A2A2A)

val Ember = Color(0xFFF0A968)
val EmberGlow = Color(0xFFFFC48A)
val Accent = Color(0xFFC9874A)

val Breach = Color(0xFFFF3B30)
val Pulse = Color(0xFF34D399)

val HairlineBorder = Color(0x14FFFFFF) // rgba(255,255,255,0.08)
val FocusRing = Color(0x80C9874A) // rgba(201,135,74,0.5)

/**
 * Stack'd runs a single bespoke dark palette. Material3's ColorScheme slots
 * (primary/secondary/tertiary/surfaceVariant/...) don't map onto it cleanly —
 * there is no real "secondary" here — so the app's own colors travel through
 * this instead of being shoehorned into semantic slots that would lie.
 */
@Immutable
data class StackdColors(
    val background: Color = Obsidian,
    val surface: Color = Obsidian2,
    val surfaceRaised: Color = Obsidian3,
    val textPrimary: Color = Silver,
    val textMuted: Color = SilverDim,
    val divider: Color = Muted,
    val surfaceInset: Color = Muted2,
    val accent: Color = Ember,
    val accentGlow: Color = EmberGlow,
    val accentDeep: Color = Accent,
    val breach: Color = Breach,
    val live: Color = Pulse,
    val border: Color = HairlineBorder,
    val ring: Color = FocusRing,
)

val LocalStackdColors = staticCompositionLocalOf { StackdColors() }
