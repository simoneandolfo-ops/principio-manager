package com.recallshot.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recallshot.app.data.ScreenshotRepository
import com.recallshot.app.notifications.ScreenshotNotifier
import com.recallshot.app.permissions.MediaPermissions
import com.recallshot.app.settings.SettingsRepository
import kotlinx.coroutines.flow.first

class ScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.settings.first()
        val full = inputData.getBoolean(KEY_FULL_SCAN, false)
        val rearmTrigger = inputData.getBoolean(KEY_REARM_MEDIA_TRIGGER, false)

        if ((!settings.autoImportScreenshots && !full) || !MediaPermissions.canRead(applicationContext)) {
            if (rearmTrigger) WorkScheduler.appendMediaTrigger(applicationContext)
            return Result.success()
        }

        val scanStarted = System.currentTimeMillis() / 1000L
        val since = if (full || settings.lastMediaScanSeconds == 0L) null
        else (settings.lastMediaScanSeconds - 10L).coerceAtLeast(0L)

        val repo = ScreenshotRepository(applicationContext)
        val imported = repo.scanMediaStore(since)
        if (imported > 0 && !full) ScreenshotNotifier.show(applicationContext, imported)
        settingsRepo.setLastScanSeconds(scanStarted)
        if (settings.runOcrAutomatically) repo.enqueuePendingOcr()

        if (rearmTrigger) WorkScheduler.appendMediaTrigger(applicationContext)
        Result.success()
    } catch (_: SecurityException) {
        if (inputData.getBoolean(KEY_REARM_MEDIA_TRIGGER, false)) {
            WorkScheduler.appendMediaTrigger(applicationContext)
        }
        Result.success()
    } catch (_: Throwable) {
        Result.retry()
    }

    companion object {
        const val KEY_FULL_SCAN = "full_scan"
        const val KEY_MEDIA_CHANGE = "media_change"
        const val KEY_REARM_MEDIA_TRIGGER = "rearm_media_trigger"
    }
}
