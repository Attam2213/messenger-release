package com.example.messenger.utils

import android.content.Context
import android.media.MediaPlayer
import androidx.core.net.toUri
import java.io.File
import com.example.messenger.shared.infrastructure.AudioPlayer

class AudioPlayer(private val context: Context) : AudioPlayer {
    private var player: MediaPlayer? = null
    var onProgressChanged: ((Float) -> Unit)? = null // 0.0 to 1.0
    var onCompletion: (() -> Unit)? = null

    override fun play(filePath: String) {
        playFile(File(filePath))
    }

    fun playFile(file: File) {
        stop()
        MediaPlayer.create(context, file.toUri()).apply {
            player = this
            setOnCompletionListener { 
                onCompletion?.invoke()
                stop() 
            }
            start()
        }
    }
    
    fun playBytes(data: ByteArray) {
        try {
            stop()
            // Create temp file
            val tempFile = File.createTempFile("voice_", ".m4a", context.cacheDir)
            tempFile.writeBytes(data)
            tempFile.deleteOnExit()
            
            player = MediaPlayer.create(context, tempFile.toUri())
            player?.setOnCompletionListener {
                onCompletion?.invoke()
                // Do not nullify player immediately if we want to allow replay without reloading, 
                // but for simplicity, we might reset state in UI. 
                // Actually, standard behavior is to stop.
                // Let's keep it simple: stop and reset.
                // But wait, if we want to seek after completion, we shouldn't destroy it?
                // For now, let's stick to simple "play finishes -> reset".
                // User can replay by clicking play again.
            }
            player?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        if (player?.isPlaying == true) {
            player?.pause()
        }
    }

    fun resume() {
        if (player != null && !player!!.isPlaying) {
            player?.start()
        }
    }

    fun seekTo(progress: Float) {
        player?.let {
            val duration = it.duration
            val newPos = (duration * progress).toInt()
            it.seekTo(newPos)
        }
    }

    override fun isPlaying(): Boolean {
        return player?.isPlaying == true
    }
    
    fun getProgress(): Float {
        return player?.let {
            if (it.duration > 0) it.currentPosition.toFloat() / it.duration.toFloat() else 0f
        } ?: 0f
    }

    override fun stop() {
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        player = null
    }
}
