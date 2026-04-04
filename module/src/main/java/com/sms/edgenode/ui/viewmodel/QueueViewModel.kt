package com.sms.edgenode.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sms.edgenode.data.db.EdgeNodeDatabase
import com.sms.edgenode.data.db.SmsEntity
import com.sms.edgenode.data.db.SmsStatus
import com.sms.edgenode.ui.screens.StatusFilter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.sms.edgenode.ui.state.UiState

class QueueViewModel(private val db: EdgeNodeDatabase) : ViewModel() {

    var searchQuery by mutableStateOf("")
    var selectedFilter by mutableStateOf(StatusFilter.ALL)
    
    var isRefreshing by mutableStateOf(false)
        private set

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            // Local DB view is always up-to-date, 
            // but we simulate a sync check for UX feedback.
            kotlinx.coroutines.delay(1000) 
            isRefreshing = false
        }
    }

    private val _messages = db.smsDao().observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredMessages: StateFlow<UiState<List<SmsEntity>>> = combine(
        _messages,
        snapshotFlow { searchQuery },
        snapshotFlow { selectedFilter }
    ) { messages, query, filter ->
        messages.filter { sms ->
            val matchesSearch = query.isBlank() || 
                sms.body.contains(query, ignoreCase = true) ||
                sms.sender.contains(query, ignoreCase = true)
            
            val matchesStatus = filter == StatusFilter.ALL || 
                sms.status in filter.statuses
            
            matchesSearch && matchesStatus
        }
    }
    .map { if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun deleteMessage(sms: SmsEntity) {
        viewModelScope.launch {
            db.smsDao().delete(sms)
        }
    }

    fun undoDelete(sms: SmsEntity) {
        viewModelScope.launch {
            db.smsDao().insert(sms)
        }
    }
}
