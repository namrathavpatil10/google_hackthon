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
    val mouthMovementScore: Float? = null, // High = Natural movement, Low = Still/Static
    val jitter: Float? = null // Movement speed/instability of landmarks
)

class BlinkTracker {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    // Rolling window
    private val windowMs = 10_000L
    private val eyeSamples = ArrayDeque<Pair<Long, Float>>()
    private val mouthSamples = ArrayDeque<Pair<Long, Float>>() 
    private val positionHistory = ArrayDeque<Pair<Long, Rect>>()

    suspend fun addFrame(bitmap: Bitmap): FaceInfo {
        val faceData = detectFaceData(bitmap)
        val now = System.currentTimeMillis()

        faceData?.eyeOpenProb?.let { eyeSamples.addLast(now to it) }
        faceData?.mouthRatio?.let { mouthSamples.addLast(now to it) }
        faceData?.boundingBox?.let { positionHistory.addLast(now to it) }

        // Evict old samples
        while (eyeSamples.isNotEmpty() && now - eyeSamples.first().first > windowMs) eyeSamples.removeFirst()
        while (mouthSamples.isNotEmpty() && now - mouthSamples.first().first > windowMs) mouthSamples.removeFirst()
        while (positionHistory.isNotEmpty() && now - positionHistory.first().first > 1000L) positionHistory.removeFirst()

        return FaceInfo(
            boundingBox = faceData?.boundingBox,
            eyeOpenProb = faceData?.eyeOpenProb,
            blinkRate = estimateBlinkRate(),
            mouthMovementScore = estimateMouthMovement(),
            jitter = estimateJitter()
        )
    }

    private fun estimateJitter(): Float? {
        if (positionHistory.size < 3) return null
        
        // Calculate velocity of the bounding box center
        val centers = positionHistory.map { (_, rect) ->
            Pair(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        
        var totalDist = 0f
        for (i in 1 until centers.size) {
            val d = Math.sqrt(
                Math.pow((centers[i].first - centers[i-1].first).toDouble(), 2.0) +
                Math.pow((centers[i].second - centers[i-1].second).toDouble(), 2.0)
            ).toFloat()
            totalDist += d
        }
        
        // Normalize jitter: Deepfakes often have high-frequency micro-shakes
        // If movement is high but duration is short, it's jittery
        return (totalDist / positionHistory.size).coerceIn(0f, 1f)
    }

    private fun estimateBlinkRate(): Float? {
        if (eyeSamples.size < 5) return null
        var blinks = 0
        var prevOpen = true
        for ((_, prob) in eyeSamples) {
            val open = prob > 0.5f
            if (!open && prevOpen) blinks++
            prevOpen = open
        }
        val durationMin = (windowMs / 60_000.0).toFloat()
        return blinks / durationMin
    }

    private fun estimateMouthMovement(): Float? {
        if (mouthSamples.size < 10) return null
        val ratios = mouthSamples.map { it.second }
        val avg = ratios.average()
        val variance = ratios.map { (it - avg) * (it - avg) }.average()
        
        // Artificial deepfakes often have "frozen" mouths or very repetitive micro-fluctuations.
        // If variance is extremely low, it's suspicious.
        return (variance.toFloat() * 1000f).coerceIn(0f, 1f)
    }

    private data class InternalFaceData(
        val boundingBox: Rect, 
        val eyeOpenProb: Float,
        val mouthRatio: Float? = null
    )

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
                    
                    // Mouth landmarks for movement tracking
                    val mouthBottom = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_BOTTOM)
                    val mouthLeft = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT)
                    
                    var ratio: Float? = null
                    if (mouthBottom != null && mouthLeft != null) {
                        val dy = Math.abs(mouthBottom.position.y - mouthLeft.position.y)
                        val dx = Math.abs(mouthBottom.position.x - mouthLeft.position.x)
                        ratio = if (dx != 0f) dy / dx else 0f
                    }

                    val prob = if (left != null && right != null) (left + right) / 2f else null
                    
                    if (prob == null) {
                        cont.resume(null)
                    } else {
                        cont.resume(InternalFaceData(face.boundingBox, prob, ratio))
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }

    fun close() = detector.close()
}

