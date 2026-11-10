package com.mowazze3.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.mowazze3.ui.theme.MowazzeTheme

private fun encodeQr(content: String): BitMatrix = QRCodeWriter().encode(
    content,
    BarcodeFormat.QR_CODE,
    0,
    0,
    // UTF-8 because network names can be Arabic. The margin is the quiet zone scanners need.
    mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2),
)

/** Always dark on white, even in dark mode, so it stays scannable. Never mirrored in RTL. */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { encodeQr(content) }
    val background = MowazzeTheme.extra.qrBackground
    val foreground = MowazzeTheme.extra.qrForeground
    Canvas(modifier) {
        drawRect(background)
        val modules = matrix.width
        scale(size.minDimension / modules, pivot = Offset.Zero) {
            for (y in 0 until modules) {
                for (x in 0 until modules) {
                    if (matrix[x, y]) drawRect(foreground, topLeft = Offset(x.toFloat(), y.toFloat()), size = Size(1f, 1f))
                }
            }
        }
    }
}
