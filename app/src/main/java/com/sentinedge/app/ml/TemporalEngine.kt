package com.sentinedge.app.ml

import java.util.LinkedList
import java.util.Queue

/**
 * Temporal Awareness Engine
 * Inspired by hybrid CNN-RNN architectures like Naman712/Deep-fake-detection.
 * Tracks spatial scores and landmark stability over time to detect flickering/jitter.
 */
class TemporalEngine(private val windowSize: Int = 20) {

    private val scoreHistory: Queue<Float> = LinkedList()
    private val jitterHistory: Queue<Float> = LinkedList()

    data class TemporalInsight(
        val coherenceScore: Float, // 1.0 = Highly consistent, 0.0 = Glitchy/Flickering
        val jitterWarning: Boolean,
        val suggestedVerdictAdjustment: Float // Offset to add/subtract from trust score
    )

    fun processFrame(currentScore: Float, landmarkJitter: Float?): TemporalInsight {
        // 1. Update Score History (Temporal Consistency)
        if (scoreHistory.size >= windowSize) scoreHistory.poll()
        scoreHistory.add(currentScore)

        // 2. Update Jitter History (Landmark Stability)
        landmarkJitter?.let {
            if (jitterHistory.size >= windowSize) jitterHistory.poll()
            jitterHistory.add(it)
        }

        // 3. Calculate Variance (Flicker detection)
        val variance = if (scoreHistory.size > 5) calculateVariance(scoreHistory.toList()) else 0f
        
        // 4. Calculate Jitter intensity
        val avgJitter = if (jitterHistory.isNotEmpty()) jitterHistory.average().toFloat() else 0f

        // Logic: If score fluctuates wildly (variance > 0.03) or jitter is high, it's a fake
        val isGlitchy = variance > 0.03f || avgJitter > 0.12f
        
        // We apply a heavy penalty for glitches. 
        val adjustment = if (isGlitchy) -0.4f else 0.0f 

        return TemporalInsight(
            coherenceScore = (1.0f - (variance * 15f)).coerceIn(0f, 1f),
            jitterWarning = avgJitter > 0.10f,
            suggestedVerdictAdjustment = adjustment
        )
    }

    private fun calculateVariance(scores: List<Float>): Float {
        val avg = scores.average()
        return scores.map { (it - avg).let { d -> (d * d).toFloat() } }.average().toFloat()
    }

    fun reset() {
        scoreHistory.clear()
        jitterHistory.clear()
    }
}
