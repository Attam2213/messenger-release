package com.example.messenger.shared.utils

expect class FileHandler(context: Any?) {
    suspend fun saveBackup(content: String, fileName: String): String?
    suspend fun readBackup(fileName: String): String?
}
