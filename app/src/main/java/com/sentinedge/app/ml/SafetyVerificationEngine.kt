package com.sentinedge.app.ml

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * SentinEdge Safety Engine
 * The "Forensic Firewall" that scans for AI watermarks, C2PA digital signatures,
 * and metadata anomalies before the NPU even touches the pixels.
 */
object SafetyVerificationEngine {

    /**
     * Scans an image/video frame for forensic signatures.
     * Returns a trust score and specific flags for the Reasoning Engine.
     */
    fun verifyImage(context: Context, uri: Uri): VerificationResult {
        val metadataScore = checkMetadata(context, uri)
        val watermarkFlag = scanForWatermarks(context, uri)
        
        // C2PA/Metadata logic:
        // 1. If a known AI signature is found, trust score is slashed to near-zero.
        // 2. If it's a raw camera file with no anomalies, we boost the baseline score.
        var finalScore = metadataScore
        if (watermarkFlag != WatermarkType.NONE) {
            finalScore = 0.1f // Digital proof of AI generation
        }

        return VerificationResult(
            trustScore = finalScore,
            watermarkFound = watermarkFlag != WatermarkType.NONE,
            watermarkType = watermarkFlag,
            metadataSuspicious = metadataScore < 0.6f
        )
    }

    private fun checkMetadata(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE) ?: ""
                val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: ""

                var score = 0.5f // Neutral baseline

                // 1. Scan for AI Software Signatures (DALL-E, Firefly, Midjourney, etc.)
                val aiSoftwareList = listOf(
                    "Midjourney", "DALL-E", "Firefly", "Stable Diffusion", 
                    "AI Generator", "GAN", "Topaz", "Remini"
                )
                if (aiSoftwareList.any { software.contains(it, ignoreCase = true) || comment.contains(it, ignoreCase = true) }) {
                    score -= 0.4f
                }

                // 2. Scan for "Empty" metadata characteristic of web-scraped deepfakes
                if (make.isNullOrBlank() && model.isNullOrBlank() && software.isBlank()) {
                    score -= 0.2f // Suspiciously clean metadata
                }

                // 3. Boost for authentic Camera Hardware data
                if (!make.isNullOrBlank() && !model.isNullOrBlank()) {
                    score += 0.3f
                }

                score.coerceIn(0f, 1f)
            } ?: 0.5f
        } catch (e: Exception) {
            0.5f
        }
    }

    /**
     * Advanced Byte-Level Scanner
     * Looks for C2PA (Content Authenticity), IPTC Digital Source, and SynthID markers.
     */
    private fun scanForWatermarks(context: Context, uri: Uri): WatermarkType {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(16384) // 16KB window
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val content = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)
                    
                    when {
                        // Content Authenticity Initiative / C2PA
                        content.contains("c2pa", ignoreCase = true) || content.contains("jumbf", ignoreCase = true) -> 
                            return WatermarkType.C2PA
                        
                        // Google SynthID or IPTC AI tags
                        content.contains("DigitalSourceType", ignoreCase = true) && content.contains("trainedAlgorithmicMedia", ignoreCase = true) -> 
                            return WatermarkType.IPTC_AI
                        
                        // Adobe Firefly / generative tags
                        content.contains("adobe:authoringTool", ignoreCase = true) && content.contains("generative", ignoreCase = true) -> 
                            return WatermarkType.ADOBE_GEN
                        
                        // Community-known markers
                        content.contains("midjourney", ignoreCase = true) || content.contains("stable-diffusion", ignoreCase = true) -> 
                            return WatermarkType.AI_BRANDED
                    }
                }
                WatermarkType.NONE
            } ?: WatermarkType.NONE
        } catch (e: Exception) {
            WatermarkType.NONE
        }
    }

    enum class WatermarkType {
        NONE,
        C2PA,       // Content Authenticity (Open Standard)
        IPTC_AI,    // Metadata-level AI flag
        ADOBE_GEN,  // Firefly/Adobe Generative AI
        AI_BRANDED  // Explicit brand names in bytes
    }

    data class VerificationResult(
        val trustScore: Float,
        val watermarkFound: Boolean,
        val watermarkType: WatermarkType,
        val metadataSuspicious: Boolean
    )
}
