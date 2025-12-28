package com.example.messenger.shared.infrastructure

interface NotificationHandler {
    fun showNotification(title: String, message: String)
}
