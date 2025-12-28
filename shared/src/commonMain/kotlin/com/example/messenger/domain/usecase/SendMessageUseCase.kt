package com.example.messenger.domain.usecase

import com.example.messenger.crypto.CryptoManager
import com.example.messenger.domain.model.MessageContent
import com.example.messenger.repository.MessengerRepository
import com.example.messenger.shared.db.MessageEntity
import com.example.messenger.shared.db.OutboxEntity
import com.example.messenger.shared.infrastructure.WorkScheduler
import com.example.messenger.shared.utils.SharedSettingsManager
import com.example.messenger.shared.utils.randomUUID
import io.ktor.util.encodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.Clock

class SendMessageUseCase(
    private val repository: MessengerRepository,
    private val cryptoManager: CryptoManager,
    private val settingsManager: SharedSettingsManager,
    private val workScheduler: WorkScheduler
) {

    suspend fun sendMessage(
        toPublicKey: String,
        text: String,
        replyToId: String? = null,
        replyToAuthor: String? = null,
        replyPreview: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val content = MessageContent(
                text = text,
                replyToId = replyToId,
                replyToAuthor = replyToAuthor,
                replyPreview = replyPreview
            )
            val contentJson = Json.encodeToString(content)
            
            queueEncryptedMessage(toPublicKey, contentJson, "MSG", null, null)

            // Save locally
            saveLocalMessage(toPublicKey, contentJson, "MSG", null)

            workScheduler.scheduleOneTimeSync()
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun sendGroupMessage(groupId: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val group = repository.getGroupById(groupId) ?: throw Exception("Group not found")
            val members = group.members.split(",")
            val myKey = cryptoManager.getMyPublicKeyString()
            val messageId = randomUUID()
            
            val innerContent = MessageContent(text = content, groupId = groupId)
            val innerJson = Json.encodeToString(innerContent)

            // Queue for each member
            members.forEach { rawMemberKey ->
                val memberKey = rawMemberKey.trim()
                if (memberKey.isNotEmpty() && memberKey != myKey) {
                    try {
                        queueEncryptedMessage(memberKey, innerJson, "MSG", groupId, messageId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Save locally
            saveLocalMessage(groupId, innerJson, "MSG", groupId, messageId)
            
            workScheduler.scheduleOneTimeSync()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendMedia(toPublicKey: String, bytes: ByteArray, type: String, filename: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val contentBase64 = bytes.encodeBase64()
            val content = MessageContent(text = contentBase64, filename = filename)
            val contentJson = Json.encodeToString(content)
            
            queueEncryptedMessage(toPublicKey, contentJson, type, null, null)
            saveLocalMessage(toPublicKey, contentJson, type, null)
            
            workScheduler.scheduleOneTimeSync()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendGroupMedia(groupId: String, bytes: ByteArray, type: String, filename: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val group = repository.getGroupById(groupId) ?: throw Exception("Group not found")
            val members = group.members.split(",")
            val myKey = cryptoManager.getMyPublicKeyString()
            val messageId = randomUUID()
            
            val contentBase64 = bytes.encodeBase64()
            val innerContent = MessageContent(text = contentBase64, filename = filename, groupId = groupId)
            val innerJson = Json.encodeToString(innerContent)
            
             // Queue for each member
            members.forEach { rawMemberKey ->
                val memberKey = rawMemberKey.trim()
                if (memberKey.isNotEmpty() && memberKey != myKey) {
                    try {
                        queueEncryptedMessage(memberKey, innerJson, type, groupId, messageId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            saveLocalMessage(groupId, innerJson, type, groupId, messageId)
            
            workScheduler.scheduleOneTimeSync()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendTyping(toPublicKey: String, isTyping: Boolean) = withContext(Dispatchers.IO) {
        try {
            val contentJson = Json.encodeToString(mapOf("isTyping" to isTyping))
            queueEncryptedMessage(toPublicKey, contentJson, "TYPING", null, null)
            // Typing status is ephemeral, maybe don't trigger sync or save? 
            // But queueEncryptedMessage inserts into outbox, so we need sync to send it.
            workScheduler.scheduleOneTimeSync()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendReadAck(toPublicKey: String, messageId: String) = withContext(Dispatchers.IO) {
        try {
            val contentJson = Json.encodeToString(mapOf("messageId" to messageId))
            queueEncryptedMessage(toPublicKey, contentJson, "READ_ACK", null, messageId)
            workScheduler.scheduleOneTimeSync()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAuthAck(toPublicKey: String) {
        try {
            val contentJson = "{}"
            queueEncryptedMessage(toPublicKey, contentJson, "AUTH_ACK", null, null)
            workScheduler.scheduleOneTimeSync()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun queueEncryptedMessage(
        toPublicKey: String, 
        contentString: String, 
        type: String, 
        groupId: String? = null, 
        relatedMessageId: String? = null
    ) {
        val aesKey = cryptoManager.generateAesKey()
        val aesKeyBase64 = aesKey.encodeBase64()
        
        val encryptedContent = cryptoManager.encryptAes(contentString.encodeToByteArray(), aesKey)
        val encryptedContentBase64 = encryptedContent.encodeBase64()
        
        // Encrypt AES key with recipient's public key
        val encryptedAesKey = cryptoManager.encrypt(aesKeyBase64, toPublicKey)
        
        // Sign the encrypted content
        val signature = cryptoManager.sign(encryptedContentBase64)
        
        val payload = buildJsonObject {
            put("id", relatedMessageId ?: randomUUID())
            put("type", type)
            put("data", encryptedContentBase64)
            put("key", encryptedAesKey)
            put("sign", signature)
            if (groupId != null) put("groupId", groupId)
        }.toString()
        
        // Add to Outbox
        repository.insertOutboxItem(
            toPublicKey,
            payload
        )
    }

    private suspend fun saveLocalMessage(
        toPublicKey: String, // Or groupId
        contentString: String,
        type: String,
        groupId: String? = null,
        existingMessageId: String? = null
    ) {
        val myKey = cryptoManager.getMyPublicKeyString()
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val messageId = existingMessageId ?: randomUUID()
        
        // Encrypt for self so we can read it later
        val aesKey = cryptoManager.generateAesKey()
        val aesKeyBase64 = aesKey.encodeBase64()
        
        val encryptedContent = cryptoManager.encryptAes(contentString.encodeToByteArray(), aesKey)
        val encryptedContentBase64 = encryptedContent.encodeBase64()
        
        val encryptedAesKeyForMe = cryptoManager.encrypt(aesKeyBase64, myKey)
        val signatureForMe = cryptoManager.sign(encryptedContentBase64)
        
        val payloadForMe = buildJsonObject {
            put("id", messageId)
            put("type", type)
            put("data", encryptedContentBase64)
            put("key", encryptedAesKeyForMe)
            put("sign", signatureForMe)
            if (groupId != null) put("groupId", groupId)
        }.toString()
        
        val entity = MessageEntity(
            messageId = messageId,
            fromPublicKey = myKey,
            toPublicKey = toPublicKey,
            encryptedContent = payloadForMe,
            timestamp = timestamp,
            isDelivered = false,
            deliveredAt = null,
            isRead = false,
            groupId = groupId
        )
        repository.insertMessage(entity)
    }
}

