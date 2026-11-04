package com.vaultt.feature.videos

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.domain.model.VaultObject
import com.vaultt.core.security.rememberSecurityManager
import com.vaultt.feature.photos.PhotosViewModel
import com.vaultt.core.util.FileShareHelper
import com.vaultt.feature.vault.BatchActionBar
import com.vaultt.feature.vault.FoldersViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideosScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: VideosViewModel = hiltViewModel(),
    onDeleteRequest: (android.content.IntentSender) -> Unit = {}
) {
    val videos by viewModel.videos.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val duplicates by viewModel.duplicateAlert.collectAsState()
    val deletePromptUris by viewModel.deletePromptUris.collectAsState()
    val photosViewModel: PhotosViewModel = hiltViewModel()
    val securityManager = rememberSecurityManager()
    val context = LocalContext.current
    val foldersViewModel: FoldersViewModel = hiltViewModel()
    val folders by foldersViewModel.folders.collectAsState()
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMove by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            securityManager.endExternalActivity()
            if (uris.isNotEmpty()) {
                viewModel.importVideos(uris)
            }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.deleteIntentSender.collect {
            onDeleteRequest(it)
        // Handled at MainActivity level
        }
    }

    if (duplicates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDuplicateAlert() },
            title = { Text("Duplicates Detected") },
            text = { Text("${duplicates.size} videos are already in your vault. Skip them?") },
            confirmButton = {
                Button(onClick = { viewModel.clearDuplicateAlert() }) { Text("Skip") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.continueImportingDuplicates() }) { Text("Import Anyway") }
            }
        )
    }

    if (deletePromptUris.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDeletePrompt() },
            title = { Text("Delete from Public Gallery?") },
            text = {
                Text("${deletePromptUris.size} video(s) are safely encrypted into Vault T.\n\nDo you want to delete the original file(s) from your public gallery to hide them completely from other apps?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uris = deletePromptUris
                        viewModel.clearDeletePrompt()
                        viewModel.confirmDeleteOriginals(uris)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete from Gallery")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearDeletePrompt() }) {
                    Text("Keep in Gallery")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected.isEmpty()) "Videos" else "${selected.size} selected") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    if (selected.isNotEmpty()) BatchActionBar(
                        selected.size,
                        onMove = { showMove = true },
                        onShare = { scope.launch { FileShareHelper.shareVaultObjects(context, viewModel.repository, videos.filter { it.id in selected }, securityManager) } },
                        onRename = { renameText = videos.firstOrNull { it.id == selected.first() }?.name.orEmpty(); showRename = true },
                        onTrash = { showTrash = true }
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    securityManager.beginExternalActivity()
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import Video")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (videos.isEmpty() && !isImporting) {
                EmptyVideosState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(videos, key = { it.id }) { video ->
                        VideoGridItem(
                            video = video,
                            onVideoClick = { if (selected.isNotEmpty()) selected = if (video.id in selected) selected - video.id else selected + video.id else onVideoClick(video.id) },
                            onLongClick = { selected = selected + video.id },
                            loadThumbnail = { photosViewModel.getThumbnail(video.id) }
                        )
                    }
                }
            }
            if (showMove) AlertDialog(onDismissRequest = { showMove = false }, title = { Text("Move selected items") },
                text = { Column { folders.forEach { folder -> TextButton(onClick = { foldersViewModel.moveAll(selected.toList(), folder.id); selected = emptySet(); showMove = false }) { Text(folder.name) } } } },
                confirmButton = { TextButton(onClick = { showMove = false }) { Text("Cancel") } })
            if (showTrash) AlertDialog(onDismissRequest = { showTrash = false }, title = { Text("Move to trash?") },
                text = { Text("Move ${selected.size} selected videos to trash?") },
                confirmButton = { TextButton(onClick = { foldersViewModel.deleteObjects(selected.toList()); selected = emptySet(); showTrash = false }) { Text("Move to trash", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { showTrash = false }) { Text("Cancel") } })
            if (showRename) AlertDialog(onDismissRequest = { showRename = false }, title = { Text("Rename item") },
                text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true) },
                confirmButton = { TextButton(onClick = { selected.firstOrNull()?.let { viewModel.renameObject(it, renameText.trim()) }; selected = emptySet(); showRename = false }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } })

            if (isImporting) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Encrypting Videos...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun VideoGridItem(
    video: VaultObject,
    onVideoClick: () -> Unit,
    onLongClick: () -> Unit,
    loadThumbnail: suspend () -> ByteArray?
) {
    var thumbnailBytes by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(video.id) {
        thumbnailBytes = loadThumbnail()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onVideoClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        thumbnailBytes?.let { bytes ->
            val bitmap = remember(bytes) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = video.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = Color.White.copy(alpha = 0.7f)
        )
        
        Text(
            text = video.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.4f)).padding(2.dp).fillMaxWidth(),
            maxLines = 1
        )
    }
}

@Composable
fun EmptyVideosState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No videos yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = "Import your private videos into Vault T.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
