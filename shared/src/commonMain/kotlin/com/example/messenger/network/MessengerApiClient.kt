package com.example.messenger.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

import com.example.messenger.config.AppConfig

class MessengerApiClient {
    private val BASE_URL = AppConfig.API_URL

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    suspend fun sendMessage(message: MessageRequest): Map<String, String> {
        // Since Map<String, Any> is hard to serialize with kotlinx.serialization without custom serializers for Any,
        // and the response is likely simple, we can use JsonObject or Map<String, String> if the values are strings.
        // Or we can return the raw JsonObject.
        // Let's assume Map<String, String> for now or use JsonObject.
        // Retrofit was returning Map<String, Any>.
        val response: JsonObject = client.post("$BASE_URL/send") {
            contentType(ContentType.Application.Json)
            setBody(message)
        }.body()
        
        // Convert JsonObject to Map<String, String> for simplicity, or return JsonObject
        return response.mapValues { it.value.toString() }
    }

    suspend fun checkMessages(recipientHash: String): List<MessageRequest> {
        return client.get("$BASE_URL/check/$recipientHash").body()
    }

    suspend fun uploadFile(fileData: ByteArray, fileName: String): UploadResponse {
        return client.post("$BASE_URL/upload") {
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", fileData, Headers.build {
                        append(HttpHeaders.ContentType, "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    })
                }
            ))
        }.body()
    }

    suspend fun downloadFile(fileId: String): ByteArray {
        return client.get("$BASE_URL/files/$fileId").body()
    }
}
