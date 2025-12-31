package com.example.messenger.shared.infrastructure

interface AudioPlayer {
    fun play(filePath: String)
    fun stop()
    fun isPlaying(): Boolean
}
