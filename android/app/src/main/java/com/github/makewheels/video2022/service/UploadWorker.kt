package com.github.makewheels.video2022.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSStsTokenCredentialProvider
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import com.github.makewheels.video2022.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_FILE_URI = "fileUri"
        const val KEY_FILE_ID = "fileId"
        const val KEY_VIDEO_ID = "videoId"
        const val KEY_BUCKET = "bucket"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_OBJECT_KEY = "key"
        const val KEY_ACCESS_KEY_ID = "accessKeyId"
        const val KEY_SECRET_KEY = "secretKey"
        const val KEY_SESSION_TOKEN = "sessionToken"
        const val KEY_PROGRESS = "progress"
        const val CHANNEL_ID = "upload_channel"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val fileUri = inputData.getString(KEY_FILE_URI) ?: return@withContext Result.failure()
        val fileId = inputData.getString(KEY_FILE_ID) ?: return@withContext Result.failure()
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val bucket = inputData.getString(KEY_BUCKET) ?: return@withContext Result.failure()
        val endpoint = inputData.getString(KEY_ENDPOINT) ?: return@withContext Result.failure()
        val objectKey = inputData.getString(KEY_OBJECT_KEY) ?: return@withContext Result.failure()
        val accessKeyId = inputData.getString(KEY_ACCESS_KEY_ID) ?: return@withContext Result.failure()
        val secretKey = inputData.getString(KEY_SECRET_KEY) ?: return@withContext Result.failure()
        val sessionToken = inputData.getString(KEY_SESSION_TOKEN) ?: return@withContext Result.failure()

        try {
            createNotificationChannel()
            setForeground(createForegroundInfo(0))

            val credentialProvider = OSSStsTokenCredentialProvider(accessKeyId, secretKey, sessionToken)
            val ossClient = OSSClient(applicationContext, endpoint, credentialProvider)

            val uri = android.net.Uri.parse(fileUri)
            val inputStream = applicationContext.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure()
            val bytes = inputStream.readBytes()
            inputStream.close()

            val putRequest = PutObjectRequest(bucket, objectKey, bytes)
            putRequest.setProgressCallback { _, currentSize, totalSize ->
                val progress = if (totalSize > 0) currentSize.toFloat() / totalSize else 0f
                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
            }

            ossClient.putObject(putRequest)

            notifyUploadFinish(fileId, videoId)

            setForeground(createForegroundInfo(100))
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun notifyUploadFinish(fileId: String, videoId: String) {
        try {
            val baseUrl = com.github.makewheels.video2022.BuildConfig.API_BASE_URL
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val api = retrofit.create(com.github.makewheels.video2022.data.api.VideoApi::class.java)
            api.uploadFinish(fileId)
            api.rawFileUploadFinish(videoId)
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "上传", NotificationManager.IMPORTANCE_LOW)
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("正在上传视频")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
