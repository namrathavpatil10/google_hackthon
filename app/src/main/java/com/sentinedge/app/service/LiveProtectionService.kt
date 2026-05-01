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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.sentinedge.app.R
import com.sentinedge.app.ml.ForensicPixelAnalyzer
import kotlinx.coroutines.*

/**
 * SentinEdge Forensic Guardian (REWRITTEN - ORIGIN MAIN BRANCH STYLE)
 * Uses Pixel-Level Error Level Analysis (ELA) and Visual Watermark Scanning.
 * This is the most reliable way to catch AI content on YouTube/Screen.
 */
class LiveProtectionService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var statusText: TextView? = null
    private var artifactText: TextView? = null
    
    private var isDetecting = false
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resCode = intent?.getIntExtra("projection_result_code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("projection_data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("projection_data")
        }

        if (data != null && resCode == Activity.RESULT_OK) {
            startForeground(2025, createNotification())
            val mpManager = getSystemService(MediaProjectionManager::class.java)
            mediaProjection = mpManager.getMediaProjection(resCode, data)
            showOverlay()
        } else { stopSelf() }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT; height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START; x = 100; y = 400
        }

        val container = object : LinearLayout(this) {
            override fun performClick(): Boolean = super.performClick()
        }.apply {
            orientation = VERTICAL; gravity = Gravity.CENTER; setPadding(40, 40, 40, 40)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xEE1A1A3A.toInt()); cornerRadius = 50f; setStroke(3, 0xFF7C6FFF.toInt())
            }
        }

        statusText = TextView(this).apply {
            text = "🛡️ PIXEL GUARD READY"; setTextColor(0xFF4FC3F7.toInt()); textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 5); gravity = Gravity.CENTER
        }
        container.addView(statusText)

        artifactText = TextView(this).apply {
            text = "SCANNING FOR DNA ARTIFACTS"; setTextColor(android.graphics.Color.GRAY); textSize = 10f
            setPadding(0, 0, 0, 25); gravity = Gravity.CENTER
        }
        container.addView(artifactText)

        val btnRow = LinearLayout(this).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        val startBtn = Button(this).apply {
            text = "SCAN"; textSize = 11f; setTextColor(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF4CAF50.toInt()); cornerRadius = 15f }
            setOnClickListener { if (!isDetecting) startPullLoop() }
        }
        val stopBtn = Button(this).apply {
            text = "STOP"; textSize = 11f; setTextColor(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFFFC107.toInt()); cornerRadius = 15f }
            setOnClickListener { stopPullLoop() }
        }
        val exitBtn = Button(this).apply {
            text = "X"; textSize = 12f; setTextColor(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFFF5252.toInt()); cornerRadius = 40f }
            setOnClickListener { stopSelf() }
        }

        btnRow.addView(startBtn, LinearLayout.LayoutParams(160, 90).apply { setMargins(8, 0, 8, 0) })
        btnRow.addView(stopBtn, LinearLayout.LayoutParams(160, 90).apply { setMargins(8, 0, 8, 0) })
        btnRow.addView(exitBtn, LinearLayout.LayoutParams(90, 90).apply { setMargins(8, 0, 8, 0) })
        container.addView(btnRow)

        container.setOnTouchListener(object : View.OnTouchListener {
            private var iX = 0; private var iY = 0; private var tX = 0f; private var tY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { iX = lp.x; iY = lp.y; tX = event.rawX; tY = event.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = iX + (event.rawX - tX).toInt(); lp.y = iY + (event.rawY - tY).toInt()
                        windowManager.updateViewLayout(container, lp); return true
                    }
                    MotionEvent.ACTION_UP -> { v.performClick(); return true }
                }
                return false
            }
        })
        overlayView = container; windowManager.addView(overlayView, lp)
    }

    private fun startPullLoop() {
        isDetecting = true
        statusText?.text = "🔬 ANALYZING PIXELS..."
        
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(480, 800, PixelFormat.RGBA_8888, 1)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Forensic-Pull", 480, 800, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
        )

        serviceScope.launch {
            while (isDetecting) {
                val image = try { imageReader?.acquireLatestImage() } catch (_: Exception) { null }
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bitmap = Bitmap.createBitmap(480, 800, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    // ELA (Error Level Analysis) - Best way to see pixel-level forgery
                    val ela = ForensicPixelAnalyzer.analyzeELA(bitmap)
                    val watermark = ForensicPixelAnalyzer.scanVisualWatermarks(bitmap)

                    withContext(Dispatchers.Main) {
                        if (watermark) {
                            statusText?.text = "🚫 FAKE: AI LOGO FOUND"; statusText?.setTextColor(android.graphics.Color.RED)
                        } else if (ela > 0.5f) {
                            statusText?.text = "⚠️ HIGH PIXEL ERROR"; statusText?.setTextColor(android.graphics.Color.YELLOW)
                        } else {
                            statusText?.text = "🛡️ PIXELS AUTHENTIC"; statusText?.setTextColor(android.graphics.Color.GREEN)
                        }
                    }
                }
                delay(1500) // Paced pull for stability
            }
        }
    }

    private fun stopPullLoop() {
        isDetecting = false; virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        statusText?.text = "🛡️ GUARD PAUSED"; statusText?.setTextColor(0xFF4FC3F7.toInt())
    }

    private fun createNotification(): Notification {
        val chanId = "pixel_guard"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(chanId, "Pixel Guard", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        }
        return NotificationCompat.Builder(this, chanId).setContentTitle("SentinEdge Pixel Guard Active")
            .setContentText("Scanning screen DNA...").setSmallIcon(android.R.drawable.ic_secure).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel(); stopPullLoop(); mediaProjection?.stop()
        overlayView?.let { windowManager.removeView(it) }
    }
}
