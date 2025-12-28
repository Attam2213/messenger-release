package com.example.messenger.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.util.Log

/**
 * Manages the Proximity Sensor to turn the screen off during calls when near the ear.
 * Uses PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK.
 */
class ProximitySensorManager(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null

    init {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // Check if the specialized WakeLock is supported (most phones support it)
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "Messenger:ProximitySensorManager"
            )
            wakeLock?.setReferenceCounted(false)
        } else {
            Log.w("ProximitySensorManager", "PROXIMITY_SCREEN_OFF_WAKE_LOCK not supported")
        }
    }

    fun start() {
        if (wakeLock?.isHeld == false) {
            try {
                wakeLock?.acquire()
                Log.d("ProximitySensorManager", "WakeLock acquired")
            } catch (e: Exception) {
                Log.e("ProximitySensorManager", "Failed to acquire WakeLock", e)
            }
        }
    }

    fun stop() {
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
                Log.d("ProximitySensorManager", "WakeLock released")
            } catch (e: Exception) {
                Log.e("ProximitySensorManager", "Failed to release WakeLock", e)
            }
        }
    }
}
