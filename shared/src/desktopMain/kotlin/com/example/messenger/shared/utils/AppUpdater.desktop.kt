package com.example.messenger.shared.utils

import java.awt.Desktop
import java.net.URI

actual class AppUpdater actual constructor(context: Any?) {
    actual suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        // Desktop implementation would check GitHub releases similar to Android
        return null
    }

    actual fun downloadAndInstall(url: String, fileName: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
