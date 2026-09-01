package com.recallshot.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val REMINDERS = "recallshot_reminders"
    const val SCREENSHOTS = "recallshot_screenshots"
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(REMINDERS, "Promemoria RecallShot", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Promemoria collegati ai tuoi screenshot" })
        manager.createNotificationChannel(NotificationChannel(SCREENSHOTS, "Nuovi screenshot", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Avvisi discreti quando RecallShot rileva un nuovo screenshot"
            setSound(null, null)
            enableVibration(false)
        })
    }
}
