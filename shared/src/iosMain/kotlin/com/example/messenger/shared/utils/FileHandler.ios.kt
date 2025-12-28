package com.example.messenger.shared.utils

actual class FileHandler actual constructor(context: Any?) {
    actual suspend fun saveBackup(content: String, fileName: String): String? {
        // Not implemented for iOS yet
        return null
    }

    actual suspend fun readBackup(fileName: String): String? {
        return null
    }
}
