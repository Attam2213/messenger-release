package com.example.messenger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.messenger.viewmodel.SharedMessengerViewModel

@Composable
fun SettingsScreen(
    viewModel: SharedMessengerViewModel,
    onBack: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }
    
    // Settings States
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    
    val panicDeleteContacts by viewModel.panicDeleteContacts.collectAsState()
    val panicDeleteMessages by viewModel.panicDeleteMessages.collectAsState()
    val panicDeleteKeys by viewModel.panicDeleteKeys.collectAsState()
    val autoLockTime by viewModel.autoLockTime.collectAsState()

    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()

    val str: (String) -> String = { Strings.get(it, language) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(str("settings")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance Section
            Text(str("appearance"), style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(str("theme"))
                TextButton(onClick = {
                    val newMode = when (themeMode) {
                        "system" -> "light"
                        "light" -> "dark"
                        else -> "system"
                    }
                    viewModel.setThemeMode(newMode)
                }) {
                    Text(str("theme_$themeMode"))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Language Section
            Text(str("language"), style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(str("language"))
                TextButton(onClick = {
                    val newLang = if (language == "ru") "en" else "ru"
                    viewModel.setLanguage(newLang)
                }) {
                    Text(if (language == "ru") "Русский" else "English")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            
            // Notifications Section
            Text(str("notifications"), style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(str("enable_notifications"))
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Security Section
            Text(str("security"), style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(str("panic_config"), style = MaterialTheme.typography.subtitle1)
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(str("delete_contacts"))
                Switch(
                    checked = panicDeleteContacts,
                    onCheckedChange = { viewModel.setPanicDeleteContacts(it) }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(str("delete_messages"))
                Switch(
                    checked = panicDeleteMessages,
                    onCheckedChange = { viewModel.setPanicDeleteMessages(it) }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(str("delete_keys"))
                Switch(
                    checked = panicDeleteKeys,
                    onCheckedChange = { viewModel.setPanicDeleteKeys(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(str("auto_lock"))
                TextButton(onClick = {
                    val newTime = when (autoLockTime) {
                        0L -> 60000L // 1 min
                        60000L -> 300000L // 5 min
                        else -> 0L // Disabled
                    }
                    viewModel.setAutoLockTime(newTime)
                }) {
                    val text = when (autoLockTime) {
                        0L -> str("disabled")
                        60000L -> "1 ${str("min")}"
                        300000L -> "5 ${str("min")}"
                        else -> "${autoLockTime / 60000} ${str("min")}"
                    }
                    Text(text)
                }
            }

            // App Updates
            Text(str("app_updates"), style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.height(8.dp))

            if (updateInfo != null) {
                 Text(str("update_available") + updateInfo!!.version)
                 Spacer(modifier = Modifier.height(8.dp))
                 Button(onClick = { viewModel.downloadUpdate(updateInfo!!) }) {
                     Text(str("install"))
                 }
            } else {
                 Button(
                     onClick = { viewModel.checkForUpdates() },
                     enabled = !isCheckingUpdate
                 ) {
                     if (isCheckingUpdate) {
                         CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colors.onPrimary)
                         Spacer(modifier = Modifier.width(8.dp))
                         Text(str("checking"))
                     } else {
                         Text(str("check_update"))
                     }
                 }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(str("danger_zone"), style = MaterialTheme.typography.h6, color = MaterialTheme.colors.error)
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(str("clear_all_data"), color = MaterialTheme.colors.onError)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text(str("clear_dialog_title")) },
                text = { Text(str("clear_dialog_text")) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllData()
                            showClearDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    ) {
                        Text(str("delete_all"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text(str("cancel"))
                    }
                }
            )
        }
    }
}
