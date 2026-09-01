package com.recallshot.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.recallshot.app.data.ScreenshotEntity
import com.recallshot.app.ui.components.categoryLabel
import com.recallshot.app.ui.components.imageModel
import com.recallshot.app.ui.components.shareUri

private val viewerCategories = listOf("PRODUCT","PLACE","TRAVEL","CONVERSATION","DOCUMENT","IDEA","CONTACT","LINK","OTHER")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(items: List<ScreenshotEntity>, startId: Long, remindersEnabled: Boolean, onBack: () -> Unit, onFavorite: (ScreenshotEntity) -> Unit, onSave: (ScreenshotEntity, String, String, String) -> Unit, onDelete: (ScreenshotEntity) -> Unit, onReminder: (ScreenshotEntity, Long?) -> Unit, onRetryOcr: (Long) -> Unit) {
    if (items.isEmpty()) { LaunchedEffect(Unit) { onBack() }; return }
    val initial = items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initial) { items.size }
    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]
    var showInfo by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }; val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("${pagerState.currentPage + 1} / ${items.size}") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Indietro") } }, actions = { IconButton(onClick = { onFavorite(current) }) { Icon(if (current.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Preferito") }; IconButton(onClick = { val share = Intent(Intent.ACTION_SEND).apply { type = current.mimeType; putExtra(Intent.EXTRA_STREAM, current.shareUri(context)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(share, "Condividi screenshot")) }) { Icon(Icons.Outlined.Share, "Condividi") } }) }, bottomBar = { BottomAppBar { IconButton(onClick = { showInfo = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Info, "Informazioni") }; IconButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Delete, "Elimina") } } }) { pad -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(pad)) { page -> ZoomableScreenshot(items[page]) } }
    if (showInfo) ModalBottomSheet(onDismissRequest = { showInfo = false }) { ViewerInfoSheet(current, remindersEnabled, onSave, onReminder, onRetryOcr) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Eliminare lo screenshot?") }, text = { Text(if (current.sourceKind == "MEDIASTORE") "Verrà eliminato anche l'originale dalla galleria del telefono." else "Verrà eliminato da RecallShot.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(current) }) { Text("Elimina") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } })
}

@Composable private fun ZoomableScreenshot(item: ScreenshotEntity) { var scale by remember(item.id) { mutableFloatStateOf(1f) }; var offset by remember(item.id) { mutableStateOf(Offset.Zero) }; val state = rememberTransformableState { zoom, pan, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset = if (scale > 1f) offset + pan else Offset.Zero }; Box(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surface)) { AsyncImage(model = item.imageModel(), contentDescription = item.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().transformable(state).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ViewerInfoSheet(item: ScreenshotEntity, remindersEnabled: Boolean, onSave: (ScreenshotEntity, String, String, String) -> Unit, onReminder: (ScreenshotEntity, Long?) -> Unit, onRetryOcr: (Long) -> Unit) {
    var title by remember(item.id,item.title) { mutableStateOf(item.title) }; var note by remember(item.id,item.note) { mutableStateOf(item.note) }; var category by remember(item.id,item.category) { mutableStateOf(item.category) }; var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Dettagli", style = MaterialTheme.typography.titleLarge); OutlinedTextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth()); ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) { OutlinedTextField(categoryLabel(category), {}, readOnly = true, label = { Text("Categoria") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { viewerCategories.forEach { c -> DropdownMenuItem(text = { Text(categoryLabel(c)) }, onClick = { category = c; expanded = false }) } } }; OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)); Button(onClick = { onSave(item, title, note, category) }, modifier = Modifier.fillMaxWidth()) { Text("Salva") }; if (item.ocrText.isNotBlank()) { Text("Testo riconosciuto", style = MaterialTheme.typography.titleMedium); Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Text(item.ocrText, Modifier.padding(14.dp)) } } else if (item.ocrStatus == "PENDING") { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Analisi in corso…") } else if (item.ocrStatus == "ERROR" || item.ocrStatus == "PERMISSION") { FilledTonalButton(onClick = { onRetryOcr(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("Riprova analisi") } }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilledTonalButton(onClick = { onReminder(item, System.currentTimeMillis()+24*60*60*1000L) }, enabled = remindersEnabled, modifier = Modifier.weight(1f)) { Text("Domani") }; FilledTonalButton(onClick = { onReminder(item, System.currentTimeMillis()+7*24*60*60*1000L) }, enabled = remindersEnabled, modifier = Modifier.weight(1f)) { Text("7 giorni") } }; if (item.reminderAt != null) TextButton(onClick = { onReminder(item, null) }, modifier = Modifier.fillMaxWidth()) { Text("Rimuovi promemoria") } }
}
