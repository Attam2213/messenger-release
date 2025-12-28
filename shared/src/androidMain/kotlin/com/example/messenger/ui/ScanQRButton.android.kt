package com.example.messenger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.IconButton
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.activity.compose.rememberLauncherForActivityResult

@Composable
actual fun ScanQRButton(
    modifier: Modifier,
    onResult: (String) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            onResult(result.contents)
        }
    }

    IconButton(
        onClick = {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scan a QR Code")
            options.setCameraId(0) // Use a specific camera of the device
            options.setBeepEnabled(false)
            options.setBarcodeImageEnabled(true)
            launcher.launch(options)
        },
        modifier = modifier
    ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
    }
}
