package com.example.messenger.infrastructure

import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.messenger.shared.infrastructure.WorkScheduler
import com.example.messenger.worker.SendMessageWorker
import java.util.concurrent.TimeUnit

class AndroidWorkScheduler(private val workManager: WorkManager) : WorkScheduler {
    override fun scheduleOneTimeSync() {
        val request = OneTimeWorkRequestBuilder<SendMessageWorker>().build()
        workManager.enqueueUniqueWork(
            "SendMessageWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    override fun schedulePeriodicSync(intervalMinutes: Long) {
        val request = PeriodicWorkRequestBuilder<SendMessageWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).build()
        
        workManager.enqueueUniquePeriodicWork(
            "PeriodicSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
