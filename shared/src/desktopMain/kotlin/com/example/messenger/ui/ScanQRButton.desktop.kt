package com.example.messenger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.IconButton
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner

@Composable
actual fun ScanQRButton(
    modifier: Modifier,
    onResult: (String) -> Unit
) {
    // Desktop not implemented
    IconButton(
        onClick = { /* Not supported */ },
        modifier = modifier,
        enabled = false
    ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
    }
}
