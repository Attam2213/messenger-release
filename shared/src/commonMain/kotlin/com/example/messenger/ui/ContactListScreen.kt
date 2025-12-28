package com.example.messenger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.messenger.viewmodel.SharedMessengerViewModel
import com.example.messenger.shared.db.ContactEntity
import kotlinx.coroutines.launch

@Composable
fun ContactListScreen(
    viewModel: SharedMessengerViewModel,
    onContactClick: (String, Boolean) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val pollingStatus by viewModel.pollingStatus.collectAsState()
    
    // Simple state for now
    var searchQuery by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    Scaffold(
        scaffoldState = scaffoldState,
        drawerContent = {
            DrawerContent(
                viewModel = viewModel,
                onNavigate = onNavigate,
                closeDrawer = {
                    scope.launch { scaffoldState.drawerState.close() }
                }
            )
        },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Messenger")
                        Text(pollingStatus, style = MaterialTheme.typography.caption)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch { scaffoldState.drawerState.open() }
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshMessages() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Groups
            if (groups.isNotEmpty()) {
                item {
                    Text(
                        "Groups",
                        style = MaterialTheme.typography.subtitle1,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                items(groups) { group ->
                    ContactItem(
                        name = group.name,
                        key = group.groupId,
                        isGroup = true,
                        onClick = onContactClick
                    )
                }
            }
            
            // Contacts
            item {
                Text(
                    "Contacts",
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.padding(8.dp)
                )
            }
            items(contacts) { contact ->
                ContactItem(
                    name = contact.name,
                    key = contact.publicKey,
                    isGroup = false,
                    onClick = onContactClick
                )
            }
        }
        
        if (showDialog) {
            AddContactGroupDialog(
                contacts = contacts,
                onDismiss = { showDialog = false },
                onAddContact = { name, key ->
                    viewModel.addContact(name, key) { success, _ ->
                        if (success) showDialog = false
                    }
                },
                onCreateGroup = { name, members ->
                    viewModel.createGroup(name, members) { success, _ ->
                        if (success) showDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun ContactItem(
    name: String,
    key: String,
    isGroup: Boolean,
    onClick: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick(key, isGroup) },
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isGroup) Icons.Default.List else Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = name, style = MaterialTheme.typography.h6)
                Text(
                    text = key.take(8) + "...", 
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AddContactGroupDialog(
    contacts: List<ContactEntity>,
    onDismiss: () -> Unit,
    onAddContact: (String, String) -> Unit,
    onCreateGroup: (String, List<String>) -> Unit
) {
    var isGroupMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGroupMode) "Create Group" else "Add Contact") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(bottom = 16.dp)) {
                    TextButton(
                        onClick = { isGroupMode = false },
                        enabled = isGroupMode
                    ) {
                        Text("Contact")
                    }
                    TextButton(
                        onClick = { isGroupMode = true },
                        enabled = !isGroupMode
                    ) {
                        Text("Group")
                    }
                }
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isGroupMode) {
                    Text("Select Members:", style = MaterialTheme.typography.subtitle2)
                    LazyColumn(
                        modifier = Modifier.height(200.dp).fillMaxWidth()
                    ) {
                        items(contacts) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val current = selectedMembers.toMutableSet()
                                        if (current.contains(contact.publicKey)) {
                                            current.remove(contact.publicKey)
                                        } else {
                                            current.add(contact.publicKey)
                                        }
                                        selectedMembers = current
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedMembers.contains(contact.publicKey),
                                    onCheckedChange = null // Handled by Row click
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(contact.name)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = publicKey,
                            onValueChange = { publicKey = it },
                            label = { Text("Public Key") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ScanQRButton(
                            onResult = { result ->
                                publicKey = result
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isGroupMode) {
                        onCreateGroup(name, selectedMembers.toList())
                    } else {
                        onAddContact(name, publicKey)
                    }
                },
                enabled = name.isNotBlank() && (if (isGroupMode) selectedMembers.isNotEmpty() else publicKey.isNotBlank())
            ) {
                Text(if (isGroupMode) "Create" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
