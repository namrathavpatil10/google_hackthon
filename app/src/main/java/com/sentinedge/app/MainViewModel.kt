package com.sentinedge.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinedge.app.ml.BlinkTracker
import com.sentinedge.app.ml.DeepfakeDetector
import com.sentinedge.app.ml.DetectionResult
import com.sentinedge.app.source.DebugFileSource
import com.sentinedge.app.source.FrameSource
import com.sentinedge.app.source.LiveCameraSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SourceType { VIDEO, CAMERA }

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class Running(
        val latestResult: DetectionResult,
        val framesAnalyzed: Int,
        val blinkRate: Float?,
        val sourceType: SourceType = SourceType.VIDEO,
    ) : AnalysisState
    data class Finished(
        val finalScore: Float,
        val framesAnalyzed: Int,
        val verdict: Verdict,
    ) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

enum class Verdict { REAL, SUSPICIOUS, DEEPFAKE }

fun Float.toVerdict(confidence: Float = 1f) = when {
    confidence < 0.4f -> Verdict.SUSPICIOUS // Unsure model = suspicious
    this >= 0.82f -> Verdict.REAL           // Be strict for "Real"
    this >= 0.40f -> Verdict.SUSPICIOUS
    else -> Verdict.DEEPFAKE
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val detector = DeepfakeDetector(app).also { it.init() }
    private val blinkTracker = BlinkTracker()

    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    private var activeSource: FrameSource? = null
    private var analysisJob: Job? = null

    fun analyzeVideo(uri: Uri) {
        stopAnalysis()
        startAnalysis(DebugFileSource(getApplication(), uri), SourceType.VIDEO)
    }

    fun startLiveCamera(): LiveCameraSource {
        stopAnalysis()
        val source = LiveCameraSource()
        startAnalysis(source, SourceType.CAMERA)
        return source
    }

    private fun startAnalysis(source: FrameSource, sourceType: SourceType) {
        activeSource = source
        _state.value = AnalysisState.Loading

        var framesAnalyzed = 0
        val scoreBuffer = ArrayDeque<Float>()

        analysisJob = viewModelScope.launch {
            try {
                source.frames().collect { frame ->
                    val faceInfo = blinkTracker.addFrame(frame)
                    val result = detector.analyze(frame, faceInfo.boundingBox)
                    framesAnalyzed++

                    // Combine model score (70%) + blink signal (30%)
                    val combinedScore = combineSignals(result.trustScore, faceInfo.blinkRate)
                    val combined = result.copy(trustScore = combinedScore, blinkRate = faceInfo.blinkRate)

                    scoreBuffer.addLast(combinedScore)
                    if (scoreBuffer.size > 30) scoreBuffer.removeFirst()

                    _state.value = AnalysisState.Running(
                        latestResult = combined,
                        framesAnalyzed = framesAnalyzed,
                        blinkRate = faceInfo.blinkRate,
                        sourceType = sourceType,
                    )
                }
                // Flow completed — only for video; camera runs until stopped
                if (sourceType == SourceType.VIDEO) {
                    val avgScore = if (scoreBuffer.isEmpty()) 0f else scoreBuffer.average().toFloat()
                    _state.value = AnalysisState.Finished(
                        finalScore = avgScore,
                        framesAnalyzed = framesAnalyzed,
                        verdict = avgScore.toVerdict(1f),
                    )
                }
            } catch (e: Exception) {
                _state.value = AnalysisState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Combines the model's trust score with the blink-rate heuristic.
     *
     * Model score  = 70% weight (primary AI signal)
     * Blink signal = 30% weight (secondary human-behaviour signal)
     *
     * Blink penalty:
     *   < 10/min  → strong fake signal (deepfakes rarely blink) → blinkScore = 0.1
     *   10–25/min → normal human range                          → blinkScore = 1.0
     *   25–35/min → slightly elevated, mild suspicion           → blinkScore = 0.6
     *   > 35/min  → abnormally high, suspicious                 → blinkScore = 0.3
     *   null      → not enough data yet, no penalty applied
     */
    private fun combineSignals(modelScore: Float, blinkRate: Float?): Float {
        // If the model is weak, it might fluctuate. 
        // We smooth it and apply a very conservative blink penalty.
        val baseScore = modelScore.coerceIn(0f, 1f)
        
        if (blinkRate == null) return baseScore

        val blinkPenalty = when {
            blinkRate < 4f   -> 0.80f  // Reduced penalty for weak models
            blinkRate < 8f   -> 0.92f
            blinkRate <= 30f -> 1.0f
            else             -> 0.90f
        }

        return (baseScore * blinkPenalty).coerceIn(0f, 1f)
    }

    fun stopAnalysis() {
        analysisJob?.cancel()
        activeSource?.release()
        activeSource = null
        _state.value = AnalysisState.Idle
    }

    override fun onCleared() {
        stopAnalysis()
        detector.close()
        blinkTracker.close()
    }
}
