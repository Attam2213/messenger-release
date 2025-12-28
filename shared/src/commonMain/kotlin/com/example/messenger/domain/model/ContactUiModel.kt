package com.example.messenger.domain.model

import com.example.messenger.shared.db.ContactEntity

data class ContactUiModel(
    val contact: ContactEntity,
    val unreadCount: Long
)
