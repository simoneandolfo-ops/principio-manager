package com.recallshot.app.ui.screens

val recallCategories = listOf(
    "PRODUCT", "PLACE", "TRAVEL", "CONVERSATION", "DOCUMENT", "IDEA", "CONTACT", "LINK",
    "MUSIC", "PERSON", "CAR", "ANIMAL", "FOOD", "OTHER"
)

fun recallCategoryLabel(category: String): String = when (category) {
    "PRODUCT" -> "Prodotti"
    "PLACE" -> "Posti"
    "TRAVEL" -> "Viaggi"
    "CONVERSATION" -> "Chat"
    "DOCUMENT" -> "Documenti"
    "IDEA" -> "Idee"
    "CONTACT" -> "Contatti"
    "LINK" -> "Link"
    "MUSIC" -> "Musica"
    "PERSON" -> "Persone"
    "CAR" -> "Auto"
    "ANIMAL" -> "Animali"
    "FOOD" -> "Cibo"
    else -> "Altro"
}
