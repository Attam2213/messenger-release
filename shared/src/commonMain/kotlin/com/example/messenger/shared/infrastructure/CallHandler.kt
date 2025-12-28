package com.example.messenger.shared.infrastructure

interface CallHandler {
    fun initiateCall(contactPublicKey: String, isVideo: Boolean)
    fun handleIncomingCall(fromKey: String, type: String, content: String)
}
