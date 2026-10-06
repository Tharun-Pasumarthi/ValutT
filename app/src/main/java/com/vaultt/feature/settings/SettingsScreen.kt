package com.vaultt.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showDestroyDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    
    val stats by viewModel.storageStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader("Security")
            
            ListItem(
                headlineContent = { Text("Biometric Unlock") },
                supportingContent = { Text("Use fingerprint or face to unlock") },
                leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                trailingContent = { 
                    Switch(
                        checked = viewModel.isBiometricEnabled(), 
                        onCheckedChange = { viewModel.setBiometricEnabled(it) }
                    ) 
                }
            )
            
            ListItem(
                headlineContent = { Text("Change PIN") },
                supportingContent = { Text("Update your vault access code") },
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.clickable { showPinDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader("Vault")
            
            ListItem(
                headlineContent = { Text("Storage Analyzer") },
                supportingContent = { Text("View encrypted space usage") },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                modifier = Modifier.clickable { 
                    viewModel.loadStats()
                    showStatsDialog = true 
                }
            )
            
            ListItem(
                headlineContent = { Text("Encryption Protocol") },
                supportingContent = { Text("XChaCha20-Poly1305 (Military-Grade)") },
                leadingContent = { Icon(Icons.Default.Security, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            ListItem(
                headlineContent = { Text("Destroy Vault", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Permanently delete ALL data and keys") },
                leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { showDestroyDialog = true }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showPinDialog) {
        ChangePinDialog(
            onDismiss = { showPinDialog = false },
            onPinChanged = { old, new -> 
                viewModel.changePin(old, new)
            }
        )
    }

    if (showStatsDialog) {
        StorageStatsDialog(
            stats = stats,
            onDismiss = { showStatsDialog = false }
        )
    }

    if (showDestroyDialog) {
        AlertDialog(
            onDismissRequest = { showDestroyDialog = false },
            title = { Text("PERMANENT DESTRUCTION") },
            text = { Text("This will permanently delete all encrypted files, database records, and security keys. THIS CANNOT BE UNDONE. Are you absolutely sure?") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.destroyVault { 
                        // Navigation handled by setup status observation
                    }
                    showDestroyDialog = false
                }) {
                    Text("DESTROY EVERYTHING", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestroyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChangePinDialog(onDismiss: () -> Unit, onPinChanged: (String, String) -> Boolean) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { oldPin = it; error = null },
                    label = { Text("Current PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it; error = null },
                    label = { Text("New 4-6 Digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null
                )
                if (error != null) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (onPinChanged(oldPin, newPin)) {
                        onDismiss()
                    } else {
                        error = "Current PIN is incorrect"
                    }
                }, 
                enabled = oldPin.isNotEmpty() && newPin.length >= 4
            ) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun StorageStatsDialog(stats: Map<String, Long>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage Usage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stats.isEmpty()) {
                    Text("Calculating...")
                } else {
                    stats.forEach { (type, size) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(type, style = MaterialTheme.typography.bodyMedium)
                            Text(formatSize(size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
