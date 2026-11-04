package com.vaultt.feature.vault

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.core.security.rememberSecurityManager
import com.vaultt.domain.repository.VaultFolder
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    viewModel: FoldersViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var folderToRename by remember { mutableStateOf<VaultFolder?>(null) }
    var renameFolderName by remember { mutableStateOf("") }
    var folderToDelete by remember { mutableStateOf<VaultFolder?>(null) }
    val context = LocalContext.current
    val securityManager = rememberSecurityManager()
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create folder")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (folders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No folders yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap + below to create a separate folder to organize your photos, videos, and files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(folders, key = { it.id }) { folder ->
                        FolderCardItem(
                            folder = folder,
                            onFolderClick = { onAlbumClick(folder.id) },
                            onRename = {
                                folderToRename = folder
                                renameFolderName = folder.name
                            },
                            onDelete = { folderToDelete = folder },
                            onExportZip = {
                                scope.launch {
                                    isExporting = true
                                    val items = viewModel.objects(folder.id).firstOrNull() ?: emptyList()
                                    viewModel.exportFolderAsZip(context, folder.name, items, securityManager) {
                                        isExporting = false
                                    }
                                }
                            },
                            viewModel = viewModel
                        )
                    }
                }
            }

            if (isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Exporting folder as ZIP...")
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newFolderName.isNotBlank(),
                    onClick = {
                        val name = newFolderName.trim()
                        showCreate = false
                        newFolderName = ""
                        viewModel.create(name) { onAlbumClick(it) }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }

    folderToRename?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text("Rename Folder") },
            text = {
                OutlinedTextField(
                    value = renameFolderName,
                    onValueChange = { renameFolderName = it },
                    label = { Text("New Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = renameFolderName.isNotBlank(),
                    onClick = {
                        val newName = renameFolderName.trim()
                        viewModel.renameFolder(folder.id, newName)
                        folderToRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) { Text("Cancel") }
            }
        )
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Folder?") },
            text = {
                Text("Are you sure you want to delete \"${folder.name}\"? Items inside will remain in the vault as unfiled.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(folder.id)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun FolderCardItem(
    folder: VaultFolder,
    onFolderClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExportZip: () -> Unit,
    viewModel: FoldersViewModel
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var thumbnailBytes by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(folder.coverVaultObjectId) {
        val targetId = folder.coverVaultObjectId
        if (targetId != null) {
            val res = viewModel.getThumbnail(targetId)
            thumbnailBytes = res.getOrNull()
        } else {
            // Pick first item thumbnail if available
            val firstItem = viewModel.objects(folder.id).firstOrNull()?.firstOrNull()
            if (firstItem != null) {
                val res = viewModel.getThumbnail(firstItem.id)
                thumbnailBytes = res.getOrNull()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onFolderClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Folder Main Thumbnail / Cover
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val bmp = remember(thumbnailBytes) {
                    thumbnailBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Folder Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Folder Title & Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (folder.itemCount == 1) "1 item" else "${folder.itemCount} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Options Overflow Menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Folder Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename Folder") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export as ZIP") },
                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onExportZip()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
