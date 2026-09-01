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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.recallshot.app.data.ScreenshotEntity
import com.recallshot.app.ui.components.CategoryCard
import com.recallshot.app.ui.components.ScreenshotCard
import com.recallshot.app.ui.components.imageModel

private val categories = listOf("PRODUCT","PLACE","TRAVEL","CONVERSATION","DOCUMENT","IDEA","CONTACT","LINK","OTHER")

@Composable
fun DashboardScreen(all: List<ScreenshotEntity>, autoOcrEnabled: Boolean, visible: List<ScreenshotEntity>, reminders: List<ScreenshotEntity>, query: String, onQuery: (String) -> Unit, onCategory: (String) -> Unit, onOpen: (Long, List<ScreenshotEntity>) -> Unit, onFavorite: (ScreenshotEntity) -> Unit, onImport: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("RecallShot", style = MaterialTheme.typography.headlineMedium); Text("La memoria dei tuoi screenshot", color = MaterialTheme.colorScheme.secondary) }; FilledTonalIconButton(onClick = onImport) { Icon(Icons.Outlined.AddPhotoAlternate, "Importa") } } }
        if (autoOcrEnabled && all.any { it.ocrStatus == "PENDING" }) item { val pending = all.count { it.ocrStatus == "PENDING" }; Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Sto classificando $pending screenshot…") } } }
        item { OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Cerca nei tuoi screenshot…") }, singleLine = true) }
        if (query.isBlank()) {
            item { Text("Categorie", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp)); LazyVerticalGrid(columns = GridCells.Fixed(3), userScrollEnabled = false, modifier = Modifier.heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(categories.take(8)) { cat -> CategoryCard(cat, all.count { it.category == cat }, false, onClick = { onCategory(cat) }) } } }
            if (reminders.isNotEmpty()) { item { Text("Da ricordare", style = MaterialTheme.typography.titleLarge) }; items(reminders, key = { "rem-${it.id}" }) { item -> ScreenshotCard(item, onOpen = { onOpen(item.id, reminders) }, onFavorite = { onFavorite(item) }) } }
        }
        item { Text(if (query.isBlank()) "Recenti" else "${visible.size} risultati", style = MaterialTheme.typography.titleLarge) }
        if (visible.isEmpty()) item { Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (all.isEmpty()) "Nessuno screenshot ancora" else "Nessun risultato", style = MaterialTheme.typography.titleMedium); Text(if (all.isEmpty()) "Gli screenshot reali della galleria compariranno qui dopo la scansione." else "Prova con parole diverse.", color = MaterialTheme.colorScheme.secondary) } } }
        else items(visible.chunked(3), key = { row -> row.joinToString("-") { it.id.toString() } }) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { item -> AsyncImage(model = item.imageModel(), contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).aspectRatio(0.78f).clip(RoundedCornerShape(10.dp)).clickable { onOpen(item.id, visible) }) }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } }
    }
}
