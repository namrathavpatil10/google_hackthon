package com.sentinedge.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinedge.app.Verdict

private val ColorReal = Color(0xFF4CAF50)
private val ColorSuspicious = Color(0xFFFFC107)
private val ColorDeepfake = Color(0xFFF44336)

@Composable
fun TrustScoreRing(
    trustScore: Float,          // 0.0–1.0
    verdict: Verdict,
    modifier: Modifier = Modifier,
    ringSize: Dp = 160.dp,
    strokeWidth: Dp = 14.dp,
) {
    val animatedScore by animateFloatAsState(
        targetValue = trustScore,
        animationSpec = tween(durationMillis = 400),
        label = "trustScore"
    )
    val ringColor by animateColorAsState(
        targetValue = when (verdict) {
            Verdict.REAL -> ColorReal
            Verdict.SUSPICIOUS -> ColorSuspicious
            Verdict.DEEPFAKE -> ColorDeepfake
        },
        animationSpec = tween(durationMillis = 400),
        label = "ringColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(ringSize),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Background track
            drawArc(
                color = Color.White.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            // Filled arc
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedScore,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(trustScore * 100).toInt()}%",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ringColor,
            )
            Text(
                text = verdict.label(),
                fontSize = 12.sp,
                color = ringColor,
            )
        }
    }
}

private fun Verdict.label() = when (this) {
    Verdict.REAL -> "REAL"
    Verdict.SUSPICIOUS -> "SUSPICIOUS"
    Verdict.DEEPFAKE -> "DEEPFAKE"
}
