package com.recallshot.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recallshot.app.settings.SettingsRepository
import kotlinx.coroutines.flow.first

class OcrRecoveryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (SettingsRepository(applicationContext).settings.first().runOcrAutomatically) {
            OcrQueueWorker.start(applicationContext)
        }
        return Result.success()
    }
}
