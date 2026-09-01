package com.recallshot.app.ui.components

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.recallshot.app.data.ScreenshotEntity
import java.io.File

fun ScreenshotEntity.imageModel(): Any = privateCopyPath?.let(::File) ?: Uri.parse(contentUri.removePrefix("shared:"))

fun ScreenshotEntity.shareUri(context: Context): Uri = privateCopyPath?.let { path ->
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
} ?: Uri.parse(contentUri.removePrefix("shared:"))
