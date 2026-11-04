package com.vaultt.feature.audio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.domain.model.VaultObject
import com.vaultt.core.security.rememberSecurityManager
import com.vaultt.core.util.FileShareHelper
import com.vaultt.feature.vault.BatchActionBar
import com.vaultt.feature.vault.FoldersViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudioScreen(
    onBack: () -> Unit,
    onAudioClick: (String) -> Unit,
    viewModel: AudioViewModel = hiltViewModel()
) {
    val audioFiles by viewModel.audioFiles.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val securityManager = rememberSecurityManager()
    val context = LocalContext.current
    val foldersViewModel: FoldersViewModel = hiltViewModel()
    val folders by foldersViewModel.folders.collectAsState()
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMove by remember { mutableStateOf(false) }; var showTrash by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }; var renameText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            securityManager.endExternalActivity()
            if (uris.isNotEmpty()) {
                viewModel.importAudio(uris)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected.isEmpty()) "Audio" else "${selected.size} selected") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    if (selected.isNotEmpty()) BatchActionBar(selected.size, { showMove = true },
                        { scope.launch { FileShareHelper.shareVaultObjects(context, viewModel.repository, audioFiles.filter { it.id in selected }, securityManager) } },
                        { renameText = audioFiles.firstOrNull { it.id == selected.first() }?.name.orEmpty(); showRename = true },
                        { showTrash = true })
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    securityManager.beginExternalActivity()
                    audioPickerLauncher.launch("audio/*")
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import Audio")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (audioFiles.isEmpty() && !isImporting) {
                EmptyAudioState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(audioFiles) { audio ->
                        AudioListItem(
                            audio = audio,
                            onClick = { if (selected.isNotEmpty()) selected = if (audio.id in selected) selected - audio.id else selected + audio.id else onAudioClick(audio.id) },
                            onLongClick = { selected = selected + audio.id }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                        )
                    }
                }
            }
            if (showMove) AlertDialog(onDismissRequest = { showMove = false }, title = { Text("Move selected items") }, text = { Column { folders.forEach { f -> TextButton(onClick = { foldersViewModel.moveAll(selected.toList(), f.id); selected = emptySet(); showMove = false }) { Text(f.name) } } } }, confirmButton = { TextButton({ showMove = false }) { Text("Cancel") } })
            if (showTrash) AlertDialog(onDismissRequest = { showTrash = false }, title = { Text("Move to trash?") }, text = { Text("Move ${selected.size} selected audio files to trash?") }, confirmButton = { TextButton({ foldersViewModel.deleteObjects(selected.toList()); selected = emptySet(); showTrash = false }) { Text("Move to trash", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ showTrash = false }) { Text("Cancel") } })
            if (showRename) AlertDialog(onDismissRequest = { showRename = false }, title = { Text("Rename item") }, text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true) }, confirmButton = { TextButton({ selected.firstOrNull()?.let { viewModel.renameAudio(it, renameText.trim()) }; selected = emptySet(); showRename = false }) { Text("Save") } }, dismissButton = { TextButton({ showRename = false }) { Text("Cancel") } })

            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AudioListItem(audio: VaultObject, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = audio.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = "${audio.size / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun EmptyAudioState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No audio files",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = "Import your private audio into Vault T.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
