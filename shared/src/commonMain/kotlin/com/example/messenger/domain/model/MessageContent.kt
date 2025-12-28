package com.example.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageContent(
    val text: String? = null,
    val groupId: String? = null,
    val replyToId: String? = null,
    val replyToAuthor: String? = null,
    val replyPreview: String? = null,
    val filename: String? = null
)
