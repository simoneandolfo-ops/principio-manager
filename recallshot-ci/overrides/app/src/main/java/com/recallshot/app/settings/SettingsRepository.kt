package com.recallshot.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "recallshot_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val AUTO_IMPORT = booleanPreferencesKey("auto_import")
        val AUTO_OCR = booleanPreferencesKey("auto_ocr")
        val REMINDERS = booleanPreferencesKey("reminders")
        val LAST_SCAN = longPreferencesKey("last_media_scan_seconds")
        val SCREENSHOT_NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("screenshot_notification_permission_asked")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingComplete = p[Keys.ONBOARDING] ?: false,
            autoImportScreenshots = p[Keys.AUTO_IMPORT] ?: true,
            runOcrAutomatically = p[Keys.AUTO_OCR] ?: true,
            remindersEnabled = p[Keys.REMINDERS] ?: true,
            lastMediaScanSeconds = p[Keys.LAST_SCAN] ?: 0L,
            screenshotNotificationPermissionAsked = p[Keys.SCREENSHOT_NOTIFICATION_PERMISSION_ASKED] ?: false
        )
    }

    suspend fun finishOnboarding() = context.dataStore.edit { it[Keys.ONBOARDING] = true }
    suspend fun setAutoImport(value: Boolean) = context.dataStore.edit { it[Keys.AUTO_IMPORT] = value }
    suspend fun setAutoOcr(value: Boolean) = context.dataStore.edit { it[Keys.AUTO_OCR] = value }
    suspend fun setReminders(value: Boolean) = context.dataStore.edit { it[Keys.REMINDERS] = value }
    suspend fun setLastScanSeconds(value: Long) = context.dataStore.edit { it[Keys.LAST_SCAN] = value }
    suspend fun markScreenshotNotificationPermissionAsked() = context.dataStore.edit {
        it[Keys.SCREENSHOT_NOTIFICATION_PERMISSION_ASKED] = true
    }
}
