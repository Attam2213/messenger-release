package com.example.messenger.shared.infrastructure

interface AudioRecorder {
    fun startRecording(filePath: String)
    fun stopRecording()
    fun isRecording(): Boolean
}
