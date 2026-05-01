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
import com.sentinedge.app.service.LiveProtectionService
import com.sentinedge.app.ui.*
import com.sentinedge.app.ui.theme.SentinEdgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var isBackgroundRunning by mutableStateOf(false)

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, LiveProtectionService::class.java).apply {
                putExtra("projection_data", result.data)
                putExtra("projection_result_code", result.resultCode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            isBackgroundRunning = true
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    private fun toggleBackground(active: Boolean) {
        if (active) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                return
            }
            val mpManager = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } else {
            stopService(Intent(this, LiveProtectionService::class.java))
            isBackgroundRunning = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        setContent {
            SentinEdgeTheme {
                val state by viewModel.state.collectAsState()
                
                when (val s = state) {
                    is AnalysisState.Idle -> MainScreen(
                        onMediaSelected = { uri -> 
                            val mimeType = contentResolver.getType(uri)
                            if (mimeType?.startsWith("video") == true) {
                                viewModel.analyzeVideo(uri)
                            } else {
                                viewModel.analyzeImage(uri)
                            }
                        },
                        onToggleBackground = { toggleBackground(it) },
                        isBackgroundActive = isBackgroundRunning
                    )
                    is AnalysisState.Running -> {
                        VideoAnalysisScreen(state = s, onStop = { viewModel.stopAnalysis() })
                    }
                    is AnalysisState.Finished -> {
                        ResultScreen(
                            state = s,
                            onAnalyzeAnother = { uri -> 
                                val mimeType = contentResolver.getType(uri)
                                if (mimeType?.startsWith("video") == true) {
                                    viewModel.analyzeVideo(uri)
                                } else {
                                    viewModel.analyzeImage(uri)
                                }
                            },
                            onLiveCamera = { viewModel.startLiveCamera() }
                        )
                    }
                    else -> MainScreen(
                        onMediaSelected = { viewModel.analyzeImage(it) },
                        onToggleBackground = { toggleBackground(it) },
                        isBackgroundActive = isBackgroundRunning
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms)
    }
}
