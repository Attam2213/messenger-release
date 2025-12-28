package com.example.messenger.shared.utils

actual class AppUpdater actual constructor(context: Any?) {
    actual suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return null
    }

    actual fun downloadAndInstall(url: String, fileName: String) {
        // Not supported on iOS via direct download
    }
}
