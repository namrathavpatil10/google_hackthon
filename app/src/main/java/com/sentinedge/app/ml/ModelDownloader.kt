package com.sentinedge.app.ml

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class ModelDownloader(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val gemmaFileName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"
    private val gemmaUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_qualcomm_sm8750.litertlm"

    fun startDownload(): Long {
        val file = File(context.getExternalFilesDir(null), gemmaFileName)
        if (file.exists()) return -1

        val request = DownloadManager.Request(Uri.parse(gemmaUrl))
            .setTitle("SentinEdge AI Reasoning Model")
            .setDescription("Downloading Gemma 4 2B model (~2GB) for AI reasoning...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, gemmaFileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        return downloadManager.enqueue(request)
    }

    fun getDownloadProgress(downloadId: Long): Flow<Int> = flow {
        emit(0)
        var isDownloading = true
        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> { emit(100); isDownloading = false }
                    DownloadManager.STATUS_FAILED -> isDownloading = false
                    else -> emit(if (bytesTotal > 0) (bytesDownloaded * 100L / bytesTotal).toInt() else 0)
                }
            } else {
                cursor.close()
                break
            }
            cursor.close()
            delay(1000)
        }
    }

    fun isModelAvailable(): Boolean {
        return File(context.getExternalFilesDir(null), gemmaFileName).exists()
    }
}
