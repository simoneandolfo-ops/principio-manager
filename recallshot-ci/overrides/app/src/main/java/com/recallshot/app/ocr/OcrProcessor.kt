package com.recallshot.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.recallshot.app.data.ScreenshotEntity
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OcrDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)
class OcrSourceException(message: String, cause: Throwable? = null) : IOException(message, cause)

class OcrProcessor(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(entity: ScreenshotEntity): String = withContext(Dispatchers.IO) {
        val uri = resolveUri(entity)
        val bitmap = decodeSampled(uri)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image).await().text.orEmpty()
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun resolveUri(entity: ScreenshotEntity): Uri {
        return if (!entity.privateCopyPath.isNullOrBlank()) {
            val file = File(entity.privateCopyPath)
            if (!file.exists() || !file.canRead()) {
                throw OcrSourceException("Private copy is missing or unreadable")
            }
            Uri.fromFile(file)
        } else {
            val raw = entity.contentUri.removePrefix("shared:")
            if (raw.isBlank()) throw OcrSourceException("Image URI is empty")
            Uri.parse(raw)
        }
    }

    private fun decodeSampled(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: throw OcrSourceException("Unable to open image source")
        } catch (e: SecurityException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw OcrSourceException("Image source not found", e)
        } catch (e: OcrSourceException) {
            throw e
        } catch (e: IOException) {
            throw OcrSourceException("Unable to read image source", e)
        } catch (e: Throwable) {
            throw OcrDecodeException("Unable to inspect image", e)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw OcrDecodeException("Invalid image dimensions: ${bounds.outWidth}x${bounds.outHeight}")
        }

        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        try {
            return resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: throw OcrSourceException("Unable to reopen image source")
                ?: throw OcrDecodeException("Bitmap decoder returned null")
        } catch (e: SecurityException) {
            throw e
        } catch (e: OcrSourceException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw OcrSourceException("Image source disappeared while decoding", e)
        } catch (e: OutOfMemoryError) {
            throw OcrDecodeException("Not enough memory to decode sampled image", e)
        } catch (e: IOException) {
            throw OcrSourceException("I/O error while decoding image", e)
        } catch (e: OcrDecodeException) {
            throw e
        } catch (e: Throwable) {
            throw OcrDecodeException("Image decode failed", e)
        }
    }

    companion object {
        // A modern phone screenshot is commonly 1080x2400 or larger. Capping the longest
        // side keeps OCR detail while preventing thousands of full-resolution bitmaps from
        // putting sustained pressure on the native heap.
        private const val MAX_DIMENSION = 2048
    }
}
