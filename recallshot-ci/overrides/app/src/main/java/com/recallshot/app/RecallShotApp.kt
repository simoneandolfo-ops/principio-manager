package com.recallshot.app

import android.app.Application
import com.recallshot.app.media.MediaStoreObserver
import com.recallshot.app.notifications.NotificationChannels
import com.recallshot.app.workers.WorkScheduler

class RecallShotApp : Application() {
    private lateinit var mediaObserver: MediaStoreObserver

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        WorkScheduler.ensurePeriodicScan(this)
        WorkScheduler.ensureOcrRecovery(this)
        mediaObserver = MediaStoreObserver(this).also { it.register() }
    }
}
