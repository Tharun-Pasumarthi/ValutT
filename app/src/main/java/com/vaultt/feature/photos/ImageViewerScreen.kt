package com.vaultt.feature.photos

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.core.security.rememberSecurityManager
import com.vaultt.core.util.FileShareHelper
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    photoId: String,
    onBack: () -> Unit,
    viewModel: PhotosViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)
    val photos by viewModel.photos.collectAsState()
    val initialPage = maxOf(0, photos.indexOfFirst { it.id == photoId })
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val securityManager = rememberSecurityManager()

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to move this photo to trash?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePhoto(photos.getOrNull(pagerState.currentPage)?.id ?: photoId)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
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
                        val current = photos.getOrNull(pagerState.currentPage)
                        Column {
                            Text(current?.name ?: "Photo Viewer", maxLines = 1)
                            if (photos.size > 1) {
                                Text(
                                    text = "${pagerState.currentPage + 1} of ${photos.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photos.find { it.id == photoId }
                                currentPhoto?.let {
                                    FileShareHelper.shareVaultObjects(
                                        context,
                                        viewModel.repository,
                                        listOf(it),
                                        securityManager
                                    )
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val photo = photos.getOrNull(page) ?: return@HorizontalPager
                var imageBytes by remember(photo.id) { mutableStateOf<ByteArray?>(null) }
                LaunchedEffect(photo.id) {
                    imageBytes = viewModel.getFullImage(photo.id)
                }
                val bitmap = remember(imageBytes) {
                    imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                if (bitmap != null) {
                    ZoomableImage(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = photo.name,
                        modifier = Modifier.fillMaxSize(),
                        zoomSensitivity = viewModel.getZoomSensitivity(),
                        onTap = { showControls = !showControls }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            // Quick navigation arrows overlay when controls are visible and multiple photos exist
            if (showControls && photos.size > 1) {
                if (pagerState.currentPage > 0) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Photo",
                            tint = Color.White
                        )
                    }
                }

                if (pagerState.currentPage < photos.size - 1) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Photo",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
