package com.example.messenger.viewmodel

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.MessengerApplication
import com.example.messenger.network.MessageRequest
import com.example.messenger.webrtc.CallManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MessengerApplication
    private val callManager = app.callManager
    private val repository = app.repository
    private val cryptoManager = app.cryptoManager
    private val ringtonePlayer = com.example.messenger.utils.RingtonePlayer(application)

    val callState: StateFlow<CallManager.CallState> = callManager.callState
    val iceConnectionState = callManager.iceConnectionState
    
    // Removed debug states to simplify
    
    init {
        callManager.sendSignalAction = { type, content, toKey ->
            viewModelScope.launch {
                sendSignal(type, content, toKey)
            }
        }
        
        viewModelScope.launch {
            callState.collect { state ->
                if (state is CallManager.CallState.Incoming) {
                    ringtonePlayer.playIncomingCallRingtone()
                } else {
                    ringtonePlayer.stop()
                }
            }
        }
    }

    fun startCall(toKey: String) {
        callManager.startCall(toKey)
    }

    fun acceptCall() {
        callManager.acceptCall()
    }
    
    fun endCall() {
        callManager.endCall()
    }

    private suspend fun sendSignal(type: String, content: String, toPublicKey: String) {
        try {
            Log.d("CallViewModel", "Sending signal: $type to ${toPublicKey.take(10)}...")
            val myPublicKey = cryptoManager.getMyPublicKeyString()
            
            // Hybrid Encryption
            val aesKey = cryptoManager.generateAesKey()
            val encryptedDataBytes = cryptoManager.encryptAes(content.toByteArray(), aesKey)
            val encryptedData = Base64.encodeToString(encryptedDataBytes, Base64.NO_WRAP)
            
            val aesKeyBytes = aesKey
            val aesKeyString = Base64.encodeToString(aesKeyBytes, Base64.NO_WRAP)
            val encryptedKey = cryptoManager.encrypt(aesKeyString, toPublicKey)
            
            val signature = cryptoManager.sign(encryptedData)

            val payloadJson = JSONObject().apply {
                put("type", type)
                put("data", encryptedData)
                put("key", encryptedKey)
                put("sign", signature)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val targetHash = cryptoManager.getHashFromPublicKeyString(toPublicKey)

            repository.sendMessage(
                MessageRequest(
                    to_hash = targetHash,
                    from_key = myPublicKey,
                    content = payloadJson,
                    timestamp = System.currentTimeMillis()
                )
            )
            Log.d("CallViewModel", "Signal sent successfully: $type")
        } catch (e: Exception) {
            Log.e("CallViewModel", "Failed to send signal", e)
        }
    }
}
