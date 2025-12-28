package com.example.messenger.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.vk.api.sdk.VK

object VkAuthManager {
    private val _isLoggedIn = MutableStateFlow(VK.isLoggedIn())
    val isLoggedIn = _isLoggedIn.asStateFlow()

    fun updateLoginState() {
        _isLoggedIn.value = VK.isLoggedIn()
    }
}
