package com.example.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.messenger.viewmodel.SharedMessengerViewModel
import com.example.messenger.shared.db.MessageEntity
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: SharedMessengerViewModel,
    contactKey: String,
    isGroup: Boolean,
    onBack: () -> Unit
) {
    val messagesFlow = remember(contactKey, isGroup) {
        if (isGroup) {
            viewModel.getMessagesForGroup(contactKey)
        } else {
            viewModel.getMessagesForContact(contactKey)
        }
    }
    
    val messages by messagesFlow.collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberScaffoldState()
    
    // Auto-scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text(if (isGroup) "Group Chat" else "Chat") },
                actions = {
                    if (!isGroup) {
                        IconButton(onClick = { viewModel.initiateCall(contactKey, false) }) {
                            Icon(Icons.Default.Call, contentDescription = "Audio Call")
                        }
                        IconButton(onClick = { viewModel.initiateCall(contactKey, true) }) {
                            Icon(Icons.Default.VideoCall, contentDescription = "Video Call")
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        isMe = message.fromPublicKey == viewModel.myPublicKey,
                        viewModel = viewModel
                    )
                }
            }
            
            Divider()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message") },
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val textToSend = inputText
                            inputText = ""
                            if (isGroup) {
                                viewModel.sendGroupMessage(contactKey, textToSend) { success, error ->
                                    if (!success) {
                                        scope.launch {
                                            scaffoldState.snackbarHostState.showSnackbar("Error: ${error ?: "Unknown error"}")
                                        }
                                    }
                                }
                            } else {
                                viewModel.sendMessage(contactKey, textToSend, null) { success, error ->
                                     if (!success) {
                                         scope.launch {
                                             scaffoldState.snackbarHostState.showSnackbar("Error: ${error ?: "Unknown error"}")
                                         }
                                     }
                                }
                            }
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colors.primary)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    viewModel: SharedMessengerViewModel
) {
    var decryptedContent by remember { mutableStateOf(message.encryptedContent) }
    
    LaunchedEffect(message.encryptedContent, message.fromPublicKey) {
        try {
             val decrypted = viewModel.decryptMessage(message.encryptedContent, message.fromPublicKey)
             if (decrypted.content.isNotEmpty()) {
                 decryptedContent = decrypted.content
             }
        } catch (e: Exception) {
            decryptedContent = "[Encrypted]"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            backgroundColor = if (isMe) MaterialTheme.colors.primary else Color.LightGray,
            contentColor = if (isMe) Color.White else Color.Black,
            elevation = 1.dp
        ) {
            Text(
                text = decryptedContent,
                modifier = Modifier.padding(8.dp)
            )
        }
        Text(
            text = if (isMe) "Me" else message.fromPublicKey.take(8),
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
