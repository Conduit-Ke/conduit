package com.sms.edgenode.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.edgenode.data.db.EdgeNodeDatabase
import com.sms.edgenode.data.db.AuditLogEntity
import kotlinx.coroutines.flow.*
import com.sms.edgenode.ui.state.UiState

class LogsViewModel(private val db: EdgeNodeDatabase) : ViewModel() {

    val logs: StateFlow<UiState<List<AuditLogEntity>>> = db.auditLogDao().observeLogs()
        .map { if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun clearLogs() {
        // Implementation for clearing logs if needed
    }
}
