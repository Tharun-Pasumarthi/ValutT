package com.vaultt.feature.backup.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaultt.feature.backup.ExportViewModel
import com.vaultt.feature.backup.model.ExportPhase
import com.vaultt.feature.backup.model.ExportPreviewStats
import java.io.File

private enum class ExportStep {
    AUTH_CHECK,
    PREVIEW,
    ENTER_PASSWORD,
    PROGRESS,
    SUCCESS,
    FAILURE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportVaultFlowDialog(
    onDismiss: () -> Unit,
    viewModel: ExportViewModel,
    masterPin: String,
    targetItemIds: List<String>? = null
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(ExportStep.AUTH_CHECK) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    var includeTrash by remember { mutableStateOf(false) }
    var includeSettings by remember { mutableStateOf(true) }

    var exportPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordMismatch by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    val previewStats by viewModel.previewStats.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    // SAF Document Creator launcher for saving .vaultt file to user storage
    val saveSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && exportState.completedFile != null) {
            viewModel.saveArchiveToSaf(exportState.completedFile!!, uri) { success ->
                if (success) {
                    Toast.makeText(context, "Archive saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save archive", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(includeTrash) {
        viewModel.loadPreview(includeTrash, targetItemIds)
    }

    LaunchedEffect(exportState.phase) {
        when (exportState.phase) {
            ExportPhase.PREPARING,
            ExportPhase.ENCRYPTING_FILES,
            ExportPhase.CREATING_ARCHIVE,
            ExportPhase.VERIFYING_ARCHIVE,
            ExportPhase.SAVING_ARCHIVE -> {
                currentStep = ExportStep.PROGRESS
            }
            ExportPhase.COMPLETED -> {
                currentStep = ExportStep.SUCCESS
            }
            ExportPhase.FAILED -> {
                currentStep = ExportStep.FAILURE
            }
            ExportPhase.CANCELLED -> {
                onDismiss()
            }
            else -> Unit
        }
    }

    Dialog(
        onDismissRequest = {
            if (currentStep == ExportStep.PROGRESS) {
                showCancelConfirm = true
            } else {
                viewModel.resetState()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (currentStep) {
                    ExportStep.AUTH_CHECK -> {
                        AuthCheckStep(
                            enteredPin = enteredPin,
                            pinError = pinError,
                            onPinChange = {
                                enteredPin = it
                                pinError = false
                            },
                            onVerify = {
                                if (enteredPin == masterPin) {
                                    currentStep = ExportStep.PREVIEW
                                } else {
                                    pinError = true
                                }
                            },
                            onCancel = onDismiss
                        )
                    }
                    ExportStep.PREVIEW -> {
                        ExportPreviewStep(
                            stats = previewStats,
                            includeTrash = includeTrash,
                            includeSettings = includeSettings,
                            onIncludeTrashChange = { includeTrash = it },
                            onIncludeSettingsChange = { includeSettings = it },
                            onContinue = { currentStep = ExportStep.ENTER_PASSWORD },
                            onCancel = onDismiss
                        )
                    }
                    ExportStep.ENTER_PASSWORD -> {
                        EnterExportPasswordStep(
                            password = exportPassword,
                            confirmPassword = confirmPassword,
                            mismatch = passwordMismatch,
                            strength = viewModel.evaluatePasswordStrength(exportPassword),
                            onPasswordChange = {
                                exportPassword = it
                                passwordMismatch = false
                            },
                            onConfirmChange = {
                                confirmPassword = it
                                passwordMismatch = false
                            },
                            onStartExport = {
                                if (exportPassword.isEmpty()) {
                                    passwordMismatch = true
                                } else if (exportPassword != confirmPassword) {
                                    passwordMismatch = true
                                } else {
                                    currentStep = ExportStep.PROGRESS
                                    viewModel.startExport(
                                        password = exportPassword,
                                        includeTrash = includeTrash,
                                        includeSettings = includeSettings,
                                        targetItemIds = targetItemIds
                                    )
                                }
                            },
                            onBack = { currentStep = ExportStep.PREVIEW }
                        )
                    }
                    ExportStep.PROGRESS -> {
                        ExportProgressStep(
                            phase = exportState.phase,
                            processed = exportState.processedFiles,
                            total = exportState.totalFiles,
                            bytesProcessed = exportState.bytesProcessed,
                            onCancel = { showCancelConfirm = true }
                        )
                    }
                    ExportStep.SUCCESS -> {
                        ExportSuccessStep(
                            archiveFile = exportState.completedFile,
                            onShare = { exportState.completedFile?.let { viewModel.shareArchive(it) } },
                            onSaveToStorage = {
                                exportState.completedFile?.let { file ->
                                    saveSafLauncher.launch(file.name)
                                }
                            },
                            onDone = {
                                viewModel.resetState()
                                onDismiss()
                            }
                        )
                    }
                    ExportStep.FAILURE -> {
                        ExportFailureStep(
                            errorMessage = exportState.errorMessage ?: "Unknown error occurred.",
                            onRetry = {
                                currentStep = ExportStep.ENTER_PASSWORD
                            },
                            onClose = {
                                viewModel.resetState()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel Export?") },
            text = {
                Text("Export will stop safely. Your original Vault T data remains completely unchanged and secure.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        viewModel.cancelExport()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Continue")
                }
            }
        )
    }
}

@Composable
private fun AuthCheckStep(
    enteredPin: String,
    pinError: Boolean,
    onPinChange: (String) -> Unit,
    onVerify: () -> Unit,
    onCancel: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Security,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Authenticate Export",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Enter your Vault T Master PIN to authorize exporting an encrypted backup.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = enteredPin,
        onValueChange = { if (it.length <= 8) onPinChange(it) },
        label = { Text("Master PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        isError = pinError,
        supportingText = if (pinError) {
            { Text("Incorrect PIN. Please try again.", color = MaterialTheme.colorScheme.error) }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onVerify,
            enabled = enteredPin.length >= 4
        ) {
            Text("Verify & Continue")
        }
    }
}

@Composable
private fun ExportPreviewStep(
    stats: ExportPreviewStats?,
    includeTrash: Boolean,
    includeSettings: Boolean,
    onIncludeTrashChange: (Boolean) -> Unit,
    onIncludeSettingsChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Archive,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Export Entire Vault",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Create a portable, password-encrypted .vaultt archive.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Reassurance card
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Export creates a separate encrypted archive. Your original Vault T data will remain unchanged.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Stats breakdown
    if (stats != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            StatRow("Photos", "${stats.photoCount} files")
            StatRow("Videos", "${stats.videoCount} files")
            StatRow("Audio", "${stats.audioCount} files")
            StatRow("Documents", "${stats.documentCount} files")
            StatRow("Other Files", "${stats.otherCount} files")
            StatRow("Albums / Folders", "${stats.folderCount} folders")
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            StatRow("Total Vault Size", formatSize(stats.totalSizeBytes), bold = true)
            StatRow("Estimated Archive Size", formatSize(stats.estimatedArchiveBytes), bold = true)
        }
    } else {
        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Optional inclusion checkboxes
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = includeTrash, onCheckedChange = onIncludeTrashChange)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Include Trash contents (${stats?.trashCount ?: 0} items)",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = includeSettings, onCheckedChange = onIncludeSettingsChange)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Include Vault Settings & Preferences",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onContinue) {
            Text("Continue Export")
        }
    }
}

@Composable
private fun EnterExportPasswordStep(
    password: String,
    confirmPassword: String,
    mismatch: Boolean,
    strength: Triple<Int, String, String>,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onStartExport: () -> Unit,
    onBack: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Key,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Set Export Password",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "This password derives a 256-bit AES-GCM key with PBKDF2 (150,000 rounds) to encrypt your archive.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Export Password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    if (password.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { (strength.first + 1) / 5f },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
                color = when (strength.first) {
                    0, 1 -> MaterialTheme.colorScheme.error
                    2 -> Color(0xFFFFA000)
                    3 -> Color(0xFF388E3C)
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strength.second,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = confirmPassword,
        onValueChange = onConfirmChange,
        label = { Text("Confirm Export Password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = mismatch,
        supportingText = if (mismatch) {
            { Text("Passwords do not match or are empty.", color = MaterialTheme.colorScheme.error) }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Warning Card
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Crucial: The export password is required to restore this archive on any device. Vault T does not store this password. If lost, the archive cannot be recovered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onStartExport,
            enabled = password.length >= 6 && confirmPassword.isNotEmpty()
        ) {
            Text("Start Encrypted Export")
        }
    }
}

@Composable
private fun ExportProgressStep(
    phase: ExportPhase,
    processed: Int,
    total: Int,
    bytesProcessed: Long,
    onCancel: () -> Unit
) {
    val phaseTitle = when (phase) {
        ExportPhase.PREPARING -> "Preparing Export..."
        ExportPhase.ENCRYPTING_FILES -> "Encrypting Files..."
        ExportPhase.CREATING_ARCHIVE -> "Creating Archive..."
        ExportPhase.VERIFYING_ARCHIVE -> "Verifying Archive Integrity..."
        ExportPhase.SAVING_ARCHIVE -> "Saving Archive..."
        else -> "Exporting..."
    }

    Icon(
        imageVector = Icons.Default.LockClock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(54.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = phaseTitle,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (total > 0) {
        Text(
            text = "Processed $processed of $total files (${formatSize(bytesProcessed)})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    } else {
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onCancel,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
    ) {
        Text("Cancel Export")
    }
}

@Composable
private fun ExportSuccessStep(
    archiveFile: File?,
    onShare: () -> Unit,
    onSaveToStorage: () -> Unit,
    onDone: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = Color(0xFF2E7D32),
        modifier = Modifier.size(56.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Vault Export Completed Successfully",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Your original Vault T data remains securely stored on this device.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (archiveFile != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = archiveFile.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Encrypted Size: ${formatSize(archiveFile.length())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Encrypted Archive")
        }

        OutlinedButton(
            onClick = onSaveToStorage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.SaveAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save to Device / Storage")
        }

        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun ExportFailureStep(
    errorMessage: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Error,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(54.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Export Failed",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = errorMessage,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Your original Vault T data remains completely intact and untouched.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onClose) {
            Text("Close")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val unit = units[digitGroups.coerceIn(0, units.size - 1)]
    val size = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "%.1f %s".format(size, unit)
}

