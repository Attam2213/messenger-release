package com.example.messenger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ScanQRButton(
    modifier: Modifier = Modifier,
    onResult: (String) -> Unit
)
