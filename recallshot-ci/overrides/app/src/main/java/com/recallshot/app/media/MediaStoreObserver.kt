package com.recallshot.app.media

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.recallshot.app.workers.WorkScheduler

class MediaStoreObserver(private val context: Context) {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            WorkScheduler.scanAfterMediaChange(context.applicationContext)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            WorkScheduler.scanAfterMediaChange(context.applicationContext)
        }
    }

    fun register() {
        val resolver = context.contentResolver
        runCatching {
            resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                resolver.registerContentObserver(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    true,
                    observer
                )
            }
        }
    }
}
