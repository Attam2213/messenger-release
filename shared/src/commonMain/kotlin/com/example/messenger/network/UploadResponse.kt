package com.example.messenger.network

import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(
    val fileId: String,
    val size: Long,
    val filename: String
)
