package com.sentinedge.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.LinkedList
import java.util.Queue
import kotlin.coroutines.resume
import kotlin.math.exp

data class DetectionResult(
    val trustScore: Float,   // 1.0 = Real, 0.0 = Fake
    val blinkRate: Float?,
    val livenessScore: Float? = null,
    val mouthMovementScore: Float? = null,
    val faceBounds: Rect? = null,
    val confidence: Float = 0f,
    val isVerified: Boolean = false,
    val watermarkFound: Boolean = false,
    val watermarkType: SafetyVerificationEngine.WatermarkType = SafetyVerificationEngine.WatermarkType.NONE,
    val metadataSuspicious: Boolean = false,
    val explanation: String = "",
    val acceleratorUsed: String = "CPU"
)

/**
 * SentinEdge Deepfake Detector (SOTA 2025 Architecture)
 * Combines Spatial ViT, Spectral DNA Analysis, and Temporal Consistency.
 */
class DeepfakeDetector(private val context: Context) {

    private var livenessModelV1: CompiledModel? = null
    private var livenessInputsV1: List<TensorBuffer>? = null
    private var livenessOutputsV1: List<TensorBuffer>? = null

    private var livenessModelV2: CompiledModel? = null
    private var livenessInputsV2: List<TensorBuffer>? = null
    private var livenessOutputsV2: List<TensorBuffer>? = null

    private var hrNetModel: CompiledModel? = null
    private var hrNetInputs: List<TensorBuffer>? = null
    private var hrNetOutputs: List<TensorBuffer>? = null

    private val temporalEngine = TemporalEngine(windowSize = 20)
    private val spectralScanner = SpectralScanner(scanSize = 64)

    private var gemmaEngine: Engine? = null
    private var vlmEngine: Engine? = null

    private val livenessModelFileV1 = "deepfake_detector.tflite"
    private val livenessModelFileV2 = "deepfake_detector_v2.tflite"
    private val hrNetModelFile = "hrnet_face.tflite"
    private val gemmaModelName = "gemma.litertlm"
    
    private var livenessInputSize = 224 

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    
    private var currentAccelerator = "CPU"

    private val scoreQueue: Queue<Float> = LinkedList()
    private val windowSize = 5
    private var frameCount = 0
    private var lastExplanation = "Forensic Guard Active"

    var currentSamplingRate = 100L

    fun init() {
        val accel = when {
            tryInit(Accelerator.NPU) -> "NPU"
            tryInit(Accelerator.GPU) -> "GPU"
            else -> "CPU"
        }
        currentAccelerator = accel
    }

    fun clearBuffer() {
        scoreQueue.clear(); temporalEngine.reset(); frameCount = 0
    }

    private fun tryInit(accelerator: Accelerator): Boolean {
        return try {
            val options = CompiledModel.Options(accelerator)
            val libDir = context.applicationInfo.nativeLibraryDir
            val cacheDir = context.cacheDir.path
            
            // Auto-load models from assets
            try {
                val lm1 = CompiledModel.create(context.assets, livenessModelFileV1, options)
                livenessModelV1 = lm1
                livenessInputsV1 = lm1.createInputBuffers()
                livenessOutputsV1 = lm1.createOutputBuffers()
            } catch (_: Exception) {}

            try {
                val lm2 = CompiledModel.create(context.assets, livenessModelFileV2, options)
                livenessModelV2 = lm2
                livenessInputsV2 = lm2.createInputBuffers()
                livenessOutputsV2 = lm2.createOutputBuffers()
            } catch (_: Exception) {}

            val gemmaFile = File(context.getExternalFilesDir(null), gemmaModelName)
            if (gemmaFile.exists()) tryInitEngine(gemmaFile.absolutePath, libDir, cacheDir, false)

            true
        } catch (_: Exception) { false }
    }

    private fun tryInitEngine(modelPath: String, libDir: String, cacheDir: String, isVlm: Boolean) {
        try {
            val config = EngineConfig(modelPath = modelPath, backend = Backend.GPU(), visionBackend = Backend.CPU(), cacheDir = cacheDir)
            val engine = Engine(config).apply { initialize() }
            if (isVlm) vlmEngine = engine else gemmaEngine = engine
        } catch (_: Exception) {}
    }

    suspend fun analyzeImage(uri: Uri, bitmap: Bitmap): DetectionResult = withContext(Dispatchers.Default) {
        val safety = SafetyVerificationEngine.verifyImage(context, uri)
        val faceBounds = detectFace(bitmap)
        val target = if (faceBounds != null) cropBitmap(bitmap, faceBounds) else bitmap

        val v1 = runInference(livenessModelV1, livenessInputsV1, livenessOutputsV1, target, livenessInputSize)
        val v2 = runInference(livenessModelV2, livenessInputsV2, livenessOutputsV2, target, livenessInputSize)
        val spectral = spectralScanner.scan(target)
        
        // Consensus: V1 (40%) + V2 (40%) + Spectral (20%)
        val visualScore = if (livenessModelV2 != null) (v1 * 0.4f) + (v2 * 0.4f) + (spectral * 0.2f) else v1
        
        val finalScore = if (safety.watermarkFound) 0.1f else (visualScore * 0.7f) + (safety.trustScore * 0.3f)

        DetectionResult(
            trustScore = finalScore, blinkRate = null, livenessScore = v1,
            watermarkFound = safety.watermarkFound, watermarkType = safety.watermarkType,
            metadataSuspicious = safety.metadataSuspicious, confidence = v1,
            isVerified = finalScore > 0.70f, explanation = generateAIExplanation(finalScore, safety.trustScore, safety.watermarkFound, null, null),
            acceleratorUsed = "SOTA Ensemble", faceBounds = faceBounds
        )
    }

    suspend fun analyzeFrame(frame: Bitmap, faceInfo: FaceInfo, isBackground: Boolean = false): DetectionResult = withContext(Dispatchers.Default) {
        val target = if (faceInfo.boundingBox != null) cropBitmap(frame, faceInfo.boundingBox) else frame

        // If Background mode: Only use pure dima806 (V1) as requested.
        val v1 = runInference(livenessModelV1, livenessInputsV1, livenessOutputsV1, target, livenessInputSize)
        
        val visualScore = if (isBackground) {
            v1
        } else {
            val v2 = runInference(livenessModelV2, livenessInputsV2, livenessOutputsV2, target, livenessInputSize)
            val spectral = spectralScanner.scan(target)
            if (livenessModelV2 != null) (v1 * 0.3f) + (v2 * 0.5f) + (spectral * 0.2f) else v1
        }

        val temporal = temporalEngine.processFrame(visualScore, faceInfo.jitter)
        val rawScore = (visualScore + temporal.suggestedVerdictAdjustment).coerceIn(0f, 1f)
        
        if (scoreQueue.size >= windowSize) scoreQueue.poll()
        scoreQueue.add(rawScore)
        val finalScore = scoreQueue.average().toFloat()

        DetectionResult(
            trustScore = finalScore, blinkRate = faceInfo.blinkRate, livenessScore = v1,
            mouthMovementScore = faceInfo.mouthMovementScore, faceBounds = faceInfo.boundingBox,
            confidence = v1, isVerified = finalScore > 0.70f,
            explanation = if (faceInfo.boundingBox == null) "Wide-frame scan" else lastExplanation,
            acceleratorUsed = if (isBackground) "Pure ViT" else "Ensemble"
        )
    }

    private fun runInference(m: CompiledModel?, inputs: List<TensorBuffer>?, outputs: List<TensorBuffer>?, bitmap: Bitmap, size: Int): Float {
        if (m == null || inputs == null || outputs == null) return 0.0f
        return try {
            preprocessToBuffer(bitmap, inputs[0], size)
            m.run(inputs, outputs)
            val res = outputs[0].readFloat()
            if (res.size >= 2) {
                val eR = exp(res[0].toDouble()); val eF = exp(res[1].toDouble())
                (eR / (eR + eF)).toFloat().let { if (it < 0.9f) it * 0.7f else it }
            } else 0.0f
        } catch (_: Exception) { 0.0f }
    }

    private fun preprocessToBuffer(bitmap: Bitmap, buffer: TensorBuffer, size: Int) {
        val s = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val p = IntArray(size * size); s.getPixels(p, 0, size, 0, 0, size, size)
        val f = FloatArray(size * size * 3)
        for (i in 0 until size * size) {
            val pix = p[i]
            f[i * 3] = ((pix shr 16 and 0xFF) / 255f - 0.5f) / 0.5f
            f[i * 3 + 1] = ((pix shr 8 and 0xFF) / 255f - 0.5f) / 0.5f
            f[i * 3 + 2] = ((pix and 0xFF) / 255f - 0.5f) / 0.5f
        }
        buffer.writeFloat(f)
    }

    private suspend fun detectFace(bitmap: Bitmap): Rect? = suspendCancellableCoroutine { cont ->
        faceDetector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it.firstOrNull()?.boundingBox) }
            .addOnFailureListener { cont.resume(null) }
    }

    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val x = rect.left.coerceAtLeast(0); val y = rect.top.coerceAtLeast(0)
        val w = rect.width().coerceAtMost(bitmap.width - x); val h = rect.height().coerceAtMost(bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    private fun generateAIExplanation(l: Float, m: Float, w: Boolean, b: Float?, mo: Float?): String {
        return if (l < 0.4f) "Artificial pixel patterns found." else "Pixels appear natural."
    }

    fun close() {
        livenessModelV1?.close(); livenessModelV2?.close(); hrNetModel?.close(); faceDetector.close()
    }
}
