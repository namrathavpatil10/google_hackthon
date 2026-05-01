package com.sentinedge.app.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sentinedge.app.AnalysisState
import com.sentinedge.app.SourceType
import com.sentinedge.app.Verdict
import com.sentinedge.app.source.LiveCameraSource
import com.sentinedge.app.toVerdict
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    state: AnalysisState,
    cameraSource: LiveCameraSource,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }

    val verdict = if (state is AnalysisState.Running) state.latestResult.trustScore.toVerdict()
                  else Verdict.REAL

    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera preview ──────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx)
            },
            update = { pv ->
                ProcessCameraProvider.getInstance(context).addListener({
                    try {
                        val provider = ProcessCameraProvider.getInstance(context).get()
                        val preview = Preview.Builder().build()
                            .also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also { it.setAnalyzer(analysisExecutor, cameraSource) }
                        
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview, analysis,
                        )
                    } catch (e: Exception) { Log.e("CameraScreen", "bind failed", e) }
                }, ContextCompat.getMainExecutor(context))
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Scan line (only while running) ──────────────────────────────
        if (state is AnalysisState.Running) {
            ScanLineOverlay(verdict = verdict)
        }

        // ── Face bracket corners ─────────────────────────────────────────
        FaceBracket(verdict = verdict)

        // ── Top gradient ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))),
        )

        // ── Bottom gradient ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)))),
        )

        // ── Top bar ──────────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = Color(0xFFFF4444), modifier = Modifier.size(10.dp))
                Text("LIVE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 2.sp)
            }
            Text("SentinEdge", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Row {
                IconButton(onClick = {
                    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                }) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop", tint = Color.White)
                }
            }
        }

        // ── DEEPFAKE alert banner ────────────────────────────────────────
        if (state is AnalysisState.Running) {
            val result = state.latestResult
            
            if (verdict == Verdict.DEEPFAKE) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = 88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF7F0000), Color(0xFFD32F2F), Color(0xFF7F0000)))
                        ),
                ) {
                    Text(
                        "⚠   DEEPFAKE DETECTED",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            } else if (result.watermarkFound) {
                // INNOVATION: AI Signature Detection
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = 88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFC107).copy(alpha = 0.9f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Text(
                            "AI WATERMARK DETECTED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            } else if (!state.isFaceDetected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = 88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.9f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            "NO FACE DETECTED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
        }

        // ── Winning "Verified" Watermark ─────────────────────────────────
        if (state is AnalysisState.Running && state.latestResult.isVerified) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-80).dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                    .border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(50.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    Text(
                        "SENTINEDGE VERIFIED",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4CAF50),
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }

        // ── Bottom stats card ────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            when (state) {
                is AnalysisState.Loading -> {
                    CircularProgressIndicator(color = Color(0xFF7C6FFF), modifier = Modifier.size(36.dp))
                    Text("Starting camera…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }

                is AnalysisState.Running -> {
                    // Detection bar
                    DetectionBar(
                        trustScore = state.latestResult.trustScore,
                        verdict = verdict,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Stats row
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .padding(vertical = 12.dp),
                    ) {
                        MiniStat(label = "Frames", value = "${state.framesAnalyzed}")
                        VerticalDivider()
                        MiniStat(
                            label = "Blink",
                            value = if (state.sourceType == SourceType.IMAGE) "N/A" else (state.blinkRate?.let { "%.0f/min".format(it) } ?: "—"),
                            warning = state.sourceType != SourceType.IMAGE && state.blinkRate != null && (state.blinkRate < 5f || state.blinkRate > 30f),
                        )
                        VerticalDivider()
                        MiniStat(
                            label = "Mouth",
                            value = if (state.sourceType == SourceType.IMAGE) "N/A" else (state.latestResult.mouthMovementScore?.let { "%.2f".format(it) } ?: "—"),
                            warning = state.sourceType != SourceType.IMAGE && (state.latestResult.mouthMovementScore ?: 1f) < 0.3f,
                        )
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun FaceBracket(verdict: Verdict) {
    val color = verdictColor(verdict).copy(alpha = 0.7f)
    val cornerSize = 28.dp
    val strokeWidth = 3.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 60.dp, vertical = 160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(16.dp))
                .border(width = strokeWidth, color = color, shape = RoundedCornerShape(16.dp)),
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, warning: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (warning) Color(0xFFFFC107) else Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f), letterSpacing = 0.5.sp)
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(Color.White.copy(alpha = 0.1f)),
    )
}
