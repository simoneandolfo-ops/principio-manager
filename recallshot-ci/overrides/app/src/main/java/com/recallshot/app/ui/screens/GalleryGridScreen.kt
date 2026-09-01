package com.recallshot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.recallshot.app.data.ScreenshotEntity
import com.recallshot.app.ui.components.imageModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryGridScreen(title: String, items: List<ScreenshotEntity>, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Indietro") } }) }) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(24.dp)) { Text("Nessuno screenshot in questa raccolta", color = MaterialTheme.colorScheme.secondary) }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.id }) { item ->
                    AsyncImage(model = item.imageModel(), contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.aspectRatio(0.78f).clip(RoundedCornerShape(10.dp)).clickable { onOpen(item.id) })
                }
            }
        }
    }
}
