package com.example.messenger.domain.usecase

import com.example.messenger.crypto.CryptoManager
import com.example.messenger.domain.model.AuthRequest
import com.example.messenger.domain.model.ProcessResult
import com.example.messenger.network.MessageRequest
import com.example.messenger.repository.MessengerRepository
import com.example.messenger.shared.db.GroupEntity
import com.example.messenger.shared.db.MessageEntity
import com.example.messenger.shared.infrastructure.CallSignalProcessor
import com.example.messenger.shared.utils.SharedSettingsManager
import com.example.messenger.shared.utils.randomUUID
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

class ProcessMessageUseCase(
    private val repository: MessengerRepository,
    private val cryptoManager: CryptoManager,
    private val settingsManager: SharedSettingsManager,
    private val callManager: CallSignalProcessor?
) {
    private val _processResult = MutableSharedFlow<ProcessResult>()
    val processResult = _processResult.asSharedFlow()

    suspend fun execute(netMsg: MessageRequest): ProcessResult = withContext(Dispatchers.Default) {
        // Check duplicates
        if (repository.doesMessageExist(netMsg.timestamp, netMsg.content)) {
            return@withContext ProcessResult.Ignored
        }

        var isValid = true
        var messageType = "MSG"
        var messageId: String? = null
        var encryptedData: String = ""
        var encryptedKey: String = ""
        var wrapperJson: JsonObject? = null
        var senderDeviceId: String?
        
        if (netMsg.content.trim().startsWith("{")) {
            try {
                val element = Json.parseToJsonElement(netMsg.content)
                if (element is JsonObject) {
                    wrapperJson = element
                    encryptedData = wrapperJson["data"]?.jsonPrimitive?.content ?: ""
                    val fileId = wrapperJson["fileId"]?.jsonPrimitive?.contentOrNull
                    encryptedKey = wrapperJson["key"]?.jsonPrimitive?.content ?: ""
                    val signature = wrapperJson["sign"]?.jsonPrimitive?.contentOrNull ?: ""
                    messageType = wrapperJson["type"]?.jsonPrimitive?.content ?: "MSG"
                    messageId = wrapperJson["id"]?.jsonPrimitive?.contentOrNull
                    senderDeviceId = wrapperJson["deviceId"]?.jsonPrimitive?.contentOrNull

                    // Ignore echoes from myself (same device)
                    if (netMsg.from_key == cryptoManager.getMyPublicKeyString() && 
                        senderDeviceId == settingsManager.deviceId.value) {
                        return@withContext ProcessResult.Ignored
                    }

                    if (signature.isNotEmpty()) {
                        val signedContent = if (!fileId.isNullOrEmpty()) fileId else encryptedData
                        if (!cryptoManager.verify(signedContent, signature, netMsg.from_key)) {
                            // Verification failed
                        }
                    }
                }
            } catch (e: Exception) {
                isValid = false
            }
        }

        if (!isValid) return@withContext ProcessResult.Ignored

        if (messageType == "ACK" && messageId != null) {
            repository.markAsDelivered(messageId)
            return@withContext ProcessResult.Ignored 
        } else if (messageType == "AUTH_ACK") {
             // Auth accepted by other side
             _processResult.emit(ProcessResult.AuthAckReceived(netMsg.from_key))
             return@withContext ProcessResult.AuthAckReceived(netMsg.from_key)
        } else if (messageType == "READ_ACK" && messageId != null) {
            try {
                val decryptedStr = decryptContent(encryptedData, encryptedKey)
                val json = Json.parseToJsonElement(decryptedStr).jsonObject
                val readMessageId = json["messageId"]?.jsonPrimitive?.contentOrNull
                if (readMessageId != null) {
                    repository.markAsRead(readMessageId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext ProcessResult.Ignored
        } else if (messageType == "AUTH_REQ") {
             try {
                val decrypted = cryptoManager.decrypt(encryptedData)
                val json = Json.parseToJsonElement(decrypted).jsonObject
                val deviceId = json["deviceId"]?.jsonPrimitive?.content ?: ""
                val model = json["model"]?.jsonPrimitive?.content ?: ""
                val request = AuthRequest(deviceId, model, Clock.System.now().toEpochMilliseconds(), netMsg.from_key)
                _processResult.emit(ProcessResult.AuthRequestReceived(request))
                return@withContext ProcessResult.AuthRequestReceived(request)
            } catch (e: Exception) { 
                return@withContext ProcessResult.Ignored
            }
        } else if (messageType == "TYPING") {
            try {
                val decrypted = cryptoManager.decrypt(encryptedData)
                val json = Json.parseToJsonElement(decrypted).jsonObject
                val isTyping = json["isTyping"]?.jsonPrimitive?.boolean ?: false
                _processResult.emit(ProcessResult.Typing(netMsg.from_key, isTyping))
                return@withContext ProcessResult.Typing(netMsg.from_key, isTyping)
            } catch (e: Exception) {
                return@withContext ProcessResult.Ignored
            }
        } else if (messageType == "GROUP_CREATE") {
             try {
                val decryptedStr = decryptContent(encryptedData, encryptedKey)
                val json = Json.parseToJsonElement(decryptedStr).jsonObject
                val groupId = json["groupId"]?.jsonPrimitive?.content ?: ""
                val groupName = json["groupName"]?.jsonPrimitive?.content ?: ""
                val membersArray = json["members"]?.jsonArray
                val membersList = membersArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                
                val group = GroupEntity(
                    groupId = groupId,
                    name = groupName,
                    members = membersList.joinToString(","),
                    createdAt = Clock.System.now().toEpochMilliseconds()
                )
                repository.insertGroup(group)
                
                if (messageId != null) {
                    sendAck(messageId, netMsg.from_key)
                }

                return@withContext ProcessResult.GroupCreated
            } catch (e: Exception) {
                return@withContext ProcessResult.Ignored
            }
        } else if (messageType == "OFFER" || messageType == "ANSWER" || messageType == "CANDIDATE" || messageType == "HANGUP") {
            try {
                val decryptedStr = decryptContent(encryptedData, encryptedKey)
                val signal = ProcessResult.CallSignal(messageType, netMsg.from_key, decryptedStr)
                callManager?.processSignal(signal)
                _processResult.emit(signal)
                return@withContext signal
            } catch (e: Exception) {
                return@withContext ProcessResult.Ignored
            }
        } else {
            // MSG, IMAGE, VIDEO, DOCUMENT
            repository.ensureContactExists(netMsg.from_key)

            var extractedGroupId: String? = null
            
            if (wrapperJson?.containsKey("groupId") == true) {
                extractedGroupId = wrapperJson?.get("groupId")?.jsonPrimitive?.contentOrNull
            }
            if (extractedGroupId == null && messageType == "MSG") {
                 try {
                     val decryptedStr = decryptContent(encryptedData, encryptedKey)
                     val json = Json.parseToJsonElement(decryptedStr).jsonObject
                     if (json.containsKey("groupId")) {
                         extractedGroupId = json["groupId"]?.jsonPrimitive?.contentOrNull
                     }
                 } catch (e: Exception) {}
            }

            if (extractedGroupId != null) {
                val existingGroup = repository.getGroupById(extractedGroupId)
                if (existingGroup == null) {
                     val placeholderGroup = GroupEntity(
                         groupId = extractedGroupId,
                         name = "Unknown Group",
                         members = "", 
                         createdAt = Clock.System.now().toEpochMilliseconds()
                     )
                     repository.insertGroup(placeholderGroup)
                }
            }

            val entity = MessageEntity(
                messageId = messageId ?: randomUUID(),
                fromPublicKey = netMsg.from_key,
                toPublicKey = cryptoManager.getMyPublicKeyString(),
                encryptedContent = netMsg.content,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                isDelivered = true,
                deliveredAt = null,
                isRead = false,
                groupId = extractedGroupId
            )
            repository.insertMessage(entity)
            
            if (messageId != null) {
                sendAck(messageId, netMsg.from_key)
            }

            val result = ProcessResult.MessageSaved(netMsg.from_key, extractedGroupId)
            _processResult.emit(result)
            return@withContext result
        }
    }

    private suspend fun decryptContent(encryptedData: String, encryptedKey: String): String {
        if (encryptedKey.isNotEmpty()) {
            val aesKeyBase64 = cryptoManager.decrypt(encryptedKey)
            val aesKeyBytes = aesKeyBase64.decodeBase64Bytes()
            val encryptedBytes = encryptedData.decodeBase64Bytes()
            val decryptedBytes = cryptoManager.decryptAes(encryptedBytes, aesKeyBytes)
            return decryptedBytes.decodeToString()
        } else {
            return cryptoManager.decrypt(encryptedData)
        }
    }

    private suspend fun sendAck(messageId: String, toPublicKey: String) {
        try {
            val myPublicKey = cryptoManager.getMyPublicKeyString()
            val rawJson = buildJsonObject {
                put("type", "ACK")
                put("id", messageId)
            }.toString()
            
            val encrypted = cryptoManager.encrypt(rawJson, toPublicKey)
            val signature = cryptoManager.sign(encrypted)
            
            val payloadJson = buildJsonObject {
                put("type", "ACK")
                put("id", messageId)
                put("data", encrypted)
                put("sign", signature)
            }.toString()

            val targetHash = cryptoManager.getHashFromPublicKeyString(toPublicKey)

            repository.sendMessage(
                MessageRequest(
                    to_hash = targetHash,
                    from_key = myPublicKey,
                    content = payloadJson,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
