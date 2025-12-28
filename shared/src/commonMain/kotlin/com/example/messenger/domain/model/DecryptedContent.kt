package com.example.messenger.domain.model

data class DecryptedContent(
    val content: String,
    val type: String,
    val status: VerificationStatus,
    val replyTo: ReplyInfo? = null,
    val extraData: Map<String, String> = emptyMap(),
    val fileId: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val encryptedAesKey: String? = null
)
