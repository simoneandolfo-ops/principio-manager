package com.recallshot.app.vision

import android.content.Context
import com.recallshot.app.data.ScreenshotEntity
import java.util.Locale

/** Lightweight, private, on-device learning from manual category corrections. */
class ClassificationLearningStore(context: Context) {
    private val prefs = context.getSharedPreferences("recallshot_classification_learning_v1", Context.MODE_PRIVATE)

    fun learn(item: ScreenshotEntity, category: String) {
        val normalizedCategory = category.uppercase(Locale.ROOT)
        val tokens = usefulTokens(listOf(item.title, item.ocrText, item.sourceApp.orEmpty()).joinToString(" ")).take(14)
        if (tokens.isEmpty()) return
        val editor = prefs.edit()
        tokens.forEach { token ->
            val key = key(normalizedCategory, token)
            editor.putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(40))
        }
        editor.putInt("category::$normalizedCategory", (prefs.getInt("category::$normalizedCategory", 0) + 1).coerceAtMost(200))
        editor.apply()
    }

    /** Returns a conservative boost. A single correction cannot dominate classification. */
    fun boost(category: String, text: String, sourceApp: String?): Double {
        val normalizedCategory = category.uppercase(Locale.ROOT)
        val tokens = usefulTokens("$text ${sourceApp.orEmpty()}").take(40)
        if (tokens.isEmpty()) return 0.0
        val hits = tokens.sumOf { prefs.getInt(key(normalizedCategory, it), 0).coerceAtMost(5) }
        val learnedExamples = prefs.getInt("category::$normalizedCategory", 0)
        if (learnedExamples <= 0 || hits <= 0) return 0.0
        return (hits * 0.025).coerceAtMost(0.16)
    }

    private fun key(category: String, token: String) = "token::$category::$token"

    private fun usefulTokens(raw: String): List<String> {
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .split(' ')
            .asSequence()
            .map { it.trim() }
            .filter { it.length in 3..24 && it !in stopWords }
            .distinct()
            .toList()
    }

    companion object {
        private val stopWords = setOf(
            "che", "con", "per", "una", "uno", "del", "della", "delle", "dei", "gli", "the", "and", "for",
            "this", "that", "from", "screenshot", "image", "foto", "http", "https", "www"
        )
    }
}
