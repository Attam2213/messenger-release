package com.example.messenger.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.example.messenger.shared.db.DatabaseDriverFactory
import com.example.messenger.shared.db.DatabaseHelper
import com.example.messenger.crypto.DesktopCryptoManager
import com.example.messenger.shared.utils.DesktopSettingsStorage
import com.example.messenger.shared.utils.SharedSettingsManager
import com.example.messenger.repository.MessengerRepository
import com.example.messenger.network.MessengerApiClient
import com.example.messenger.network.WebSocketManager
import com.example.messenger.domain.usecase.*
import com.example.messenger.viewmodel.SharedMessengerViewModel
import com.example.messenger.shared.infrastructure.WorkScheduler
import com.example.messenger.shared.infrastructure.DesktopCallHandler
import com.example.messenger.shared.infrastructure.DesktopNotificationHandler
import com.example.messenger.ui.App

import java.io.File

import com.example.messenger.shared.utils.AppUpdater

class DesktopWorkScheduler(private val scope: CoroutineScope) : WorkScheduler {
    override fun scheduleOneTimeSync() {
        scope.launch {
            // Simulate sync trigger
            println("Desktop: Sync triggered")
        }
    }
    override fun schedulePeriodicSync(intervalMinutes: Long) {
        // No-op for now
    }
}

fun main() {
    val logDir = File(System.getProperty("user.home"), "AppData/Local/Messenger")
    if (!logDir.exists()) logDir.mkdirs()
    val logFile = File(logDir, "crash.log")
    
    // Setup uncaught exception handler
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        val stackTrace = e.stackTraceToString()
        println("Uncaught Exception: $stackTrace")
        logFile.appendText("\n[${java.time.LocalDateTime.now()}] Uncaught Exception:\n$stackTrace\n")
    }

    try {
        application {
            val applicationScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
            
            val driver = remember { DatabaseDriverFactory().createDriver() }
            val database = remember { DatabaseHelper.createDatabase(driver) }
            val cryptoManager = remember { DesktopCryptoManager() }
            val settingsStorage = remember { DesktopSettingsStorage() }
            val settingsManager = remember { SharedSettingsManager(settingsStorage) }
            val apiClient = remember { MessengerApiClient() }
            val webSocketManager = remember { WebSocketManager(applicationScope) }
            
            val repository = remember { MessengerRepository(database, apiClient, webSocketManager) }
            val workScheduler = remember { DesktopWorkScheduler(applicationScope) }
            
            val sendMessageUseCase = remember { SendMessageUseCase(repository, cryptoManager, settingsManager, workScheduler) }
            val createGroupUseCase = remember { CreateGroupUseCase(repository, cryptoManager, settingsManager) }
            val processMessageUseCase = remember { ProcessMessageUseCase(repository, cryptoManager, settingsManager, null) }
            val messageSynchronizationUseCase = remember { MessageSynchronizationUseCase(repository, cryptoManager, processMessageUseCase) }
            val messageDecryptionUseCase = remember { MessageDecryptionUseCase(cryptoManager) }
            val desktopCallHandler = remember { DesktopCallHandler() }
            val notificationHandler = remember { DesktopNotificationHandler() }
            val appUpdater = remember { AppUpdater(null) }
            
            val viewModel = remember { 
                SharedMessengerViewModel(
                    repository,
                    cryptoManager,
                    settingsManager,
                    sendMessageUseCase,
                    createGroupUseCase,
                    processMessageUseCase,
                    messageSynchronizationUseCase,
                    messageDecryptionUseCase,
                    desktopCallHandler,
                    notificationHandler,
                    appUpdater,
                    applicationScope
                )
            }

            Window(onCloseRequest = ::exitApplication, title = "Messenger Desktop") {
                App(viewModel)
            }
        }
    } catch (e: Throwable) {
        val stackTrace = e.stackTraceToString()
        println("Main Exception: $stackTrace")
        logFile.appendText("\n[${java.time.LocalDateTime.now()}] Main Exception:\n$stackTrace\n")
        e.printStackTrace()
    }
}
