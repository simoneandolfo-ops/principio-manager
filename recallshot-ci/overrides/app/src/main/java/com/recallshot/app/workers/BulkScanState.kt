package com.recallshot.app.workers

import android.content.Context

/**
 * Cross-worker producer/consumer coordination for large MediaStore imports.
 *
 * A full scan can take long enough that the OCR consumer temporarily sees an
 * empty queue while the scanner is still inserting rows. Without this state,
 * the OCR worker can exit too early and leave thousands of PENDING rows behind.
 */
object BulkScanState {
    private const val PREFS = "recallshot_bulk_pipeline"
    private const val KEY_ACTIVE = "full_scan_active"
    private const val KEY_STARTED_AT = "full_scan_started_at"
    private const val STALE_AFTER_MS = 30 * 60 * 1000L

    fun begin(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun end(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, false)
            .remove(KEY_STARTED_AT)
            .apply()
    }

    fun isActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return false

        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        val stale = startedAt <= 0L || System.currentTimeMillis() - startedAt > STALE_AFTER_MS
        if (stale) {
            end(context)
            return false
        }
        return true
    }
}
