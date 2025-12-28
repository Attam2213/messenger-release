package com.example.messenger.infrastructure

import android.content.Context
import android.widget.Toast
import com.example.messenger.shared.infrastructure.NotificationHandler

class AndroidNotificationHandler(private val context: Context) : NotificationHandler {
    override fun showNotification(title: String, message: String) {
        // Ensure UI operations are on the main thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "$title: $message", Toast.LENGTH_SHORT).show()
        }
    }
}
