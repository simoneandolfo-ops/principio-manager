package com.recallshot.app.data

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.recallshot.app.media.MediaStoreScanner
import com.recallshot.app.media.SharedImageImporter
import com.recallshot.app.workers.OcrWorker
import com.recallshot.core.LocalSearchIndex
import com.recallshot.core.ScreenshotCategory
import com.recallshot.core.ScreenshotRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Instant
import com.recallshot.app.settings.SettingsRepository

class ScreenshotRepository(private val context: Context) {
    @Volatile private var cachedSnapshot: List<ScreenshotEntity>? = null
    @Volatile private var cachedIndex: LocalSearchIndex? = null
    private val dao = RecallShotDatabase.get(context).screenshotDao()
    private val scanner = MediaStoreScanner(context, dao)
    private val sharedImporter = SharedImageImporter(context, dao)
    val all: Flow<List<ScreenshotEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<ScreenshotEntity?> = dao.observeById(id)
    suspend fun scanMediaStore(sinceSeconds: Long? = null): Int = scanner.scan(sinceSeconds)
    suspend fun importShared(uri: Uri, sourceApp: String?): Long? {
        val id = sharedImporter.import(uri, sourceApp)
        if (id != null && id > 0) { val settings = SettingsRepository(context).settings.first(); if (settings.runOcrAutomatically) enqueueOcr(id) }
        return id
    }
    fun enqueueOcr(id: Long, force: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<OcrWorker>().setInputData(workDataOf(OcrWorker.KEY_ID to id, OcrWorker.KEY_FORCE to force)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("ocr-$id", if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }
    suspend fun enqueuePendingOcr() = withContext(Dispatchers.IO) { dao.pendingOcr().forEach { enqueueOcr(it.id) } }
    suspend fun setFavorite(id: Long, value: Boolean) = dao.setFavorite(id, value)
    suspend fun edit(id: Long, title: String, note: String, category: String) = dao.edit(id, title, note, category)
    suspend fun delete(id: Long) { val item = dao.getById(id); dao.deleteById(id); item?.privateCopyPath?.let { runCatching { java.io.File(it).delete() } } }
    suspend fun deleteOriginalLegacy(id: Long) = withContext(Dispatchers.IO) {
        val item = dao.getById(id) ?: return@withContext
        if (item.sourceKind == "MEDIASTORE") {
            try { context.contentResolver.delete(Uri.parse(item.contentUri), null, null) } catch (_: SecurityException) { return@withContext }
        }
        delete(id)
    }
    suspend fun setReminder(id: Long, time: Long?) = dao.setReminder(id, time)
    suspend fun clearAllReminders() = dao.clearAllReminders()
    suspend fun search(query: String, snapshot: List<ScreenshotEntity>, category: String? = null, limit: Int = 80): List<ScreenshotEntity> = withContext(Dispatchers.Default) {
        val categoryEnum = category?.let { runCatching { ScreenshotCategory.valueOf(it) }.getOrNull() }
        if (query.isBlank()) return@withContext snapshot.asSequence().filter { category == null || it.category == category }.take(limit).toList()
        val index = if (cachedSnapshot === snapshot && cachedIndex != null) cachedIndex!! else LocalSearchIndex(snapshot.map { it.toCoreRecord() }).also { cachedSnapshot = snapshot; cachedIndex = it }
        val byId = snapshot.associateBy { it.id }
        index.search(query, limit, categoryEnum).mapNotNull { byId[it.record.id] }
    }
}

fun ScreenshotEntity.categoryEnum(): ScreenshotCategory = runCatching { ScreenshotCategory.valueOf(category) }.getOrDefault(ScreenshotCategory.OTHER)
fun ScreenshotEntity.toCoreRecord() = ScreenshotRecord(id=id,title=title,description=description,ocrText=ocrText,note=note,category=categoryEnum(),sourceApp=sourceApp,createdAt=Instant.ofEpochMilli(createdAt),isFavorite=isFavorite,reminderAt=reminderAt?.let(Instant::ofEpochMilli))
