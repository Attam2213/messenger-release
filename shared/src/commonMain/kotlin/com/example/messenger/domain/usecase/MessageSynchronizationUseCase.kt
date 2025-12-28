package com.example.messenger.domain.usecase

import com.example.messenger.crypto.CryptoManager
import com.example.messenger.domain.model.SyncStatus
import com.example.messenger.domain.model.ProcessResult
import com.example.messenger.network.MessageRequest
import com.example.messenger.repository.MessengerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MessageSynchronizationUseCase(
    private val repository: MessengerRepository,
    private val cryptoManager: CryptoManager,
    private val processMessageUseCase: ProcessMessageUseCase
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Initializing)
    val status: Flow<SyncStatus> = _status.asStateFlow()

    fun reset() {
        _status.value = SyncStatus.Initializing
    }

    suspend fun forceSync() {
        try {
            if (cryptoManager.hasIdentity()) {
                val myHash = cryptoManager.getMyPublicKeyHash()
                if (myHash.isEmpty()) {
                    cryptoManager.reloadKeys()
                    if (cryptoManager.getMyPublicKeyHash().isEmpty()) {
                        _status.value = SyncStatus.Error("Key Error")
                        return
                    }
                }
                
                if (!repository.webSocketManager.connectionStatus.value) {
                     repository.webSocketManager.connect(myHash)
                }

                val newMessages = repository.checkMessages(myHash)
                var addedCount = 0
                for (netMsg in newMessages) {
                    val result = processMessageUseCase.execute(netMsg)
                    if (result !is ProcessResult.Ignored) {
                        addedCount++
                    }
                }
                if (addedCount > 0) {
                     _status.value = SyncStatus.Downloaded(addedCount)
                     delay(3000)
                     _status.value = SyncStatus.Connected
                } else {
                    if (_status.value is SyncStatus.Connecting || _status.value is SyncStatus.Error || _status.value is SyncStatus.Initializing) {
                        _status.value = SyncStatus.Connected
                    }
                }
            }
        } catch (e: Exception) {
            if (_status.value is SyncStatus.Initializing || _status.value is SyncStatus.Connecting) {
                _status.value = SyncStatus.Error(e.message ?: "Connection failed")
            }
        }
    }

    suspend fun startSynchronization() = coroutineScope {
        val webSocketManager = repository.webSocketManager

        if (cryptoManager.hasIdentity()) {
            val myHash = cryptoManager.getMyPublicKeyHash()
            // Initial sync
            try {
                forceSync()
            } catch (e: Exception) {
                // Ignore initial sync error
            }
            webSocketManager.connect(myHash)
        }
         
        launch {
            webSocketManager.connectionStatus.collect { isConnected ->
                 if (isConnected) {
                     _status.value = SyncStatus.Connected
                 } else {
                     if (_status.value is SyncStatus.Initializing) {
                         _status.value = SyncStatus.Connecting
                     }
                 }
            }
        }
        
        launch {
            webSocketManager.connectionError.collect { errorMsg ->
                 if (errorMsg != null) {
                     _status.value = SyncStatus.Error(errorMsg)
                 }
            }
        }
        
        launch {
             webSocketManager.incomingMessages.collect { text ->
                 try {
                     val netMsg = Json.decodeFromString<MessageRequest>(text)
                     processMessageUseCase.execute(netMsg)
                 } catch (e: Exception) {
                     // Ignore parse error
                 }
             }
        }
        
        while (isActive) {
            try {
                if (cryptoManager.hasIdentity()) {
                    val myHash = cryptoManager.getMyPublicKeyHash()
                    val newMessages = repository.checkMessages(myHash)
                    var addedCount = 0
                    for (netMsg in newMessages) {
                        val result = processMessageUseCase.execute(netMsg)
                        if (result !is ProcessResult.Ignored) {
                            addedCount++
                        }
                    }
                    if (addedCount > 0) {
                         _status.value = SyncStatus.Downloaded(addedCount)
                         delay(1000)
                         _status.value = SyncStatus.Connected
                    } else {
                        if (_status.value is SyncStatus.Connecting || _status.value is SyncStatus.Error) {
                            _status.value = SyncStatus.Connected
                        }
                    }
                }
            } catch (e: Exception) {
                // Keep silent on polling error to avoid spamming status
                if (_status.value !is SyncStatus.Connected) {
                     _status.value = SyncStatus.Error(e.message ?: "Polling Error")
                }
            }
            delay(3000) // Poll every 3 seconds for near real-time fallback
        }
    }
}
