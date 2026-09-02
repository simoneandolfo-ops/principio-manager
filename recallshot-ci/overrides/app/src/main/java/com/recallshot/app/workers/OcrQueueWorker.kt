package com.recallshot.app.workers

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.recallshot.app.data.RecallShotDatabase
import com.recallshot.app.ocr.OcrDecodeException
import com.recallshot.app.ocr.OcrProcessor
import com.recallshot.app.ocr.OcrSourceException
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
                delay(BETWEEN_IMAGES_MS)
            } catch (_: SecurityException) {
                dao.update(entity.copy(ocrStatus = "PERMISSION"))
                delay(ERROR_BACKOFF_MS)
            } catch (_: OcrDecodeException) {
                dao.update(entity.copy(ocrStatus = failureStatus(originalStatus, "DECODE")))
                delay(ERROR_BACKOFF_MS)
            } catch (_: OcrSourceException) {
                dao.update(entity.copy(ocrStatus = failureStatus(originalStatus, "SOURCE")))
                delay(ERROR_BACKOFF_MS)
            } catch (_: Throwable) {
                dao.update(entity.copy(ocrStatus = failureStatus(originalStatus, "OCR")))
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

    private fun failureStatus(originalStatus: String, type: String): String {
        val alreadyFailedOnce = originalStatus == "ERROR" || originalStatus.startsWith("ERROR_")
        return if (alreadyFailedOnce) "FAILED_$type" else "ERROR_$type"
    }

    companion object {
        const val UNIQUE_NAME = "recallshot-ocr-queue-v6"
        private const val MAX_RUN_MS = 7 * 60 * 1000L
        private const val EMPTY_QUEUE_WAIT_MS = 1200L
        private const val BETWEEN_IMAGES_MS = 450L
        private const val ERROR_BACKOFF_MS = 1200L

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
