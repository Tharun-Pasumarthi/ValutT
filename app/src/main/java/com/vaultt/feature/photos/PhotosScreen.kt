package com.vaultt.feature.photos

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.domain.model.VaultObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    onBack: () -> Unit,
    onPhotoClick: (String) -> Unit,
    viewModel: PhotosViewModel = hiltViewModel()
) {
    val photos by viewModel.photos.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val duplicates by viewModel.duplicateAlert.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importPhotos(uris)
            }
        }
    )

    if (duplicates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDuplicateAlert() },
            title = { Text("Duplicate Files Detected") },
            text = { 
                Text("${duplicates.size} of the selected photos appear to be already in your vault. Do you want to skip these and only import new files, or import everything anyway?") 
            },
            confirmButton = {
                Button(onClick = { viewModel.clearDuplicateAlert() }) {
                    Text("Skip Duplicates")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.continueImportingDuplicates() }) {
                    Text("Import Anyway")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos") },
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
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import Photo")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (photos.isEmpty() && !isImporting) {
                EmptyPhotosState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoGridItem(
                            photo = photo,
                            onPhotoClick = { onPhotoClick(photo.id) },
                            loadThumbnail = { viewModel.getThumbnail(photo.id) }
                        )
                    }
                }
            }

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
                            Text("Processing and Encrypting...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoGridItem(
    photo: VaultObject,
    onPhotoClick: () -> Unit,
    loadThumbnail: suspend () -> ByteArray?
) {
    var thumbnailBytes by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(photo.id) {
        thumbnailBytes = loadThumbnail()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onPhotoClick)
    ) {
        thumbnailBytes?.let { bytes ->
            val bitmap = remember(bytes) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } ?: Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {}
    }
}

@Composable
fun EmptyPhotosState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No photos yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = "Import your private photos into Vault T.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
