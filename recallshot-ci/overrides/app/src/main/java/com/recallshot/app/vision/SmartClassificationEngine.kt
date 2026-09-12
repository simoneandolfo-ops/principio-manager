package com.recallshot.app.vision

import android.content.Context
import com.recallshot.app.data.ScreenshotEntity
import java.util.Locale

/**
 * Hierarchical score-fusion engine. Text, source app, visual result and learned corrections
 * contribute independently; strong semantic text wins over incidental visual objects.
 */
class SmartClassificationEngine(context: Context) {
    private val learning = ClassificationLearningStore(context)

    data class Decision(
        val category: String,
        val confidence: Double,
        val reason: String
    )

    fun decide(
        entity: ScreenshotEntity,
        title: String,
        ocrText: String,
        textCategory: String,
        textConfidence: Double,
        visual: VisualClassifier.Result?
    ): Decision {
        val text = "$title\n$ocrText".lowercase(Locale.ROOT)
        val source = entity.sourceApp.orEmpty().lowercase(Locale.ROOT)
        val scores = mutableMapOf<String, Double>()
        fun add(cat: String, score: Double) {
            scores[cat] = maxOf(scores[cat] ?: 0.0, score.coerceIn(0.0, 1.0))
        }

        // Base text classifier remains authoritative when it is confident.
        add(textCategory, textConfidence)

        // First stage: recognize the broad content family from strong text/source signals.
        if (containsAny(source, "whatsapp", "telegram", "messenger", "signal") ||
            containsAny(text, "messaggio", "message", "scrivi un messaggio", "online", "ultimo accesso")) {
            add("CONVERSATION", 0.91)
        }
        if (containsAny(text, "fattura", "invoice", "documento", "contratto", "codice fiscale", "partita iva", "ricevuta", "receipt")) {
            add("DOCUMENT", 0.89)
        }
        if (containsAny(text, "spotify", "youtube music", "apple music", "soundcloud", "deezer", "shazam", "playlist", "album", "brano", "artista", "artist", "song", "track")) {
            add("MUSIC", 0.95)
        }
        if (containsAny(text, "prenotazione", "ristorante", "hotel", "maps", "tripadvisor", "booking.com", "indirizzo", "aperto", "chiuso")) {
            add("PLACE", 0.78)
        }
        if (containsAny(text, "volo", "flight", "gate", "boarding", "check-in", "aeroporto", "airport")) {
            add("TRAVEL", 0.92)
        }
        if (containsAny(text, "€", "eur", "prezzo", "price", "amazon", "acquista", "compralo", "spedizione")) {
            add("PRODUCT", 0.78)
        }

        // Visual evidence is strong only for photo-centric categories. It should not steal
        // a screenshot from DOCUMENT/CONVERSATION/TRAVEL when those have strong evidence.
        visual?.let {
            val visualScore = it.confidence.toDouble()
            val strongSemantic = scores.filterKeys { cat -> cat in semanticCategories }.values.maxOrNull() ?: 0.0
            val adjusted = if (strongSemantic >= 0.86) visualScore * 0.72 else visualScore
            add(it.category, adjusted)
        }

        // Extra domain cues reduce common FOOD/PRODUCT and PERSON/UI mistakes.
        if (containsAny(text, "ricetta", "ingredienti", "dessert", "pizza", "pasta", "ristorante", "menu", "menù", "antipasto", "secondo", "dolce")) {
            add("FOOD", 0.80)
        }
        if (containsAny(text, "auto", "automobile", "km", "km/h", "cv", "kw", "tesla", "fiat", "audi", "bmw", "mercedes", "toyota", "renault", "dacia")) {
            add("CAR", 0.77)
        }
        if (containsAny(text, "cane", "gatto", "dog", "cat", "pet", "animale")) add("ANIMAL", 0.83)

        // Personal learning only nudges existing evidence; it never creates a category from nothing.
        scores.keys.toList().forEach { category ->
            val boost = learning.boost(category, text, source)
            if (boost > 0.0) scores[category] = (scores.getValue(category) + boost).coerceAtMost(0.99)
        }

        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull() ?: return Decision("OTHER", 0.30, "nessun segnale forte")
        val second = ranked.getOrNull(1)?.value ?: 0.0
        val margin = best.value - second

        // Ambiguous low-score results intentionally fall back to OTHER for the second pass.
        if (best.value < 0.62 || (best.value < 0.76 && margin < 0.07)) {
            return Decision("OTHER", best.value.coerceAtMost(0.60), "segnali ambigui")
        }

        val reason = buildString {
            append("fusione punteggi")
            visual?.takeIf { it.category == best.key }?.let { append(" · visuale") }
            if (best.key == textCategory) append(" · testo")
            if (learning.boost(best.key, text, source) > 0) append(" · apprendimento")
        }
        return Decision(best.key, best.value, reason)
    }

    fun learnCorrection(item: ScreenshotEntity, category: String) = learning.learn(item, category)

    private fun containsAny(text: String, vararg terms: String): Boolean = terms.any { text.contains(it) }

    companion object {
        private val semanticCategories = setOf("DOCUMENT", "CONVERSATION", "TRAVEL", "PRODUCT", "PLACE", "LINK", "CONTACT", "IDEA", "MUSIC")
    }
}
