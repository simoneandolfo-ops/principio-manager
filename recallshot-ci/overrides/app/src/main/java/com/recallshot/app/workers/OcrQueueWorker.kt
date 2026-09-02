package com.recallshot.app.workers

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.recallshot.app.data.RecallShotDatabase
import com.recallshot.app.ocr.OcrProcessor
import com.recallshot.app.settings.SettingsRepository
import com.recallshot.core.LocalClassifier
import com.recallshot.core.MetadataExtractor
import com.recallshot.core.TitleGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class OcrQueueWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository(applicationContext)
        if (!settingsRepo.settings.first().runOcrAutomatically) return Result.success()

        val dao = RecallShotDatabase.get(applicationContext).screenshotDao()
        val processor = OcrProcessor(applicationContext)
        val classifier = LocalClassifier()
        val startedAt = SystemClock.elapsedRealtime()

        while (!isStopped && SystemClock.elapsedRealtime() - startedAt < MAX_RUN_MS) {
            // Intentionally ONE row at a time. ML Kit must finish and the DB must be
            // updated before the next screenshot is even fetched.
            val entity = dao.pendingOcr(1).firstOrNull()

            if (entity == null) {
                if (BulkScanState.isActive(applicationContext)) {
                    delay(EMPTY_QUEUE_WAIT_MS)
                    continue
                }
                return Result.success()
            }

            if (isStopped) {
                appendContinuation(applicationContext)
                return Result.success()
            }

            val originalStatus = entity.ocrStatus
            dao.update(entity.copy(ocrStatus = "PROCESSING"))

            try {
                val text = processor.read(entity)

                // A zero-text result is valid for image-only screenshots. It is not an OCR
                // exception, so we finish the row instead of hammering ML Kit forever.
                val title = TitleGenerator.generate(text, entity.displayName.ifBlank { "Screenshot" })
                val classification = classifier.classify(title, text, entity.sourceApp)
                val meta = MetadataExtractor.extract(text)
                val description = buildList {
                    meta.prices.firstOrNull()?.let { add(it) }
                    meta.flightCodes.firstOrNull()?.let { add("Volo $it") }
                    meta.urls.firstOrNull()?.let { add(it) }
                    meta.dates.firstOrNull()?.let { add(it) }
                }.joinToString(" · ").take(180)

                dao.update(
                    entity.copy(
                        title = title,
                        description = description,
                        ocrText = text,
                        category = classification.category.name,
                        confidence = classification.confidence,
                        ocrStatus = "DONE"
                    )
                )

                // Deliberate throttle: on a 5k library we prefer stable OCR throughput to
                // racing through MediaStore and ML Kit. This also gives Android time to
                // release image/native resources between recognitions.
                delay(BETWEEN_IMAGES_MS)
            } catch (_: SecurityException) {
                dao.update(entity.copy(ocrStatus = "PERMISSION"))
                delay(ERROR_BACKOFF_MS)
            } catch (_: Throwable) {
                // PENDING/PROCESSING gets one later recovery attempt. An item that had
                // already failed once becomes terminal FAILED, so a corrupt file cannot
                // monopolize the sequential queue.
                val nextStatus = if (originalStatus == "ERROR") "FAILED" else "ERROR"
                dao.update(entity.copy(ocrStatus = nextStatus))
                delay(ERROR_BACKOFF_MS)
            }
        }

        if (settingsRepo.settings.first().runOcrAutomatically &&
            (BulkScanState.isActive(applicationContext) || dao.retryableOcrCount() > 0)
        ) {
            appendContinuation(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "recallshot-ocr-queue-v5"
        private const val MAX_RUN_MS = 7 * 60 * 1000L
        private const val EMPTY_QUEUE_WAIT_MS = 1200L
        private const val BETWEEN_IMAGES_MS = 350L
        private const val ERROR_BACKOFF_MS = 900L

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<OcrQueueWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun restartAfterFullScan(context: Context) {
            val request = OneTimeWorkRequestBuilder<OcrQueueWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun appendContinuation(context: Context) {
            val request = OneTimeWorkRequestBuilder<OcrQueueWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
