package com.example.messenger.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
