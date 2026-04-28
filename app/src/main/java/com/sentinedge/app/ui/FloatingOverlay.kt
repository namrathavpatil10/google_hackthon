package com.sentinedge.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.sentinedge.app.Verdict

class FloatingOverlay(private val context: Context) {

    private val TAG = "FloatingOverlay"
    private val wm   = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var attached = false

    private val bg = GradientDrawable().apply {
        cornerRadius = px(50).toFloat()
        setColor(Color.parseColor("#6C63FF"))
    }

    private val label = TextView(context).apply {
        setPadding(px(18), px(10), px(18), px(10))
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text = "● SentinEdge"
        background = bg
        elevation = 8f
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = px(12)
        y = px(80)
    }

    fun show() {
        if (attached) return
        main.post {
            Log.d(TAG, "Adding overlay view to WindowManager")
            wm.addView(label, params)   // let it crash visibly if permission missing
            attached = true
            setupDrag()
            Log.d(TAG, "Overlay attached successfully")
        }
    }

    fun update(result: com.sentinedge.app.ml.DetectionResult, verdict: Verdict, blinkRate: Float?) {
        val pct = (result.trustScore * 100).toInt()
        val (text, color) = when (verdict) {
            Verdict.REAL       -> "✓  $pct%  Real"       to Color.parseColor("#2E7D32")
            Verdict.SUSPICIOUS -> {
                val reason = if (result.confidence < 0.4f) " (Unsure)" else ""
                "⚠  $pct%  Suspicious$reason" to Color.parseColor("#E65100")
            }
            Verdict.DEEPFAKE   -> {
                val note = blinkRate?.let { " · blink ${it.toInt()}/min" } ?: ""
                "✕  DEEPFAKE DETECTED$note" to Color.parseColor("#B71C1C")
            }
        }
        main.post {
            if (!attached) return@post
            label.text = text
            bg.setColor(color)
            try { wm.updateViewLayout(label, params) } catch (e: Exception) {
                Log.e(TAG, "updateViewLayout failed: ${e.message}")
            }
        }
    }

    fun hide() {
        main.post {
            if (attached) {
                try { wm.removeView(label) } catch (e: Exception) {
                    Log.e(TAG, "removeView failed: ${e.message}")
                }
                attached = false
            }
        }
    }

    private fun setupDrag() {
        var startX = 0; var startY = 0
        var rawX = 0f;  var rawY = 0f
        label.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    rawX = ev.rawX;    rawY = ev.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (rawX - ev.rawX).toInt()
                    params.y = startY + (ev.rawY - rawY).toInt()
                    try { wm.updateViewLayout(label, params) } catch (_: Exception) {}
                }
            }
            true
        }
    }

    private fun px(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()
}
