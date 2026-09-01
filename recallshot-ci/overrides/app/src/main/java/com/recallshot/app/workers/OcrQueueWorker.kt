package com.recallshot.app.workers

import android.content.Context
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

class OcrQueueWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository(applicationContext)
        if (!settingsRepo.settings.first().runOcrAutomatically) return Result.success()

        val dao = RecallShotDatabase.get(applicationContext).screenshotDao()
        val batch = dao.pendingOcr(BATCH_SIZE)
        if (batch.isEmpty()) return Result.success()

        val processor = OcrProcessor(applicationContext)
        val classifier = LocalClassifier()

        for (entity in batch) {
            if (isStopped) return Result.retry()
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
                dao.update(entity.copy(ocrStatus = "ERROR"))
            }
        }

        if (settingsRepo.settings.first().runOcrAutomatically && dao.pendingOcr(1).isNotEmpty()) {
            appendContinuation(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "recallshot-ocr-queue"
        private const val BATCH_SIZE = 12

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
