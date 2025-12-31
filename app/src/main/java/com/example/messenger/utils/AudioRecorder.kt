package com.example.messenger.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import com.example.messenger.shared.infrastructure.AudioRecorder

class AudioRecorder(private val context: Context) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    override fun startRecording(filePath: String) {
        val outputFile = File(filePath)
        this.outputFile = outputFile
        createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(FileOutputStream(outputFile).fd)
            prepare()
            start()
            recorder = this
            isRecording = true
        }
    }

    override fun stopRecording() {
        try {
            recorder?.stop()
            recorder?.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        recorder = null
        isRecording = false
    }

    override fun isRecording(): Boolean = isRecording

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }
}
