package com.example.messenger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val privateKey: String,
    val contacts: List<BackupContact>,
    val messages: List<BackupMessage>,
    val groups: List<BackupGroup>
)

@Serializable
data class BackupContact(
    val publicKey: String,
    val name: String,
    val createdAt: Long
)

@Serializable
data class BackupMessage(
    val messageId: String,
    val fromPublicKey: String,
    val toPublicKey: String,
    val groupId: String?,
    val encryptedContent: String,
    val timestamp: Long,
    val isDelivered: Boolean,
    val isRead: Boolean
)

@Serializable
data class BackupGroup(
    val groupId: String,
    val name: String,
    val members: String,
    val createdAt: Long
)
