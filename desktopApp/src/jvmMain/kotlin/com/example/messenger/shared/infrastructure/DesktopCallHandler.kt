package com.example.messenger.shared.infrastructure

import javax.swing.JOptionPane

class DesktopCallHandler : CallHandler {
    override fun initiateCall(contactPublicKey: String, isVideo: Boolean) {
        JOptionPane.showMessageDialog(
            null, 
            "Calls are not supported on Desktop yet.", 
            "Feature Unavailable", 
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    override fun handleIncomingCall(fromKey: String, type: String, content: String) {
        // Log or show notification that a call attempt was made
        println("Incoming call signal ignored on Desktop: $content from $fromKey (type: $type)")
    }
}
