package com.example.messenger

import android.app.Application
import com.example.messenger.crypto.CryptoManager
import com.example.messenger.crypto.AndroidCryptoManager
import com.example.messenger.network.WebSocketManager
import com.example.messenger.repository.MessengerRepository
import com.example.messenger.shared.db.AppDatabase
import com.example.messenger.shared.db.DatabaseHelper
import com.example.messenger.shared.db.DatabaseDriverFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.vk.api.sdk.VK
import com.vk.api.sdk.VKTokenExpiredHandler

import com.example.messenger.shared.utils.AndroidSettingsStorage
import com.example.messenger.shared.utils.SharedSettingsManager
import com.example.messenger.infrastructure.AndroidWorkScheduler
import com.example.messenger.shared.utils.AppUpdater

import com.example.messenger.infrastructure.AndroidCallHandler

class MessengerApplication : Application() {

    // Application Scope for global coroutines (WebSocket, Sync)
    val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var sharedDatabase: AppDatabase
    lateinit var webSocketManager: WebSocketManager
    lateinit var repository: MessengerRepository
    lateinit var cryptoManager: CryptoManager
    lateinit var webRtcManager: com.example.messenger.webrtc.WebRtcManager
    lateinit var callManager: com.example.messenger.webrtc.CallManager
    
    // API Client (Shared Ktor)
    private val apiClient = com.example.messenger.network.MessengerApiClient()
    
    // Use Cases
    lateinit var processMessageUseCase: com.example.messenger.domain.usecase.ProcessMessageUseCase
    lateinit var messageSynchronizationUseCase: com.example.messenger.domain.usecase.MessageSynchronizationUseCase
    lateinit var sendMessageUseCase: com.example.messenger.domain.usecase.SendMessageUseCase
    lateinit var createGroupUseCase: com.example.messenger.domain.usecase.CreateGroupUseCase
    lateinit var messageDecryptionUseCase: com.example.messenger.domain.usecase.MessageDecryptionUseCase
    lateinit var workManager: androidx.work.WorkManager
    lateinit var sharedSettingsManager: SharedSettingsManager
    lateinit var appUpdater: AppUpdater

    lateinit var androidCallHandler: AndroidCallHandler

    override fun onCreate() {
        super.onCreate()
        VK.addTokenExpiredHandler(tokenTracker)
        
        // Initialize SQLDelight Database
        val driver = DatabaseDriverFactory(this).createDriver()
        sharedDatabase = DatabaseHelper.createDatabase(driver)
        
        cryptoManager = AndroidCryptoManager(this)
        webRtcManager = com.example.messenger.webrtc.WebRtcManager(this)
        callManager = com.example.messenger.webrtc.CallManager(webRtcManager)
        
        androidCallHandler = AndroidCallHandler(callManager, this)

        // Initialize Shared Settings
        sharedSettingsManager = SharedSettingsManager(AndroidSettingsStorage(this))
        
        // Initialize AppUpdater
        appUpdater = AppUpdater(this)

        workManager = androidx.work.WorkManager.getInstance(this)
        val workScheduler = AndroidWorkScheduler(workManager)
        
        // Pass applicationScope to WebSocketManager so it survives ViewModel death
        webSocketManager = WebSocketManager(applicationScope)
        
        repository = MessengerRepository(
            sharedDatabase,
            apiClient,
            webSocketManager
        )
        
        processMessageUseCase = com.example.messenger.domain.usecase.ProcessMessageUseCase(repository, cryptoManager, sharedSettingsManager, callManager)
        messageSynchronizationUseCase = com.example.messenger.domain.usecase.MessageSynchronizationUseCase(repository, cryptoManager, processMessageUseCase)
        
        // Use Shared SendMessageUseCase
        sendMessageUseCase = com.example.messenger.domain.usecase.SendMessageUseCase(
            repository, 
            cryptoManager, 
            sharedSettingsManager, 
            workScheduler
        )
        
        createGroupUseCase = com.example.messenger.domain.usecase.CreateGroupUseCase(
            repository, 
            cryptoManager, 
            sharedSettingsManager
        )
        messageDecryptionUseCase = com.example.messenger.domain.usecase.MessageDecryptionUseCase(cryptoManager)
    }

    private val tokenTracker = object: VKTokenExpiredHandler {
        override fun onTokenExpired() {
            // Handle token expiration if needed
        }
    }
}
