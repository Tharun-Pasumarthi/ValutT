package com.vaultt.feature.vault

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.core.security.SecurityManager
import com.vaultt.core.security.rememberSecurityManager
import com.vaultt.core.util.FileShareHelper
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.domain.repository.VaultRepository
import com.vaultt.feature.audio.AudioViewModel
import com.vaultt.feature.documents.DocumentsViewModel
import com.vaultt.feature.photos.ZoomableImage
import com.vaultt.feature.photos.PhotosViewModel
import com.vaultt.feature.videos.VideoPlayerScreen
import com.vaultt.feature.videos.VideosViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderScreen(
    folderId: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    photos: PhotosViewModel = hiltViewModel(),
    videos: VideosViewModel = hiltViewModel(),
    audio: AudioViewModel = hiltViewModel(),
    documents: DocumentsViewModel = hiltViewModel(),
    viewModel: FoldersViewModel = hiltViewModel(),
    onOpenMedia: (VaultObject) -> Unit
) {
    val folder = viewModel.folders.collectAsState().value.find { it.id == folderId }
    val objects by viewModel.objects(folderId).collectAsState(emptyList())
    val allObjects by viewModel.allObjects().collectAsState(emptyList())
    var showMove by remember { mutableStateOf(false) }
    val security = rememberSecurityManager()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedForDelete by remember { mutableStateOf<VaultObject?>(null) }
    var selectedForRename by remember { mutableStateOf<VaultObject?>(null) }
    var renameText by remember { mutableStateOf("") }
    var selectedMediaIndex by remember { mutableStateOf<Int?>(null) }
    var showDeleteFolder by remember { mutableStateOf(false) }
    var showBatchRemove by remember { mutableStateOf(false) }
    var showBatchTrash by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<((List<Uri>) -> Unit)?>(null) }
    var addSearch by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedExisting by remember { mutableStateOf<Set<String>>(emptySet()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        security.endExternalActivity(); if (it.isNotEmpty()) pending?.invoke(it)
    }
    fun pick(mime: String, action: (List<Uri>) -> Unit) { pending = action; security.beginExternalActivity(); picker.launch(mime) }
    fun toggleSelected(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }
    fun toggleExisting(id: String) {
        selectedExisting = if (id in selectedExisting) selectedExisting - id else selectedExisting + id
    }
    LaunchedEffect(folderId) {
        selected = emptySet()
        selectedExisting = emptySet()
    }

    val media = objects.filter {
        it.type == VaultObjectType.PHOTO || it.type == VaultObjectType.VIDEO
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(topBar = {
            Column {
                TopAppBar(title = { Text(if (selected.isEmpty()) folder?.name ?: "Folder" else "${selected.size} selected") }, navigationIcon = {
                    IconButton(onClick = {
                        if (selected.isNotEmpty()) selected = emptySet() else onBack()
                    }) { Icon(Icons.Default.ArrowBack, "Back") }
                }, actions = {
                    IconButton(onClick = { showDeleteFolder = true }) { Icon(Icons.Default.Delete, "Delete folder") }
                })
                if (selected.isNotEmpty()) {
                    BatchActionBar(
                        count = selected.size,
                        onMove = { showBatchRemove = true },
                        onShare = {
                            scope.launch {
                                FileShareHelper.shareVaultObjects(context, viewModel.repository, objects.filter { it.id in selected }, security)
                            }
                        },
                        onRename = {
                            selected.firstOrNull()?.let { id ->
                                objects.firstOrNull { it.id == id }?.let {
                                    renameText = it.name
                                    selectedForRename = it
                                }
                            }
                        },
                        onTrash = { showBatchTrash = true },
                        moveIcon = Icons.Default.RemoveCircleOutline,
                        moveContentDescription = "Remove from folder"
                    )
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Import into folder", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    Button(onClick = { pick("image/*") { photos.importPhotos(it, folderId = folderId) } }) { Text("Photos") }
                    Button(onClick = { pick("video/*") { videos.importVideos(it, folderId = folderId) } }) { Text("Videos") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pick("audio/*") { audio.importAudio(it, folderId) } }) { Text("Audio") }
                    Button(onClick = { pick("*/*") { documents.importDocuments(it, folderId = folderId) } }) { Text("Files") }
                }
                OutlinedButton(onClick = { selectedExisting = emptySet(); showMove = true }, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Add existing items")
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(objects, key = { it.id }) { item ->
                        val isSelected = item.id in selected
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        toggleSelected(item.id)
                                    } else if (item.type == VaultObjectType.DOCUMENT ||
                                        item.type == VaultObjectType.OTHER
                                    ) {
                                        scope.launch {
                                            FileShareHelper.openVaultObject(context, viewModel.repository, item, security)
                                        }
                                    } else if (item.type == VaultObjectType.AUDIO) {
                                        onOpenMedia(item)
                                    } else {
                                        val mediaItems = objects.filter {
                                            it.type == VaultObjectType.PHOTO || it.type == VaultObjectType.VIDEO
                                        }
                                        val mediaIndex = mediaItems.indexOfFirst { it.id == item.id }
                                        if (mediaIndex >= 0) {
                                            selectedMediaIndex = mediaIndex
                                        } else {
                                            onOpenMedia(item)
                                        }
                                    }
                                },
                                onLongClick = { toggleSelected(item.id) }
                            ).padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected.isNotEmpty()) {
                                Checkbox(checked = isSelected, onCheckedChange = { toggleSelected(item.id) })
                            }
                            FolderItemThumbnail(item, photos)
                            Column(Modifier.weight(1f)) {
                                Text(item.name)
                                if (isSelected) {
                                    Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (selected.isEmpty()) {
                                IconButton(onClick = {
                                    scope.launch {
                                        FileShareHelper.shareVaultObjects(context, viewModel.repository, listOf(item), security)
                                    }
                                }) { Icon(Icons.Default.Share, "Share") }
                                IconButton(onClick = {
                                    renameText = item.name
                                    selectedForRename = item
                                }
                                ) { Icon(Icons.Default.Edit, "Rename") }
                                IconButton(onClick = { viewModel.move(item.id, null) }) {
                                    Icon(Icons.Default.RemoveCircleOutline, "Remove from folder")
                                }
                                IconButton(onClick = { selectedForDelete = item }) {
                                    Icon(Icons.Default.Delete, "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showMove) AlertDialog(
            onDismissRequest = { showMove = false }, title = { Text("Add existing items") },
            text = {
                Column {
                    OutlinedTextField(addSearch, { addSearch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search items") }, singleLine = true)
                    val available = allObjects.filter { it.folderId != folderId && it.name.contains(addSearch, true) }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) { items(available, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { toggleExisting(item.id) }.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = item.id in selectedExisting, onCheckedChange = { toggleExisting(item.id) })
                            FolderItemThumbnail(item, photos)
                            Text(item.name, Modifier.weight(1f))
                            Text(if (item.id in selectedExisting) "Selected" else "Add")
                        }
                    } }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedExisting.isNotEmpty(),
                    onClick = {
                        viewModel.moveAll(selectedExisting.toList(), folderId)
                        selectedExisting = emptySet()
                        showMove = false
                    }
                ) { Text("Add selected") }
            },
            dismissButton = { TextButton(onClick = { showMove = false }) { Text("Cancel") } }
        )

        if (showBatchRemove) {
            AlertDialog(
                onDismissRequest = { showBatchRemove = false },
                title = { Text("Remove from folder?") },
                text = { Text("Remove ${selected.size} selected items from this folder? The files will remain in the vault.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.moveAll(selected.toList(), null)
                        selected = emptySet()
                        showBatchRemove = false
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { showBatchRemove = false }) { Text("Cancel") } }
            )
        }

        if (showBatchTrash) {
            AlertDialog(
                onDismissRequest = { showBatchTrash = false },
                title = { Text("Move to trash?") },
                text = { Text("Move ${selected.size} selected items to trash?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteObjects(selected.toList())
                        selected = emptySet()
                        showBatchTrash = false
                    }) { Text("Move to trash", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showBatchTrash = false }) { Text("Cancel") } }
            )
        }

        selectedForDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedForDelete = null },
                title = { Text("Move to trash?") },
                text = { Text(item.name) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteObject(item.id)
                        selectedForDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { selectedForDelete = null }) { Text("Cancel") } }
            )
        }

        selectedForRename?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedForRename = null },
                title = { Text("Rename item") },
                text = {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (renameText.isNotBlank()) viewModel.rename(item.id, renameText.trim())
                        selectedForRename = null
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { selectedForRename = null }) { Text("Cancel") } }
            )
        }

        if (showDeleteFolder) {
            AlertDialog(
                onDismissRequest = { showDeleteFolder = false },
                title = { Text("Delete folder?") },
                text = { Text("The folder will be deleted, but its files will remain in the vault.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.delete(folderId)
                        showDeleteFolder = false
                        onDelete()
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDeleteFolder = false }) { Text("Cancel") } }
            )
        }

        if (selectedMediaIndex != null && media.isNotEmpty()) {
            FolderMediaViewer(
                initialIndex = selectedMediaIndex!!.coerceIn(0, media.size - 1),
                mediaList = media,
                onClose = { selectedMediaIndex = null },
                photosViewModel = photos,
                videosViewModel = videos,
                repository = viewModel.repository,
                security = security,
                onDelete = { item ->
                    viewModel.deleteObject(item.id)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FolderMediaViewer(
    initialIndex: Int,
    mediaList: List<VaultObject>,
    onClose: () -> Unit,
    photosViewModel: PhotosViewModel,
    videosViewModel: VideosViewModel,
    repository: VaultRepository,
    security: SecurityManager,
    onDelete: (VaultObject) -> Unit
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safeInitial = initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { mediaList.size }
    )
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    val currentItem = mediaList.getOrNull(pagerState.currentPage.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0)))

    if (showDeleteDialog && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    if (currentItem.type == VaultObjectType.PHOTO) "Delete Photo" else "Delete Video"
                )
            },
            text = { Text("Move '${currentItem.name}' to trash?") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = currentItem
                    showDeleteDialog = false
                    if (mediaList.size <= 1) {
                        onClose()
                    }
                    onDelete(toDelete)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentItem?.name ?: "Media",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (mediaList.size > 1) {
                                Text(
                                    text = "${pagerState.currentPage + 1} of ${mediaList.size}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            currentItem?.let { item ->
                                scope.launch {
                                    FileShareHelper.shareVaultObjects(
                                        context,
                                        repository,
                                        listOf(item),
                                        security
                                    )
                                }
                            }
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        titleContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            val item = mediaList.getOrNull(page) ?: return@HorizontalPager
            if (item.type == VaultObjectType.PHOTO) {
                FolderPhotoViewerPage(
                    item = item,
                    photosViewModel = photosViewModel,
                    onTap = { showControls = !showControls }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    VideoPlayerScreen(
                        videoId = item.id,
                        onBack = onClose,
                        viewModel = videosViewModel,
                        showTopBar = false
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderPhotoViewerPage(
    item: VaultObject,
    photosViewModel: PhotosViewModel,
    onTap: () -> Unit
) {
    var imageBytes by remember(item.id) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(item.id) {
        imageBytes = photosViewModel.getFullImage(item.id)
    }

    val bitmap = remember(imageBytes) {
        imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    if (bitmap != null) {
        ZoomableImage(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            zoomSensitivity = photosViewModel.getZoomSensitivity(),
            onTap = onTap
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun FolderItemThumbnail(item: VaultObject, photos: PhotosViewModel) {
    var bytes by remember(item.id) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(item.id) {
        if (item.type == VaultObjectType.PHOTO || item.type == VaultObjectType.VIDEO) {
            bytes = photos.getThumbnail(item.id)
        }
    }
    val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = item.name,
            modifier = Modifier.size(48.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(
            imageVector = when (item.type) {
                VaultObjectType.PHOTO -> Icons.Default.Image
                VaultObjectType.VIDEO -> Icons.Default.Movie
                VaultObjectType.AUDIO -> Icons.Default.AudioFile
                else -> Icons.Default.Description
            },
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
    }
}
