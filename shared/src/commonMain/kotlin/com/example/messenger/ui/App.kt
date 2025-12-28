package com.example.messenger.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.CircularProgressIndicator
import com.example.messenger.viewmodel.SharedMessengerViewModel

@Composable
fun App(viewModel: SharedMessengerViewModel) {
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Loading) }

    LaunchedEffect(isOnboardingCompleted) {
        if (!isOnboardingCompleted) {
            currentScreen = Screen.Onboarding
        } else if (currentScreen == Screen.Loading || currentScreen == Screen.Onboarding) {
            currentScreen = Screen.ContactList
        }
    }

    MessengerTheme {
        when (val screen = currentScreen) {
            is Screen.Onboarding -> {
                OnboardingScreen(
                    viewModel = viewModel,
                    onOnboardingComplete = {
                        currentScreen = Screen.ContactList
                    }
                )
            }
            is Screen.ContactList -> {
                ContactListScreen(
                    viewModel = viewModel,
                    onContactClick = { contactKey, isGroup ->
                        currentScreen = Screen.Chat(contactKey, isGroup)
                    },
                    onNavigate = { currentScreen = it }
                )
            }
            is Screen.Chat -> {
                ChatScreen(
                    viewModel = viewModel,
                    contactKey = screen.contactKey,
                    isGroup = screen.isGroup,
                    onBack = { currentScreen = Screen.ContactList }
                )
            }
            is Screen.AuthRequests -> {
                AuthRequestsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.ContactList }
                )
            }
            is Screen.Settings -> {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.ContactList }
                )
            }
            is Screen.Profile -> {
                ProfileScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.ContactList }
                )
            }
            // Add other screens as needed
            is Screen.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                // Fallback or other screens
                ContactListScreen(
                    viewModel = viewModel,
                    onContactClick = { contactKey, isGroup ->
                        currentScreen = Screen.Chat(contactKey, isGroup)
                    },
                    onNavigate = { currentScreen = it }
                )
            }
        }
    }
}

sealed class Screen {
    object Loading : Screen()
    object Onboarding : Screen()
    object ContactList : Screen()
    data class Chat(val contactKey: String, val isGroup: Boolean) : Screen()
    object Settings : Screen()
    object Profile : Screen()
    object Wallet : Screen()
    object Music : Screen()
    object AuthRequests : Screen()
}
