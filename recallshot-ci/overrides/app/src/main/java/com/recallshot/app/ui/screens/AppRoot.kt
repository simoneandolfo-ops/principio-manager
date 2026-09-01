package com.recallshot.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.recallshot.app.permissions.MediaPermissions
import com.recallshot.app.settings.AppSettings

@Composable
fun AppRoot(
    vm: MainViewModel,
    settings: AppSettings,
    onSetting: (String, Boolean) -> Unit,
    openId: Long?,
    onConsumedOpenId: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    val all by vm.all.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (MediaPermissions.canRead(context)) vm.scanNow(full = true)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)) { uris ->
        uris.forEach { uri -> vm.importShared(uri, "photo-picker") }
    }

    var pendingReminder by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingReminder
        pendingReminder = null
        if (granted && pending != null) vm.setReminder(pending.first, pending.second)
    }

    LaunchedEffect(openId) {
        if (openId != null && openId > 0) {
            detailId = openId
            onConsumedOpenId()
        }
    }

    val detail = detailId?.let { id -> all.firstOrNull { it.id == id } }
    if (detail != null) {
        DetailScreen(
            item = detail,
            remindersEnabled = settings.remindersEnabled,
            onBack = { detailId = null },
            onFavorite = { vm.toggleFavorite(detail) },
            onSave = { t, n, c -> vm.save(detail, t, n, c) },
            onRetryOcr = { vm.retryOcr(detail.id) },
            onDelete = { vm.delete(detail.id); detailId = null },
            onReminder = { at ->
                if (settings.remindersEnabled) {
                    if (
                        at != null && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingReminder = detail.id to at
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.setReminder(detail.id, at)
                    }
                }
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Outlined.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Outlined.Collections, null) }, label = { Text("Libreria") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Impostazioni") })
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> DashboardScreen(
                    all,
                    settings.runOcrAutomatically,
                    visible,
                    reminders,
                    query,
                    category,
                    vm::setQuery,
                    vm::setCategory,
                    { detailId = it },
                    vm::toggleFavorite,
                    { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
                1 -> LibraryScreen(visible, query, vm::setQuery, { detailId = it }, vm::toggleFavorite)
                else -> SettingsScreen(
                    settings,
                    { onSetting("auto_import", it) },
                    { onSetting("auto_ocr", it) },
                    { onSetting("reminders", it) },
                    onRescan = { mediaLauncher.launch(MediaPermissions.requestPermissions()) }
                )
            }
        }
    }
}
