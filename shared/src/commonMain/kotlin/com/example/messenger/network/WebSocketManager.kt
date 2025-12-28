package com.example.messenger.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

import com.example.messenger.config.AppConfig

class WebSocketManager(
    private val scope: CoroutineScope
) {
    private val client = HttpClient {
        install(WebSockets)
    }

    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages: SharedFlow<String> = _incomingMessages
    
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus
    
    private val _connectionError = MutableSharedFlow<String?>()
    val connectionError: SharedFlow<String?> = _connectionError
    
    private var currentHash: String? = null
    private var isConnected = false

    fun connect(myHash: String) {
        if (isConnected && currentHash == myHash) return
        
        currentHash = myHash
        scope.launch {
            try {
                _connectionError.emit(null)
                // Use default session if possible or raw websocket
                client.webSocket(
                    method = HttpMethod.Get,
                    host = AppConfig.BASE_URL,
                    port = AppConfig.WS_PORT,
                    path = "/ws/$myHash"
                ) {
                    isConnected = true
                    _connectionStatus.emit(true)
                    println("WebSocket Connected: $myHash")
                    
                    try {
                        for (frame in incoming) {
                            frame as? Frame.Text ?: continue
                            val text = frame.readText()
                            println("WebSocket Message: $text")
                            _incomingMessages.emit(text)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _connectionError.emit(e.message)
                    } finally {
                        isConnected = false
                        _connectionStatus.emit(false)
                        println("WebSocket Disconnected")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isConnected = false
                _connectionStatus.emit(false)
                _connectionError.emit(e.message)
                reconnect(myHash)
            }
        }
    }

    private fun reconnect(myHash: String) {
        scope.launch {
            delay(5000)
            if (isActive) {
                println("WebSocket Reconnecting...")
                connect(myHash)
            }
        }
    }

    fun disconnect() {
        currentHash = null
        // Ktor client usually closes connection when the block ends or client is closed
        // But for long lived connections we might need to cancel the job
    }
}
