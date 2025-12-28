package com.example.messenger.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.messenger.MessengerApplication
import com.example.messenger.network.MessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SendMessageWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as MessengerApplication
        val repository = app.repository
        val cryptoManager = app.cryptoManager

        val pendingMessages = repository.getAllPendingMessages()

        if (pendingMessages.isEmpty()) {
            return@withContext Result.success()
        }

        var failCount = 0

        for (msg in pendingMessages) {
            try {
                val myKey = cryptoManager.getMyPublicKeyString()
                val targetHash = cryptoManager.getHashFromPublicKeyString(msg.recipientKey)
                
                // Construct MessageRequest
                // Note: contentJson is ALREADY the encrypted wrapperJson
                val request = MessageRequest(
                    to_hash = targetHash,
                    from_key = myKey,
                    content = msg.contentJson,
                    timestamp = msg.createdAt
                )

                repository.sendMessage(request)

                // If successful, delete from outbox
                repository.deletePendingMessage(msg.id)
                
                // Check if we need to update the main message status
                val relatedId = msg.relatedMessageId
                if (relatedId != null) {
                    val remaining = repository.countRemainingForMessage(relatedId)
                    if (remaining == 0L) {
                        repository.markAsDelivered(relatedId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                failCount++
            }
        }
        
        if (failCount > 0) {
             return@withContext Result.retry()
        }

        Result.success()
    }
}
