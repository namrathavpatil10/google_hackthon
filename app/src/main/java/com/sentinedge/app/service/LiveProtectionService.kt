package com.sentinedge.app.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.sentinedge.app.R
import com.sentinedge.app.ml.DeepfakeDetector
import com.sentinedge.app.ml.FaceInfo
import kotlinx.coroutines.*

/**
 * Foreground service that provides background deepfake protection
 * via Screen Capture and a floating overlay.
 */
class LiveProtectionService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var statusText: TextView? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private lateinit var detector: DeepfakeDetector
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        Log.d("LiveProtectionService", "Service Created")
        detector = DeepfakeDetector(this).apply { init() }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LiveProtectionService", "onStartCommand received")
        
        val projectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("projection_data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("projection_data")
        }

        if (projectionData != null) {
            val mpManager = getSystemService(MediaProjectionManager::class.java)
            // Use Activity.RESULT_OK instead of -1 for standard compliance
            mediaProjection = mpManager.getMediaProjection(Activity.RESULT_OK, projectionData)
            
            if (mediaProjection != null) {
                showOverlay()
                startScreenCapture()
            } else {
                Log.e("LiveProtectionService", "Failed to create MediaProjection")
            }
        } else {
            Log.e("LiveProtectionService", "No projection data found in intent")
        }
        
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 150 
        }

        // Create a clear status card overlay
        overlayView = TextView(this).apply {
            text = "SECURE ✓"
            setTextColor(0xFF4CAF50.toInt())
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(40, 20, 40, 20)
            gravity = Gravity.CENTER
            
            // Add a high-contrast dark background with purple border
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xEE000000.toInt())
                cornerRadius = 30f
                setStroke(3, 0xFF7C6FFF.toInt())
            }
            background = drawable
        }
        
        statusText = overlayView as TextView
        windowManager.addView(overlayView, layoutParams)
        Log.d("LiveProtectionService", "Overlay added to WindowManager (High Contrast)")
    }

    private fun startScreenCapture() {
        val metrics = resources.displayMetrics
        val width = 480 // Low res for faster analysis
        val height = (metrics.heightPixels * (width.toFloat() / metrics.widthPixels)).toInt()
        
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "SentinEdge-Capture",
            width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            
            serviceScope.launch(Dispatchers.Default) {
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    // Basic analysis: Run detector on screen frame
                    val result = detector.analyzeFrame(bitmap, FaceInfo(null, null, null), isLive = true)
                    
                    withContext(Dispatchers.Main) {
                        updateStatus(result.trustScore)
                    }
                } catch (e: Exception) {
                    Log.e("LiveProtectionService", "Analysis failed", e)
                    image.close()
                }
            }
        }, null)
    }

    private fun updateStatus(score: Float) {
        statusText?.let {
            if (score > 0.7f) {
                it.text = "SECURE ✓"
                it.setTextColor(0xFF4CAF50.toInt())
            } else if (score > 0.4f) {
                it.text = "SUSPICIOUS ⚠"
                it.setTextColor(0xFFFFC107.toInt())
            } else {
                it.text = "FAKE DETECTED ✕"
                it.setTextColor(0xFFFF5252.toInt())
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "live_protection"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Live Protection", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SentinEdge Active")
            .setContentText("Monitoring screen for deepfake artifacts.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LiveProtectionService", "Service Destroyed")
        serviceScope.cancel()
        overlayView?.let { windowManager.removeView(it) }
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        detector.close()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
