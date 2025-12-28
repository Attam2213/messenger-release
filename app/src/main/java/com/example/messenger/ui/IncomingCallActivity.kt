package com.example.messenger.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.messenger.MessengerApplication
import com.example.messenger.webrtc.CallManager

class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Unlock Screen Logic ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Dismiss Keyguard to allow interaction if possible
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }

        val app = application as MessengerApplication
        val callManager = app.callManager

        // Handle Notification Actions
        if (intent.action == "ACCEPT_CALL") {
            callManager.acceptCall()
            finish()
            val mainIntent = android.content.Intent(this, com.example.messenger.MainActivity::class.java)
            mainIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(mainIntent)
            return
        } else if (intent.action == "DECLINE_CALL") {
            callManager.endCall()
            finish()
            return
        }

        setContent {
            val callState by callManager.callState.collectAsState()

            // If call is no longer Incoming (accepted elsewhere or ended), finish
            if (callState !is CallManager.CallState.Incoming) {
                finish()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF202020))
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .background(Color.Gray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("User", fontSize = 32.sp, color = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        "Incoming Audio Call",
                        color = Color.White,
                        fontSize = 24.sp
                    )
                }

                // Actions
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Decline
                    Button(
                        onClick = {
                            callManager.endCall()
                            finish()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White)
                    }

                    // Accept
                    Button(
                        onClick = {
                            callManager.acceptCall()
                            finish()
                            // Launch Main Activity to show Call Screen
                            val intent = android.content.Intent(this@IncomingCallActivity, com.example.messenger.MainActivity::class.java)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White)
                    }
                }
            }
        }
    }
}
