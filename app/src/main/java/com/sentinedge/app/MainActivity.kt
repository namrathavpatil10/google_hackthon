package com.sentinedge.app

import android.Manifest
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
import androidx.core.content.ContextCompat
import com.sentinedge.app.ml.DetectionResult
import com.sentinedge.app.service.DeepfakeOverlayService
import com.sentinedge.app.source.LiveCameraSource
import com.sentinedge.app.ui.*
import com.sentinedge.app.ui.theme.SentinEdgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // ── Permissions ────────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    // ── MediaProjection for "Protect Live Calls" ───────────────────────

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            ContextCompat.startForegroundService(
                this,
                DeepfakeOverlayService.buildIntent(this, result.resultCode, result.data!!)
            )
        }
    }

    // ── Overlay permission redirect ────────────────────────────────────

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from Settings, try again if permission was granted
        if (Settings.canDrawOverlays(this)) launchScreenCapture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()

        setContent {
            SentinEdgeTheme {
                val state by viewModel.state.collectAsState()
                var cameraSource by remember { mutableStateOf<LiveCameraSource?>(null) }

                when (val s = state) {
                    is AnalysisState.Idle -> HomeScreen(
                        onVideoSelected = { uri -> viewModel.analyzeVideo(uri) },
                        onLiveCamera = {
                            cameraSource = viewModel.startLiveCamera()
                        },
                        onProtectLiveCalls = { requestLiveCallProtection() },
                    )

                    is AnalysisState.Loading -> {
                        val src = cameraSource
                        if (src != null) {
                            CameraScreen(state = s, cameraSource = src, onStop = {
                                cameraSource = null; viewModel.stopAnalysis()
                            })
                        } else {
                            VideoAnalysisScreen(
                                state = AnalysisState.Running(
                                    latestResult = DetectionResult(0f, null, 0f),
                                    framesAnalyzed = 0,
                                    blinkRate = null,
                                ),
                                onStop = viewModel::stopAnalysis,
                            )
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

                    is AnalysisState.Finished -> ResultScreen(
                        state = s,
                        onAnalyzeAnother = { uri -> viewModel.analyzeVideo(uri) },
                        onLiveCamera = { cameraSource = viewModel.startLiveCamera() },
                    )

                    is AnalysisState.Error -> HomeScreen(
                        onVideoSelected = { uri -> viewModel.analyzeVideo(uri) },
                        onLiveCamera = { cameraSource = viewModel.startLiveCamera() },
                        onProtectLiveCalls = { requestLiveCallProtection() },
                    )
                }
            }
        }
    }

    // ── "Protect Live Calls" flow ──────────────────────────────────────

    private fun requestLiveCallProtection() {
        if (!Settings.canDrawOverlays(this)) {
            // Step 1: ask for "draw over other apps" permission
            overlayPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
            )
        } else {
            launchScreenCapture()
        }
    }

    private fun launchScreenCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    // ── Runtime permissions ────────────────────────────────────────────

    private fun requestRuntimePermissions() {
        val perms = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }
}
