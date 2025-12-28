package com.example.messenger.domain.usecase

import com.example.messenger.crypto.CryptoManager
import com.example.messenger.network.MessageRequest
import com.example.messenger.repository.MessengerRepository
import com.example.messenger.shared.db.GroupEntity
import com.example.messenger.shared.utils.SharedSettingsManager
import com.example.messenger.shared.utils.randomUUID
import io.ktor.util.encodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.datetime.Clock

class CreateGroupUseCase(
    private val repository: MessengerRepository,
    private val cryptoManager: CryptoManager,
    private val settingsManager: SharedSettingsManager
) {
    suspend fun execute(name: String, memberKeys: List<String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val myKey = cryptoManager.getMyPublicKeyString()
            val allMembers = (memberKeys + myKey).distinct()
            val groupId = randomUUID()

            val groupEntity = GroupEntity(
                groupId = groupId,
                name = name,
                members = allMembers.joinToString(","),
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
            repository.insertGroup(groupEntity)

            val payloadJson = buildJsonObject {
                put("type", "GROUP_CREATE")
                put("groupId", groupId)
                put("groupName", name)
                putJsonArray("members") {
                    allMembers.forEach { add(it) }
                }
                put("deviceId", settingsManager.deviceId.value)
            }.toString()

            memberKeys.forEach { memberKey ->
                if (memberKey != myKey) {
                    try {
                        sendEncryptedMessage(memberKey, payloadJson, "GROUP_CREATE")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendEncryptedMessage(toPublicKey: String, contentString: String, type: String) {
        val myPublicKey = cryptoManager.getMyPublicKeyString()
        
        val aesKey = cryptoManager.generateAesKey()
        val aesKeyBase64 = aesKey.encodeBase64()
        
        val encryptedContent = cryptoManager.encryptAes(contentString.encodeToByteArray(), aesKey)
        val encryptedContentBase64 = encryptedContent.encodeBase64()
        
        val encryptedAesKey = cryptoManager.encrypt(aesKeyBase64, toPublicKey)
        
        val signature = cryptoManager.sign(encryptedContentBase64)

        val wrapperJson = buildJsonObject {
            put("id", randomUUID())
            put("type", type)
            put("data", encryptedContentBase64)
            put("key", encryptedAesKey)
            put("sign", signature)
            put("deviceId", settingsManager.deviceId.value)
        }.toString()

        val targetHash = cryptoManager.getHashFromPublicKeyString(toPublicKey)

        repository.sendMessage(
            MessageRequest(
                to_hash = targetHash,
                from_key = myPublicKey,
                content = wrapperJson,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
    }
}
