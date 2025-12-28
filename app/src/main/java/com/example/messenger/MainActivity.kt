package com.example.messenger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.messenger.crypto.CryptoManager
import com.example.messenger.ui.theme.MessengerTheme
import com.example.messenger.utils.LocaleManager
import com.example.messenger.shared.utils.SharedSettingsManager
import androidx.compose.foundation.isSystemInDarkTheme
import android.content.Intent
import android.provider.Settings
import com.vk.api.sdk.VK
import com.vk.api.sdk.auth.VKAuthenticationResult
import com.vk.api.sdk.auth.VKScope
import androidx.activity.result.ActivityResultLauncher
import com.example.messenger.utils.VkAuthManager
import com.example.messenger.ui.CallScreen
import com.example.messenger.ui.IncomingCallScreen
import com.example.messenger.webrtc.CallManager
import com.example.messenger.viewmodel.CallViewModel
import com.example.messenger.viewmodel.SharedMessengerViewModel
import com.example.messenger.ui.App as SharedApp
import com.example.messenger.infrastructure.AndroidNotificationHandler
import com.example.messenger.shared.utils.FileHandler

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

enum class AuthState {
    LOCKED,
    AUTHORIZED,
    SECURITY_REQUIRED
}

class MainActivity : AppCompatActivity() {
    private var authState by mutableStateOf(AuthState.LOCKED)
    private lateinit var cryptoManager: CryptoManager
    private lateinit var settingsManager: SharedSettingsManager
    private lateinit var authLauncher: ActivityResultLauncher<Collection<VKScope>>

    // Permission request launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            // Some permissions were denied - you might want to show a message
            android.util.Log.w("MainActivity", "Some permissions were denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request all permissions on first launch
        requestAllPermissions()
        
        authLauncher = VK.login(this) { result : VKAuthenticationResult ->
            when (result) {
                is VKAuthenticationResult.Success -> {
                    VkAuthManager.updateLoginState()
                }
                is VKAuthenticationResult.Failed -> {
                    VkAuthManager.updateLoginState()
                }
            }
        }
        
        // Initialize Locale from Shared Settings
        val app = application as MessengerApplication
        settingsManager = app.sharedSettingsManager
        cryptoManager = app.cryptoManager
        
        // Initial locale update
        LocaleManager.updateResources(this, settingsManager.language.value)
        
        // Observe language changes
        lifecycleScope.launch {
            settingsManager.language.collect { lang ->
                LocaleManager.updateResources(this@MainActivity, lang)
                // Recreate activity if needed, but Compose handles config changes usually?
                // For locale changes, it's often better to recreate to ensure all resources are reloaded.
                // However, doing it inside collect might cause loop if not careful.
                // For now, updateResources updates the configuration context.
            }
        }

        // Start Background Service for Message Sync
        val serviceIntent = Intent(this, com.example.messenger.service.MessengerService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            val themeMode by settingsManager.themeMode.collectAsState()
            val darkTheme = when(themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            MessengerTheme(darkTheme = darkTheme) {
                when (authState) {
                    AuthState.AUTHORIZED -> {
                        MainContent()
                    }
                    AuthState.LOCKED -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.main_locked_title), style = MaterialTheme.typography.h5)
                                Text(stringResource(R.string.main_unlock_subtitle), style = MaterialTheme.typography.subtitle1)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { authenticate() }) {
                                    Text(stringResource(R.string.main_unlock_btn))
                                }
                            }
                        }
                    }
                    AuthState.SECURITY_REQUIRED -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                Text("Требуется защита", style = MaterialTheme.typography.h5)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Для использования приложения необходимо установить PIN-код, пароль или графический ключ на устройстве.",
                                    style = MaterialTheme.typography.body1,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { openSecuritySettings() }) {
                                    Text("Настроить")
                                }
                            }
                        }
                    }
                }
            }
        }

        checkSecurity()
    }

    fun loginVk() {
        authLauncher.launch(arrayListOf(VKScope.WALL, VKScope.FRIENDS)) // VKScope.AUDIO access is restricted by VK
    }

    private fun checkSecurity() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                if (cryptoManager.hasIdentity()) {
                    authenticate()
                } else {
                    authState = AuthState.AUTHORIZED
                }
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                authState = AuthState.SECURITY_REQUIRED
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                // If device has no hardware support for biometrics.
                // If we have no identity, we should let the user onboard.
                if (cryptoManager.hasIdentity()) {
                     // If we have identity, we ideally want protection.
                     // But if the device literally has no hardware/support, we can't force it.
                     // We could fallback to a custom PIN, but for now let's allow access 
                     // or maybe show a warning?
                     // For now, to avoid blocking valid users on emulators/old phones:
                     authenticate() 
                } else {
                    authState = AuthState.AUTHORIZED
                }
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                authState = AuthState.SECURITY_REQUIRED
            }
            else -> {
                // For other errors (like STATUS_UNKNOWN), try to proceed or authenticate
                 if (cryptoManager.hasIdentity()) {
                    authenticate()
                } else {
                    authState = AuthState.AUTHORIZED
                }
            }
        }
    }

    private fun openSecuritySettings() {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        startActivity(intent)
    }

    private fun authenticate() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    try {
                        authState = AuthState.AUTHORIZED
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL || 
                        errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                        authState = AuthState.SECURITY_REQUIRED
                    }
                    // Show toast or UI feedback?
                    // For now, staying in LOCKED state is fine, user can retry.
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.main_biometric_title))
            .setSubtitle(getString(R.string.main_unlock_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            // If authentication fails to start, maybe fallback to security required?
            authState = AuthState.SECURITY_REQUIRED
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        
        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        // Microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        // Storage permissions (for Android 13+ use media permissions)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        // Notification permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Request all permissions at once
        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    val app = context.applicationContext as MessengerApplication
    val notificationHandler = remember { AndroidNotificationHandler(context) }
    val fileHandler = remember { FileHandler(context) }
    
    val sharedViewModel = remember {
        SharedMessengerViewModel(
            repository = app.repository,
            cryptoManager = app.cryptoManager,
            settingsManager = app.sharedSettingsManager,
            sendMessageUseCase = app.sendMessageUseCase,
            createGroupUseCase = app.createGroupUseCase,
            processMessageUseCase = app.processMessageUseCase,
            messageSynchronizationUseCase = app.messageSynchronizationUseCase,
            messageDecryptionUseCase = app.messageDecryptionUseCase,
            callHandler = app.androidCallHandler, 
            notificationHandler = notificationHandler,
            appUpdater = app.appUpdater,
            fileHandler = fileHandler,
            scope = app.applicationScope
        )
    }

    val callViewModel: CallViewModel = viewModel()
    val webRtcManager = app.webRtcManager
    val callState by callViewModel.callState.collectAsState()

    // We let SharedApp handle the onboarding flow (Welcome -> Language -> Identity -> Home)
    // based on settingsManager.isOnboardingCompleted
    
    Box(modifier = Modifier.fillMaxSize()) {
        SharedApp(viewModel = sharedViewModel)
        
        // Call Overlay
        when (val state = callState) {
            is com.example.messenger.webrtc.CallManager.CallState.Incoming -> {
                IncomingCallScreen(
                    callerName = state.fromKey.take(8),
                    onAccept = { callViewModel.acceptCall() },
                    onReject = { callViewModel.endCall() }
                )
            }
            is com.example.messenger.webrtc.CallManager.CallState.Outgoing, is com.example.messenger.webrtc.CallManager.CallState.Connected -> {
                CallScreen(
                    viewModel = callViewModel,
                    webRtcManager = webRtcManager,
                    onEndCall = { callViewModel.endCall() }
                )
            }
            else -> {}
        }
    }
}
