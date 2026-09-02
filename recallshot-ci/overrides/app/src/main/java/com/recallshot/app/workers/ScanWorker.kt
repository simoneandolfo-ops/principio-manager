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
    override suspend fun doWork(): Result {
        val full = inputData.getBoolean(KEY_FULL_SCAN, false)
        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.settings.first()

        if ((!settings.autoImportScreenshots && !full) || !MediaPermissions.canRead(applicationContext)) {
            return Result.success()
        }

        if (full) BulkScanState.begin(applicationContext)

        return try {
            val scanStarted = System.currentTimeMillis() / 1000L
            val since = if (full || settings.lastMediaScanSeconds == 0L) {
                null
            } else {
                (settings.lastMediaScanSeconds - 2L).coerceAtLeast(0L)
            }

            val repo = ScreenshotRepository(applicationContext)
            val imported = repo.scanMediaStore(since)

            if (imported > 0 && !full) ScreenshotNotifier.show(applicationContext, imported)
            settingsRepo.setLastScanSeconds(scanStarted)

            if (full) {
                // Producer has definitely finished inserting. Clear the coordination flag
                // BEFORE forcing a new OCR worker so that the consumer can terminate only
                // when the real queue is empty.
                BulkScanState.end(applicationContext)
            }

            if (settings.runOcrAutomatically) {
                if (full) {
                    // Critical 0.2.4 fix: do not use KEEP here. A previous OCR worker can be
                    // alive/finishing while the scan completes, which caused the final kick
                    // to be ignored and left thousands of PENDING screenshots uncatalogued.
                    OcrQueueWorker.restartAfterFullScan(applicationContext)
                } else {
                    repo.enqueuePendingOcr()
                }
            }

            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            // Also clear on cancellation/error. BulkScanState has a stale timeout as a second
            // safety net, but a normal failure must never leave OCR waiting forever.
            if (full) BulkScanState.end(applicationContext)
        }
    }

    companion object {
        const val KEY_FULL_SCAN = "full_scan"
    }
}
