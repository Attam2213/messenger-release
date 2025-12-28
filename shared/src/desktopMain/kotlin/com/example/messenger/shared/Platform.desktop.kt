package com.example.messenger.shared

class DesktopPlatform : Platform {
    override val name: String = "Desktop (Windows/Linux/macOS)"
}

actual fun getPlatform(): Platform = DesktopPlatform()
