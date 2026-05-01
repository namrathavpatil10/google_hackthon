package com.sentinedge.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinedge.app.AnalysisState
import com.sentinedge.app.SourceType
import com.sentinedge.app.Verdict
import com.sentinedge.app.toVerdict

@Composable
fun VideoAnalysisScreen(
    state: AnalysisState.Running,
    onStop: () -> Unit,
    downloadProgress: Int? = null,
) {
    val verdict = state.latestResult.trustScore.toVerdict()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0A1A), Color(0xFF13132A)))),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onStop) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Stop", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Analyzing Video", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PulsingDot()
                        Text("Scanning in background", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Trust ring - Always visible
            TrustScoreRing(
                trustScore = state.latestResult.trustScore,
                verdict = verdict,
                ringSize = 190.dp,
                strokeWidth = 15.dp,
            )

            Spacer(Modifier.height(24.dp))

            // Detection confidence bar - Always visible
            val realPercent = (state.latestResult.trustScore * 100).toInt()
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$realPercent% REALNESS",
                    color = if (state.latestResult.trustScore > 0.5f) Color(0xFF4CAF50) else Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                DetectionBar(
                    trustScore = state.latestResult.trustScore,
                    verdict = verdict,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))

            // Stats card
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A3A)),
            ) {
                StatItem("Frames analyzed", "${state.framesAnalyzed}", Color.White)
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 20.dp))
                StatItem(
                    "Trust score",
                    "%.1f%%".format(state.latestResult.trustScore * 100),
                    verdictColor(verdict),
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 20.dp))
                
                if (state.sourceType != SourceType.IMAGE) {
                    StatItem(
                        "Natural responses",
                        state.latestResult.mouthMovementScore?.let { "%.2f".format(it) } ?: "Scanning...",
                        if ((state.latestResult.mouthMovementScore ?: 1f) < 0.3f) Color(0xFFFF5252) else Color.White,
                    )
                    state.blinkRate?.let { rate ->
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 20.dp))
                        val (label, warn) = when {
                            rate < 5f   -> "%.0f/min · abnormally low ⚠".format(rate) to true
                            rate > 30f  -> "%.0f/min · unusually high ⚠".format(rate) to true
                            else        -> "%.0f/min · normal ✓".format(rate) to false
                        }
                        StatItem("Blink rate", label, if (warn) Color(0xFFFFC107) else Color(0xFF4CAF50))
                    }
                } else {
                    StatItem("Analysis Mode", "Static Image (Full Scan)", Color.White.copy(alpha = 0.6f))
                }
            }

            val explanation = state.latestResult.explanation
            if (explanation.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1A3A))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("AI FORENSIC ANALYSIS", fontSize = 12.sp, color = Color(0xFF7B61FF), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(explanation, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 19.sp)
                }
            } else if (downloadProgress != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1A1A3A))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PulsingDot()
                    Text(
                        "AI Reasoning model downloading ($downloadProgress%)...",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (downloadProgress != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Downloading AI Reasoning ($downloadProgress%)", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.width(80.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF7B61FF),
                        trackColor = Color.White.copy(alpha = 0.1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Stop button
            OutlinedButton(
                onClick = onStop,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Stop Analysis", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatItem(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF7C6FFF).copy(alpha = alpha))
    )
}
