package com.recallshot.app.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkScheduler {
    fun ensurePeriodicScan(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "recallshot-media-scan",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun ensureOcrRecovery(context: Context) {
        val request = PeriodicWorkRequestBuilder<OcrRecoveryWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "recallshot-ocr-recovery",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scanNow(context: Context, full: Boolean = false) {
        val builder = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(workDataOf(ScanWorker.KEY_FULL_SCAN to full))
        if (!full) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        val request = builder.build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            if (full) "recallshot-full-scan" else "recallshot-scan-now",
            if (full) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }
}
