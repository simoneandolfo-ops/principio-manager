package com.recallshot.app.ocr

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.recallshot.app.data.ScreenshotEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OcrDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)
class OcrSourceException(message: String, cause: Throwable? = null) : IOException(message, cause)

class OcrProcessor(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(entity: ScreenshotEntity): String = withContext(Dispatchers.IO) {
        val source = resolveSource(entity)
        val bitmap = decodeSampled(source)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image).await().text.orEmpty()
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * Cross-Android source resolution.
     *
     * Never trust a filesystem path for a MediaStore item under scoped storage. A private
     * copy is used only when it really exists and is readable; otherwise we always fall
     * back to the persisted MediaStore/content URI. This is intentionally OEM-agnostic.
     */
    private fun resolveSource(entity: ScreenshotEntity): ImageSource {
        val privatePath = entity.privateCopyPath?.trim().orEmpty()
        if (privatePath.isNotEmpty()) {
            val privateFile = File(privatePath)
            if (privateFile.isFile && privateFile.canRead()) {
                return ImageSource.FileSource(privateFile)
            }
            // Important: do NOT fail here. MediaStore rows may contain legacy/inaccessible
            // paths on Android 10+ even though the content:// URI is fully readable.
        }

        val raw = entity.contentUri.removePrefix("shared:").trim()
        if (raw.isBlank()) throw OcrSourceException("Image URI is empty")

        // Normal case for MediaStore and providers on every modern Android/OEM.
        if (raw.startsWith("content://", ignoreCase = true)) {
            return ImageSource.ContentUri(Uri.parse(raw))
        }

        // A shared/private file URI can still be processed if it points to a readable file.
        if (raw.startsWith("file://", ignoreCase = true)) {
            val file = File(Uri.parse(raw).path ?: "")
            if (file.isFile && file.canRead()) return ImageSource.FileSource(file)
            throw OcrSourceException("File URI is missing or unreadable")
        }

        // Some older/import pipelines persisted only the MediaStore row id. Repair that
        // representation instead of treating it as a filesystem path.
        raw.toLongOrNull()?.let { id ->
            return ImageSource.ContentUri(
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            )
        }

        // Last compatibility fallback for legacy absolute paths. This is never the preferred
        // route and is deliberately isolated from normal MediaStore access.
        if (raw.startsWith("/")) {
            val file = File(raw)
            if (file.isFile && file.canRead()) return ImageSource.FileSource(file)
        }

        // Uri.parse can still represent a provider-specific URI that omitted the literal
        // prefix in old data. Only accept it if ContentResolver can actually open it later.
        val parsed = Uri.parse(raw)
        if (parsed.scheme != null) return ImageSource.ContentUri(parsed)

        throw OcrSourceException("Unsupported image source")
    }

    private fun decodeSampled(source: ImageSource): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        try {
            openStream(source).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
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
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        try {
            val decoded = openStream(source).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            return decoded ?: throw OcrDecodeException("Bitmap decoder returned null")
        } catch (e: SecurityException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw OcrSourceException("Image source disappeared while decoding", e)
        } catch (e: OutOfMemoryError) {
            throw OcrDecodeException("Not enough memory to decode sampled image", e)
        } catch (e: OcrSourceException) {
            throw e
        } catch (e: IOException) {
            throw OcrSourceException("I/O error while decoding image", e)
        } catch (e: OcrDecodeException) {
            throw e
        } catch (e: Throwable) {
            throw OcrDecodeException("Image decode failed", e)
        }
    }

    private fun openStream(source: ImageSource): InputStream = when (source) {
        is ImageSource.FileSource -> FileInputStream(source.file)
        is ImageSource.ContentUri -> context.contentResolver.openInputStream(source.uri)
            ?: throw OcrSourceException("ContentResolver returned no stream for image")
    }

    private sealed interface ImageSource {
        data class ContentUri(val uri: Uri) : ImageSource
        data class FileSource(val file: File) : ImageSource
    }

    companion object {
        private const val MAX_DIMENSION = 2048
    }
}
