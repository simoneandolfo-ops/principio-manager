package com.recallshot.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recallshot.app.data.ScreenshotRepository
import com.recallshot.app.settings.SettingsRepository
import com.recallshot.app.permissions.MediaPermissions
import com.recallshot.app.notifications.ScreenshotNotifier
import kotlinx.coroutines.flow.first

class ScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.settings.first()
        val full = inputData.getBoolean(KEY_FULL_SCAN, false)
        if ((!settings.autoImportScreenshots && !full) || !MediaPermissions.canRead(applicationContext)) return Result.success()
        val scanStarted = System.currentTimeMillis() / 1000L
        val since = if (full || settings.lastMediaScanSeconds == 0L) null else (settings.lastMediaScanSeconds - 2L).coerceAtLeast(0L)
        val repo = ScreenshotRepository(applicationContext)
        val imported = repo.scanMediaStore(since)
        if (imported > 0 && !full) ScreenshotNotifier.show(applicationContext, imported)
        settingsRepo.setLastScanSeconds(scanStarted)
        if (settings.runOcrAutomatically) repo.enqueuePendingOcr()
        Result.success()
    } catch (_: SecurityException) { Result.success() } catch (_: Throwable) { Result.retry() }
    companion object { const val KEY_FULL_SCAN = "full_scan" }
}
