package com.sentinedge.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.sentinedge.app.Verdict

/**
 * Horizontal FAKE ◄────────► REAL confidence bar.
 * Fill slides from left (fake) to right (real) based on trustScore.
 */
@Composable
fun DetectionBar(
    trustScore: Float,    // 0.0 = fake, 1.0 = real
    verdict: Verdict,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = trustScore,
        animationSpec = tween(500),
        label = "bar",
    )

    val barColor = when (verdict) {
        Verdict.REAL      -> Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Color(0xFF4CAF50)))
        Verdict.SUSPICIOUS -> Brush.horizontalGradient(listOf(Color(0xFFE65100), Color(0xFFFFC107)))
        Verdict.DEEPFAKE  -> Brush.horizontalGradient(listOf(Color(0xFF7F0000), Color(0xFFFF5252)))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Labels
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("FAKE", fontSize = 10.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                "%.0f%% Realness".format(trustScore * 100),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
            )
            Text("REAL", fontSize = 10.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            // Filled portion
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50.dp))
                    .background(barColor),
            )

            // Thumb marker
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(animated)
                    .wrapContentWidth(Alignment.End)
                    .padding(end = 0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color.White),
                )
            }
        }

        // Verdict label
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(verdictColor(verdict).copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text(
                    text = when (verdict) {
                        Verdict.REAL       -> "✓  Authentic"
                        Verdict.SUSPICIOUS -> "⚠  Suspicious"
                        Verdict.DEEPFAKE   -> "✕  Deepfake Detected"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = verdictColor(verdict),
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
