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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import com.example.messenger.domain.model.DecryptedContent

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
    
    var contactName by remember { mutableStateOf("") }
    var showEditNameDialog by remember { mutableStateOf(false) }
    
    val isRecording by viewModel.isRecording.collectAsState()

    LaunchedEffect(contactKey) {
        if (!isGroup) {
            val contact = viewModel.getContact(contactKey)
            contactName = contact?.name ?: ""
        }
    }

    LaunchedEffect(contactKey, messages) {
        if (!isGroup) {
            viewModel.markAsRead(contactKey)
        }
    }

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
                title = { Text(if (isGroup) "Group Chat" else contactName.ifEmpty { "Chat" }) },
                actions = {
                    if (!isGroup) {
                        if (contactName.startsWith("Unknown")) {
                            IconButton(onClick = { showEditNameDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Contact")
                            }
                        }
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
                if (isRecording) {
                    Text(
                        text = "Recording...",
                        color = Color.Red,
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                    )
                    IconButton(onClick = { viewModel.cancelRecording() }) {
                        Icon(Icons.Default.Stop, contentDescription = "Cancel", tint = Color.Gray)
                    }
                    IconButton(onClick = { viewModel.stopAndSendAudio(contactKey) }) {
                        Icon(Icons.Default.Send, contentDescription = "Send Audio", tint = MaterialTheme.colors.primary)
                    }
                } else {
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
                    
                    if (inputText.isBlank()) {
                        IconButton(onClick = { viewModel.startRecording() }) {
                            Icon(Icons.Default.Mic, contentDescription = "Record", tint = MaterialTheme.colors.primary)
                        }
                    } else {
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
    }
    if (showEditNameDialog) {
        EditContactNameDialog(
            currentName = "",
            onDismiss = { showEditNameDialog = false },
            onSave = { newName ->
                viewModel.updateContactName(contactKey, newName) { success ->
                    if (success) {
                        contactName = newName
                        showEditNameDialog = false
                    }
                }
            }
        )
    }
}

@Composable
fun EditContactNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Contact") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") }
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    viewModel: SharedMessengerViewModel
) {
    var decryptedContent by remember { mutableStateOf<DecryptedContent?>(null) }
    var rawContent by remember { mutableStateOf(message.encryptedContent) }
    
    LaunchedEffect(message.encryptedContent, message.fromPublicKey) {
        try {
             val decrypted = viewModel.decryptMessage(message.encryptedContent, message.fromPublicKey)
             decryptedContent = decrypted
             if (decrypted.type == "TEXT") {
                rawContent = decrypted.content
             }
        } catch (e: Exception) {
            rawContent = "[Encrypted]"
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
            Column(modifier = Modifier.padding(8.dp)) {
                if (decryptedContent?.type == "AUDIO") {
                    val isPlaying by viewModel.isPlaying.collectAsState()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { 
                                if (isPlaying) viewModel.stopAudio() 
                                else viewModel.playAudio(decryptedContent?.content ?: "")
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Stop" else "Play",
                                tint = if (isMe) Color.White else Color.Black
                            )
                        }
                        Text(
                            text = "Voice Message",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                } else {
                    Text(
                        text = rawContent,
                        modifier = Modifier.padding(0.dp)
                    )
                }
            }
        }
        Text(
            text = if (isMe) "Me" else message.fromPublicKey.take(8),
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
