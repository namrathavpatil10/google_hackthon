package com.sentinedge.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.sentinedge.app.ml.DetectionResult
import com.sentinedge.app.service.LiveProtectionService
import com.sentinedge.app.source.LiveCameraSource
import com.sentinedge.app.ui.*
import com.sentinedge.app.ui.theme.SentinEdgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var isBackgroundServiceRunning by mutableStateOf(false)

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, LiveProtectionService::class.java).apply {
                putExtra("projection_data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            isBackgroundServiceRunning = true
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    private fun toggleBackgroundProtection(active: Boolean) {
        if (active) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                return
            }
            
            val mpManager = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } else {
            stopService(Intent(this, LiveProtectionService::class.java))
            isBackgroundServiceRunning = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()

        setContent {
            SentinEdgeTheme {
                val state by viewModel.state.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                var cameraSource by remember { mutableStateOf<LiveCameraSource?>(null) }

                when (val s = state) {
                    is AnalysisState.Idle -> HomeScreen(
                        onMediaSelected = { uri -> 
                            val mimeType = contentResolver.getType(uri)
                            if (mimeType?.startsWith("video") == true) {
                                viewModel.analyzeVideo(uri)
                            } else {
                                viewModel.analyzeImage(uri)
                            }
                        },
                        onLiveCamera = {
                            cameraSource = viewModel.startLiveCamera()
                        },
                        onToggleBackgroundProtection = { toggleBackgroundProtection(it) },
                        isBackgroundProtectionActive = isBackgroundServiceRunning,
                        downloadProgress = downloadProgress
                    )

                    is AnalysisState.Loading -> {
                        val src = cameraSource
                        if (src != null) {
                            CameraScreen(state = s, cameraSource = src, onStop = {
                                cameraSource = null; viewModel.stopAnalysis()
                            })
                        } else {
                            // Unified Loading Screen for Images and Videos
                            MainScreen(state = s, onMediaSelected = { uri -> 
                                val mimeType = contentResolver.getType(uri)
                                if (mimeType?.startsWith("video") == true) {
                                    viewModel.analyzeVideo(uri)
                                } else {
                                    viewModel.analyzeImage(uri)
                                }
                            }, onStop = viewModel::stopAnalysis)
                        }
                    }

                    is AnalysisState.Running -> {
                        if (s.sourceType == SourceType.CAMERA) {
                            val src = cameraSource
                            if (src != null) {
                                CameraScreen(state = s, cameraSource = src, onStop = {
                                    cameraSource = null; viewModel.stopAnalysis()
                                })
                            }
                        } else {
                            VideoAnalysisScreen(state = s, onStop = viewModel::stopAnalysis)
                        }
                    }

                    is AnalysisState.Finished -> {
                        // REMOVED ResultScreen usage. Handling finished state within MainScreen or same screen.
                        MainScreen(state = s, onMediaSelected = { uri -> 
                            val mimeType = contentResolver.getType(uri)
                            if (mimeType?.startsWith("video") == true) {
                                viewModel.analyzeVideo(uri)
                            } else {
                                viewModel.analyzeImage(uri)
                            }
                        }, onStop = viewModel::stopAnalysis)
                    }

                    is AnalysisState.Error -> MainScreen(
                        state = s,
                        onMediaSelected = { uri -> 
                            val mimeType = contentResolver.getType(uri)
                            if (mimeType?.startsWith("video") == true) {
                                viewModel.analyzeVideo(uri)
                            } else {
                                viewModel.analyzeImage(uri)
                            }
                        },
                        onStop = viewModel::stopAnalysis
                    )
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val perms = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }
}
