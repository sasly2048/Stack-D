package app.stackd.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Web app's radius scale, base --radius: 0.625rem (10px).
// sm = base-6, md = base-2, lg = base, xl = base+4, 2xl = base+8.
val RadiusSm = RoundedCornerShape(4.dp)
val RadiusMd = RoundedCornerShape(8.dp)
val RadiusLg = RoundedCornerShape(10.dp)
val RadiusXl = RoundedCornerShape(14.dp)
val Radius2Xl = RoundedCornerShape(18.dp)
val RadiusFull = RoundedCornerShape(percent = 50)

val StackdShapes = Shapes(
    extraSmall = RadiusSm,
    small = RadiusMd,
    medium = RadiusLg,
    large = RadiusXl,
    extraLarge = Radius2Xl,
)
