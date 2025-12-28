package com.example.messenger.viewmodel

import com.example.messenger.crypto.CryptoManager
import com.example.messenger.domain.model.AuthRequest
import com.example.messenger.domain.model.SyncStatus
import com.example.messenger.domain.model.DecryptedContent
import com.example.messenger.domain.model.ProcessResult
import com.example.messenger.domain.usecase.*
import com.example.messenger.repository.MessengerRepository
import com.example.messenger.shared.db.ContactEntity
import com.example.messenger.shared.db.GroupEntity
import com.example.messenger.shared.db.MessageEntity
import com.example.messenger.shared.infrastructure.CallHandler
import com.example.messenger.shared.infrastructure.NotificationHandler
import com.example.messenger.shared.utils.SharedSettingsManager
import com.example.messenger.shared.utils.AppUpdater
import com.example.messenger.shared.utils.UpdateInfo
import com.example.messenger.shared.utils.FileHandler
import com.example.messenger.domain.model.BackupData
import com.example.messenger.domain.model.BackupContact
import com.example.messenger.domain.model.BackupMessage
import com.example.messenger.domain.model.BackupGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

import com.example.messenger.domain.model.ContactUiModel

class SharedMessengerViewModel(
    private val repository: MessengerRepository,
    private val cryptoManager: CryptoManager,
    private val settingsManager: SharedSettingsManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
    private val processMessageUseCase: ProcessMessageUseCase,
    private val messageSynchronizationUseCase: MessageSynchronizationUseCase,
    private val messageDecryptionUseCase: MessageDecryptionUseCase,
    private val callHandler: CallHandler?,
    private val notificationHandler: NotificationHandler?,
    private val appUpdater: AppUpdater?,
    private val fileHandler: FileHandler?,
    private val scope: CoroutineScope
) {

    // Contacts & Groups
    val contacts: Flow<List<ContactEntity>> = repository.getAllContacts()

    val contactsUi: Flow<List<ContactUiModel>> = contacts
        .combine(repository.getUnreadCountsMap(myPublicKey)) { contactList, unreadMap ->
            contactList.map { contact ->
                ContactUiModel(contact, unreadMap[contact.publicKey] ?: 0L)
            }
        }
    val groups: Flow<List<GroupEntity>> = repository.getAllGroups()

    // Polling/Sync Status
    private val _pollingStatus = MutableStateFlow("Initializing")
    val pollingStatus: StateFlow<String> = _pollingStatus.asStateFlow()

    private val _authRequests = MutableStateFlow<List<AuthRequest>>(emptyList())
    val authRequests: StateFlow<List<AuthRequest>> = _authRequests.asStateFlow()

    // Update Status
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()
    
    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isBackupProcessing = MutableStateFlow(false)
    val isBackupProcessing: StateFlow<Boolean> = _isBackupProcessing.asStateFlow()

    // Typing Status
    private val _typingStatuses = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingStatuses: StateFlow<Map<String, Boolean>> = _typingStatuses.asStateFlow()

    // Settings
    val themeMode = settingsManager.themeMode
    val language = settingsManager.language
    val notificationsEnabled = settingsManager.notificationsEnabled
    val panicDeleteContacts = settingsManager.panicDeleteContacts
    val panicDeleteMessages = settingsManager.panicDeleteMessages
    val panicDeleteKeys = settingsManager.panicDeleteKeys
    val autoLockTime = settingsManager.autoLockTime
    val isOnboardingCompleted = settingsManager.isOnboardingCompleted
    val userName = settingsManager.userName

    val myPublicKey: String
        get() {
            val key = cryptoManager.getMyPublicKeyString()
            if (key.isEmpty()) {
                cryptoManager.reloadKeys()
                return cryptoManager.getMyPublicKeyString()
            }
            return key
        }

    init {
        observeSyncStatus()
        observeProcessResults()
    }

    private fun observeProcessResults() {
        scope.launch {
            processMessageUseCase.processResult.collect { result ->
                when (result) {
                    is ProcessResult.Typing -> {
                         val current = _typingStatuses.value.toMutableMap()
                         current[result.fromKey] = result.isTyping
                         _typingStatuses.value = current
                    }
                    is ProcessResult.AuthRequestReceived -> {
                        val current = _authRequests.value.toMutableList()
                        current.add(result.request)
                        _authRequests.value = current
                    }
                    is ProcessResult.AuthAckReceived -> {
                         notificationHandler?.showNotification("Security", "Device authorized successfully")
                    }
                    is ProcessResult.CallSignal -> {
                        callHandler?.handleIncomingCall(result.fromKey, result.type, result.content)
                    }
                    is ProcessResult.MessageSaved -> {
                        if (settingsManager.notificationsEnabled.value) {
                            val contact = repository.getContact(result.fromKey)
                            val name = contact?.name ?: "Unknown"
                            val title = if (result.groupId != null) {
                                val group = repository.getGroupById(result.groupId)
                                val groupName = group?.name ?: "Group"
                                "$groupName ($name)"
                            } else {
                                name
                            }
                            notificationHandler?.showNotification(title, "New Message")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
    private fun observeSyncStatus() {
        scope.launch {
            messageSynchronizationUseCase.status.collect { status ->
                _pollingStatus.value = when (status) {
                    is SyncStatus.Initializing -> "Initializing..."
                    is SyncStatus.Connecting -> "Connecting..."
                    is SyncStatus.Connected -> "Connected"
                    is SyncStatus.Downloaded -> "Downloaded ${status.count} messages"
                    is SyncStatus.Error -> "Error: ${status.message}"
                    is SyncStatus.Idle -> "Idle"
                }
            }
        }
    }

    // Contacts
    fun addContact(name: String, publicKey: String, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                repository.insertContact(
                    ContactEntity(
                        publicKey = publicKey,
                        name = name,
                        createdAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun updateContactName(publicKey: String, name: String, onResult: (Boolean) -> Unit) {
        scope.launch {
            try {
                repository.updateContactName(publicKey, name)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
    
    suspend fun getContact(publicKey: String): ContactEntity? {
        return repository.getContact(publicKey)
    }

    fun markAsRead(contactKey: String) {
        scope.launch {
            repository.markMessagesAsRead(myPublicKey, contactKey)
        }
    }

    // Groups
    fun createGroup(name: String, members: List<String>, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                createGroupUseCase.execute(name, members)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    // Messages
    fun getMessagesForContact(contactPublicKey: String): Flow<List<MessageEntity>> {
        return repository.getMessagesForContact(myPublicKey, contactPublicKey)
    }

    fun getMessagesForGroup(groupId: String): Flow<List<MessageEntity>> {
        return repository.getMessagesForGroup(groupId)
    }

    fun sendMessage(toPublicKey: String, content: String, replyToId: String?, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                val result = sendMessageUseCase.sendMessage(toPublicKey, content, replyToId)
                if (result.isSuccess) {
                    onResult(true, null)
                } else {
                    onResult(false, result.exceptionOrNull()?.message)
                }
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun sendGroupMessage(groupId: String, content: String, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
             try {
                val result = sendMessageUseCase.sendGroupMessage(groupId, content)
                if (result.isSuccess) {
                    onResult(true, null)
                } else {
                    onResult(false, result.exceptionOrNull()?.message)
                }
             } catch (e: Exception) {
                onResult(false, e.message)
             }
        }
    }

    fun refreshMessages() {
        scope.launch {
            messageSynchronizationUseCase.forceSync()
        }
    }

    suspend fun decryptMessage(encryptedContent: String, fromPublicKey: String): DecryptedContent {
        return messageDecryptionUseCase.execute(encryptedContent, fromPublicKey)
    }

    // Settings Actions
    fun setThemeMode(mode: String) {
        settingsManager.setThemeMode(mode)
    }

    fun setLanguage(lang: String) {
        settingsManager.setLanguage(lang)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        settingsManager.setNotificationsEnabled(enabled)
    }

    fun setPanicDeleteContacts(delete: Boolean) {
        settingsManager.setPanicDeleteContacts(delete)
    }

    fun setPanicDeleteMessages(delete: Boolean) {
        settingsManager.setPanicDeleteMessages(delete)
    }

    fun setPanicDeleteKeys(delete: Boolean) {
        settingsManager.setPanicDeleteKeys(delete)
    }

    fun setAutoLockTime(time: Long) {
        settingsManager.setAutoLockTime(time)
    }

    fun setUserName(name: String) {
        settingsManager.setUserName(name)
    }

    fun completeOnboarding() {
        settingsManager.setOnboardingCompleted(true)
    }
    
    // Identity Management
    fun hasIdentity(): Boolean = cryptoManager.hasIdentity()

    fun createIdentityInMemory() = cryptoManager.createIdentityInMemory()

    suspend fun importIdentity(privateKey: String) {
        cryptoManager.importIdentity(privateKey)
        cryptoManager.reloadKeys()
    }

    suspend fun encryptWithPassword(data: String, password: String): String {
        return cryptoManager.encryptWithPassword(data, password)
    }

    // Call Handling
    fun initiateCall(contactPublicKey: String, isVideo: Boolean) {
        callHandler?.initiateCall(contactPublicKey, isVideo)
    }

    // Auth Requests
    fun acceptAuthRequest(request: AuthRequest) {
        scope.launch {
            try {
                sendMessageUseCase.sendAuthAck(request.publicKey)
                val current = _authRequests.value.toMutableList()
                current.remove(request)
                _authRequests.value = current
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun rejectAuthRequest(request: AuthRequest) {
        // Just remove from list for now
        val current = _authRequests.value.toMutableList()
        current.remove(request)
        _authRequests.value = current
    }

    // Data Management
    fun clearAllData() {
        scope.launch {
            try {
                messageSynchronizationUseCase.reset()
                repository.disconnect()
                repository.clearAllTables()
                cryptoManager.clearIdentity()
                settingsManager.clear()
                // Restart sync or notify UI
                _pollingStatus.value = "Data cleared. Restarting..."
                // Optionally reload keys to generate new identity
                cryptoManager.reloadKeys()
            } catch (e: Exception) {
                _pollingStatus.value = "Error clearing data: ${e.message}"
            }
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        scope.launch {
            try {
                clearAllData()
                settingsManager.setOnboardingCompleted(false)
                onLogoutComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                // Still callback to allow exit
                onLogoutComplete()
            }
        }
    }

    // Updates
    fun checkForUpdates() {
        val updater = appUpdater ?: return
        
        scope.launch {
            _isCheckingUpdate.value = true
            try {
                // Assuming current version is 1.0.0, replace with real version fetching
                val currentVersion = updater.getCurrentVersion()
                val info = updater.checkForUpdate(currentVersion)
                _updateInfo.value = info
                if (info == null) {
                    val msg = if (language.value == "ru") 
                        "Обновлений нет (v$currentVersion)" 
                    else 
                        "No updates available (v$currentVersion)"
                    notificationHandler?.showNotification("Update", msg)
                }
            } catch (e: Exception) {
                val title = if (language.value == "ru") "Ошибка обновления" else "Update Error"
                val msg = e.message ?: "Unknown error"
                notificationHandler?.showNotification(title, msg)
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        _isDownloading.value = true
        _downloadProgress.value = 0f
        appUpdater?.downloadAndInstall(info.downloadUrl, "messenger_update.apk") { progress ->
            _downloadProgress.value = progress
            if (progress >= 1f) {
                _isDownloading.value = false
            }
        }
    }

    // Backup & Restore
    fun exportBackup(password: String? = null) {
        scope.launch {
            _isBackupProcessing.value = true
            try {
                val privateKey = cryptoManager.getMyPrivateKeyString()
                val contacts = repository.getAllContactsSnapshot()
                val groups = repository.getAllGroupsSnapshot()
                val messages = repository.getAllMessagesSnapshot()

                val backupContacts = contacts.map { BackupContact(it.publicKey, it.name, it.createdAt) }
                val backupGroups = groups.map { BackupGroup(it.groupId, it.name, it.members, it.createdAt) }
                val backupMessages = messages.map { 
                    BackupMessage(
                        it.messageId, it.fromPublicKey, it.toPublicKey, it.groupId, 
                        it.encryptedContent, it.timestamp, it.isDelivered, it.isRead
                    )
                }

                val backupData = BackupData(
                    privateKey = privateKey,
                    contacts = backupContacts,
                    messages = backupMessages,
                    groups = backupGroups
                )

                val jsonString = Json.encodeToString(backupData)
                
                val finalContent = if (!password.isNullOrEmpty()) {
                    cryptoManager.encryptWithPassword(jsonString, password)
                } else {
                    jsonString
                }

                val fileName = "messenger_backup_${Clock.System.now().toEpochMilliseconds()}.json"
                
                val path = fileHandler?.saveBackup(finalContent, fileName)
                
                if (path != null) {
                    notificationHandler?.showNotification("Backup", "Backup saved to $path")
                } else {
                    notificationHandler?.showNotification("Backup", "Failed to save backup")
                }
            } catch (e: Exception) {
                notificationHandler?.showNotification("Backup", "Error: ${e.message}")
            } finally {
                _isBackupProcessing.value = false
            }
        }
    }

    fun importBackup(fileName: String, password: String? = null) {
        scope.launch {
            _isBackupProcessing.value = true
            try {
                val content = fileHandler?.readBackup(fileName)
                if (content == null) {
                    notificationHandler?.showNotification("Restore", "File not found in Downloads: $fileName")
                    _isBackupProcessing.value = false
                    return@launch
                }

                val jsonString = if (!password.isNullOrEmpty()) {
                     cryptoManager.decryptWithPassword(content, password)
                } else {
                    content
                }
                
                val backupData = Json.decodeFromString<BackupData>(jsonString)
                
                // Restore Identity
                if (backupData.privateKey.isNotEmpty()) {
                    cryptoManager.importIdentity(backupData.privateKey)
                    cryptoManager.reloadKeys()
                }
                
                // Restore Data
                val contacts = backupData.contacts.map { 
                    ContactEntity(it.publicKey, it.name, it.createdAt) 
                }
                val groups = backupData.groups.map { 
                    GroupEntity(it.groupId, it.name, it.members, it.createdAt) 
                }
                val messages = backupData.messages.map { 
                    MessageEntity(it.messageId, it.fromPublicKey, it.toPublicKey, it.encryptedContent, it.timestamp, it.isDelivered, null, it.isRead, it.groupId) 
                }
                
                repository.restoreBackup(contacts, messages, groups)
                
                notificationHandler?.showNotification("Restore", "Backup restored successfully")
                refreshMessages()
                
            } catch (e: Exception) {
                notificationHandler?.showNotification("Restore", "Error: ${e.message}")
            } finally {
                _isBackupProcessing.value = false
            }
        }
    }
}
