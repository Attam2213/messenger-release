package com.example.messenger.domain.usecase

import com.example.messenger.crypto.CryptoManager
import com.example.messenger.domain.model.DecryptedContent
import com.example.messenger.domain.model.ReplyInfo
import com.example.messenger.domain.model.VerificationStatus
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull

class MessageDecryptionUseCase(
    private val cryptoManager: CryptoManager
) {

    suspend fun execute(content: String, senderPublicKey: String? = null): DecryptedContent {
        return try {
            // 1. Try to parse as JSON (New format with signature)
            if (content.trim().startsWith("{")) {
                 val jsonElement = try {
                     Json.parseToJsonElement(content).jsonObject
                 } catch (e: Exception) {
                     // Fallback to legacy handling if not valid JSON
                     throw e
                 }
                 
                 val type = jsonElement["type"]?.jsonPrimitive?.content ?: "MSG"
                 val signature = jsonElement["sign"]?.jsonPrimitive?.contentOrNull ?: ""
                 val deviceId = jsonElement["deviceId"]?.jsonPrimitive?.contentOrNull ?: ""
                 val extra = mutableMapOf<String, String>()
                 if (deviceId.isNotEmpty()) {
                     extra["deviceId"] = deviceId
                 }

                 if (type == "IMAGE" || type == "AUDIO" || type == "VIDEO" || type == "DOCUMENT") {
                     val fileId = jsonElement["fileId"]?.jsonPrimitive?.contentOrNull
                     
                     if (!fileId.isNullOrEmpty()) {
                         // New Media Format (Multipart Upload)
                         var isValid = false
                         if (senderPublicKey != null && signature.isNotEmpty()) {
                             isValid = cryptoManager.verify(fileId, signature, senderPublicKey)
                         }
                         
                         val status = if (!isValid) {
                             if (signature.isNotEmpty()) VerificationStatus.INVALID else VerificationStatus.NOT_SIGNED
                         } else {
                             VerificationStatus.VERIFIED
                         }
                         
                         val encryptedAesKey = jsonElement["key"]?.jsonPrimitive?.content ?: ""
                         val fileName = jsonElement["filename"]?.jsonPrimitive?.contentOrNull
                         val fileSize = jsonElement["fileSize"]?.jsonPrimitive?.longOrNull ?: 0L
                         
                         return DecryptedContent(
                            content = "",
                            type = type,
                            status = status,
                            extraData = extra,
                            fileId = fileId,
                            fileName = fileName,
                            fileSize = fileSize,
                            encryptedAesKey = encryptedAesKey
                         )
                     }
                 }

                 // Standard Message or Old Media Format
                 val encryptedData = jsonElement["data"]?.jsonPrimitive?.content ?: ""
                 
                 var isValid = false
                 if (senderPublicKey != null && signature.isNotEmpty()) {
                     isValid = cryptoManager.verify(encryptedData, signature, senderPublicKey)
                 }
                 
                 val status = if (!isValid) {
                     if (signature.isNotEmpty()) VerificationStatus.INVALID else VerificationStatus.NOT_SIGNED
                 } else {
                     VerificationStatus.VERIFIED
                 }

                 if ((type == "IMAGE" || type == "AUDIO" || type == "VIDEO" || type == "DOCUMENT") && !jsonElement.containsKey("fileId")) {
                     // Old Base64 Media Format
                     val encryptedAesKey = jsonElement["key"]?.jsonPrimitive?.content ?: ""
                     val aesKeyBase64 = cryptoManager.decrypt(encryptedAesKey)
                     val aesKeyBytes = aesKeyBase64.decodeBase64Bytes()
                     
                     val encryptedBytes = encryptedData.decodeBase64Bytes()
                     val decryptedBytes = cryptoManager.decryptAes(encryptedBytes, aesKeyBytes)
                     
                     // Try to parse as MessageContent JSON wrapper first
                     try {
                         val decryptedStr = decryptedBytes.decodeToString()
                         if (decryptedStr.trim().startsWith("{")) {
                             val contentJson = Json.parseToJsonElement(decryptedStr).jsonObject
                             if (contentJson.containsKey("text")) {
                                 val mediaBase64 = contentJson["text"]?.jsonPrimitive?.content ?: ""
                                 
                                 // Extract metadata
                                 if (contentJson.containsKey("filename")) {
                                     extra["filename"] = contentJson["filename"]?.jsonPrimitive?.content ?: ""
                                 }
                                 if (contentJson.containsKey("duration")) {
                                     extra["duration"] = (contentJson["duration"]?.jsonPrimitive?.longOrNull ?: 0L).toString()
                                 }
                                 
                                 return DecryptedContent(mediaBase64, type, status, extraData = extra)
                             }
                         }
                     } catch (e: Exception) {
                         // Not JSON or parse error, fall back to treating as raw bytes
                     }

                     val decryptedBase64 = decryptedBytes.encodeBase64()
                     
                     if (type == "DOCUMENT") {
                         val filename = jsonElement["filename"]?.jsonPrimitive?.contentOrNull ?: "document"
                         extra["filename"] = filename
                     }
                     
                     DecryptedContent(decryptedBase64, type, status, extraData = extra)
                 } else {
                    // TEXT
                    val encryptedAesKey = jsonElement["key"]?.jsonPrimitive?.contentOrNull
                    val decrypted: String
                    
                    if (!encryptedAesKey.isNullOrEmpty()) {
                        // Hybrid Encryption
                        val aesKeyBase64 = cryptoManager.decrypt(encryptedAesKey)
                        val aesKeyBytes = aesKeyBase64.decodeBase64Bytes()
                        
                        val encryptedBytes = encryptedData.decodeBase64Bytes()
                        val decryptedBytes = cryptoManager.decryptAes(encryptedBytes, aesKeyBytes)
                        decrypted = decryptedBytes.decodeToString()
                    } else {
                        // Legacy RSA Encryption
                        decrypted = cryptoManager.decrypt(encryptedData)
                    }

                    try {
                        // Try to parse as JSON (Format: { text: "...", replyTo: {...} })
                        if (decrypted.trim().startsWith("{")) {
                            val contentJson = Json.parseToJsonElement(decrypted).jsonObject
                            if (contentJson.containsKey("text")) {
                                val text = contentJson["text"]?.jsonPrimitive?.content ?: ""
                                val replyJson = contentJson["replyTo"]?.jsonObject
                                var rInfo: ReplyInfo? = null
                                if (replyJson != null) {
                                    rInfo = ReplyInfo(
                                        replyJson["id"]?.jsonPrimitive?.content ?: "",
                                        replyJson["author"]?.jsonPrimitive?.content ?: "",
                                        replyJson["preview"]?.jsonPrimitive?.content ?: ""
                                    )
                                }
                                DecryptedContent(text, "TEXT", status, rInfo, extraData = extra)
                            } else {
                                // Not our JSON format, treat as plain text
                                DecryptedContent(decrypted, "TEXT", status, extraData = extra)
                            }
                        } else {
                            DecryptedContent(decrypted, "TEXT", status, extraData = extra)
                        }
                    } catch (e: Exception) {
                        // Fallback
                        DecryptedContent(decrypted, "TEXT", status, extraData = extra)
                    }
                }
            } else {
                // Old format (raw encrypted string)
                DecryptedContent(cryptoManager.decrypt(content), "TEXT", VerificationStatus.NOT_SIGNED)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                // Fallback attempt
                DecryptedContent(cryptoManager.decrypt(content), "TEXT", VerificationStatus.NOT_SIGNED)
            } catch (e2: Exception) {
                // Return a friendly error instead of raw JSON
                DecryptedContent(
                    content = "Error: Could not decrypt message. The sender might have used an old key.",
                    type = "TEXT",
                    status = VerificationStatus.INVALID,
                    extraData = mutableMapOf("original_content" to content)
                )
            }
        }
    }
}
