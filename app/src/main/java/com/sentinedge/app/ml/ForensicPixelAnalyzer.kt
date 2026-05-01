package com.sentinedge.app.ml

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import kotlin.math.abs

/**
 * SentinEdge Forensic Pixel Analyzer
 * Implements "Error Level Analysis" (ELA) and Visual Watermark scanning.
 * This is the easiest, deterministic way to catch deepfake artifacts at the pixel level.
 */
object ForensicPixelAnalyzer {

    /**
     * Performs ELA by re-compressing the frame and finding differences.
     * High ELA error = Likely manipulated/Deepfake pixels.
     */
    fun analyzeELA(original: Bitmap): Float {
        val stream = ByteArrayOutputStream()
        original.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val jpegBytes = stream.toByteArray()
        val decompressed = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        var totalError = 0L
        val width = original.width
        val height = original.height
        val step = 4
        var count = 0
        
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val p1 = original.getPixel(x, y)
                val p2 = decompressed.getPixel(x, y)
                totalError += (abs(Color.red(p1) - Color.red(p2)) + 
                              abs(Color.green(p1) - Color.green(p2)) + 
                              abs(Color.blue(p1) - Color.blue(p2)))
                count++
            }
        }
        return (totalError.toFloat() / (count * 10f)).coerceIn(0f, 1f)
    }

    /**
     * Scans for visual signatures of common AI generators in corners.
     */
    fun scanVisualWatermarks(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val regions = listOf(
            Pair(w - 100, h - 100), // Bottom Right (Most common)
            Pair(0, h - 100),       // Bottom Left
            Pair(w - 100, 0)        // Top Right
        )
        for (r in regions) {
            if (isUnnaturalRegion(bitmap, r.first, r.second)) return true
        }
        return false
    }

    private fun isUnnaturalRegion(bitmap: Bitmap, x: Int, y: Int): Boolean {
        val size = 50
        if (x + size >= bitmap.width || y + size >= bitmap.height) return false
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, x, y, size, size)
        val lumas = pixels.map { p -> (Color.red(p) + Color.green(p) + Color.blue(p)) / 3f }
        val mean = lumas.average()
        val variance = lumas.map { (it - mean) * (it - mean) }.average()
        return variance > 1500.0 // AI Logos/Watermarks have very sharp edges (high variance)
    }
}
