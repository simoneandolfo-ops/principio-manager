package com.recallshot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.recallshot.app.data.ScreenshotEntity
import com.recallshot.app.ui.components.ScreenshotCard
import com.recallshot.app.ui.components.imageModel

@Composable
fun DashboardScreen(all: List<ScreenshotEntity>, autoOcrEnabled: Boolean, visible: List<ScreenshotEntity>, reminders: List<ScreenshotEntity>, query: String, onQuery: (String) -> Unit, onCategory: (String) -> Unit, onOpen: (Long, List<ScreenshotEntity>) -> Unit, onFavorite: (ScreenshotEntity) -> Unit, onImport: () -> Unit) {
    val waiting = all.count { it.ocrStatus == "PENDING" || it.ocrStatus == "PROCESSING" || it.ocrStatus == "ERROR" || it.ocrStatus.startsWith("ERROR_") }
    val done = all.count { it.ocrStatus == "DONE" }
    val terminalErrors = all.count { it.ocrStatus == "FAILED" || it.ocrStatus.startsWith("FAILED_") || it.ocrStatus == "PERMISSION" }
    val categorized = all.count { it.ocrStatus == "DONE" && it.category != "OTHER" }
    val other = all.count { it.ocrStatus == "DONE" && it.category == "OTHER" }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("RecallShot", style = MaterialTheme.typography.headlineMedium)
                    Text("La memoria dei tuoi screenshot", color = MaterialTheme.colorScheme.secondary)
                }
                FilledTonalIconButton(onClick = onImport) { Icon(Icons.Outlined.AddPhotoAlternate, "Importa") }
            }
        }

        // The technical/progress card is temporary UI only. Once the queue is drained it
        // disappears completely, even if a few unreadable files remain; diagnostics belong
        // in settings, not on the user's finished home screen.
        if (autoOcrEnabled && waiting > 0) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Classificazione in corso", style = MaterialTheme.typography.titleSmall)
                    Text("Completati $done · In coda $waiting · Errori $terminalErrors")
                    Text("Categorie $categorized · Altro $other", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Cerca nei tuoi screenshot…") },
                singleLine = true
            )
        }

        if (query.isBlank()) {
            item {
                Text("Categorie", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    userScrollEnabled = false,
                    modifier = Modifier.height(590.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recallCategories) { cat ->
                        RecallCategoryTile(cat, all.count { it.category == cat }) { onCategory(cat) }
                    }
                }
            }
            if (reminders.isNotEmpty()) {
                item { Text("Da ricordare", style = MaterialTheme.typography.titleLarge) }
                items(reminders, key = { "rem-${it.id}" }) { item ->
                    ScreenshotCard(item, onOpen = { onOpen(item.id, reminders) }, onFavorite = { onFavorite(item) })
                }
            }
        }

        item { Text(if (query.isBlank()) "Recenti" else "${visible.size} risultati", style = MaterialTheme.typography.titleLarge) }
        if (visible.isEmpty()) item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (all.isEmpty()) "Nessuno screenshot ancora" else "Nessun risultato", style = MaterialTheme.typography.titleMedium)
                    Text(if (all.isEmpty()) "Gli screenshot reali della galleria compariranno qui dopo la scansione." else "Prova con parole diverse.", color = MaterialTheme.colorScheme.secondary)
                }
            }
        } else items(visible.chunked(3), key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    AsyncImage(
                        model = item.imageModel(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(0.78f).clip(RoundedCornerShape(10.dp)).clickable { onOpen(item.id, visible) }
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RecallCategoryTile(category: String, count: Int, onClick: () -> Unit) {
    val icon: ImageVector = when (category) {
        "PRODUCT" -> Icons.Outlined.ShoppingBag
        "PLACE" -> Icons.Outlined.Place
        "TRAVEL" -> Icons.Outlined.Flight
        "CONVERSATION" -> Icons.Outlined.ChatBubbleOutline
        "DOCUMENT" -> Icons.Outlined.Description
        "IDEA" -> Icons.Outlined.Lightbulb
        "CONTACT" -> Icons.Outlined.PersonOutline
        "LINK" -> Icons.Outlined.Link
        "MUSIC" -> Icons.Outlined.MusicNote
        "PERSON" -> Icons.Outlined.People
        "CAR" -> Icons.Outlined.DirectionsCar
        "ANIMAL" -> Icons.Outlined.Pets
        "FOOD" -> Icons.Outlined.Restaurant
        else -> Icons.Outlined.Collections
    }
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(recallCategoryLabel(category), style = MaterialTheme.typography.titleMedium)
        Text("$count screenshot", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
    }
}
