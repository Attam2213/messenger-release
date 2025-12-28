package com.example.messenger.shared.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.messenger.shared.utils.SettingsStorage

class SharedSettingsManager(private val storage: SettingsStorage) {

    private val _themeMode = MutableStateFlow(storage.getString(KEY_THEME_MODE, "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(storage.getString(KEY_LANGUAGE, "ru") ?: "ru")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(storage.getBoolean(KEY_NOTIFICATIONS_ENABLED, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    // Security Settings
    private val _panicDeleteContacts = MutableStateFlow(storage.getBoolean(KEY_PANIC_DELETE_CONTACTS, true))
    val panicDeleteContacts: StateFlow<Boolean> = _panicDeleteContacts.asStateFlow()

    private val _panicDeleteMessages = MutableStateFlow(storage.getBoolean(KEY_PANIC_DELETE_MESSAGES, true))
    val panicDeleteMessages: StateFlow<Boolean> = _panicDeleteMessages.asStateFlow()

    private val _panicDeleteKeys = MutableStateFlow(storage.getBoolean(KEY_PANIC_DELETE_KEYS, true))
    val panicDeleteKeys: StateFlow<Boolean> = _panicDeleteKeys.asStateFlow()

    private val _autoLockTime = MutableStateFlow(storage.getLong(KEY_AUTO_LOCK_TIME, 0L))
    val autoLockTime: StateFlow<Long> = _autoLockTime.asStateFlow()

    private val _deviceId = MutableStateFlow(getOrCreateDeviceId())
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _isAccountConfirmed = MutableStateFlow(storage.getBoolean(KEY_ACCOUNT_CONFIRMED, true))
    val isAccountConfirmed: StateFlow<Boolean> = _isAccountConfirmed.asStateFlow()

    private val _userName = MutableStateFlow(storage.getString(KEY_USER_NAME, "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(storage.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    fun isLanguageSet(): Boolean {
        return storage.getString(KEY_LANGUAGE, null) != null
    }

    private fun getOrCreateDeviceId(): String {
        var id = storage.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            // Use a simple random ID if UUID is not available in common (or use expect/actual for UUID)
            // For now, let's use a simple workaround or expect/actual.
            // But since UUID is Java, let's make a simple random string generator for now or use expect/actual.
            id = generateRandomId()
            storage.putString(KEY_DEVICE_ID, id)
        }
        return id
    }
    
    private fun generateRandomId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..36).map { chars.random() }.joinToString("")
    }

    fun setThemeMode(mode: String) {
        storage.putString(KEY_THEME_MODE, mode)
        _themeMode.value = mode
    }

    fun setLanguage(lang: String) {
        storage.putString(KEY_LANGUAGE, lang)
        _language.value = lang
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
        _notificationsEnabled.value = enabled
    }

    fun setPanicDeleteContacts(delete: Boolean) {
        storage.putBoolean(KEY_PANIC_DELETE_CONTACTS, delete)
        _panicDeleteContacts.value = delete
    }

    fun setPanicDeleteMessages(delete: Boolean) {
        storage.putBoolean(KEY_PANIC_DELETE_MESSAGES, delete)
        _panicDeleteMessages.value = delete
    }

    fun setPanicDeleteKeys(delete: Boolean) {
        storage.putBoolean(KEY_PANIC_DELETE_KEYS, delete)
        _panicDeleteKeys.value = delete
    }
    
    fun setAutoLockTime(timeMillis: Long) {
        storage.putLong(KEY_AUTO_LOCK_TIME, timeMillis)
        _autoLockTime.value = timeMillis
    }

    fun setAccountConfirmed(confirmed: Boolean) {
        storage.putBoolean(KEY_ACCOUNT_CONFIRMED, confirmed)
        _isAccountConfirmed.value = confirmed
    }

    fun setUserName(name: String) {
        storage.putString(KEY_USER_NAME, name)
        _userName.value = name
    }

    fun setOnboardingCompleted(completed: Boolean) {
        storage.putBoolean(KEY_ONBOARDING_COMPLETED, completed)
        _isOnboardingCompleted.value = completed
    }
    
    fun clear() {
        storage.clear()
        _themeMode.value = "system"
        _language.value = "ru"
        // Generate new Device ID on clear? Or keep?
        // Usually panic clears everything.
        _deviceId.value = getOrCreateDeviceId()
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_PANIC_DELETE_CONTACTS = "panic_delete_contacts"
        private const val KEY_PANIC_DELETE_MESSAGES = "panic_delete_messages"
        private const val KEY_PANIC_DELETE_KEYS = "panic_delete_keys"
        private const val KEY_AUTO_LOCK_TIME = "auto_lock_time"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ACCOUNT_CONFIRMED = "account_confirmed"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
