package com.sentinedge.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
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
    downloadProgress: Int? = null,
) {
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAnalyzeAnother(it) }
    }

    val color = verdictColor(state.verdict)
    val bgColor = when (state.verdict) {
        Verdict.REAL       -> Color(0xFF0A1A0A)
        Verdict.SUSPICIOUS -> Color(0xFF1A160A)
        Verdict.DEEPFAKE   -> Color(0xFF1A0A0A)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColor, Color(0xFF0A0A1A)))),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(vertical = 60.dp),
        ) {
            // Top icon
            Icon(
                imageVector = when (state.verdict) {
                    Verdict.REAL     -> Icons.Filled.CheckCircle
                    else             -> Icons.Filled.Warning
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(56.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "ANALYSIS COMPLETE",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.45f),
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(16.dp))

            // Trust ring
            TrustScoreRing(
                trustScore = state.finalScore,
                verdict = state.verdict,
                ringSize = 200.dp,
                strokeWidth = 16.dp,
            )

            Spacer(Modifier.height(24.dp))

            // Verdict
            Text(
                text = when (state.verdict) {
                    Verdict.REAL       -> "LIKELY AUTHENTIC"
                    Verdict.SUSPICIOUS -> "SUSPICIOUS CONTENT"
                    Verdict.DEEPFAKE   -> "DEEPFAKE DETECTED"
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(8.dp))

            // Explanation or download note
            if (!state.result?.explanation.isNullOrBlank()) {
                Text(
                    text = state.result!!.explanation,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else if (downloadProgress != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF7B61FF),
                    )
                    Text(
                        "AI Reasoning model downloading ($downloadProgress%)...",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            } else {
                Text(
                    text = "The analysis examined visual patterns, anatomical consistency, and metadata integrity.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            // Stats card
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A3A)),
            ) {
                ResultStatRow("Final trust score", "%.1f%%".format(state.finalScore * 100), color)
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(horizontal = 20.dp))

                if (state.result?.watermarkFound == true) {
                    ResultStatRow("AI Watermark", "FOUND", Color(0xFFFF5252))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(horizontal = 20.dp))
                }

                if (state.result?.metadataSuspicious == true) {
                    ResultStatRow("Metadata Integrity", "SUSPICIOUS", Color(0xFFFFC107))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(horizontal = 20.dp))
                }

                ResultStatRow(
                    "NPU Accelerator",
                    state.accelerator,
                    if (state.accelerator.contains("NPU")) Color(0xFF4FC3F7) else color,
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(horizontal = 20.dp))
                ResultStatRow("Verdict", state.verdict.name, color)
            }

            Spacer(Modifier.height(28.dp))

            // Primary button
            Button(
                onClick = { mediaPicker.launch("*/*") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C6FFF)),
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Analyze Another File", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            // Secondary button
            OutlinedButton(
                onClick = onLiveCamera,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4FC3F7)),
                border = BorderStroke(1.dp, Color(0xFF4FC3F7)),
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Protect Video Call", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResultStatRow(label: String, value: String, valueColor: Color) {
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
