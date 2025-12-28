package com.example.messenger.shared.utils

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val changelog: String
)

expect class AppUpdater(context: Any?) {
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo?
    fun downloadAndInstall(url: String, fileName: String)
}
