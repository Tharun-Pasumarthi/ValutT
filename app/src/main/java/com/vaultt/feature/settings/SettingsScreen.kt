package com.vaultt.feature.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.core.security.IntruderLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showDestroyDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showZoomDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showIntruderLogsDialog by remember { mutableStateOf(false) }

    var isStealthEnabled by remember { mutableStateOf(viewModel.isStealthModeEnabled()) }
    var isDeleteOriginalsEnabled by remember { mutableStateOf(viewModel.isDeleteOriginalsEnabled()) }
    var isFlipToLockEnabled by remember { mutableStateOf(viewModel.isFlipToLockEnabled()) }
    var isIntruderEnabled by remember { mutableStateOf(viewModel.isIntruderCaptureEnabled()) }
    var zoomSensitivity by remember { mutableFloatStateOf(viewModel.getZoomSensitivity()) }
    var autoLockTimeout by remember { mutableIntStateOf(viewModel.getAutoLockTimeout()) }

    val stats by viewModel.storageStats.collectAsState()
    val intruderLogs by viewModel.intruderLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            SettingsSectionHeader("Authentication & Security")
            
            ListItem(
                headlineContent = { Text("Biometric Unlock") },
                supportingContent = { Text("Use fingerprint or face recognition") },
                leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                trailingContent = { 
                    Switch(
                        checked = viewModel.isBiometricEnabled(), 
                        onCheckedChange = { viewModel.setBiometricEnabled(it) }
                    ) 
                }
            )
            
            ListItem(
                headlineContent = { Text("Change Vault PIN") },
                supportingContent = { Text("Update your 4-6 digit access code") },
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.clickable { showPinDialog = true }
            )

            ListItem(
                headlineContent = { Text("Delete from Public Gallery") },
                supportingContent = {
                    Text(
                        if (isDeleteOriginalsEnabled) "Auto-delete: Removes original photos and videos from gallery after import"
                        else "Ask: Prompts whether to remove originals from gallery after import"
                    )
                },
                leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = isDeleteOriginalsEnabled,
                        onCheckedChange = {
                            isDeleteOriginalsEnabled = it
                            viewModel.setDeleteOriginalsEnabled(it)
                        }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader("Stealth & Disguise")

            ListItem(
                headlineContent = { Text("Calculator Disguise Mode") },
                supportingContent = {
                    Text(
                        if (isStealthEnabled) "Enabled: App opens as a calculator; enter PIN and press '=' to unlock"
                        else "Disabled: Standard Vault lock screen"
                    )
                },
                leadingContent = { Icon(Icons.Default.Calculate, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = isStealthEnabled,
                        onCheckedChange = {
                            isStealthEnabled = it
                            viewModel.setStealthModeEnabled(it)
                        }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader("Intruder Detection & Anti-Theft")

            ListItem(
                headlineContent = { Text("Intruder Selfie Capture") },
                supportingContent = { Text("Silently snap front camera photo on 3 failed PIN attempts") },
                leadingContent = { Icon(Icons.Default.CameraFront, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = isIntruderEnabled,
                        onCheckedChange = {
                            isIntruderEnabled = it
                            viewModel.setIntruderCaptureEnabled(it)
                        }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Break-in Logs & Selfies") },
                supportingContent = {
                    val count = intruderLogs.size
                    Text(if (count == 0) "No break-in attempts recorded" else "$count attempt(s) recorded with selfies")
                },
                leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                trailingContent = {
                    if (intruderLogs.isNotEmpty()) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text("${intruderLogs.size}", color = Color.White)
                        }
                    }
                },
                modifier = Modifier.clickable {
                    viewModel.loadIntruderLogs()
                    showIntruderLogsDialog = true
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader("Auto-Lock & Sensor Gestures")

            ListItem(
                headlineContent = { Text("Flip-to-Lock") },
                supportingContent = { Text("Instantly locks vault when phone is placed face down") },
                leadingContent = { Icon(Icons.Default.ScreenRotation, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = isFlipToLockEnabled,
                        onCheckedChange = {
                            isFlipToLockEnabled = it
                            viewModel.setFlipToLockEnabled(it)
                        }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Auto-Lock on Inactivity") },
                supportingContent = {
                    val label = when (autoLockTimeout) {
                        0 -> "Immediately when leaving"
                        30 -> "After 30 seconds"
                        60 -> "After 1 minute"
                        120 -> "After 2 minutes"
                        300 -> "After 5 minutes"
                        else -> "After $autoLockTimeout seconds"
                    }
                    Text(label)
                },
                leadingContent = { Icon(Icons.Default.Timer, contentDescription = null) },
                modifier = Modifier.clickable { showAutoLockDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader("Viewer & Sensitivity")

            ListItem(
                headlineContent = { Text("Pinch & Zoom Sensitivity") },
                supportingContent = {
                    val label = when {
                        zoomSensitivity <= 0.8f -> "Smooth / Controlled (0.7x)"
                        zoomSensitivity >= 1.3f -> "Fast / Responsive (1.4x)"
                        else -> "Standard / Balanced (1.0x)"
                    }
                    Text(label)
                },
                leadingContent = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
                modifier = Modifier.clickable { showZoomDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader("Storage & Data")
            
            ListItem(
                headlineContent = { Text("Storage Analyzer") },
                supportingContent = { Text("Breakdown of encrypted space usage") },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                modifier = Modifier.clickable { 
                    viewModel.loadStats()
                    showStatsDialog = true 
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            ListItem(
                headlineContent = { Text("Destroy Vault", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Permanently erase ALL files, records, and encryption keys") },
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

    if (showAutoLockDialog) {
        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            title = { Text("Auto-Lock Timeout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val options = listOf(
                        0 to "Immediately on app exit",
                        30 to "30 seconds",
                        60 to "1 minute",
                        120 to "2 minutes",
                        300 to "5 minutes"
                    )
                    options.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    autoLockTimeout = seconds
                                    viewModel.setAutoLockTimeout(seconds)
                                    showAutoLockDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = autoLockTimeout == seconds,
                                onClick = {
                                    autoLockTimeout = seconds
                                    viewModel.setAutoLockTimeout(seconds)
                                    showAutoLockDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoLockDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showZoomDialog) {
        AlertDialog(
            onDismissRequest = { showZoomDialog = false },
            title = { Text("Pinch & Zoom Sensitivity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val options = listOf(
                        0.7f to "Smooth / Controlled (0.7x)",
                        1.0f to "Standard / Balanced (1.0x)",
                        1.4f to "Fast / Responsive (1.4x)"
                    )
                    options.forEach { (value, title) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    zoomSensitivity = value
                                    viewModel.setZoomSensitivity(value)
                                    showZoomDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = Math.abs(zoomSensitivity - value) < 0.1f,
                                onClick = {
                                    zoomSensitivity = value
                                    viewModel.setZoomSensitivity(value)
                                    showZoomDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZoomDialog = false }) { Text("Close") }
            }
        )
    }

    if (showIntruderLogsDialog) {
        IntruderLogsDialog(
            logs = intruderLogs,
            onDismiss = { showIntruderLogsDialog = false },
            onClearAll = { viewModel.clearIntruderLogs() },
            onGetImage = { fileName -> viewModel.getIntruderImage(fileName) }
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
                    viewModel.destroyVault { }
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
fun IntruderLogsDialog(
    logs: List<IntruderLog>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onGetImage: suspend (String) -> ByteArray?
) {
    var previewImage by remember { mutableStateOf<ByteArray?>(null) }
    var previewTitle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }

    if (previewImage != null) {
        AlertDialog(
            onDismissRequest = { previewImage = null },
            title = { Text(previewTitle, style = MaterialTheme.typography.titleMedium) },
            text = {
                val bmp = remember(previewImage) {
                    BitmapFactory.decodeByteArray(previewImage, 0, previewImage!!.size)
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Intruder Selfie",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Text("Unable to render intruder selfie")
                }
            },
            confirmButton = {
                Button(onClick = { previewImage = null }) { Text("Close") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Intruder Break-in Logs") },
        text = {
            if (logs.isEmpty()) {
                Text("No break-in attempts recorded. Your vault is secure!")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (log.imageFileName != null) {
                                        scope.launch {
                                            previewTitle = "${log.attemptCount} Failed Attempts - ${dateFormat.format(Date(log.timestamp))}"
                                            previewImage = onGetImage(log.imageFileName)
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (log.imageFileName != null) Icons.Default.CameraAlt else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${log.attemptCount} Failed PIN Attempts",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = dateFormat.format(Date(log.timestamp)),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (log.imageFileName != null) {
                                        Text(
                                            text = "Tap to view captured selfie",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (logs.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear All Logs", color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
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
                        error = "Current PIN is incorrect or new PIN invalid"
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
