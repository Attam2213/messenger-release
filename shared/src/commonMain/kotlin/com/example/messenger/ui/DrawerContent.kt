package com.example.messenger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.messenger.viewmodel.SharedMessengerViewModel

@Composable
fun DrawerContent(
    viewModel: SharedMessengerViewModel,
    onNavigate: (Screen) -> Unit,
    closeDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(MaterialTheme.colors.primary),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colors.onPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Messenger User",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onPrimary
                )
                Text(
                    viewModel.myPublicKey.take(8) + "...",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onPrimary.copy(alpha = 0.7f)
                )
            }
        }

        Divider()

        // Menu Items
        DrawerItem(
            icon = Icons.Default.Contacts,
            text = "Contacts",
            onClick = {
                onNavigate(Screen.ContactList)
                closeDrawer()
            }
        )
        
        DrawerItem(
            icon = Icons.Default.Person,
            text = "Profile",
            onClick = {
                onNavigate(Screen.Profile)
                closeDrawer()
            }
        )

        DrawerItem(
            icon = Icons.Default.Security,
            text = "Auth Requests",
            onClick = {
                onNavigate(Screen.AuthRequests)
                closeDrawer()
            }
        )

        DrawerItem(
            icon = Icons.Default.Settings,
            text = "Settings",
            onClick = {
                onNavigate(Screen.Settings)
                closeDrawer()
            }
        )

        Spacer(modifier = Modifier.weight(1f))
        Divider()
        
        DrawerItem(
            icon = Icons.Default.ExitToApp,
            text = "Logout",
            onClick = {
                viewModel.logout {
                    // Navigate to onboarding or restart
                    // This is usually handled by observing state in App.kt, but we can force it here just in case
                }
                closeDrawer()
            }
        )
    }
}

@Composable
fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(32.dp))
        Text(
            text,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface
        )
    }
}
