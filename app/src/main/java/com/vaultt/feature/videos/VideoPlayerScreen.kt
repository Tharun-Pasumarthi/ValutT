package com.vaultt.feature.videos

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.vaultt.core.media.TinkDataSource
import com.vaultt.core.security.CryptoManager
import com.vaultt.core.storage.StorageManager
import com.vaultt.feature.photos.shareBytes
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import kotlin.OptIn

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    onBack: () -> Unit,
    viewModel: VideosViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val hiltEntryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            VideoPlayerEntryPoint::class.java
        )
    }
    
    val cryptoManager = hiltEntryPoint.cryptoManager()
    val storageManager = hiltEntryPoint.storageManager()
    
    val exoPlayer = remember {
        val dataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            TinkDataSource(
                cryptoManager = cryptoManager,
                file = storageManager.getObjectFile(videoId),
                associatedData = videoId.toByteArray()
            )
        }
        
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri("vault://$videoId"))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Video") },
            text = { Text("Move this video to trash?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteVideo(videoId)
                    showDeleteDialog = false
                    onBack()
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
            VideoPlayerTopBar(
                onBack = onBack, 
                onDelete = { showDeleteDialog = true },
                onShare = {
                    coroutineScope.launch {
                        val videoFile = storageManager.getObjectFile(videoId)
                        val out = ByteArrayOutputStream()
                        withContext(Dispatchers.IO) {
                            FileInputStream(videoFile).use { fis ->
                                cryptoManager.newDecryptingStream(fis, videoId.toByteArray()).use { dec ->
                                    dec.copyTo(out)
                                }
                            }
                        }
                        shareBytes(context, out.toByteArray(), "video.mp4")
                    }
                }
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            Text(
                text = "Vault Protected",
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlayerTopBar(onBack: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    TopAppBar(
        title = { Text("Video Player", color = Color.White) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        actions = {
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f))
    )
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface VideoPlayerEntryPoint {
    fun cryptoManager(): CryptoManager
    fun storageManager(): StorageManager
}
