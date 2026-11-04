package com.vaultt.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BatchActionBar(
    count: Int,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onTrash: () -> Unit,
    moveIcon: ImageVector = Icons.Default.Folder,
    moveContentDescription: String = "Move"
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$count selected", style = MaterialTheme.typography.titleMedium)
        Row {
            IconButton(onClick = onMove) { Icon(moveIcon, moveContentDescription) }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Rename") }
            IconButton(onClick = onTrash) { Icon(Icons.Default.Delete, "Move to trash") }
        }
    }
}
