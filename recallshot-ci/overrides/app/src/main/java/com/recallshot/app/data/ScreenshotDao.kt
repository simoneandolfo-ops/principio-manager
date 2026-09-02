package com.recallshot.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {
    @Query("SELECT * FROM screenshots ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ScreenshotEntity?>

    @Query("SELECT * FROM screenshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE contentUri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): ScreenshotEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ScreenshotEntity): Long

    @Update
    suspend fun update(entity: ScreenshotEntity)

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE screenshots SET isFavorite = :value WHERE id = :id")
    suspend fun setFavorite(id: Long, value: Boolean)

    @Query("UPDATE screenshots SET title = :title, note = :note, category = :category WHERE id = :id")
    suspend fun edit(id: Long, title: String, note: String, category: String)

    @Query("UPDATE screenshots SET reminderAt = :reminderAt WHERE id = :id")
    suspend fun setReminder(id: Long, reminderAt: Long?)

    @Query("UPDATE screenshots SET reminderAt = NULL")
    suspend fun clearAllReminders()

    // 0.2.5: PROCESSING is retryable too. If Android kills the worker while ML Kit
    // is working on an image, the next worker can safely recover that row.
    @Query("SELECT * FROM screenshots WHERE ocrStatus IN ('PENDING','ERROR','PROCESSING') ORDER BY importedAt ASC LIMIT :limit")
    suspend fun pendingOcr(limit: Int = 1): List<ScreenshotEntity>

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrStatus IN ('PENDING','ERROR','PROCESSING')")
    suspend fun retryableOcrCount(): Int

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrStatus = 'DONE'")
    suspend fun doneOcrCount(): Int

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrStatus IN ('FAILED','PERMISSION')")
    suspend fun failedOcrCount(): Int

    @Query("SELECT COUNT(*) FROM screenshots")
    suspend fun count(): Int
}
