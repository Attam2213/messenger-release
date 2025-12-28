package com.example.messenger.domain.model

sealed class SyncStatus {
    object Initializing : SyncStatus()
    object Connecting : SyncStatus()
    object Connected : SyncStatus()
    object Idle : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data class Downloaded(val count: Int) : SyncStatus()
}
