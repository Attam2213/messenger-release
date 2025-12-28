package com.example.messenger.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.messenger.MessengerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessageWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as MessengerApplication
        val syncUseCase = app.messageSynchronizationUseCase
        
        try {
            syncUseCase.forceSync()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
