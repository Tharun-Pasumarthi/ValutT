package com.vaultt.feature.documents

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.domain.model.VaultObject
import com.vaultt.core.security.rememberSecurityManager
import com.vaultt.feature.photos.shareBytes
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel(),
    onDeleteRequest: (android.content.IntentSender) -> Unit
) {
    val documents by viewModel.documents.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val duplicates by viewModel.duplicateAlert.collectAsState()
    var selectedDoc by remember { mutableStateOf<VaultObject?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val securityManager = rememberSecurityManager()

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            securityManager.endExternalActivity()
            if (uris.isNotEmpty()) {
                viewModel.importDocuments(uris)
            }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.deleteIntentSender.collect {
            onDeleteRequest(it)
        }
    }

    if (duplicates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDuplicateAlert() },
            title = { Text("Duplicates Detected") },
            text = { Text("${duplicates.size} documents are already in your vault. Skip them?") },
            confirmButton = {
                Button(onClick = { viewModel.clearDuplicateAlert() }) { Text("Skip") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.continueImportingDuplicates() }) { Text("Import Anyway") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    securityManager.beginExternalActivity()
                    docPickerLauncher.launch("*/*")
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import Document")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (documents.isEmpty() && !isImporting) {
                EmptyDocumentsState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(documents, key = { it.id }) { doc ->
                        DocumentListItem(
                            doc = doc,
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.getDocumentData(doc.id).onSuccess { bytes ->
                                        openDocument(context, bytes, doc.name, doc.mimeType)
                                    }
                                }
                            },
                            onShare = {
                                coroutineScope.launch {
                                    viewModel.getDocumentData(doc.id).onSuccess { bytes ->
                                        shareBytes(context, bytes, doc.name)
                                    }
                                }
                            },
                            onDelete = { selectedDoc = doc }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                        )
                    }
                }
            }

            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (selectedDoc != null) {
                AlertDialog(
                    onDismissRequest = { selectedDoc = null },
                    title = { Text("Delete Document") },
                    text = { Text("Move '${selectedDoc?.name}' to trash?") },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedDoc?.let { viewModel.deleteDocument(it.id) }
                            selectedDoc = null
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedDoc = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

fun openDocument(context: Context, bytes: ByteArray, filename: String, mimeType: String?) {
    val tempFile = File(context.cacheDir, filename)
    FileOutputStream(tempFile).use { it.write(bytes) }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, 
        "${context.packageName}.fileprovider", 
        tempFile
    )
    
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType ?: context.contentResolver.getType(uri) ?: "*/*")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Open Document"))
}

@Composable
fun DocumentListItem(doc: VaultObject, onClick: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = "${doc.size / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun EmptyDocumentsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No documents",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = "Import your private documents into Vault T.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
