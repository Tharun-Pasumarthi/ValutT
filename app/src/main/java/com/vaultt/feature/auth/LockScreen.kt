package com.vaultt.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.vaultt.core.security.BiometricHelper
import dagger.hilt.android.EntryPointAccessors

@Composable
fun LockScreen(viewModel: SecurityViewModel) {
    val setupComplete by viewModel.setupComplete.collectAsState()
    
    if (!setupComplete) {
        SetupFlow(onSetup = { pin, q, a -> viewModel.setupVault(pin, q, a) })
    } else {
        AuthFlow(viewModel)
    }
}

@Composable
fun SetupFlow(onSetup: (String, String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("What is your favorite color?") }
    var answer by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Setup Vault T", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Protect your files with a PIN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 6) pin = it },
            label = { Text("Set 4-6 Digit PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Recovery Question (if you forget your PIN)", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Security Question") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Secret Answer") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { if (pin.length >= 4 && answer.isNotBlank()) onSetup(pin, question, answer) },
            enabled = pin.length >= 4 && answer.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Initialize Vault")
        }
    }
}

@Composable
fun AuthFlow(viewModel: SecurityViewModel) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val biometricHelper = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AuthEntryPoint::class.java
        ).biometricHelper()
    }

    fun triggerBiometric() {
        if (viewModel.isBiometricEnabled() && biometricHelper.canAuthenticate()) {
            viewModel.setTemporaryPause(true)
            biometricHelper.showBiometricPrompt(
                activity = context as FragmentActivity,
                onSuccess = { 
                    viewModel.setTemporaryPause(false)
                    viewModel.unlock("BIOMETRIC_SUCCESS") 
                },
                onError = { 
                    viewModel.setTemporaryPause(false)
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        triggerBiometric()
    }

    if (showRecovery) {
        RecoveryFlow(
            question = viewModel.getRecoveryQuestion() ?: "Security Question",
            onResult = { answer, newPin ->
                val success = viewModel.resetPinWithRecovery(answer, newPin)
                if (!success) {
                    // Error is handled inside RecoveryFlow local state if we want, 
                    // but we can return the result
                }
                success
            },
            onCancel = { showRecovery = false }
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock, 
                contentDescription = null, 
                modifier = Modifier.size(72.dp), 
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Vault T", style = MaterialTheme.typography.displaySmall)
            Text("Secure. Private. Offline.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            
            Spacer(modifier = Modifier.height(48.dp))
            
            OutlinedTextField(
                value = pin,
                onValueChange = { 
                    if (it.all { char -> char.isDigit() } && it.length <= 6) {
                        pin = it
                        error = false
                    }
                },
                label = { Text("Vault PIN") },
                isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            
            if (error) {
                Text(
                    text = "Incorrect PIN", 
                    color = MaterialTheme.colorScheme.error, 
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { if (!viewModel.unlock(pin)) error = true },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Unlock Vault")
            }
            
            if (biometricHelper.canAuthenticate() && viewModel.isBiometricEnabled()) {
                IconButton(
                    onClick = { triggerBiometric() },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint, 
                        contentDescription = "Biometric Unlock", 
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            TextButton(
                onClick = { showRecovery = true }, 
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text("Forgot PIN? Use Recovery")
            }
        }
    }
}

@Composable
fun RecoveryFlow(question: String, onResult: (String, String) -> Boolean, onCancel: () -> Unit) {
    var answer by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Vault Recovery", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Answer the following question to reset your PIN:", 
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = question, 
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it; error = null },
            label = { Text("Your Secret Answer") },
            modifier = Modifier.fillMaxWidth(),
            isError = error != null
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = newPin,
            onValueChange = { 
                if (it.all { char -> char.isDigit() } && it.length <= 6) {
                    newPin = it
                    error = null
                }
            },
            label = { Text("Set New 4-6 Digit PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            isError = error != null
        )
        
        if (error != null) {
            Text(
                text = error!!, 
                color = MaterialTheme.colorScheme.error, 
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                if (onResult(answer, newPin)) {
                    // Success is handled by isLocked state changing in MainActivity
                } else {
                    error = "Incorrect answer. Please try again."
                }
            },
            enabled = answer.isNotBlank() && newPin.length >= 4,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Reset PIN & Access")
        }
        
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text("Cancel")
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AuthEntryPoint {
    fun biometricHelper(): BiometricHelper
}
