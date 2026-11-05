package com.vaultt.feature.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.hypot

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    ORIGINAL("Original", -1f),
    FREE("Free", null),
    SQUARE("1:1", 1.0f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f)
}

private enum class TouchHandle {
    NONE,
    CENTER,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropDialog(
    photoBytes: ByteArray,
    item: VaultObject,
    repository: VaultRepository,
    onDismiss: () -> Unit,
    onSuccess: (isReplaced: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember(photoBytes) {
        mutableStateOf(BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size))
    }
    var selectedRatio by remember { mutableStateOf(CropAspectRatio.ORIGINAL) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // cropRect stores normalized coordinates [0f..1f] relative to the displayed image bounds
    var cropRectNorm by remember { mutableStateOf(Rect(0.05f, 0.05f, 0.95f, 0.95f)) }
    var activeHandle by remember { mutableStateOf(TouchHandle.NONE) }
    var isSaving by remember { mutableStateOf(false) }

    fun rotateImage(degrees: Float = 90f) {
        sourceBitmap?.let { bmp ->
            val matrix = Matrix().apply { postRotate(degrees) }
            sourceBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            cropRectNorm = Rect(0.05f, 0.05f, 0.95f, 0.95f)
        }
    }

    fun flipImage(horizontal: Boolean) {
        sourceBitmap?.let { bmp ->
            val matrix = Matrix().apply {
                if (horizontal) {
                    postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
                } else {
                    postScale(1f, -1f, bmp.width / 2f, bmp.height / 2f)
                }
            }
            sourceBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            cropRectNorm = Rect(0.05f, 0.05f, 0.95f, 0.95f)
        }
    }

    fun resetAll() {
        sourceBitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
        cropRectNorm = Rect(0.05f, 0.05f, 0.95f, 0.95f)
        selectedRatio = CropAspectRatio.ORIGINAL
    }

    fun applyAspectRatio(ratio: CropAspectRatio, bmpWidth: Float, bmpHeight: Float) {
        selectedRatio = ratio
        val targetRatio = when (ratio) {
            CropAspectRatio.FREE -> null
            CropAspectRatio.ORIGINAL -> bmpWidth / bmpHeight
            CropAspectRatio.SQUARE -> 1.0f
            CropAspectRatio.RATIO_4_3 -> 4f / 3f
            CropAspectRatio.RATIO_16_9 -> 16f / 9f
            CropAspectRatio.RATIO_9_16 -> 9f / 16f
        }
        if (targetRatio == null) {
            // Freeform
            return
        }

        // Calculate a centered normalized rect that matches targetRatio
        val imgAspect = bmpWidth / bmpHeight
        val desiredNormWidth: Float
        val desiredNormHeight: Float

        if (targetRatio > imgAspect) {
            // Target is wider than image
            desiredNormWidth = 0.9f
            desiredNormHeight = (desiredNormWidth * imgAspect / targetRatio).coerceIn(0.1f, 0.9f)
        } else {
            // Target is taller than image
            desiredNormHeight = 0.9f
            desiredNormWidth = (desiredNormHeight * targetRatio / imgAspect).coerceIn(0.1f, 0.9f)
        }

        val left = ((1f - desiredNormWidth) / 2f).coerceIn(0f, 1f - desiredNormWidth)
        val top = ((1f - desiredNormHeight) / 2f).coerceIn(0f, 1f - desiredNormHeight)
        cropRectNorm = Rect(left, top, left + desiredNormWidth, top + desiredNormHeight)
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
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
                // Top Action Bar with accessible touch targets & full rotate/flip controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(24.dp))
                    }

                    Text(
                        text = "Crop Photo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { rotateImage(90f) },
                            enabled = !isSaving,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90°", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = { flipImage(horizontal = true) },
                            enabled = !isSaving,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Flip Horizontal", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = { flipImage(horizontal = false) },
                            enabled = !isSaving,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Flip Vertical", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = { resetAll() },
                            enabled = !isSaving,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset All", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }

                // Interactive Crop Canvas Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .onSizeChanged { containerSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = sourceBitmap
                    if (bmp != null && containerSize.width > 0 && containerSize.height > 0) {
                        val bmpAspect = bmp.width.toFloat() / bmp.height.toFloat()
                        val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()

                        val drawWidth = if (bmpAspect > containerAspect) {
                            containerSize.width.toFloat()
                        } else {
                            containerSize.height.toFloat() * bmpAspect
                        }
                        val drawHeight = if (bmpAspect > containerAspect) {
                            containerSize.width.toFloat() / bmpAspect
                        } else {
                            containerSize.height.toFloat()
                        }

                        val drawLeft = (containerSize.width - drawWidth) / 2f
                        val drawTop = (containerSize.height - drawHeight) / 2f

                        val bmpImage = remember(bmp) { bmp.asImageBitmap() }

                        // Touch target threshold (minimum 52dp hit radius)
                        val handleRadius = with(LocalDensity.current) { 52.dp.toPx() }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(bmp, drawWidth, drawHeight, drawLeft, drawTop, selectedRatio) {
                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            val cropL = drawLeft + (cropRectNorm.left * drawWidth)
                                            val cropT = drawTop + (cropRectNorm.top * drawHeight)
                                            val cropR = drawLeft + (cropRectNorm.right * drawWidth)
                                            val cropB = drawTop + (cropRectNorm.bottom * drawHeight)

                                            val distTL = hypot(startOffset.x - cropL, startOffset.y - cropT)
                                            val distTR = hypot(startOffset.x - cropR, startOffset.y - cropT)
                                            val distBL = hypot(startOffset.x - cropL, startOffset.y - cropB)
                                            val distBR = hypot(startOffset.x - cropR, startOffset.y - cropB)

                                            val distT = abs(startOffset.y - cropT)
                                            val distB = abs(startOffset.y - cropB)
                                            val distL = abs(startOffset.x - cropL)
                                            val distR = abs(startOffset.x - cropR)

                                            activeHandle = when {
                                                distTL < handleRadius -> TouchHandle.TOP_LEFT
                                                distTR < handleRadius -> TouchHandle.TOP_RIGHT
                                                distBL < handleRadius -> TouchHandle.BOTTOM_LEFT
                                                distBR < handleRadius -> TouchHandle.BOTTOM_RIGHT
                                                distT < handleRadius && startOffset.x in cropL..cropR -> TouchHandle.TOP
                                                distB < handleRadius && startOffset.x in cropL..cropR -> TouchHandle.BOTTOM
                                                distL < handleRadius && startOffset.y in cropT..cropB -> TouchHandle.LEFT
                                                distR < handleRadius && startOffset.y in cropT..cropB -> TouchHandle.RIGHT
                                                startOffset.x in cropL..cropR && startOffset.y in cropT..cropB -> TouchHandle.CENTER
                                                else -> TouchHandle.NONE
                                            }
                                        },
                                        onDragEnd = {
                                            activeHandle = TouchHandle.NONE
                                        },
                                        onDragCancel = {
                                            activeHandle = TouchHandle.NONE
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val normDx = dragAmount.x / drawWidth
                                            val normDy = dragAmount.y / drawHeight
                                            val minDim = 0.08f // Minimum crop dimension

                                            var l = cropRectNorm.left
                                            var t = cropRectNorm.top
                                            var r = cropRectNorm.right
                                            var b = cropRectNorm.bottom

                                            val fixedRatio = selectedRatio.ratio
                                            // Ratio adjusted to normalized space: ratioNorm = (targetRatio / imgAspect)
                                            val ratioNorm = if (fixedRatio != null) (fixedRatio / bmpAspect) else null

                                            when (activeHandle) {
                                                TouchHandle.CENTER -> {
                                                    val w = r - l
                                                    val h = b - t
                                                    val newL = (l + normDx).coerceIn(0f, 1f - w)
                                                    val newT = (t + normDy).coerceIn(0f, 1f - h)
                                                    l = newL
                                                    t = newT
                                                    r = newL + w
                                                    b = newT + h
                                                }
                                                TouchHandle.TOP_LEFT -> {
                                                    l = (l + normDx).coerceIn(0f, r - minDim)
                                                    t = (t + normDy).coerceIn(0f, b - minDim)
                                                    if (ratioNorm != null) {
                                                        val w = r - l
                                                        t = (b - (w / ratioNorm)).coerceIn(0f, b - minDim)
                                                    }
                                                }
                                                TouchHandle.TOP_RIGHT -> {
                                                    r = (r + normDx).coerceIn(l + minDim, 1f)
                                                    t = (t + normDy).coerceIn(0f, b - minDim)
                                                    if (ratioNorm != null) {
                                                        val w = r - l
                                                        t = (b - (w / ratioNorm)).coerceIn(0f, b - minDim)
                                                    }
                                                }
                                                TouchHandle.BOTTOM_LEFT -> {
                                                    l = (l + normDx).coerceIn(0f, r - minDim)
                                                    b = (b + normDy).coerceIn(t + minDim, 1f)
                                                    if (ratioNorm != null) {
                                                        val w = r - l
                                                        b = (t + (w / ratioNorm)).coerceIn(t + minDim, 1f)
                                                    }
                                                }
                                                TouchHandle.BOTTOM_RIGHT -> {
                                                    r = (r + normDx).coerceIn(l + minDim, 1f)
                                                    b = (b + normDy).coerceIn(t + minDim, 1f)
                                                    if (ratioNorm != null) {
                                                        val w = r - l
                                                        b = (t + (w / ratioNorm)).coerceIn(t + minDim, 1f)
                                                    }
                                                }
                                                TouchHandle.TOP -> {
                                                    t = (t + normDy).coerceIn(0f, b - minDim)
                                                    if (ratioNorm != null) {
                                                        val h = b - t
                                                        val center = (l + r) / 2f
                                                        val w = (h * ratioNorm).coerceIn(minDim, 1f)
                                                        l = (center - w / 2f).coerceIn(0f, 1f - w)
                                                        r = l + w
                                                    }
                                                }
                                                TouchHandle.BOTTOM -> {
                                                    b = (b + normDy).coerceIn(t + minDim, 1f)
                                                    if (ratioNorm != null) {
                                                        val h = b - t
                                                        val center = (l + r) / 2f
                                                        val w = (h * ratioNorm).coerceIn(minDim, 1f)
                                                        l = (center - w / 2f).coerceIn(0f, 1f - w)
                                                        r = l + w
                                                    }
                                                }
                                                TouchHandle.LEFT -> {
                                                    l = (l + normDx).coerceIn(0f, r - minDim)
                                                    if (ratioNorm != null) {
                                                        val w = r - l
                                                        val center = (t + b) / 2f
                                                        val h = (w / ratioNorm).coerceIn(minDim, 1f)
                                                        t = (center - h / 2f).coerceIn(0f, 1f - h)
                                                        b = t + h
                                                    }
                                                }
                                                TouchHandle.RIGHT -> {
                                                    r = (r + normDx).coerceIn(l + minDim, 1f)
                                                    if (ratioNorm != null) {
                                                        val w = r - l
                                                        val center = (t + b) / 2f
                                                        val h = (w / ratioNorm).coerceIn(minDim, 1f)
                                                        t = (center - h / 2f).coerceIn(0f, 1f - h)
                                                        b = t + h
                                                    }
                                                }
                                                TouchHandle.NONE -> {}
                                            }

                                            cropRectNorm = Rect(l, t, r, b)
                                        }
                                    )
                                }
                        ) {
                            // 1. Draw source image
                            drawImage(
                                image = bmpImage,
                                dstOffset = androidx.compose.ui.unit.IntOffset(drawLeft.toInt(), drawTop.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(drawWidth.toInt(), drawHeight.toInt())
                            )

                            // 2. Crop Window Screen Bounds
                            val cropLeft = drawLeft + (cropRectNorm.left * drawWidth)
                            val cropTop = drawTop + (cropRectNorm.top * drawHeight)
                            val cropW = (cropRectNorm.right - cropRectNorm.left) * drawWidth
                            val cropH = (cropRectNorm.bottom - cropRectNorm.top) * drawHeight
                            val cropRight = cropLeft + cropW
                            val cropBottom = cropTop + cropH

                            // 3. Darkened Scrim Overlay (Outside of crop rect)
                            val scrimColor = Color.Black.copy(alpha = 0.65f)
                            // Top Scrim
                            drawRect(scrimColor, Offset(drawLeft, drawTop), Size(drawWidth, cropTop - drawTop))
                            // Bottom Scrim
                            drawRect(scrimColor, Offset(drawLeft, cropBottom), Size(drawWidth, (drawTop + drawHeight) - cropBottom))
                            // Left Scrim
                            drawRect(scrimColor, Offset(drawLeft, cropTop), Size(cropLeft - drawLeft, cropH))
                            // Right Scrim
                            drawRect(scrimColor, Offset(cropRight, cropTop), Size((drawLeft + drawWidth) - cropRight, cropH))

                            // 4. Outer Crop Frame
                            drawRect(
                                color = Color.White.copy(alpha = 0.9f),
                                topLeft = Offset(cropLeft, cropTop),
                                size = Size(cropW, cropH),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // 5. Rule of Thirds Grid Lines
                            val gridColor = Color.White.copy(alpha = 0.45f)
                            val gridStroke = 1.dp.toPx()
                            // Vertical lines
                            drawLine(gridColor, Offset(cropLeft + cropW / 3f, cropTop), Offset(cropLeft + cropW / 3f, cropBottom), gridStroke)
                            drawLine(gridColor, Offset(cropLeft + 2f * cropW / 3f, cropTop), Offset(cropLeft + 2f * cropW / 3f, cropBottom), gridStroke)
                            // Horizontal lines
                            drawLine(gridColor, Offset(cropLeft, cropTop + cropH / 3f), Offset(cropRight, cropTop + cropH / 3f), gridStroke)
                            drawLine(gridColor, Offset(cropLeft, cropTop + 2f * cropH / 3f), Offset(cropRight, cropTop + 2f * cropH / 3f), gridStroke)

                            // 6. Bold Corner Brackets (uCrop / Google Photos style)
                            val bracketLen = 26.dp.toPx()
                            val bracketStroke = 5.dp.toPx()
                            val bracketColor = Color.White

                            // Top-Left
                            drawLine(bracketColor, Offset(cropLeft - 2, cropTop), Offset(cropLeft + bracketLen, cropTop), bracketStroke)
                            drawLine(bracketColor, Offset(cropLeft, cropTop - 2), Offset(cropLeft, cropTop + bracketLen), bracketStroke)

                            // Top-Right
                            drawLine(bracketColor, Offset(cropRight - bracketLen, cropTop), Offset(cropRight + 2, cropTop), bracketStroke)
                            drawLine(bracketColor, Offset(cropRight, cropTop - 2), Offset(cropRight, cropTop + bracketLen), bracketStroke)

                            // Bottom-Left
                            drawLine(bracketColor, Offset(cropLeft - 2, cropBottom), Offset(cropLeft + bracketLen, cropBottom), bracketStroke)
                            drawLine(bracketColor, Offset(cropLeft, cropBottom - bracketLen), Offset(cropLeft, cropBottom + 2), bracketStroke)

                            // Bottom-Right
                            drawLine(bracketColor, Offset(cropRight - bracketLen, cropBottom), Offset(cropRight + 2, cropBottom), bracketStroke)
                            drawLine(bracketColor, Offset(cropRight, cropBottom - bracketLen), Offset(cropRight, cropBottom + 2), bracketStroke)

                            // 7. Edge Middle Indicators
                            val midIndicatorLen = 14.dp.toPx()
                            // Top edge mid
                            drawLine(bracketColor, Offset(cropLeft + cropW / 2f - midIndicatorLen / 2f, cropTop), Offset(cropLeft + cropW / 2f + midIndicatorLen / 2f, cropTop), bracketStroke)
                            // Bottom edge mid
                            drawLine(bracketColor, Offset(cropLeft + cropW / 2f - midIndicatorLen / 2f, cropBottom), Offset(cropLeft + cropW / 2f + midIndicatorLen / 2f, cropBottom), bracketStroke)
                            // Left edge mid
                            drawLine(bracketColor, Offset(cropLeft, cropTop + cropH / 2f - midIndicatorLen / 2f), Offset(cropLeft, cropTop + cropH / 2f + midIndicatorLen / 2f), bracketStroke)
                            // Right edge mid
                            drawLine(bracketColor, Offset(cropRight, cropTop + cropH / 2f - midIndicatorLen / 2f), Offset(cropRight, cropTop + cropH / 2f + midIndicatorLen / 2f), bracketStroke)
                        }
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                // Resolution & Aspect Indicator
                sourceBitmap?.let { bmp ->
                    val cropPixelW = ((cropRectNorm.right - cropRectNorm.left) * bmp.width).toInt()
                    val cropPixelH = ((cropRectNorm.bottom - cropRectNorm.top) * bmp.height).toInt()
                    Text(
                        text = "$cropPixelW × $cropPixelH px • ${selectedRatio.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Aspect Ratio Selector Chips with smooth horizontal scroll and compact height
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    CropAspectRatio.entries.forEach { ratio ->
                        FilterChip(
                            selected = selectedRatio == ratio,
                            onClick = {
                                sourceBitmap?.let { bmp ->
                                    applyAspectRatio(ratio, bmp.width.toFloat(), bmp.height.toFloat())
                                }
                            },
                            label = {
                                Text(
                                    text = ratio.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedRatio == ratio) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.height(38.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = Color(0xFF252525),
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedRatio == ratio,
                                borderColor = Color(0xFF424242),
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.5.dp
                            )
                        )
                    }
                }

                // Shifted-Up Bottom Action Buttons (Directly in view, never going off screen)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val bmp = sourceBitmap ?: return@FilledTonalButton
                            isSaving = true
                            scope.launch {
                                val cropped = performCrop(bmp, cropRectNorm)
                                val result = saveCroppedPhoto(cropped, repository, item, replaceOriginal = false)
                                isSaving = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Saved as new photo copy", Toast.LENGTH_SHORT).show()
                                    onSuccess(false)
                                } else {
                                    Toast.makeText(context, "Failed to save cropped image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF263238),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.FileCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save as Copy",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = {
                            val bmp = sourceBitmap ?: return@Button
                            isSaving = true
                            scope.launch {
                                val cropped = performCrop(bmp, cropRectNorm)
                                val result = saveCroppedPhoto(cropped, repository, item, replaceOriginal = true)
                                isSaving = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Original photo replaced", Toast.LENGTH_SHORT).show()
                                    onSuccess(true)
                                } else {
                                    Toast.makeText(context, "Failed to update image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(20.dp))
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
}

private fun performCrop(bitmap: Bitmap, normRect: Rect): Bitmap {
    val left = (normRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top = (normRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val width = ((normRect.right - normRect.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - left)
    val height = ((normRect.bottom - normRect.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - top)

    return Bitmap.createBitmap(bitmap, left, top, width, height)
}

private suspend fun saveCroppedPhoto(
    croppedBitmap: Bitmap,
    repository: VaultRepository,
    originalItem: VaultObject,
    replaceOriginal: Boolean
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val stream = ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)
        val bytes = stream.toByteArray()

        if (replaceOriginal) {
            repository.importFromBytes(bytes, originalItem.name, VaultObjectType.PHOTO.name, originalItem.folderId).getOrThrow()
            repository.deleteObject(originalItem.id)
        } else {
            val extension = if (originalItem.name.contains('.')) originalItem.name.substring(originalItem.name.lastIndexOf('.')) else ".jpg"
            val baseName = if (originalItem.name.contains('.')) originalItem.name.substring(0, originalItem.name.lastIndexOf('.')) else originalItem.name
            val newName = "${baseName}_cropped$extension"
            repository.importFromBytes(bytes, newName, VaultObjectType.PHOTO.name, originalItem.folderId).getOrThrow()
        }
        Unit
    }
}
