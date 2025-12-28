package com.example.messenger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.messenger.viewmodel.SharedMessengerViewModel
import com.example.messenger.domain.model.AuthRequest

@Composable
fun AuthRequestsScreen(
    viewModel: SharedMessengerViewModel,
    onBack: () -> Unit
) {
    val requests by viewModel.authRequests.collectAsState()
    val language by viewModel.language.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.get("auth_requests", language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = Strings.get("cancel", language))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(requests) { request ->
                AuthRequestItem(
                    request = request,
                    language = language,
                    onAccept = { viewModel.acceptAuthRequest(request) },
                    onReject = { viewModel.rejectAuthRequest(request) }
                )
            }
        }
    }
}

@Composable
fun AuthRequestItem(
    request: AuthRequest,
    language: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${Strings.get("device", language)}: ${request.model}")
            Text("${Strings.get("public_key", language)}: ${request.publicKey.take(8)}...", style = MaterialTheme.typography.caption)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = onAccept) { Text(Strings.get("accept", language)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onReject, 
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                ) { 
                    Text(Strings.get("reject", language)) 
                }
            }
        }
    }
}
