package com.example.messenger

import com.example.messenger.domain.usecase.MessageSynchronizationUseCase
import com.example.messenger.shared.infrastructure.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSLog
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundFetchIntervalMinimum

class IosWorkScheduler(
    private val scope: CoroutineScope,
    private val syncUseCaseProvider: () -> MessageSynchronizationUseCase
) : WorkScheduler {
    
    override fun scheduleOneTimeSync() {
        scope.launch {
            performSync()
        }
    }

    override fun schedulePeriodicSync(intervalMinutes: Long) {
        // iOS handles periodic tasks via Background Fetch or BGTaskScheduler.
        // We set the minimum background fetch interval to the system minimum.
        // Actual timing is determined by the OS.
        UIApplication.sharedApplication.setMinimumBackgroundFetchInterval(UIBackgroundFetchIntervalMinimum)
        NSLog("IosWorkScheduler: Scheduled periodic sync (Background Fetch enabled)")
    }
    
    suspend fun performSync(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            NSLog("IosWorkScheduler: Starting sync")
            syncUseCaseProvider().forceSync()
            NSLog("IosWorkScheduler: Sync completed")
            true
        } catch (e: Exception) {
            NSLog("IosWorkScheduler: Sync failed: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
