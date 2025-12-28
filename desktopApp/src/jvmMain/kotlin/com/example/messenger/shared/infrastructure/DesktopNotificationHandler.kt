package com.example.messenger.shared.infrastructure

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage

class DesktopNotificationHandler : NotificationHandler {
    override fun showNotification(title: String, message: String) {
        if (SystemTray.isSupported()) {
            val tray = SystemTray.getSystemTray()
            // Create a simple transparent icon since we just want the notification
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val trayIcon = TrayIcon(image, "Messenger")
            trayIcon.isImageAutoSize = true
            try {
                tray.add(trayIcon)
                trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO)
                // Remove after a delay or immediately? 
                // Removing immediately might clear the notification on some platforms.
                // But keeping it requires management.
                // For now, let's keep it simple and just beep if tray fails or as well.
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Toolkit.getDefaultToolkit().beep()
    }
}
