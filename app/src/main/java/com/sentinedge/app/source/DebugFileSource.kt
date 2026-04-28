package com.sentinedge.app.source

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Extracts frames from a video URI (file picker or assets) and emits them as bitmaps.
 * Samples every [sampleEveryNthFrame]-th frame to match live-camera throughput.
 */
class DebugFileSource(
    private val context: Context,
    private val videoUri: Uri,
    private val sampleEveryNthFrame: Int = 3,
    private val targetFrameDelayMs: Long = 100L,  // ~10 fps analysis rate
) : FrameSource {

    private val retriever = MediaMetadataRetriever()
    private var released = false

    override fun frames(): Flow<Bitmap> = flow {
        retriever.setDataSource(context, videoUri)

        val durationUs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLong()
            ?.times(1_000L)   // ms → µs
            ?: return@flow

        val videoFrameRate = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
            ?.toInt()
            ?: 30

        val frameStepUs = (1_000_000L / videoFrameRate) * sampleEveryNthFrame
        var timeUs = 0L

        while (!released && timeUs < durationUs) {
            val frame = retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            if (frame != null) {
                emit(frame)
            }
            timeUs += frameStepUs
            delay(targetFrameDelayMs)
        }
    }.flowOn(Dispatchers.IO)

    override fun release() {
        released = true
        retriever.release()
    }
}
