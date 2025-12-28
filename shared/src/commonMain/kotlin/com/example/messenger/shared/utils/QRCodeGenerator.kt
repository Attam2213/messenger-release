package com.example.messenger.shared.utils

import androidx.compose.ui.graphics.ImageBitmap

expect object QRCodeGenerator {
    fun generateQRCode(content: String, size: Int = 512): ImageBitmap?
}
