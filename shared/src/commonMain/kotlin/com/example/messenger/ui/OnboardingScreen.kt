package com.example.messenger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.messenger.viewmodel.SharedMessengerViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull


@Composable
fun OnboardingScreen(
    viewModel: SharedMessengerViewModel,
    onOnboardingComplete: () -> Unit
) {
    var step by remember { mutableStateOf(OnboardingStep.Welcome) }

    when (step) {
        OnboardingStep.Welcome -> {
            WelcomeStep(
                onNext = { step = OnboardingStep.Language }
            )
        }
        OnboardingStep.Language -> {
            LanguageSelectionStep(
                viewModel = viewModel,
                onNext = { step = OnboardingStep.Profile }
            )
        }
        OnboardingStep.Profile -> {
            ProfileSetupStep(
                viewModel = viewModel,
                onNext = { step = OnboardingStep.Identity }
            )
        }
        OnboardingStep.Identity -> {
            IdentitySetupStep(
                viewModel = viewModel,
                onComplete = {
                    viewModel.completeOnboarding()
                    onOnboardingComplete()
                }
            )
        }
    }
}

@Composable
fun ProfileSetupStep(
    viewModel: SharedMessengerViewModel,
    onNext: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val language by viewModel.language.collectAsState()
    val isRussian = language == "ru"
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (isRussian) "Ваш профиль" else "Your Profile",
            style = MaterialTheme.typography.h5
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            if (isRussian) "Введите ваше имя (видно другим пользователям)" 
            else "Enter your name (visible to other users)",
            style = MaterialTheme.typography.body1
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(if (isRussian) "Имя" else "Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                viewModel.setUserName(name)
                onNext()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(0.5f)
        ) {
            Text(if (isRussian) "Далее" else "Next")
        }
    }
}

@Composable
fun WelcomeStep(
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Messenger", style = MaterialTheme.typography.h4)
        Text("Добро пожаловать в Messenger", style = MaterialTheme.typography.h5)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Secure and fast messaging.", style = MaterialTheme.typography.body1)
        Text("Безопасный и быстрый обмен сообщениями.", style = MaterialTheme.typography.body1)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(0.5f)
        ) {
            Text("Start / Начать")
        }
    }
}

@Composable
fun LanguageSelectionStep(
    viewModel: SharedMessengerViewModel,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select Language / Выберите язык", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                viewModel.setLanguage("en")
                onNext()
            },
            modifier = Modifier.fillMaxWidth(0.5f)
        ) {
            Text("English")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                viewModel.setLanguage("ru")
                onNext()
            },
            modifier = Modifier.fillMaxWidth(0.5f)
        ) {
            Text("Русский")
        }
    }
}

private enum class IdentityStep {
    Selection,
    SetupEncryption,
    ShowKeys,
    ImportKeys
}

@Composable
fun IdentitySetupStep(
    viewModel: SharedMessengerViewModel,
    onComplete: () -> Unit
) {
    val language by viewModel.language.collectAsState()
    val isRussian = language == "ru"
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    var currentStep by remember { mutableStateOf(IdentityStep.Selection) }
    var tempPrivKey by remember { mutableStateOf("") }
    var rawJsonKeys by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }
    
    // UI strings
    val strCreate = if (isRussian) "Создать новый аккаунт" else "Create New Identity"
    val strImport = if (isRussian) "Импортировать аккаунт" else "Import Identity"
    val strSetupEnc = if (isRussian) "Защита ключей паролем" else "Protect Keys with Password"
    val strSetupDesc = if (isRussian) "Придумайте пароль, чтобы зашифровать ваш приватный ключ. Это безопасно." else "Set a password to encrypt your private key. This is secure."
    val strPassHint = if (isRussian) "Пароль" else "Password"
    val strConfirmHint = if (isRussian) "Подтвердите пароль" else "Confirm Password"
    val strMismatch = if (isRussian) "Пароли не совпадают" else "Passwords do not match"
    val strEncryptBtn = if (isRussian) "Зашифровать и создать" else "Encrypt & Create"
    val strSkipBtn = if (isRussian) "Пропустить (Небезопасно)" else "Skip (Insecure)"
    val strYourKeys = if (isRussian) "Ваш секретный ключ" else "Your Secret Key"
    val strSaveWarning = if (isRussian) "ВАЖНО: Сохраните этот ключ! Мы не сможем восстановить его." else "IMPORTANT: Save this key! We cannot recover it."
    val strCopy = if (isRussian) "Копировать" else "Copy"
    val strISaved = if (isRussian) "Я сохранил ключ" else "I Saved It"
    val strPaste = if (isRussian) "Вставьте JSON ключа" else "Paste Key JSON"
    val strImportBtn = if (isRussian) "Импортировать" else "Import"
    val strBack = if (isRussian) "Назад" else "Back"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (currentStep) {
            IdentityStep.Selection -> {
                Text(if (isRussian) "Настройка профиля" else "Profile Setup", style = MaterialTheme.typography.h5)
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        val kp = viewModel.createIdentityInMemory()
                        tempPrivKey = kp.privateKeyBase64
                        currentStep = IdentityStep.SetupEncryption
                    },
                    modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
                ) {
                    Text(strCreate)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { currentStep = IdentityStep.ImportKeys },
                    modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
                ) {
                    Text(strImport)
                }
            }
            
            IdentityStep.SetupEncryption -> {
                Text(strSetupEnc, style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(16.dp))
                Text(strSetupDesc, style = MaterialTheme.typography.body2)
                Spacer(modifier = Modifier.height(24.dp))
                
                var password by remember { mutableStateOf("") }
                var confirmPassword by remember { mutableStateOf("") }
                var passwordError by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it 
                        passwordError = false
                    },
                    label = { Text(strPassHint) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { 
                        confirmPassword = it 
                        passwordError = false
                    },
                    label = { Text(strConfirmHint) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(0.7f),
                    isError = passwordError
                )
                if (passwordError) {
                    Text(strMismatch, color = MaterialTheme.colors.error)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (password.isNotEmpty() && password == confirmPassword) {
                            scope.launch {
                                val json = buildJsonObject {
                                    put("priv", tempPrivKey)
                                }.toString()
                                rawJsonKeys = viewModel.encryptWithPassword(json, password)
                                currentStep = IdentityStep.ShowKeys
                            }
                        } else {
                            passwordError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.7f).height(50.dp),
                    enabled = password.isNotEmpty()
                ) {
                    Text(strEncryptBtn)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = {
                        rawJsonKeys = buildJsonObject {
                            put("priv", tempPrivKey)
                        }.toString()
                        currentStep = IdentityStep.ShowKeys
                    }
                ) {
                    Text(strSkipBtn, color = MaterialTheme.colors.error)
                }
            }
            
            IdentityStep.ShowKeys -> {
                Text(strYourKeys, style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(8.dp))
                Text(strSaveWarning, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = rawJsonKeys,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(0.8f).height(150.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(rawJsonKeys))
                    },
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strCopy)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.importIdentity(tempPrivKey)
                            onComplete()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
                ) {
                    Text(strISaved)
                }
            }
            
            IdentityStep.ImportKeys -> {
                Text(strImport, style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = importText,
                    onValueChange = { 
                        importText = it
                        importError = false
                    },
                    label = { Text(strPaste) },
                    modifier = Modifier.fillMaxWidth(0.8f).height(200.dp),
                    isError = importError
                )
                
                if (importError) {
                    Text(
                        if (isRussian) "Ошибка импорта" else "Import Error",
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val priv = try {
                                    if (importText.trim().startsWith("{")) {
                                        val element = Json.parseToJsonElement(importText)
                                        element.jsonObject["priv"]?.jsonPrimitive?.content ?: importText
                                    } else {
                                        importText
                                    }
                                } catch (e: Exception) {
                                    importText
                                }
                                
                                viewModel.importIdentity(priv)
                                onComplete()
                            } catch (e: Exception) {
                                importError = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
                ) {
                    Text(strImportBtn)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { currentStep = IdentityStep.Selection }) {
                    Text(strBack)
                }
            }
        }
    }
}

enum class OnboardingStep {
    Welcome,
    Language,
    Profile,
    Identity
}
