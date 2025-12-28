package com.example.messenger

import androidx.compose.ui.window.ComposeUIViewController
import com.example.messenger.ui.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val viewModel = IosMessengerApp.createViewModel()
    App(viewModel)
}
