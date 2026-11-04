package com.vaultt.feature.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun ZoomableImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    zoomSensitivity: Float = 1.0f,
    onTap: (() -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap?.invoke() },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    }
                )
            }
            .pointerInput(zoomSensitivity) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }

                        if (activePointers.size >= 2) {
                            // Multi-touch pinch: always process zoom
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val effectiveZoom = 1f + (zoomChange - 1f) * zoomSensitivity.coerceIn(0.4f, 2.5f)
                                val newScale = (scale * effectiveZoom).coerceIn(1f, 5f)

                                if (newScale > 1f) {
                                    val limitX = 1000f * (newScale - 1f)
                                    val limitY = 1500f * (newScale - 1f)
                                    offset = Offset(
                                        (offset.x + panChange.x).coerceIn(-limitX, limitX),
                                        (offset.y + panChange.y).coerceIn(-limitY, limitY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                                scale = newScale
                                event.changes.forEach { it.consume() }
                            }
                        } else if (scale > 1.05f) {
                            // Single pointer while zoomed in: pan inside the zoomed image
                            val panChange = event.calculatePan()
                            if (panChange != Offset.Zero) {
                                val limitX = 1000f * (scale - 1f)
                                val limitY = 1500f * (scale - 1f)
                                offset = Offset(
                                    (offset.x + panChange.x).coerceIn(-limitX, limitX),
                                    (offset.y + panChange.y).coerceIn(-limitY, limitY)
                                )
                                event.changes.forEach { it.consume() }
                            }
                        } else {
                            // At 1.0x scale with 1 pointer: DO NOT consume!
                            // This allows HorizontalPager to receive horizontal drags and swipe freely between images.
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit
        )

        // On-screen Zoom Badge and Quick Reset Controls
        if (scale > 1.05f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "%.1fx".format(scale),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}
