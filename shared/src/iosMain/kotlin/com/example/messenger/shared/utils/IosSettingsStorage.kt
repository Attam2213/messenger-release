package com.example.messenger.shared.utils

import platform.Foundation.NSUserDefaults

class IosSettingsStorage : SettingsStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String, defaultValue: String?): String? {
        return defaults.stringForKey(key) ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        // boolForKey returns false if key doesn't exist, which might be ambiguous if defaultValue is true.
        // But standard behavior is usually sufficient.
        if (defaults.objectForKey(key) == null) return defaultValue
        return defaults.boolForKey(key)
    }

    override fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        if (defaults.objectForKey(key) == null) return defaultValue
        return defaults.integerForKey(key).toLong()
    }

    override fun putLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = key)
    }

    override fun clear() {
        val dictionary = defaults.dictionaryRepresentation()
        for (key in dictionary.keys) {
            defaults.removeObjectForKey(key as String)
        }
    }
}
