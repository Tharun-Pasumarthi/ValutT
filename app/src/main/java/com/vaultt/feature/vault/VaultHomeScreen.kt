package com.vaultt.feature.vault

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHomeScreen(
    onLock: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    onNavigateToVideos: () -> Unit,
    onNavigateToAudio: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToClock: () -> Unit,
    onNavigateToAlarm: () -> Unit,
    onNavigateToTimer: () -> Unit,
    onTakePhoto: () -> Unit,
    onRecordAudio: () -> Unit,
    onImportPhotos: (List<Uri>) -> Unit,
    onImportVideos: (List<Uri>) -> Unit,
    onImportAudio: (List<Uri>) -> Unit,
    onImportDocs: (List<Uri>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    var showAddMenu by remember { mutableStateOf(false) }
    val photoCount by viewModel.photoCount.collectAsState()
    val videoCount by viewModel.videoCount.collectAsState()
    val audioCount by viewModel.audioCount.collectAsState()
    val docCount by viewModel.docCount.collectAsState()
    val trashCount by viewModel.trashCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault T") },
                actions = {
                    IconButton(onClick = onNavigateToBrowser) {
                        Icon(Icons.Default.Language, contentDescription = "Private Browser")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock")
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
                onClick = { showAddMenu = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Vault Secure",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Encrypted • Offline • Protected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            item { SecurityStatusCard() }

            item {
                Text("Categories", style = MaterialTheme.typography.titleMedium)
                CategoryGrid(
                    photoCount = photoCount,
                    videoCount = videoCount,
                    audioCount = audioCount,
                    docCount = docCount,
                    onPhotos = onNavigateToPhotos,
                    onVideos = onNavigateToVideos,
                    onAudio = onNavigateToAudio,
                    onDocs = onNavigateToDocuments
                )
            }

            item {
                Text("Utilities", style = MaterialTheme.typography.titleMedium)
                UtilityRow(onNavigateToClock, onNavigateToAlarm, onNavigateToTimer)
            }
            
            item {
                CategoryCard(
                    title = "Trash",
                    count = trashCount,
                    icon = Icons.Default.Delete,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    onClick = onNavigateToTrash
                )
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (showAddMenu) {
            AddMenu(
                onDismiss = { showAddMenu = false },
                onImportPhotos = onImportPhotos,
                onImportVideos = onImportVideos,
                onImportAudio = onImportAudio,
                onImportDocs = onImportDocs,
                onTakePhoto = onTakePhoto,
                onRecordAudio = onRecordAudio
            )
        }
    }
}

@Composable
fun CategoryGrid(
    photoCount: Int,
    videoCount: Int,
    audioCount: Int,
    docCount: Int,
    onPhotos: () -> Unit,
    onVideos: () -> Unit,
    onAudio: () -> Unit,
    onDocs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryCard("Photos", photoCount, Icons.Default.Image, Modifier.weight(1f), onPhotos)
            CategoryCard("Videos", videoCount, Icons.Default.Videocam, Modifier.weight(1f), onVideos)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryCard("Audio", audioCount, Icons.Default.MusicNote, Modifier.weight(1f), onAudio)
            CategoryCard("Docs", docCount, Icons.Default.Description, Modifier.weight(1f), onDocs)
        }
    }
}

@Composable
fun UtilityRow(onClock: () -> Unit, onAlarm: () -> Unit, onTimer: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UtilityCard("Clock", Icons.Default.WatchLater, Modifier.weight(1f), onClock)
        UtilityCard("Alarm", Icons.Default.Schedule, Modifier.weight(1f), onAlarm)
        UtilityCard("Timer", Icons.Default.Timer, Modifier.weight(1f), onTimer)
    }
}

@Composable
fun CategoryCard(title: String, count: Int, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(100.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text("$count items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun UtilityCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(80.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SecurityStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SECURITY STATUS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            StatusItem("Encryption Enabled", true)
            StatusItem("Keystore Protected", true)
            StatusItem("Offline Mode", true)
            StatusItem("Screenshot Protection", true)
        }
    }
}

@Composable
fun StatusItem(label: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = if (active) Color(0xFF4CAF50) else Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
