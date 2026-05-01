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
 * SentinEdge Deepfake Detector
 * Optimized for Qualcomm NPU using a Dual-Model Ensemble and Temporal Analysis.
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

    // Temporal Engine
    private val temporalEngine = TemporalEngine(windowSize = 20)

    // LLM/VLM Engines
    private var gemmaEngine: Engine? = null
    private var vlmEngine: Engine? = null

    // Primary Qualcomm Models
    private val livenessModelFileV1 = "deepfake_detector_v1.tflite"
    private val livenessModelFileV2 = "deepfake_detector_v2.tflite"
    private val hrNetModelFile = "hrnet_face.tflite"
    private val gemmaModelName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"
    
    private var livenessInputSize = 224 

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    
    private var currentAccelerator = "CPU"
    private var vlmAccelerator = "CPU"
    private var gemmaAccelerator = "CPU"

    // Stability Buffer (5-frame window)
    private val scoreQueue: Queue<Float> = LinkedList()
    private val windowSize = 5
    
    private var frameCount = 0
    private var lastExplanation = "Analysis: Initializing detection..."

    var currentSamplingRate = 100L // Default 10 FPS (100ms)

    private fun configureNativeRuntime(nativeLibraryDir: String) {
        try {
            android.system.Os.setenv("LD_LIBRARY_PATH", nativeLibraryDir, true)
            android.system.Os.setenv("ADSP_LIBRARY_PATH", nativeLibraryDir, true)
        } catch (e: Exception) {
            android.util.Log.w("DeepfakeDetector", "Failed to set native library paths: ${e.message}")
        }
    }

    fun init() {
        val accel = when {
            tryInit(Accelerator.NPU) -> "NPU"
            tryInit(Accelerator.GPU) -> "GPU"
            else -> "CPU"
        }
        currentAccelerator = accel
    }

    fun clearBuffer() {
        scoreQueue.clear()
        temporalEngine.reset()
        frameCount = 0
        lastExplanation = "Analysis: Initializing detection..."
    }

    // Tries to load all models with the given accelerator (NPU → GPU → CPU fallback order).
    // Returns true on success; init() uses the first accelerator that does not throw.
    private fun tryInit(accelerator: Accelerator): Boolean {
        return try {
            val options = CompiledModel.Options(accelerator)
            val libDir = context.applicationInfo.nativeLibraryDir
            val cacheDir = context.cacheDir.path
            
            configureNativeRuntime(libDir)
            
            // 1. Initialize Liveness Models (Ensemble)
            try {
                val lm1 = CompiledModel.create(context.assets, livenessModelFileV1, options)
                livenessModelV1 = lm1
                livenessInputsV1 = lm1.createInputBuffers()
                livenessOutputsV1 = lm1.createOutputBuffers()
            } catch (e: Exception) {
                android.util.Log.e("DeepfakeDetector", "Failed to init V1 model: ${e.message}")
            }

            try {
                val lm2 = CompiledModel.create(context.assets, livenessModelFileV2, options)
                livenessModelV2 = lm2
                livenessInputsV2 = lm2.createInputBuffers()
                livenessOutputsV2 = lm2.createOutputBuffers()
            } catch (e: Exception) {
                android.util.Log.w("DeepfakeDetector", "V2 model not found: ${e.message}")
            }

            // 2. Initialize HRNet Model
            try {
                val hrm = CompiledModel.create(context.assets, hrNetModelFile, options)
                hrNetModel = hrm
                hrNetInputs = hrm.createInputBuffers()
                hrNetOutputs = hrm.createOutputBuffers()
            } catch (e: Exception) {
                android.util.Log.w("DeepfakeDetector", "HRNet model not found: ${e.message}")
            }

            // 3. Optional: Initialize Large Models from Disk (Gemma/VLM)
            val gemmaFile = File(context.getExternalFilesDir(null), gemmaModelName)
            if (gemmaFile.exists()) {
                gemmaAccelerator = tryInitEngine(gemmaFile.absolutePath, libDir, cacheDir, isVlm = false)
            }

            val vlmFile = File(context.getExternalFilesDir(null), "fastvlm.litertlm")
            if (vlmFile.exists()) {
                vlmAccelerator = tryInitEngine(vlmFile.absolutePath, libDir, cacheDir, isVlm = true)
            }
            
            true
        } catch (e: Exception) {
            android.util.Log.e("DeepfakeDetector", "Initialization failed: ${e.message}")
            false
        }
    }

    private fun tryInitEngine(modelPath: String, libDir: String, cacheDir: String, isVlm: Boolean): String {
        // Log libDir usage to ensure native deps are handled
        android.util.Log.d("DeepfakeDetector", "Initializing engine with libDir: $libDir")
        
        val backends = listOf(
            "GPU" to Backend.GPU(),
            "CPU" to Backend.CPU()
        )

        for ((name, backend) in backends) {
            try {
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = if (isVlm) backend else Backend.CPU(),
                    cacheDir = cacheDir
                )
                val engine = Engine(config).apply { initialize() }
                
                if (isVlm) {
                    vlmEngine = engine
                } else {
                    gemmaEngine = engine
                }
                return name
            } catch (e: Exception) {
                android.util.Log.w("DeepfakeDetector", "Failed to initialize engine on $name: ${e.message}")
            }
        }
        return "None"
    }

    // Single-frame image analysis. Trust score = 0.7*visual + 0.3*metadata safety.
    // Watermark detection overrides to 0.1f regardless of visual score.
    suspend fun analyzeImage(uri: Uri, bitmap: Bitmap): DetectionResult = withContext(Dispatchers.Default) {
        val safetyResult = SafetyVerificationEngine.verifyImage(context, uri)
        
        val faceBounds = detectFace(bitmap)
        val targetBitmap = if (faceBounds != null) {
            cropBitmap(bitmap, faceBounds)
        } else {
            bitmap
        }

        // For static images, we use the primary dima806 model (V1) exclusively as requested.
        // This provides the cleanest forensic score for stills without ensemble overhead.
        val visualScore = runInference(livenessModelV1, livenessInputsV1, livenessOutputsV1, targetBitmap, livenessInputSize)
        
        val finalTrustScore = if (safetyResult.watermarkFound) {
            0.1f
        } else if (visualScore < 0.3f) {
            visualScore
        } else {
            (visualScore * 0.7f) + (safetyResult.trustScore * 0.3f)
        }

        val aiExplanation = if (vlmEngine != null) {
            generateVlmReasoning(targetBitmap)
        } else {
            generateAIExplanation(
                visualScore, 
                safetyResult.trustScore, 
                safetyResult.watermarkFound, 
                null, 
                null,
                watermarkType = safetyResult.watermarkType
            )
        }

        DetectionResult(
            trustScore = finalTrustScore,
            blinkRate = null,
            livenessScore = visualScore,
            watermarkFound = safetyResult.watermarkFound,
            watermarkType = safetyResult.watermarkType,
            metadataSuspicious = safetyResult.metadataSuspicious,
            confidence = visualScore,
            isVerified = finalTrustScore > 0.70f,
            explanation = aiExplanation,
            acceleratorUsed = "Vision: $currentAccelerator (dima806 Optimized), AI Reasoning: ${if (vlmEngine != null) "VLM" else "None"}",
            faceBounds = faceBounds
        )
    }

    private suspend fun detectFace(bitmap: Bitmap): Rect? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                cont.resume(faces.firstOrNull()?.boundingBox)
            }
            .addOnFailureListener {
                cont.resume(null)
            }
    }

    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val x = rect.left.coerceAtLeast(0)
        val y = rect.top.coerceAtLeast(0)
        val width = rect.width().coerceAtMost(bitmap.width - x)
        val height = rect.height().coerceAtMost(bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    private suspend fun generateGemmaReasoning(score: Float, blink: Float?, mouth: Float?): String = withContext(Dispatchers.Default) {
        val engine = gemmaEngine ?: return@withContext "AI reasoning engine unavailable."
        return@withContext try {
            val conversation = engine.createConversation()
            val prompt = "You are a deepfake expert. Explain why this video is suspicious. " +
                    "Findings: Liveness Score $score, Blink Rate ${blink ?: "unknown"}, Mouth movement ${mouth ?: "unknown"}."
            val response = conversation.sendMessageAsync(Contents.of(Content.Text(prompt))).last()
            response.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        } catch (e: Exception) {
            "LLM Error: ${e.localizedMessage}"
        }
    }

    private suspend fun generateVlmReasoning(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val engine = vlmEngine ?: return@withContext "AI reasoning engine unavailable."
        return@withContext try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val response = engine.createConversation().sendMessageAsync(Contents.of(
                Content.ImageBytes(stream.toByteArray()),
                Content.Text("Analyze this person for deepfake signs. Be direct.")
            )).last()
            response.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        } catch (e: Exception) {
            "VLM Error: ${e.localizedMessage}"
        }
    }

    // Per-frame video/camera analysis. V1+V2 ensemble if both models loaded, else V1 only.
    // Temporal engine applies a -0.4f penalty when score variance > 0.03 (flicker detection).
    // Final score is a 5-frame rolling average for stability.
    suspend fun analyzeFrame(
        frame: Bitmap, 
        faceInfo: FaceInfo,
        isLive: Boolean = true
    ): DetectionResult = withContext(Dispatchers.Default) {
        // Use isLive to potentially adjust sensitivity or logging
        if (isLive) {
             android.util.Log.v("DeepfakeDetector", "Live frame analysis started")
        }

        val targetBitmap = if (faceInfo.boundingBox != null) {
            cropBitmap(frame, faceInfo.boundingBox)
        } else {
            frame
        }

        val v1Score = runInference(livenessModelV1, livenessInputsV1, livenessOutputsV1, targetBitmap, livenessInputSize)
        val v2Score = runInference(livenessModelV2, livenessInputsV2, livenessOutputsV2, targetBitmap, livenessInputSize)
        val visualScore = if (livenessModelV2 != null) (v1Score * 0.4f) + (v2Score * 0.6f) else v1Score
        
        val temporalInsight = temporalEngine.processFrame(visualScore, faceInfo.jitter)
        val rawScore = (visualScore + temporalInsight.suggestedVerdictAdjustment).coerceIn(0f, 1f)
        
        if (scoreQueue.size >= windowSize) scoreQueue.poll()
        scoreQueue.add(rawScore)
        val finalScore = scoreQueue.average().toFloat()

        updateSamplingRate(finalScore)

        if (frameCount % 60 == 0 || lastExplanation.contains("Initializing")) {
            lastExplanation = if (vlmEngine != null) {
                generateVlmReasoning(targetBitmap)
            } else if (gemmaEngine != null) {
                generateGemmaReasoning(visualScore, faceInfo.blinkRate, faceInfo.mouthMovementScore)
            } else {
                generateAIExplanation(visualScore, 1.0f, false, faceInfo.blinkRate, faceInfo.mouthMovementScore, temporalInsight)
            }
        }
        frameCount++

        DetectionResult(
            trustScore = finalScore,
            blinkRate = faceInfo.blinkRate,
            livenessScore = visualScore,
            mouthMovementScore = faceInfo.mouthMovementScore,
            faceBounds = faceInfo.boundingBox,
            confidence = visualScore,
            isVerified = finalScore > 0.70f,
            explanation = if (faceInfo.boundingBox == null) "Analysis: Wide-frame scanning (no specific face locked)." else lastExplanation,
            acceleratorUsed = "Vision: $currentAccelerator (Ensemble)"
        )
    }

    private fun updateSamplingRate(score: Float) {
        currentSamplingRate = if (score > 0.9f) 200L else if (score < 0.8f) 50L else 100L
    }

    // Rule-based forensic report built from individual signal scores.
    // Used as the reasoning text when no Gemma/VLM engine is loaded.
    private fun generateAIExplanation(
        liveness: Float,
        metadata: Float,
        watermark: Boolean,
        blink: Float?,
        mouth: Float?,
        temporalInsight: TemporalEngine.TemporalInsight? = null,
        watermarkType: SafetyVerificationEngine.WatermarkType = SafetyVerificationEngine.WatermarkType.NONE
    ): String {
        val reasons = mutableListOf<String>()
        
        when (watermarkType) {
            SafetyVerificationEngine.WatermarkType.C2PA -> reasons.add("Verified C2PA Digital Signature: Metadata indicates this content was modified or generated by AI.")
            SafetyVerificationEngine.WatermarkType.ADOBE_GEN -> reasons.add("Adobe Content Credentials: AI Generative tools (Firefly) detected.")
            SafetyVerificationEngine.WatermarkType.IPTC_AI -> reasons.add("IPTC Forensic Flag: File metadata explicitly labels this as 'trainedAlgorithmicMedia'.")
            SafetyVerificationEngine.WatermarkType.AI_BRANDED -> reasons.add("Embedded AI Branding: Hidden text signatures from known AI generators found in file bytes.")
            else -> if (watermark) reasons.add("Digital AI watermark detected.")
        }

        if (liveness < 0.45f) reasons.add("Artificial visual patterns detected.")
        if (metadata < 0.4f) reasons.add("Suspicious metadata.")
        if (blink != null && (blink < 5f || blink > 35f)) reasons.add("Unnatural blinking.")
        if (mouth != null && mouth < 0.2f) reasons.add("Unnatural mouth movement.")
        temporalInsight?.let {
            if (it.coherenceScore < 0.6f) reasons.add("Significant temporal flickering detected.")
            if (it.jitterWarning) reasons.add("Unnatural 'micro-jitters' detected.")
        }

        return if (reasons.isEmpty()) "Analysis: No obvious signs of AI manipulation detected." 
        else "Reasoning: " + reasons.joinToString(" ")
    }

    // Runs TFLite inference and returns a trust score in [0, 1].
    // Model label convention (dima806/deepfake_vs_real_image_detection config.json):
    //   index 0 = Real, index 1 = Fake — softmax applied over both logits.
    // Returns 0.8f stub when the model failed to load.
    private fun runInference(
        m: CompiledModel?, 
        inputs: List<TensorBuffer>?, 
        outputs: List<TensorBuffer>?, 
        bitmap: Bitmap, 
        size: Int
    ): Float {
        if (m == null || inputs == null || outputs == null) return 0.8f
        
        return try {
            preprocessToBuffer(bitmap, inputs[0], size)
            m.run(inputs, outputs)

            val results = outputs[0].readFloat()
            if (results.size >= 2) {
                val expReal = exp(results[0].toDouble())
                val expFake = exp(results[1].toDouble())
                (expReal / (expReal + expFake)).toFloat() // Index 0 = Real
            } else {
                val score = results.getOrElse(0) { 0.8f }
                if (score > 1.0f) score / 255f else score
            }
        } catch (e: Exception) {
            0.8f
        }
    }

    private fun preprocessToBuffer(bitmap: Bitmap, buffer: TensorBuffer, size: Int) {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        val floatArray = FloatArray(size * size * 3)
        for (i in 0 until size * size) {
            val pixel = pixels[i]
            floatArray[i * 3]     = (((pixel shr 16) and 0xFF) / 127.5f - 1.0f)
            floatArray[i * 3 + 1] = (((pixel shr 8)  and 0xFF) / 127.5f - 1.0f)
            floatArray[i * 3 + 2] = ((pixel          and 0xFF) / 127.5f - 1.0f)
        }
        buffer.writeFloat(floatArray)
    }
    
    fun close() {
        livenessModelV1?.close()
        livenessModelV2?.close()
        hrNetModel?.close()
        gemmaEngine?.close()
        vlmEngine?.close()
        faceDetector.close()
    }
}
