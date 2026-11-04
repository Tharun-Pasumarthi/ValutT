package com.vaultt.feature.documents

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultt.core.security.rememberSecurityManager
import com.vaultt.core.util.FileShareHelper
import com.vaultt.feature.photos.ZoomableImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentId: String,
    onBack: () -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val securityManager = rememberSecurityManager()

    var docName by remember { mutableStateOf("PDF Document") }
    var pageCount by remember { mutableIntStateOf(0) }
    var renderedPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Options
    var isContinuousMode by remember { mutableStateOf(true) }
    var isInvertedDarkMode by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { renderedPages.size })

    val invertMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    DisposableEffect(documentId) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val bitmaps = mutableListOf<Bitmap>()

        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = viewModel.documents.value.find { it.id == documentId }
                if (doc != null) {
                    withContext(Dispatchers.Main) { docName = doc.name }
                }

                val bytes = viewModel.getData(documentId)
                if (bytes == null || bytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Unable to read document contents"
                        isLoading = false
                    }
                    return@launch
                }

                val tempFile = File.createTempFile("vault_pdf_", ".tmp", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(bytes) }
                pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                // Unlink file from directory immediately so bytes exist purely in file descriptor memory
                tempFile.delete()

                val pdfRenderer = PdfRenderer(pfd!!)
                renderer = pdfRenderer
                val totalPages = pdfRenderer.pageCount

                withContext(Dispatchers.Main) {
                    pageCount = totalPages
                }

                val displayMetrics = context.resources.displayMetrics
                val targetDensity = (displayMetrics.density * 1.5f).coerceAtLeast(1.5f)

                for (i in 0 until totalPages) {
                    val page = pdfRenderer.openPage(i)
                    val width = (page.width * targetDensity).toInt()
                    val height = (page.height * targetDensity).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmaps.add(bitmap)

                    if (i == 0 || i == totalPages - 1 || i % 3 == 0) {
                        val currentList = bitmaps.toList()
                        withContext(Dispatchers.Main) {
                            renderedPages = currentList
                            isLoading = false
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    renderedPages = bitmaps.toList()
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Error rendering PDF: ${e.localizedMessage ?: "Unknown error"}"
                    isLoading = false
                }
            }
        }

        onDispose {
            job.cancel()
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
            bitmaps.forEach {
                runCatching { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    val currentPageIndex = if (isContinuousMode) {
        (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount)
    } else {
        (pagerState.currentPage + 1).coerceAtMost(pageCount)
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
                        Column(modifier = Modifier.clickable { if (pageCount > 1) showJumpDialog = true }) {
                            Text(text = docName, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            if (pageCount > 0) {
                                Text(
                                    text = "Page $currentPageIndex of $pageCount (Tap to Jump)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Night / Dark mode invert toggle
                        IconButton(onClick = { isInvertedDarkMode = !isInvertedDarkMode }) {
                            Icon(
                                imageVector = if (isInvertedDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Reading Mode",
                                tint = if (isInvertedDarkMode) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // View mode toggle: Continuous vertical scroll vs Single page reader
                        IconButton(onClick = { isContinuousMode = !isContinuousMode }) {
                            Icon(
                                imageVector = if (isContinuousMode) Icons.Default.ViewAgenda else Icons.Default.ViewCarousel,
                                contentDescription = "Toggle Reading Mode"
                            )
                        }

                        // Jump to page dialog
                        if (pageCount > 1) {
                            IconButton(onClick = { showJumpDialog = true }) {
                                Icon(Icons.Default.FindInPage, contentDescription = "Jump to page")
                            }
                        }

                        // Share Document
                        IconButton(onClick = {
                            val doc = viewModel.documents.value.find { it.id == documentId }
                            if (doc != null) {
                                coroutineScope.launch {
                                    FileShareHelper.shareVaultObjects(
                                        context,
                                        viewModel.repository,
                                        listOf(doc),
                                        securityManager
                                    )
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Document")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isInvertedDarkMode) Color(0xFF121212) else Color(0xFF202124)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && renderedPages.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Rendering encrypted PDF in memory...", color = Color.White)
                }
            } else if (errorMessage != null && renderedPages.isEmpty()) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                if (isContinuousMode) {
                    // Continuous Vertical Scroll Mode
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(renderedPages) { index, bmp ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(containerColor = if (isInvertedDarkMode) Color.Black else Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        modifier = Modifier.fillMaxWidth(),
                                        colorFilter = if (isInvertedDarkMode) ColorFilter.colorMatrix(invertMatrix) else null
                                    )
                                    Text(
                                        text = "Page ${index + 1} of $pageCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isInvertedDarkMode) Color.LightGray else Color.DarkGray,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Single Page Swipe Mode with Pinch-to-Zoom
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val bmp = renderedPages.getOrNull(pageIndex)
                        if (bmp != null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                ZoomableImage(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${pageIndex + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    onTap = { showControls = !showControls }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJumpDialog && pageCount > 1) {
        var targetPageText by remember { mutableStateOf(currentPageIndex.toString()) }
        var sliderValue by remember { mutableFloatStateOf(currentPageIndex.toFloat()) }

        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Select page (1 to $pageCount):")
                    
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            targetPageText = it.toInt().toString()
                        },
                        valueRange = 1f..pageCount.toFloat(),
                        steps = (pageCount - 2).coerceAtLeast(0)
                    )

                    OutlinedTextField(
                        value = targetPageText,
                        onValueChange = { text ->
                            if (text.all { it.isDigit() }) {
                                targetPageText = text
                                val parsed = text.toIntOrNull()
                                if (parsed != null && parsed in 1..pageCount) {
                                    sliderValue = parsed.toFloat()
                                }
                            }
                        },
                        label = { Text("Page Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            targetPageText = "1"
                            sliderValue = 1f
                        }) { Text("First Page") }

                        TextButton(onClick = {
                            targetPageText = pageCount.toString()
                            sliderValue = pageCount.toFloat()
                        }) { Text("Last Page") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = targetPageText.toIntOrNull()?.coerceIn(1, pageCount) ?: 1
                    coroutineScope.launch {
                        if (isContinuousMode) {
                            listState.animateScrollToItem(target - 1)
                        } else {
                            pagerState.animateScrollToPage(target - 1)
                        }
                    }
                    showJumpDialog = false
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            }
        )
    }
}
