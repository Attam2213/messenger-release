package com.example.messenger.shared.utils

interface SettingsStorage {
    fun getString(key: String, defaultValue: String?): String?
    fun putString(key: String, value: String)
    
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    
    fun getLong(key: String, defaultValue: Long): Long
    fun putLong(key: String, value: Long)
    
    fun clear()
}
