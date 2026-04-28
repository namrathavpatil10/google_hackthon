package com.sentinedge.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class FaceInfo(
    val boundingBox: Rect?,
    val eyeOpenProb: Float?,
    val blinkRate: Float?,
)

class BlinkTracker {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    // Rolling window: stores eye-open probability per frame (1.0 = eyes open)
    private val windowMs = 10_000L
    private val samples = ArrayDeque<Pair<Long, Float>>()  // timestamp → avg eye open prob

    suspend fun addFrame(bitmap: Bitmap): FaceInfo {
        val faceData = detectFaceData(bitmap)
        val now = System.currentTimeMillis()

        faceData?.eyeOpenProb?.let { prob ->
            samples.addLast(now to prob)
        }

        // Evict samples older than the window
        while (samples.isNotEmpty() && now - samples.first().first > windowMs) {
            samples.removeFirst()
        }

        return FaceInfo(
            boundingBox = faceData?.boundingBox,
            eyeOpenProb = faceData?.eyeOpenProb,
            blinkRate = estimateBlinkRate()
        )
    }

    private fun estimateBlinkRate(): Float? {
        if (samples.size < 5) return null
        var blinks = 0
        var prevOpen = true
        for ((_, prob) in samples) {
            val open = prob > 0.5f
            if (!open && prevOpen) blinks++
            prevOpen = open
        }
        val durationMin = (windowMs / 60_000.0).toFloat()
        return blinks / durationMin
    }

    private data class InternalFaceData(val boundingBox: Rect, val eyeOpenProb: Float)

    private suspend fun detectFaceData(bitmap: Bitmap): InternalFaceData? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val face = faces.firstOrNull()
                    if (face == null) {
                        cont.resume(null)
                        return@addOnSuccessListener
                    }

                    val left = face.leftEyeOpenProbability
                    val right = face.rightEyeOpenProbability
                    
                    if (left == null || right == null) {
                        cont.resume(null)
                        return@addOnSuccessListener
                    }

                    val prob = (left + right) / 2f
                    cont.resume(InternalFaceData(face.boundingBox, prob))
                }
                .addOnFailureListener { cont.resume(null) }
        }

    fun close() = detector.close()
}
