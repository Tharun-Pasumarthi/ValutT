package com.vaultt.feature.videos

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.vaultt.core.media.TinkDataSource
import com.vaultt.core.security.CryptoManager
import com.vaultt.core.storage.StorageManager
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.domain.repository.VaultRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.abs

private enum class TrimHandle {
    NONE,
    START,
    END,
    SCRUB
}

@OptIn(UnstableApi::class)
@Composable
fun VideoTrimDialog(
    videoId: String,
    item: VaultObject,
    repository: VaultRepository,
    onDismiss: () -> Unit,
    onSuccess: (isReplaced: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isInitializing by remember { mutableStateOf(true) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    var isTrimming by remember { mutableStateOf(false) }
    var trimProgressText by remember { mutableStateOf("Preparing...") }
    var showReplaceConfirmDialog by remember { mutableStateOf(false) }

    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var filmstripSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf(TrimHandle.NONE) }

    val hiltEntryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            VideoPlayerEntryPoint::class.java
        )
    }
    val cryptoManager = hiltEntryPoint.cryptoManager()
    val storageManager = hiltEntryPoint.storageManager()

    // ExoPlayer initialized with encrypted TinkDataSource
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
                setMediaItem(
                    MediaItem.Builder()
                        .setUri("vault://$videoId")
                        .setMimeType("video/*")
                        .build()
                )
                repeatMode = Player.REPEAT_MODE_OFF
                prepare()
                playWhenReady = false
            }
    }

    // Monitor ExoPlayer position and enforce looping within [startMs, endMs]
    LaunchedEffect(exoPlayer, startMs, endMs) {
        while (true) {
            val pos = exoPlayer.currentPosition
            currentPositionMs = pos
            if (exoPlayer.isPlaying) {
                if (pos >= endMs || pos < startMs) {
                    exoPlayer.seekTo(startMs)
                }
            }
            delay(40)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && totalDurationMs <= 0L) {
                    val dur = exoPlayer.duration.coerceAtLeast(1000L)
                    totalDurationMs = dur
                    if (endMs == 0L) {
                        endMs = dur
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            thumbnails.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
        }
    }

    // Extract filmstrip thumbnail bitmaps
    LaunchedEffect(videoId) {
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "trim_film_${UUID.randomUUID()}.mp4")
            try {
                repository.getDecryptedStream(videoId).use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }

                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(tempFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val dur = durStr?.toLongOrNull() ?: 10000L
                totalDurationMs = dur
                startMs = 0L
                endMs = dur

                val frameCount = 8
                val stepUs = (dur * 1000L) / frameCount
                val frames = mutableListOf<Bitmap>()
                for (i in 0 until frameCount) {
                    val timeUs = (i * stepUs) + (stepUs / 2)
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val scaled = Bitmap.createScaledBitmap(frame, 120, 80, true)
                        frames.add(scaled)
                    }
                }
                retriever.release()
                withContext(Dispatchers.Main) {
                    thumbnails = frames
                    isInitializing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isInitializing = false
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    fun executeTrim(replaceOriginal: Boolean) {
        exoPlayer.pause()
        showReplaceConfirmDialog = false
        isTrimming = true
        trimProgressText = if (replaceOriginal) "Replacing original video & generating thumbnail..." else "Saving trimmed copy & generating thumbnail..."
        scope.launch {
            val result = trimAndSaveVideo(
                context = context,
                repository = repository,
                cryptoManager = cryptoManager,
                storageManager = storageManager,
                item = item,
                startMs = startMs,
                endMs = endMs,
                replaceOriginal = replaceOriginal
            )
            isTrimming = false
            if (result.isSuccess) {
                Toast.makeText(context, if (replaceOriginal) "Video trimmed and replaced" else "Trimmed video saved as new copy", Toast.LENGTH_SHORT).show()
                onSuccess(replaceOriginal)
            } else {
                Toast.makeText(context, "Trim failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isTrimming && !showReplaceConfirmDialog) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Header Bar with high-touch area buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isTrimming,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = "Trim Video",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = {
                            startMs = 0L
                            endMs = totalDurationMs
                            exoPlayer.seekTo(0L)
                        },
                        enabled = !isTrimming,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Range", tint = Color.White)
                    }
                }

                if (isInitializing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading video preview...", color = Color.White)
                        }
                    }
                } else if (isTrimming) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(trimProgressText, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Lossless remuxing & thumbnail creation in progress", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                } else {
                    // 1. Live Video Preview Player Container (Entire container is clickable for play/pause)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF141414))
                            .clickable {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.currentPosition !in startMs..endMs) {
                                        exoPlayer.seekTo(startMs)
                                    }
                                    exoPlayer.play()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Play/Pause touch overlay
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // 2. Timeline Range & Accessible Stepper Buttons for Sensitivity
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        // Start & End Stepper Row with high touchability
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Start Time Stepper Controls
                            Column(horizontalAlignment = Alignment.Start) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("START", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(formatTime(startMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD600))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            val newStart = (startMs - 500L).coerceAtLeast(0L)
                                            startMs = newStart
                                            exoPlayer.seekTo(newStart)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFF2C2C2C),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .height(42.dp)
                                            .defaultMinSize(minWidth = 52.dp)
                                    ) {
                                        Text("-0.5s", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val newStart = (startMs + 500L).coerceAtMost(endMs - 500L)
                                            startMs = newStart
                                            exoPlayer.seekTo(newStart)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFF2C2C2C),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .height(42.dp)
                                            .defaultMinSize(minWidth = 52.dp)
                                    ) {
                                        Text("+0.5s", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            // Center Trimmed Duration Badge
                            val trimmedDuration = (endMs - startMs).coerceAtLeast(0L)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("DURATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = formatTime(trimmedDuration),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // End Time Stepper Controls
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(formatTime(endMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD600))
                                    Text("END", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            val newEnd = (endMs - 500L).coerceAtLeast(startMs + 500L)
                                            endMs = newEnd
                                            exoPlayer.seekTo(newEnd)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFF2C2C2C),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .height(42.dp)
                                            .defaultMinSize(minWidth = 52.dp)
                                    ) {
                                        Text("-0.5s", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val newEnd = (endMs + 500L).coerceAtMost(totalDurationMs)
                                            endMs = newEnd
                                            exoPlayer.seekTo(newEnd)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFF2C2C2C),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .height(42.dp)
                                            .defaultMinSize(minWidth = 52.dp)
                                    ) {
                                        Text("+0.5s", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Filmstrip with Generous Touch Target Trimmer Handles
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .padding(horizontal = 16.dp)
                            .onSizeChanged { filmstripSize = it }
                    ) {
                        val trackWidth = filmstripSize.width.toFloat()
                        val trackHeight = filmstripSize.height.toFloat()

                        if (trackWidth > 0 && totalDurationMs > 0) {
                            val startNorm = (startMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                            val endNorm = (endMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                            val playNorm = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

                            val startX = startNorm * trackWidth
                            val endX = endNorm * trackWidth
                            val playX = playNorm * trackWidth
                            // Generous 56dp touch target for handles (minimum 56dp hit radius)
                            val handleHitWidth = with(LocalDensity.current) { 56.dp.toPx() }

                            // Underneath: 8 Filmstrip Thumbnail Bitmaps
                            if (thumbnails.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    thumbnails.forEach { bmp ->
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.DarkGray)
                                )
                            }

                            // Interactive Canvas Overlay
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(totalDurationMs, startMs, endMs) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val distStart = abs(offset.x - startX)
                                                val distEnd = abs(offset.x - endX)
                                                activeHandle = when {
                                                    distStart < handleHitWidth -> TrimHandle.START
                                                    distEnd < handleHitWidth -> TrimHandle.END
                                                    offset.x in startX..endX -> TrimHandle.SCRUB
                                                    else -> TrimHandle.NONE
                                                }
                                                if (activeHandle != TrimHandle.NONE) {
                                                    exoPlayer.pause()
                                                }
                                                if (activeHandle == TrimHandle.SCRUB) {
                                                    val seekNorm = (offset.x / trackWidth).coerceIn(0f, 1f)
                                                    val seekMs = (seekNorm * totalDurationMs).toLong()
                                                    exoPlayer.seekTo(seekMs)
                                                }
                                            },
                                            onDragEnd = { activeHandle = TrimHandle.NONE },
                                            onDragCancel = { activeHandle = TrimHandle.NONE },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                // Sensitivity adjustment: snap to 50ms increments to eliminate micro-jitter
                                                val rawDeltaMs = (dragAmount.x / trackWidth) * totalDurationMs
                                                val deltaMs = (rawDeltaMs / 50).toLong() * 50L
                                                val minTrimMs = 500L

                                                when (activeHandle) {
                                                    TrimHandle.START -> {
                                                        val newStart = (startMs + deltaMs).coerceIn(0L, endMs - minTrimMs)
                                                        startMs = newStart
                                                        exoPlayer.seekTo(newStart)
                                                    }
                                                    TrimHandle.END -> {
                                                        val newEnd = (endMs + deltaMs).coerceIn(startMs + minTrimMs, totalDurationMs)
                                                        endMs = newEnd
                                                        exoPlayer.seekTo(newEnd)
                                                    }
                                                    TrimHandle.SCRUB -> {
                                                        val targetX = (change.position.x / trackWidth).coerceIn(0f, 1f)
                                                        val seekMs = (targetX * totalDurationMs).toLong().coerceIn(startMs, endMs)
                                                        exoPlayer.seekTo(seekMs)
                                                    }
                                                    TrimHandle.NONE -> {}
                                                }
                                            }
                                        )
                                    }
                            ) {
                                // Dim regions outside trim window
                                val dimColor = Color.Black.copy(alpha = 0.72f)
                                if (startX > 0) {
                                    drawRect(dimColor, Offset.Zero, Size(startX, trackHeight))
                                }
                                if (endX < trackWidth) {
                                    drawRect(dimColor, Offset(endX, 0f), Size(trackWidth - endX, trackHeight))
                                }

                                // Vibrant Trim window border
                                val trimColor = Color(0xFFFFD600)
                                drawRect(
                                    color = trimColor,
                                    topLeft = Offset(startX, 0f),
                                    size = Size(endX - startX, trackHeight),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                )

                                // Left Handle Bar (20dp wide tactile pill)
                                val handleWidthPx = 20.dp.toPx()
                                drawRoundRect(
                                    color = trimColor,
                                    topLeft = Offset(startX - handleWidthPx / 2f, 0f),
                                    size = Size(handleWidthPx, trackHeight),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                                // Grip ridges
                                drawLine(Color.Black, Offset(startX - 2.dp.toPx(), trackHeight * 0.35f), Offset(startX - 2.dp.toPx(), trackHeight * 0.65f), strokeWidth = 2.dp.toPx())
                                drawLine(Color.Black, Offset(startX + 2.dp.toPx(), trackHeight * 0.35f), Offset(startX + 2.dp.toPx(), trackHeight * 0.65f), strokeWidth = 2.dp.toPx())

                                // Right Handle Bar (20dp wide tactile pill)
                                drawRoundRect(
                                    color = trimColor,
                                    topLeft = Offset(endX - handleWidthPx / 2f, 0f),
                                    size = Size(handleWidthPx, trackHeight),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                                drawLine(Color.Black, Offset(endX - 2.dp.toPx(), trackHeight * 0.35f), Offset(endX - 2.dp.toPx(), trackHeight * 0.65f), strokeWidth = 2.dp.toPx())
                                drawLine(Color.Black, Offset(endX + 2.dp.toPx(), trackHeight * 0.35f), Offset(endX + 2.dp.toPx(), trackHeight * 0.65f), strokeWidth = 2.dp.toPx())

                                // Playhead needle line
                                if (playX in startX..endX) {
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(playX, -4.dp.toPx()),
                                        end = Offset(playX, trackHeight + 4.dp.toPx()),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 6.dp.toPx(),
                                        center = Offset(playX, -4.dp.toPx())
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 4. Shifted-Up Bottom Action Buttons (Directly in view, easily touchable, never going off screen)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                exoPlayer.pause()
                                executeTrim(replaceOriginal = false)
                            },
                            enabled = endMs > startMs + 300L && !isTrimming,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.FileCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save as Copy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                exoPlayer.pause()
                                showReplaceConfirmDialog = true
                            },
                            enabled = endMs > startMs + 300L && !isTrimming,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Replace Original",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Compact Confirmation Dialog when replacing original video
    if (showReplaceConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Replace Original Video?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "The original video will be replaced with the trimmed segment (${formatTime(endMs - startMs)}). This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReplaceConfirmDialog = false
                        executeTrim(replaceOriginal = true)
                    }
                ) {
                    Text(
                        text = "Replace",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private suspend fun trimAndSaveVideo(
    context: Context,
    repository: VaultRepository,
    cryptoManager: CryptoManager,
    storageManager: StorageManager,
    item: VaultObject,
    startMs: Long,
    endMs: Long,
    replaceOriginal: Boolean
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val tempInputFile = File(context.cacheDir, "trim_in_${UUID.randomUUID()}.mp4")
        val tempOutputFile = File(context.cacheDir, "trim_out_${UUID.randomUUID()}.mp4")

        try {
            // Stage decrypted input
            repository.getDecryptedStream(item.id).use { input ->
                FileOutputStream(tempInputFile).use { output -> input.copyTo(output) }
            }

            val extractor = MediaExtractor()
            extractor.setDataSource(tempInputFile.absolutePath)

            val trackCount = extractor.trackCount
            val muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val indexMap = mutableMapOf<Int, Int>()

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val muxerTrackIndex = muxer.addTrack(format)
                    indexMap[i] = muxerTrackIndex
                }
            }

            muxer.start()

            val bufferSize = 1024 * 1024
            val dstBuf = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = extractor.sampleTime
                if (bufferInfo.presentationTimeUs > endUs) break

                val sampleFlags = extractor.sampleFlags
                bufferInfo.flags = sampleFlags

                val trackIndex = extractor.sampleTrackIndex
                val muxerTrack = indexMap[trackIndex]
                if (muxerTrack != null && bufferInfo.presentationTimeUs >= startUs) {
                    muxer.writeSampleData(muxerTrack, dstBuf, bufferInfo)
                }

                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            // Extract accurate video thumbnail from the trimmed output
            var thumbnailBytes: ByteArray? = null
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(tempOutputFile.absolutePath)
                val frameBitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(1000000)
                retriever.release()

                if (frameBitmap != null) {
                    val stream = ByteArrayOutputStream()
                    frameBitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                    thumbnailBytes = stream.toByteArray()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Encrypt and import trimmed video into vault
            val newId: String
            FileInputStream(tempOutputFile).use { trimmedInput ->
                val trimmedBytes = trimmedInput.readBytes()
                if (replaceOriginal) {
                    newId = repository.importFromBytes(trimmedBytes, item.name, VaultObjectType.VIDEO.name, item.folderId).getOrThrow()
                    repository.deleteObject(item.id)
                } else {
                    val extension = if (item.name.contains('.')) item.name.substring(item.name.lastIndexOf('.')) else ".mp4"
                    val baseName = if (item.name.contains('.')) item.name.substring(0, item.name.lastIndexOf('.')) else item.name
                    val newName = "${baseName}_trimmed$extension"
                    newId = repository.importFromBytes(trimmedBytes, newName, VaultObjectType.VIDEO.name, item.folderId).getOrThrow()
                }
            }

            // Place thumbnail directly for the newly created video object
            if (thumbnailBytes != null && thumbnailBytes.isNotEmpty()) {
                val encryptedThumb = cryptoManager.encrypt(thumbnailBytes, (newId + "_thumb").toByteArray())
                storageManager.getThumbnailFile(newId).writeBytes(encryptedThumb)
            }

            Unit
        } finally {
            tempInputFile.delete()
            tempOutputFile.delete()
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
