package com.example.messenger.shared.infrastructure

interface WorkScheduler {
    fun scheduleOneTimeSync()
    fun schedulePeriodicSync(intervalMinutes: Long)
}
