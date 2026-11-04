package com.vaultt.feature.documents

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.vaultt.core.util.FileShareHelper
import kotlinx.coroutines.launch
import com.vaultt.feature.vault.BatchActionBar
import com.vaultt.feature.vault.FoldersViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel(),
    onDeleteRequest: (android.content.IntentSender) -> Unit
) {
    var activePdfDocId by remember { mutableStateOf<String?>(null) }

    if (activePdfDocId != null) {
        PdfViewerScreen(
            documentId = activePdfDocId!!,
            onBack = { activePdfDocId = null },
            viewModel = viewModel
        )
        return
    }

    val documents by viewModel.documents.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val duplicates by viewModel.duplicateAlert.collectAsState()
    var selectedDoc by remember { mutableStateOf<VaultObject?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val securityManager = rememberSecurityManager()
    val foldersViewModel: FoldersViewModel = hiltViewModel()
    val folders by foldersViewModel.folders.collectAsState()
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMove by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            securityManager.endExternalActivity()
            if (uris.isNotEmpty()) {
                viewModel.importDocuments(uris)
            }
        }
    )

    if (duplicates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDuplicateAlert() },
            title = { Text("Duplicate Documents Detected") },
            text = {
                Text("${duplicates.size} document(s) already exist in your vault. Do you want to import them anyway?")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.continueImportingDuplicates() }) {
                    Text("Import Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearDuplicateAlert() }) {
                    Text("Skip")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                BatchActionBar(
                    count = selected.size,
                    onMove = { showMove = true },
                    onShare = {
                        val itemsToShare = documents.filter { it.id in selected }
                        coroutineScope.launch {
                            FileShareHelper.shareVaultObjects(context, viewModel.repository, itemsToShare, securityManager)
                        }
                    },
                    onRename = {
                        renameText = documents.find { it.id == selected.first() }?.name ?: ""
                        showRename = true
                    },
                    onTrash = { showTrash = true }
                )
            } else {
                TopAppBar(
                    title = { Text("Documents") },
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
                                if (doc.name.endsWith(".pdf", ignoreCase = true) || doc.mimeType == "application/pdf") {
                                    activePdfDocId = doc.id
                                } else {
                                    coroutineScope.launch {
                                        FileShareHelper.openVaultObject(context, viewModel.repository, doc, securityManager)
                                    }
                                }
                            },
                            onShare = {
                                coroutineScope.launch {
                                    FileShareHelper.shareVaultObjects(context, viewModel.repository, listOf(doc), securityManager)
                                }
                            },
                            onDelete = { selectedDoc = doc },
                            onLongClick = { selected = selected + doc.id }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                        )
                    }
                }
            }
            if (showMove) AlertDialog(
                onDismissRequest = { showMove = false },
                title = { Text("Move selected items") },
                text = {
                    Column {
                        folders.forEach { f ->
                            TextButton(onClick = {
                                foldersViewModel.moveAll(selected.toList(), f.id)
                                selected = emptySet()
                                showMove = false
                            }) { Text(f.name) }
                        }
                    }
                },
                confirmButton = { TextButton({ showMove = false }) { Text("Cancel") } }
            )
            if (showTrash) AlertDialog(
                onDismissRequest = { showTrash = false },
                title = { Text("Move to trash?") },
                text = { Text("Move ${selected.size} selected documents to trash?") },
                confirmButton = {
                    TextButton({
                        foldersViewModel.deleteObjects(selected.toList())
                        selected = emptySet()
                        showTrash = false
                    }) { Text("Move to trash", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton({ showTrash = false }) { Text("Cancel") } }
            )
            if (showRename) AlertDialog(
                onDismissRequest = { showRename = false },
                title = { Text("Rename item") },
                text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true) },
                confirmButton = {
                    TextButton({
                        selected.firstOrNull()?.let { viewModel.renameObject(it, renameText.trim()) }
                        selected = emptySet()
                        showRename = false
                    }) { Text("Save") }
                },
                dismissButton = { TextButton({ showRename = false }) { Text("Cancel") } }
            )

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

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun DocumentListItem(
    doc: VaultObject,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    val isPdf = doc.name.endsWith(".pdf", ignoreCase = true) || doc.mimeType == "application/pdf"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${doc.size / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                if (isPdf) {
                    Text(
                        text = "• In-Memory PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No documents in vault",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
