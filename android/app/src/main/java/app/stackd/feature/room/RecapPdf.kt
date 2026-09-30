package app.stackd.feature.room

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import app.stackd.data.ai.SessionRecap
import app.stackd.data.ai.SessionRecapInput
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Session recap as a one-page PDF — web session-recap-card exportPdf, same
 * letter-size layout, rendered on-device with PdfDocument (the file never
 * leaves the phone until the user shares it).
 */
object RecapPdf {
    private const val W = 612 // US letter, points
    private const val H = 792
    private const val M = 56f

    fun share(context: Context, recap: SessionRecap, input: SessionRecapInput, displayName: String?): Boolean =
        runCatching {
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
            draw(page.canvas, recap, input, displayName)
            doc.finishPage(page)
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safeCode = input.roomCode.filter { it.isLetterOrDigit() }
            val file = File(dir, "stackd-recap-$safeCode-${LocalDate.now()}.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, "Session recap").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess

    private fun paint(size: Float, rgb: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = rgb
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Draws wrapped text at (x, y-top) and returns the y below it. */
    private fun Canvas.block(text: String, p: TextPaint, x: Float, y: Float, width: Float, spacing: Float = 1.15f): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .build()
        save()
        translate(x, y)
        layout.draw(this)
        restore()
        return y + layout.height
    }

    private fun draw(c: Canvas, recap: SessionRecap, input: SessionRecapInput, displayName: String?) {
        val inner = W - M * 2
        val grey = Color.rgb(140, 140, 140)
        val ink = Color.rgb(30, 30, 30)
        val rule = Paint().apply { color = Color.rgb(220, 220, 220); strokeWidth = 0.5f }

        // Header bar
        c.drawRect(0f, 0f, W.toFloat(), 40f, Paint().apply { color = Color.rgb(15, 15, 17) })
        c.drawText("STACK'D · SESSION RECAP", M, 26f, paint(10f, Color.rgb(226, 226, 226), bold = true))
        val codePaint = paint(8f, Color.rgb(160, 160, 160)).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("ROOM ${input.roomCode}", W - M, 26f, codePaint)

        var y = 56f
        y = c.block(recap.title, paint(22f, Color.rgb(20, 20, 20), bold = true), M, y, inner) + 8f

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        c.drawText("${displayName?.let { "$it · " } ?: ""}$stamp", M, y + 9f, paint(9f, Color.rgb(120, 120, 120)))
        y += 24f

        // Metric row
        c.drawLine(M, y, W - M, y, rule)
        y += 14f
        val metrics = listOf(
            "SCORE" to "${recap.score.takeIf { it > 0 } ?: input.score}/100",
            "TIER" to input.tier,
            "DURATION" to "${Math.round(input.durationSeconds / 60.0)} min",
            "XP" to "+${recap.xp.takeIf { it > 0 } ?: input.xp}",
            "ANOMALIES" to input.breachesCount.toString(),
        )
        val cell = inner / metrics.size
        metrics.forEachIndexed { i, (label, value) ->
            val x = M + i * cell
            c.drawText(label, x, y + 7f, paint(7f, grey))
            c.drawText(value, x, y + 27f, paint(14f, Color.rgb(20, 20, 20), bold = true))
        }
        y += 44f
        c.drawLine(M, y, W - M, y, rule)
        y += 24f

        fun section(label: String) {
            c.drawText(label, M, y + 9f, paint(9f, grey, bold = true))
            y += 16f
        }
        val body = paint(11f, ink)

        section("SUMMARY")
        y = c.block(recap.summary, body, M, y, inner) + 20f

        section("REFLECTIONS")
        recap.reflections.forEach { y = c.block("— $it", body, M + 4f, y, inner - 12f) + 6f }
        y += 12f

        section("NEXT STEP")
        c.block(recap.nextStep, body, M, y, inner)

        // Footer
        c.drawLine(M, H - 60f, W - M, H - 60f, rule)
        c.drawText("Non-digital space is a human right.", M, H - 40f, paint(8f, grey))
        c.drawText("stackd.app", W - M, H - 40f, paint(8f, grey).apply { textAlign = Paint.Align.RIGHT })
    }
}
