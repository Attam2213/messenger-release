package com.example.messenger.webrtc

import android.util.Log
import com.example.messenger.domain.model.ProcessResult
import com.example.messenger.shared.infrastructure.CallSignalProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/**
 * Manages Call State and Signaling Logic.
 * Bridges WebRtcManager with ViewModel/Repository.
 */
class CallManager(
    private val webRtcManager: WebRtcManager
) : WebRtcManager.SignalingCallback, CallSignalProcessor {

    companion object {
        private const val TAG = "CallManager"
    }

    sealed class CallState {
        object Idle : CallState()
        data class Incoming(val fromKey: String, val offerSdp: String) : CallState()
        data class Outgoing(val toKey: String) : CallState()
        object Connected : CallState()
        object Ended : CallState()
    }

    // --- State Flows ---
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _iceConnectionState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val iceConnectionState: StateFlow<PeerConnection.IceConnectionState> = _iceConnectionState.asStateFlow()

    // --- Internal State ---
    private var currentPeerKey: String? = null
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>() // Arrived before Remote SDP set
    private val pendingOutgoingCandidates = mutableListOf<IceCandidate>() // Generated before Signaling ready

    // Callback to send signals via Repository/Socket
    var sendSignalAction: ((type: String, content: String, toKey: String) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) flushOutgoingCandidates()
        }

    init {
        webRtcManager.setSignalingCallback(this)
        webRtcManager.logCallback = { Log.d(TAG, "WebRTC: $it") }
    }

    // --- 1. Start / Accept / End Call ---

    fun startCall(targetKey: String) {
        if (_callState.value !is CallState.Idle) return

        currentPeerKey = targetKey
        _callState.value = CallState.Outgoing(targetKey)

        // 1. Create PeerConnection as INITIATOR
        initializePeerConnection(isInitiator = true)
        
        // 2. Create Offer
        webRtcManager.createOffer()
    }

    fun acceptCall() {
        val state = _callState.value
        if (state is CallState.Incoming) {
            val offerSdp = state.offerSdp
            _callState.value = CallState.Connected

            // 1. Create PeerConnection as ANSWERER (Receiver)
            initializePeerConnection(isInitiator = false)

            // 2. Set Remote Description (Offer)
            val sdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
            webRtcManager.setRemoteDescription(sdp) {
                // 3. Create Answer (after Remote SDP is set)
                webRtcManager.createAnswer()
                drainPendingRemoteCandidates()
            }
        }
    }

    fun endCall() {
        if (currentPeerKey != null) {
            sendSignalAction?.invoke("HANGUP", "", currentPeerKey!!)
        }
        cleanup()
    }

    private fun cleanup() {
        webRtcManager.close()
        _callState.value = CallState.Idle
        _iceConnectionState.value = PeerConnection.IceConnectionState.NEW
        currentPeerKey = null
        pendingRemoteCandidates.clear()
        pendingOutgoingCandidates.clear()
    }

    // --- 2. Signal Processing (Incoming) ---

    override fun processSignal(signal: ProcessResult.CallSignal) {
        Log.d(TAG, "Signal: ${signal.type} from ${signal.fromKey}")
        
        when (signal.type) {
            "OFFER" -> handleOffer(signal.fromKey, signal.content)
            "ANSWER" -> handleAnswer(signal.content)
            "CANDIDATE" -> handleCandidate(signal.content)
            "HANGUP" -> handleHangup()
        }
    }

    private fun handleOffer(fromKey: String, sdpContent: String) {
        if (_callState.value is CallState.Idle) {
            currentPeerKey = fromKey
            _callState.value = CallState.Incoming(fromKey, sdpContent)
            // Note: We don't create PeerConnection yet. User must click "Accept".
        }
    }

    private fun handleAnswer(sdpContent: String) {
        if (_callState.value is CallState.Outgoing) {
            _callState.value = CallState.Connected
            val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpContent)
            webRtcManager.setRemoteDescription(sdp) {
                drainPendingRemoteCandidates()
            }
        }
    }

    private fun handleCandidate(content: String) {
        try {
            val json = JSONObject(content)
            val candidate = IceCandidate(
                json.getString("sdpMid"),
                json.getInt("sdpMLineIndex"),
                json.getString("candidate")
            )

            if (webRtcManager.hasPeerConnection()) {
                webRtcManager.addIceCandidate(candidate)
            } else {
                pendingRemoteCandidates.add(candidate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse candidate", e)
        }
    }

    private fun handleHangup() {
        cleanup()
    }

    // --- 3. Internal Logic ---

    private fun initializePeerConnection(isInitiator: Boolean) {
        webRtcManager.createPeerConnection(object : PeerConnection.Observer {
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                s?.let { _iceConnectionState.value = it }
                if (s == PeerConnection.IceConnectionState.DISCONNECTED || s == PeerConnection.IceConnectionState.FAILED) {
                    // Optional: Auto-reconnect or End Call
                    // endCall() 
                }
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(c: IceCandidate?) {
                c?.let { onLocalIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onAddStream(s: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(s: org.webrtc.MediaStream?) {}
            override fun onDataChannel(d: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: org.webrtc.RtpReceiver?, s: Array<out org.webrtc.MediaStream>?) {}
        }, isInitiator)
        
        // Default audio routing: Speaker for testing, can be toggled
        // webRtcManager.setSpeakerphoneOn(true) 
    }

    private fun drainPendingRemoteCandidates() {
        pendingRemoteCandidates.forEach { webRtcManager.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    // --- 4. Signaling Callbacks (Outgoing) ---

    override fun onOfferCreated(description: SessionDescription) {
        currentPeerKey?.let { key ->
            sendSignalAction?.invoke("OFFER", description.description, key)
        }
    }

    override fun onAnswerCreated(description: SessionDescription) {
        currentPeerKey?.let { key ->
            sendSignalAction?.invoke("ANSWER", description.description, key)
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        onLocalIceCandidate(candidate)
    }

    private fun onLocalIceCandidate(candidate: IceCandidate) {
        val json = JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }
        
        if (sendSignalAction != null && currentPeerKey != null) {
            sendSignalAction?.invoke("CANDIDATE", json.toString(), currentPeerKey!!)
        } else {
            pendingOutgoingCandidates.add(candidate)
        }
    }

    private fun flushOutgoingCandidates() {
        if (currentPeerKey == null || sendSignalAction == null) return
        
        val iterator = pendingOutgoingCandidates.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
             val json = JSONObject().apply {
                put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
                put("candidate", candidate.sdp)
            }
            sendSignalAction?.invoke("CANDIDATE", json.toString(), currentPeerKey!!)
            iterator.remove()
        }
    }
}
