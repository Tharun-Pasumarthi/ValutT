package com.vaultt.feature.vault

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.feature.photos.ZoomableImage
import com.vaultt.feature.videos.VideoPlayerScreen
import com.vaultt.feature.videos.VideosViewModel

enum class TrashFilterType(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Docs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    videosViewModel: VideosViewModel = hiltViewModel()
) {
    val trashItems by viewModel.trashItems.collectAsState()
    var selectedFilter by remember { mutableStateOf(TrashFilterType.ALL) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showEmptyConfirmation by remember { mutableStateOf(false) }
    var selectedForPermanentDelete by remember { mutableStateOf<VaultObject?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchRestoreDialog by remember { mutableStateOf(false) }
    var selectedMediaIndex by remember { mutableStateOf<Int?>(null) }
    var selectedNonMediaItem by remember { mutableStateOf<VaultObject?>(null) }

    val filteredItems = remember(trashItems, selectedFilter) {
        when (selectedFilter) {
            TrashFilterType.ALL -> trashItems
            TrashFilterType.PHOTOS -> trashItems.filter { it.type == VaultObjectType.PHOTO }
            TrashFilterType.VIDEOS -> trashItems.filter { it.type == VaultObjectType.VIDEO }
            TrashFilterType.AUDIO -> trashItems.filter { it.type == VaultObjectType.AUDIO }
            TrashFilterType.DOCUMENTS -> trashItems.filter {
                it.type == VaultObjectType.DOCUMENT || it.type == VaultObjectType.OTHER
            }
        }
    }

    val mediaItems = remember(filteredItems) {
        filteredItems.filter { it.type == VaultObjectType.PHOTO || it.type == VaultObjectType.VIDEO }
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    val isSelectionMode = selectedIds.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text("${selectedIds.size} selected")
                        } else {
                            Column {
                                Text("Trash")
                                Text(
                                    text = if (trashItems.size == 1) "1 item" else "${trashItems.size} items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSelectionMode) {
                                selectedIds = emptySet()
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                imageVector = if (isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                                contentDescription = if (isSelectionMode) "Clear selection" else "Back"
                            )
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = {
                                selectedIds = if (selectedIds.size == filteredItems.size) {
                                    emptySet()
                                } else {
                                    filteredItems.map { it.id }.toSet()
                                }
                            }) {
                                Icon(
                                    imageVector = if (selectedIds.size == filteredItems.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                    contentDescription = "Select All"
                                )
                            }
                            IconButton(onClick = { showBatchRestoreDialog = true }) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showBatchDeleteDialog = true }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            if (trashItems.isNotEmpty()) {
                                IconButton(onClick = { showEmptyConfirmation = true }) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = "Empty Trash", tint = MaterialTheme.colorScheme.error)
                                }
                            }
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
            ) {
                // Category Filter Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrashFilterType.values().forEach { filterType ->
                        val count = when (filterType) {
                            TrashFilterType.ALL -> trashItems.size
                            TrashFilterType.PHOTOS -> trashItems.count { it.type == VaultObjectType.PHOTO }
                            TrashFilterType.VIDEOS -> trashItems.count { it.type == VaultObjectType.VIDEO }
                            TrashFilterType.AUDIO -> trashItems.count { it.type == VaultObjectType.AUDIO }
                            TrashFilterType.DOCUMENTS -> trashItems.count {
                                it.type == VaultObjectType.DOCUMENT || it.type == VaultObjectType.OTHER
                            }
                        }
                        FilterChip(
                            selected = selectedFilter == filterType,
                            onClick = { selectedFilter = filterType },
                            label = { Text("${filterType.label} ($count)") }
                        )
                    }
                }

                if (filteredItems.isEmpty()) {
                    EmptyTrashState(selectedFilter)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            val isSelected = item.id in selectedIds
                            TrashItemRow(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                viewModel = viewModel,
                                onClick = {
                                    if (isSelectionMode) {
                                        toggleSelection(item.id)
                                    } else if (item.type == VaultObjectType.PHOTO || item.type == VaultObjectType.VIDEO) {
                                        val mediaIdx = mediaItems.indexOfFirst { it.id == item.id }
                                        if (mediaIdx >= 0) {
                                            selectedMediaIndex = mediaIdx
                                        }
                                    } else {
                                        selectedNonMediaItem = item
                                    }
                                },
                                onLongClick = {
                                    toggleSelection(item.id)
                                },
                                onRestore = { viewModel.restoreObject(item.id) },
                                onDelete = { selectedForPermanentDelete = item }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // Empty Trash Dialog
        if (showEmptyConfirmation) {
            AlertDialog(
                onDismissRequest = { showEmptyConfirmation = false },
                title = { Text("Empty Trash?") },
                text = { Text("This will permanently delete all ${trashItems.size} items in the trash. This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.emptyTrash()
                        showEmptyConfirmation = false
                        selectedIds = emptySet()
                    }) {
                        Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyConfirmation = false }) { Text("Cancel") }
                }
            )
        }

        // Single Item Permanent Delete Dialog
        selectedForPermanentDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedForPermanentDelete = null },
                title = { Text("Delete Permanently?") },
                text = { Text("Are you sure you want to permanently erase '${item.name}'? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.permanentlyDelete(item.id)
                        selectedForPermanentDelete = null
                    }) {
                        Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedForPermanentDelete = null }) { Text("Cancel") }
                }
            )
        }

        // Batch Restore Dialog
        if (showBatchRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showBatchRestoreDialog = false },
                title = { Text("Restore Selected Items?") },
                text = { Text("Restore ${selectedIds.size} selected items back to your vault?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.restoreObjects(selectedIds.toList())
                        selectedIds = emptySet()
                        showBatchRestoreDialog = false
                    }) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchRestoreDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Batch Permanent Delete Dialog
        if (showBatchDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteDialog = false },
                title = { Text("Permanently Delete Selected?") },
                text = { Text("This will permanently erase ${selectedIds.size} selected items. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.permanentlyDeleteObjects(selectedIds.toList())
                        selectedIds = emptySet()
                        showBatchDeleteDialog = false
                    }) {
                        Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Non-media Item (Audio / Document) Preview Dialog
        selectedNonMediaItem?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedNonMediaItem = null },
                icon = {
                    Icon(
                        imageVector = if (item.type == VaultObjectType.AUDIO) Icons.Default.MusicNote else Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Type: ${item.type.name}", style = MaterialTheme.typography.bodyMedium)
                        Text("Size: ${formatSize(item.size)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Status: Trashed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.restoreObject(item.id)
                        selectedNonMediaItem = null
                    }) {
                        Text("Restore to Vault")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val toDelete = item
                        selectedNonMediaItem = null
                        selectedForPermanentDelete = toDelete
                    }) {
                        Text("Delete Forever", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }

        // Full-screen Swipable Trash Media Viewer & Navigator
        if (selectedMediaIndex != null && mediaItems.isNotEmpty()) {
            TrashMediaViewer(
                initialIndex = selectedMediaIndex!!.coerceIn(0, mediaItems.size - 1),
                mediaList = mediaItems,
                onClose = { selectedMediaIndex = null },
                viewModel = viewModel,
                videosViewModel = videosViewModel,
                onRestore = { item ->
                    viewModel.restoreObject(item.id)
                },
                onDeleteForever = { item ->
                    viewModel.permanentlyDelete(item.id)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashItemRow(
    item: VaultObject,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    viewModel: VaultViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        TrashItemThumbnail(item = item, viewModel = viewModel)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " • ${item.type.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (!isSelectionMode) {
            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Delete Forever",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TrashItemThumbnail(
    item: VaultObject,
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier.size(56.dp)
) {
    var thumbnailBytes by remember(item.id) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(item.id) {
        if (item.type == VaultObjectType.PHOTO || item.type == VaultObjectType.VIDEO) {
            thumbnailBytes = viewModel.getThumbnail(item.id)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = thumbnailBytes?.let {
            remember(it) { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (item.type == VaultObjectType.VIDEO) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = when (item.type) {
                    VaultObjectType.PHOTO -> Icons.Default.Image
                    VaultObjectType.VIDEO -> Icons.Default.Movie
                    VaultObjectType.AUDIO -> Icons.Default.MusicNote
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TrashMediaViewer(
    initialIndex: Int,
    mediaList: List<VaultObject>,
    onClose: () -> Unit,
    viewModel: VaultViewModel,
    videosViewModel: VideosViewModel,
    onRestore: (VaultObject) -> Unit,
    onDeleteForever: (VaultObject) -> Unit
) {
    BackHandler(onBack = onClose)
    val safeIndex = initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { mediaList.size }
    )
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    val currentItem = mediaList.getOrNull(pagerState.currentPage.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0)))

    if (showDeleteDialog && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Permanently Delete?") },
            text = { Text("Permanently delete '${currentItem.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = currentItem
                    showDeleteDialog = false
                    if (mediaList.size <= 1) {
                        onClose()
                    }
                    onDeleteForever(toDelete)
                }) {
                    Text("Delete Forever", color = MaterialTheme.colorScheme.error)
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
                                text = currentItem?.name ?: "Trash Preview",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "In Trash • ${pagerState.currentPage + 1} of ${mediaList.size}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.65f),
                        titleContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                currentItem?.let { item ->
                                    if (mediaList.size <= 1) {
                                        onClose()
                                    }
                                    onRestore(item)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore to Vault")
                        }

                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Forever")
                        }
                    }
                }
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
                TrashPhotoPage(
                    item = item,
                    viewModel = viewModel,
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
private fun TrashPhotoPage(
    item: VaultObject,
    viewModel: VaultViewModel,
    onTap: () -> Unit
) {
    var imageBytes by remember(item.id) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(item.id) {
        imageBytes = viewModel.getFullImage(item.id)
    }

    val bitmap = remember(imageBytes) {
        imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    if (bitmap != null) {
        ZoomableImage(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            zoomSensitivity = viewModel.getZoomSensitivity(),
            onTap = onTap
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
fun EmptyTrashState(selectedFilter: TrashFilterType = TrashFilterType.ALL) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (selectedFilter == TrashFilterType.ALL) "Trash is empty" else "No ${selectedFilter.label.lowercase()} in trash",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Items moved to trash will appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return "%.1f %s".format(bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
