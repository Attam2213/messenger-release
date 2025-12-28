package com.example.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val deviceId: String,
    val model: String,
    val timestamp: Long,
    val publicKey: String
)
