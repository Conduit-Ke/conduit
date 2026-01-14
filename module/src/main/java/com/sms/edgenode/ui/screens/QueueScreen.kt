package com.sms.edgenode.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sms.edgenode.EdgeNodeApp
import com.sms.edgenode.data.db.SmsEntity
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

enum class StatusFilter(val label: String, val statuses: List<String>) {
    ALL("All", emptyList()),
    PENDING("Pending", listOf("PENDING", "HELD")),
    DELIVERED("Delivered", listOf("DELIVERED")),
    FAILED("Failed", listOf("FAILED", "LOOP_DETECTED"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as EdgeNodeApp
    val queue by app.database.smsDao().observeQueue().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    // Search History Management
    val prefs = remember { context.getSharedPreferences("search_history", Context.MODE_PRIVATE) }
    var searchHistory by remember { 
        mutableStateOf(
            prefs.getStringSet("history", emptySet())?.toList()?.sorted() ?: emptyList()
        ) 
    }

    fun addToHistory(query: String) {
        if (query.isBlank()) return
        val newHistory = (searchHistory + query).distinct().takeLast(10) // Keep last 10
        searchHistory = newHistory
        prefs.edit().putStringSet("history", newHistory.toSet()).apply()
    }

    fun removeFromHistory(query: String) {
        val newHistory = searchHistory - query
        searchHistory = newHistory
        prefs.edit().putStringSet("history", newHistory.toSet()).apply()
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(StatusFilter.ALL) }
    
    // Filter messages based on search query and status filter
    val filteredQueue = remember(queue, searchQuery, selectedFilter) {
        queue.filter { sms ->
            val matchesSearch = searchQuery.isEmpty() || 
                sms.body.contains(searchQuery, ignoreCase = true) ||
                sms.sender.contains(searchQuery, ignoreCase = true)
            
            val matchesStatus = selectedFilter == StatusFilter.ALL || 
                sms.status in selectedFilter.statuses
            
            matchesSearch && matchesStatus
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Gmail-style Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { 
                isSearchActive = false
                addToHistory(it)
            },
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isSearchActive) 0.dp else 16.dp),
            placeholder = { Text("Search messages...") },
            leadingIcon = { 
                if (isSearchActive) {
                    IconButton(onClick = { isSearchActive = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (searchQuery.isEmpty()) {
                    // Show History
                    if (searchHistory.isNotEmpty()) {
                        item {
                            Text(
                                "Recent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                            )
                        }
                        items(searchHistory.reversed()) { historyItem ->
                            ListItem(
                                headlineContent = { Text(historyItem) },
                                leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                                trailingContent = {
                                    IconButton(onClick = { removeFromHistory(historyItem) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete")
                                    }
                                },
                                modifier = Modifier
                                    .clickable { 
                                        searchQuery = historyItem 
                                        isSearchActive = false // Optional: close search on select
                                    }
                                    .fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // Search Results Preview
                    items(filteredQueue.take(5)) { sms ->
                        ListItem(
                            headlineContent = { 
                                Text(sms.sender, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            },
                            supportingContent = { 
                                Text(sms.body, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            },
                            leadingContent = {
                                Icon(Icons.Outlined.Message, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                isSearchActive = false
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Gmail-style Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(StatusFilter.entries) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) },
                    leadingIcon = {
                        if (selectedFilter == filter) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        } else {
                            Icon(
                                imageVector = when (filter) {
                                    StatusFilter.ALL -> Icons.Outlined.Inbox
                                    StatusFilter.PENDING -> Icons.Outlined.Schedule
                                    StatusFilter.DELIVERED -> Icons.Outlined.CheckCircle
                                    StatusFilter.FAILED -> Icons.Outlined.Error
                                },
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Results count
        Text(
            text = "${filteredQueue.size} message${if (filteredQueue.size != 1) "s" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        AnimatedContent(
            targetState = filteredQueue.isEmpty(),
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "queue_content"
        ) { isEmpty ->
            if (isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedFilter != StatusFilter.ALL)
                                "No matching messages"
                            else
                                "No messages in queue",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredQueue, key = { it.id }) { sms ->
                        SwipeToDismissItem(
                            sms = sms,
                            onDismiss = { 
                                scope.launch {
                                    app.database.smsDao().delete(sms)
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissItem(
    sms: SmsEntity,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topNotification = com.sms.edgenode.ui.LocalTopNotificationState.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current
    val app = context.applicationContext as EdgeNodeApp
    val scope = rememberCoroutineScope()
    
    val canDismiss = sms.status == "DELIVERED"

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (canDismiss && (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd)) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                
                // Show Undo Notification
                topNotification.showNotification(
                    message = "Message from ${sms.sender} deleted",
                    actionLabel = "UNDO",
                    onAction = {
                        scope.launch {
                            app.database.smsDao().insert(sms)
                        }
                    }
                )
                
                onDismiss()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = canDismiss,
        enableDismissFromStartToEnd = canDismiss,
        backgroundContent = {
            if (canDismiss) {
                val color = MaterialTheme.colorScheme.errorContainer
                val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(color, MaterialTheme.shapes.medium)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        modifier = modifier
    ) {
        SmsCard(sms = sms)
    }
}

@Composable
private fun SmsCard(sms: SmsEntity, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (sms.status) {
                "DELIVERED" -> MaterialTheme.colorScheme.primaryContainer
                "FAILED", "LOOP_DETECTED" -> MaterialTheme.colorScheme.errorContainer
                "HELD" -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sms.sender,
                    style = MaterialTheme.typography.titleSmall
                )
                StatusChip(sms.status)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = sms.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimestamp(sms.receivedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (sms.attempts > 0) {
                    Text(
                        text = "Attempts: ${sms.attempts}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (icon, containerColor, contentColor) = when (status) {
        "DELIVERED" -> Triple(
            Icons.Outlined.CheckCircle,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        "FAILED", "LOOP_DETECTED" -> Triple(
            Icons.Outlined.Error,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
        "HELD" -> Triple(
            Icons.Outlined.Pause,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary
        )
        else -> Triple(
            Icons.Outlined.Schedule,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
    }
    
    AssistChip(
        onClick = { },
        label = { Text(status, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor.copy(alpha = 0.15f),
            labelColor = containerColor,
            leadingIconContentColor = containerColor
        ),
        border = null
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
