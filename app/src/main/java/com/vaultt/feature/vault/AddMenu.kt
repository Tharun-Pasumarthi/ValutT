package com.vaultt.feature.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMenu(
    onDismiss: () -> Unit,
    onImportPhotos: (List<Uri>) -> Unit,
    onImportVideos: (List<Uri>) -> Unit,
    onImportAudio: (List<Uri>) -> Unit,
    onImportDocs: (List<Uri>) -> Unit,
    onTakePhoto: () -> Unit,
    onRecordAudio: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { if (it.isNotEmpty()) onImportPhotos(it) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { if (it.isNotEmpty()) onImportVideos(it) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { if (it.isNotEmpty()) onImportAudio(it) }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { if (it.isNotEmpty()) onImportDocs(it) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add to Vault",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleLarge
            )
            
            AddMenuItem("Import Photos", Icons.Default.Image) {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                onDismiss()
            }
            AddMenuItem("Import Videos", Icons.Default.Movie) {
                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                onDismiss()
            }
            AddMenuItem("Import Audio", Icons.Default.Mic) {
                audioPicker.launch("audio/*")
                onDismiss()
            }
            AddMenuItem("Import Documents", Icons.Default.Description) {
                docPicker.launch("*/*")
                onDismiss()
            }
            AddMenuItem("Take Photo", Icons.Default.CameraAlt) {
                onTakePhoto()
                onDismiss()
            }
            AddMenuItem("Record Audio", Icons.Default.Mic) {
                onRecordAudio()
                onDismiss()
            }
        }
    }
}

@Composable
private fun AddMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable { onClick() }
    )
}
