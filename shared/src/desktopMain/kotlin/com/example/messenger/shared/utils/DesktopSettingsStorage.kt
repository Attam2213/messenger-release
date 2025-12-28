package com.example.messenger.shared.utils

import java.util.prefs.Preferences

class DesktopSettingsStorage : SettingsStorage {
    private val prefs = Preferences.userNodeForPackage(DesktopSettingsStorage::class.java)

    override fun getString(key: String, defaultValue: String?): String? {
        return prefs.get(key, defaultValue)
    }

    override fun putString(key: String, value: String) {
        prefs.put(key, value)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }

    override fun putLong(key: String, value: Long) {
        prefs.putLong(key, value)
    }

    override fun clear() {
        prefs.clear()
    }
}
