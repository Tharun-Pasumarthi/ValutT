package com.vaultt.feature.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(onBack: () -> Unit, onAlbumClick: (String) -> Unit, viewModel: FoldersViewModel = hiltViewModel()) {
    val folders by viewModel.folders.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("Folders") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
    }) }, floatingActionButton = {
        FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "Create folder") }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders, key = { it.id }) { folder ->
                Card(Modifier.fillMaxWidth().clickable { onAlbumClick(folder.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(folder.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (folder.itemCount == 1) "1 item" else "${folder.itemCount} items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (folders.isEmpty()) item { Text("No folders yet. Create one to organize mixed content.") }
        }
    }
    if (showCreate) AlertDialog(
        onDismissRequest = { showCreate = false }, title = { Text("Create folder") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            viewModel.create(name.trim()) { onAlbumClick(it) }; showCreate = false
        }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
    )
}
