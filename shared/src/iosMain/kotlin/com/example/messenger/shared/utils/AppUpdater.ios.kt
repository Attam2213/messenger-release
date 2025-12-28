package com.example.messenger.shared.utils

actual class AppUpdater actual constructor(context: Any?) {
    actual suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return null
    }

    actual fun downloadAndInstall(url: String, fileName: String) {
        // Not supported on iOS via direct download
    }

    actual fun getCurrentVersion(): String {
        return "1.0.0" // TODO: Implement real version retrieval for iOS
    }
}
