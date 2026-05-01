package com.sentinedge.app.ml

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Handles automatic background downloading of the 3GB Gemma model.
 */
class ModelDownloader(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val gemmaUrl = "https://huggingface.co/qualcomm/Gemma-7b-it-Quantized-TFLite/resolve/main/gemma.litertlm" // Example URL

    fun startDownload(): Long {
        val file = File(context.getExternalFilesDir(null), "gemma.litertlm")
        if (file.exists()) return -1

        val request = DownloadManager.Request(Uri.parse(gemmaUrl))
            .setTitle("SentinEdge AI Reasoning Model")
            .setDescription("Downloading 2.8GB AI model for on-device reasoning...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "gemma.litertlm")
            .setAllowedOverMetered(true) // User choice usually, but keeping it simple
            .setAllowedOverRoaming(false)

        return downloadManager.enqueue(request)
    }

    fun getDownloadProgress(downloadId: Long): Flow<Int> = flow {
        var isDownloading = true
        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                val bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))

                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    isDownloading = false
                }

                if (bytesTotal > 0) {
                    val progress = (bytesDownloaded * 100L / bytesTotal).toInt()
                    emit(progress)
                }
            }
            cursor.close()
            delay(1000)
        }
    }

    fun isModelAvailable(): Boolean {
        return File(context.getExternalFilesDir(null), "gemma.litertlm").exists()
    }
}
