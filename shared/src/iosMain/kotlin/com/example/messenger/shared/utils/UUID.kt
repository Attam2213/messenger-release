package com.example.messenger.shared.utils

import platform.Foundation.NSUUID

actual fun randomUUID(): String = NSUUID().UUIDString()
