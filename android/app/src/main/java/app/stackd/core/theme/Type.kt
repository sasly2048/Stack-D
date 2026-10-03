package app.stackd.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.stackd.R

// The same faces the web app pulls from Google Fonts — Inter 400/500/600/800
// and JetBrains Mono 400/500 — bundled as static .ttf (latin subset, ~380KB
// total) rather than fetched at runtime. Bundling keeps first paint identical
// offline and avoids a Downloadable-Fonts dependency for six files. Both are
// SIL Open Font License 1.1.
//
// Only the weights the design actually uses are shipped. Compose synthesises
// anything else, so asking for a weight not listed here yields a faux-bold
// rather than a crash.
val DisplayFamily = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_800, FontWeight.ExtraBold),
)

val MonoFamily = FontFamily(
    Font(R.font.jbmono_400, FontWeight.Normal),
    Font(R.font.jbmono_500, FontWeight.Medium),
)

/**
 * True monospace, kept only for technical tags: the screen path breadcrumb
 * ("STACK'D / INSIGHTS") and room codes. Everything else moved to sans.
 */
val TechLabel = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 11.5.sp,
    letterSpacing = 0.26.em,
)

/**
 * Small label / eyebrow style. Was widely spaced JetBrains Mono, which read as
 * dated and noisy across ~260 labels; now Inter SemiBold with light tracking,
 * so uppercase eyebrows stay crisp and modern. Name kept to avoid churn.
 */
val MonoLabel = TextStyle(
    fontFamily = DisplayFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    letterSpacing = 0.06.em,
)

val MonoLabelSmall = MonoLabel.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.05.em)
val StackdTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.02).em,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em,
    ),
    // ExtraBold rather than Bold: 700 isn't among the bundled weights, so Bold
    // would be synthesised. The web's headings use font-extrabold anyway.
    headlineLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    // Buttons and actions: sans SemiBold, lightly tracked (was spaced mono).
    labelLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.08.em,
    ),
    labelMedium = MonoLabel,
    labelSmall = MonoLabelSmall,
)

/**
 * Editorial serif for featured values/titles, as the web uses its serif stack
 * ("Iowan Old Style", "Palatino Linotype", Palatino, Georgia) for "+10 XP
 * waiting", "P0" and Atlas titles. Bundled TeX Gyre Pagella is the Palatino
 * design the web resolves to on Windows/Android browsers, so app and site
 * render the same letterforms. GUST Font License (free to redistribute
 * unmodified); source: CTAN fonts/tex-gyre.
 */
val SerifFamily: FontFamily = FontFamily(
    Font(R.font.serif_regular, FontWeight.Normal),
    Font(R.font.serif_bold, FontWeight.Bold),
)
