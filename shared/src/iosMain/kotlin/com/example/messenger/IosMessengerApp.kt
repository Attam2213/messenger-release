package com.example.messenger

import com.example.messenger.crypto.IosCryptoManager
import com.example.messenger.shared.db.DatabaseDriverFactory
import com.example.messenger.shared.db.DatabaseHelper
import com.example.messenger.repository.MessengerRepository
import com.example.messenger.network.MessengerApiClient
import com.example.messenger.network.WebSocketManager
import com.example.messenger.shared.utils.IosSettingsStorage
import com.example.messenger.shared.utils.SharedSettingsManager
import com.example.messenger.viewmodel.SharedMessengerViewModel
import com.example.messenger.domain.usecase.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import com.example.messenger.shared.utils.AppUpdater
import com.example.messenger.shared.utils.FileHandler

object IosMessengerApp {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val driver = DatabaseDriverFactory().createDriver()
    private val database = DatabaseHelper.createDatabase(driver)
    
    private val cryptoManager = IosCryptoManager()
    private val settingsStorage = IosSettingsStorage()
    private val settingsManager = SharedSettingsManager(settingsStorage)
    
    private val apiClient = MessengerApiClient()
    private val webSocketManager = WebSocketManager(applicationScope)
    
    val repository = MessengerRepository(database, apiClient, webSocketManager)
    
    // Lazy initialization for UseCases to handle dependencies
    private val processMessageUseCase by lazy {
        ProcessMessageUseCase(repository, cryptoManager, settingsManager, null) // CallManager null
    }
    
    private val messageSynchronizationUseCase by lazy {
        MessageSynchronizationUseCase(repository, cryptoManager, processMessageUseCase)
    }
    
    private val workScheduler by lazy {
        IosWorkScheduler(applicationScope) { messageSynchronizationUseCase }
    }
    
    private val sendMessageUseCase by lazy {
        SendMessageUseCase(repository, cryptoManager, settingsManager, workScheduler)
    }
    
    private val createGroupUseCase by lazy {
        CreateGroupUseCase(repository, cryptoManager, settingsManager)
    }
    
    private val messageDecryptionUseCase by lazy {
        MessageDecryptionUseCase(cryptoManager)
    }
    
    fun createViewModel(): SharedMessengerViewModel {
        return SharedMessengerViewModel(
            repository = repository,
            cryptoManager = cryptoManager,
            settingsManager = settingsManager,
            sendMessageUseCase = sendMessageUseCase,
            createGroupUseCase = createGroupUseCase,
            processMessageUseCase = processMessageUseCase,
            messageSynchronizationUseCase = messageSynchronizationUseCase,
            messageDecryptionUseCase = messageDecryptionUseCase,
            callHandler = null, // TODO: Implement IosCallHandler
            notificationHandler = null, // TODO: Implement IosNotificationHandler
            audioRecorder = null,
            audioPlayer = null,
            appUpdater = AppUpdater(null),
            fileHandler = FileHandler(null),
            scope = applicationScope
        )
    }
    
    // Called from iOS AppDelegate application(_:performFetchWithCompletionHandler:)
    fun performBackgroundFetch(completion: (Boolean) -> Unit) {
        applicationScope.launch {
            val result = workScheduler.performSync()
            completion(result)
        }
    }
}
