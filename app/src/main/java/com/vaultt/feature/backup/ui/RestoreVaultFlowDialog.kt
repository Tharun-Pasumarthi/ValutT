package com.vaultt.feature.backup.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.vaultt.feature.backup.RestoreViewModel
import com.vaultt.feature.backup.model.ArchiveHeader
import com.vaultt.feature.backup.model.ArchiveManifest
import com.vaultt.feature.backup.model.ArchiveMetadata
import com.vaultt.feature.backup.model.RestorePhase

private enum class RestoreStep {
    SELECT_FILE,
    ENTER_PASSWORD,
    PREVIEW,
    CONFLICT_PROMPT,
    REPLACE_AUTH_CHECK,
    PROGRESS,
    SUCCESS,
    FAILURE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreVaultFlowDialog(
    onDismiss: () -> Unit,
    viewModel: RestoreViewModel,
    masterPin: String
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(RestoreStep.SELECT_FILE) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isVerifyingPassword by remember { mutableStateOf(false) }

    var replaceExisting by remember { mutableStateOf(false) }
    var replacePin by remember { mutableStateOf("") }
    var replacePinError by remember { mutableStateOf(false) }

    val header by viewModel.header.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val currentVaultCount by viewModel.currentVaultItemCount.collectAsState()

    // SAF Document picker for .vaultt archives
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            viewModel.inspectArchive(uri) { success, error ->
                if (success) {
                    currentStep = RestoreStep.ENTER_PASSWORD
                } else {
                    Toast.makeText(context, error ?: "Invalid .vaultt archive.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(restoreState.phase) {
        when (restoreState.phase) {
            RestorePhase.INSPECTING_ARCHIVE,
            RestorePhase.VERIFYING_PASSWORD,
            RestorePhase.PARSING_MANIFEST,
            RestorePhase.RESTORING_OBJECTS,
            RestorePhase.VERIFYING_INTEGRITY -> {
                currentStep = RestoreStep.PROGRESS
            }
            RestorePhase.COMPLETED -> {
                currentStep = RestoreStep.SUCCESS
            }
            RestorePhase.FAILED -> {
                currentStep = RestoreStep.FAILURE
            }
            RestorePhase.CANCELLED -> {
                onDismiss()
            }
            else -> Unit
        }
    }

    Dialog(
        onDismissRequest = {
            if (currentStep != RestoreStep.PROGRESS) {
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
                    RestoreStep.SELECT_FILE -> {
                        SelectArchiveStep(
                            onPickFile = {
                                openDocumentLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                            },
                            onCancel = onDismiss
                        )
                    }
                    RestoreStep.ENTER_PASSWORD -> {
                        EnterRestorePasswordStep(
                            header = header,
                            password = restorePassword,
                            errorMessage = passwordError,
                            isLoading = isVerifyingPassword,
                            onPasswordChange = {
                                restorePassword = it
                                passwordError = null
                            },
                            onVerify = {
                                val uri = selectedUri ?: return@EnterRestorePasswordStep
                                isVerifyingPassword = true
                                viewModel.verifyPasswordAndLoadPreview(uri, restorePassword) { success, error ->
                                    isVerifyingPassword = false
                                    if (success) {
                                        currentStep = RestoreStep.PREVIEW
                                    } else {
                                        passwordError = error ?: "Incorrect password or corrupted archive."
                                    }
                                }
                            },
                            onBack = { currentStep = RestoreStep.SELECT_FILE }
                        )
                    }
                    RestoreStep.PREVIEW -> {
                        RestorePreviewStep(
                            manifest = preview?.first,
                            metadata = preview?.second,
                            onContinue = {
                                if (currentVaultCount > 0) {
                                    currentStep = RestoreStep.CONFLICT_PROMPT
                                } else {
                                    // Empty vault, restore directly
                                    replaceExisting = false
                                    selectedUri?.let { uri ->
                                        currentStep = RestoreStep.PROGRESS
                                        viewModel.startRestore(uri, restorePassword, replaceExisting = false)
                                    }
                                }
                            },
                            onCancel = onDismiss
                        )
                    }
                    RestoreStep.CONFLICT_PROMPT -> {
                        ConflictResolutionStep(
                            existingItemCount = currentVaultCount,
                            onMerge = {
                                replaceExisting = false
                                selectedUri?.let { uri ->
                                    currentStep = RestoreStep.PROGRESS
                                    viewModel.startRestore(uri, restorePassword, replaceExisting = false)
                                }
                            },
                            onReplace = {
                                currentStep = RestoreStep.REPLACE_AUTH_CHECK
                            },
                            onCancel = onDismiss
                        )
                    }
                    RestoreStep.REPLACE_AUTH_CHECK -> {
                        ReplaceAuthCheckStep(
                            enteredPin = replacePin,
                            pinError = replacePinError,
                            onPinChange = {
                                replacePin = it
                                replacePinError = false
                            },
                            onConfirm = {
                                if (replacePin == masterPin) {
                                    replaceExisting = true
                                    selectedUri?.let { uri ->
                                        currentStep = RestoreStep.PROGRESS
                                        viewModel.startRestore(uri, restorePassword, replaceExisting = true)
                                    }
                                } else {
                                    replacePinError = true
                                }
                            },
                            onBack = { currentStep = RestoreStep.CONFLICT_PROMPT }
                        )
                    }
                    RestoreStep.PROGRESS -> {
                        RestoreProgressStep(
                            phase = restoreState.phase,
                            processed = restoreState.processedFiles,
                            total = restoreState.totalFiles,
                            onCancel = { viewModel.cancelRestore() }
                        )
                    }
                    RestoreStep.SUCCESS -> {
                        RestoreSuccessStep(
                            restoredCount = restoreState.restoredCount,
                            onDone = {
                                viewModel.resetState()
                                onDismiss()
                            }
                        )
                    }
                    RestoreStep.FAILURE -> {
                        RestoreFailureStep(
                            errorMessage = restoreState.errorMessage ?: "Restore failed.",
                            onRetry = { currentStep = RestoreStep.ENTER_PASSWORD },
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
}

@Composable
private fun SelectArchiveStep(
    onPickFile: () -> Unit,
    onCancel: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.CloudDownload,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(52.dp)
    )
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = "Restore Vault Archive",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Select a password-encrypted Vault T archive (.vaultt) from your device storage.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onPickFile,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Select .vaultt Archive File")
    }

    Spacer(modifier = Modifier.height(12.dp))
    TextButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Cancel")
    }
}

@Composable
private fun EnterRestorePasswordStep(
    header: ArchiveHeader?,
    password: String,
    errorMessage: String?,
    isLoading: Boolean,
    onPasswordChange: (String) -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Enter Archive Password",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "This archive is protected with ${header?.cipherAlgorithm ?: "AES-256-GCM"}. Enter the password set during export.",
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
        isError = errorMessage != null,
        supportingText = if (errorMessage != null) {
            { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(20.dp))

    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp))
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onVerify,
                enabled = password.isNotEmpty()
            ) {
                Text("Verify Password")
            }
        }
    }
}

@Composable
private fun RestorePreviewStep(
    manifest: ArchiveManifest?,
    metadata: ArchiveMetadata?,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Preview,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Restore Preview",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Review archive contents before restoring.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (manifest != null) {
        val photos = manifest.items.count { it.type == "PHOTO" }
        val videos = manifest.items.count { it.type == "VIDEO" }
        val audio = manifest.items.count { it.type == "AUDIO" }
        val docs = manifest.items.count { it.type == "DOCUMENT" }
        val others = manifest.items.count { it.type != "PHOTO" && it.type != "VIDEO" && it.type != "AUDIO" && it.type != "DOCUMENT" }
        val folderCount = metadata?.folders?.size ?: 0

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            StatRow("Total Files", "${manifest.totalItems} files", bold = true)
            StatRow("Total Archive Size", formatSize(manifest.totalSizeBytes), bold = true)
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            StatRow("Photos", "$photos files")
            StatRow("Videos", "$videos files")
            StatRow("Audio", "$audio files")
            StatRow("Documents", "$docs files")
            if (others > 0) StatRow("Other Files", "$others files")
            StatRow("Folders / Albums", "$folderCount folders")
            if (metadata?.settings != null) {
                StatRow("Vault Settings", "Included")
            }
        }
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
            Text("Proceed to Restore")
        }
    }
}

@Composable
private fun ConflictResolutionStep(
    existingItemCount: Int,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        tint = Color(0xFFFFA000),
        modifier = Modifier.size(52.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Vault T Already Contains Data",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Your current vault has $existingItemCount items stored. Choose how you want to restore:",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(20.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onMerge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AddCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Restore Into Existing Vault (Merge)")
        }

        OutlinedButton(
            onClick = onReplace,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Replace Existing Vault")
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ReplaceAuthCheckStep(
    enteredPin: String,
    pinError: Boolean,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Dangerous,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(54.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Confirm Replace Vault",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Replacing will permanently overwrite existing items in this vault. Enter your Master PIN to confirm.",
        style = MaterialTheme.typography.bodySmall,
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
            { Text("Incorrect PIN. Authorization failed.", color = MaterialTheme.colorScheme.error) }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

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
            onClick = onConfirm,
            enabled = enteredPin.length >= 4,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Confirm Replace")
        }
    }
}

@Composable
private fun RestoreProgressStep(
    phase: RestorePhase,
    processed: Int,
    total: Int,
    onCancel: () -> Unit
) {
    val phaseTitle = when (phase) {
        RestorePhase.INSPECTING_ARCHIVE -> "Inspecting Archive..."
        RestorePhase.VERIFYING_PASSWORD -> "Verifying Password..."
        RestorePhase.PARSING_MANIFEST -> "Reading Manifest..."
        RestorePhase.RESTORING_OBJECTS -> "Restoring & Encrypting Media..."
        RestorePhase.VERIFYING_INTEGRITY -> "Verifying File Hashes..."
        else -> "Restoring Vault..."
    }

    Icon(
        imageVector = Icons.Default.CloudSync,
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
            text = "Restoring file $processed of $total",
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
        Text("Cancel Restore")
    }
}

@Composable
private fun RestoreSuccessStep(
    restoredCount: Int,
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
        text = "Restoration Completed Successfully",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Restored $restoredCount files into Vault T with full hardware-backed encryption.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Done")
    }
}

@Composable
private fun RestoreFailureStep(
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
        text = "Restoration Failed",
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
        text = "Incomplete restored data was safely cleaned up. Your original vault is preserved.",
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

