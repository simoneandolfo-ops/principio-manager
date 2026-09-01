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
            if (batch.isEmpty()) return Result.success()

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
                    // ERROR from older builds gets exactly one recovery attempt here.
                    // A fresh PENDING item gets one retry on the next pass. If it fails
                    // again it becomes FAILED so one corrupt image cannot block 5000 others.
                    val nextStatus = if (entity.ocrStatus == "ERROR") "FAILED" else "ERROR"
                    dao.update(entity.copy(ocrStatus = nextStatus))
                }
                yield()
            }
        }

        if (settingsRepo.settings.first().runOcrAutomatically && dao.retryableOcrCount() > 0) {
            appendContinuation(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "recallshot-ocr-queue-v3"
        private const val BATCH_SIZE = 48
        private const val MAX_RUN_MS = 7 * 60 * 1000L

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<OcrQueueWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
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
