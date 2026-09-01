package com.recallshot.app.settings

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val autoImportScreenshots: Boolean = true,
    val runOcrAutomatically: Boolean = true,
    val remindersEnabled: Boolean = true,
    val lastMediaScanSeconds: Long = 0L,
    val screenshotNotificationPermissionAsked: Boolean = false
)
