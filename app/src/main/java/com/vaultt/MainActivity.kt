package com.vaultt

import android.app.Activity
import android.content.IntentSender
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vaultt.core.security.SecurityManager
import com.vaultt.feature.auth.LockScreen
import com.vaultt.feature.auth.SecurityViewModel
import com.vaultt.feature.photos.ImageViewerScreen
import com.vaultt.feature.photos.PhotosScreen
import com.vaultt.feature.photos.PhotosViewModel
import com.vaultt.feature.videos.VideoPlayerScreen
import com.vaultt.feature.videos.VideosScreen
import com.vaultt.feature.videos.VideosViewModel
import com.vaultt.feature.audio.AudioPlayerScreen
import com.vaultt.feature.audio.AudioScreen
import com.vaultt.feature.audio.AudioViewModel
import com.vaultt.feature.vault.VaultHomeScreen
import com.vaultt.feature.vault.HomeViewModel
import com.vaultt.feature.vault.TrashScreen
import com.vaultt.feature.vault.BrowserScreen
import com.vaultt.feature.settings.SettingsScreen
import com.vaultt.feature.documents.DocumentsScreen
import com.vaultt.feature.documents.DocumentsViewModel
import com.vaultt.feature.tools.CameraScreen
import com.vaultt.feature.tools.AudioRecorderScreen
import com.vaultt.feature.utilities.ClockScreen
import com.vaultt.feature.utilities.AlarmScreen
import com.vaultt.feature.utilities.TimerScreen
import com.vaultt.ui.theme.VaultTTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Screenshot Protection
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        enableEdgeToEdge()
        setContent {
            VaultTTheme {
                val securityViewModel: SecurityViewModel = hiltViewModel()
                val photosViewModel: PhotosViewModel = hiltViewModel()
                val videosViewModel: VideosViewModel = hiltViewModel()
                val audioViewModel: AudioViewModel = hiltViewModel()
                val docsViewModel: DocumentsViewModel = hiltViewModel()
                val homeViewModel: HomeViewModel = hiltViewModel()
                
                val isLocked by securityViewModel.isLocked.collectAsState()
                val navController = rememberNavController()
                
                // Launcher for MediaStore delete request (Required for Android 10+ to delete gallery items)
                val deleteLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    // Media deleted from public gallery
                }

                // Listen for delete requests from all ViewModels to hide from Gallery
                LaunchedEffect(photosViewModel, videosViewModel, docsViewModel) {
                    launch {
                        photosViewModel.deleteIntentSender.collectLatest { intentSender ->
                            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        }
                    }
                    launch {
                        videosViewModel.deleteIntentSender.collectLatest { intentSender ->
                            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        }
                    }
                    launch {
                        docsViewModel.deleteIntentSender.collectLatest { intentSender ->
                            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        }
                    }
                }

                // Periodic inactivity checker loop
                LaunchedEffect(Unit) {
                    while (isActive) {
                        securityManager.checkInactivityLock()
                        delay(1000)
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLocked) {
                        LockScreen(viewModel = securityViewModel)
                    } else {
                        NavHost(navController = navController, startDestination = "home") {
                            composable("home") {
                                VaultHomeScreen(
                                    onLock = { securityViewModel.lock() },
                                    onNavigateToPhotos = { navController.navigate("photos") },
                                    onNavigateToVideos = { navController.navigate("videos") },
                                    onNavigateToAudio = { navController.navigate("audio") },
                                    onNavigateToDocuments = { navController.navigate("documents") },
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onNavigateToTrash = { navController.navigate("trash") },
                                    onNavigateToBrowser = { navController.navigate("browser") },
                                    onNavigateToClock = { navController.navigate("clock") },
                                    onNavigateToAlarm = { navController.navigate("alarm") },
                                    onNavigateToTimer = { navController.navigate("timer") },
                                    onTakePhoto = { navController.navigate("camera") },
                                    onRecordAudio = { navController.navigate("audio_recorder") },
                                    onImportPhotos = { photosViewModel.importPhotos(it) },
                                    onImportVideos = { videosViewModel.importVideos(it) },
                                    onImportAudio = { audioViewModel.importAudio(it) },
                                    onImportDocs = { docsViewModel.importDocuments(it) },
                                    viewModel = homeViewModel
                                )
                            }
                            composable("photos") {
                                PhotosScreen(
                                    onBack = { navController.popBackStack() },
                                    onPhotoClick = { id -> navController.navigate("photo_viewer/$id") },
                                    viewModel = photosViewModel
                                )
                            }
                            composable(
                                "photo_viewer/{photoId}",
                                arguments = listOf(navArgument("photoId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val photoId = backStackEntry.arguments?.getString("photoId") ?: return@composable
                                ImageViewerScreen(
                                    photoId = photoId,
                                    onBack = { navController.popBackStack() },
                                    viewModel = photosViewModel
                                )
                            }
                            composable("videos") {
                                VideosScreen(
                                    onBack = { navController.popBackStack() },
                                    onVideoClick = { id -> navController.navigate("video_player/$id") },
                                    viewModel = videosViewModel
                                )
                            }
                            composable(
                                "video_player/{videoId}",
                                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
                                VideoPlayerScreen(
                                    videoId = videoId,
                                    onBack = { navController.popBackStack() },
                                    viewModel = videosViewModel
                                )
                            }
                            composable("audio") {
                                AudioScreen(
                                    onBack = { navController.popBackStack() },
                                    onAudioClick = { id -> navController.navigate("audio_player/$id") },
                                    viewModel = audioViewModel
                                )
                            }
                            composable(
                                "audio_player/{audioId}",
                                arguments = listOf(navArgument("audioId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val audioId = backStackEntry.arguments?.getString("audioId") ?: return@composable
                                AudioPlayerScreen(
                                    audioId = audioId,
                                    onBack = { navController.popBackStack() },
                                    viewModel = audioViewModel
                                )
                            }
                            composable("documents") {
                                DocumentsScreen(
                                    onBack = { navController.popBackStack() },
                                    viewModel = docsViewModel
                                )
                            }
                            composable("trash") {
                                TrashScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("browser") {
                                BrowserScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("camera") {
                                CameraScreen(
                                    onBack = { navController.popBackStack() },
                                    viewModel = photosViewModel
                                )
                            }
                            composable("audio_recorder") {
                                AudioRecorderScreen(
                                    onBack = { navController.popBackStack() },
                                    viewModel = audioViewModel
                                )
                            }
                            composable("clock") { ClockScreen(onBack = { navController.popBackStack() }) }
                            composable("alarm") { AlarmScreen(onBack = { navController.popBackStack() }) }
                            composable("timer") { TimerScreen(onBack = { navController.popBackStack() }) }
                        }
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        securityManager.updateActivity()
    }

    override fun onResume() {
        super.onResume()
        securityManager.onAppForegrounded()
    }
    
    override fun onPause() {
        super.onPause()
        securityManager.onAppBackgrounded()
    }
}
