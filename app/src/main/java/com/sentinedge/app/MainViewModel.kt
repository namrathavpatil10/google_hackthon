package com.sentinedge.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinedge.app.ml.BlinkTracker
import com.sentinedge.app.ml.DeepfakeDetector
import com.sentinedge.app.ml.DetectionResult
import com.sentinedge.app.ml.ModelDownloader
import com.sentinedge.app.source.DebugFileSource
import com.sentinedge.app.source.FrameSource
import com.sentinedge.app.source.LiveCameraSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SourceType { IMAGE, VIDEO, CAMERA }

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class Running(
        val latestResult: DetectionResult,
        val framesAnalyzed: Int,
        val blinkRate: Float?,
        val sourceType: SourceType = SourceType.VIDEO,
        val isFaceDetected: Boolean = true
    ) : AnalysisState
    data class Finished(
        val finalScore: Float,
        val framesAnalyzed: Int,
        val verdict: Verdict,
        val result: DetectionResult? = null,
        val accelerator: String = "CPU"
    ) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

enum class Verdict { REAL, SUSPICIOUS, DEEPFAKE }

fun Float.toVerdict() = when {
    this >= 0.70f -> Verdict.REAL
    this >= 0.35f -> Verdict.SUSPICIOUS
    else -> Verdict.DEEPFAKE
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val detector = DeepfakeDetector(app).also { it.init() }
    private val blinkTracker = BlinkTracker()
    private val modelDownloader = ModelDownloader(app)

    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()

    // --- Initialisation: auto-download Gemma if not present ---
    // Kicks off a DownloadManager job on first launch; polls progress every second.
    // When complete, re-inits the detector so Gemma is picked up without restart.
    init {
        if (!modelDownloader.isModelAvailable()) {
            val id = modelDownloader.startDownload()
            if (id != -1L) {
                viewModelScope.launch {
                    modelDownloader.getDownloadProgress(id).collect { progress ->
                        _downloadProgress.value = progress
                        if (progress == 100) {
                            detector.init()
                            _downloadProgress.value = null
                        }
                    }
                }
            }
        }
    }

    private var activeSource: FrameSource? = null
    private var analysisJob: Job? = null

    // --- Image analysis ---
    // Downsamples the bitmap to ~1000px to avoid OOM, then runs single-frame detection.
    fun analyzeImage(uri: Uri) {
        stopAnalysis()
        detector.clearBuffer()
        _state.value = AnalysisState.Loading
        
        viewModelScope.launch {
            try {
                // Add a small artificial delay so the UI doesn't flicker too fast
                delay(800)

                val contentResolver = getApplication<Application>().contentResolver
                
                // 1. Get dimensions first without loading into memory
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                contentResolver.openInputStream(uri)?.use { 
                    BitmapFactory.decodeStream(it, null, options) 
                }

                // 2. Calculate optimal scaling to prevent OOM (Target ~1000px)
                val targetSize = 1000
                var inSampleSize = 1
                if (options.outHeight > targetSize || options.outWidth > targetSize) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / inSampleSize >= targetSize && halfWidth / inSampleSize >= targetSize) {
                        inSampleSize *= 2
                    }
                }

                // 3. Load the downsampled bitmap
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                val bitmap = contentResolver.openInputStream(uri)?.use { 
                    BitmapFactory.decodeStream(it, null, decodeOptions) 
                }
                
                if (bitmap == null) {
                    _state.value = AnalysisState.Error("Could not load image")
                    return@launch
                }

                val result = detector.analyzeImage(uri, bitmap)
                
                _state.value = AnalysisState.Finished(
                    finalScore = result.trustScore,
                    framesAnalyzed = 1,
                    verdict = result.trustScore.toVerdict(),
                    result = result,
                    accelerator = result.acceleratorUsed
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Analysis failed", e)
                _state.value = AnalysisState.Error(e.message ?: "Analysis failed")
            }
        }
    }

    // --- Video / camera analysis ---
    // Both paths funnel into startAnalysis(); DebugFileSource extracts frames from a file,
    // LiveCameraSource streams frames from CameraX.
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
        detector.clearBuffer()
        _state.value = AnalysisState.Loading

        var framesAnalyzed = 0
        var totalScoreSum = 0f
        var lastResult: DetectionResult? = null
        var bestResult: DetectionResult? = null

        analysisJob = viewModelScope.launch {
            try {
                source.frames().collect { frame ->
                    val faceInfo = blinkTracker.addFrame(frame)
                    val result = detector.analyzeFrame(frame, faceInfo, isLive = sourceType == SourceType.CAMERA)
                    
                    // Dynamically update sampling interval based on suspicion level
                    if (source is LiveCameraSource) {
                        source.samplingInterval = detector.currentSamplingRate
                    }

                    lastResult = result
                    framesAnalyzed++
                    totalScoreSum += result.trustScore
                    
                    // Keep track of the most "significant" result to show at the end
                    if (bestResult == null || 
                        (result.trustScore < 0.3f && (bestResult?.trustScore ?: 1.0f) > 0.3f) ||
                        (result.trustScore > 0.8f && (bestResult?.trustScore ?: 0f) < 0.8f)) {
                        bestResult = result
                    }

                    _state.value = AnalysisState.Running(
                        latestResult = result,
                        framesAnalyzed = framesAnalyzed,
                        blinkRate = faceInfo.blinkRate,
                        sourceType = sourceType,
                        isFaceDetected = faceInfo.boundingBox != null
                    )
                }
                
                if (sourceType == SourceType.VIDEO) {
                    val avgScore = if (framesAnalyzed == 0) 0f else totalScoreSum / framesAnalyzed
                    _state.value = AnalysisState.Finished(
                        finalScore = avgScore,
                        framesAnalyzed = framesAnalyzed,
                        verdict = avgScore.toVerdict(),
                        result = bestResult ?: lastResult,
                        accelerator = bestResult?.acceleratorUsed ?: lastResult?.acceleratorUsed ?: "CPU"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AnalysisState.Error(e.message ?: "Analysis failed")
            }
        }
    }

    // --- Stop ---
    // Cancels the coroutine (CancellationException is re-thrown so it doesn't surface as an error),
    // releases the frame source, and resets UI state to Idle.
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
