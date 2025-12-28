package com.example.messenger.domain.model

import com.example.messenger.domain.model.AuthRequest

sealed class ProcessResult {
    object Ignored : ProcessResult()
    data class MessageSaved(val fromKey: String, val groupId: String?) : ProcessResult()
    data class AuthRequestReceived(val request: AuthRequest) : ProcessResult()
    data class AuthAckReceived(val fromKey: String) : ProcessResult()
    data class Typing(val fromKey: String, val isTyping: Boolean) : ProcessResult()
    object GroupCreated : ProcessResult()
    data class CallSignal(val type: String, val fromKey: String, val content: String) : ProcessResult()
}
