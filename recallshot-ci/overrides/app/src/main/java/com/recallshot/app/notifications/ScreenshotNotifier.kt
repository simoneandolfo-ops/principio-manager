package com.recallshot.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.recallshot.app.MainActivity
import com.recallshot.app.R

object ScreenshotNotifier {
    fun show(context: Context, count: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context,
            90,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (count == 1) "Screenshot aggiunto · classificazione in corso…"
        else "$count nuovi screenshot aggiunti · classificazione in corso…"
        val notification = NotificationCompat.Builder(context, NotificationChannels.SCREENSHOTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RecallShot")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setTimeoutAfter(6500)
            .build()
        NotificationManagerCompat.from(context).notify(901, notification)
    }
}
