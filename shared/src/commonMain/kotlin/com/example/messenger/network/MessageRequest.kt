package com.example.messenger.network

import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(
    val to_hash: String,
    val from_key: String,
    val content: String,
    val timestamp: Long
)
