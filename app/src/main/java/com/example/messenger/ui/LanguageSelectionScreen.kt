package com.example.messenger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.messenger.R
import com.example.messenger.shared.utils.SharedSettingsManager

@Composable
fun LanguageSelectionScreen(
    settingsManager: SharedSettingsManager,
    onLanguageSelected: () -> Unit
) {
    val context = LocalContext.current
    val languages = listOf(
        "en" to stringResource(R.string.lang_en),
        "ru" to stringResource(R.string.lang_ru)
    )
    
    // Get current language from settings
    val currentLanguage by settingsManager.language.collectAsState()
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }
    
    // Update local state if external state changes (optional)
    LaunchedEffect(currentLanguage) {
        selectedLanguage = currentLanguage
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.lang_select_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column {
                languages.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (code == selectedLanguage),
                                onClick = { selectedLanguage = code }
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (code == selectedLanguage),
                            onClick = { selectedLanguage = code }
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    settingsManager.setLanguage(selectedLanguage)
                    onLanguageSelected()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(stringResource(R.string.lang_continue))
            }
        }
    }
}
