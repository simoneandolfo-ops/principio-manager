package com.recallshot.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recallshot.app.MainViewModel
import com.recallshot.app.data.ScreenshotEntity
import com.recallshot.app.permissions.MediaPermissions
import com.recallshot.app.settings.AppSettings
import com.recallshot.app.ui.components.categoryLabel

@Composable
fun AppRoot(vm: MainViewModel, settings: AppSettings, onSetting: (String, Boolean) -> Unit, openId: Long?, onConsumedOpenId: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var galleryCategory by remember { mutableStateOf<String?>(null) }
    var viewerIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var viewerStartId by remember { mutableStateOf<Long?>(null) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val all by vm.all.collectAsStateWithLifecycle(); val visible by vm.visible.collectAsStateWithLifecycle(); val reminders by vm.reminders.collectAsStateWithLifecycle(); val query by vm.query.collectAsStateWithLifecycle(); val context = LocalContext.current
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (MediaPermissions.canRead(context)) vm.scanNow(full = true) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)) { uris -> uris.forEach { vm.importShared(it, "photo-picker") } }
    var pendingReminder by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> val pending = pendingReminder; pendingReminder = null; if (granted && pending != null) vm.setReminder(pending.first, pending.second) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result -> val id = pendingDeleteId; pendingDeleteId = null; if (result.resultCode == Activity.RESULT_OK && id != null) vm.delete(id) }
    fun openViewer(id: Long, items: List<ScreenshotEntity>) { viewerIds = items.map { it.id }; viewerStartId = id }
    fun requestDelete(item: ScreenshotEntity) {
        if (item.sourceKind == "MEDIASTORE" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(Uri.parse(item.contentUri))); pendingDeleteId = item.id; deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build()) }
        else if (item.sourceKind == "MEDIASTORE") vm.deleteOriginalLegacy(item.id) else vm.delete(item.id)
    }
    LaunchedEffect(openId) { if (openId != null && openId > 0) { viewerIds = all.map { it.id }; viewerStartId = openId; onConsumedOpenId() } }
    val viewerItems = viewerIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
    if (viewerStartId != null && viewerItems.isNotEmpty()) {
        BackHandler { viewerStartId = null }
        PhotoViewerScreen(viewerItems, viewerStartId!!, settings.remindersEnabled, { viewerStartId = null }, vm::toggleFavorite, { item,t,n,c -> vm.save(item,t,n,c) }, ::requestDelete, { item,at -> if (settings.remindersEnabled) { if (at != null && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { pendingReminder = item.id to at; notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } else vm.setReminder(item.id, at) } }, vm::retryOcr)
        return
    }
    val cat = galleryCategory
    if (cat != null) { BackHandler { galleryCategory = null }; val catItems = all.filter { it.category == cat }; GalleryGridScreen(categoryLabel(cat), catItems, { galleryCategory = null }) { openViewer(it, catItems) }; return }
    BackHandler(enabled = tab != 0) { tab = 0 }
    Scaffold(bottomBar = { NavigationBar { NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Outlined.Home, null) }, label = { Text("Home") }); NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Outlined.Collections, null) }, label = { Text("Libreria") }); NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Impostazioni") }) } }) { padding ->
        Surface(modifier = Modifier.padding(padding)) { when (tab) { 0 -> DashboardScreen(all, settings.runOcrAutomatically, visible, reminders, query, vm::setQuery, { galleryCategory = it }, ::openViewer, vm::toggleFavorite) { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }; 1 -> LibraryScreen(visible, query, vm::setQuery, ::openViewer); else -> SettingsScreen(settings, { onSetting("auto_import", it) }, { onSetting("auto_ocr", it) }, { onSetting("reminders", it) }, onRescan = { mediaLauncher.launch(MediaPermissions.requestPermissions()) }) } }
    }
}
