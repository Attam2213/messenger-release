package com.example.messenger.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.messenger.viewmodel.CallViewModel
import com.example.messenger.webrtc.WebRtcManager
import com.example.messenger.webrtc.CallManager

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    webRtcManager: WebRtcManager,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    var isMicOn by remember { mutableStateOf(true) }
    var isSpeakerOn by remember { mutableStateOf(false) } // Default to Earpiece? Or Speaker? Let's default false (Earpiece) for privacy, or true for testing.
    
    // Call State
    val callState by viewModel.callState.collectAsState()
    val iceState by viewModel.iceConnectionState.collectAsState()
    
    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }
    
    // Effect to toggle speaker based on state
    LaunchedEffect(isSpeakerOn) {
        webRtcManager.setSpeakerphoneOn(isSpeakerOn)
    }

    if (!hasPermissions) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Microphone permission is required.", color = Color.White)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF202020))) {
        
        // Status Text (Top)
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when(callState) {
                    is CallManager.CallState.Incoming -> "Incoming Audio Call..."
                    is CallManager.CallState.Outgoing -> "Calling..."
                    is CallManager.CallState.Connected -> "Connected"
                    is CallManager.CallState.Ended -> "Call Ended"
                    else -> "Ready"
                },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (callState is CallManager.CallState.Connected) {
                Text(
                    text = "Quality: $iceState",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }

        // Avatar (Center)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(120.dp)
                .background(Color.Gray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "User", // Placeholder
                color = Color.White,
                fontSize = 24.sp
            )
        }

        // Controls (Bottom)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 1. Speaker Toggle
            IconButton(
                onClick = { isSpeakerOn = !isSpeakerOn },
                modifier = Modifier
                    .size(56.dp)
                    .background(if (isSpeakerOn) Color.White else Color.DarkGray, CircleShape)
            ) {
                Icon(
                    imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Speaker",
                    tint = if (isSpeakerOn) Color.Black else Color.White
                )
            }

            // 2. Mic Toggle
            IconButton(
                onClick = { 
                    isMicOn = !isMicOn
                    // Assuming WebRtcManager has a method for this, otherwise we add it
                    // webRtcManager.toggleMicrophone(isMicOn) 
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(if (isMicOn) Color.DarkGray else Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "Mic",
                    tint = if (isMicOn) Color.White else Color.Black
                )
            }

            // 3. End Call
            IconButton(
                onClick = onEndCall,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Red, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White
                )
            }
        }
    }
}
