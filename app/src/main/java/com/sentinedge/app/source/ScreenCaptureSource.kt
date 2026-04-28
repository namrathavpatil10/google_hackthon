package com.sentinedge.app.source

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Captures the device screen via MediaProjection and emits frames as Bitmaps.
 * Plugs directly into the existing FrameSource → DeepfakeDetector pipeline.
 */
class ScreenCaptureSource(
    private val mediaProjection: MediaProjection,
    metrics: DisplayMetrics,
) : FrameSource {

    // Analyse at half resolution for speed; detector rescales to 224×224 anyway
    private val width  = metrics.widthPixels  / 2
    private val height = metrics.heightPixels / 2
    private val dpi    = metrics.densityDpi

    private val _frames = MutableSharedFlow<Bitmap>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override fun frames(): Flow<Bitmap> = _frames.asSharedFlow()

    private val imageReader: ImageReader = ImageReader.newInstance(
        width, height, PixelFormat.RGBA_8888, 2
    )

    private val handlerThread = HandlerThread("ScreenCapture").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private var virtualDisplay: VirtualDisplay? = null
    private var released = false

    init {
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                release()
            }
        }, null)

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane  = image.planes[0]
                val buffer = plane.buffer
                val pixelStride  = plane.pixelStride
                val rowStride    = plane.rowStride
                val rowPadding   = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // Crop out any row-padding columns
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                bitmap.recycle()

                _frames.tryEmit(cropped)
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "SentinEdgeCapture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null, null,
        )
    }

    override fun release() {
        if (released) return
        released = true
        virtualDisplay?.release()
        imageReader.close()
        handlerThread.quitSafely()
        mediaProjection.stop()
    }
}
