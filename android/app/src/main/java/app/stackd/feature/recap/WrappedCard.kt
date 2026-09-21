package app.stackd.feature.recap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import app.stackd.data.recap.WrappedStats
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the shareable Wrapped card, a 1080×1350 image — the Android port of
 * the web's `drawCard`. Same layout, same colours, same copy, so a card shared
 * from either platform is recognisably the same artifact.
 *
 * Kept in native android.graphics (not Compose Canvas) because the output is a
 * Bitmap headed for a file and the share sheet, not an on-screen composable —
 * drawing straight to the Bitmap skips a capture round-trip.
 */
object WrappedCard {

    private const val EMBER = 0xFFF0A968.toInt()
    private const val OBSIDIAN = 0xFF0A0A0A.toInt()
    private const val SILVER = 0xFFE2E2E2.toInt()
    private const val W = 1080
    private const val H = 1350

    fun render(stats: WrappedStats): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val mono = Typeface.MONOSPACE
        val serif = Typeface.create(Typeface.SERIF, Typeface.BOLD)

        c.drawColor(OBSIDIAN)

        // Ember glow behind the headline number.
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                540f, 420f, 720f,
                intArrayOf(0x33F0A968, 0x000A0A0A), floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), this)
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.textAlign = Paint.Align.CENTER
        p.color = EMBER
        p.typeface = mono
        p.textSize = 26f
        p.letterSpacing = 0.18f
        val header = "STACK WRAPPED · ${if (stats.rolling) "LAST 12 MONTHS" else stats.year}"
        c.drawText(header, 540f, 140f, p)
        p.letterSpacing = 0f

        p.color = SILVER
        p.typeface = serif
        p.textSize = 56f
        c.drawText(stats.displayName, 540f, 230f, p)

        p.color = EMBER
        p.textSize = 220f
        c.drawText(stats.totalHours.toString(), 540f, 470f, p)
        p.color = SILVER
        p.typeface = mono
        p.textSize = 32f
        c.drawText("HOURS HELD", 540f, 525f, p)

        val rows = listOf(
            "SESSIONS" to stats.totalSessions.toString(),
            "XP EARNED" to stats.totalXp.toString(),
            "LONGEST SESSION" to "${stats.longestSessionMinutes} min",
            "BEST STREAK" to "${stats.bestStreak} days",
            "PEAK DAY" to stats.topWeekday,
            "PEAK HOUR" to "${stats.peakHour.toString().padStart(2, '0')}:00",
            "UNBROKEN" to stats.perfectSessions.toString(),
            "TOP ALLY" to (stats.topCollaborator?.name ?: "—"),
        )
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x12FFFFFF; strokeWidth = 1f
        }
        var y = 640f
        for ((label, value) in rows) {
            p.textAlign = Paint.Align.LEFT
            p.color = 0x73E2E2E2
            p.typeface = mono
            p.textSize = 24f
            c.drawText(label, 120f, y, p)
            p.textAlign = Paint.Align.RIGHT
            p.color = SILVER
            p.typeface = serif
            p.textSize = 34f
            c.drawText(value, 960f, y, p)
            c.drawLine(120f, y + 22f, 960f, y + 22f, line)
            y += 74f
        }

        p.textAlign = Paint.Align.CENTER
        stats.personality?.let {
            p.color = EMBER
            p.typeface = serif
            p.textSize = 36f
            c.drawText(it, 540f, y + 40f, p)
        }

        p.color = 0x66E2E2E2
        p.typeface = mono
        p.textSize = 24f
        c.drawText("TOP ${maxOf(1, 100 - stats.percentile)}% OF STACKERS", 540f, 1230f, p)
        p.color = SILVER
        p.textSize = 26f
        p.letterSpacing = 0.3f
        c.drawText("STACK'D", 540f, 1285f, p)

        return bmp
    }

    /**
     * Renders the card, writes it to cacheDir/shared, and launches the system
     * share sheet. Returns false if the write fails so the caller can surface
     * it rather than opening an empty share sheet.
     */
    fun share(context: Context, stats: WrappedStats): Boolean {
        return runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "stackd-wrapped.png")
            FileOutputStream(file).use { render(stats).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, "Stack Wrapped")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, "Share Wrapped").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }
}
