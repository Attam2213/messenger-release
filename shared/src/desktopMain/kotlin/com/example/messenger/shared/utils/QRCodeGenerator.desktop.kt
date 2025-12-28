package com.example.messenger.shared.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

actual object QRCodeGenerator {
    actual fun generateQRCode(content: String, size: Int): ImageBitmap? {
        return try {
            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            
            val width = bitMatrix.width
            val height = bitMatrix.height
            
            val pixels = ByteArray(width * height * 4)
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val offset = (y * width + x) * 4
                    val color = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    
                    pixels[offset] = (color shr 16 and 0xFF).toByte() // R
                    pixels[offset + 1] = (color shr 8 and 0xFF).toByte()  // G
                    pixels[offset + 2] = (color and 0xFF).toByte()         // B
                    pixels[offset + 3] = (color shr 24 and 0xFF).toByte()  // A
                }
            }

            val bitmap = Bitmap()
            bitmap.allocPixels(ImageInfo.makeS32(width, height, ColorAlphaType.OPAQUE))
            bitmap.installPixels(pixels)
            bitmap.asComposeImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
