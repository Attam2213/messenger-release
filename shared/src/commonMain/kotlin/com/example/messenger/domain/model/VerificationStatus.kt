package com.example.messenger.domain.model

import kotlinx.serialization.Serializable

enum class VerificationStatus {
    VERIFIED,
    INVALID,
    NOT_SIGNED,
    UNTRUSTED_DEVICE
}
