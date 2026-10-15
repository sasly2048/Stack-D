package app.stackd.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.Stackd
import kotlin.math.cos
import kotlin.math.sin

/**
 * Six-axis radar of the DNA traits — the web's `focus-radar.tsx`, drawn on one
 * Canvas: three guide rings, spokes, the filled trait polygon, and labels.
 */
@Composable
fun FocusRadar(traits: List<AnalyticsEngine.Trait>, modifier: Modifier = Modifier) {
    if (traits.isEmpty()) return
    val colors = Stackd.colors
    val accent = colors.accent
    val grid = colors.textPrimary.copy(alpha = 0.12f)
    val labelColor = colors.textMuted

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = size.minDimension / 2 * 0.72f
        val n = traits.size

        fun point(i: Int, fraction: Float): Offset {
            val angle = Math.PI * 2 * i / n - Math.PI / 2
            return Offset(
                cx + (radius * fraction * cos(angle)).toFloat(),
                cy + (radius * fraction * sin(angle)).toFloat(),
            )
        }

        // Guide rings + spokes.
        listOf(0.33f, 0.66f, 1f).forEach { ring ->
            val path = Path()
            for (i in 0..n) {
                val p = point(i % n, ring)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, grid, style = Stroke(width = 1.dp.toPx()))
        }
        for (i in 0 until n) {
            drawLine(grid, Offset(cx, cy), point(i, 1f), strokeWidth = 1.dp.toPx())
        }

        // Trait polygon.
        val poly = Path()
        traits.forEachIndexed { i, t ->
            val p = point(i, t.value.coerceIn(0, 100) / 100f)
            if (i == 0) poly.moveTo(p.x, p.y) else poly.lineTo(p.x, p.y)
        }
        poly.close()
        drawPath(poly, accent.copy(alpha = 0.25f))
        drawPath(poly, accent, style = Stroke(width = 2.dp.toPx()))

        // Labels just outside each vertex.
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (labelColor.alpha * 255).toInt(),
                (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue * 255).toInt(),
            )
            textSize = 10.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        traits.forEachIndexed { i, t ->
            val p = point(i, 1.18f)
            drawContext.canvas.nativeCanvas.drawText(t.label, p.x, p.y, paint)
        }
    }
}

private val RADAR_TIPS = mapOf(
    "Depth" to "Try one longer Stack this week.",
    "Precision" to "Hold each Stack all the way through to lift your score.",
    "Discipline" to "Pick a length you can hold clean, then build from there.",
    "Flow" to "Protect one quiet window for an uninterrupted Stack.",
    "Consistency" to "Try shorter daily Stacks.",
    "Volume" to "Add one more Stack to your week.",
)

/** "Strongest: X · Weakest: Y" plus a tip for the weakest axis; null when there's no shape yet. */
internal fun radarReading(traits: List<AnalyticsEngine.Trait>): Pair<String, String?>? {
    val strongest = traits.maxByOrNull { it.value } ?: return null
    val weakest = traits.minByOrNull { it.value } ?: return null
    if (strongest.value == weakest.value) return null
    return "Strongest: ${strongest.label} · Weakest: ${weakest.label}" to RADAR_TIPS[weakest.label]
}

/** One interpretation line under the radar so the shape turns into an action. */
@Composable
fun FocusRadarReading(traits: List<AnalyticsEngine.Trait>) {
    val (line, tip) = radarReading(traits) ?: return
    val colors = Stackd.colors
    Text(
        line,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textPrimary,
        fontWeight = FontWeight.SemiBold,
    )
    tip?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
    }
}
