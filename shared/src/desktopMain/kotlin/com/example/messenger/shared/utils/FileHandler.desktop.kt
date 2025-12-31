package com.example.messenger.shared.utils

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class FileHandler actual constructor(context: Any?) {
    actual suspend fun saveBackup(content: String, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val home = System.getProperty("user.home")
                val downloads = File(home, "Downloads")
                if (!downloads.exists()) downloads.mkdirs()
                val file = File(downloads, fileName)
                file.writeText(content)
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    actual suspend fun readBackup(fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val home = System.getProperty("user.home")
                val downloads = File(home, "Downloads")
                val file = File(downloads, fileName)
                if (file.exists()) {
                    file.readText()
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    actual suspend fun readFile(filePath: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) file.readBytes() else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    actual suspend fun saveFile(bytes: ByteArray, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(System.getProperty("java.io.tmpdir"), fileName)
                file.writeBytes(bytes)
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    actual fun getTempPath(fileName: String): String {
        return File(System.getProperty("java.io.tmpdir"), fileName).absolutePath
    }
}
