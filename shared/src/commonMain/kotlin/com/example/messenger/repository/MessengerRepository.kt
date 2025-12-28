package com.example.messenger.repository

import com.example.messenger.shared.db.AppDatabase
import com.example.messenger.shared.db.ContactEntity
import com.example.messenger.shared.db.GroupEntity
import com.example.messenger.shared.db.MessageEntity
import com.example.messenger.shared.db.OutboxEntity
import com.example.messenger.network.MessageRequest
import com.example.messenger.network.MessengerApiClient
import com.example.messenger.network.UploadResponse
import com.example.messenger.network.WebSocketManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

import kotlinx.datetime.Clock

class MessengerRepository(
    private val sharedDb: AppDatabase,
    private val api: MessengerApiClient,
    val webSocketManager: WebSocketManager
) {
    suspend fun insertOutboxItem(toPublicKey: String, payload: String) {
        sharedDb.outboxEntityQueries.insert(
            OutboxEntity(
                id = 0, // Autoincrement ignored
                type = "MSG", 
                recipientKey = toPublicKey,
                contentJson = payload,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                relatedMessageId = null
            )
        )
    }

    // Outbox
    suspend fun queueMessage(outboxEntity: OutboxEntity) {
        sharedDb.outboxEntityQueries.insert(outboxEntity)
    }

    suspend fun getAllPendingMessages(): List<OutboxEntity> = 
        sharedDb.outboxEntityQueries.getAll().executeAsList()

    suspend fun deletePendingMessage(id: Long) = 
        sharedDb.outboxEntityQueries.delete(id)

    suspend fun countRemainingForMessage(relatedMessageId: String): Long = 
        sharedDb.outboxEntityQueries.countRemainingForMessage(relatedMessageId).executeAsOne()

    // Contacts
    fun getAllContacts(): Flow<List<ContactEntity>> = 
        sharedDb.contactEntityQueries.getAllContacts()
            .asFlow()
            .mapToList(Dispatchers.IO)

    suspend fun getAllContactsSnapshot(): List<ContactEntity> = 
        sharedDb.contactEntityQueries.getAllContacts().executeAsList()

    suspend fun insertContact(contact: ContactEntity) = 
        sharedDb.contactEntityQueries.insertContact(contact)
    
    suspend fun deleteAllContacts() = sharedDb.contactEntityQueries.deleteAll()
    
    suspend fun ensureContactExists(publicKey: String) {
        val existing = sharedDb.contactEntityQueries.getContactByKey(publicKey).executeAsOneOrNull()
        if (existing == null) {
            sharedDb.contactEntityQueries.insertContact(
                ContactEntity(
                    publicKey = publicKey,
                    name = "Unknown ${publicKey.take(4)}...",
                    createdAt = Clock.System.now().toEpochMilliseconds()
                )
            )
        }
    }

    suspend fun deleteContact(publicKey: String) = sharedDb.contactEntityQueries.deleteContactByKey(publicKey)

    suspend fun getContact(publicKey: String): ContactEntity? = 
        sharedDb.contactEntityQueries.getContactByKey(publicKey).executeAsOneOrNull()

    suspend fun updateContactName(publicKey: String, name: String) = 
        sharedDb.contactEntityQueries.updateName(name, publicKey)

    // Groups
    fun getAllGroups(): Flow<List<GroupEntity>> = 
        sharedDb.groupEntityQueries.getAllGroups()
            .asFlow()
            .mapToList(Dispatchers.IO)

    suspend fun getAllGroupsSnapshot(): List<GroupEntity> = 
        sharedDb.groupEntityQueries.getAllGroups().executeAsList()

    suspend fun getGroupById(groupId: String): GroupEntity? = 
        sharedDb.groupEntityQueries.getGroupById(groupId).executeAsOneOrNull()

    suspend fun insertGroup(group: GroupEntity) = 
        sharedDb.groupEntityQueries.insertGroup(group)

    suspend fun deleteGroup(groupId: String) = sharedDb.groupEntityQueries.deleteGroup(groupId)

    suspend fun clearAllGroups() = sharedDb.groupEntityQueries.clearAll()

    // Messages
    suspend fun insertMessage(message: MessageEntity) = 
        sharedDb.messageEntityQueries.insertMessage(message)

    suspend fun doesMessageExist(timestamp: Long, content: String): Boolean {
        return sharedDb.messageEntityQueries.checkMessageExists(timestamp, content).executeAsOne() > 0
    }

    suspend fun clearAllMessages() = sharedDb.messageEntityQueries.clearAll()

    suspend fun getAllMessagesSnapshot(): List<MessageEntity> = 
        sharedDb.messageEntityQueries.getAllMessages().executeAsList()

    suspend fun markAsDelivered(messageId: String) = 
        sharedDb.messageEntityQueries.markAsDelivered(messageId)

    suspend fun markAsRead(messageId: String) = 
        sharedDb.messageEntityQueries.markAsRead(messageId)

    suspend fun markMessagesAsRead(myKey: String, otherKey: String) = 
        sharedDb.messageEntityQueries.markMessagesAsRead(myKey, otherKey)

    suspend fun getUnreadMessages(myKey: String, otherKey: String) = 
        sharedDb.messageEntityQueries.getUnreadMessages(myKey, otherKey).executeAsList()

    fun getUnreadCount(myKey: String, otherKey: String): Flow<Long> = 
        sharedDb.messageEntityQueries.getUnreadCount(myKey, otherKey)
            .asFlow()
            .mapToOne(Dispatchers.IO)

    fun getUnreadCountsMap(myKey: String): Flow<Map<String, Long>> = 
        sharedDb.messageEntityQueries.getUnreadCounts(myKey) { key, count -> key to count }
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.toMap() }

    suspend fun deleteMessagesBetween(myKey: String, otherKey: String) = 
        sharedDb.messageEntityQueries.deleteMessagesBetween(myKey, otherKey)

    suspend fun deleteGroupMessages(groupId: String) = 
        sharedDb.messageEntityQueries.deleteGroupMessages(groupId)

    fun getMessagesForContact(myKey: String, otherKey: String): Flow<List<MessageEntity>> = 
        sharedDb.messageEntityQueries.getMessagesBetween(myKey, otherKey)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun getMessagesForGroup(groupId: String): Flow<List<MessageEntity>> = 
        sharedDb.messageEntityQueries.getGroupMessages(groupId)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun disconnect() {
        webSocketManager.disconnect()
    }
    
    // Generic DB
    suspend fun clearAllTables() {
        sharedDb.transaction {
            sharedDb.messageEntityQueries.clearAll()
            sharedDb.groupEntityQueries.clearAll()
            sharedDb.contactEntityQueries.deleteAll()
            sharedDb.outboxEntityQueries.clearAll()
        }
    }

    suspend fun restoreBackup(
        contacts: List<ContactEntity>,
        messages: List<MessageEntity>,
        groups: List<GroupEntity>
    ) {
        sharedDb.transaction {
            // Clear existing data? Or merge? 
            // Usually restore overwrites or merges. 
            // Let's merge (insert or ignore is default for some, but usually replace).
            // For now, let's assume clean restore or merge.
            
            contacts.forEach { contact ->
                sharedDb.contactEntityQueries.insertContact(contact)
            }
            
            groups.forEach { group ->
                sharedDb.groupEntityQueries.insertGroup(group)
            }
            
            messages.forEach { msg ->
                sharedDb.messageEntityQueries.insertMessage(msg)
            }
        }
    }

    // Network
    suspend fun sendMessage(request: MessageRequest) = api.sendMessage(request)
    suspend fun checkMessages(recipientHash: String) = api.checkMessages(recipientHash)
    suspend fun uploadFile(bytes: ByteArray, filename: String) = api.uploadFile(bytes, filename)
    suspend fun downloadFile(fileId: String) = api.downloadFile(fileId)
}
