package com.sentinedge.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sentinedge.app.MainActivity
import com.sentinedge.app.ml.BlinkTracker
import com.sentinedge.app.ml.DeepfakeDetector
import com.sentinedge.app.source.ScreenCaptureSource
import com.sentinedge.app.toVerdict
import com.sentinedge.app.ui.FloatingOverlay
import kotlinx.coroutines.*

class DeepfakeOverlayService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.sentinedge.app.STOP_OVERLAY"
        private const val CHANNEL_ID = "sentinedge_protection"
        private const val NOTIF_ID   = 1001
        private const val TAG = "DeepfakeOverlaySvc"

        fun buildIntent(context: Context, resultCode: Int, data: Intent) =
            Intent(context, DeepfakeOverlayService::class.java).also {
                it.putExtra(EXTRA_RESULT_CODE, resultCode)
                it.putExtra(EXTRA_RESULT_DATA, data)
            }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var mediaProjection: MediaProjection? = null
    private var captureSource: ScreenCaptureSource? = null
    private var detector: DeepfakeDetector? = null
    private var blinkTracker: BlinkTracker? = null
    private var overlay: FloatingOverlay? = null

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { stopSelf() }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            IntentFilter(ACTION_STOP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        @Suppress("DEPRECATION")
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        Log.d(TAG, "resultCode=$resultCode resultData=$resultData")
        if (resultCode == Int.MIN_VALUE || resultData == null) {
            Log.e(TAG, "Missing MediaProjection data — stopping")
            stopSelf(); return START_NOT_STICKY
        }

        try {
            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            mediaProjection = projection
            Log.d(TAG, "MediaProjection obtained: $projection")

            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                metrics.widthPixels  = bounds.width()
                metrics.heightPixels = bounds.height()
                metrics.densityDpi   = resources.displayMetrics.densityDpi
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getMetrics(metrics)
            }
            Log.d(TAG, "Screen: ${metrics.widthPixels}×${metrics.heightPixels} dpi=${metrics.densityDpi}")

            val source = ScreenCaptureSource(projection, metrics)
            captureSource = source
            Log.d(TAG, "ScreenCaptureSource created")

            val det = DeepfakeDetector(this).also { it.init() }
            detector = det
            Log.d(TAG, "DeepfakeDetector initialised")

            val blink = BlinkTracker()
            blinkTracker = blink
            Log.d(TAG, "BlinkTracker created")

            val ov = FloatingOverlay(this)
            overlay = ov
            Log.d(TAG, "Calling overlay.show()")
            ov.show()
            Log.d(TAG, "overlay.show() returned")

            serviceScope.launch {
                source.frames().collect { frame ->
                    val faceInfo  = blink.addFrame(frame)
                    val result    = det.analyze(frame, faceInfo.boundingBox)
                    val combined  = combineSignals(result.trustScore, faceInfo.blinkRate)
                    val verdict   = combined.toVerdict(result.confidence)

                    withContext(Dispatchers.Main) {
                        ov.update(result.copy(trustScore = combined), verdict, faceInfo.blinkRate)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onStartCommand", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        captureSource?.release()
        detector?.close()
        blinkTracker?.close()
        overlay?.hide()
        unregisterReceiver(stopReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun combineSignals(modelScore: Float, blinkRate: Float?): Float {
        val baseScore = modelScore.coerceIn(0f, 1f)
        if (blinkRate == null) return baseScore

        val blinkPenalty = when {
            blinkRate < 4f   -> 0.80f
            blinkRate < 8f   -> 0.92f
            blinkRate <= 30f -> 1.0f
            else             -> 0.90f
        }
        return (baseScore * blinkPenalty).coerceIn(0f, 1f)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SentinEdge Active")
            .setContentText("Monitoring your screen for deepfakes")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SentinEdge Protection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Active deepfake monitoring" }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
