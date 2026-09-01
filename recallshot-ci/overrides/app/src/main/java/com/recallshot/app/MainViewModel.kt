package com.recallshot.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recallshot.app.data.ScreenshotEntity
import com.recallshot.app.data.ScreenshotRepository
import com.recallshot.app.notifications.ReminderScheduler
import com.recallshot.app.workers.WorkScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ScreenshotRepository(app)
    private val _query = MutableStateFlow("")
    private val _category = MutableStateFlow<String?>(null)
    val query = _query.asStateFlow()
    val category = _category.asStateFlow()
    val all = repo.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val visible: StateFlow<List<ScreenshotEntity>> = combine(repo.all, _query, _category) { items, q, cat -> Triple(items, q, cat) }
        .mapLatest { (items, q, cat) -> repo.search(q, items, cat) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reminders: StateFlow<List<ScreenshotEntity>> = all.map { list -> list.filter { it.reminderAt != null && it.reminderAt >= System.currentTimeMillis() }.sortedBy { it.reminderAt }.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setQuery(value: String) { _query.value = value }
    fun setCategory(value: String?) { _category.value = value }
    fun scanNow(full: Boolean = false) { WorkScheduler.scanNow(getApplication(), full) }
    fun importShared(uri: Uri, sourceApp: String?) = viewModelScope.launch { repo.importShared(uri, sourceApp) }
    fun toggleFavorite(item: ScreenshotEntity) = viewModelScope.launch { repo.setFavorite(item.id, !item.isFavorite) }
    fun retryOcr(id: Long) { repo.enqueueOcr(id, force = true) }
    fun processPendingOcr() = viewModelScope.launch { repo.enqueuePendingOcr() }
    fun save(item: ScreenshotEntity, title: String, note: String, category: String) = viewModelScope.launch { repo.edit(item.id, title.trim().ifBlank { "Screenshot" }, note.trim(), category) }
    fun delete(id: Long) = viewModelScope.launch { ReminderScheduler.cancel(getApplication(), id); repo.delete(id) }
    fun deleteOriginalLegacy(id: Long) = viewModelScope.launch { ReminderScheduler.cancel(getApplication(), id); repo.deleteOriginalLegacy(id) }
    fun disableAllReminders() = viewModelScope.launch { ReminderScheduler.cancelAll(getApplication()); repo.clearAllReminders() }
    fun setReminder(id: Long, atMillis: Long?) = viewModelScope.launch { repo.setReminder(id, atMillis); if (atMillis == null) ReminderScheduler.cancel(getApplication(), id) else ReminderScheduler.schedule(getApplication(), id, atMillis) }
}
