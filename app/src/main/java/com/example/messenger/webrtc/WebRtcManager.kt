package com.example.messenger.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.messenger.utils.ProximitySensorManager
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Manages WebRTC Core Logic for Audio Calls.
 * Handles PeerConnectionFactory, AudioDeviceModule, and PeerConnection.
 */
class WebRtcManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "WebRtcManager"
        private const val STUN_SERVER = "stun:stun.l.google.com:19302"
        private const val TURN_SERVER_UDP = "turn:155.212.170.166:3478?transport=udp"
        private const val TURN_SERVER_TCP = "turn:155.212.170.166:3478?transport=tcp"
        private const val TURN_USER = "admin"
        private const val TURN_PASS = "password123"
    }

    // --- Core WebRTC Components ---
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    
    // --- Utilities ---
    private val proximitySensorManager = ProximitySensorManager(context)

    // --- Signaling Callback ---
    interface SignalingCallback {
        fun onOfferCreated(description: SessionDescription)
        fun onAnswerCreated(description: SessionDescription)
        fun onIceCandidate(candidate: IceCandidate)
    }
    private var signalingCallback: SignalingCallback? = null

    // --- Debug Logging ---
    var logCallback: ((String) -> Unit)? = null
    private fun log(msg: String) {
        Log.d(TAG, msg)
        logCallback?.invoke(msg)
    }
    private fun logError(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
        logCallback?.invoke("ERR: $msg ${t?.message ?: ""}")
    }

    init {
        initializeFactory()
    }

    // 1. Initialize PeerConnectionFactory and Audio Device Module
    private fun initializeFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()

        // Configure Audio Device Module (Hardware AEC/NS preferred)
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            // Error callbacks for debugging
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(p0: String?) = logError("AudioRecordInitError: $p0")
                override fun onWebRtcAudioRecordStartError(p0: JavaAudioDeviceModule.AudioRecordStartErrorCode?, p1: String?) = logError("AudioRecordStartError: $p0 $p1")
                override fun onWebRtcAudioRecordError(p0: String?) = logError("AudioRecordError: $p0")
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(p0: String?) = logError("AudioTrackInitError: $p0")
                override fun onWebRtcAudioTrackStartError(p0: JavaAudioDeviceModule.AudioTrackStartErrorCode?, p1: String?) = logError("AudioTrackStartError: $p0 $p1")
                override fun onWebRtcAudioTrackError(p0: String?) = logError("AudioTrackError: $p0")
            })
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        log("WebRtcManager initialized. Factory ready.")
    }

    fun setSignalingCallback(cb: SignalingCallback) {
        this.signalingCallback = cb
    }

    // 2. Prepare Local Audio (Source & Track)
    fun initAudio() {
        if (localAudioTrack != null) return // Already initialized

        try {
            // Audio Constraints: Echo Cancellation, Auto Gain, Noise Suppression
            val constraints = MediaConstraints().apply {
                // Mandatory constraints
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                
                // Optional constraints for better quality
                optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                optional.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
                optional.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
            }

            audioSource = peerConnectionFactory?.createAudioSource(constraints)
            localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)
            localAudioTrack?.setEnabled(true)
            
            log("Local Audio initialized.")
        } catch (e: Exception) {
            logError("Failed to init audio", e)
        }
    }

    // 3. Create PeerConnection
    fun createPeerConnection(observer: PeerConnection.Observer, isInitiator: Boolean): PeerConnection? {
        initAudio() // Ensure audio is ready

        val iceServers = listOf(
            PeerConnection.IceServer.builder(STUN_SERVER).createIceServer(),
            PeerConnection.IceServer.builder(TURN_SERVER_UDP)
                .setUsername(TURN_USER)
                .setPassword(TURN_PASS)
                .createIceServer(),
            PeerConnection.IceServer.builder(TURN_SERVER_TCP)
                .setUsername(TURN_USER)
                .setPassword(TURN_PASS)
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL // Use TURN/STUN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        if (peerConnection == null) {
            logError("Failed to create PeerConnection")
            return null
        }

        // --- TRANSCEIVER LOGIC ---
        // If we are the Initiator (Caller), we add the transceiver immediately.
        // If we are the Answerer (Callee), we wait for the remote offer to create the transceiver.
        if (isInitiator) {
            log("Role: INITIATOR. Adding Audio Transceiver.")
            val transceiverInit = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
            val transceiver = peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, transceiverInit)
            
            if (localAudioTrack != null) {
                transceiver?.sender?.setTrack(localAudioTrack, true)
                log("Track attached to sender.")
            }
        } else {
            log("Role: RECEIVER. Waiting for remote offer.")
        }
        
        // Activate Proximity Sensor if Speaker is OFF
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (!am.isSpeakerphoneOn) {
                enableProximitySensor(true)
            }
        } catch (e: Exception) {
            logError("Failed to check speaker state", e)
        }

        return peerConnection
    }

    // 4. Offer / Answer
    fun createOffer() {
        val constraints = MediaConstraints() // Default constraints
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    log("Offer Created.")
                    setLocalDescription(it)
                    signalingCallback?.onOfferCreated(it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(s: String?) = logError("CreateOffer failed: $s")
            override fun onSetFailure(s: String?) {}
        }, constraints)
    }

    fun createAnswer() {
        // Before creating answer, Answerer must attach their track to the received transceiver
        attachTrackToTransceivers()

        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    log("Answer Created.")
                    setLocalDescription(it)
                    signalingCallback?.onAnswerCreated(it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(s: String?) = logError("CreateAnswer failed: $s")
            override fun onSetFailure(s: String?) {}
        }, constraints)
    }

    private fun attachTrackToTransceivers() {
        if (localAudioTrack == null) {
            logError("No local audio track to attach!")
            return
        }
        
        peerConnection?.transceivers?.forEach { transceiver ->
            if (transceiver.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                // For Answerer, we find the transceiver created by remote offer
                if (transceiver.sender.track() == null) {
                    log("Attaching track to Transceiver ${transceiver.mid}")
                    try {
                        transceiver.sender.setTrack(localAudioTrack, true)
                        transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                    } catch (e: Exception) {
                        logError("Failed to attach track", e)
                    }
                }
            }
        }
    }

    private fun setLocalDescription(desc: SessionDescription) {
        peerConnection?.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() = log("Local SDP Set.")
            override fun onCreateFailure(s: String?) = logError("SetLocalSDP failed: $s")
            override fun onSetFailure(s: String?) = logError("SetLocalSDP failed: $s")
        }, desc)
    }

    fun setRemoteDescription(desc: SessionDescription, onSuccess: () -> Unit) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                log("Remote SDP Set.")
                onSuccess()
            }
            override fun onCreateFailure(s: String?) = logError("SetRemoteSDP failed: $s")
            override fun onSetFailure(s: String?) = logError("SetRemoteSDP failed: $s")
        }, desc)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    // 5. Cleanup
    fun close() {
        try {
            peerConnection?.close()
        } catch (e: Exception) {}
        peerConnection = null
        
        try {
            localAudioTrack?.dispose()
            audioSource?.dispose()
        } catch (e: Exception) {}
        localAudioTrack = null
        audioSource = null
        
        enableProximitySensor(false)
        
        log("WebRtcManager resources closed.")
    }
    
    // 6. Audio Routing Utilities
    fun setSpeakerphoneOn(enable: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = enable
            log("Speakerphone: $enable")
            
            // Proximity Sensor Logic:
            // Enable if: Speaker is OFF AND Call is Active (PeerConnection exists)
            // Disable if: Speaker is ON
            val useProximity = !enable && hasPeerConnection()
            enableProximitySensor(useProximity)
            
        } catch (e: Exception) {
            logError("Failed to set audio route", e)
        }
    }
    
    fun toggleMicrophone(enable: Boolean) {
        localAudioTrack?.setEnabled(enable)
        log("Microphone enabled: $enable")
    }
    
    // 7. Proximity Sensor
    fun enableProximitySensor(enable: Boolean) {
        if (enable) {
            proximitySensorManager.start()
        } else {
            proximitySensorManager.stop()
        }
    }
    
    fun hasPeerConnection() = peerConnection != null
}
