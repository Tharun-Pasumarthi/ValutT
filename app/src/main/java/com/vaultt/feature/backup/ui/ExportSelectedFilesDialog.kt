package com.vaultt.feature.backup.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaultt.domain.model.VaultObject
import com.vaultt.domain.model.VaultObjectType
import com.vaultt.feature.backup.ExportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSelectedFilesDialog(
    onDismiss: () -> Unit,
    viewModel: ExportViewModel,
    onProceedWithSelected: (selectedIds: List<String>) -> Unit
) {
    val allItems by viewModel.allVaultObjects.collectAsState()
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectedCategory by remember { mutableStateOf<String?>("ALL") }

    val filteredItems = remember(allItems, selectedCategory) {
        if (selectedCategory == "ALL" || selectedCategory == null) {
            allItems
        } else {
            allItems.filter { it.type.name == selectedCategory }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Export Selected Files",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedIds.size} file(s) selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Warning Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Exporting these files creates copies outside Vault T. They may no longer be protected by Vault T.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == "ALL",
                        onClick = { selectedCategory = "ALL" },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedCategory == VaultObjectType.PHOTO.name,
                        onClick = { selectedCategory = VaultObjectType.PHOTO.name },
                        label = { Text("Photos") }
                    )
                    FilterChip(
                        selected = selectedCategory == VaultObjectType.VIDEO.name,
                        onClick = { selectedCategory = VaultObjectType.VIDEO.name },
                        label = { Text("Videos") }
                    )
                    FilterChip(
                        selected = selectedCategory == VaultObjectType.AUDIO.name,
                        onClick = { selectedCategory = VaultObjectType.AUDIO.name },
                        label = { Text("Audio") }
                    )
                    FilterChip(
                        selected = selectedCategory == VaultObjectType.DOCUMENT.name,
                        onClick = { selectedCategory = VaultObjectType.DOCUMENT.name },
                        label = { Text("Docs") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        selectedIds.clear()
                        selectedIds.addAll(filteredItems.map { it.id })
                    }) {
                        Text("Select All (${filteredItems.size})")
                    }
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = { selectedIds.clear() }) {
                            Text("Clear")
                        }
                    }
                }

                HorizontalDivider()

                // List of items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedIds.remove(item.id)
                                    else selectedIds.add(item.id)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedIds.add(item.id)
                                    else selectedIds.remove(item.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ItemThumbnail(item, viewModel)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatSize(item.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                onProceedWithSelected(selectedIds.toList())
                            }
                        },
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Text("Export ${selectedIds.size} Files")
                    }
                }
            }
        }
    }
}

private fun getItemIcon(type: VaultObjectType): ImageVector = when (type) {
    VaultObjectType.PHOTO -> Icons.Default.Image
    VaultObjectType.VIDEO -> Icons.Default.Videocam
    VaultObjectType.AUDIO -> Icons.Default.AudioFile
    VaultObjectType.DOCUMENT -> Icons.Default.Description
    VaultObjectType.OTHER -> Icons.Default.InsertDriveFile
}

@Composable
private fun ItemThumbnail(
    item: VaultObject,
    viewModel: ExportViewModel
) {
    if (item.type == VaultObjectType.PHOTO || item.type == VaultObjectType.VIDEO) {
        var thumbBytes by remember(item.id) { mutableStateOf<ByteArray?>(null) }
        LaunchedEffect(item.id) {
            thumbBytes = viewModel.getThumbnail(item.id).getOrNull()
        }
        val bmp = remember(thumbBytes) {
            thumbBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getItemIcon(item.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getItemIcon(item.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val unit = units[digitGroups.coerceIn(0, units.size - 1)]
    val size = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "%.1f %s".format(size, unit)
}

