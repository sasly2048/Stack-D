package app.stackd.feature.room

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.stackd.R
import java.io.File
import java.io.FileOutputStream

/**
 * The post-session share image (1080×1350): the ceremony's headline, the
 * session line and score over the ember glow and a resting phone stack. Drawn
 * straight to a Bitmap like [app.stackd.feature.recap.WrappedCard], so the two
 * share artifacts read as one family.
 */
object SessionShareCard {

    private const val EMBER = 0xFFF0A968.toInt()
    private const val GROUND = 0xFF0E0C0A.toInt()
    private const val IVORY = 0xFFEDE6DA.toInt()
    private const val MUTED = 0x99EDE6DA.toInt()
    private const val W = 1080
    private const val H = 1350

    data class Content(val headline: String, val line: String, val score: String)

    fun render(context: Context, content: Content): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val serif = ResourcesCompat.getFont(context, R.font.serif_regular) ?: Typeface.SERIF
        c.drawColor(GROUND)

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(540f, 470f, 760f, intArrayOf(0x40F0A968, 0x000E0C0A), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), this)
        }

        // Four phones at rest, slightly fanned, the app's stack motif.
        val phone = Paint(Paint.ANTI_ALIAS_FLAG)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x66F0A968 }
        for (i in 3 downTo 0) {
            c.save()
            c.rotate((i - 1.5f) * 4f, 540f, 420f)
            val r = RectF(450f, 250f - i * 10f, 630f, 590f - i * 10f)
            phone.color = if (i == 0) 0xFF1D1915.toInt() else 0xFF15120F.toInt()
            c.drawRoundRect(r, 34f, 34f, phone)
            c.drawRoundRect(r, 34f, 34f, edge)
            c.restore()
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        p.typeface = serif; p.color = IVORY; p.textSize = 104f
        c.drawText(content.headline, 540f, 790f, p)
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); p.color = MUTED; p.textSize = 40f
        c.drawText(content.line, 540f, 870f, p)
        p.typeface = serif; p.color = EMBER; p.textSize = 56f
        c.drawText(content.score, 540f, 980f, p)

        p.typeface = Typeface.MONOSPACE; p.color = IVORY; p.textSize = 28f; p.letterSpacing = 0.3f
        c.drawText("STACK'D", 540f, 1230f, p)
        p.letterSpacing = 0f; p.color = MUTED; p.textSize = 24f
        c.drawText("stackd.raghav.studio", 540f, 1275f, p)
        return bmp
    }

    /** Writes the card to cacheDir/shared and opens the share sheet; false if that fails. */
    fun share(context: Context, content: Content): Boolean = runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "stackd-session.png")
        FileOutputStream(file).use { render(context, content).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share this stack").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
