package app.stackd.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a QR code — the Android shape of the web's `<QRCode>`
 * invite. Only `zxing-core` is on the classpath (no android-integration
 * BarcodeEncoder), so the BitMatrix is drawn to a Bitmap by hand: black
 * modules on a white quiet-zone, which every scanner expects regardless of
 * app theme. Sized in dp; the matrix is generated once per (content, px).
 */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val bitmap = remember(content, px) { encodeQr(content, px) }
    Box(
        modifier = modifier
            .size(size)
            .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR code for the room invite link",
                modifier = Modifier.size(size),
            )
        }
    }
}

/** BitMatrix → Bitmap. Returns null if encoding fails (e.g. empty content). */
private fun encodeQr(content: String, px: Int): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, px, px, hints)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.RGB_565)
    val black = android.graphics.Color.BLACK
    val white = android.graphics.Color.WHITE
    for (x in 0 until px) {
        for (y in 0 until px) {
            bmp.setPixel(x, y, if (matrix[x, y]) black else white)
        }
    }
    bmp
}.getOrNull()
