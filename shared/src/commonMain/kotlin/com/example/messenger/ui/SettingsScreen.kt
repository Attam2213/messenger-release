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
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    
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
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    
    val isBackupProcessing by viewModel.isBackupProcessing.collectAsState()

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
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(str("app_updates"), style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.height(8.dp))

            if (updateInfo != null) {
                 Text(str("update_available") + " v${updateInfo!!.version}", color = MaterialTheme.colors.primary)
                 Text(updateInfo!!.changelog, style = MaterialTheme.typography.caption)
                 Spacer(modifier = Modifier.height(8.dp))
                 
                 if (isDownloading) {
                     Column(modifier = Modifier.fillMaxWidth()) {
                         LinearProgressIndicator(
                             progress = downloadProgress,
                             modifier = Modifier.fillMaxWidth()
                         )
                         Spacer(modifier = Modifier.height(4.dp))
                         Text(
                             text = "${(downloadProgress * 100).toInt()}%",
                             style = MaterialTheme.typography.caption,
                             modifier = Modifier.align(Alignment.End)
                         )
                     }
                 } else {
                     Button(onClick = { viewModel.downloadUpdate(updateInfo!!) }) {
                         Text(str("install"))
                     }
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

            // Backup & Restore
            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(str("backup_restore"), style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isBackupProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(str("export"))
                    }
                    Button(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(str("import"))
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

        if (showExportDialog) {
            ExportBackupDialog(
                onDismiss = { showExportDialog = false },
                onExport = { pwd -> 
                    viewModel.exportBackup(pwd)
                    showExportDialog = false
                },
                str = str
            )
        }
    
        if (showImportDialog) {
            ImportBackupDialog(
                onDismiss = { showImportDialog = false },
                onImport = { file, pwd ->
                    viewModel.importBackup(file, pwd)
                    showImportDialog = false
                },
                str = str
            )
        }
    }
}

@Composable
fun ExportBackupDialog(
    onDismiss: () -> Unit,
    onExport: (String?) -> Unit,
    str: (String) -> String
) {
    var password by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("export_backup")) },
        text = {
            Column {
                Text(str("backup_password_hint"))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onExport(password.ifEmpty { null }) }) {
                Text(str("ok"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str("cancel"))
            }
        }
    )
}

@Composable
fun ImportBackupDialog(
    onDismiss: () -> Unit,
    onImport: (String, String?) -> Unit,
    str: (String) -> String
) {
    var fileName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("import_backup")) },
        text = {
            Column {
                Text(str("backup_filename_hint"))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Filename") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(str("backup_password_hint"))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (fileName.isNotEmpty()) {
                        onImport(fileName, password.ifEmpty { null }) 
                    }
                },
                enabled = fileName.isNotEmpty()
            ) {
                Text(str("ok"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str("cancel"))
            }
        }
    )
}
