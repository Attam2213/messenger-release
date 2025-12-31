package com.example.messenger.shared.utils

actual class FileHandler actual constructor(context: Any?) {
    actual suspend fun saveBackup(content: String, fileName: String): String? {
        // Not implemented for iOS yet
        return null
    }

    actual suspend fun readBackup(fileName: String): String? {
        return null
    }

    actual suspend fun readFile(filePath: String): ByteArray? {
        return null
    }

    actual suspend fun saveFile(bytes: ByteArray, fileName: String): String? {
        return null
    }

    actual fun getTempPath(fileName: String): String {
        return fileName // Placeholder
    }
}
