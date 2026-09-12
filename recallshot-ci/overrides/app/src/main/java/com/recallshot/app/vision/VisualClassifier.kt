package com.recallshot.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.recallshot.app.data.ScreenshotEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Best-effort on-device visual classification.
 *
 * It is deliberately isolated from the OCR pipeline: if visual analysis cannot open,
 * decode or classify an image, OCR still succeeds and the item simply remains OTHER.
 */
class VisualClassifier(private val context: Context) {
    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_LABEL_CONFIDENCE)
                .build()
        )
    }

    data class Result(val category: String, val confidence: Float)

    suspend fun classify(entity: ScreenshotEntity, ocrText: String): Result? = withContext(Dispatchers.IO) {
        // Text/UI signals are especially useful for music-player screenshots where a
        // generic image model often sees artwork/text rather than the player itself.
        musicFromText(ocrText)?.let { return@withContext it }

        val bitmap = runCatching { decodeForVision(entity) }.getOrNull() ?: return@withContext null
        try {
            val labels = runCatching {
                labeler.process(InputImage.fromBitmap(bitmap, 0)).await()
            }.getOrNull() ?: return@withContext null

            var best: Result? = null
            for (label in labels) {
                val category = categoryForLabel(label.text) ?: continue
                val candidate = Result(category, label.confidence)
                if (best == null || candidate.confidence > best!!.confidence) best = candidate
            }
            best
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun musicFromText(text: String): Result? {
        val normalized = text.lowercase()
        val strongTerms = listOf(
            "spotify", "youtube music", "apple music", "soundcloud", "deezer", "shazam",
            "playlist", "brano", "album", "artista", "artist", "song", "track", "riproduzione"
        )
        if (strongTerms.any { normalized.contains(it) }) return Result("MUSIC", 0.96f)

        // A player screenshot very often contains both elapsed and total track duration.
        val timeMatches = Regex("(?<!\\d)\\d{1,2}:\\d{2}(?!\\d)").findAll(normalized).count()
        if (timeMatches >= 2 && normalized.length < 1200) return Result("MUSIC", 0.78f)
        return null
    }

    private fun categoryForLabel(raw: String): String? {
        val label = raw.lowercase()
        return when {
            containsAny(label, "person", "people", "human", "selfie", "portrait", "face") -> "PERSON"
            containsAny(label, "car", "automobile", "vehicle", "motor vehicle", "sports car", "sedan") -> "CAR"
            containsAny(label, "dog", "cat", "animal", "pet", "bird", "horse", "wildlife", "mammal") -> "ANIMAL"
            containsAny(label, "food", "dish", "meal", "cuisine", "dessert", "cake", "pizza", "pasta", "sandwich", "bread", "fruit", "vegetable") -> "FOOD"
            containsAny(label, "music", "musician", "concert", "musical instrument", "album cover") -> "MUSIC"
            else -> null
        }
    }

    private fun containsAny(label: String, vararg terms: String): Boolean = terms.any { label.contains(it) }

    private fun decodeForVision(entity: ScreenshotEntity): Bitmap? {
        for (uri in candidateUris(entity)) {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val inspected = runCatching {
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            }.isSuccess
            if (!inspected || bounds.outWidth <= 0 || bounds.outHeight <= 0) continue

            var sample = 1
            while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = runCatching {
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
            if (bitmap != null) return bitmap
        }
        return null
    }

    private fun candidateUris(entity: ScreenshotEntity): List<Uri> {
        val result = mutableListOf<Uri>()
        entity.privateCopyPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.exists() && file.canRead()) result += Uri.fromFile(file)
        }
        val raw = entity.contentUri.removePrefix("shared:").trim()
        if (raw.isNotBlank()) {
            val parsed = runCatching { Uri.parse(raw) }.getOrNull()
            if (parsed != null) result += parsed
        }
        return result.distinctBy { it.toString() }
    }

    companion object {
        private const val MAX_DIMENSION = 1280
        private const val MIN_LABEL_CONFIDENCE = 0.58f
    }
}
