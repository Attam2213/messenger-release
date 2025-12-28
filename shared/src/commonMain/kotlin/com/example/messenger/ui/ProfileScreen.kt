package com.example.messenger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.messenger.shared.utils.QRCodeGenerator
import com.example.messenger.viewmodel.SharedMessengerViewModel

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
fun ProfileScreen(
    viewModel: SharedMessengerViewModel,
    onBack: () -> Unit
) {
    val myPublicKey = viewModel.myPublicKey
    val language by viewModel.language.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val qrCode = remember(myPublicKey) {
        QRCodeGenerator.generateQRCode(myPublicKey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.get("my_profile", language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.get("cancel", language))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(Strings.get("my_public_key", language), style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (qrCode != null) {
                Image(
                    bitmap = qrCode,
                    contentDescription = Strings.get("public_key", language),
                    modifier = Modifier.size(256.dp)
                )
            } else {
                Text(Strings.get("qr_error", language))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = myPublicKey,
                onValueChange = {},
                readOnly = true,
                label = { Text(Strings.get("public_key", language)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = {
                clipboardManager.setText(AnnotatedString(myPublicKey))
            }) {
                Text(Strings.get("copy_clipboard", language))
            }
        }
    }
}
