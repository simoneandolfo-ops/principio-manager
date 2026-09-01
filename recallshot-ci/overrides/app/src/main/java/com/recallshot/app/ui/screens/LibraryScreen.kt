package com.recallshot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.recallshot.app.data.ScreenshotEntity
import com.recallshot.app.ui.components.imageModel

@Composable
fun LibraryScreen(items: List<ScreenshotEntity>, query: String, onQuery: (String) -> Unit, onOpen: (Long, List<ScreenshotEntity>) -> Unit) {
    var favoritesOnly by remember { mutableStateOf(false) }
    val shown = if (favoritesOnly) items.filter { it.isFavorite } else items
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Libreria", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(6.dp)) }
        item(span = { GridItemSpan(maxLineSpan) }) { OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(6.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Cerca nella libreria") }, singleLine = true) }
        item(span = { GridItemSpan(maxLineSpan) }) { Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("Tutti") }); FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("Preferiti") }) } }
        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(if (favoritesOnly) "Nessun preferito" else "Nessun risultato", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.secondary) }
        else items(shown, key = { it.id }) { item -> AsyncImage(model = item.imageModel(), contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.aspectRatio(0.78f).clip(RoundedCornerShape(10.dp)).clickable { onOpen(item.id, shown) }) }
    }
}
