package com.recallshot.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.recallshot.app.data.RecallShotDatabase
import com.recallshot.app.vision.SmartClassificationEngine
import com.recallshot.app.vision.VisualClassifier
import com.recallshot.core.LocalClassifier

/**
 * Incremental second pass for OTHER/low-confidence items. It never reruns OCR.
 * The max processed id is persisted, so new screenshots are reconsidered without
 * repeatedly burning battery on the whole library.
 */
class ReclassificationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = RecallShotDatabase.get(applicationContext).screenshotDao()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var cursor = prefs.getLong(KEY_CURSOR, 0L)
        val visual = VisualClassifier(applicationContext)
        val textClassifier = LocalClassifier()
        val smart = SmartClassificationEngine(applicationContext)

        while (!isStopped) {
            val batch = dao.secondPassCandidates(cursor, MAX_CONFIDENCE, BATCH_SIZE)
            if (batch.isEmpty()) {
                prefs.edit().putLong(KEY_CURSOR, dao.maxId()).apply()
                return Result.success()
            }

            for (item in batch) {
                if (isStopped) return Result.retry()
                val textResult = textClassifier.classify(item.title, item.ocrText, item.sourceApp)
                val visualResult = visual.classify(item, item.ocrText)
                val decision = smart.decide(
                    entity = item,
                    title = item.title,
                    ocrText = item.ocrText,
                    textCategory = textResult.category.name,
                    textConfidence = textResult.confidence,
                    visual = visualResult
                )
                if (decision.category != item.category || decision.confidence > item.confidence) {
                    dao.updateClassification(item.id, decision.category, decision.confidence)
                }
                cursor = item.id
                prefs.edit().putLong(KEY_CURSOR, cursor).apply()
            }
        }
        return Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "recallshot-smart-reclassification-v1"
        private const val PREFS = "recallshot_reclassification_v1"
        private const val KEY_CURSOR = "max_processed_id"
        private const val BATCH_SIZE = 24
        private const val MAX_CONFIDENCE = 0.78

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReclassificationWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
