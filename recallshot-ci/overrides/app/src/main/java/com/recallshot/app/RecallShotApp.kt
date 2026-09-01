package com.recallshot.app

import android.app.Application
import com.recallshot.app.media.MediaStoreObserver
import com.recallshot.app.notifications.NotificationChannels
import com.recallshot.app.workers.OcrQueueWorker
import com.recallshot.app.workers.WorkScheduler

class RecallShotApp : Application() {
    private lateinit var mediaObserver: MediaStoreObserver

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        WorkScheduler.ensurePeriodicScan(this)
        WorkScheduler.ensureOcrRecovery(this)
        // Resume the bulk OCR queue immediately after app/process start. The worker
        // itself respects the user's automatic-OCR setting.
        OcrQueueWorker.start(this)
        mediaObserver = MediaStoreObserver(this).also { it.register() }
    }
}
