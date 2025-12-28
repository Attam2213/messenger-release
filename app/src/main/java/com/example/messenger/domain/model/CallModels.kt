package com.example.messenger.domain.model

data class CallSignalingMessage(
    val type: String, // "OFFER", "ANSWER", "CANDIDATE", "HANGUP"
    val sdp: String? = null, // For OFFER/ANSWER
    val sdpMid: String? = null, // For CANDIDATE
    val sdpMLineIndex: Int? = null, // For CANDIDATE
    val candidate: String? = null, // For CANDIDATE
    val callerId: String,
    val recipientId: String
)
