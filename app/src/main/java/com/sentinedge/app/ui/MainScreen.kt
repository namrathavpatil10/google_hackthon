package com.sentinedge.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinedge.app.AnalysisState
import com.sentinedge.app.Verdict
import com.sentinedge.app.toVerdict

private val BgGradient = Brush.verticalGradient(listOf(Color(0xFF0D0D1A), Color(0xFF1A1A2E)))

@Composable
fun MainScreen(
    state: AnalysisState,
    onVideoSelected: (Uri) -> Unit,
    onStop: () -> Unit,
) {
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onVideoSelected(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            // App title
            Text(
                text = "SentinEdge",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Text(
                text = "On-device deepfake detection",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(8.dp))

            // State-driven center card
            when (val s = state) {
                is AnalysisState.Idle -> IdleCard { videoPicker.launch("video/*") }
                is AnalysisState.Loading -> LoadingCard()
                is AnalysisState.Running -> RunningCard(state = s, onStop = onStop)
                is AnalysisState.Finished -> FinishedCard(state = s, onAnalyzeAnother = { videoPicker.launch("video/*") })
                is AnalysisState.Error -> ErrorCard(message = s.message, onRetry = onStop)
            }
        }
    }
}

@Composable
private fun IdleCard(onPickVideo: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E3A))
            .padding(28.dp),
    ) {
        Text(
            text = "Upload a video to analyze",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "SentinEdge will extract frames and score each one for deepfake artifacts using the on-device ViT model.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onPickVideo,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
        ) {
            Text("Upload Video File", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingCard() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E3A))
            .padding(28.dp),
    ) {
        CircularProgressIndicator(color = Color(0xFF6C63FF))
        Text("Preparing video…", color = Color.White)
    }
}

@Composable
private fun RunningCard(state: AnalysisState.Running, onStop: () -> Unit) {
    val verdict = state.latestResult.trustScore.toVerdict()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E3A))
            .padding(28.dp),
    ) {
        TrustScoreRing(
            trustScore = state.latestResult.trustScore,
            verdict = verdict,
        )

        StatRow("Frames analyzed", "${state.framesAnalyzed}")
        state.blinkRate?.let { rate ->
            val label = when {
                rate < 10f -> "abnormally low"
                rate > 30f -> "unusually high"
                else -> "normal"
            }
            StatRow("Blink rate", "%.1f /min ($label)".format(rate))
        }
        StatRow("Artifact score", "%.3f".format(state.latestResult.artifactScore))

        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun FinishedCard(state: AnalysisState.Finished, onAnalyzeAnother: () -> Unit) {
    val verdictColor = when (state.verdict) {
        Verdict.REAL -> Color(0xFF4CAF50)
        Verdict.SUSPICIOUS -> Color(0xFFFFC107)
        Verdict.DEEPFAKE -> Color(0xFFF44336)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E3A))
            .padding(28.dp),
    ) {
        TrustScoreRing(
            trustScore = state.finalScore,
            verdict = state.verdict,
            ringSize = 180.dp,
        )
        Text(
            text = when (state.verdict) {
                Verdict.REAL -> "Likely authentic"
                Verdict.SUSPICIOUS -> "Suspicious — review carefully"
                Verdict.DEEPFAKE -> "Deepfake detected"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = verdictColor,
            textAlign = TextAlign.Center,
        )
        StatRow("Frames analyzed", "${state.framesAnalyzed}")
        StatRow("Average trust score", "%.1f%%".format(state.finalScore * 100))

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onAnalyzeAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
        ) {
            Text("Analyze Another Video", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF3A1E1E))
            .padding(28.dp),
    ) {
        Text("Error", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
        Text(message, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))) {
            Text("Back")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
