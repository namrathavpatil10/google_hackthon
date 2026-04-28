package com.sentinedge.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.sentinedge.app.Verdict
import com.sentinedge.app.toVerdict

@Composable
fun VideoAnalysisScreen(
    state: AnalysisState.Running,
    onStop: () -> Unit,
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
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Stop", tint = Color.White)
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

            // Trust ring
            TrustScoreRing(
                trustScore = state.latestResult.trustScore,
                verdict = verdict,
                ringSize = 190.dp,
                strokeWidth = 15.dp,
            )

            Spacer(Modifier.height(24.dp))

            // Detection confidence bar
            DetectionBar(
                trustScore = state.latestResult.trustScore,
                verdict = verdict,
                modifier = Modifier.fillMaxWidth(),
            )

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
                Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 20.dp))
                StatItem(
                    "Trust score",
                    "%.1f%%".format(state.latestResult.trustScore * 100),
                    verdictColor(verdict),
                )
                Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 20.dp))
                StatItem(
                    "Artifact score",
                    "%.3f".format(state.latestResult.artifactScore),
                    if (state.latestResult.artifactScore > 0.5f) Color(0xFFFF5252) else Color.White,
                )
                state.blinkRate?.let { rate ->
                    Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 20.dp))
                    val (label, warn) = when {
                        rate < 10f  -> "%.0f/min · abnormally low ⚠".format(rate) to true
                        rate > 30f  -> "%.0f/min · unusually high ⚠".format(rate) to true
                        else        -> "%.0f/min · normal ✓".format(rate) to false
                    }
                    StatItem("Blink rate", label, if (warn) Color(0xFFFFC107) else Color(0xFF4CAF50))
                }
            }

            Spacer(Modifier.weight(1f))

            // Stop button
            OutlinedButton(
                onClick = onStop,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF7C6FFF), Color(0xFF4FC3F7)))
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Stop Analysis", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(Color(0xFF7C6FFF).copy(alpha = alpha)),
    )
}

fun verdictColor(verdict: Verdict) = when (verdict) {
    Verdict.REAL       -> Color(0xFF4CAF50)
    Verdict.SUSPICIOUS -> Color(0xFFFFC107)
    Verdict.DEEPFAKE   -> Color(0xFFFF5252)
}
