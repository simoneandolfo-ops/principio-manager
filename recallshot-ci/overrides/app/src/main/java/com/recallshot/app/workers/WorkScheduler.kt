package com.recallshot.app.workers

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val MEDIA_TRIGGER_WORK = "recallshot-media-content-trigger-v2"

    fun ensurePeriodicScan(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "recallshot-media-scan",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun ensureMediaTrigger(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MEDIA_TRIGGER_WORK,
            ExistingWorkPolicy.KEEP,
            mediaTriggerRequest()
        )
    }

    fun appendMediaTrigger(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MEDIA_TRIGGER_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            mediaTriggerRequest()
        )
    }

    private fun mediaTriggerRequest(): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addContentUriTrigger(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), true)
                }
            }
            .setTriggerContentUpdateDelay(650, TimeUnit.MILLISECONDS)
            .setTriggerContentMaxDelay(3, TimeUnit.SECONDS)
            .build()
        return OneTimeWorkRequestBuilder<ScanWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ScanWorker.KEY_MEDIA_CHANGE to true, ScanWorker.KEY_REARM_MEDIA_TRIGGER to true))
            .build()
    }

    fun ensureOcrRecovery(context: Context) {
        val request = PeriodicWorkRequestBuilder<OcrRecoveryWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "recallshot-ocr-recovery",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scanAfterMediaChange(context: Context) {
        val wm = WorkManager.getInstance(context)
        val fast = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInitialDelay(650, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ScanWorker.KEY_MEDIA_CHANGE to true))
            .build()
        val confirm = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .setInputData(workDataOf(ScanWorker.KEY_MEDIA_CHANGE to true))
            .build()
        wm.enqueueUniqueWork("recallshot-media-change-fast", ExistingWorkPolicy.REPLACE, fast)
        wm.enqueueUniqueWork("recallshot-media-change-confirm", ExistingWorkPolicy.REPLACE, confirm)
    }

    fun scanNow(context: Context, full: Boolean = false) {
        val builder = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(workDataOf(ScanWorker.KEY_FULL_SCAN to full))
        if (!full) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        WorkManager.getInstance(context).enqueueUniqueWork(
            if (full) "recallshot-full-scan" else "recallshot-scan-now",
            ExistingWorkPolicy.REPLACE,
            builder.build()
        )
    }
}
