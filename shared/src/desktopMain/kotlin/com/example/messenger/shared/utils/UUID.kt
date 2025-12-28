package com.example.messenger.shared.utils

import java.util.UUID

actual fun randomUUID(): String = UUID.randomUUID().toString()
