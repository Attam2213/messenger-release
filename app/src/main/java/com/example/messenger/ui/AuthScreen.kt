package com.example.messenger.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.messenger.R
import com.example.messenger.crypto.CryptoManager
import com.example.messenger.utils.QrUtils
import org.json.JSONObject
import com.example.messenger.repository.MessengerRepository
import com.example.messenger.network.MessageRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.Base64
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.messenger.shared.utils.SharedSettingsManager
import kotlinx.coroutines.withContext

enum class AuthStep {
    Selection,
    SetupEncryption,
    CreateIdentity,
    ImportIdentity,
    WaitingForConfirmation
}

@Composable
fun AuthScreen(
    cryptoManager: CryptoManager,
    settingsManager: SharedSettingsManager,
    repository: MessengerRepository,
    onAuthSuccess: () -> Unit,
    onScanRequest: () -> Unit,
    scanResult: String?
) {
    var currentStep by rememberSaveable { mutableStateOf(AuthStep.Selection) }
    var rawJsonKeys by rememberSaveable { mutableStateOf("") }
    var tempPrivKey by rememberSaveable { mutableStateOf("") }
    var importText by rememberSaveable { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) } // Add loading state
    var showDecryptDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Handle scan result
    LaunchedEffect(scanResult) {
        if (!scanResult.isNullOrEmpty()) {
            importText = scanResult
            currentStep = AuthStep.ImportIdentity
        }
    }

    // Проверяем наличие ключей при запуске
    LaunchedEffect(Unit) {
        if (cryptoManager.hasIdentity()) {
            // If keys exist, we assume the user is authorized.
            // We skip the confirmation check to avoid race conditions or stuck states.
            settingsManager.setAccountConfirmed(true)
            onAuthSuccess()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentStep) {
                AuthStep.Selection -> {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            val kp = cryptoManager.createIdentityInMemory()
                            val priv = kp.privateKeyBase64
                            tempPrivKey = priv
                            currentStep = AuthStep.SetupEncryption
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(stringResource(R.string.auth_create_identity))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { currentStep = AuthStep.ImportIdentity },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(stringResource(R.string.auth_import_identity))
                    }
                }

                AuthStep.SetupEncryption -> {
                    Text(stringResource(R.string.auth_setup_enc_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.auth_setup_enc_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    var confirmPassword by remember { mutableStateOf("") }
                    var passwordError by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it 
                            passwordError = false
                        },
                        label = { Text(stringResource(R.string.profile_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { 
                            confirmPassword = it 
                            passwordError = false
                        },
                        label = { Text(stringResource(R.string.profile_confirm_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = passwordError
                    )
                    if (passwordError) {
                        Text(
                            stringResource(R.string.profile_password_mismatch),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (password.isNotEmpty() && password == confirmPassword) {
                                val jsonToEncrypt = JSONObject().apply { 
                                    put("priv", tempPrivKey) 
                                }.toString()
                                scope.launch {
                                    rawJsonKeys = cryptoManager.encryptWithPassword(jsonToEncrypt, password)
                                    currentStep = AuthStep.CreateIdentity
                                }
                            } else {
                                passwordError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = password.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.auth_setup_enc_btn))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = {
                            rawJsonKeys = JSONObject().apply {
                                put("priv", tempPrivKey)
                            }.toString()
                            currentStep = AuthStep.CreateIdentity
                        }
                    ) {
                        Text(stringResource(R.string.auth_setup_skip_btn), color = MaterialTheme.colorScheme.error)
                    }
                }

                AuthStep.CreateIdentity -> {
                    var showZoomedQr by remember { mutableStateOf(false) }

                    if (showZoomedQr) {
                        Dialog(onDismissRequest = { showZoomedQr = false }) {
                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val qrBitmap = remember(rawJsonKeys) {
                                        QrUtils.generateQrBitmap(rawJsonKeys)
                                    }
                                    if (qrBitmap != null) {
                                        Image(
                                            bitmap = qrBitmap, 
                                            contentDescription = "Private Key QR Zoomed", 
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clickable { showZoomedQr = false }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(stringResource(R.string.auth_back), modifier = Modifier.clickable { showZoomedQr = false })
                                }
                            }
                        }
                    }

                    Text(stringResource(R.string.auth_your_secret_key), style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.auth_save_warning),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    if (!rawJsonKeys.trim().startsWith("{")) {
                         Spacer(modifier = Modifier.height(8.dp))
                         Text(
                             stringResource(R.string.auth_key_encrypted_hint),
                             style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.primary
                         )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // QR Code
                    val qrBitmap = remember(rawJsonKeys) {
                        if (rawJsonKeys.length > 500) {
                             // QR might be too dense, but we try anyway. 
                             // If it's too big, it might cause lag. 
                             // Optimization: generate on background thread or show placeholder?
                             // For now, let's keep it simple but be aware.
                             QrUtils.generateQrBitmap(rawJsonKeys)
                        } else {
                             QrUtils.generateQrBitmap(rawJsonKeys)
                        }
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap, 
                            contentDescription = "Private Key QR",
                            modifier = Modifier
                                .size(200.dp)
                                .clickable { showZoomedQr = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = rawJsonKeys,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.auth_raw_json_key)) },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(rawJsonKeys))
                            Toast.makeText(context, context.getString(R.string.auth_keys_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.auth_copy_keys))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    cryptoManager.importIdentity(tempPrivKey)
                                    if (cryptoManager.hasIdentity()) {
                                        settingsManager.setAccountConfirmed(true)
                                        withContext(Dispatchers.Main) {
                                            onAuthSuccess()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Failed to save identity", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.auth_i_saved_it))
                    }
                }

                AuthStep.ImportIdentity -> {
                    Text(stringResource(R.string.auth_import_identity), style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onScanRequest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.auth_scan_qr))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.auth_or))
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text(stringResource(R.string.auth_paste_json)) },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (isSending) return@Button
                            
                            val cleanImportText = importText.replace("\\s".toRegex(), "")
                            
                            scope.launch {
                                try {
                                    val priv: String = try {
                                        val json = JSONObject(cleanImportText)
                                        json.getString("priv")
                                    } catch (e: Exception) {
                                        // Fallback: assume raw private key string
                                        cleanImportText
                                    }
                                    
                                    // Attempt to import. If this fails, it might be encrypted.
                                    // IMPORTANT: For plain text import, we assume it is confirmed since we have the private key
                                    cryptoManager.importIdentity(priv)
                                    settingsManager.setAccountConfirmed(true)
                                    
                                    // If import succeeds, proceed to success
                                    withContext(Dispatchers.Main) {
                                        onAuthSuccess()
                                    }
                                } catch (e: Exception) {
                                    // Import failed, likely because key is encrypted or invalid
                                    showDecryptDialog = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.auth_import_continue))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { currentStep = AuthStep.Selection }) {
                        Text(stringResource(R.string.auth_back))
                    }
                }
                
                AuthStep.WaitingForConfirmation -> {
                    val myKey = cryptoManager.getMyPublicKeyString()
                    val myHash = cryptoManager.getHashFromPublicKeyString(myKey)

                    Text(stringResource(R.string.auth_waiting_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ID: ${myHash.take(10)}...", 
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.auth_waiting_message),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Polling for ACK
                    LaunchedEffect(Unit) {
                        while(true) {
                            try {
                                val messages = repository.checkMessages(myHash)
                                
                                for (msg in messages) {
                                    try {
                                        // Try to parse as JSON first (for AUTH_ACK)
                                        if (msg.content.trim().startsWith("{")) {
                                            val jsonPayload = JSONObject(msg.content)
                                            val type = jsonPayload.optString("type")
                                            
                                            if (type == "AUTH_ACK") {
                                                val data = jsonPayload.getString("data")
                                                // Decrypt inner data
                                                val innerDecrypted = cryptoManager.decrypt(data)
                                                val innerJson = JSONObject(innerDecrypted)
                                                val allowedDeviceId = innerJson.getString("deviceId")
                                                
                                                if (allowedDeviceId == deviceId) {
                                                    settingsManager.setAccountConfirmed(true)
                                                    onAuthSuccess()
                                                }
                                            }
                                        } else {
                                            // Fallback for older/other formats if necessary
                                            // But for AUTH_ACK it should be JSON
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                            delay(3000)
                        }
                    }
                    
                    Button(
                        onClick = { currentStep = AuthStep.Selection },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.auth_back))
                    }
                }
            }
        }
    }

    if (showDecryptDialog) {
        Dialog(onDismissRequest = { showDecryptDialog = false }) {
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.auth_decrypt_password_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.auth_decrypt_password_message), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.profile_password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val cleanImportText = importText.replace("\\s".toRegex(), "")
                                    val decryptedJson = cryptoManager.decryptWithPassword(cleanImportText, password)
                                    val json = JSONObject(decryptedJson)
                                    val priv = json.getString("priv")
                                    
                                    cryptoManager.importIdentity(priv)
                                    showDecryptDialog = false
                                    settingsManager.setAccountConfirmed(true)
                                    onAuthSuccess() // Skip confirmation since user has password
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, context.getString(R.string.auth_password_incorrect), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.auth_decrypt_btn))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showDecryptDialog = false }) {
                        Text(stringResource(R.string.contact_cancel_btn))
                    }
                }
            }
        }
    }
}
