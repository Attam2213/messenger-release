package com.example.messenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.messenger.MainActivity
import com.example.messenger.MessengerApplication
import com.example.messenger.R
import com.example.messenger.ui.IncomingCallActivity
import com.example.messenger.webrtc.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.content.pm.ServiceInfo

import com.example.messenger.domain.model.SyncStatus

class MessengerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val INCOMING_CALL_ID = 1001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForegroundService()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as MessengerApplication
        
        // Start Synchronization
        serviceScope.launch {
            app.messageSynchronizationUseCase.startSynchronization()
        }

        // Observe Sync Status for Notifications
        serviceScope.launch {
            app.messageSynchronizationUseCase.status.collect { status ->
                if (status is SyncStatus.Downloaded) {
                    if (status.count > 0) {
                        showNewMessagesNotification(status.count)
                    }
                }
            }
        }
        
        // Observe Call State for Incoming Calls
        serviceScope.launch {
            app.callManager.callState.collect { state ->
                if (state is CallManager.CallState.Incoming) {
                    showIncomingCallNotification(state.fromKey)
                } else {
                    cancelIncomingCallNotification()
                }
            }
        }

        return START_STICKY
    }

    private fun showIncomingCallNotification(fromKey: String) {
        val channelId = "messenger_incoming_calls_v2"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming audio calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                enableLights(true)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Sound is handled by CallViewModel/RingtonePlayer, but we can set it here too if needed
                // For now, let RingtonePlayer handle it to avoid double playing
                setSound(null, null) 
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Full Screen Intent
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            111,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action
        val acceptIntent = Intent(this, IncomingCallActivity::class.java).apply {
            action = "ACCEPT_CALL"
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, 112, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action
        val declineIntent = Intent(this, IncomingCallActivity::class.java).apply {
            action = "DECLINE_CALL"
        }
        val declinePendingIntent = PendingIntent.getActivity(
            this, 113, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Audio Call")
            .setContentText("Call from User") // Ideally resolve name
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setFullScreenIntent(fullScreenPendingIntent, true) // IMPORTANT
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .setContentIntent(fullScreenPendingIntent)
            .build()

        notificationManager.notify(INCOMING_CALL_ID, notification)
    }
    
    private fun cancelIncomingCallNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INCOMING_CALL_ID)
    }

    private fun showNewMessagesNotification(count: Int) {
        val channelId = "messenger_messages_v2"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.status_downloaded, count))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startForegroundService() {
        val channelId = "MessengerServiceChannel"
        val channelName = "Messenger Background Service"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            123,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Messenger is running")
            .setContentText("Listening for new messages...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }
}
