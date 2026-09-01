package com.recallshot.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.recallshot.app.data.ScreenshotEntity
import java.io.File
import kotlinx.coroutines.tasks.await

class OcrProcessor(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(entity: ScreenshotEntity): String {
        val image = if (!entity.privateCopyPath.isNullOrBlank()) {
            InputImage.fromFilePath(context, Uri.fromFile(File(entity.privateCopyPath)))
        } else {
            val raw = entity.contentUri.removePrefix("shared:")
            InputImage.fromFilePath(context, Uri.parse(raw))
        }
        return recognizer.process(image).await().text.orEmpty()
    }
}
