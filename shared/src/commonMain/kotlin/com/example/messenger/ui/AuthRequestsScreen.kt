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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auth Requests") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(requests) { request ->
                AuthRequestItem(
                    request = request,
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
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Device: ${request.model}")
            Text("Key: ${request.publicKey.take(8)}...", style = MaterialTheme.typography.caption)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = onAccept) { Text("Accept") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onReject, 
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                ) { 
                    Text("Reject") 
                }
            }
        }
    }
}
