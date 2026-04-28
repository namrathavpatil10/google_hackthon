package com.sentinedge.app.source

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

interface FrameSource {
    /** Emits decoded RGB [Bitmap] frames at the configured rate. */
    fun frames(): Flow<Bitmap>
    fun release()
}
