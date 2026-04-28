package com.sentinedge.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun ResultScreen(
    state: AnalysisState.Finished,
    onAnalyzeAnother: (Uri) -> Unit,
    onLiveCamera: () -> Unit,
) {
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAnalyzeAnother(it) }
    }

    val color = verdictColor(state.verdict)
    val bgColor = when (state.verdict) {
        Verdict.REAL -> Color(0xFF0A1A0A)
        Verdict.SUSPICIOUS -> Color(0xFF1A160A)
        Verdict.DEEPFAKE -> Color(0xFF1A0A0A)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColor, Color(0xFF0A0A1A)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(48.dp))

            // Icon
            Icon(
                imageVector = if (state.verdict == Verdict.REAL) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text("Analysis Complete", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f), letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))

            // Big ring
            TrustScoreRing(
                trustScore = state.finalScore,
                verdict = state.verdict,
                ringSize = 200.dp,
                strokeWidth = 16.dp,
            )

            Spacer(Modifier.height(24.dp))

            // Verdict text
            Text(
                text = when (state.verdict) {
                    Verdict.REAL -> "Likely Authentic"
                    Verdict.SUSPICIOUS -> "Suspicious Content"
                    Verdict.DEEPFAKE -> "Deepfake Detected"
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = when (state.verdict) {
                    Verdict.REAL -> "No deepfake artifacts found. This video appears genuine."
                    Verdict.SUSPICIOUS -> "Some artifacts detected. Exercise caution with this content."
                    Verdict.DEEPFAKE -> "Strong deepfake signals detected. This video is likely AI-generated."
                },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )

            Spacer(Modifier.height(32.dp))

            // Summary card
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A3A))
                    .padding(24.dp),
            ) {
                ResultStatRow("Final trust score", "%.1f%%".format(state.finalScore * 100), color)
                Divider(color = Color.White.copy(alpha = 0.07f))
                ResultStatRow("Total frames scanned", "${state.framesAnalyzed}", Color.White)
                Divider(color = Color.White.copy(alpha = 0.07f))
                ResultStatRow("Verdict", state.verdict.name, color)
            }

            Spacer(Modifier.weight(1f))

            // Action buttons
            Button(
                onClick = { videoPicker.launch("video/*") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C6FFF)),
            ) {
                Icon(Icons.Filled.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Analyze Another Video", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onLiveCamera,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4FC3F7)),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0277BD)))
                ),
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Switch to Live Camera", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun ResultStatRow(label: String, value: String, valueColor: Color) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
