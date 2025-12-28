package com.example.messenger.infrastructure

import com.example.messenger.shared.infrastructure.CallHandler
import com.example.messenger.webrtc.CallManager
import android.content.Context
import android.content.Intent
import com.example.messenger.MainActivity

class AndroidCallHandler(
    private val callManager: CallManager,
    private val context: Context
) : CallHandler {

    override fun initiateCall(contactPublicKey: String, isVideo: Boolean) {
        // Start call logic
        callManager.startCall(contactPublicKey)
        
        // Ensure Call Screen Overlay is shown (handled by MainActivity observing CallState)
        // If MainActivity is not in foreground, we might need to start it
        // For now, assume MainActivity handles state changes
    }

    override fun handleIncomingCall(fromKey: String, type: String, content: String) {
        // Process incoming signal
        val result = com.example.messenger.domain.model.ProcessResult.CallSignal(type, fromKey, content)
        callManager.processSignal(result)
    }
}
