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
import kotlinx.coroutines.yield

class OcrQueueWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository(applicationContext)
        if (!settingsRepo.settings.first().runOcrAutomatically) return Result.success()

        val dao = RecallShotDatabase.get(applicationContext).screenshotDao()
        val processor = OcrProcessor(applicationContext)
        val classifier = LocalClassifier()
        val startedAt = SystemClock.elapsedRealtime()

        while (!isStopped && SystemClock.elapsedRealtime() - startedAt < MAX_RUN_MS) {
            val batch = dao.pendingOcr(BATCH_SIZE)

            if (batch.isEmpty()) {
                // During a 5k+ full scan the producer can temporarily have no rows ready
                // while it is still inserting the next group. Do not interpret that gap as
                // "cataloguing finished". Stay alive until the producer explicitly finishes.
                if (BulkScanState.isActive(applicationContext)) {
                    delay(EMPTY_QUEUE_WAIT_MS)
                    continue
                }
                return Result.success()
            }

            for (entity in batch) {
                if (isStopped) {
                    appendContinuation(applicationContext)
                    return Result.success()
                }

                try {
                    val text = processor.read(entity)
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
                } catch (_: SecurityException) {
                    dao.update(entity.copy(ocrStatus = "PERMISSION"))
                } catch (_: Throwable) {
                    // Fresh PENDING gets one controlled retry. Existing ERROR gets a final
                    // attempt, then becomes FAILED so one corrupt image never blocks the queue.
                    val nextStatus = if (entity.ocrStatus == "ERROR") "FAILED" else "ERROR"
                    dao.update(entity.copy(ocrStatus = nextStatus))
                }
                yield()
            }
        }

        // WorkManager jobs should stay bounded. If either the producer is still scanning
        // or retryable rows still exist, chain another worker immediately.
        if (settingsRepo.settings.first().runOcrAutomatically &&
            (BulkScanState.isActive(applicationContext) || dao.retryableOcrCount() > 0)
        ) {
            appendContinuation(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "recallshot-ocr-queue-v4"
        private const val BATCH_SIZE = 48
        private const val MAX_RUN_MS = 7 * 60 * 1000L
        private const val EMPTY_QUEUE_WAIT_MS = 1200L

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<OcrQueueWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Used when a full scan completes. REPLACE is deliberate: the final producer event
         * must always create a fresh consumer even if an older unique worker is still winding down.
         */
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
