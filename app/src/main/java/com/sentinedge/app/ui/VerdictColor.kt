package com.sentinedge.app.ui

import androidx.compose.ui.graphics.Color
import com.sentinedge.app.Verdict

fun verdictColor(verdict: Verdict): Color = when (verdict) {
    Verdict.REAL -> Color(0xFF4CAF50)
    Verdict.SUSPICIOUS -> Color(0xFFFFC107)
    Verdict.DEEPFAKE -> Color(0xFFFF5252)
}
