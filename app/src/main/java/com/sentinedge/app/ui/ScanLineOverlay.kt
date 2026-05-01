package com.sentinedge.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sentinedge.app.Verdict

/**
 * Glowing horizontal scan line that sweeps top→bottom continuously.
 * Color shifts green (real) / red (fake) based on current verdict.
 */
@Composable
fun ScanLineOverlay(verdict: Verdict, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanProgress",
    )

    val lineColor = verdictColor(verdict)

    Canvas(modifier = modifier.fillMaxSize()) {
        val y = size.height * progress

        // Glow behind the line
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    lineColor.copy(alpha = 0.15f),
                    lineColor.copy(alpha = 0.3f),
                    lineColor.copy(alpha = 0.15f),
                    Color.Transparent,
                ),
            ),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 18f,
        )

        // Sharp line
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    lineColor.copy(alpha = 0.9f),
                    lineColor,
                    lineColor.copy(alpha = 0.9f),
                    Color.Transparent,
                ),
            ),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2.5f,
        )
    }
}
