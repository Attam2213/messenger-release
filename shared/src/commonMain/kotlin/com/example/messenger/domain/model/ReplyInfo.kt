package com.example.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReplyInfo(
    val id: String,
    val author: String,
    val preview: String
)
