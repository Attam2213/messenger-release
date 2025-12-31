package com.example.messenger.shared.utils

import android.content.Context
import android.os.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class FileHandler actual constructor(context: Any?) {
    private val ctx = context as Context

    actual suspend fun saveBackup(content: String, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Try public Downloads first
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloads.exists()) downloads.mkdirs()
                val file = File(downloads, fileName)
                file.writeText(content)
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to app-specific external storage
                try {
                    val appDownloads = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(appDownloads, fileName)
                    file.writeText(content)
                    file.absolutePath
                } catch (e2: Exception) {
                    e2.printStackTrace()
                    null
                }
            }
        }
    }

    actual suspend fun readBackup(fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Try public Downloads
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloads, fileName)
                if (file.exists()) {
                    return@withContext file.readText()
                }
                
                // Try app-specific
                val appDownloads = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val file2 = File(appDownloads, fileName)
                if (file2.exists()) {
                    return@withContext file2.readText()
                }
                
                null
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
                val file = File(ctx.cacheDir, fileName)
                file.writeBytes(bytes)
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    actual fun getTempPath(fileName: String): String {
        return File(ctx.cacheDir, fileName).absolutePath
    }
}
